"""Check APK identity, signature, ZIP alignment and ELF LOAD alignment with local SDK tools."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile


def run(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError(f"{Path(command[0]).name} failed: {result.stdout}\n{result.stderr}")
    return result.stdout


def elf_load_alignments(data: bytes) -> list[int]:
    if data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("Expected little-endian ELF")
    if data[4] == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        align_offset, align_format = 48, "<Q"
    else:
        offset = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        align_offset, align_format = 28, "<I"
    return [struct.unpack_from(align_format, data, offset + i * size + align_offset)[0]
            for i in range(count) if struct.unpack_from("<I", data, offset + i * size)[0] == 1]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    parser.add_argument("--output", type=Path, default=Path("research/reports/apk-verification.json"))
    parser.add_argument("--version", default="0.2.0")
    parser.add_argument("--release", action="store_true", help="Require a non-debuggable APK signed with a release certificate")
    args = parser.parse_args()
    tools = args.sdk / "build-tools/36.0.0"
    badging = run([str(tools / "aapt2.exe"), "dump", "badging", str(args.apk)])
    assert "package: name='com.leaf2android'" in badging
    assert f"versionName='{args.version}'" in badging
    assert re.search(r"(?:minSdkVersion|sdkVersion):'31'", badging) and "targetSdkVersion:'36'" in badging
    assert "compileSdkVersion='36'" in badging
    signature = run([str(tools / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(args.apk)])
    if args.release:
        assert "application-debuggable" not in badging, "Release APK must not be debuggable"
        assert "CN=Android Debug" not in signature, "Release APK must use its independent signing key"
    run([str(tools / "zipalign.exe"), "-c", "-P", "16", "4", str(args.apk)])
    libraries = {}
    with zipfile.ZipFile(args.apk) as archive:
        assert archive.testzip() is None
        assert not any(name.lower().endswith((".exe", ".dll", ".cab")) for name in archive.namelist())
        for name in archive.namelist():
            if name.endswith(".so"):
                align = elf_load_alignments(archive.read(name))
                if "/arm64-v8a/" in name or "/x86_64/" in name:
                    assert align and all(n >= 16384 for n in align), (name, align)
                libraries[name] = align
    report = {
        "apk": str(args.apk), "bytes": args.apk.stat().st_size,
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "application_id": "com.leaf2android", "version": args.version, "min_sdk": 31, "target_sdk": 36,
        "build_type": "release" if args.release else "debug", "debuggable": "application-debuggable" in badging,
        "signature_verified": True, "zip_16k_alignment": True, "elf_load_alignments": libraries,
        "signing_details": signature.splitlines(),
        "permissions": re.findall(r"uses-permission(?:[^:]*): name='([^']+)'", badging),
        "real_hardware_verified": False,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in ["apk", "bytes", "sha256", "signature_verified", "zip_16k_alignment"]}))


if __name__ == "__main__":
    main()
