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
    - ``loadRs1`` MEM装载RS1
    - ``op1Rs1`` ALU: ``rs1 <> _``
    - ``op1Pc`` ALU: ``pc <> _``
* RS2
    - ``loadRs2`` MEM装载RS2
    - ``op2Rs2`` ALU: ``_ <> rs2``
    - ``op2Imm`` ALU: ``_ <> imm``
    - ``op2Four`` ALU: ``_ <> 4``
* 立即数 ``immI, immJ, immU, immS, immB``
    - 选择立即数格式
* ``alu`` 使用ALU
* 内存
    - ``mem`` 内存操作
    - ``store`` 如果 ``mem``， ``store`` 即储存， ``!store`` 即读取；否则忽略
* 跳转
    - ``jump`` PC跳转
    - ``link`` 回写``PC+4``至RD
    - ``branch`` 分支跳转
* 回写
    - ``writeback`` 回写至RD
    - ``wbOp1`` 回写 ``aluOp1``
    - ``wbOp2`` 回写 ``aluOp2``
    - 以上两者皆否，回写 ``aluOut``

有效信号
------

按照 ``信号 (来源)`` 的格式::

    rs1Valid (MEM)
    rs2Valid (MEM)
    immValid (IMM)

    aluOp1Valid (Combinational)
    aluOp2Valid (Combinational)
    aluOutValid (ALU)

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
设：``loadRs1 loadRs2 alu writeback``

状态 -> MEM (rs1/rs2) -> ALU (rs1 <> rs2) -> WB (rd)

OP-IMM
------
设：``loadRs1 op2Imm immI alu writeback``

状态 -> MEM (rs1) -> IMM (immI) -> ALU (rs1 <> imm) -> WB (rd)

JAL
---
设：``op1Pc op2Imm immJ alu jump link``

状态 -> IMM (immJ) -> ALU (pc + imm) -> LINK

JALR
----
设：``loadRs1 op2Imm immI alu jump link``

状态 -> MEM (rs1) -> IMM (immI) -> ALU (rs1 + imm) -> LINK

LOAD
----
设：``loadRs1 op2Imm immI alu memory writeback``

状态 -> MEM (rs1) -> IMM (immI) ALU (rs1 + imm) -> MEM (aluout) -> WB (rd)

STORE
-----
设：``loadRs1 op2Imm immS alu memory store``

状态 -> MEM (rs1) -> IMM (immS) -> ALU (rs1 + imm) -> MEM (aluout)

BRANCH
------
设：``op1Pc op2Imm immB loadRs1 loadRs2 alu branch``

状态 -> [MEM (rs1/rs2) <|> (IMM (immB) -> ALU (pc + imm))] -> ALU (rs1 <> rs2) -> BRANCH

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

ALU
---
ALU是一个单独状态机，等待输入有效，计算，然后设输出有效

IMM
---
IMM是一个单独状态机，等待立即数转换完成

MEM
---
MEM是一个单独状态机，等待内存操作完成

LINK
----
设：``op1Pc op2Four alu writeback``
清：除了``jump``

状态 -> ALU (PC + 4) -> WB (aluout)

WB
--
``jump`` -> JUMP
_ -> FETCH
