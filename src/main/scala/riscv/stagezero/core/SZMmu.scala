package riscv.stagezero.core

import spinal.core._
import spinal.lib.fsm._

case class SZMmuIO(privAddrWidth: Int) extends Bundle {
  /**
    * CPU状态机的接口
    */
  val ready: Bool = out Bool
  val vAddr: UInt = in UInt(32 bits)
  val vAddrValid: Bool = in Bool

  val store: Bool = in Bool
  val word: Bool = in Bool
  val halfWord: Bool = in Bool
  val byte: Bool = in Bool

  val memOut: Bits = out Bits(32 bits)
  val memOutValid: Bool = out Bool

  /**
    * 私有内存接口
    */
  val priMemAddr: UInt = out UInt(privAddrWidth bits)
  val priMemValid: Bool = out Bool
  val priMemRData: Bits = in Bits(16 bits)
  val priMemWData: Bits = out Bits(16 bits)
  val priMemWStrb: Bits = out Bits(2 bits)
}

/**
  * 内存管理单元
  * @param privAddrWidth 私有内存的字地址宽度
  */
case class SZMmu(privAddrWidth: Int) extends Component {
  val io: SZMmuIO = SZMmuIO(privAddrWidth)
  // TODO
}
