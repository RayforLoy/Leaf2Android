"""Build a SHA-256 inventory of reference files without executing manufacturer code."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

REFERENCE = Path(__file__).resolve().parent
ROOT = REFERENCE.parent


def category(relative: str) -> str:
    name = Path(relative).name
    if relative.startswith("reference/firmware/"):
        return "firmware_absence_notice"
    if relative.startswith("reference/wiview/provenance/"):
        return "acquisition_provenance"
    if relative.lower().endswith(".cab"):
        return "windows_ce_client_installer"
    if relative.endswith("Installer.data"):
        return "macos_client_installer_data"
    if relative.endswith("ReadMe.txt"):
        return "manufacturer_readme_rtf"
    if relative.lower().endswith(".pdf"):
        return "manufacturer_installation_guide"
    if "/extracted/" in relative or "/legacy-extraction/" in relative:
        return "extracted_windows_ce_client_component"
    if name.endswith(".strings.json"):
        return "generated_static_strings"
    if name == "binary-analysis.json":
        return "generated_pe_analysis"
    if name == "protocol-evidence.json":
        return "verified_static_protocol_anchors"
    if name == "version-comparison.json":
        return "generated_static_version_comparison"
    if name == "sources.lock.json":
        return "pinned_input_manifest"
    if name == "relocation-manifest.json":
        return "relocation_audit"
    if name == "build_inventory.py":
        return "inventory_maintenance_tool"
    return "project_documentation"


def main() -> None:
    lock = json.loads((REFERENCE / "sources.lock.json").read_text(encoding="utf-8"))
    sources = {item["path"]: item for item in lock["sources"]}
    rows = []
    for path in sorted(REFERENCE.rglob("*")):
        if not path.is_file() or path.name == "inventory.json" or "__pycache__" in path.parts:
            continue
        relative = path.relative_to(ROOT).as_posix()
        source = sources.get(relative)
        version = "1.0.3" if "1.0.3" in relative or "_1_0_3" in relative else "1.0.2" if "1.0.2" in relative else None
        row = {
            "path": relative,
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "classification": category(relative),
            "version": version,
        }
        if source:
            row["source_id"] = source["id"]
            row["source_url"] = source.get("url")
            row["provenance"] = source["provenance"]
            assert row["bytes"] == source["size"] and row["sha256"] == source["sha256"], relative
        elif relative.startswith("reference/analysis/"):
            row["derived_from"] = [item["path"] for item in lock["sources"] if item["path"].lower().endswith(".cab") and (version is None or version in item["path"])]
            row["provenance"] = "Extracted client bytes or generated static-analysis data; not camera-back firmware."
        elif relative.startswith("reference/wiview/provenance/"):
            row["provenance"] = "Original retrieval log or public archive metadata retained unchanged."
        else:
            row["provenance"] = "Leaf2Android project-maintained reference metadata/documentation."
        rows.append(row)
    report = {
        "schema_version": 1,
        "date": "2026-11-10",
        "project": "Leaf2Android",
        "scope": "All files under reference except this inventory and Python bytecode caches.",
        "file_count": len(rows),
        "total_bytes": sum(row["bytes"] for row in rows),
        "largest_file_bytes": max((row["bytes"] for row in rows), default=0),
        "files_exceeding_90_mib": [row["path"] for row in rows if row["bytes"] > 90 * 1024 * 1024],
        "camera_back_firmware": {"present": False, "image_count": 0, "note": "No identifiable Aptus / Valeo camera-back firmware image was supplied. Extracted ARM PE files are Windows CE WiView client components."},
        "pinned_inputs": len(sources),
        "entries": rows,
    }
    (REFERENCE / "inventory.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Reference inventory: {report['file_count']} files, {report['total_bytes']} bytes, largest {report['largest_file_bytes']} bytes; camera-back firmware images: 0")


if __name__ == "__main__":
    main()
