.global _reset_vector
_reset_vector: j _start
_exception_vector: j _exception_vector
_interrupt_vector: j _interrupt_vector
_system_vector: j _system_vector

_success: j _success // 测试成功，向量 0xC0000050
_failure: j _failure // 测试失败，向量 0xC0000054

_start:
2:  j 1f
4:  j _success
1:  j 3f
    j _failure
5:  j 6f
3:  j 5b
7:  j 8f
9:  j 4b
8:  j 9b
6:  j 7b
j _failure
j _failure
j _failure
j _failure
j _failure
j _failure
j _failure
j _failure
j _failure
j _failure
