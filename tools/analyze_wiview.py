"""Inspect a Windows CE CAB and its ARM PE files without loading their code."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys

import pefile


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CAB = ROOT / "reference/wiview/1.0.2/LeafWiView-1.0.2.CAB"
DEFAULT_OUTPUT = ROOT / "reference/analysis/wiview-1.0.2"


def cab_members(data: bytes) -> list[dict]:
    if len(data) < 36 or data[:4] != b"MSCF":
        raise ValueError("Not a Microsoft CAB")
    cabinet_size = struct.unpack_from("<I", data, 8)[0]
    offset = struct.unpack_from("<I", data, 16)[0]
    count = struct.unpack_from("<H", data, 28)[0]
    if cabinet_size != len(data) or not 36 <= offset < len(data) or count > 256:
        raise ValueError("Invalid or oversized CAB directory")
    entries = []
    for _ in range(count):
        if offset + 16 > len(data):
            raise ValueError("Truncated CAB file entry")
        size, folder_offset, folder, _, _, attributes = struct.unpack_from("<IIHHHH", data, offset)
        end = data.find(b"\0", offset + 16)
        if end < 0 or end - offset > 1024:
            raise ValueError("Invalid CAB member name")
        name = data[offset + 16 : end].decode("utf-8" if attributes & 0x80 else "ascii")
        # This release is a flat cabinet. Refuse paths before invoking expand.exe.
        if not re.fullmatch(r"[A-Za-z0-9_~. -]+", name) or name in (".", ".."):
            raise ValueError(f"Unexpected CAB member name: {name!r}")
        entries.append({"cab_name": name, "size": size, "folder": folder, "folder_offset": folder_offset})
        offset = end + 1
    if len({entry["cab_name"].casefold() for entry in entries}) != len(entries):
        raise ValueError("Duplicate CAB member names")
    return entries


def string_records(data: bytes, pe: pefile.PE) -> list[dict]:
    records = []
    for section in pe.sections:
        # Executable code and bitmap resources contain many accidental printable runs.
        if section.Characteristics & 0x20000000 or section.Name.rstrip(b"\0") in (b".rsrc", b".reloc", b".pdata"):
            continue
        start = section.PointerToRawData
        raw = data[start : start + section.SizeOfRawData]
        for encoding, pattern in (
            ("ascii", rb"[\x20-\x7e]{4,}"),
            ("utf-16le", rb"(?:[\x20-\x7e]\x00){4,}"),
        ):
            for match in re.finditer(pattern, raw):
                offset = start + match.start()
                records.append({
                    "offset": f"0x{offset:x}",
                    "va": f"0x{pe.OPTIONAL_HEADER.ImageBase + section.VirtualAddress + match.start():x}",
                    "encoding": encoding,
                    "text": match.group().decode(encoding),
                })
    return sorted(records, key=lambda item: (int(item["offset"], 16), item["encoding"]))


def inspect_pe(path: Path) -> tuple[dict, list[dict]]:
    data = path.read_bytes()
    pe = pefile.PE(data=data)
    imports = {}
    for module in getattr(pe, "DIRECTORY_ENTRY_IMPORT", ()):
        imports[module.dll.decode("ascii", errors="replace")] = [
            {"name": entry.name.decode("ascii", errors="replace") if entry.name else None,
             "ordinal": entry.ordinal, "iat_va": f"0x{entry.address:x}"}
            for entry in module.imports
        ]
    exports = [
        {"name": entry.name.decode("ascii", errors="replace") if entry.name else None,
         "ordinal": entry.ordinal, "rva": f"0x{entry.address:x}"}
        for entry in getattr(getattr(pe, "DIRECTORY_ENTRY_EXPORT", None), "symbols", ())
    ]
    versions = {}
    for group in getattr(pe, "FileInfo", ()):
        for block in group:
            for table in getattr(block, "StringTable", ()):
                versions.update({k.decode("utf-8", "replace"): v.decode("utf-8", "replace") for k, v in table.entries.items()})
    export_directory = getattr(pe, "DIRECTORY_ENTRY_EXPORT", None)
    module_name = pe.get_string_at_rva(export_directory.struct.Name).decode("ascii", "replace") if export_directory else None
    timestamp_offset = export_directory.struct.get_field_absolute_offset("TimeDateStamp") if export_directory else None
    sections = []
    for section in pe.sections:
        raw = section.get_data()
        normalized = bytearray(raw)
        if timestamp_offset is not None:
            relative = timestamp_offset - section.PointerToRawData
            if 0 <= relative <= len(raw) - 4:
                normalized[relative : relative + 4] = b"\0" * 4
        sections.append({
            "name": section.Name.rstrip(b"\0").decode("ascii"),
            "rva": f"0x{section.VirtualAddress:x}", "offset": f"0x{section.PointerToRawData:x}",
            "raw_size": section.SizeOfRawData, "sha256": hashlib.sha256(raw).hexdigest(),
            "sha256_without_export_timestamp": hashlib.sha256(normalized).hexdigest(),
            "executable": bool(section.Characteristics & 0x20000000),
        })
    result = {
        "cab_name": path.name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
        "machine": f"0x{pe.FILE_HEADER.Machine:x}", "image_base": f"0x{pe.OPTIONAL_HEADER.ImageBase:x}",
        "entry_rva": f"0x{pe.OPTIONAL_HEADER.AddressOfEntryPoint:x}",
        "module_name": module_name, "versions": versions, "imports": imports, "exports": exports,
        "file_timestamp": pe.FILE_HEADER.TimeDateStamp,
        "export_timestamp_offset": f"0x{timestamp_offset:x}" if timestamp_offset is not None else None,
        "sections": sections,
    }
    strings = string_records(data, pe)
    pe.close()
    return result, strings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cab", type=Path, default=DEFAULT_CAB)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    cabinet = args.cab.resolve()
    output = args.output.resolve()
    members = cab_members(cabinet.read_bytes())
    extracted = output / "extracted"
    extracted.mkdir(parents=True, exist_ok=True)
    if sys.platform != "win32":
        parser.error("Extraction uses the Windows expand.exe utility")
    subprocess.run([str(Path(__import__("os").environ["SystemRoot"]) / "System32/expand.exe"),
                    "-F:*", str(cabinet), str(extracted)], check=True, capture_output=True)
    binaries = []
    for member in members:
        path = extracted / member["cab_name"]
        if path.stat().st_size != member["size"]:
            raise ValueError(f"Extracted size mismatch: {path.name}")
        if path.read_bytes()[:2] != b"MZ":
            continue
        binary, strings = inspect_pe(path)
        binaries.append(binary)
        (output / (path.name + ".strings.json")).write_text(json.dumps(strings, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {"cab_sha256": hashlib.sha256(cabinet.read_bytes()).hexdigest(), "members": members, "binaries": binaries}
    (output / "binary-analysis.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Inspected {len(members)} CAB members and {len(binaries)} PE files: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

