package riscv.stagezero

//import riscv.stagezero.core.SZException
import riscv.stagezero.core._
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

case class StageZeroTopLevel(privMemSize: Int) extends Component {
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
  val memPrivValid: Bool = Reg(Bool) init False
  val memPrivRData: Bits = Bits(16 bits)
  val memPrivWData: Bits = Bits(16 bits)
  val memPrivWen: Bool = Bool
  val memPrivWstrb: Bits = Bits(2 bits)

  memPrivRData := memPriv.readWriteSync(
    memPrivAddr, memPrivWData, memPrivValid, memPrivWen, memPrivWstrb)

  /**
    * 核心状态机。大部分使用摩尔状态机，以最大限度降低对主频的影响（大概）。注意：这也会导致周期数增加。
    */
  val core_fsm: StateMachine = new StateMachine {
    /**
      * 发射状态
      */
    val sFetch: State = new State with EntryPoint
    val sFetchWait: State = new State

    /**
      * 一级解码（操作码）
      */
    val sOpcodeDec: State = new State

    /**
      * 二级解码
      */
    val sOpDec: State = new State
    val sOpimmDec: State = new State
    val sBrDec: State = new State
    val sJalDec: State = new State
    val sJalrDec: State = new State
    val sLwDec: State = new State
    val sSwDec: State = new State

    /**
      * 读取ALU参数
      */
    val sPcOp1: State = new State
    val sRs1Op1: State = new State
    val sResOp1FourOp2: State = new State
    val sRs2Op2: State = new State
    val sImmOp2: State = new State

    /**
      * 异常控制状态
      */
    val sException: State = new State

    /**
      * 运算的状态
      */
    val sAlu: State = new State

    /**
      * 内存映射的状态
      */
    val sMmap: State = new State

    /**
      * 可执行（私有）内存的状态
      */
    val sPriMemW: State = new State
    val sPriMemH: State = new State
    val sPriMemB: State = new State

    /**
      * 不可执行（外部）内存状态
      */
    val sExtMemW: State = new State
    val sExtMemH: State = new State
    val sExtMemB: State = new State

    /**
      * 输入/输出设备操作状态
      */
    val sIoW: State = new State
    val sIoH: State = new State
    val sIoB: State = new State

    /**
      * 回写状态
      */
    val sWriteBack: State = new State

    /**
      * 控制转移状态
      */
    val sJ: State = new State
    val sIncPc: State = new State

    /**
      * 数据路径（寄存器）
      */
    val pc: UInt = Reg(UInt(32 bits)) init U"32'hC0000000"
    val waitCounter: UInt = Reg(UInt(1 bits)) init 0
    val gpio0: Bits = Reg(Bits(8 bits)) init 0x00
    val dir0: Bits = Reg(Bits(8 bits)) init 0x00
    // 高16位/低16位
    val memHigh: Bool = Reg(Bool) init False
    val memWrite: Bool = Reg(Bool) init False

    /**
      * 数据路径（ALU）
      */
    // ALU 所有输入/输出都是同步的
    val aluOp1: Bits = Reg(Bits(32 bits)) init 0
    val aluOp2: Bits = Reg(Bits(32 bits)) init 0
    val aluSigned: Bool = Reg(Bool) init False
    val aluOpSel = Reg(SZAluOp()) init SZAluOp.Add
    val aluRes: Bits = Bits(32 bits)

    val alu = SZAlu()
    alu.io.op1 <> aluOp1
    alu.io.op2 <> aluOp2
    alu.io.signed <> aluSigned
    alu.io.opSel <> aluOpSel
    alu.io.res <> aluRes

    /**
      * 数据路径（状态机流程）
      */
    // 真为读取，假为写入
    val loadStoreN: Bool = Reg(Bool) init False
    // 真则用内存回写，假则用运算结果回写
    val memToReg: Bool = Reg(Bool) init False
    // 真则跳转
    val doJump: Bool = Reg(Bool) init False
    // 真则分支，假则跳转
    val branchJumpN: Bool = Reg(Bool) init False
    // 真则相对跳转，假则绝对跳转
    val jalJalrN: Bool = Reg(Bool) init False
    // 真则在计算偏移值
    val offset: Bool = Reg(Bool) init False

    /**
      * 数据路径（寄存器文件）
      */
    val aRs1: UInt = Reg(UInt(4 bits))
    val aRs2: UInt = Reg(UInt(4 bits))

    val dRs1: Bits = Bits(32 bits)
    val dRs2: Bits = Bits(32 bits)

    val aRd:  UInt = Reg(UInt(4 bits))
    val dRd:  Bits = Reg(Bits(32 bits))
    val writeback: Bool = Reg(Bool)

    val regFile = SZRegFile()
    regFile.io.aRs1 <> aRs1
    regFile.io.dRs1 <> dRs1
    regFile.io.aRs2 <> aRs2
    regFile.io.dRs2 <> dRs2
    regFile.io.aRd <> aRd
    regFile.io.dRd <> dRd

    /**
      * 数据路径（陷阱）
      */

    val tcause = Reg(UInt(32 bits))
    val tepc = Reg(UInt(32 bits))

    /**
      * 数据路径（组合）
      */
    io.gpio0_o := gpio0
    io.dir0_o := dir0
    val opcode: Bits = Bits(7 bits)
    opcode := memPrivRData(6 downto 0)

    /**
      * 默认值（副作用）
      */
    io.mem_valid := False
    io.mem_wstrb := B"4'b0000"
    // 物理字地址（32位）
    memPrivAddr := U(
      (privMemAddrWidth - 1 downto 1) -> pc(privMemAddrWidth downto 2),
      0 -> memHigh
    )
    memPrivValid := False

    /**
      * 运行主状态机
      */
    when (io.run) {

      /**
        * 发射状态（初始）。地址合法则发射等待。下周期开始读取低半字
        *
        * (地址合法) -> sFetchWait（等待）；请求读取低半字
        * (地址非法) -> sException（异常）；记录异常原因
        */
      sFetch.whenIsActive {
        // 执行内存地址从0xC0000000开始
        when (pc(31 downto 30) === U"2'b11") {
          // 下周期开始读取内存
          memPrivValid := True
          // 从低半字开始
          memHigh := False
          // 等待
          goto(sFetchWait)
        } otherwise {
          tepc := pc
          tcause := U(SZException.InstructionAccessFault.asBits, 32 bits)
          goto(sException)
        }
      } // sFetch.whenIsActive

      /**
        * 发射等待状态。开始读取低半字；下周期开始读取高半字。
        *
        * (True) -> sOpDec（操作码解码）；请求读取高半字
        */
      sFetchWait.whenIsActive{
        // 等待一周期取低半字，并且准备取高半字
        memPrivValid := True
        // 高半字
        memHigh := True
        // 解码
        goto(sOpDec)
      } // sFetchWait.whenIsActive

      /**
        * 操作码解码状态。当前内存输出指令低半字；开始读取高半字；根据操作码解码结果改变状态
        *
        * 写入指令低半字
        *
        * (OP) -> sOpDec
        * (OPIMM) -> sOpimmDec
        * (BRANCH) -> sBrDec
        * (LOAD) -> sLwDec
        * (STORE) -> sSwDec
        * (JAL) -> sJalDec
        * (JALR) -> sJalrDec
        * (非法操作码) -> sException（异常）；记录原因
        */
      // TODO
      sOpcodeDec.whenIsActive{}

      /**
        * OP（运算）指令的解码。此时完整的指令已经读取完成。
        *
        * (根据func3+funct7) -> sRs2Op2 -> sRs1Op1 -> sAlu -> sWriteBack
        * (非法指令) -> sException
        */
      // TODO
      sOpDec.whenIsActive{}

      /**
        * OPIMM（即时数运算）指令的解码。
        *
        * (根据funct3) -> sImmOp2 -> sRs1Op1 -> sAlu -> sWriteBack
        * (非法指令) -> sException
        */
      // TODO
      sOpimmDec.whenIsActive{}

      /**
        * BRANCH（分支）指令的解码。
        *
        * (_) -> sRs2Op2 -> sRs1Op1 -> sAlu -> sJ
        */
      // TODO
      sBrDec.whenIsActive{}

      /**
        * JAL（跳转链接）指令的解码
        *
        * (_) -> sImmOp2 -> sPcOp1 -> sAlu (PC + imm) -> sResOp1FourOp2 -> sAlu (+4) -> sJ
        */
      // TODO
      sJalDec.whenIsActive{}

      /**
        * JALR（跳转链接寄存器）指令的解码
        *
        * (_) -> sImmOp2 -> sRs1Op1 -> sAlu -> sJ
        */
      // TODO
      sJalrDec.whenIsActive{}

      /**
        * LOAD（读取）指令的解码
        *
        * (_) -> sImmOp2 -> sRs1Op1 -> sAlu (rs1 + imm) -> sMmap
        */
      // TODO
      sLwDec.whenIsActive{}

      /**
        * STORE（储存）指令的解码
        *
        * (_) -> sImmOp2 -> sRs1Op1 -> sAlu (rs1 + imm) -> sMmap
        */
      // TODO
      sSwDec.whenIsActive{}

      /**
        * 读取RS1至Op1
        *
        * (_) -> sAlu
        */
      // TODO
      sRs1Op1.whenIsActive{}

      /**
        * 读取PC至Op1
        *
        * (_) -> sAlu
        */
      // TODO
      sPcOp1.whenIsActive{}

      /**
        * 读取ALU结果至Op1，并将4写入Op2
        *
        * (_) -> sAlu
        */
      // TODO
      sResOp1FourOp2.whenIsActive{}

      /**
        * 读取RS2至Op2
        *
        * (_) -> sRs1Op1
        */
      // TODO
      sRs2Op2.whenIsActive{}

      /**
        * 读取imm至Op2
        *
        * (jump & jalJalrN) -> sPcOp1
        * (_) -> sRs1Op1
        */
      // TODO
      sImmOp2.whenIsActive{}

      /**
        * ALU操作
        *
        * (branch) -> sBr
        * (jump & jalJalrN & offset) -> sResOp1FourOp2
        * (jump & jalJalrN & !offset) -> sJ
        * (jump & !jalJalrN) -> sJ
        * (memToReg) -> sMmap
        * (_) -> sWriteBack
        */
      // TODO
      sAlu.whenIsActive{}

      /**
        * 内存映射
        *
        * (0x00000000 - 0x7FFFFFFF) -> sExtMem[WHB]
        * (0x80000000 - 0xBFFFFFFF) -> sIo[WHB]
        * (0xC0000000 - 0xFFFFFFFF) -> sPriMem[WHB]
        */
      // TODO
      sMmap.whenIsActive{}

      /**
        * 私有内存字/半字/字节操作
        *
        * (memWrite) -> sIncPC
        * (!memWrite) -> sWriteBack
        */
      // TODO
      sPriMemW.whenIsActive{}
      sPriMemH.whenIsActive{}
      sPriMemB.whenIsActive{}

      /**
        * 外部内存字/半字/字节操作
        */
      // TODO
      sExtMemW.whenIsActive{}
      sExtMemH.whenIsActive{}
      sExtMemB.whenIsActive{}

      /**
        * 输入/输出设备字/半字/字节操作
        */
      // TODO
      sIoW.whenIsActive{}
      sIoH.whenIsActive{}
      sIoB.whenIsActive{}

      /**
        * 跳转
        *
        * (branchJumpN) -> sFetch
        * (!branchJumpN) -> sWriteBack -> sFetch
        * (未对齐) -> sException
        */
      // TODO
      sJ.whenIsActive{}

      /**
        * PC+4
        *
        * (_) -> sFetch
        */
      // TODO
      sIncPc.whenIsActive{}

      /**
        * 回写
        *
        * (doJump) -> sFetch
        * (_) -> sPcInc -> sFetch
        */
      // TODO
      sWriteBack.whenIsActive{}

    } // when (io.run)
  }
}

object StageZeroTopLevelSynthesis {
  def main(args: Array[String]): Unit = {
    //val firmware = Array.fill[Bits](128)(B(0, 32 bits))
    SpinalVerilog(StageZeroTopLevel(512))
  }
}