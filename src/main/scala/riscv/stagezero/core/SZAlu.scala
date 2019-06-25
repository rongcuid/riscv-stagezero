package riscv.stagezero.core

import spinal.core._

/**
  * ALU操作枚举
  */
object SZAluOp extends SpinalEnum {
  val Add, Sub, Sll, Slt, Xor, Srl, Sra, Or, And = newElement()
}

case class SZAluIO() extends Bundle {
  val aluValid: Bool = in Bool
  val op1Rs1: Bool = in Bool
  val op1Pc: Bool = in Bool
  val op2Rs2: Bool = in Bool
  val op2Imm: Bool = in Bool
  val op2Four: Bool = in Bool

  val rs1: Bits = in Bits(32 bits)
  val rs2: Bits = in Bits(32 bits)
  val pc: UInt = in UInt(32 bits)
  val imm: Bits = in Bits(32 bits)

  val rs1Valid: Bool = in Bool
  val rs2Valid: Bool = in Bool
  val immValid: Bool = in Bool

  val signed: Bool = in Bool
  val opSel: SpinalEnumCraft[SZAluOp.type] = in(SZAluOp)
  val res: Bits = out Bits(32 bits)
  val resValid: Bool = out Bool
}

/**
  * 运算逻辑单元（ALU）
  */
case class SZAlu() extends Component {
  val io = SZAluIO()

  val res = Reg(Bits(32 bits))
  val resValid = Reg(Bool)

  val aluValid = Reg(Bool)

  val op1 = Reg(Bits(32 bits))
  val op1Valid = Reg(Bool) init False
  val op2 = Reg(Bits(32 bits))
  val op2Valid = Reg(Bool) init False

  aluValid := io.aluValid

  when(io.op1Rs1){
    op1Valid := io.rs1Valid
    op1 := io.rs1
  }.elsewhen(io.op1Pc){
    op1Valid := True
    op1 := B(io.pc)
  }.otherwise{
    op1Valid := False
  }

  when(io.op2Imm){
    op2Valid := io.immValid
    op2 := io.imm
  }.elsewhen(io.op2Rs2){
    op2 := io.rs2
    op2Valid := io.rs2Valid
  }.elsewhen(io.op2Four){
    op2 := B"32'd4"
    op2Valid := True
  }.otherwise{
    op2Valid := False
  }

  resValid := aluValid && op1Valid && op2Valid

  switch(io.opSel){
    is(SZAluOp.Add){
      res := B(U(op1) + U(op2))
    }
    is(SZAluOp.Sub){
      res := B(U(op1) - U(op2))
    }
    is(SZAluOp.Sll){
      val shift: UInt = UInt(5 bits)
      shift := U(op2(4 downto 0))
      res := op1 |<< shift
    }
    is(SZAluOp.Slt){
      when(io.signed){
        res := (S(op1) < S(op2)).asBits(32 bits)
      }.otherwise{
        res := (U(op1) < U(op2)).asBits(32 bits)
      }
    }
    is(SZAluOp.Xor){
      res := op1 ^ op2
    }
    is(SZAluOp.Srl){
      val shift: UInt = UInt(5 bits)
      shift := U(op2(4 downto 0))
      res := op1 |>> shift
    }
    is(SZAluOp.Sra){
      val shift: UInt = UInt(5 bits)
      shift := U(op2(4 downto 0))
      res := op1 >> shift
    }
    is(SZAluOp.Or){
      res := op1 | op2
    }
    is(SZAluOp.And){
      res := op1 & op2
    }
  }

  io.resValid := resValid
  io.res := res
}
