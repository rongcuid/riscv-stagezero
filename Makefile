RISCV_PREFIX   ?= riscv32-unknown-elf-
RISCV_GCC      ?= $(RISCV_PREFIX)gcc
RISCV_OBJDUMP  ?= $(RISCV_PREFIX)objdump
RISCV_OBJCOPY  ?= $(RISCV_PREFIX)objcopy
RISCV_GCC_OPTS ?= -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -Wl,--build-id=none

all: compile-subarch-tests

SUBARCH_SRC=
SUBARCH_SRC += tests/00-JAL-JALR-OPIMM.S

SUBARCH_ELF = $(SUBARCH_SRC:.S=.elf)
SUBARCH_BIN = $(SUBARCH_SRC:.S=.bin)
SUBARCH_DISASM = $(SUBARCH_SRC:.S=.disasm)

.PHONY: subarch-tests clean
.PRECIOUS: %.bin %.elf %.hex %.disasm

compile-subarch-tests: $(SUBARCH_BIN) $(SUBARCH_DISASM)

tests/%.bin: tests/%.elf
    $(RISCV_OBJCOPY) -O binary $^ $@

tests/%.disasm: tests/%.elf
    $(RISCV_OBJDUMP) -D $^ > $@

tests/%.elf: tests/%.S
    $(RISCV_GCC) $(RISCV_GCC_OPTS) \
        -Itests/ \
        -Ifirmware/ \
        -Tfirmware/generic_priv512B.ld \
        -o $@

clean:
    rm -fv tests/*.{bin,elf,hex,disasm,o}
