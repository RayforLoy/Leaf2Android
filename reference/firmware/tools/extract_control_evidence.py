"""Reproduce bounded PPC disassembly for the retained Leaf 5.041 image.

Reads bytes only; never loads or executes vendor code. Capstone 5 is required.
Virtual addresses are mapped to the original UPD with VA - 0x74000 + 0x128.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
from pathlib import Path

import capstone


EXPECTED_SHA256 = "80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6"
BASE = 0x74000
HEADER = 0x128
RANGES = {
    "pda-message-catalog": [(0x1026A4, 0x102920)],
    "get-picture-params": [(0x87ADC, 0x88DC0)],
    "set-picture-params": [(0x8931C, 0x89BF4)],
    "marking-storage": [(0x1C7DB0, 0x1C7F18)],
    "set-camera-params": [(0x99754, 0x9BDDC)],
    "get-camera-params": [(0xE3A68, 0xE51AC)],
    "sync-camera-params": [(0x8A1B4, 0x8D700)],
    "iso-storage": [(0x1C61D8, 0x1C6310), (0x1CBE00, 0x1CBEB0)],
    "image-settings-storage": [(0x1C558C, 0x1C5820), (0x1CBC78, 0x1CBE00)],
    "camera-settings-storage": [
        (0x1C54FC, 0x1C558C), (0x1C5E98, 0x1C6138),
        (0x1C6310, 0x1C6698), (0x1C82EC, 0x1C8574),
        (0x1CE4D4, 0x1CE6F4),
    ],
    "pin-storage": [(0x359BC0, 0x359C20), (0x364764, 0x3648DC)],
    "brightness-driver": [(0x95EE8, 0x96010)],
    "interface-selectors": [(0xA11B0, 0xA1258)],
}
TABLES = {
    "sync_fields": (0x456E74, 46),
    "picture_fields": (0x456620, 25),
    "compression_labels": (0x458FB0, 5),
    "orientation_labels": (0x6313CC, 5),
}


def ascii_string(payload: bytes, address: int) -> str | None:
    offset = address - BASE
    if not 0 <= offset < len(payload):
        return None
    end = payload.find(b"\0", offset, offset + 512)
    if end < 0:
        return None
    raw = payload[offset:end]
    if len(raw) < 2 or not all(value in (9, 10, 13) or 32 <= value < 127 for value in raw):
        return None
    return raw.decode("ascii")


def resolved_string(payload: bytes, address: int) -> tuple[int, str] | None:
    offset = address - BASE
    word = struct.unpack_from(">I", payload, offset)[0]
    opcode, source_register = word >> 26, (word >> 16) & 31
    immediate = word & 0xFFFF
    if immediate & 0x8000:
        immediate -= 0x10000
    if opcode != 14:
        return None
    if source_register in (2, 13):
        value = ({2: 0x45B428, 13: 0x638B70}[source_register] + immediate) & 0xFFFFFFFF
    elif offset >= 4:
        previous = struct.unpack_from(">I", payload, offset - 4)[0]
        if not (previous >> 26 == 15 and ((previous >> 16) & 31) == 0
                and ((previous >> 21) & 31) == source_register):
            return None
        value = (((previous & 0xFFFF) << 16) + immediate) & 0xFFFFFFFF
    else:
        return None
    text = ascii_string(payload, value)
    return (value, text) if text else None


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--firmware", type=Path,
                        default=root / "originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD")
    parser.add_argument("--output", type=Path, default=root / "analysis/controls-5.041")
    args = parser.parse_args()
    original = args.firmware.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    if digest != EXPECTED_SHA256:
        raise ValueError("This evidence map applies only to the SHA-256 pinned 5.041 image")
    if original[:8] != b"LEAF FRM" or struct.unpack_from(">I", original, 0x14)[0] != 5041:
        raise ValueError("Unexpected firmware wrapper or version")
    payload = original[HEADER:]
    decoder = capstone.Cs(capstone.CS_ARCH_PPC, capstone.CS_MODE_32 | capstone.CS_MODE_BIG_ENDIAN)
    decoder.skipdata = True
    args.output.mkdir(parents=True, exist_ok=True)
    metadata = {
        "schema_version": 1,
        "source": "originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD",
        "source_sha256": digest,
        "source_size": len(original),
        "firmware_version": "5.041",
        "architecture": "PowerPC 32-bit big-endian",
        "capstone_version": capstone.__version__,
        "address_mapping": "UPD file offset = virtual address - 0x74000 + 0x128",
        "scope": "Static evidence only; no hardware or Bluetooth session was run",
        "disassembly": [],
        "tables": {},
    }
    for name, ranges in RANGES.items():
        lines, references = [], []
        for start, end in ranges:
            lines.append(f"# VA 0x{start:08x}..0x{end:08x} (exclusive)")
            for instruction in decoder.disasm(payload[start - BASE:end - BASE], start):
                resolved = resolved_string(payload, instruction.address)
                note = ""
                if resolved:
                    address, value = resolved
                    note = f" ; ASCII@0x{address:08x} {value!r}"
                    references.append({"instruction_va": instruction.address,
                                       "string_va": address, "text": value})
                lines.append(f"{instruction.address:08x} [UPD {instruction.address - BASE + HEADER:08x}] "
                             f"{instruction.mnemonic:9} {instruction.op_str}{note}")
        target = args.output / (name + ".ppc.txt")
        target.write_text("\n".join(lines) + "\n", encoding="utf-8")
        metadata["disassembly"].append({"file": target.name, "ranges": ranges,
                                         "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                                         "resolved_ascii_references": references})
    for name, (address, count) in TABLES.items():
        entries = []
        for index in range(count):
            value = struct.unpack_from(">I", payload, address - BASE + index * 4)[0]
            text = ascii_string(payload, value)
            if text is None:
                raise ValueError(f"Unresolved expected field table {name}[{index}]")
            entries.append({"index": index, "pointer_va": address + index * 4,
                            "string_va": value, "field": text})
        metadata["tables"][name] = entries
    target = args.output / "extraction.json"
    target.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"firmware_sha256": digest, "disassembly_files": len(RANGES),
                      "table_fields": sum(count for _, count in TABLES.values())}, indent=2))


if __name__ == "__main__":
    main()
