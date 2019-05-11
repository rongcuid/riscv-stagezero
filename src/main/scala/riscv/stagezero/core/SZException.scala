package riscv.stagezero.core

import spinal.core._

object SZException extends SpinalEnum {
  val InstructionAddressMisaligned
    , InstructionAccessFault
    , IllegalInstruction
    , LoadAddressMisaligned
    , LoadAccessFault
    , StoreAddressMisaligned
    , StoreAccessFault
    , ECall
    , TimerInterrupt = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding") {
    InstructionAddressMisaligned -> 0
    InstructionAccessFault -> 1
    IllegalInstruction -> 2
    LoadAddressMisaligned -> 4
    LoadAccessFault -> 5
    StoreAddressMisaligned -> 6
    StoreAccessFault -> 7
    ECall -> 11
    TimerInterrupt -> (0x80000000 + 7)
  }
}
