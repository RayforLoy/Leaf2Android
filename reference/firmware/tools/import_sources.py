"""Copy a hash-deduplicated selection from the user-provided firmware archive.

The source tree is read-only. This tool never executes firmware or vendor tools,
never deletes files, and refuses to overwrite an existing different payload.
All original source aliases and every omitted analysis output are inventoried.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


SOURCE_SETS = (
    "CaptureOne.Mac.7.1.6",
    "CaptureOne.Mac.7.1.4",
    "CaptureOne.Win.11.3.2",
    "LeafCapture.Win.11.2.9",
)
PREFERRED = ("FirmWareAptusII.UPD", "FirmWareAptusII.BIN", "BootRecovery.UPD")
ROOT_ANALYSIS_SUFFIXES = {".txt", ".json", ".log", ".py"}
KEEP_ANALYSIS_DIRS = {"disassembly", "evidence", "entropy", "strings"}
KEEP_RECONSTRUCTED = {
    "ce_reconstructed/fs/Windows/LeafCE.exe",
    "ce_reconstructed/Registry/default.reg",
    "ce_reconstructed/rom_meta.json",
    "ce_reconstructed/attributes.ini",
}


def sha256(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def copy_exact(source: Path, destination: Path, digest: str) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        if sha256(destination) != digest:
            raise ValueError(f"Refusing to overwrite different bytes: {destination}")
    else:
        shutil.copy2(source, destination)
    if source.stat().st_size != destination.stat().st_size or sha256(destination) != digest:
        raise ValueError(f"Copied payload failed integrity verification: {destination}")


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def import_sources(source_root: Path, output_root: Path) -> dict:
    source_root = source_root.resolve(strict=True)
    output_root = output_root.resolve()
    if source_root == output_root or source_root in output_root.parents or output_root in source_root.parents:
        raise ValueError("Source and destination trees must not overlap")
    aliases, provenance, canonical, retained_analysis, omitted_analysis = [], [], {}, [], []
    for source_set in SOURCE_SETS:
        manifest_path = source_root / (source_set + "-manifest.json")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
        manifest_target = output_root / "provenance" / manifest_path.name
        copy_exact(manifest_path, manifest_target, sha256(manifest_path))
        provenance.append({
            "source_set": source_set,
            "manifest": manifest_target.relative_to(output_root).as_posix(),
            "manifest_sha256": sha256(manifest_path),
            "original_metadata": {key: value for key, value in manifest.items() if key != "files"},
        })
        entries = sorted(manifest["files"], key=lambda item: (
            PREFERRED.index(item.get("file", item.get("path")))
            if item.get("file", item.get("path")) in PREFERRED else len(PREFERRED),
            item.get("file", item.get("path")),
        ))
        for entry in entries:
            relative = entry.get("file", entry.get("path"))
            source = (source_root / source_set / relative).resolve(strict=True)
            if source_root not in source.parents:
                raise ValueError(f"Manifest escapes source root: {source}")
            digest, size = sha256(source), source.stat().st_size
            if digest != entry["sha256"] or size != entry["size"]:
                raise ValueError(f"Source disagrees with supplied manifest: {source}")
            if digest not in canonical:
                target = output_root / "originals" / source_set / relative
                copy_exact(source, target, digest)
                canonical[digest] = target.relative_to(output_root).as_posix()
            aliases.append({
                "source_set": source_set,
                "original_relative_path": relative,
                "size": size,
                "sha256": digest,
                "canonical_path": canonical[digest],
                "original_entry": entry,
            })
    original_readme = source_root / "README.txt"
    copy_exact(original_readme, output_root / "provenance" / "original-README.txt", sha256(original_readme))
    analysis_root = source_root / "analysis"
    for source in sorted(analysis_root.rglob("*")):
        if not source.is_file():
            continue
        relative = source.relative_to(analysis_root).as_posix()
        if "__pycache__" in source.parts or source.suffix == ".pyc":
            continue
        keep = (
            ("/" not in relative and source.suffix in ROOT_ANALYSIS_SUFFIXES)
            or relative.split("/")[0] in KEEP_ANALYSIS_DIRS
            or relative in KEEP_RECONSTRUCTED
        )
        digest = sha256(source)
        item = {"original_relative_path": relative, "size": source.stat().st_size, "sha256": digest}
        if keep:
            target = output_root / "analysis" / "supplied" / relative
            copy_exact(source, target, digest)
            item["retained_path"] = target.relative_to(output_root).as_posix()
            retained_analysis.append(item)
        else:
            item["omission_reason"] = (
                "third-party dependency; see retained third-party-tools.json"
                if relative.startswith("vendor/") else
                "reproducible extracted/reconstructed output; canonical original firmware retained"
            )
            omitted_analysis.append(item)
    write_json(output_root / "provenance" / "source-aliases.json", {
        "schema_version": 1,
        "policy": "One byte-identical canonical file per SHA-256; all original names remain aliases.",
        "source_entry_count": len(aliases),
        "source_entry_bytes": sum(item["size"] for item in aliases),
        "unique_payload_count": len(canonical),
        "unique_payload_bytes": sum(item["size"] for item in aliases if item["canonical_path"] == "originals/" + item["source_set"] + "/" + item["original_relative_path"]),
        "sources": provenance,
        "aliases": aliases,
    })
    report = {
        "schema_version": 1,
        "source_root": str(source_root),
        "method": "Read-only static source import; vendor code not executed; every retained file checked by SHA-256 and size.",
        "retained_analysis": retained_analysis,
        "omitted_analysis": omitted_analysis,
        "excluded_external_inputs": [
            {"path": r"D:\leaf\Leaf Capture 11.2.9 Windows .exe", "reason": "113212841-byte installer; embedded firmware and original manifest retained"},
            {"path": r"D:\leaf\LEAFDM SYS\Lens_Calibration", "reason": "device-specific lens calibration, not executable firmware"},
            {"path": r"D:\leaf\LEAFDM SYS", "reason": "camera settings/calibration data; no firmware executable found"},
            {"path": r"D:\leaf\ccd_analysis_20261019", "reason": "unrelated CCD analysis and personal image data"},
        ],
        "retained_analysis_count": len(retained_analysis),
        "retained_analysis_bytes": sum(item["size"] for item in retained_analysis),
        "omitted_analysis_count": len(omitted_analysis),
        "omitted_analysis_bytes": sum(item["size"] for item in omitted_analysis),
    }
    write_json(output_root / "provenance" / "analysis-selection.json", report)
    return {key: value for key, value in report.items() if not isinstance(value, list)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_root", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    print(json.dumps(import_sources(args.source_root, args.output), indent=2))


if __name__ == "__main__":
    main()
