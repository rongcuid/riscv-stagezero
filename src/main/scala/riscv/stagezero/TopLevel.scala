package riscv.stagezero

import spinal.core._

class TopLevel(firmware: Array[Data]) extends Component {
  val io: Bundle = new Bundle {
    val mem_ready = in Bool
    val mem_valid = out Bool
    val mem_addr  = out UInt(32 bits)
    val mem_rdata = in Bits(32 bits)
    val mem_wstrb = out Bits(4 bits)
    val mem_wdata = out Bits(32 bits)
  }
}

object StageZeroTopLevel {
  def main(args: Array[String]): Unit = {
    SpinalVerilog(new TopLevel(new Array[Data](0)))
    SpinalVhdl(new TopLevel(new Array[Data](0)))
  }
}