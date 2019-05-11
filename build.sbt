name := "riscv-stagezero"

version := "1.0"

scalaVersion := "2.12.8"

EclipseKeys.withSource := true

libraryDependencies ++= Seq(
  "com.github.spinalhdl" % "spinalhdl-core_2.12" % "1.3.3",
  "com.github.spinalhdl" % "spinalhdl-lib_2.12" % "1.3.3"
)

fork := true
