.global _reset_vector
_reset_vector: j _start
_exception_vector: j _exception_vector
_interrupt_vector: j _interrupt_vector
_system_vector: j _system_vector

_success: j _success // 测试成功，向量 0xC0000050
_failure: j _failure // 测试失败，向量 0xC0000054

_start:
1: la x1, 2f
jr x1
j _failure
j _failure
3: la x1, 4f
jalr x0, x1, 4
j _failure
2: la x1, 3b
jr x1
4: j _failure
la x1, 5f
jalr x0, x1, -8
j _failure
j _failure
j _success
j _failure
5: j _failure
