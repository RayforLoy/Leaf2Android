"""Bounded PPC disassembly with verifiable string address constructions.

Requires capstone 5.x. Addresses are preferred firmware VAs from header
offset 0x120. File offsets always refer to the original wrapped UPD.
"""

from collections import defaultdict
from pathlib import Path
import hashlib
import json
import re
import struct

from capstone import Cs, CS_ARCH_PPC, CS_MODE_32, CS_MODE_BIG_ENDIAN

from analyze_firmware import HEADER_SIZE, dump_json


def main():
    root = Path(__file__).resolve().parent
    source = root.parent / "CaptureOne.Mac.7.1.6" / "FirmWareAptusII.UPD"
    wrapped = source.read_bytes()
    payload = wrapped[HEADER_SIZE:]
    base = struct.unpack_from(">I", wrapped, 0x120)[0]
    words = list(struct.unpack(">" + str(len(payload) // 4) + "I", payload))
    anchors = {2: 0x45b428, 13: 0x638b70}  # entry's lis/addi initialization
    refs = []
    strings = {}

    def cstring(va):
        off = va - base
        if not 0 <= off < len(payload):
            return None
        end = payload.find(b"\0", off, off + 513)
        if end < 0:
            return None
        raw = payload[off:end]
        if len(raw) < 4 or not all(32 <= byte <= 126 or byte in (9, 10, 13) for byte in raw):
            return None
        return raw.decode("ascii")

    for index, word in enumerate(words):
        op, rd, ra = word >> 26, (word >> 21) & 31, (word >> 16) & 31
        imm = struct.unpack(">h", struct.pack(">H", word & 0xffff))[0]
        address, basis = None, None
        if op == 14 and ra in anchors:
            address, basis = (anchors[ra] + imm) & 0xffffffff, "entry-initialized SDA register"
        if op == 14 and index:
            prev = words[index - 1]
            if prev >> 26 == 15 and (prev >> 16) & 31 == 0 and (prev >> 21) & 31 == ra:
                address = (((prev & 0xffff) << 16) + imm) & 0xffffffff
                basis = "adjacent lis/addi pair"
        text = cstring(address) if address is not None else None
        if text:
            row = {"instruction_va": base + index * 4, "instruction_file_offset": HEADER_SIZE + index * 4,
                   "string_va": address, "string_file_offset": HEADER_SIZE + address - base,
                   "text": text, "basis": basis}
            refs.append(row)
            strings[address] = text

    interesting = re.compile(r"RequestSync|GetImage|GetCameraParam|SetCameraParam|CEVersion|Bluetooth|"
                             r"NewPinCode|PinCode|passkey|SPP:|GAP:|ATTACHMENT|FileCommand|PictureTaken|"
                             r"GetPictureParams|SetPictureParams|ReSync|\^<|PDA Interface|PDAInterface|"
                             r"PXAInterface|GetParamList|packet|bytes|Leaf Wi|Aptus.*Comm|AFiComm", re.I)
    selected = [row for row in refs if interesting.search(row["text"])]
    dump_json(root / "evidence" / "ppc_string_xrefs.json", {
        "source": str(source), "source_sha256": hashlib.sha256(wrapped).hexdigest(),
        "payload_base_va": base,
        "file_offset_for_va": "VA - 0x74000 + 0x128 (this UPD only)",
        "all_ascii_address_constructions_count": len(refs),
        "limitations": "String references establish code presence, not model enablement or runtime reachability.",
        "selected": selected,
    })
    refs_by_va = {row["instruction_va"]: row for row in refs}
    md = Cs(CS_ARCH_PPC, CS_MODE_32 | CS_MODE_BIG_ENDIAN)

    def disassemble(lo, hi):
        lines = []
        for ins in md.disasm(payload[lo-base:hi-base], lo):
            note = ""
            ref = refs_by_va.get(ins.address)
            if ref:
                note = f" ; str@{ref['string_va']:#x} {ref['text']!r}"
            off = ins.address - base + HEADER_SIZE
            lines.append(f"{ins.address:08x} [file {off:08x}] {ins.mnemonic:9} {ins.op_str}{note}")
        return "\n".join(lines)

    # Keep complete local windows instead of claiming uncertain function bounds.
    windows = [("entry", base, base + 0x90),
               ("GetImage_attributes_and_transmit", 0x97d80, 0x98a40),
               ("GetCameraParam_name_and_adjacent_code", 0xe51a0, 0xe55c0),
               ("CEVersion_and_bluetooth_settings", 0x9b140, 0x9b5a0),
               ("PDAInterface_local", 0x102380, 0x102b00),
               ("bluetooth_stack_setup", 0x358b80, 0x358e40),
               ("bluetooth_gap_passkey_event", 0x359220, 0x3592d0)]
    for row in selected:
        if re.search(r"SPP:|GAP:.*passkey|BluetoothSupport|Switch BlueTooth|ReSyncRequest|Leaf Wi|Aptus.*Comm|AFiComm", row["text"], re.I):
            lo = max(base, row["instruction_va"] - 32)
            windows.append((f"xref_{row['instruction_va']:08x}", lo, lo + 112))
    target = root / "disassembly"
    target.mkdir(exist_ok=True)
    for name, lo, hi in windows:
        (target / (name + ".ppc.txt")).write_text(disassemble(lo, hi) + "\n", encoding="utf-8")
    print(f"PPC base {base:#x}; {len(refs)} ASCII address constructions; {len(selected)} selected; {len(windows)} disassembly windows")
    for row in selected:
        if row["text"] in ("RequestSync", "GetImage", "GetCameraParam", "SetCameraParam", "GetPictureParams", "SetPictureParams"):
            print(f"{row['text']}: instruction {row['instruction_va']:#x}, file {row['instruction_file_offset']:#x}")


if __name__ == "__main__":
    main()
