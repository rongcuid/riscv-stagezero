架构
====

RISC-V RV32E v1.9

配置
====
私有内存大小：512/1K/2K/4K/8K/16K/32K/64K

架构
====
物理地址空间：可执行内存；虚拟空间：0xC0000000 + 以下数字
``0x0`` -- 配置
``0x1 ~ 0x3F`` -- 寄存器x1 -- x15
``0x40`` -- 复位向量
``0x44`` -- 异常向量
``0x48`` -- 中断向量
``0x4C`` -- 系统向量
``0x50`` ~ 0x1FF -- 内存（512）（``.text + .bss + .data + heap + stack``）
``0x200 ~ end`` -- 非法内存（512）

配置寄存器 （``0xC0000000``）
========
物理内存大小：2^[2:0] * 512字节

流程
====

控制信号
------

* RS1
    - ``op1Rs1``
    - ``op1Pc``
* RS2
    - ``op2Rs2``
    - ``op2ImmI``
    - ``op2ImmJ``
    - ``op2ImmS``
    - ``op2ImmB``
    - ``op2ImmU``
    - ``op2Four``
* alu
* 内存
    - ``mem``
    - ``store``
* 跳转
    - ``jump``
    - ``link``
    - ``branch``
* ``writeback``

MMU
---

16位宽，分半字读写

操作码解码
--------

清空所有控制信号。

解码``inst[6:0]``并且跳转至相应二级解码状态::

    0b0000011 -> LOAD
    0b0100011 -> STORE
    0b0001111 -> MISC-MEM

    0b1100011 -> BRANCH
    0b1100111 -> JALR
    0b1101111 -> JAL

    0b1110011 -> SYSTEM

    0b0010011 -> OP-IMM
    0b0110011 -> OP
    
    0b0010111 -> AUIPC
    0b0110111 -> LUI

    _ -> ILLEGAL INSTRUCTION

OP
--
设：``op1Rs1 op2Rs1 alu writeback``
状态 -> MEM (rs1/rs2) -> ALU (rs1 <> rs2) -> WB (rd)

OP-IMM
------
设：``op1Rs1 op2ImmI alu writeback``
状态 -> MEM (rs1) -> ALU (rs1 <> imm) -> WB (rd)

JAL
---
设：``op1Pc op2ImmJ alu jump link writeback``
状态 -> ALU (pc + imm) -> LINK

JALR
----
设：``op1Rs1 op2ImmI alu jump link wb``
状态 -> ALU (rs1 + imm) -> LINK

LOAD
----
设：``op1Rs1 op2ImmI alu memory writeback``
状态 -> MEM (rs1) -> ALU (rs1 + imm) -> MEM (aluout) -> WB (rd)

STORE
-----
设：``op1Rs1 op2ImmS alu memory store``
状态 -> MEM (rs1) -> ALU (rs1 + imm) -> MEM (aluout)

BRANCH
------
设：``op1Pc op2ImmB op1Rs1 op2Rs2 alu branch``
状态 -> MEM (rs1/rs2) | (pc + imm) -> ALU (rs1 <> rs2) -> BRANCH

LUI
---
设：``op2ImmU writeback``
状态 -> WB (op2)

AUIPC
-----
设：``op1Pc writeback``
状态 -> WB (op1)

SYSTEM
------
跳转系统向量

MISC-MEM
--------
NOP

执行状态
======

ALU
---

MEM
---

LINK
----

WB
--
