"""Reconstruct CE modules with the pinned third-party parser.

Replace its permissive decompression fallback with checked LZX framing and
lengths. Short initialized data is extended only to its virtual section size.
This script refuses to overwrite an existing extraction.
It never runs any extracted EXE or DLL.
"""

from pathlib import Path
import hashlib
import importlib
import json
import sys


ROOT = Path(__file__).resolve().parent
VENDOR = ROOT / "vendor" / "extract-wince-rom"
INPUT = ROOT / "extracted" / "FirmWareAptusII.BIN.payload.bin"
OUTPUT = ROOT / "ce_reconstructed"


def main():
    if OUTPUT.exists():
        raise SystemExit(f"Refusing to overwrite {OUTPUT}; preserve or rename it first.")
    if OUTPUT.resolve().parent != ROOT:
        raise SystemExit("Output is outside the analysis directory")
    sys.path.insert(0, str(VENDOR))
    from wince_decompr.wincedecompr import CEDecompressROM
    from winmob_extract import extract_image

    logs = []

    def strict_lzx(source, expected):
        encoded_size = int.from_bytes(source[:3], "little")
        if not 0 < encoded_size <= expected:
            raise ValueError(f"Invalid LZX initialized size {encoded_size}, virtual size {expected}")
        count = ((encoded_size - 1) >> 12) + 2
        starts = [count * 3] + [int.from_bytes(source[i*3:i*3+3], "little") for i in range(1, count)]
        if starts[-1] != len(source) or any(a >= b for a, b in zip(starts, starts[1:])):
            raise ValueError("LZX block offsets do not consume the stored data exactly")
        declared = sum(int.from_bytes(source[a+4:a+8], "little") for a in starts[:-1])
        if declared != encoded_size:
            raise ValueError(f"LZX block sizes total {declared}, header declares {encoded_size}")
        buffer = bytearray(expected + 4096)
        actual = CEDecompressROM(bytes(source), len(source), buffer, expected, 0, 1, 4096)
        if actual != encoded_size:
            raise ValueError(f"LZX length mismatch: got {actual}, header declares {encoded_size}")
        result = bytes(buffer[:expected])
        logs.append({"source_size": len(source), "source_sha256": hashlib.sha256(source).hexdigest(),
                     "decompressed_initialized_size": actual, "virtual_size": expected,
                     "virtual_zero_tail": expected - actual,
                     "decompressed_sha256": hashlib.sha256(result).hexdigest()})
        return result

    importlib.import_module("winmob_extract.xip").ce_rom_decompress = strict_lzx
    importlib.import_module("winmob_extract.pe.reconstruct").ce_rom_decompress = strict_lzx
    try:
        result = extract_image(str(INPUT), out_dir=str(OUTPUT), fs_mode="raw", sections_mode="full")
        if not result:
            raise ValueError("CE reconstruction failed")
    finally:
        (ROOT / "ce_decompression.json").write_text(json.dumps(logs, indent=2) + "\n", encoding="utf-8")
    print(f"Strict LZX validation: {len(logs)} successful decompressions")


if __name__ == "__main__":
    main()
