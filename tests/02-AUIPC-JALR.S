.global _reset_vector
_reset_vector: j _start
_exception_vector: j _exception_vector
_interrupt_vector: j _interrupt_vector
_system_vector: j _system_vector

_success: j _success // 测试成功，向量 0xC0000050
_failure: j _failure // 测试失败，向量 0xC0000054

_start:
	auipc x1, 0 // 0xC0000058
	addi x1, x1, -8
	jr x1    // j _success
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
