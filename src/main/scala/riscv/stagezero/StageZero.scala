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
    * 核心状态机。大部分使用摩尔状态机，以最大限度降低对主频的影响（大概）。注意：这也会导致周期数增加。
    */
  val core_fsm: StateMachine = new StateMachine {
    /**
      * 发射状态
      */
    // TODO

    /**
      * 一级解码（操作码）
      */
    // TODO
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
      * 跳转相关状态
      */
    val sLink = new State
    val sJump = new State

    /**
      * 回写状态
      */
    val sWriteBack: State = new State

    /**
      * 控制信号（寄存器）
      */
    val loadRs1 = Reg(Bool)
    val op1Pc = Reg(Bool)

    val op2Imm = Reg(Bool)

    val immI = Reg(Bool)
    val immJ = Reg(Bool)

    val alu = Reg(Bool)

    val jump = Reg(Bool)
    val link = Reg(Bool)

    val writeback = Reg(Bool)

    /**
      * 运行主状态机
      */
    when (io.run) {
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
      }

      sOpImm.whenIsActive{
        loadRs1 := True
        op2Imm := True
        immI := True
        alu := True
        writeback := True
        // TODO
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