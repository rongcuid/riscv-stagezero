package riscv.stagezero.core

import spinal.core._

case class SZRegFileIO() extends Bundle {
  val aRs1: UInt = in UInt(4 bits)
  val dRs1: Bits = out Bits(32 bits)

  val aRs2: UInt = in UInt(4 bits)
  val dRs2: Bits = out Bits(32 bits)

  val aRd: UInt = in UInt(4 bits)
  val dRd: Bits = in Bits(32 bits)
  val we: Bool = in Bool
}

/**
  * RV32E的寄存器文件。同周期读取，时钟沿写入
  */
case class SZRegFile() extends Component {
  val io: SZRegFileIO = SZRegFileIO()
}
