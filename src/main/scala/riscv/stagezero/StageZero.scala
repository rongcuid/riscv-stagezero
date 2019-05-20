package riscv.stagezero

import riscv.stagezero.core._
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

case class StageZero(privMemSize: Int) extends Component {
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
  /**
    * 常量
    */
  // 由于是半字地址，所以减少1位
  val privMemAddrWidth: Int = (Math.log10(privMemSize) / Math.log10(2)).toInt - 1
  val firmware: Array[Bits] = Array.fill(privMemSize / 2)(B"8'b0")

  /**
    * 私有可执行内存，地址从0xC0000000开始。SSRAM无输出寄存器，延迟一周期。
    */
  val memPriv = Mem(Bits(16 bits), initialContent = firmware)

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
  val decode = Reg(Bool)

  val rs1Valid = Reg(Bool)
  val rs2Valid = Reg(Bool)
  val immValid = Reg(Bool)

  val loadRs1 = Reg(Bool)
  val op1Rs1 = Reg(Bool)
  val op1Pc = Reg(Bool)

  val loadRs2 = Reg(Bool)
  val op2Imm = Reg(Bool)
  val op2Rs2 = Reg(Bool)
  val op2Four = Reg(Bool)

  val immI = Reg(Bool)
  val immJ = Reg(Bool)
  val immU = Reg(Bool)
  val immS = Reg(Bool)
  val immB = Reg(Bool)

  val alu = Reg(Bool)

  val jump = Reg(Bool)
  val link = Reg(Bool)
  val writeback = Reg(Bool)
  /**
    * 数据单元
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
    * 核心状态机。
    */
  val fsmCore: StateMachine = new StateMachine {
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
        // TODO vAddr
        goto(sMem)
      }

      sInit.whenIsActive{
        // Now does nothing
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
        goto(sMem)
      }

      /**
        * 一级解码状态
        */
      sDecode.whenIsActive{
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
        * JAL OPIMM （基础测试 J + NOP）
        *
        * OP （无需新技术）
        *
        * BRANCH LOAD STORE SYSTEM （方便测试套件）
        *
        * AUIPC LUI JALR
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
        // TODO
        goto(sImm)
      }

      sOpImm.whenIsActive{
        loadRs1 := True
        op2Imm := True
        immI := True
        alu := True
        writeback := True
        // TODO
        goto(sImm)
      }

      /**
        * 加载与运算
        */

      sImm.whenIsActive{
        when(immI) {
          // TODO
        }.elsewhen(immJ){
          // TODO
        }.otherwise{
          // TODO
        }

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
        */
      sMem.whenIsActive{
        when(decode){
          decode := False
          goto(sDecode)
        }.elsewhen(loadRs1){
          // TODO MMU
          rs1Valid := True
          loadRs1 := False
          when(!loadRs2){
            when(op2Imm){
              goto(sImm)
            }.otherwise{
              goto(sAlu)
            }
          }
        }.elsewhen(loadRs2){
          // TODO MMU
        }.otherwise{
          // TODO MMU
          goto(sInit)
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
        }
      }
    } // when (io.run)
  }
}

object StageZeroTopLevelSynthesis {
  def main(args: Array[String]): Unit = {
    //val firmware = Array.fill[Bits](128)(B(0, 32 bits))
    SpinalVerilog(StageZero(512))
  }
}