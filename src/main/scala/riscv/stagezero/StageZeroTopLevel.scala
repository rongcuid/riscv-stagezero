package riscv.stagezero

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

class StageZeroTopLevel(firmware: Array[Data]) extends Component {
  val io: Bundle = new Bundle {
    val run: Bool = in Bool
    val mem_ready: Bool = in Bool
    val mem_err: Bool = in Bool
    val mem_valid: Bool = out Bool
    val mem_addr: UInt = out UInt(32 bits)
    val mem_rdata: Bits = in Bits(32 bits)
    val mem_wstrb: Bits = out Bits(4 bits)
    val mem_wdata: Bits = out Bits(32 bits)

    val gpio0_o: Bits = out Bits(8 bits)
    val gpio0_i: Bits = in Bits(8 bits)
    val dir0_o: Bits = out Bits(8 bits)
  }

  /**
    * 核心状态机。大部分使用摩尔状态机，以最大限度降低对主频的影响（大概）。注意：这也会导致周期数增加。
    */
  val core_fsm: StateMachine = new StateMachine {
    val pc: UInt = Reg(UInt(32 bits)) init 0xC0000000

    /**
      * 解码的状态
      */
    val s_fetch: State = new State with EntryPoint
    val s_decode: State = new State
    val s_rs1_op1: State = new State
    val s_rs2_op2: State = new State
    val s_imm_op2: State = new State

    val s_op_dec: State = new State
    val s_opimm_dec: State = new State
    val s_br_dec: State = new State
    val s_lw_dec: State = new State
    val s_sw_dec: State = new State

    /**
      * 运算的状态
      */
    val s_alu_add: State = new State
    val s_alu_sub: State = new State
    val s_alu_sll: State = new State
    val s_alu_slt: State = new State
    val s_alu_sltu: State = new State
    val s_alu_xor: State = new State
    val s_alu_srl: State = new State
    val s_alu_sra: State = new State
    val s_alu_or: State = new State
    val s_alu_and: State = new State

    /**
      * 控制转移状态
      */
    val s_beq: State = new State
    val s_bne: State = new State
    val s_blt: State = new State
    val s_bge: State = new State
    val s_bltu: State = new State
    val s_bgeu: State = new State

    /**
      * 内存映射的状态
      */
    val s_mmap: State = new State

    /**
      * 可执行（私有）内存的状态
      */
    val s_lw_pri_mem: State = new State
    val s_lh_pri_mem: State = new State
    val s_lb_pri_mem: State = new State
    val s_sw_pri_mem: State = new State
    val s_sh_pri_mem: State = new State
    val s_sb_pri_mem: State = new State

    /**
      * 不可执行（外部）内存状态
      */
    val s_lw_ext_mem: State = new State
    val s_lh_ext_mem: State = new State
    val s_lb_ext_mem: State = new State
    val s_sw_ext_mem: State = new State
    val s_sh_ext_mem: State = new State
    val s_sb_ext_mem: State = new State

    /**
      * 回写状态
      */
    val s_write_back_mem2reg: State = new State
    val s_write_back_alu2reg: State = new State
  }
}

object StageZeroTopLevelSynthesis {
  def main(args: Array[String]): Unit = {
    SpinalVerilog(new StageZeroTopLevel(new Array[Data](0)))
    SpinalVhdl(new StageZeroTopLevel(new Array[Data](0)))
  }
}