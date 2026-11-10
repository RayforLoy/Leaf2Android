"""Disassemble a bounded ARM32 PE range and annotate PC-relative literal loads."""

from __future__ import annotations

import argparse
from pathlib import Path
import struct
import sys

from capstone import CS_ARCH_ARM, CS_MODE_ARM, CS_MODE_LITTLE_ENDIAN, Cs
from capstone.arm import ARM_OP_MEM, ARM_REG_PC
import pefile

from pe_research import arm_functions, literal_references


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    targets = parser.add_mutually_exclusive_group(required=True)
    targets.add_argument("--va", type=lambda value: int(value, 0))
    targets.add_argument("--xref", type=lambda value: int(value, 0), help="Find literal references to a VA")
    parser.add_argument("--bytes", default=256, type=lambda value: int(value, 0))
    args = parser.parse_args()
    if args.bytes <= 0 or args.bytes > 65536:
        parser.error("--bytes must be between 1 and 65536")
    pe = pefile.PE(str(args.binary))
    if pe.FILE_HEADER.Machine != 0x1C0:
        parser.error("This tool expects a little-endian ARM32 Windows CE image")
    base = pe.OPTIONAL_HEADER.ImageBase
    if args.xref is not None:
        functions = arm_functions(pe)
        for reference in literal_references(pe, args.xref):
            function = next((f for f in functions if f[0] <= reference < f[1]), None)
            print(f"reference={reference:#x} function={hex(function[0]) if function else '?'} end={hex(function[1]) if function else '?'}")
        pe.close()
        return
    code = pe.get_data(args.va - base, args.bytes)
    disassembler = Cs(CS_ARCH_ARM, CS_MODE_ARM | CS_MODE_LITTLE_ENDIAN)
    disassembler.detail = True
    disassembler.skipdata = True
    for instruction in disassembler.disasm(code, args.va):
        annotation = ""
        if instruction.id and instruction.mnemonic.startswith("ldr"):
            for operand in instruction.operands:
                if operand.type == ARM_OP_MEM and operand.mem.base == ARM_REG_PC:
                    address = instruction.address + 8 + operand.mem.disp
                    literal = pe.get_data(address - base, 4)
                    if len(literal) == 4:
                        value = struct.unpack("<I", literal)[0]
                        annotation = f" ; [{address:#x}] = {value:#x}"
                        if base <= value < base + pe.OPTIONAL_HEADER.SizeOfImage:
                            raw = pe.get_data(value - base, 120)
                            if len(raw) > 3 and raw[1] == 0:
                                text = raw.decode("utf-16le", "replace").split("\0", 1)[0]
                            else:
                                text = raw.split(b"\0", 1)[0].decode("ascii", "replace")
                            if text and text.isascii() and all(c.isprintable() for c in text):
                                annotation += f" {text!r}"
        print(f"{instruction.address:08x}: {instruction.mnemonic:8} {instruction.op_str}{annotation}")
    pe.close()


if __name__ == "__main__":
    main()
