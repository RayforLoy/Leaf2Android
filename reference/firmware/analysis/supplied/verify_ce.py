"""Verify reconstructed PEs against independently parsed CE module sections."""

from collections import Counter
from pathlib import Path
import hashlib
import json
import pefile

from analyze_firmware import dump_json, save_strings


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    root = Path(__file__).resolve().parent
    inventory = json.loads((root / "ce_inventory.json").read_text(encoding="utf-8"))
    logs = json.loads((root / "ce_decompression.json").read_text(encoding="utf-8"))
    by_compressed_hash = {row["source_sha256"]: row for row in logs}
    win = root / "ce_reconstructed" / "fs" / "Windows"
    results = []
    for module in inventory["modules"]:
        path = win / module["name"]
        pe = pefile.PE(str(path))
        if pe.OPTIONAL_HEADER.ImageBase != module["image_base"]:
            raise ValueError(f"Image base mismatch: {module['name']}")
        sections = []
        for index, section in enumerate(module["sections"]):
            if not section["psize"]:
                continue
            raw = (root / section["stored_bytes_path"]).read_bytes()
            size = min(section["psize"], section["vsize"])
            reconstructed = pe.get_data(section["rva"], section["vsize"] if section["compressed_flag"] else size)
            if section["compressed_flag"]:
                check = by_compressed_hash[digest(raw)]
                expected = check["decompressed_sha256"]
                if len(reconstructed) != section["vsize"]:
                    raise ValueError(f"Short decompressed section: {module['name']}:{index}")
            else:
                expected = digest(raw)
            if digest(reconstructed) != expected:
                raise ValueError(f"Section mismatch: {module['name']}:{index}")
            sections.append({"index": index, "rva": section["rva"], "checked_length": len(reconstructed),
                             "sha256": digest(reconstructed), "matches_source_or_checked_lzx": True})
        resource_types = {}
        for entry in getattr(getattr(pe, "DIRECTORY_ENTRY_RESOURCE", None), "entries", []):
            resource_types[str(entry.name) if entry.name else str(entry.id)] = len(entry.directory.entries)
        results.append({"name": module["name"], "path": path.relative_to(root).as_posix(),
                        "sha256": digest(path.read_bytes()), "machine": pe.FILE_HEADER.Machine,
                        "image_base": pe.OPTIONAL_HEADER.ImageBase,
                        "entry_point_va": pe.OPTIONAL_HEADER.ImageBase + pe.OPTIONAL_HEADER.AddressOfEntryPoint,
                        "source_sections_verified": sections, "resource_types": resource_types})
        pe.close()
    files = []
    for file in inventory["files"]:
        data = (win / file["name"]).read_bytes()
        if len(data) != file["real_size"]:
            raise ValueError(f"File size mismatch: {file['name']}")
        if file["stored_size"] != file["real_size"]:
            log = by_compressed_hash[file["stored_sha256"]]
            if log["virtual_zero_tail"]:
                raise ValueError(f"Unexpected virtual tail on file: {file['name']}")
            expected = log["decompressed_sha256"]
        else:
            expected = file["stored_sha256"]
        if digest(data) != expected:
            raise ValueError(f"File mismatch: {file['name']}")
        files.append({"name": file["name"], "sha256": digest(data), "verified": True})
    output = {"modules_verified": len(results), "files_verified": len(files),
              "source_sections_verified": sum(len(m["source_sections_verified"]) for m in results),
              "lzx_calls": len(logs), "modules": results, "files": files,
              "limitations": ["PE headers are reconstructed; binaries are analysis artifacts, not flash images.",
                              "ROM split addresses and shared RVAs remain in original metadata and .cerom.",
                              "Synthetic padding is excluded from original uncompressed section comparisons."]}
    dump_json(root / "ce_verification.json", output)
    leaf = (win / "LeafCE.exe").read_bytes()
    rows = save_strings(root, "LeafCE.reconstructed", leaf)
    needles = ("Bluetooth", "bluetooth", "RequestSync", "GetImage", "GetCameraParam", "SetCameraParam",
               "PictureTaken", "CEVersion", "PinCode", "CurrentTime", "FileCommand")
    dump_json(root / "evidence" / "LeafCE.reconstructed.json",
              [row for row in rows if any(needle in row["text"] for needle in needles)])
    print(f"Verified {len(results)} modules / {output['source_sections_verified']} sections and {len(files)} files")
    print("LeafCE resource types:", next(m["resource_types"] for m in results if m["name"] == "LeafCE.exe"))


if __name__ == "__main__":
    main()
