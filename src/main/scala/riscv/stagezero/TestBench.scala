package riscv.stagezero

import java.io.{BufferedInputStream, File, FileInputStream}

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.misc.HexTools

import StageZero._

object TestBench {
  val MAX_HANG_COUNT = 512
  val PC_SUCCESS: BigInt = 0xC0000050L

  def runProgram(fileName: String): Unit = {
    val compiled = SimConfig.withVcdWave.compile{
      val dut = StageZero(512, fileName)
      dut.pc.simPublic()
      dut.inst.simPublic()
      dut
    }

    compiled.doSim{ dut =>
      dut.clockDomain.forkStimulus(period = 10)
      SimTimeout(1000000 * 10)

      var prev_pc: BigInt = 0xC0000040L
      var hangCount = 0
      for (i <- 0 until 1000000) {
        dut.clockDomain.waitSampling()
        val pc = dut.pc.toBigInt
        val inst = dut.inst.toBigInt
        if (dut.dDecode.toBoolean) {
          println(f"(TB) PC = 0x$pc%08x, inst = 0x$inst%08x")
        }
        /**
          * 如果跳转至成功向量，则结束并报告成功
         */
        if (pc == PC_SUCCESS) {
          println(f"(II) Success! PC = 0x$pc%08x")
          simSuccess()
        }

        /**
          * 检测挂起
          */
        if (prev_pc == pc) {
          hangCount += 1
        }
        else {
          hangCount = 0
        }

        /**
          * 发现挂起，结束并且报告失败
          */
        if (hangCount >= MAX_HANG_COUNT) {
          println("(WW) Failure! CPU hangs")
          simFailure()
        }
        prev_pc = pc
      }
    }

  }

  def main(args: Array[String]): Unit = {
    args.foreach(runProgram)
  }
}
