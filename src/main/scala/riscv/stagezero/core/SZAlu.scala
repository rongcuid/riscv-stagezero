package riscv.stagezero.core

import spinal.core._

/**
  * ALU操作枚举
  */
object SZAluOp extends SpinalEnum {
  val Add, Sub, Sll, Slt, Xor, Srl, Sra, Or, And = newElement()
}

case class SZAluIO() extends Bundle {
  val op1: Bits = in Bits(32 bits)
  val op2: Bits = in Bits(32 bits)
  val signed: Bool = in Bool
  val opSel: SpinalEnumCraft[SZAluOp.type] = in(SZAluOp)
  val res: Bits = out Bits(32 bits)
}

/**
  * 运算逻辑单元（ALU）
  */
case class SZAlu() extends Component {
  val io = SZAluIO()
  // TODO
}
