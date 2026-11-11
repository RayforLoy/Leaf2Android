"""Static, SHA-pinned classic Aptus 4.701 evidence extraction; vendor code is never run."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import struct

import capstone
import pefile

FIRMWARE = Path(__file__).resolve().parents[2]
OUTPUT = Path(__file__).resolve().parent
BASE, HEADER = 0x74000, 0x128
SOURCE = "originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD"
PIN = "a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a"
CATALOG = [
    ("ReSyncRequest", 0xE644C, 0xA78F8, 0xA7AB0, False),
    ("GetImage", 0xE649C, 0x90A38, 0x91830, True),
    ("GetPictureParams", 0xE64D4, 0x8261C, 0x838A0, True),
    ("FileCommand", 0xE650C, 0xA4B04, 0xA634C, True),
    ("ImageFunction", 0xE6544, 0xA7B88, 0xA898C, True),
    ("SetPictureParams", 0xE657C, 0x83EEC, 0x84810, True),
    ("RequestSync", 0xE65B0, 0x84EA0, 0x877A4, False),
    ("SetCameraParam", 0xE65E0, 0x9236C, 0x942D0, False),
    ("GetCameraParam", 0xE6610, 0xD31B0, 0xD48E8, False),
]
VTABLES = {
    "ReSyncRequest": 0x3D286C, "GetImage": 0x3CE0D0,
    "GetPictureParams": 0x3CC1F8, "FileCommand": 0x3D2150,
    "ImageFunction": 0x3D29AC, "SetPictureParams": 0x3CC5EC,
    "RequestSync": 0x3CCA18, "SetCameraParam": 0x3CE544,
    "GetCameraParam": 0x3DD2F0,
}
RANGES = {
    "pda-message-catalog": [(0xE640C, 0xE6684)],
    "pxa-boot-registration": [(0xE5B00, 0xE5D70)],
    "request-sync": [(0x84EA0, 0x877B0)],
    "set-camera-params": [(0x9236C, 0x942DC)],
    "get-camera-params": [(0xD31B0, 0xD48F4)],
    "get-image": [(0x90A38, 0x9183C), (0x95230, 0x952A4)],
    "get-picture-params": [(0x8261C, 0x838AC)],
    "set-picture-params": [(0x83EEC, 0x8481C)],
    "marking-storage": [(0x1C0BD0, 0x1C0D38)],
    "shoot-setup-storage": [(0x1BE884, 0x1BEA78), (0x1BF22C, 0x1BF344)],
    "model-name-and-id-getters": [(0x210890, 0x2108F0)],
    "file-command": [(0xA4B04, 0xA6358)],
    "image-function": [(0xA7B88, 0xA8998)],
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def text_at(payload: bytes, address: int) -> str | None:
    offset = address - BASE
    if not 0 <= offset < len(payload):
        return None
    end = payload.find(b"\0", offset, offset + 512)
    raw = payload[offset:end] if end >= 0 else b""
    if len(raw) > 1 and all(value in (9, 10, 13) or 32 <= value < 127 for value in raw):
        return raw.decode("ascii")
    return None


def literal(payload: bytes, address: int) -> str | None:
    offset = address - BASE
    word = struct.unpack_from(">I", payload, offset)[0]
    if word >> 26 != 14:
        return None
    register = (word >> 16) & 31
    immediate = struct.unpack_from(">h", payload, offset + 2)[0]
    if register in (2, 13):
        value = ({2: 0x3D19D8, 13: 0x4852A0}[register] + immediate) & 0xFFFFFFFF
    elif offset >= 4:
        previous = struct.unpack_from(">I", payload, offset - 4)[0]
        if previous >> 26 != 15 or (previous >> 16) & 31 or (previous >> 21) & 31 != register:
            return None
        value = (((previous & 0xFFFF) << 16) + immediate) & 0xFFFFFFFF
    else:
        return None
    return text_at(payload, value)


def branch_target(payload: bytes, address: int) -> int:
    word = struct.unpack_from(">I", payload, address - BASE)[0]
    if word >> 26 != 18 or not word & 1:
        raise ValueError(f"Expected direct constructor call at {address:#x}")
    distance = word & 0x3FFFFFC
    if distance & 0x2000000:
        distance -= 0x4000000
    return (distance if word & 2 else address + distance) & 0xFFFFFFFF


def client_constructors(version: str) -> dict:
    path = FIRMWARE.parent / f"analysis/wiview-{version}/extracted/00LeafCE.001"
    raw = path.read_bytes()
    pe = pefile.PE(data=raw, fast_load=True)
    literals = {}
    for match in re.finditer(rb"(?:[\x20-\x7e]\x00){5,}", raw):
        value = match.group().decode("utf-16-le")
        if re.search(r"\^<[A-Za-z]", value):
            va = pe.OPTIONAL_HEADER.ImageBase + pe.get_rva_from_offset(match.start())
            literals[va] = value
    references = []
    for section in pe.sections:
        if not section.Characteristics & 0x20000000:
            continue
        data = section.get_data()
        start = pe.OPTIONAL_HEADER.ImageBase + section.VirtualAddress
        for offset in range(0, len(data) - 3, 4):
            word = struct.unpack_from("<I", data, offset)[0]
            if word & 0x0F7F0000 != 0x051F0000:
                continue
            pool = start + offset + 8 + ((word & 0xFFF) if word & 0x800000 else -(word & 0xFFF))
            try:
                pointer = struct.unpack_from("<I", raw, pe.get_offset_from_rva(pool - pe.OPTIONAL_HEADER.ImageBase))[0]
            except (pefile.PEFormatError, struct.error):
                continue
            if pointer in literals:
                references.append({"instruction_va": start + offset, "literal_va": pointer,
                                   "tag": re.search(r"\^<([A-Za-z][A-Za-z0-9_]*)", literals[pointer]).group(1),
                                   "literal": literals[pointer]})
    return {"path": path.relative_to(FIRMWARE.parent.parent).as_posix(), "sha256": sha(raw),
            "referenced_construction_tags": sorted({item["tag"] for item in references}),
            "literal_references": references,
            "scope": "ARM LDR literal references establish construction text, not successful camera effects."}


def ce_summary() -> dict:
    relative = "originals/CaptureOne.Mac.7.1.6/FirmWareAptus.BIN"
    raw = (FIRMWARE / relative).read_bytes()
    if sha(raw) != "4a0d32186d95bd0aaf0a741d161b98712e30beac595d7d5d10dabffaa86397c0":
        raise ValueError("Unexpected classic CE source")
    payload = raw[HEADER:]
    if payload[:7] != b"B000FF\n":
        raise ValueError("Missing CE load-record signature")
    start, length = struct.unpack_from("<II", payload, 7)
    offset, records = 15, []
    while offset + 12 <= len(payload):
        address, size, checksum = struct.unpack_from("<III", payload, offset)
        if address == 0:
            if checksum or offset + 12 != len(payload):
                raise ValueError("Invalid CE terminator")
            entry = size
            break
        data = payload[offset + 12:offset + 12 + size]
        if len(data) != size or sum(data) & 0xFFFFFFFF != checksum:
            raise ValueError("CE record checksum or length mismatch")
        records.append({"address": address, "size": size, "checksum": checksum})
        offset += 12 + size
    else:
        raise ValueError("Missing CE terminator")
    return {"source": relative, "source_size": len(raw), "sha256": sha(raw), "interface_version": "1.8.6",
            "wrapper_version_bytes": raw[20:24].hex(), "format": "B000FF Windows CE load records",
            "image_start": start, "image_length": length, "entry": entry,
            "record_count": len(records), "all_record_checksums_valid": True, "records": records,
            "scope": "Container/header verification only; compressed CE modules were not reconstructed in this increment."}


def main() -> None:
    raw = (FIRMWARE / SOURCE).read_bytes()
    if sha(raw) != PIN or raw[:8] != b"LEAF FRM" or struct.unpack_from(">I", raw, 20)[0] != 4701:
        raise ValueError("This evidence map requires the pinned classic 4.701 image")
    payload = raw[HEADER:]
    if struct.unpack_from(">I", raw, 12)[0] != len(payload):
        raise ValueError("UPD payload length mismatch")
    payload_name = "FirmwareAptus-4.701.payload.bin"
    (OUTPUT / payload_name).write_bytes(payload)
    decoder = capstone.Cs(capstone.CS_ARCH_PPC, capstone.CS_MODE_32 | capstone.CS_MODE_BIG_ENDIAN)
    decoder.skipdata = True
    report = {"schema_version": 1, "date": "2026-11-11", "source": SOURCE, "source_sha256": PIN,
              "source_size": len(raw), "version": "4.701", "payload_file": payload_name,
              "payload_size": len(payload), "payload_sha256": sha(payload),
              "mapping": "UPD file offset = VA - 0x74000 + 0x128; payload offset = VA - 0x74000",
              "capstone_version": capstone.__version__, "scope": "Static code/containers only; no vendor code, Bluetooth traffic or flash operation executed.",
              "catalogue": [], "disassembly": [], "classic_ce_container": ce_summary(),
              "clients": [client_constructors(version) for version in ("1.0.2", "1.0.3")]}
    for tag, call, constructor, getter, conditional in CATALOG:
        if branch_target(payload, call) != constructor or literal(payload, getter + 4) != tag:
            raise ValueError(f"Catalog verification failed: {tag}")
        vtable = VTABLES[tag]
        words = struct.unpack_from(">20I", payload, vtable - BASE)
        assigned = False
        for offset in range(constructor - BASE + 8, constructor - BASE + 0x120, 4):
            store, add, load = (struct.unpack_from(">I", payload, offset - delta)[0] for delta in (0, 4, 8))
            register = (store >> 21) & 31
            if (store >> 26 == 36 and store & 0xFFFF == 0x17C
                    and add >> 26 == 14 and (add >> 21) & 31 == register
                    and (add >> 16) & 31 == register and load >> 26 == 15
                    and (load >> 21) & 31 == register and not (load >> 16) & 31):
                immediate = struct.unpack_from(">h", payload, offset - 2)[0]
                assigned |= (((load & 0xFFFF) << 16) + immediate) & 0xFFFFFFFF == vtable
        if not assigned or getter not in words:
            raise ValueError(f"Constructor/vtable/name link failed: {tag}")
        report["catalogue"].append({"tag": tag, "pda_registration_call_va": call,
                                    "constructor_va": constructor, "name_getter_va": getter,
                                    "vtable_va": vtable,
                                    "interface_id": 1, "image_branch_conditional": conditional})
    for name, ranges in RANGES.items():
        lines = []
        for start, end in ranges:
            lines.append(f"# classic 4.701 VA {start:#x}..{end:#x}, exclusive")
            for instruction in decoder.disasm(payload[start - BASE:end - BASE], start):
                value = literal(payload, instruction.address)
                lines.append(f"{instruction.address:08x} [UPD {instruction.address - BASE + HEADER:08x}] "
                             f"{instruction.mnemonic:9} {instruction.op_str}" + (f" ; ASCII {value!r}" if value else ""))
        path = OUTPUT / (name + ".ppc.txt")
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        report["disassembly"].append({"path": path.name, "ranges": ranges, "sha256": sha(path.read_bytes())})
    report["model_association"] = {
        "level": "Compiled shared-firmware model descriptor; not a model-exclusive flashing/compatibility proof",
        "model_label_va": 0x3F5B7C, "model_label": text_at(payload, 0x3F5B7C),
        "model_name_pointer_va": 0x47DBDC, "model_name_getter_va": 0x2108CC,
        "descriptor_id_getter_va": 0x2108C4, "descriptor_id_constant": 12,
        "descriptor_vtable_entries": [0x3F59B8, 0x3F59BC],
        "iso_adjuster_rtti_va": 0x4121AC, "iso_adjuster_rtti": text_at(payload, 0x4121AC),
        "firmware_config": (FIRMWARE / "originals/CaptureOne.Mac.7.1.6/Firmware.config").read_text(encoding="ascii"),
        "config_maps_a_model": False,
    }
    if struct.unpack_from(">II", payload, 0x3F59B8 - BASE) != (0x2108C4, 0x2108CC):
        raise ValueError("Unexpected Aptus 22 model descriptor")
    if struct.unpack_from(">I", payload, 0x47DBDC - BASE)[0] != 0x3F5B7C:
        raise ValueError("Unexpected Aptus 22 model-name pointer")
    report["field_tables"] = {}
    for name, address, count in (("sync", 0x3CCA44, 33), ("picture", 0x3CC224, 24)):
        fields = []
        for index in range(count):
            pointer = struct.unpack_from(">I", payload, address - BASE + index * 4)[0]
            value = text_at(payload, pointer)
            if value is None:
                raise ValueError("Unexpected classic field table")
            fields.append({"index": index, "pointer_va": address + index * 4, "field": value})
        report["field_tables"][name] = fields
    (OUTPUT / "evidence.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"version": "4.701", "payload_size": len(payload), "catalogue_tags": len(CATALOG),
                      "disassembly_files": len(RANGES), "ce_records": report["classic_ce_container"]["record_count"],
                      "client_constructed_tags": {item["path"]: item["referenced_construction_tags"] for item in report["clients"]}}, indent=2))


if __name__ == "__main__":
    main()
