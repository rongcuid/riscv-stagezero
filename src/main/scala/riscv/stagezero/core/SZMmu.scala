package riscv.stagezero.core

import spinal.core._
import spinal.lib.fsm._

object MmuOpWidth extends SpinalEnum(defaultEncoding=binaryOneHot) {
  val word, halfword, byte = newElement()
}

case class SZMmuIO(privAddrWidth: Int) extends Bundle {
  /**
    * CPU状态机的接口
    */
  val ready: Bool = out Bool
  val vAddr: UInt = in UInt(32 bits) // 必须对齐
  val vAddrValid: Bool = in Bool
  val wData: Bits = in Bits(32 bits)

  val signed: Bool = in Bool
  val store: Bool = in Bool
  val width: SpinalEnumCraft[MmuOpWidth.type] = in(MmuOpWidth)

  val memOut: Bits = out Bits(32 bits)
  val memOutValid: Bool = out Bool
  val accessError: Bool = out Bool

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

  val accessError = Reg(Bool) init False
  val outValid = Reg(Bool) init False

  val priPAddr = Reg(UInt(privAddrWidth bits))
  val priMemValid = Reg(Bool) init False
  val priMemWStrb = Reg(Bits(2 bits)) init B"00"
  val priMemWData = Reg(Bits(16 bits))

  val wData = Reg(Bits(32 bits))
  val memOut = Reg(Bits(32 bits))

  io.ready := fsmMmu.isActive(fsmMmu.ready)

  io.memOutValid := outValid
  io.memOut := memOut

  io.priMemAddr := priPAddr
  io.priMemValid := priMemValid
  io.priMemWData := priMemWData
  io.priMemWStrb := priMemWStrb

  val fsmMmu = new StateMachine {
    val signed = Reg(Bool)
    val store = Reg(Bool)
    val width = Reg(MmuOpWidth)

    val vAddr = UInt(32 bits)

    val ready = new State with EntryPoint {
      when(io.vAddrValid) {
        vAddr := io.vAddr
        wData := io.wData

        store := io.store
        width := io.width
        signed := io.signed

        accessError := False
        outValid := False

        goto(mmap)
      }
    }
    val mmap = new State {
      /**
        * 私有内存数据移动，单独无副作用
        */
      // WStrb写入遮罩
      when(store){
        when(width === MmuOpWidth.word || width === MmuOpWidth.halfword){
          priMemWStrb := B"11"
        }.otherwise{
          // 低字节 -> 0b01; 高字节 -> 0b10
          priMemWStrb := B(1 -> vAddr(0), 0 -> !vAddr(0))
        }
      }.otherwise{
        priMemWStrb := B"00"
      }
      // 写入数据移动
      when(width === MmuOpWidth.byte && vAddr(1)){
        // 字节3或者字节1时，写入高字节
        priMemWData(15 downto 8) := wData(7 downto 0)
      }.otherwise{
        // 所有其他情况都是低字节
        priMemWData := wData(15 downto 0)
      }
      // 物理地址
      priPAddr := vAddr(privAddrWidth downto 1)

      /**
        * 地址映射以及副作用
        */
      switch(vAddr(31 downto 30)) {
        is(U"10"){ // 0x80XXXXXX
          // TODO
        }
        is(U"11"){ // 0xC0XXXXXX 私有内存
          // 合法性判断与状态转移
          when(vAddr(29 downto privAddrWidth+1).orR){
            // 越界
            accessError := True
            goto(ready)
          }.elsewhen(width === MmuOpWidth.word){
            // 字操作；按照低半字->高半字的顺序进行
            priMemValid := True
            goto(privL)
          }.elsewhen(vAddr(1)){
            // 非全字操作，位于高半字
            priMemValid := True
            goto(privH)
          }.elsewhen(!vAddr(1)){
            // 非全字操作，位于低半字
            priMemValid := True
            goto(privL)
          }
        }
        default{ // 0x0XXXXXXX 主内存
          // TODO
        }
      }
    }

    val privL = new State {
      // 高半字写数据。只对全字有效，但是垃圾没有副作用
      priMemWData := wData(31 downto 16)

      when(width === MmuOpWidth.word){
        // 全字操作接下来需要设高位地址
        priPAddr(0) := True
        goto(privH)
      }.otherwise{
        // 非全字操作无论如何，下一周期无需操作
        priMemValid := False
        when(store){
          // 存储操作不需要读取内容
          goto(ready)
        }.otherwise {
          // 读取操作只需读取半字
          goto(loadPriv)
        }
      }
    }

    val privH = new State {
      // 只有读全字的时候才有有效数据，否则刚刚开始读取。但是此处垃圾无害，因为会覆盖掉
      memOut(15 downto 0) := io.priMemRData
      // 无论如何，下一周期无需操作
      priMemValid := False
      when(store){
        // 存储操作无需读取内容
        goto(ready)
      }.otherwise {
        // 读取操作只需读取半字
        goto(loadPriv)
      }
    }

    val loadPriv = new State {
      when(width === MmuOpWidth.word){
        // 全字读高半字
        memOut(31 downto 16) := io.priMemRData
      }.elsewhen(width === MmuOpWidth.halfword){
        // 半字读至低半字并且延伸至32位
        when(signed){
          memOut := B(S(io.priMemRData, 32 bits))
        }.otherwise{
          memOut := B(U(io.priMemRData, 32 bits))
        }
      }.elsewhen(vAddr(0)){
        // 字节1读至低字节并且延伸至32位
        when(signed){
          memOut := B(S(io.priMemRData(15 downto 8), 32 bits))
        }.otherwise{
          memOut := B(U(io.priMemRData(15 downto 8), 32 bits))
        }
      }.otherwise{
        // 字节0读至低字节并且延伸至32位
        when(signed){
          memOut := B(S(io.priMemRData(7 downto 0), 32 bits))
        }.otherwise{
          memOut := B(U(io.priMemRData(7 downto 0), 32 bits))
        }
      }
      outValid := True
      goto(ready)
    }
  }
}
