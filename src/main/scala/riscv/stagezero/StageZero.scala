package riscv.stagezero

import riscv.stagezero.core._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.misc.HexTools

import scala.language.postfixOps

object StageZero {
  /**
    * 由HexTools.initRam修改而来。本函数从Intel Hex加载16位内存
    * @param ram 内存（16位）
    * @param onChipRamHexFile ihex路径
    * @param hexOffset 地址偏移值
    */
   def initRam16[T <: Data](ram : Mem[T], onChipRamHexFile : String, hexOffset : BigInt): Unit ={
    val initContent = Array.fill[BigInt](ram.wordCount)(0)
    HexTools.readHexFile(onChipRamHexFile, 0,(address,data) => {
      //println(f"(TB) addr 0x$address%x data 0x$data%02x")
      val addressWithoutOffset = (address - hexOffset).toInt
      initContent(addressWithoutOffset >> 1) |= BigInt(data) << ((addressWithoutOffset & 1)*8)
    })
//     for (i <- 16 until initContent.length / 2) {
//       println(f"(TB) ${initContent(i*2+1)}%04x${initContent(i*2)}%04x")
//     }
    ram.initBigInt(initContent)
}
}

case class StageZero(privMemSize: Int, firmware: String) extends Component {
  val io = new Bundle {
    val run: Bool = in Bool
    val mem_ready: Bool = in Bool
    val mem_err: Bool = in Bool
    val mem_valid: Bool = out Bool
    //val mem_addr: UInt = out UInt(31 bits)
    val mem_rdata: Bits = in Bits(32 bits)
    val mem_wstrb: Bits = out Bits(4 bits)
    //val mem_wdata: Bits = out Bits(32 bits)

    val gpio0_o: Bits = out Bits(8 bits)
    val gpio0_i: Bits = in Bits(8 bits)
    val dir0_o: Bits = out Bits(8 bits)
  }
  // TODO
  io.mem_valid := False
  io.mem_wstrb := B"4'b0"
  io.gpio0_o := B"8'b0"
  io.dir0_o := B"8'b0"
  /**
    * 常量
    */
  // 由于是半字地址，所以减少1位
  val privMemAddrWidth: Int = (Math.log10(privMemSize) / Math.log10(2)).toInt - 1
  //val firmware: Array[Bits] = Array.fill(privMemSize / 2)(B"8'b0")

  /**
    * 私有可执行内存，地址从0xC0000000开始。SSRAM无输出寄存器，延迟一周期。
    */
  val memPriv = Mem(Bits(16 bits), privMemSize / 2)
  StageZero.initRam16(memPriv, firmware, 0xC0000000l)

  val memPrivAddr: UInt = UInt(privMemAddrWidth bits)
  val memPrivValid: Bool = Bool
  val memPrivRData: Bits = Bits(16 bits)
  val memPrivWData: Bits = Bits(16 bits)
  val memPrivWen: Bool = Bool
  val memPrivWstrb: Bits = Bits(2 bits)

  memPrivRData := memPriv.readWriteSync(
    memPrivAddr, memPrivWData, memPrivValid, memPrivWen, memPrivWstrb)


  /**
    * 控制信号（寄存器）
    */
  val decode = Reg(Bool) init False

  val mmuVAddrValid = Reg(Bool) init False

  val rs1Valid = Reg(Bool) init False
  val rs2Valid = Reg(Bool) init False
  val immValid = Reg(Bool) init False

  val loadRs1 = Reg(Bool) init False
  val op1Rs1 = Reg(Bool) init False
  val op1Pc = Reg(Bool) init False

  val loadRs2 = Reg(Bool) init False
  val op2Imm = Reg(Bool) init False
  val op2Rs2 = Reg(Bool) init False
  val op2Four = Reg(Bool) init False

  val immI = Reg(Bool) init False
  val immJ = Reg(Bool) init False
  val immU = Reg(Bool) init False
  val immS = Reg(Bool) init False
  val immB = Reg(Bool) init False

  val alu = Reg(Bool) init False

  val jump = Reg(Bool) init False
  val link = Reg(Bool) init False
  val writeback = Reg(Bool) init False

  /**
    * 数据路径
    */
  val inst = Reg(Bits(32 bits))
  val rs1 = Reg(Bits(32 bits))
  val rs2 = Reg(Bits(32 bits))
  val pc = Reg(UInt(32 bits)) init U"32'hC0000040"
  val imm = Reg(Bits(32 bits))

  val aluSigned = Reg(Bool)
  val aluOp = Reg(SZAluOp)

  val aluRes = Bits(32 bits)
  val aluResValid = Bool

  /**
    * 运算单元
    */
  val alu0 = SZAlu()

  alu0.io.op1Rs1 <> op1Rs1
  alu0.io.op1Pc <> op1Pc

  alu0.io.op2Rs2 <> op2Rs2
  alu0.io.op2Imm <> op2Imm
  alu0.io.op2Four <> op2Four

  alu0.io.rs1 <> rs1
  alu0.io.rs2 <> rs2
  alu0.io.pc <> pc
  alu0.io.imm <> imm

  alu0.io.rs1Valid <> rs1Valid
  alu0.io.rs2Valid <> rs2Valid
  alu0.io.immValid <> immValid

  alu0.io.signed <> aluSigned
  alu0.io.opSel <> aluOp
  alu0.io.res <> aluRes
  alu0.io.resValid <> aluResValid

  /**
    * 内存管理单元
    */
  val mmu = SZMmu(privMemAddrWidth)

  val mmuReady = Bool
  val mmuNextReady = Bool
  val mmuVAddr = Reg(UInt(32 bits))
  val mmuWData = Reg(Bits(32 bits))

  val mmuSigned = Reg(Bool)
  val mmuStore = Reg(Bool)
  val mmuWidth = Reg(MmuOpWidth())

  val mmuOut = Bits(32 bits)
  val mmuOutValid = Bool
  val mmuAccessError = Bool

  mmu.io.ready <> mmuReady
  mmu.io.nextReady <> mmuNextReady
  mmu.io.vAddr <> mmuVAddr
  mmu.io.vAddrValid <> mmuVAddrValid
  mmu.io.wData <> mmuWData
  mmu.io.signed <> mmuSigned
  mmu.io.store <> mmuStore
  mmu.io.width <> mmuWidth
  mmu.io.memOut <> mmuOut
  mmu.io.memOutValid <> mmuOutValid
  mmu.io.accessError <> mmuAccessError
  mmu.io.priMemAddr <> memPrivAddr
  mmu.io.priMemValid <> memPrivValid
  mmu.io.priMemRData <> memPrivRData
  mmu.io.priMemWData <> memPrivWData
  mmu.io.priMemWStrb <> memPrivWstrb

  /**
    * 核心状态机。
    */
  val fsmCore = new StateMachine {
    /**
      * 初始化
      */
    val sReset = new State with EntryPoint
    val sInit = new State

    /**
      * 发射状态
      */
    val sFetch = new State

    /**
      * 一级解码（操作码）
      */
    val sDecode = new State
    /**
      * 二级解码
      */
    val sOp = new State
    val sOpImm = new State
    val sJal = new State
    val sJalr = new State
    val sLoad = new State
    val sStore = new State
    val sBranch = new State
    val sLui = new State
    val sAuiPc = new State
    val sSystem = new State
    val sMiscMem = new State

    /**
      * 运算相关状态
      */
    val sImm = new State
    val sAlu: State = new State

    /**
      * 内存状态
      */
    val sMem = new State
    val memWaiting = Reg(Bool)
    /**
      * 跳转相关状态
      */
    val sLink = new State
    val sJump = new State

    /**
      * 回写状态
      */
    val sWriteBack: State = new State

    /**
      * 运行主状态机
      */
    when (io.run) {
      /**
        * 初始化
        */
      sReset.whenIsActive{
        mmuWidth := MmuOpWidth.word
        goto(sMem)
      }

      sInit.whenIsActive{
        // 现在没有别的功能
        goto(sFetch)
      }
      /**
        * 发射状态
        */
      sFetch.whenIsActive{
        decode := True
        rs1Valid := False
        rs2Valid := False
        immValid := False

        mmuWidth := MmuOpWidth.word
        goto(sMem)
      }

      /**
        * 一级解码状态
        */
      sDecode.whenIsActive{
        decode := False
        loadRs1 := False
        loadRs2 := False
        op1Rs1 := False
        op1Pc := False
        op2Imm := False
        op2Rs2 := False
        op2Four := False
        immI := False
        immJ := False
        immU := False
        immS := False
        immB := False
        alu := False
        jump := False
        link := False
        writeback := False
        switch(inst(6 downto 0)) {
          is(B"00_000_11"){
            // TODO LOAD
          }
          is(B"01_000_11"){
            // TODO STORE
          }
          is(B"00_011_11"){
            // TODO MISC-MEM
          }
          is(B"11_000_11"){
            // TODO BRANCH
          }
          is(B"11_001_11"){
            // TODO JALR
          }
          is(B"11_011_11"){
            goto(sJal)
          }
          is(B"11_100_11"){
            // TODO SYSTEM
          }
          is(B"00_100_11"){
            goto(sOpImm)
          }
          is(B"01_100_11"){
            // TODO OP
          }
          is(B"00_101_11"){
            // TODO AUIPC
          }
          is(B"01_101_11"){
            // TODO LUI
          }
          default{
            // TODO ILLEGAL INSTRUCTION
          }
        }
      }

      /**
        * 二级解码状态，按照未完成架构测试所需要的顺序排列：
        *
        * JAL JALR OPIMM （基础测试 JR + NOP）
        *
        * OP （无需新技术）
        *
        * BRANCH LOAD STORE SYSTEM （方便测试套件）
        *
        * AUIPC LUI
        *
        * MISC-MEM
        */

      sJal.whenIsActive{
        op1Pc := True
        op2Imm := True
        immJ := True
        alu := True
        jump := True
        link := True
        goto(sImm)
      }

      sJalr.whenIsActive{
        loadRs1 := True
        op2Imm := True
        immI := True
        alu := True
        jump := True
        link := True
        mmuWidth := MmuOpWidth.word
        goto(sMem)
      }

      sOpImm.whenIsActive{
        loadRs1 := True
        op2Imm := True
        immI := True
        alu := True
        writeback := True
        mmuWidth := MmuOpWidth.word
        goto(sMem)
      }

      /**
        * 加载与运算
        */

      sImm.whenIsActive{
        when(immI) {
          val base: SInt = SInt(12 bits)
          base := S(inst(31 downto 20))
          imm := B(base.resize(32))
        }.elsewhen(immJ){
          imm := (
            (31 downto 20) -> inst(31)
            , (19 downto 12) -> inst(19 downto 12)
            , 11 -> inst(20)
            , (10 downto 5) -> inst(30 downto 25)
            , (4 downto 1) -> inst(24 downto 21)
            , 0 -> false
          )
        }.elsewhen(immU){
          // TODO
        }.elsewhen(immS){
          // TODO
        }.elsewhen(immB){
          // TODO
        }

        immValid := True
        goto(sAlu) // 这个实现里，IMM 下一个状态必然是 ALU
      }

      sAlu.whenIsActive{
        // 等待ALU运算结束
        when(aluResValid){
          alu := False
          when(link){
            goto(sLink)
          }.elsewhen(writeback){
            goto(sWriteBack)
          }.otherwise{
            //TODO Memory etc
          }
        }
      }

      /**
        * 内存
        *
        * 进入本状态前，需要设置好内存操作。如果是写入，需要设置好写入数据。
        */
      sMem
        .onEntry{
          memWaiting := False
        }.whenIsActive{
        val aRs1 = UInt(4 bits)
        aRs1 := U(inst(18 downto 15))
        val aRs2 = UInt(4 bits)
        aRs2 := U(inst(23 downto 20))
        val aRd = UInt(4 bits)
        aRd := U(inst(10 downto 7))
        // TODO Invalid x16+

        // 自动开始操作
        when(!memWaiting){
          memWaiting := True
          mmuVAddrValid := True
        }.otherwise{
          mmuVAddrValid := False
        }

        // 自动复位
        when(memWaiting && mmuOutValid){memWaiting := False}

        // 设定地址以及等待操作完成
        when(decode){
          // 发射 -> 解码，因此读取PC
          when(memWaiting) {
            when(mmuOutValid){
              inst := mmuOut
              goto(sDecode)
            }
          }.otherwise {
            mmuVAddr := pc
            mmuStore := False
          }
        }.elsewhen(loadRs1){
          // ALU 使用RS1
          op1Rs1 := True
          // 加载RS1
          when(memWaiting){
            // 无需加载RS2的话，加载立即数。否则继续加载RS2
            when(mmuOutValid){
              loadRs1 := False
              rs1Valid := True
              rs1 := mmuOut
              when(!loadRs2) {
                goto(sAlu)
              }
            }
          }.elsewhen(!aRs1.orR){ // x0
            rs1 := 0
            rs1Valid := True
            loadRs1 := False
            // ALU使用RS1
            when(!loadRs2) {
              goto(sAlu)
            }
          }.otherwise {
            mmuVAddr := U(32 bits, (31 downto 30) -> U"2'b11", (5 downto 2) -> aRs1, default -> false)
            mmuStore := False
          }
        }.elsewhen(loadRs2){
          op2Rs2 := True
          // 加载RS2
          when(memWaiting){
            // RS2加载完成后，开始运算
            when(mmuOutValid){
              loadRs2 := False
              rs2Valid := True
              rs2 := mmuOut
              goto(sAlu)
            }
          }.elsewhen(!aRs2.orR){ // x0
            rs2 := 0
            rs2Valid := True
            loadRs2 := False
            goto(sAlu)
          }.otherwise{
            mmuVAddr := U(32 bits, (31 downto 30) -> U"2'b11", (5 downto 2) -> aRs2, default -> false)
            mmuStore := False
          }
        }.elsewhen(writeback){
          // 回写
          when(memWaiting){
            // 写入需要等待ready
            when(mmuNextReady){
              writeback := False
              // 回写后返回发射
              goto(sFetch)
            }
          }.otherwise{
            mmuVAddr := U(32 bits, (31 downto 30) -> U"2'b11", (5 downto 2) -> aRd, default -> false)
            mmuStore := True
          }
        }.otherwise{
          // 初始化设备，先读第一字
          when(memWaiting) {
            when(mmuOutValid) {
              goto(sInit)
            }
          }.otherwise {
            mmuVAddr := U"32'hC0000000"
            mmuStore := False
          }
        }
      }

      /**
        * 跳转
        */
      sLink.whenIsActive{
        op1Pc := True
        op2Four := True
        op1Rs1 := False
        op2Imm := False
        op2Rs2 := False
        link := False
        writeback := True
        goto(sAlu)
      }

      sJump.whenIsActive{
        // TODO FETCH
        pc := U(aluRes)
        goto(sFetch)
      }

      /**
        * 回写
        */
      sWriteBack.whenIsActive{
        // TODO
        when(jump){
          goto(sJump)
        }.otherwise{
          // TODO FETCH
          goto(sFetch)
        }
      }
    } // when (io.run)
  }

  /**
    * 各种调试信号
    */
  val dReset = Bool.simPublic()
  val dInit = Bool.simPublic()
  val dFetch = Bool.simPublic()
  val dDecode = Bool.simPublic()
  val dJal = Bool.simPublic()
  val dJalr = Bool.simPublic()
  val dOpImm = Bool.simPublic()
  val dImm = Bool.simPublic()
  val dAlu = Bool.simPublic()
  val dMem = Bool.simPublic()
  dReset := fsmCore.isActive(fsmCore.sReset)
  dInit := fsmCore.isActive(fsmCore.sInit)
  dFetch := fsmCore.isActive(fsmCore.sFetch)
  dDecode := fsmCore.isActive(fsmCore.sDecode)
  dJal := fsmCore.isActive(fsmCore.sJal)
  dJalr := fsmCore.isActive(fsmCore.sJalr)
  dOpImm := fsmCore.isActive(fsmCore.sOpImm)
  dImm := fsmCore.isActive(fsmCore.sImm)
  dAlu := fsmCore.isActive(fsmCore.sAlu)
  dMem := fsmCore.isActive(fsmCore.sMem)
}

object StageZeroTopLevelSynthesis {
  def main(args: Array[String]): Unit = {
    //val firmware = Array.fill[Bits](128)(B(0, 32 bits))
    //SpinalVerilog(StageZero(512))
  }
}