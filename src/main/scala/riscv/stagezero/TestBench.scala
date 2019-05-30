package riscv.stagezero

import java.io.{BufferedInputStream, File, FileInputStream}

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.misc.HexTools

import StageZero._

object TestBench {
  def runProgram(fileName: String): Unit = {
    val priMem: Mem[Bits] = Mem(Bits(16 bits), 256)
    HexTools.initRam(priMem, fileName, 0)

    SimConfig.withWave.compile(StageZero(priMem)).doSim{ dut =>
      dut.clockDomain.forkStimulus(period = 10)
    }
  }
  def main(args: Array[String]): Unit = {
  }
}
