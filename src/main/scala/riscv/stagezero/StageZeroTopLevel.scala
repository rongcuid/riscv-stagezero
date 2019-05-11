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
  // 由于是字地址，所以减少2位
  val privMemAddrWidth: Int = (Math.log10(privMemSize) / Math.log10(2)).toInt - 2
  val firmware: Array[Bits] = Array.fill(privMemSize / 4)(B"0")

  /**
    * 私有可执行内存，地址从0xC0000000开始。应带输出寄存器，即延迟2周期。
    */
  val memPriv = Mem(Bits(32 bits), initialContent = firmware)
  val memPrivAddr: UInt = UInt(privMemAddrWidth bits)
  val memPrivValid: Bool = Reg(Bool) init False
  val memPrivRData: Bits = memPriv.readSync(address = memPrivAddr, enable = memPrivValid)

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
    val sDecode: State = new State
    val sRs1Op1: State = new State
    val sRs2Op2: State = new State
    val sImmOp2: State = new State

    val sOpDec: State = new State
    val sOpimmDec: State = new State
    val sBrDec: State = new State
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
    val pc: UInt = Reg(UInt(32 bits)) init U"32'hC0000000"
    val waitCounter: UInt = Reg(UInt(1 bits)) init 0
    val gpio0: Bits = Reg(Bits(8 bits)) init 0x00
    val dir0: Bits = Reg(Bits(8 bits)) init 0x00

    /**
      * 数据路径（陷阱）
      */
    val tcause: UInt = Reg(UInt(32 bits))
    val tepc: UInt = Reg(UInt(32 bits))

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
    memPrivAddr := pc(privMemAddrWidth+1 downto 2)
    memPrivValid := False

    /**
      * 运行主状态机
      */
    when (io.run) {
      sFetch.whenIsActive {
        /**
          * 执行内存地址从0xC0000000开始
          */
        when (pc(31 downto 30) === U"2'b11") {
          // 下周期开始读取内存
          memPrivValid := True
          // 等待
          goto(sFetchWait)
        } otherwise {
          tepc := pc
//          tcause := SZException.InstructionAccessFault.asBits.asUInt
          goto(sException)
        }
      } // sFetch.whenIsActive
    } // when (io.run)
  }
}

object StageZeroTopLevelSynthesis {
  def main(args: Array[String]): Unit = {
    //val firmware = Array.fill[Bits](128)(B(0, 32 bits))
    SpinalVerilog(StageZeroTopLevel(512))
  }
}