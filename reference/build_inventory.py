"""Build or read-only verify reference hashes without running manufacturer code.

The WiView six-input lock and firmware source-alias lock remain independent.
Only this root inventory and Python bytecode caches are outside the inventory;
nested inventories are ordinary inventoried evidence. Uses the standard library.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

REFERENCE = Path(__file__).resolve().parent
ROOT = REFERENCE.parent
REPORT_DATE = "2026-11-11"  # Evidence revision date, deliberately not today's clock.
FIRMWARE_PREFIX = "reference/firmware/"
FIRMWARE_LOCK = FIRMWARE_PREFIX + "provenance/source-aliases.json"


def sha256(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def category(relative: str) -> str:
    name = Path(relative).name
    if relative.startswith(FIRMWARE_PREFIX + "originals/"):
        suffix = Path(relative).suffix.lower()
        if suffix == ".upd":
            return "original_back_recovery_image" if name == "BootRecovery.UPD" else "original_back_main_firmware_image"
        if suffix == ".bin":
            return "original_back_windows_ce_rom_container"
        if suffix == ".ubf":
            return "original_back_board_fpga_payload"
        return "original_back_manufacturer_configuration"
    if relative == FIRMWARE_PREFIX + "inventory.json":
        return "nested_firmware_inventory"
    if relative == FIRMWARE_LOCK:
        return "firmware_pinned_source_alias_manifest"
    if relative.startswith(FIRMWARE_PREFIX + "provenance/"):
        return "firmware_source_provenance"
    if relative.endswith("ce_reconstructed/fs/Windows/LeafCE.exe"):
        return "reconstructed_back_windows_ce_ui_executable"
    if relative.startswith(FIRMWARE_PREFIX + "analysis/supplied/"):
        return "supplied_firmware_static_analysis"
    if relative.startswith((FIRMWARE_PREFIX + "analysis/controls-aptus-classic/", FIRMWARE_PREFIX + "analysis/controls-5.041/")):
        if relative.endswith(".payload.bin"):
            return "derived_back_powerpc_payload_not_flashable"
        if name.endswith(".py"):
            return "firmware_control_evidence_tool"
        if name.endswith(".md"):
            return "firmware_control_analysis_documentation"
        return "generated_firmware_static_control_evidence"
    if relative.startswith(FIRMWARE_PREFIX + "analysis/"):
        return "firmware_control_analysis_documentation"
    if relative.startswith(FIRMWARE_PREFIX + "tools/"):
        return "firmware_reference_maintenance_tool"
    if relative.startswith(FIRMWARE_PREFIX):
        return "firmware_reference_documentation"
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
    if name == "wiview-control-evidence.json":
        return "verified_static_client_control_evidence"
    if name == "lab-candidates.json":
        return "experimental_firmware_candidate_manifest"
    if name == "version-comparison.json":
        return "generated_static_version_comparison"
    if name == "sources.lock.json":
        return "pinned_input_manifest"
    if name == "relocation-manifest.json":
        return "relocation_audit"
    if name == "build_inventory.py":
        return "inventory_maintenance_tool"
    return "project_documentation"


def firmware_version(alias: dict) -> str | None:
    entry = alias["original_entry"]
    header = entry.get("leaf_header", {})
    return entry.get("firmware_version") or header.get("main_firmware_version") or header.get("interface_version")


def validate_pin(path: str, size: int, digest: str, expected: dict) -> None:
    if size != expected["size"] or digest != expected["sha256"]:
        raise ValueError(f"Pinned source size/SHA-256 mismatch: {path}")


def build_report() -> dict:
    lock = json.loads((REFERENCE / "sources.lock.json").read_text(encoding="utf-8"))
    sources = {item["path"]: item for item in lock["sources"]}
    firmware_lock = json.loads((ROOT / FIRMWARE_LOCK).read_text(encoding="utf-8"))
    source_manifests = {
        source["source_set"]: FIRMWARE_PREFIX + source["manifest"]
        for source in firmware_lock["sources"]
    }
    for source in firmware_lock["sources"]:
        manifest_path = ROOT / source_manifests[source["source_set"]]
        if sha256(manifest_path) != source["manifest_sha256"]:
            raise ValueError(f"Firmware provenance manifest SHA-256 mismatch: {manifest_path}")
    firmware_sources: dict[str, list[dict]] = {}
    for alias in firmware_lock["aliases"]:
        relative = FIRMWARE_PREFIX + alias["canonical_path"]
        if not relative.startswith(FIRMWARE_PREFIX + "originals/") or ".." in Path(relative).parts:
            raise ValueError(f"Firmware alias is outside originals: {relative}")
        firmware_sources.setdefault(relative, []).append(alias)
    if len(firmware_lock["aliases"]) != firmware_lock["source_entry_count"]:
        raise ValueError("Firmware alias count disagrees with its source lock")
    if sum(item["size"] for item in firmware_lock["aliases"]) != firmware_lock["source_entry_bytes"]:
        raise ValueError("Firmware source byte total disagrees with its source lock")
    if len(firmware_sources) != firmware_lock["unique_payload_count"]:
        raise ValueError("Firmware canonical count disagrees with its source lock")
    rows, seen_wiview, seen_firmware = [], set(), set()
    for path in sorted(REFERENCE.rglob("*")):
        if (not path.is_file() or path == REFERENCE / "inventory.json"
                or "__pycache__" in path.parts or path.suffix == ".pyc"):
            continue
        relative = path.relative_to(ROOT).as_posix()
        source = sources.get(relative)
        version = "1.0.3" if "1.0.3" in relative or "_1_0_3" in relative else "1.0.2" if "1.0.2" in relative else None
        row = {
            "path": relative,
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
            "classification": category(relative),
            "version": version,
        }
        if source:
            validate_pin(relative, row["bytes"], row["sha256"], source)
            seen_wiview.add(relative)
            row.update(source_id=source["id"], source_url=source.get("url"), provenance=source["provenance"])
        elif relative in firmware_sources:
            aliases = firmware_sources[relative]
            for alias in aliases:
                validate_pin(relative, row["bytes"], row["sha256"], alias)
            seen_firmware.add(relative)
            versions = sorted({value for item in aliases if (value := firmware_version(item)) is not None})
            row.update(
                version=versions[0] if len(versions) == 1 else None,
                source_lock=FIRMWARE_LOCK,
                source_manifests=sorted({source_manifests[item["source_set"]] for item in aliases}),
                source_alias_count=len(aliases),
                source_aliases=[{"source_set": item["source_set"], "original_relative_path": item["original_relative_path"]} for item in aliases],
                provenance="User-supplied manufacturer firmware bytes; exact size/SHA-256 verified against the separate firmware source-alias lock and supplied package-extraction manifests. URLs, where recorded, identify the installer, not an invented direct firmware download.",
            )
            if len(versions) > 1:
                row["recorded_versions"] = versions
        elif relative.startswith(FIRMWARE_PREFIX):
            row["version"] = None
            row["source_lock"] = FIRMWARE_LOCK
            if relative.startswith(FIRMWARE_PREFIX + "analysis/supplied/"):
                row["provenance_manifest"] = FIRMWARE_PREFIX + "provenance/analysis-selection.json"
                row["provenance"] = "User-supplied firmware static analysis and reconstructed outputs retained byte-for-byte; the selection manifest preserves original paths and SHA-256 values."
            elif relative.startswith(FIRMWARE_PREFIX + "provenance/"):
                row["provenance"] = "Original supplied source manifests/notes or the explicit source-alias/analysis-selection mapping; no direct firmware URL has been inferred."
            else:
                row["provenance"] = "Leaf2Android firmware reference documentation, reproducible static evidence, or maintenance metadata; separate from original manufacturer bytes."
            if "/controls-aptus-classic/" in relative:
                row["version"] = "4.701"
                row["derived_from"] = [FIRMWARE_PREFIX + "originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD"]
            elif "/controls-5.041/" in relative:
                row["version"] = "5.041"
                row["derived_from"] = [FIRMWARE_PREFIX + "originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD"]
        elif relative == "reference/analysis/lab-candidates.json":
            row["derived_from"] = [
                "reference/analysis/wiview-control-evidence.json",
                FIRMWARE_PREFIX + "originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD",
                FIRMWARE_PREFIX + "originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD",
            ]
            row["provenance"] = "Static experimental candidate metadata. Confirmed vendor constructions, firmware parser/handler evidence and unconfirmed research names are distinguished; user-created hypothesis headers are not extracted vendor syntax."
        elif relative.startswith("reference/analysis/"):
            row["derived_from"] = [item["path"] for item in lock["sources"] if item["path"].lower().endswith(".cab") and (version is None or version in item["path"])]
            row["provenance"] = "Extracted PDA client bytes or generated static-analysis data; separate from camera-back firmware."
        elif relative.startswith("reference/wiview/provenance/"):
            row["provenance"] = "Original retrieval log or public archive metadata retained unchanged."
        else:
            row["provenance"] = "Leaf2Android project-maintained reference metadata/documentation."
        rows.append(row)
    missing = (set(sources) - seen_wiview) | (set(firmware_sources) - seen_firmware)
    if missing:
        raise ValueError("Missing pinned source files: " + ", ".join(sorted(missing)))
    originals = [row for row in rows if row["path"] in firmware_sources]
    counts = Counter(row["classification"] for row in originals)
    main_count = counts["original_back_main_firmware_image"]
    recovery_count = counts["original_back_recovery_image"]
    rom_count = counts["original_back_windows_ce_rom_container"]
    original_bytes = sum(row["bytes"] for row in originals)
    if original_bytes != firmware_lock["unique_payload_bytes"]:
        raise ValueError("Firmware retained byte total disagrees with its source lock")
    return {
        "schema_version": 2,
        "date": REPORT_DATE,
        "project": "Leaf2Android",
        "scope": "All files under reference except reference/inventory.json and Python bytecode caches; nested inventories are included.",
        "file_count": len(rows),
        "total_bytes": sum(row["bytes"] for row in rows),
        "largest_file_bytes": max((row["bytes"] for row in rows), default=0),
        "files_exceeding_90_mib": [row["path"] for row in rows if row["bytes"] > 90 * 1024 * 1024],
        "camera_back_firmware": {
            "present": bool(main_count or recovery_count or rom_count),
            "image_count": main_count + recovery_count + rom_count,
            "image_count_definition": "Unique original main/recovery UPD images plus Windows CE BIN ROM containers; UBF board payloads and configuration are counted separately.",
            "main_firmware_image_count": main_count,
            "recovery_image_count": recovery_count,
            "windows_ce_rom_container_count": rom_count,
            "board_fpga_payload_count": counts["original_back_board_fpga_payload"],
            "manufacturer_configuration_count": counts["original_back_manufacturer_configuration"],
            "unique_original_file_count": len(originals),
            "unique_original_bytes": original_bytes,
            "source_alias_count": firmware_lock["source_entry_count"],
            "source_alias_bytes": firmware_lock["source_entry_bytes"],
            "source_set_count": len(firmware_lock["sources"]),
            "source_lock": FIRMWARE_LOCK,
            "independent_inventory": FIRMWARE_PREFIX + "inventory.json",
            "primary_aptus22_analysis": FIRMWARE_PREFIX + "analysis/controls-aptus-classic/evidence.json",
            "shared_dialect_comparison": FIRMWARE_PREFIX + "analysis/controls-5.041/extraction.json",
            "note": "Classic shared 4.701 contains an Aptus 22 model descriptor and is the primary Aptus 22-related evidence, not a model-exclusive flashing package. Aptus II has no Bluetooth hardware; shared PDA code in 5.041 does not establish a radio. Original names/bytes and all 310 aliases are retained; actual wireless effects remain device/firmware-specific.",
        },
        "pinned_inputs": len(sources),
        "firmware_pinned_inputs": len(firmware_sources),
        "source_locks": [
            {"path": "reference/sources.lock.json", "scope": "WiView original client media and guides", "unique_input_count": len(sources)},
            {"path": FIRMWARE_LOCK, "scope": "Firmware original payloads and source aliases", "unique_input_count": len(firmware_sources), "source_alias_count": len(firmware_lock["aliases"])},
        ],
        "entries": rows,
    }


def difference(expected: dict, actual: dict) -> str:
    expected_rows = {item["path"]: item for item in expected.get("entries", [])}
    actual_rows = {item["path"]: item for item in actual["entries"]}
    missing = sorted(expected_rows.keys() - actual_rows.keys())
    added = sorted(actual_rows.keys() - expected_rows.keys())
    changed = sorted(key for key in expected_rows.keys() & actual_rows.keys() if expected_rows[key] != actual_rows[key])
    details = [f"missing={len(missing)}", f"added={len(added)}", f"changed={len(changed)}"]
    for label, values in (("Missing", missing), ("Added", added), ("Changed", changed)):
        if values:
            details.append(label + ": " + ", ".join(values[:10]))
    if not (missing or added or changed):
        details.append("Report-level metadata differs")
    return "; ".join(details)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Compare all current entries, sizes, SHA-256 and metadata; never write files")
    args = parser.parse_args()
    try:
        report = build_report()
        target = REFERENCE / "inventory.json"
        if args.verify:
            expected = json.loads(target.read_text(encoding="utf-8"))
            if report != expected:
                raise ValueError("Reference inventory mismatch: " + difference(expected, report))
        else:
            target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise SystemExit(str(error)) from error
    mode = "Verified" if args.verify else "Built"
    firmware = report["camera_back_firmware"]
    print(f"{mode} reference inventory: {report['file_count']} files, {report['total_bytes']} bytes; "
          f"WiView inputs={report['pinned_inputs']}, firmware originals={firmware['unique_original_file_count']}, "
          f"UPD/BIN images/containers={firmware['image_count']}, source aliases={firmware['source_alias_count']}")


if __name__ == "__main__":
    main()
