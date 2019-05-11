package riscv.stagezero

import riscv.stagezero.core.SZException
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
      * 解码的状态
      */
    val sOpDec: State = new State
    val sOpimmDec: State = new State
    val sRs1Op1: State = new State
    val sRs2Op2: State = new State
    val sImmOp2: State = new State

    val sBrDec: State = new State
    val sJalDec: State = new State
    val sJrDec: State = new State
    val sLwDec: State = new State
    val sSwDec: State = new State

    /**
      * 异常控制状态
      */
    val sException: State = new State

    /**
      * 运算的状态
      */
    val sAluAdd: State = new State
    val sAluSub: State = new State
    val sAluSll: State = new State
    val sAluSlt: State = new State
    val sAluSltu: State = new State
    val sAluXor: State = new State
    val sAluSrl: State = new State
    val sAluSra: State = new State
    val sAluOr: State = new State
    val sAluAnd: State = new State

    /**
      * 控制转移状态
      */
    val sBeq: State = new State
    val sBne: State = new State
    val sBlt: State = new State
    val sBge: State = new State
    val sBltu: State = new State
    val sBgeu: State = new State
    val sJal: State = new State
    val sJr: State = new State

    /**
      * 内存映射的状态
      */
    val sMmap: State = new State

    /**
      * 可执行（私有）内存的状态
      */
    val sLwPriMem: State = new State
    val sLhPriMem: State = new State
    val sLbPriMem: State = new State
    val sSwPriMem: State = new State
    val sShPriMem: State = new State
    val sSbPriMem: State = new State

    /**
      * 不可执行（外部）内存状态
      */
    val sLwExtMem: State = new State
    val sLhExtMem: State = new State
    val sLbExtMem: State = new State
    val sSwExtMem: State = new State
    val sShExtMem: State = new State
    val sSbExtMem: State = new State

    /**
      * 回写状态
      */
    val sWriteBackMem2reg: State = new State
    val sWriteBackAlu2reg: State = new State


    /**
      * 数据路径（寄存器）
      */
    val pc = Reg(UInt(32 bits)) init U"32'hC0000000"
    val waitCounter = Reg(UInt(1 bits)) init 0
    val gpio0 = Reg(Bits(8 bits)) init 0x00
    val dir0 = Reg(Bits(8 bits)) init 0x00
    // 高16位/低16位
    val memHigh = Reg(Bool) init False

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
          tcause := SZException.InstructionAccessFault.asBits.asUInt
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
        * (JR) -> sJrDec
        * (非法操作码) -> sException（异常）；记录原因
        */
      sOpDec.whenIsActive{
        val opcode = Bits(7 bits)
        opcode := memPrivRData(6 downto 0)
      }

    } // when (io.run)
  }
}

object StageZeroTopLevelSynthesis {
  def main(args: Array[String]): Unit = {
    //val firmware = Array.fill[Bits](128)(B(0, 32 bits))
    SpinalVerilog(StageZeroTopLevel(512))
  }
}