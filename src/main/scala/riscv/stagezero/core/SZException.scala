package riscv.stagezero.core

import spinal.core._

object SZException extends SpinalEnum(defaultEncoding = binarySequential) {
  val InstructionAddressMisaligned
    , InstructionAccessFault
    , IllegalInstruction
    , LoadAddressMisaligned
    , LoadAccessFault
    , StoreAddressMisaligned
    , StoreAccessFault
    , ECall
    , TimerInterrupt = newElement()
}
