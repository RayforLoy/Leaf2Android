"""Reproduce bounded capture-notification / gray-pick static evidence.

Reads retained manufacturer bytes only. No vendor code or hardware is executed.
Invoked by analyze_dp67.py so its --verify also regenerates these artifacts.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys

import capstone
import pefile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from pe_research import arm_functions, literal_references

SAMPLES = {
    "dp67": {
        "path": "reference/dp67/extracted/LEAFDP~2.001",
        "sha256": "d0e67119db4a8e49a2cf8ec264143ccdf5da2d67623f835df9ace5fdf9c620b7",
        "strings": "reference/dp67/analysis/LEAFDP~2.001.strings.json",
        "ranges": {
            "receive-picture-notification": [(0x24A44, 0x25128)],
            "on-picture-taken": [(0x17360, 0x17C6C)],
            "file-cache": [(0x30E70, 0x3114C)],
            "get-image": [(0x26F00, 0x27340)],
            "busy-calibration-ui": [(0x34C98, 0x34DDC), (0x35250, 0x35314)],
            "packet-ack-nak": [(0x257C0, 0x264DC)],
        },
    },
    "wiview-1.0.3": {
        "path": "reference/analysis/wiview-1.0.3/extracted/00LeafCE.001",
        "sha256": "ac6d0699870d7f9a1e00fe8bf507a35cfc38f3d2b6619e5dbae30eb248b87fe9",
        "strings": "reference/analysis/wiview-1.0.3/00LeafCE.001.strings.json",
        "ranges": {
            "notification-router": [(0x29FD0, 0x2A8A8)],
            "picture-notification-parser": [(0x28718, 0x28B2C)],
            "picture-notification-model": [(0x16B14, 0x16BC4), (0x16C64, 0x17278)],
            "image-change-parser": [(0x29A94, 0x29B44)],
            "attachment-drain": [(0x28664, 0x28718)],
            "stop-notification-ack": [(0x29C28, 0x29CCC)],
            "packet-ack-nak": [(0x31DC8, 0x31E04), (0x31E8C, 0x31EFC)],
            "gray-click": [(0x777F0, 0x77BC0)],
            "image-click-scale": [(0x765DC, 0x768FC)],
            "get-image-constructor": [(0x36070, 0x36288)],
            "sync-dimensions": [(0x2FE34, 0x2FF88)],
            "sync-target-embedding": [(0x18150, 0x18168), (0x25D04, 0x25D34), (0x2ED40, 0x2ED7C)],
            "full-preview-grid": [(0x38F10, 0x38F40), (0x78A70, 0x78AB8)],
            "orientation-parser": [(0x2BC9C, 0x2BDB8)],
            "orientation-to-bitmap": [(0x55148, 0x5526C)],
            "oriented-bitmap-wrapper": [(0x54D98, 0x54F3C)],
            "rotate-bitmap-helper": [(0x955A4, 0x95814)],
            "rotate-pixel-kernel": [(0x94DE4, 0x951BC)],
            "dib-constructor": [(0x92CFC, 0x92DA0)],
            "image-state-accessors": [(0x4809C, 0x480A4), (0x48B88, 0x48B90)],
            "get-bitmap-cache-download": [(0x51234, 0x516FC)],
            "saved-state-bookkeeping": [(0x25570, 0x255C8)],
            "shoot-view-notification": [(0x789A0, 0x7904C)],
        },
    },
    "wiview-1.0.2": {
        "path": "reference/analysis/wiview-1.0.2/extracted/00LeafCE.001",
        "sha256": "2f769acbb0c4aa410e50e1638dfb50f7a9b40d11debd5b04f66170ac4623b50d",
        "strings": "reference/analysis/wiview-1.0.2/00LeafCE.001.strings.json",
        "ranges": {
            "notification-router": [(0x298AC, 0x2A0D4)],
            "picture-notification-parser": [(0x282D4, 0x286E8)],
            "picture-notification-model": [(0x16BF4, 0x171CC)],
            "stop-notification-ack": [(0x29504, 0x295A8)],
            "gray-click": [(0x767B8, 0x76B8C)],
            "image-click-scale": [(0x755B4, 0x758C8)],
            "get-image-constructor": [(0x35088, 0x352A0)],
        },
    },
}
FIRMWARE = {
    "classic-4.701": {
        "path": "reference/firmware/originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD",
        "sha256": "a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a",
        "version": 4701, "r2": 0x3D19D8, "r13": 0x4852A0,
        "ranges": {
            "pda-catalog": [(0xE640C, 0xE6684)],
            "registered-name-lookup": [(0xE727C, 0xE7320)],
            "incoming-dispatch": [(0xE6C5C, 0xE6E30)],
            "create-outgoing-message": [(0xE7B60, 0xE7C3C)],
            "outgoing-command-selector": [(0xE3C18, 0xE3F00)],
            "standard-error-constructors": [(0xB0B74, 0xB0D34)],
            "get-image-processing": [(0x90C20, 0x91314)],
            "gray-light-parse": [(0x915D0, 0x9165C)],
            "picture-dimensions-reply": [(0x86F80, 0x86FE8)],
        },
    },
    "shared-5.041": {
        "path": "reference/firmware/originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD",
        "sha256": "80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6",
        "version": 5041, "r2": 0x45B428, "r13": 0x638B70,
        "ranges": {
            "pda-catalog": [(0x1026A4, 0x102920)],
            "registered-name-lookup": [(0x1033FC, 0x1034A0)],
            "incoming-dispatch": [(0x102DDC, 0x102FB0)],
            "create-outgoing-message": [(0x103CE0, 0x103DBC)],
            "outgoing-command-selector": [(0xFFF64, 0x1003B0)],
            "standard-error-constructors": [(0xB9D30, 0xB9EF0)],
            "get-image-processing": [(0x97FE4, 0x98570)],
        },
    },
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def arm_literal(pe: pefile.PE, address: int) -> int | None:
    try:
        word = struct.unpack("<I", pe.get_data(address - pe.OPTIONAL_HEADER.ImageBase, 4))[0]
        if word & 0x0F7F0000 != 0x051F0000:
            return None
        pool = address + 8 + ((word & 0xFFF) if word & 0x800000 else -(word & 0xFFF))
        return struct.unpack("<I", pe.get_data(pool - pe.OPTIONAL_HEADER.ImageBase, 4))[0]
    except (struct.error, pefile.PEFormatError):
        return None


def wide(pe: pefile.PE, address: int) -> str:
    raw = pe.get_data(address - pe.OPTIONAL_HEADER.ImageBase, 1024)
    end = next((i for i in range(0, len(raw) - 1, 2) if raw[i:i + 2] == b"\0\0"), len(raw))
    return raw[:end].decode("utf-16-le")


def ppc_word(payload: bytes, address: int) -> int:
    return struct.unpack_from(">I", payload, address - 0x74000)[0]


def ppc_branch(payload: bytes, address: int) -> int:
    word = ppc_word(payload, address)
    if word >> 26 != 18 or not word & 1:
        raise ValueError(f"Expected PPC BL at {address:#x}")
    distance = word & 0x3FFFFFC
    if distance & 0x2000000:
        distance -= 0x4000000
    return (distance if word & 2 else address + distance) & 0xFFFFFFFF


def ascii_at(payload: bytes, address: int) -> str | None:
    offset = address - 0x74000
    if not 0 <= offset < len(payload):
        return None
    end = payload.find(b"\0", offset, offset + 512)
    raw = payload[offset:end] if end >= 0 else b""
    if raw and all(c in (9, 10, 13) or 32 <= c < 127 for c in raw):
        return raw.decode("ascii")
    return None


def ppc_literal(payload: bytes, address: int, metadata: dict) -> int | None:
    offset = address - 0x74000
    word = ppc_word(payload, address)
    if word >> 26 != 14:
        return None
    source = (word >> 16) & 31
    immediate = struct.unpack_from(">h", payload, offset + 2)[0]
    if source in (2, 13):
        return (metadata[f"r{source}"] + immediate) & 0xFFFFFFFF
    if offset >= 4:
        previous = ppc_word(payload, address - 4)
        if previous >> 26 == 15 and (previous >> 16) & 31 == 0 and (previous >> 21) & 31 == source:
            return (((previous & 0xFFFF) << 16) + immediate) & 0xFFFFFFFF
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "analysis/capture-notifications")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = {"schema_version": 1, "date": "2026-11-15", "hardware_tested_by_this_analysis": False,
              "capstone_version": capstone.__version__, "sources": [], "disassembly": [], "ack_literal_references": []}
    for sample, metadata in SAMPLES.items():
        raw = (ROOT / metadata["path"]).read_bytes()
        if sha(raw) != metadata["sha256"]:
            raise ValueError(f"Unexpected source bytes: {sample}")
        report["sources"].append({"id": sample, "path": metadata["path"], "size": len(raw), "sha256": sha(raw)})
        pe = pefile.PE(data=raw)
        base = pe.OPTIONAL_HEADER.ImageBase
        rows = json.loads((ROOT / metadata["strings"]).read_text(encoding="utf-8"))
        labels = {int(row["va"], 16): row["text"] for row in rows}
        imports = {entry.address: module.dll.decode() + "!" + (entry.name.decode() if entry.name else "#" + str(entry.ordinal))
                   for module in pe.DIRECTORY_ENTRY_IMPORT for entry in module.imports}
        functions = arm_functions(pe)
        decoder = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM | capstone.CS_MODE_LITTLE_ENDIAN)
        for name, ranges in metadata["ranges"].items():
            lines, references = ["# Static ARM VA ranges; literal pools may decode as data-like instructions."], []
            for start, end in ranges:
                lines.append(f"# VA {start:#x}..{end:#x}, exclusive")
                for ins in decoder.disasm(pe.get_data(start - base, end - start), start):
                    value = arm_literal(pe, ins.address)
                    text = labels.get(value)
                    if text is None and value is not None and base <= value < base + pe.OPTIONAL_HEADER.SizeOfImage:
                        try:
                            candidate = wide(pe, value)
                            if candidate and all(c.isprintable() and ord(c) < 128 for c in candidate):
                                text = candidate
                        except UnicodeError:
                            pass
                    note = " ; " + repr(text) if text else (" ; IAT " + imports[value] if value in imports else "")
                    if text:
                        references.append({"instruction_va": ins.address, "literal_va": value, "text": text})
                    offset = pe.get_offset_from_rva(ins.address - base)
                    lines.append(f"{ins.address:08x} [file {offset:08x}] {ins.mnemonic:9} {ins.op_str}{note}")
            target = output / sample / (name + ".arm.txt")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text("\n".join(lines) + "\n", encoding="utf-8")
            report["disassembly"].append({"sample": sample, "path": target.relative_to(output).as_posix(),
                                          "ranges": [{"start_va": a, "end_va_exclusive": b} for a, b in ranges],
                                          "sha256": sha(target.read_bytes()), "literal_references": references})
        for row in rows:
            if "<Ack" in row["text"] or "<Nak" in row["text"]:
                for address in literal_references(pe, int(row["va"], 16)):
                    report["ack_literal_references"].append({"sample": sample, "literal": row["text"], "encoding": row["encoding"],
                        "instruction_va": address, "containing_function": next(([a, b] for a, b in functions if a <= address < b), None)})
        if sample == "wiview-1.0.3":
            if wide(pe, arm_literal(pe, 0x36240)) != ' light="%d,%d"':
                raise ValueError("Gray-pick wire format changed")
            if wide(pe, arm_literal(pe, 0x2FE38)) != "picturedimensions":
                raise ValueError("PictureDimensions parse anchor changed")
    firmware_payloads = {}
    for sample, metadata in FIRMWARE.items():
        raw = (ROOT / metadata["path"]).read_bytes()
        if sha(raw) != metadata["sha256"] or raw[:8] != b"LEAF FRM" or struct.unpack_from(">I", raw, 20)[0] != metadata["version"]:
            raise ValueError(f"Unexpected firmware source: {sample}")
        report["sources"].append({"id": sample, "path": metadata["path"], "size": len(raw), "sha256": sha(raw)})
        payload = raw[0x128:]
        firmware_payloads[sample] = payload
        decoder = capstone.Cs(capstone.CS_ARCH_PPC, capstone.CS_MODE_32 | capstone.CS_MODE_BIG_ENDIAN)
        decoder.skipdata = True
        for name, ranges in metadata["ranges"].items():
            lines, references = ["# Static PPC VA ranges. UPD offset = VA - 0x74000 + 0x128."], []
            for start, end in ranges:
                lines.append(f"# VA {start:#x}..{end:#x}, exclusive")
                for ins in decoder.disasm(payload[start - 0x74000:end - 0x74000], start):
                    value = ppc_literal(payload, ins.address, metadata)
                    text = ascii_at(payload, value) if value is not None else None
                    if text:
                        references.append({"instruction_va": ins.address, "literal_va": value, "text": text})
                    lines.append(f"{ins.address:08x} [UPD {ins.address - 0x74000 + 0x128:08x}] {ins.mnemonic:9} {ins.op_str}" + (" ; " + repr(text) if text else ""))
            target = output / sample / (name + ".ppc.txt")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text("\n".join(lines) + "\n", encoding="utf-8")
            report["disassembly"].append({"sample": sample, "path": target.relative_to(output).as_posix(),
                                          "ranges": [{"start_va": a, "end_va_exclusive": b} for a, b in ranges],
                                          "sha256": sha(target.read_bytes()), "literal_references": references})
    classic, shared = firmware_payloads["classic-4.701"], firmware_payloads["shared-5.041"]
    assertions = [
        (ppc_branch(classic, 0xE6CEC), 0xE727C), (ppc_branch(classic, 0xE6D28), 0xE7B60),
        (ppc_branch(classic, 0xE7BD8), 0xE3C18), (ppc_word(classic, 0x3E002C + 6 * 4), 0xE3E50),
        (ppc_word(classic, 0xE6D1C), 0x38A0000C), (ascii_at(classic, 0x3E04C8), "0x01"),
        (ppc_branch(shared, 0x102E6C), 0x1033FC), (ppc_branch(shared, 0x102EA8), 0x103CE0),
        (ppc_branch(shared, 0x103D58), 0xFFF64), (ppc_word(shared, 0x46DF20 + 6 * 4), 0x10019C),
        (ppc_word(shared, 0x102E9C), 0x38A0000C), (ascii_at(shared, 0x46E4AC), "0x01"),
        (ascii_at(classic, 0x3D32D0), "0x01"), (ascii_at(classic, 0x3D32D8), "0x02"),
        (ppc_branch(classic, 0x912C8), 0xB0B74), (ppc_branch(shared, 0x9850C), 0xB9D30),
    ]
    if any(actual != expected for actual, expected in assertions):
        raise ValueError("Firmware error-path anchor changed")
    report["findings"] = {
        "notification_ack": {
            "picture_taken_and_image_change": "No unsolicited notification Ack is established in the traced DP-67/WiView handlers. WiView's common 0x28664 follow-up drains attachment bytes, not an Ack writer.",
            "stop_exception": {"wiview_1_0_3_handler": "0x29c28..0x29ccc", "ack_literal_load": "0x29c40", "link_write": "0x29c7c", "wiview_1_0_2_handler": "0x29504..0x295a8"},
            "image_packets": "Preserve the separately evidenced image-transfer Ack/Nak paths. A packet reply and an unsolicited notification are different protocol contexts.",
            "limit": "All direct Ack/Nak literal references are listed; this is bounded static analysis, not a proof about every possible dynamic string construction.",
        },
        "error_0x01": {
            "classic": {"dispatcher": "0xe6c5c..0xe6e30", "registered_name_lookup": "0xe727c..0xe7320", "null_handler_branch": "0xe6cfc..0xe6d28", "outgoing_enum": 12, "create": "0xe7b60", "selector": "0xe3c18", "selector_case": "0xe3e50..0xe3e80"},
            "shared_5_041": {"dispatcher": "0x102ddc..0x102fb0", "registered_name_lookup": "0x1033fc..0x1034a0", "null_handler_branch": "0x102e7c..0x102ea8", "outgoing_enum": 12, "create": "0x103ce0", "selector": "0xfff64", "selector_case": "0x10019c..0x1001cc"},
            "semantics": "A generic no-current-handler dispatch branch constructs Error error_code=0x01; Ack is absent from the nine registered PDA command names. This is not a GetImage-specific busy diagnosis.",
            "get_image_distinction": "The traced GetImage lookup failure passes standard-error enum 3, mapped to error_code=0x02; image-processing failures use a separate error attribute (e.g. 1ffc/8002). Standard enum 2 is also mapped to 0x01 by the generic constructor, so the numeric code alone is not a unique cause.",
            "causality_limit": "A superfluous notification Ack could yield an uncorrelated Error that a pending GetImage accepts. This fits the reported symptom but no Valeo firmware dump, live capture or device reproduction was performed here.",
        },
        "capture_path": {
            "dp67": "OnPictureTaken consumes the complete file_name, splits on '/', requires first component '.', and resolves the corresponding cached file object. CDOSFile::Cache uses its complete name in the same CProtocolLayer::GetImage used for normal downloads; no auto-only basename substitution was established.",
            "busy": "Explicit busy=yes is handled as busy/calibration state. This attribute must not be inferred from error_code=0x01.",
            "wiview": "The PictureTaken parser recognizes parametersReady=yes and imageSaved=yes; notification processing records image model states 0/1/2 respectively, with imageSaved taking precedence. It retains the complete notified path. The extra 0x25570 call in the saved branch performs two-word bookkeeping arithmetic; it is not established as a download-queue operation.",
            "download_state_gate": "GetBitmapBase 0x51234..0x516fc resolves cache/request variants then constructs GetImage at 0x5143c; no image-state==2 requirement is present in this traced construction path. Notification/UI state handling is not evidence that every automatic preview must await imageSaved. No new universal parametersReady gate is established.",
            "image_change": "Has a separate route and invalidation notification; does not justify acknowledging or starting an overlapping binary download unconditionally.",
        },
        "gray_pick": {
            "wire": 'GetImage light="{x},{y}"', "constructor_1_0_3": "0x36070..0x36288", "literal_load": "0x36240",
            "sync_dimensions": "picturedimensions is parsed as two comma-separated integers; max becomes the landscape grid width W, min its height H (0x2feb4..0x2ff64). Sync target is global+8, so target+0x104/+0x108 become global+0x10c/+0x110.",
            "full_grid": "Getters 0x38f10/0x38f28 return W-1/H-1; initial full-preview ROI adds 1 at 0x78a90..0x78ab4, making [0,W)x[0,H).",
            "screen_to_roi": "0x76678..0x76790 scales tap x/y by current ROI width/height divided by displayed image rectangle width/height, then adds ROI origin. The resulting image-grid point is passed to the gray handler at 0x76860.",
            "orientation": {"parser": "0x2bc9c..0x2bdb8", "enums": {"Portrait CCW": 0, "Portrait CW": 1, "Landscape": 2, "Auto": 3}, "gray_handler": "0x777f0..0x77bc0", "CW": "(H-y, x)", "CCW": "(y, W-x)", "Landscape": "(x,y)", "edge_note": "The traced rotation subtracts from W/H, without an explicit -1. Do not silently present an alternative convention as vendor behavior."},
            "portrait_bitmap_bridge": {
                "orientation_to_wrapper": "0x5522c reads the photo model orientation; 0x5523c calls wrapper 0x54d98 with that value and the decoded bitmap.",
                "wrapper": "0x54d98..0x54f3c reads decoded bitmap dimensions. Landscape=2 expects width>=height. Portrait CCW/CW expects height>=width, logging a mismatch if width>height. For a normal portrait bitmap, 0x54ee8..0x54f18 calls 0x955a4 with angle 270 for CW=1 or 90 for CCW=0, then swaps wrapper width/height.",
                "rotation_helper": "0x955a4..0x95814 allocates the swapped bitmap and calls pixel kernel 0x94de4. DIB constructor 0x92cfc writes a positive height at 0x92d1c. For this 24-bit bottom-up DIB, the kernel's 90 branch (0x94ee8..0x94f94) is visually clockwise; its 270 branch (0x950f4..0x951b0) is counterclockwise.",
                "logical_pixel_transforms": {"90": "(sourceHeight-1-sourceY, sourceX)", "270": "(sourceY, sourceWidth-1-sourceX)"},
                "inference": "The PDA rotates a portrait JPEG into a temporary landscape display bitmap; the gray handler restores that display rotation. An Android view of the unchanged original JPEG must not apply the PDA rotation again. With full FIT and consistent photo metadata, normalized JPEG position maps directly to Landscape W by H or Portrait H by W camera grid.",
                "pixel_edge_convention": "Kernel pixel indices use -1, whereas the separate gray-point edge transform subtracts from W/H. An Android normalized-grid floor/clamp rule is an explicit boundary choice, not asserted bit-identical legacy rounding.",
            },
            "supported_android_subset": "Full FIT request only; explicit per-photo Landscape, Portrait CW or Portrait CCW bound to the current preview bytes; positive PictureDimensions; unchanged JPEG decode with no additional EXIF/display rotation or unknown crop; JPEG direction/aspect consistent with the grid. Map normalized JPEG position directly to W by H for Landscape or H by W for either Portrait. Reject Auto, missing or contradictory metadata; do not call this MOS sensor pixels.",
            "remaining_limits": "Arbitrary zoom ROI, unknown crops and automatic EXIF rotations remain outside this bridge. Aspect agreement is a consistency check, not evidence of an otherwise unknown crop. No hardware gray-sample accuracy test was performed; tapping white material does not guarantee a valid exposure/gray result.",
        },
    }
    target = output / "evidence.json"
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"capture_sources": len(report["sources"]), "capture_disassembly_files": len(report["disassembly"]),
                      "capture_ack_literal_references": len(report["ack_literal_references"]), "hardware_tested": False}, indent=2))


if __name__ == "__main__":
    main()
