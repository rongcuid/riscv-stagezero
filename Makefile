RISCV_PREFIX   ?= riscv32-unknown-elf-
RISCV_GCC      ?= $(RISCV_PREFIX)gcc
RISCV_OBJDUMP  ?= $(RISCV_PREFIX)objdump
RISCV_OBJCOPY  ?= $(RISCV_PREFIX)objcopy
RISCV_GCC_OPTS ?= -march=rv32e -mabi=ilp32e -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -Wl,--build-id=none

all: compile-subarch-tests

SUBARCH_SRC=
SUBARCH_SRC += tests/00-JAL-NOP.S
SUBARCH_SRC += tests/01-JAL.S

SUBARCH_ELF = $(SUBARCH_SRC:.S=.elf)
SUBARCH_HEX = $(SUBARCH_SRC:.S=.hex)
SUBARCH_DISASM = $(SUBARCH_SRC:.S=.disasm)

.PHONY: subarch-tests clean
.PRECIOUS: tests/%.bin tests/%.elf tests/%.hex tests/%.disasm

compile-subarch-tests: $(SUBARCH_HEX) $(SUBARCH_DISASM)

tests/%.hex: tests/%.elf
	#hexdump -v -e '1/4 "%08x " "\n"' $^ > $@
	$(RISCV_OBJCOPY) -O ihex $^ $@

tests/%.bin: tests/%.elf
	$(RISCV_OBJCOPY) -O binary $^ $@

tests/%.disasm: tests/%.elf
	$(RISCV_OBJDUMP) -S -D $^ > $@

tests/%.elf: tests/%.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) \
		-Itests/ \
		-Ifirmware/ \
		-Tfirmware/generic_priv512B.ld \
		$^ \
		-o $@

clean:
	rm -fv tests/*.bin tests/*.elf tests/*.hex tests/*.disasm tests/*.o
