"""Small static-analysis helpers for the ARM Windows CE samples in this project."""

from __future__ import annotations

import struct

import pefile


def arm_functions(pe: pefile.PE) -> list[tuple[int, int]]:
    """Read CE .pdata entries: begin VA, 8-bit prologue, 22-bit instruction count.

    Bit 30 selects 32-bit ARM instructions rather than 16-bit Thumb instructions.
    Entries are accepted only when the full function lies in an executable section.
    """
    base = pe.OPTIONAL_HEADER.ImageBase
    executable = [(base + s.VirtualAddress, base + s.VirtualAddress + s.Misc_VirtualSize)
                  for s in pe.sections if s.Characteristics & 0x20000000]
    functions = []
    for section in pe.sections:
        if section.Name.rstrip(b"\0") != b".pdata":
            continue
        data = section.get_data()
        for offset in range(0, len(data) - 7, 8):
            begin, packed = struct.unpack_from("<II", data, offset)
            width = 4 if packed & 0x40000000 else 2
            end = begin + ((packed >> 8) & 0x3FFFFF) * width
            if begin < end and any(lo <= begin < end <= hi for lo, hi in executable):
                functions.append((begin, end))
    return sorted(set(functions))


def literal_references(pe: pefile.PE, value: int) -> list[int]:
    """Find ARM32 LDR literal instructions whose pool word equals value."""
    base = pe.OPTIONAL_HEADER.ImageBase
    references = []
    for section in pe.sections:
        if not section.Characteristics & 0x20000000:
            continue
        data = section.get_data()
        for offset in range(0, len(data) - 3, 4):
            word = struct.unpack_from("<I", data, offset)[0]
            if word & 0x0F7F0000 != 0x051F0000:
                continue
            instruction = base + section.VirtualAddress + offset
            displacement = word & 0xFFF
            address = instruction + 8 + (displacement if word & 0x800000 else -displacement)
            if not base <= address <= base + pe.OPTIONAL_HEADER.SizeOfImage - 4:
                continue
            literal = pe.get_data(address - base, 4)
            if len(literal) == 4 and struct.unpack("<I", literal)[0] == value:
                references.append(instruction)
    return references
