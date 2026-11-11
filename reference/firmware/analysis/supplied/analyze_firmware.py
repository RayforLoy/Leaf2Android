"""Read-only analysis of Leaf firmware; all derived files stay in --output.

No vendor executable is run and no camera connection is opened.
Python 3.10+ standard library only. The optional CE PE reconstruction step
is separate; see third-party-tools.json and the analysis report.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET


HEADER_SIZE = 0x128
MAGIC = b"LEAF FRM"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def dump_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def entropy(data):
    if not data:
        return 0.0
    return round(-sum((n / len(data)) * math.log2(n / len(data)) for n in Counter(data).values()), 6)


def xor32le(data):
    # Firmware checks complete DWORDs only: the final 1-3 bytes are excluded.
    complete = data[:len(data) // 4 * 4]
    value = 0
    for (word,) in struct.iter_unpack("<I", complete):
        value ^= word
    return value


def leaf_header(data):
    if not data.startswith(MAGIC):
        return None
    if len(data) < HEADER_SIZE:
        raise ValueError("Truncated LEAF header")
    kind, length, checksum, version = struct.unpack_from(">4I", data, 8)
    computed_checksum = xor32le(data[HEADER_SIZE:])
    description = data[24:280].split(b"\0", 1)[0].decode("ascii", "replace")
    result = {
        "magic": "LEAF FRM", "header_size": HEADER_SIZE,
        "payload_sha256": sha256(data[HEADER_SIZE:]),
        "field_08_be": kind, "payload_length_be": length,
        "payload_length_matches": length == len(data) - HEADER_SIZE,
        "checksum_stored_be": f"0x{checksum:08x}",
        "checksum_computed_xor32le": f"0x{computed_checksum:08x}",
        "checksum_matches": checksum == computed_checksum,
        "checksum_trailing_bytes_excluded": length % 4,
        "field_14_be": version, "field_14_bytes": data[20:24].hex(),
        "description": description,
        "trailing_header_words_be": [f"0x{x:08x}" for x in struct.unpack_from(">4I", data, 280)],
    }
    if description.startswith("<Desc>"):
        tree = ET.fromstring(description)
        result["rbf_metadata"] = {
            child.tag: re.findall(r'"([^\"]*)"', child.text or "") for child in tree
        }
    return result


def scan_strings(data):
    rows = []
    for match in re.finditer(rb"[\x20-\x7e]{5,}", data):
        rows.append({"offset": match.start(), "encoding": "ascii", "text": match.group().decode("ascii")})
    # A byte regex already checks both alignments.
    for match in re.finditer(rb"(?:[\x20-\x7e]\x00){5,}", data):
        rows.append({"offset": match.start(), "encoding": "utf-16le",
                     "text": match.group().decode("utf-16le")})
    return sorted(rows, key=lambda row: (row["offset"], row["encoding"]))


def save_strings(output, stem, data):
    rows = scan_strings(data)
    target = output / "strings" / (stem + ".txt")
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as stream:
        for row in rows:
            stream.write(f"0x{row['offset']:08x}\t{row['encoding']}\t{row['text']}\n")
    return rows


class CEImage:
    def __init__(self, leaf_data):
        self.data = leaf_data
        payload = leaf_data[HEADER_SIZE:]
        if payload[:7] != b"B000FF\n":
            raise ValueError("Not a Windows CE B000FF image")
        self.start, self.length = struct.unpack_from("<II", payload, 7)
        offset = 15
        self.records = []
        while offset + 12 <= len(payload):
            address, size, check = struct.unpack_from("<III", payload, offset)
            if address == 0:
                self.entry = size
                if check or offset + 12 != len(payload):
                    raise ValueError("Unexpected CE terminator/trailing data")
                break
            raw = payload[offset + 12:offset + 12 + size]
            if len(raw) != size or sum(raw) & 0xffffffff != check:
                raise ValueError(f"Invalid CE record at {offset:#x}")
            self.records.append({"address": address, "size": size, "checksum": check,
                                 "source_file_offset": HEADER_SIZE + offset + 12})
            offset += 12 + size
        else:
            raise ValueError("No CE terminator")
        spans = sorted((r["address"], r["address"] + r["size"]) for r in self.records)
        if any(left[1] > right[0] for left, right in zip(spans, spans[1:])):
            raise ValueError("Overlapping CE records")
        if spans[0][0] != self.start or spans[-1][1] != self.start + self.length:
            raise ValueError("CE image span mismatch")

    def read(self, address, size):
        out = bytearray()
        while size:
            row = next((r for r in self.records if r["address"] <= address < r["address"] + r["size"]), None)
            if row is None:
                raise ValueError(f"Unmapped CE address {address:#x}")
            available = min(size, row["address"] + row["size"] - address)
            file_offset = row["source_file_offset"] + address - row["address"]
            out.extend(self.data[file_offset:file_offset + available])
            address += available
            size -= available
        return bytes(out)

    def cstring(self, address):
        value = bytearray()
        for index in range(512):
            char = self.read(address + index, 1)
            if char == b"\0":
                return value.decode("ascii")
            value.extend(char)
        raise ValueError("Unterminated CE name")

    def describe(self, output):
        signature, toc = struct.unpack("<II", self.read(self.start + 0x40, 8))
        if signature != 0x43454345:
            raise ValueError("No CE ECEC marker")
        values = struct.unpack("<17I2H3I", self.read(toc, 84))
        keys = ["dllfirst", "dlllast", "physfirst", "physlast", "nummods", "RAMStart",
                "RAMFree", "RAMEnd", "CopyEntries", "CopyOffset", "ProfileLen", "ProfileOffset",
                "numfiles", "KernelFlags", "FSRamPercent", "DrivglobStart", "DrivglobLen",
                "CPUType", "MiscFlags", "Extensions", "TrackingStart", "TrackingLen"]
        header = dict(zip(keys, values))
        modules = []
        for index in range(header["nummods"]):
            row = struct.unpack("<8I", self.read(toc + 84 + index * 32, 32))
            name = self.cstring(row[4])
            e32 = self.read(row[5], 108)
            nsections, flags, entry_rva, vbase = struct.unpack_from("<HHII", e32)
            major, minor = struct.unpack_from("<HH", e32, 12)
            sections = []
            for section_index in range(nsections):
                s = struct.unpack("<6I", self.read(row[6] + section_index * 24, 24))
                section = dict(zip(["vsize", "rva", "psize", "dataptr", "realaddr", "flags"], s))
                # Uncompressed psize can include alignment padding omitted by
                # B000FF records. Only vsize bytes carry module content.
                stored_length = min(s[2], s[0])
                raw = self.read(s[3], stored_length) if stored_length else b""
                section["stored_bytes_length"] = len(raw)
                section["alignment_padding_omitted"] = s[2] - stored_length
                section["stored_sha256"] = sha256(raw)
                section["compressed_flag"] = bool(s[5] & 0x2000)
                section["stored_bytes_path"] = f"ce_raw/modules/{name}/section_{section_index:02d}.bin"
                path = output / section["stored_bytes_path"]
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(raw)
                sections.append(section)
            modules.append({"name": name, "attributes": row[0], "filetime_low": row[1],
                            "filetime_high": row[2], "original_file_size": row[3],
                            "name_va": row[4], "e32_va": row[5], "o32_va": row[6], "load_va": row[7],
                            "e32_raw_hex": e32.hex(), "e32_imageflags": flags,
                            "entry_rva": entry_rva, "image_base": vbase,
                            "subsystem_major": major, "subsystem_minor": minor,
                            "sections": sections})
        files = []
        for index in range(header["numfiles"]):
            row = struct.unpack("<7I", self.read(toc + 84 + 32 * header["nummods"] + index * 28, 28))
            name = self.cstring(row[5])
            raw = self.read(row[6], row[4])
            path = output / "ce_raw" / "files" / (name + (".compressed" if row[3] != row[4] else ""))
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
            files.append({"name": name, "attributes": row[0], "filetime_low": row[1],
                          "filetime_high": row[2], "real_size": row[3], "stored_size": row[4],
                          "load_va": row[6], "stored_sha256": sha256(raw),
                          "stored_bytes_path": path.relative_to(output).as_posix()})
        flat = bytearray(b"\xff") * self.length
        for row in self.records:
            offset = row["address"] - self.start
            flat[offset:offset + row["size"]] = self.read(row["address"], row["size"])
        (output / "extracted" / "FirmWareAptusII.ce.flat.bin").write_bytes(flat)
        return {"source": "CaptureOne.Mac.7.1.6/FirmWareAptusII.BIN", "image_start": self.start,
                "image_length": self.length, "entry_point": self.entry, "romhdr_va": toc,
                "all_record_checksums_valid": True, "records": self.records, "romhdr": header,
                "modules": modules, "files": files,
                "flat_image_gap_fill": "0xff (synthetic; only listed records contain source bytes)"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    sources = sorted(path for path in args.root.glob("CaptureOne.*") if path.is_dir())
    inventory = []
    groups = defaultdict(list)
    header_by_sha = {}
    manifest_issues = []
    for source in sources:
        manifest = json.loads(source.with_name(source.name + "-manifest.json").read_text(encoding="utf-8-sig"))
        expected = {row["file"].replace("\\", "/"): row for row in manifest["files"]}
        seen = set()
        for path in sorted(source.rglob("*")):
            if not path.is_file():
                continue
            relative = path.relative_to(source).as_posix()
            data = path.read_bytes()
            digest = sha256(data)
            seen.add(relative)
            reference = expected.get(relative)
            if reference is None or reference["sha256"] != digest or reference["size"] != len(data):
                manifest_issues.append(f"{source.name}/{relative}: manifest mismatch")
            if digest not in header_by_sha:
                header_by_sha[digest] = leaf_header(data)
            header = header_by_sha[digest]
            row = {"source": source.name, "file": relative, "size": len(data), "sha256": digest, "header": header}
            if path.suffix.upper() == ".UPD" and header:
                version = header["field_14_be"]
                row["firmware_version"] = None if version >= 0xffffff00 else f"{version // 1000}.{version % 1000:03d}"
            inventory.append(row)
            groups[digest].append(f"{source.name}/{relative}")
        for missing in sorted(set(expected) - seen):
            manifest_issues.append(f"{source.name}/{missing}: absent")
    by_name = defaultdict(list)
    for row in inventory:
        by_name[row["file"]].append(row)
    differences = [name for name, rows in by_name.items()
                   if len({r["sha256"] for r in rows}) != 1 or len(rows) != len(sources)]
    failures = [f"{r['source']}/{r['file']}" for r in inventory if r["header"] and
                not (r["header"]["checksum_matches"] and r["header"]["payload_length_matches"])]
    summary = {"sources": [s.name for s in sources], "file_count": len(inventory),
               "unique_contents": len(groups), "per_source_count": dict(Counter(r["source"] for r in inventory)),
               "manifest_issues": manifest_issues, "header_validation_failures": failures,
               "cross_source_differences": differences,
               "all_sources_identical_by_relative_path": not differences,
               "header_file_count": sum(r["header"] is not None for r in inventory)}
    dump_json(output / "inventory.json", inventory)
    dump_json(output / "duplicate_groups.json", dict(groups))
    dump_json(output / "validation.json", summary)
    if failures or manifest_issues:
        raise ValueError("Input validation failed; see validation.json")
    extracted = output / "extracted"
    extracted.mkdir(exist_ok=True)
    chosen = sources[[s.name for s in sources].index("CaptureOne.Mac.7.1.6")]
    for filename in ("FirmWareAptusII.UPD", "FirmWareAptusII.BIN", "BootRecovery.UPD"):
        data = (chosen / filename).read_bytes()
        (extracted / (filename + ".payload.bin")).write_bytes(data[HEADER_SIZE:])
        strings = save_strings(output, filename, data)
        relevant = re.compile(r"bluetooth|RequestSync|ReSync|GetImage|GetCameraParam|SetCameraParam|"
                              r"PictureTaken|GetPictureParams|SetPictureParams|FileCommand|attachment|PacketSize|"
                              r"pSOS|pNA\+|SPP:|GAP:|Passkey|RFCOMM|PXA|WINCE420|CEVersion", re.I)
        dump_json(output / "evidence" / (filename + ".json"), [r for r in strings if relevant.search(r["text"])])
        dump_json(output / "entropy" / (filename + ".json"),
                  [{"offset": start, "length": len(data[start:start+65536]), "entropy": entropy(data[start:start+65536])}
                   for start in range(0, len(data), 65536)])
    ce = CEImage((chosen / "FirmWareAptusII.BIN").read_bytes())
    dump_json(output / "ce_inventory.json", ce.describe(output))
    rbfs = [{"file": row["file"], "sha256": row["sha256"], "size": row["size"], **row["header"]}
            for row in inventory if row["source"] == chosen.name and row["file"].lower().endswith(".ubf")]
    for row in rbfs:
        if "AptusII" in row["file"] or "Aptus_II" in row["file"]:
            destination = extracted / "fpga" / (Path(row["file"]).stem + ".rbf")
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes((chosen / row["file"]).read_bytes()[HEADER_SIZE:])
    dump_json(output / "fpga_inventory.json", rbfs)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"CE records: {len(ce.records)}; outputs: {output}")


if __name__ == "__main__":
    main()
