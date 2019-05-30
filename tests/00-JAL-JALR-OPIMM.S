_reset_vector: j _start
_exception_vector: j _exception_handler_l1
_interrupt_vector: j _interrupt_handler_l1
_system_vector: j _system_handler_l1

_start:
    nop
    nop
    nop
    j _start

_exception_handler_l1: j _exception_handler_l1
_interrupt_handler_l1: j _interrupt_handler_l1
_system_handler_l1: j _system_handler_l1
