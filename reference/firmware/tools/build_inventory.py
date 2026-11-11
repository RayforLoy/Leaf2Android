"""Build or verify the independent firmware reference inventory."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def classification(relative: str) -> str:
    suffix = Path(relative).suffix.lower()
    if relative.startswith("originals/"):
        if suffix == ".bin":
            return "original-windows-ce-load-record-image"
        if suffix == ".upd":
            return "original-recovery-image" if Path(relative).name == "BootRecovery.UPD" else "original-main-firmware-image"
        if suffix == ".ubf":
            return "original-board-fpga-payload"
        return "original-manufacturer-configuration"
    if relative.endswith("ce_reconstructed/fs/Windows/LeafCE.exe"):
        return "derived-windows-ce-ui-executable-not-flashable"
    if relative.startswith("analysis/supplied/"):
        return "supplied-static-analysis-or-reconstruction"
    if relative.startswith(("analysis/controls-aptus-classic/", "analysis/controls-5.041/")):
        if relative.endswith(".payload.bin"):
            return "derived-powerpc-payload-not-flashable"
        if suffix == ".py":
            return "leaf2android-reproduction-tool"
        if suffix == ".md":
            return "leaf2android-documentation"
        return "leaf2android-static-control-evidence"
    if relative.startswith("analysis/"):
        return "leaf2android-static-analysis"
    if relative.startswith("provenance/"):
        return "provenance-and-source-alias-manifest"
    if relative.startswith("tools/"):
        return "leaf2android-reproduction-tool"
    return "leaf2android-documentation"


def build(root: Path) -> dict:
    aliases = json.loads((root / "provenance/source-aliases.json").read_text(encoding="utf-8"))
    original_sources = {}
    for item in aliases["aliases"]:
        original_sources.setdefault(item["canonical_path"], []).append({
            "source_set": item["source_set"],
            "original_relative_path": item["original_relative_path"],
        })
    selection = json.loads((root / "provenance/analysis-selection.json").read_text(encoding="utf-8"))
    analysis_sources = {item["retained_path"]: item["original_relative_path"]
                        for item in selection["retained_analysis"]}
    entries = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or "__pycache__" in path.parts or path.suffix == ".pyc":
            continue
        relative = path.relative_to(root).as_posix()
        if relative == "inventory.json":
            continue
        item = {"path": relative, "size": path.stat().st_size,
                "sha256": digest(path), "classification": classification(relative)}
        if relative in original_sources:
            item["original_sources"] = original_sources[relative]
        if relative in analysis_sources:
            item["supplied_analysis_relative_path"] = analysis_sources[relative]
        if relative.startswith("analysis/controls-aptus-classic/"):
            item["version"] = "4.701"
            item["derived_from"] = ["originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD"]
        elif relative.startswith("analysis/controls-5.041/"):
            item["version"] = "5.041"
            item["derived_from"] = ["originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD"]
        entries.append(item)
    return {
        "schema_version": 1,
        "project": "Leaf2Android",
        "scope": "reference/firmware; inventory.json excludes itself",
        "integrity": "SHA-256 over exact file bytes, independent of paths and timestamps",
        "source_entry_count": aliases["source_entry_count"],
        "unique_original_payload_count": aliases["unique_payload_count"],
        "unique_original_payload_bytes": aliases["unique_payload_bytes"],
        "file_count": len(entries),
        "total_bytes": sum(item["size"] for item in entries),
        "single_files_over_90_mib": [item["path"] for item in entries if item["size"] > 90 * 1024 * 1024],
        "files": entries,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    actual = build(args.root)
    target = args.root / "inventory.json"
    if args.verify:
        expected = json.loads(target.read_text(encoding="utf-8"))
        if actual != expected:
            raise SystemExit("Firmware inventory mismatch; rebuild only after reviewing the changed files")
    else:
        target.write_text(json.dumps(actual, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"verified": args.verify, "file_count": actual["file_count"],
                      "total_bytes": actual["total_bytes"],
                      "single_files_over_90_mib": actual["single_files_over_90_mib"]}, indent=2))


if __name__ == "__main__":
    main()
