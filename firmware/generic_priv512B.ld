OUTPUT_ARCH( "riscv" )
ENTRY(_start)

MEMORY
{
  extRam (rw) : ORIGIN = 0, length = 0x80000000; /* 2GB */
  priRam (rwx) : ORIGIN = 0xC0000040, length = 448; /* 512 - 16 * 4 */
}

ENTRY(_reset_vector)

SECTIONS
{
  .text.init 0xC0000040 : { *(.text.init); } =0xDEADC0DE
  .text : {
    *(.text);
    _etext = .;
   } >priRam =0xDEADC0DE
   .data : { _data = .; *(.data) } =0xDEADC0DE
  . = ALIGN(4);
  .data.string : { *(.data.string);_edata = .;} =0xDEADC0DE
  . = ALIGN(4);
  .bss : {
    _bstart = .;
    *(.bss);
    _bend = .;
  }
  _end = .;
}

