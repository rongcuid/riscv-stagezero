.global _reset_vector
_reset_vector: j _start
_exception_vector: j _exception_vector
_interrupt_vector: j _interrupt_vector
_system_vector: j _system_vector

_success: j _success // 测试成功，向量 0xC0000050
_failure: j _failure // 测试失败，向量 0xC0000054

_start:
li x2, 0xC0000074
jr x2
j _failure
j _failure
j _failure
j _failure
li x1, 0xC0000050
jr x1
j _failure
j _failure
j _failure
j _failure
j _failure
j _failure
j _failure
