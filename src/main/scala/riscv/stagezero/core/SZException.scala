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
  defaultEncoding = SpinalEnumEncoding("staticEncoding") (
    InstructionAddressMisaligned -> 0
    , InstructionAccessFault -> 1
    , IllegalInstruction -> 2
    , LoadAddressMisaligned -> 4
    , LoadAccessFault -> 5
    , StoreAddressMisaligned -> 6
    , StoreAccessFault -> 7
    , ECall -> 11
    // 注意：这是长整型字面量！Scala没有无符号字面量，因此若非长整型则会识别为负数并且报错“宽度不匹配”
    , TimerInterrupt -> 0x80000007L
  )
}
