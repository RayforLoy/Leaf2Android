"""Reproduce static DP-67 analysis. Never execute the supplied installer or backups.

Only the pinned manufacturer Setup.exe, its embedded CAB, and CAB members are
retained. The user ZIP and PDA backup files are never copied to reference.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
import sys
import tempfile
import uuid
import zipfile

import pefile
from capstone import CS_ARCH_ARM, CS_MODE_ARM, CS_MODE_LITTLE_ENDIAN, Cs

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools"))
from analyze_wiview import cab_members, inspect_pe
from pe_research import arm_functions

ZIP_SHA = "c166a2bda7601c7f6dcb21bcf9f163e84894dfd00611ec681d9c2caaddb98140"
ZIP_NAME = "Leaf DP-67 app.zip"
ZIP_SIZE = 2454492
SETUP_MEMBER = "Leaf DP-67 app/Leaf DP-67 V1.2.1/Setup.exe"
SETUP_SHA = "c0b8de1e4700b27764aa4d755bde730736c649f8de5e72196daa160eaa980126"
CAB_SHA = "41d6966acb4b2cb676a6bfef3b534ce802feb37cbeaf6ac9beaac196d29d13f4"
CLIENT_SHA = "d0e67119db4a8e49a2cf8ec264143ccdf5da2d67623f835df9ace5fdf9c620b7"
FOCUS = {
    "save-shoot-setup": (0x19F08, 0x1A4A0),
    "set-image-property": (0x19CDC, 0x19F08),
    "clone-modified-slot": (0x19920, 0x19CDC),
    "light-property-caller": (0x2F7FC, 0x2F82C),
    "directory-set-camera-wrapper": (0x2AA34, 0x2AB94),
    "sync-property-parser": (0x15760, 0x17360),
    "framing-list-parser": (0x18120, 0x1840C),
    "set-camera-param": (0x279E8, 0x27CC8),
    "execute-transaction": (0x2878C, 0x28FF8),
    "receive-command": (0x24A44, 0x25128),
    "synchronize": (0x26764, 0x2691C),
    "resync": (0x2450C, 0x24928),
    "set-picture-params": (0x27708, 0x279E8),
    "image-function": (0x27F9C, 0x28270),
    "widcomm-link-timeout-setting": (0x23344, 0x23448),
}
CSV_REFS = [
    (0x19FD8, "SetupName="), (0x1A01C, ",ShootingFolder="),
    (0x1A05C, ",ISO="), (0x1A09C, ",ImageQuality="),
    (0x1A0DC, ",Framing="), (0x1A11C, ",Toning="),
    (0x1A15C, ",Light="), (0x1A19C, ",SharpTable="),
    (0x1A1DC, ",InputProfile="), (0x1A21C, ",OutputProfile="),
    (0x1A25C, ",CameraType="), (0x1A29C, ",ICCFlag="),
    (0x1A2F0, ",Prefix="),
]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_original(path: Path, data: bytes, expected: str) -> None:
    if sha(data) != expected:
        raise ValueError(f"Pinned source mismatch: {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_bytes() != data:
        raise ValueError(f"Refusing to overwrite changed original: {path}")
    path.write_bytes(data)


def word(pe: pefile.PE, address: int) -> int:
    return struct.unpack("<I", pe.get_data(address - pe.OPTIONAL_HEADER.ImageBase, 4))[0]


def literal_value(pe: pefile.PE, address: int) -> int | None:
    instruction = word(pe, address)
    if instruction & 0x0F7F0000 != 0x051F0000:
        return None
    pool = address + 8 + ((instruction & 0xFFF) if instruction & 0x800000 else -(instruction & 0xFFF))
    return word(pe, pool)


def unicode_string(pe: pefile.PE, address: int) -> str:
    raw = pe.get_data(address - pe.OPTIONAL_HEADER.ImageBase, 1024)
    end = next((offset for offset in range(0, len(raw) - 1, 2) if raw[offset:offset + 2] == b"\0\0"), len(raw))
    return raw[:end].decode("utf-16-le")


def branch_target(pe: pefile.PE, address: int) -> int:
    instruction = word(pe, address)
    if instruction & 0x0F000000 != 0x0B000000:
        raise ValueError(f"Expected ARM BL at {address:#x}")
    displacement = instruction & 0xFFFFFF
    if displacement & 0x800000:
        displacement -= 0x1000000
    return (address + 8 + displacement * 4) & 0xFFFFFFFF


def verify(zip_path: Path | None) -> None:
    """Read-only validation, including fresh derivation from the retained installer."""
    inventory = json.loads((HERE / "inventory.json").read_text(encoding="utf-8"))
    current = {path.relative_to(HERE).as_posix(): path for path in HERE.rglob("*")
               if path.is_file() and path != HERE / "inventory.json" and "__pycache__" not in path.parts}
    locked = {item["path"]: item for item in inventory["files"]}
    if set(current) != set(locked):
        raise ValueError(f"DP-67 inventory paths differ: missing={sorted(set(locked) - set(current))}, extra={sorted(set(current) - set(locked))}")
    for name, path in current.items():
        data = path.read_bytes()
        if len(data) != locked[name]["size"] or sha(data) != locked[name]["sha256"]:
            raise ValueError(f"DP-67 inventory mismatch: {name}")
    with tempfile.TemporaryDirectory(prefix="leaf-dp67-verify-") as temporary:
        output = Path(temporary)
        command = [sys.executable, str(Path(__file__).resolve()), "--output-dir", str(output)]
        if zip_path is None:
            command += ["--retained-installer", str(HERE / "original/LeafDP67-1.2.1-Setup.exe")]
        else:
            command += ["--zip", str(zip_path.resolve())]
        completed = subprocess.run(command, check=True, capture_output=True, text=True)
        regenerated = {path.relative_to(output).as_posix(): path for path in output.rglob("*")
                       if path.is_file() and path != output / "inventory.json"}
        expected = {name for name in current if name not in {"README.md", "README.en.md", "analyze_dp67.py"}}
        if set(regenerated) != expected:
            raise ValueError("Regenerated DP-67 evidence path set differs")
        for name, path in regenerated.items():
            if path.read_bytes() != current[name].read_bytes():
                raise ValueError(f"Fresh static analysis differs: {name}")
    print(json.dumps({"verified": True, "inventory_files": len(current), "regenerated_files": len(regenerated),
                      "reference_files_modified": False, "vendor_code_executed": False}, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path, help="Original user ZIP; its bytes are checked but never retained")
    parser.add_argument("--verify", action="store_true", help="Read-only inventory and fresh evidence comparison; does not require the user ZIP")
    parser.add_argument("--retained-installer", type=Path, help=argparse.SUPPRESS)
    parser.add_argument("--output-dir", type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.verify:
        verify(args.zip)
        return
    output_root = args.output_dir.resolve() if args.output_dir else HERE
    if args.zip is not None:
        archive_bytes = args.zip.read_bytes()
        if len(archive_bytes) != ZIP_SIZE or sha(archive_bytes) != ZIP_SHA:
            raise ValueError("Unexpected ZIP bytes; this analysis is pinned to the supplied archive")
        with zipfile.ZipFile(args.zip) as archive:
            entries = archive.infolist()
            for entry in entries:
                name = PurePosixPath(entry.filename)
                if name.is_absolute() or ".." in name.parts or ":" in entry.filename:
                    raise ValueError("Unsafe archive member path")
            if len(entries) != 41 or sum(item.file_size for item in entries) != 7479685:
                raise ValueError("Unexpected ZIP inventory")
            installer = archive.read(SETUP_MEMBER)
            backup_headers = []
            for entry in entries:
                if entry.filename.lower().endswith(".pbf"):
                    with archive.open(entry) as stream:
                        prefix = stream.read(12)
                    backup_headers.append(prefix.decode("ascii", "replace"))
            if backup_headers != ["POCKETPCIMAG"] * 4:
                raise ValueError("Unexpected backup header classification")
    elif args.retained_installer is not None:
        installer = args.retained_installer.read_bytes()
        # Classification-only metadata recorded from the hash-pinned ZIP during
        # the original extraction; no backup or ZIP is needed for reproduction.
        backup_headers = ["POCKETPCIMAG"] * 4
    else:
        parser.error("Use --zip to acquire the pinned sources, or --verify to check retained evidence")
    setup_path = output_root / "original/LeafDP67-1.2.1-Setup.exe"
    write_original(setup_path, installer, SETUP_SHA)
    setup = pefile.PE(data=installer)
    cab = None
    resource_rva = None
    for resource_type in setup.DIRECTORY_ENTRY_RESOURCE.entries:
        if str(resource_type.name) != "BIN":
            continue
        for resource in resource_type.directory.entries:
            if resource.struct.Id == 103:
                leaf = resource.directory.entries[0].data.struct
                resource_rva = leaf.OffsetToData
                cab = setup.get_data(leaf.OffsetToData, leaf.Size)
    if cab is None:
        raise ValueError("Missing embedded CAB resource BIN/103")
    cabinet = output_root / "original/LeafDP67-1.2.1.CAB"
    write_original(cabinet, cab, CAB_SHA)
    members = cab_members(cab)
    extracted = output_root / "extracted"
    extracted.mkdir(parents=True, exist_ok=True)
    if sys.platform != "win32":
        raise ValueError("Static CAB extraction uses the Windows expand.exe archive utility")
    subprocess.run([str(Path(os.environ["SystemRoot"]) / "System32/expand.exe"), "-F:*", str(cabinet), str(extracted)],
                   check=True, capture_output=True)
    analysis = output_root / "analysis"
    binaries, client_strings = [], []
    for member in members:
        path = extracted / member["cab_name"]
        if path.stat().st_size != member["size"]:
            raise ValueError("Extracted CAB member size mismatch")
        if path.read_bytes()[:2] == b"MZ":
            report, strings = inspect_pe(path)
            binaries.append(report)
            write_json(analysis / (path.name + ".strings.json"), strings)
            if path.name == "LEAFDP~2.001":
                client_strings = strings
    write_json(analysis / "binary-analysis.json", {"cab_sha256": CAB_SHA, "members": members, "binaries": binaries})
    main_bytes = (extracted / "LEAFDP~2.001").read_bytes()
    if sha(main_bytes) != CLIENT_SHA:
        raise ValueError("Unexpected DP-67 client; do not reuse address anchors")
    pe = pefile.PE(data=main_bytes)
    function_ranges = set(arm_functions(pe))
    strings_by_va = {int(item["va"], 16): item["text"] for item in client_strings}
    imports = {entry.address: module.dll.decode("ascii") + "!" + (entry.name.decode("ascii") if entry.name else "#" + str(entry.ordinal))
               for module in pe.DIRECTORY_ENTRY_IMPORT for entry in module.imports}
    decoder = Cs(CS_ARCH_ARM, CS_MODE_ARM | CS_MODE_LITTLE_ENDIAN)
    disassembly_files = []
    for name, (start, end) in FOCUS.items():
        if (start, end) not in function_ranges:
            raise ValueError(f"Unexpected .pdata range for {name}")
        lines = ["# Pinned DP-67 ARM function; literal pools inside .pdata ranges may decode as data-like instructions."]
        references = []
        for instruction in decoder.disasm(pe.get_data(start - pe.OPTIONAL_HEADER.ImageBase, end - start), start):
            value = literal_value(pe, instruction.address)
            note = ""
            if value in strings_by_va:
                note = " ; " + repr(strings_by_va[value])
                references.append({"instruction_va": instruction.address, "literal_va": value, "text": strings_by_va[value]})
            elif value in imports:
                note = " ; IAT " + imports[value]
            elif value is not None and pe.OPTIONAL_HEADER.ImageBase <= value < pe.OPTIONAL_HEADER.ImageBase + pe.OPTIONAL_HEADER.SizeOfImage:
                raw = pe.get_data(value - pe.OPTIONAL_HEADER.ImageBase, 6)
                if raw.startswith(b".\x001\x00\0\0"):
                    note = " ; wide '.1'"
            offset = pe.get_offset_from_rva(instruction.address - pe.OPTIONAL_HEADER.ImageBase)
            lines.append(f"{instruction.address:08x} [file {offset:08x}] {instruction.mnemonic:9} {instruction.op_str}{note}")
        target = analysis / "disassembly" / (name + ".arm.txt")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("\n".join(lines) + "\n", encoding="utf-8")
        disassembly_files.append({"file": target.relative_to(output_root).as_posix(), "begin_va": start, "end_va_exclusive": end,
                                  "sha256": sha(target.read_bytes()), "literal_references": references})
    csv = []
    for address, expected in CSV_REFS:
        value = literal_value(pe, address)
        actual = pe.get_string_at_rva(value - pe.OPTIONAL_HEADER.ImageBase).decode("ascii")
        if actual != expected:
            raise ValueError(f"CSV construction changed at {address:#x}")
        csv.append({"instruction_va": address, "literal": actual})
    if branch_target(pe, 0x19E44) != 0x19920 or branch_target(pe, 0x2AB08) != 0x279E8:
        raise ValueError("Modified-slot or setter-wrapper call anchor changed")
    if unicode_string(pe, literal_value(pe, 0x19F70)) != ".1":
        raise ValueError("Modified-name test literal changed")
    if unicode_string(pe, literal_value(pe, 0x18134)) != ";" or unicode_string(pe, literal_value(pe, 0x18250)) != ",":
        raise ValueError("Framing record delimiters changed")
    table = literal_value(pe, 0x24D4C)
    commands = [{"id": index, "name": unicode_string(pe, word(pe, table + index * 4))} for index in range(15)]
    if commands[13]["name"] != "Ack" or commands[14]["name"] != "Error" or commands[12]["name"] != "PictureTaken":
        raise ValueError("Unexpected DP-67 command IDs")
    comparisons = []
    for version in ("1.0.2", "1.0.3"):
        old = json.loads((ROOT / f"reference/analysis/wiview-{version}/binary-analysis.json").read_text(encoding="utf-8"))
        for current, previous in (("BtCoreIf.003", "BtCoreIf.005"), ("BTSDKC~1.002", "BTSDKC~1.004")):
            left = next(item for item in binaries if item["cab_name"] == current)
            right = next(item for item in old["binaries"] if item["cab_name"] == previous)
            matching = [section["name"] for section in left["sections"] if any(
                other["name"] == section["name"] and other["sha256_without_export_timestamp"] == section["sha256_without_export_timestamp"]
                for other in right["sections"])]
            comparisons.append({"wiview_version": version, "dp67_module": current, "wiview_module": previous,
                                "exact_file_equal": left["sha256"] == right["sha256"], "matching_normalized_sections": matching,
                                "dp67_sha256": left["sha256"], "wiview_sha256": right["sha256"]})
    sdk = pefile.PE(str(extracted / "BTSDKC~1.002"))
    exported = next(item for item in sdk.DIRECTORY_ENTRY_EXPORT.symbols if item.name == b"?guid_SERVCLASS_SERIAL_PORT@CBtIf@@2U_GUID@@B")
    service = str(uuid.UUID(bytes_le=sdk.get_data(exported.address, 16)))
    if service != "00001101-0000-1000-8000-00805f9b34fb":
        raise ValueError("Unexpected SPP GUID")
    evidence = {
        "schema_version": 1, "date": "2026-11-14", "hardware_tested": False,
        "scope": "Static vendor binaries only; user reports about stability are not device test results.",
        "client": {"file": "extracted/LEAFDP~2.001", "sha256": CLIENT_SHA, "file_version": "1, 2, 1, 2"},
        "functions": disassembly_files, "csv_literal_order": csv,
        "required_csv_fields": [item["literal"].lstrip(",").rstrip("=") for item in csv[:-1]],
        "conditional_csv_fields": ["Prefix"],
        "missing_in_this_writer": ["Orientation", "Color", "FileCompression", "NLCompression"],
        "save_branch": {"classifier_range": "0x19f70..0x19f88", "modified_name_rule": "contains .1, not an exact suffix test",
                        "modified_name_property": "ShootSetup", "ordinary_name_property": "DefaultShootSetup",
                        "ordinary_name_branch": "0x1a3c0..0x1a3e0",
                        "directory_wrapper": "0x2aa34..0x2ab94", "wrapper_call": 0x2AB08,
                        "wrapper_semantics": "Forwards only the supplied property names/values; no global Orientation or Color append."},
        "modified_slot": {"set_property_function": 0x19CDC, "clone_helper": 0x19920,
                          "clone_call": 0x19E44, "name_test": 0x19F70,
                          "semantics": "Changed property on a name without '.1': reuse/create name+'.1', copy 12 source values into a 13-element row, edit the returned slot, make it active. Existing modified slots are reused.",
                          "light_caller": {"function": 0x2F7FC, "property_index": 6},
                          "iso_ui_path_status": "Generic dialog path not completely mapped in this bounded audit; ISO is model column 2 in SaveShootSetup, but no invented UI clone call is asserted."},
        "prefix_support": {"flag_offset": "0x1bb", "sync_parser": "0x16a58..0x16b04",
                           "semantics": "Global flag set when a ShootSetup Prefix or ICCFlag key is parsed; not a separate Sync PrefixSupport attribute."},
        "framing": {"parser": 0x18120, "item_count_check": 0x18264,
                    "list_delimiter_load": 0x18134, "record_delimiter_load": 0x18250,
                    "semantics": "Semicolon list of comma-separated records, exactly name plus four integer values; only the name belongs in ShootSetup.Framing."},
        "set_camera_transaction": {"function": 0x279E8, "timeout_ms": 12000, "attempt_limit": 3,
                                   "expected_command_id": 13, "expected_command": "Ack",
                                   "notes": ["Requires already-synchronized state; no per-write pre-Sync in this function.",
                                             "On transport failure while still synchronized, original code may ReSync and retry. This is historical behavior, not a recommendation to automatically repeat state-changing commands.",
                                             "Directory wrapper forwards exactly the supplied property array; it does not append global Orientation/Color."]},
        "command_ids": commands,
        "receive_routing": {"matching": "0x24cec..0x24d04", "async_picture": "0x24d40..0x24d48",
                            "semantics": "Pending reply matched by command ID; Error is accepted separately; PictureTaken is asynchronous. No transaction ID was established."},
        "startup_literal_absence": {key: {encoding: len(re.findall(re.escape(key.encode(encoding)), main_bytes, re.I))
                                         for encoding in ("ascii", "utf-16-le")} for key in ("CEVersion", "EnableTakenPicture")},
        "spp_service_uuid": service, "sdk_comparison": comparisons,
        "remaining_limits": ["No physical Valeo setting write or Bluetooth capture was performed.",
                             "Absence of a literal is bounded static evidence, not proof that every indirect construction is impossible.",
                             "DP-67 differences suggest a compatibility experiment, not a proven cause/fix for a reported disconnect.",
                             "No complete comparison of every reconnect/heartbeat/image-packet code path is claimed."]
    }
    write_json(analysis / "connection-and-setting-evidence.json", evidence)
    write_json(output_root / "provenance.json", {
        "schema_version": 1, "date": "2026-11-14", "source_kind": "user_provided_zip", "source_zip_name": ZIP_NAME,
        "source_zip_sha256": ZIP_SHA, "source_zip_size": ZIP_SIZE, "source_zip_entries": 41,
        "source_zip_uncompressed_bytes": 7479685, "retained_zip_member": SETUP_MEMBER,
        "installer_sha256": SETUP_SHA, "cab_sha256": CAB_SHA, "cab_resource": "BIN/103",
        "cab_resource_rva": resource_rva, "public_download_url": None,
        "omitted": {"source_zip_retained": False, "backup_count": len(backup_headers),
                    "backup_header_classification": sorted(set(backup_headers)), "backup_contents_read": False,
                    "note": "Only the first 12 bytes of the backup magic were read for classification (POCKETPCIMAG). No PBF contents, device credentials, backup bytes, Mac metadata or temporary folders were retained."},
        "method": "Static PE resource extraction, Windows expand.exe CAB decompression and Python pefile/Capstone parsing. Supplied executables/DLLs were never loaded or run."
    })
    inventory = []
    for path in sorted(output_root.rglob("*")):
        if path.is_file() and path != output_root / "inventory.json" and "__pycache__" not in path.parts:
            data = path.read_bytes()
            inventory.append({"path": path.relative_to(output_root).as_posix(), "size": len(data), "sha256": sha(data)})
    write_json(output_root / "inventory.json", {"schema_version": 1, "scope": "reference/dp67 excluding its own inventory and Python caches",
                                        "file_count": len(inventory), "total_bytes": sum(item["size"] for item in inventory), "files": inventory})
    print(json.dumps({"retained_zip": False, "retained_backups": 0, "cab_members": len(members), "pe_files": len(binaries),
                      "focused_functions": len(disassembly_files), "client_version": "1.2.1.2", "spp_uuid": service,
                      "required_csv_fields": evidence["required_csv_fields"], "hardware_tested": False}, indent=2))


if __name__ == "__main__":
    main()
