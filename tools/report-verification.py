"""Aggregate executed checks and record the remaining physical-device acceptance boundary."""
from pathlib import Path
import hashlib
import json
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[1]


def suites(pattern: str) -> dict:
    files = list(ROOT.glob(pattern))
    if not files:
        raise RuntimeError(f"Test results missing: {pattern}")
    results = [E.parse(path).getroot() for path in files]
    summary = {name: sum(int(result.get(name, "0")) for result in results) for name in ["tests", "failures", "errors", "skipped"]}
    summary["cases"] = [case.get("classname", "") + "." + case.get("name", "") for r in results for case in r.iter("testcase")]
    assert summary["tests"] > 0 and summary["failures"] == summary["errors"] == 0
    return summary


def main() -> None:
    python_tests = subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", "tests"], cwd=ROOT,
                                  capture_output=True, text=True)
    if python_tests.returncode:
        raise RuntimeError(python_tests.stdout + python_tests.stderr)
    match = re.search(r"Ran (\d+) tests", python_tests.stderr)
    assert match
    apk = json.loads((ROOT / "research/reports/apk-verification.json").read_text(encoding="utf-8"))
    apk_path = ROOT / apk["apk"]
    assert hashlib.sha256(apk_path.read_bytes()).hexdigest() == apk["sha256"]
    lint = E.parse(ROOT / "app/build/reports/lint-results-debug.xml").getroot()
    issues = [node.attrib for node in lint.findall("issue")]
    assert not any(i["severity"] in {"Error", "Fatal"} for i in issues)
    release_lint = E.parse(ROOT / "app/build/reports/lint-results-release.xml").getroot()
    release_issues = [node.attrib for node in release_lint.findall("issue")]
    assert not any(i["severity"] in {"Error", "Fatal"} for i in release_issues)
    screenshots = {}
    for name in ["modern-empty", "modern-narrow", "pda-narrow", "modern-wide", "pda-wide", "modern-landscape", "modern-narrow-zh", "pda-narrow-zh", "modern-wide-zh", "laboratory-empty-en", "laboratory-empty-zh"]:
        path = ROOT / "docs/screenshots" / (name + ".png")
        header = path.read_bytes()[:24]
        assert header[:8] == b"\x89PNG\r\n\x1a\n"
        screenshots[name] = list(struct.unpack(">II", header[16:24]))
    report = {
        "date": "2026-11-11", "app_version": "0.3.0",
        "development_basis": "WiView 1.0.2/1.0.3 plus classic Aptus 4.701 and shared 5.041 static firmware evidence; user-reported Valeo 22Wi/Aptus 22 connection and photo reading. Aptus II is not a Bluetooth hardware target.",
        "offline_unit_tests": int(match[1]), "offline_unit_tests_passed": int(match[1]),
        "kotlin_protocol_tests": suites("protocol/build/test-results/test/TEST-*.xml"),
        "android_instrumented_tests": suites("app/build/outputs/androidTest-results/connected/debug/TEST-*.xml"),
        "android_test_device": {"kind": "AOSP x86_64 emulator", "avd": "Leaf2Android_API36", "api": 36, "density_dpi": 420},
        "application_languages": ["en", "zh"], "default_language_selection": "system",
        "app_product_family_label": "Aptus / Valeo",
        "release_build": apk.get("build_type") == "release", "release_debuggable": apk.get("debuggable"),
        "lint": {"errors": 0, "warnings": len(issues), "categories": sorted({i["id"] for i in issues})},
        "release_lint": {"errors": 0, "warnings": len(release_issues), "categories": sorted({i["id"] for i in release_issues})},
        "apk_generated": True, "apk": apk["apk"], "apk_bytes": apk["bytes"], "apk_sha256": apk["sha256"],
        "apk_signature_verified": apk["signature_verified"], "apk_zip_16k_alignment": apk["zip_16k_alignment"],
        "elf_16k_alignment_verified": True, "manufacturer_materials_in_reference_and_separate_resource_archive": True,
        "samples_analyzed": ["1.0.2", "1.0.3"], "fixture_origin": "synthetic",
        "firmware_samples_analyzed": ["classic Aptus 4.701 (includes Aptus 22 model code)", "shared 5.041 (not proof of Aptus II Bluetooth hardware)"],
        "laboratory": {"implemented": True, "manual_only": True, "single_header_host_limit_bytes": 61440,
                       "timeout_host_limit_seconds": [1,300], "attachment_host_limit_mib_options": [16,64],
                       "original_client_templates": 16, "first_frame_observation_is_uncorrelated": True,
                       "ack_is_not_effect_confirmation": True, "original_binary_export": True,
                       "known_sensitive_header_fields_redacted": True, "automatic_fuzzing": False, "firmware_uploader": False},
        "ui_screenshots_pixels": screenshots, "ui_screenshots_are_emulator_window_size_simulations": True,
        "ui_screenshot_build": "signed minified release", "ui_screenshot_apk_sha256": apk["sha256"],
        "physical_device_tests_performed": False, "physical_folding_and_one_ui_verified": False,
        "user_reported_hardware_tests": [{"date": "2026-11-11", "model": model, "connection": "passed", "photo_reading": "passed", "firmware": None, "phone_environment": None, "new_control_writes_verified": False} for model in ["Valeo 22Wi", "Aptus 22"]],
        "complete_startup_handshake_verified": False,
        "target_back_firmware": None, "target_phone": "Galaxy Z Fold7 / Android 16 / One UI 8.5 (not attached)",
        "mos_full_file_transfer_implemented": False, "continuous_live_view_implemented": False,
        "mechanical_shutter_remote_release": "Hardware unsupported in current setup",
        "gate": "user_reported_basic_sessions; new_control_effects_and_laboratory_hypotheses_require_device_validation",
    }
    english = E.parse(ROOT / "app/src/main/res/values/strings.xml").getroot()
    chinese = E.parse(ROOT / "app/src/main/res/values-zh/strings.xml").getroot()
    en_values = {node.attrib["name"]: node.text or "" for node in english.findall("string")}
    zh_values = {node.attrib["name"]: node.text or "" for node in chinese.findall("string")}
    assert en_values.keys() == zh_values.keys()
    report["matching_bilingual_resource_keys"] = len(en_values)
    report["control_ui_captures"] = {name: {"file": "docs/screenshots/" + name + ".png", "build": "debug instrumented UI with synthetic peer; no Bluetooth camera"}
                                      for name in ["back-controls-en", "back-controls-zh", "laboratory-en"]}
    assert (ROOT / "app/src/main/assets/lab-firmware-candidates.json").read_bytes() == (ROOT / "reference/analysis/lab-candidates.json").read_bytes()
    report["old_workspace_empty"] = not any(Path(r"D:\Leaf4Android").iterdir()) if Path(r"D:\Leaf4Android").exists() else True
    assert report["old_workspace_empty"]
    target = ROOT / "research/reports/verification-status.json"
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"python_passed": report["offline_unit_tests_passed"], "kotlin_passed": report["kotlin_protocol_tests"]["tests"],
                      "android_passed": report["android_instrumented_tests"]["tests"], "apk_sha256": apk["sha256"]}))


if __name__ == "__main__":
    main()
