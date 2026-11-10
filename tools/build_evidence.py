"""Rebuild the small, auditable evidence index for the two pinned WiView samples."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import uuid

import pefile
from capstone import CS_ARCH_ARM, CS_MODE_ARM, CS_MODE_LITTLE_ENDIAN, Cs

from pe_research import arm_functions


ROOT = Path(__file__).resolve().parents[1]
SAMPLES = {
    "1.0.2": {
        "exe_sha256": "2f769acbb0c4aa410e50e1638dfb50f7a9b40d11debd5b04f66170ac4623b50d",
        "reader": 0x2A1A4, "caret": 0x2A208, "end_marker": 0x2A224,
        "read_image": 0x31274, "get_image_reply": 0x315CC,
        "version_args": 0x6123C, "version_patch": 5,
    },
    "1.0.3": {
        "exe_sha256": "ac6d0699870d7f9a1e00fe8bf507a35cfc38f3d2b6619e5dbae30eb248b87fe9",
        "reader": 0x2A988, "caret": 0x2A9EC, "end_marker": 0x2AA08,
        "read_image": 0x320CC, "get_image_reply": 0x32424,
        "version_args": 0x62194, "version_patch": 7,
    },
}


def inspect_sample(version: str, anchors: dict) -> dict:
    directory = ROOT / "reference/analysis" / ("wiview-" + version)
    exe = directory / "extracted/00LeafCE.001"
    digest = hashlib.sha256(exe.read_bytes()).hexdigest()
    if digest != anchors["exe_sha256"]:
        raise ValueError(f"Unexpected {version} executable; do not reuse these address anchors")
    pe = pefile.PE(str(exe))
    disassembler = Cs(CS_ARCH_ARM, CS_MODE_ARM | CS_MODE_LITTLE_ENDIAN)
    instructions = []
    for key, expected in (("caret", "r1, #0x5e"), ("end_marker", "r1, #0x3e")):
        va = anchors[key]
        offset = pe.get_offset_from_rva(va - pe.OPTIONAL_HEADER.ImageBase)
        data = pe.get_data(va - pe.OPTIONAL_HEADER.ImageBase, 4)
        instruction = next(disassembler.disasm(data, va))
        if instruction.mnemonic != "cmp" or instruction.op_str != expected:
            raise ValueError(f"Reader anchor changed at {version}:{va:#x}")
        instructions.append({"meaning": key, "va": hex(va), "offset": hex(offset),
                             "bytes": data.hex(), "instruction": instruction.mnemonic + " " + instruction.op_str})
    functions = arm_functions(pe)
    ranges = {}
    for name in ("reader", "read_image", "get_image_reply"):
        found = next((f for f in functions if f[0] == anchors[name]), None)
        if found is None:
            raise ValueError(f"No .pdata function for {version}:{name}")
        ranges[name] = {"begin_va": hex(found[0]), "end_va_exclusive": hex(found[1])}
    sdk = pefile.PE(str(directory / "extracted/BTSDKC~1.004"))
    export = next(e for e in sdk.DIRECTORY_ENTRY_EXPORT.symbols if e.name == b'?guid_SERVCLASS_SERIAL_PORT@CBtIf@@2U_GUID@@B')
    service = str(uuid.UUID(bytes_le=sdk.get_data(export.address, 16)))
    if service != "00001101-0000-1000-8000-00805f9b34fb":
        raise ValueError("Unexpected SPP service UUID")
    strings = json.loads((directory / "00LeafCE.001.strings.json").read_text("utf-8"))
    controls = [s for s in strings if s["text"].startswith("^<")]
    version_va = anchors["version_args"]
    version_code = pe.get_data(version_va - pe.OPTIONAL_HEADER.ImageBase, 16)
    version_instructions = list(disassembler.disasm(version_code, version_va))
    expected_version_code = [
        ("mov", f"r3, #{anchors['version_patch']}"), ("str", "r3, [sp]"),
        ("mov", "r2, #1"), ("mov", "r3, #1"),
    ]
    if [(i.mnemonic, i.op_str) for i in version_instructions] != expected_version_code:
        raise ValueError("Version log argument sequence changed")
    result = {
        "version_label": version, "executable_cab_member": "00LeafCE.001",
        "executable_sha256": digest, "function_ranges": ranges, "reader_instructions": instructions,
        "spp_uuid": {"value": service, "module": "BTSDKC~1.004", "export_rva": hex(export.address)},
        "version_log": {
            "format": "Leaf WiView version %d.%d%d",
            "constructed_value": f"1.1{anchors['version_patch']}",
            "arguments_begin_va": hex(version_va),
            "note": "This is a statically reconstructed log string, not a verified About dialog or release package version.",
        },
        "control_anchors": controls,
    }
    sdk.close()
    pe.close()
    return result


def main() -> None:
    report = {
        "evidence_level": "static_binary_analysis",
        "hardware_validated": False,
        "notes": [
            "These anchors identify code in the exact pinned samples, not an Aptus II 5 firmware capture.",
            "Control values are code-page converted by the original client; arbitrary Unicode filename encoding remains unverified.",
            "The offline framer implements a bounded, non-packetized subset. Synthetic tests are not captured camera sessions.",
            "A complete startup handshake, peer authentication behavior, MOS download, and live view remain unverified.",
        ],
        "samples": [inspect_sample(version, anchors) for version, anchors in SAMPLES.items()],
    }
    path = ROOT / "reference/analysis/protocol-evidence.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Verified ARM reader anchors and SPP UUID in {len(report['samples'])} pinned samples: {path}")


if __name__ == "__main__":
    main()

