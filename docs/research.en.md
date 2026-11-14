# Reproducing original-client and protocol research

[简体中文](research.md) · [Project home](../README.en.md) · [Reference inventory](../reference/README.en.md)

The user-supplied DP-67 1.2.1 media was added on 2026-11-14: [analysis and reproduction](dp67-analysis.en.md), [reference subtree](../reference/dp67/). Only the vendor installer, embedded CAB, extracted components and evidence are retained; the complete ZIP and four PDA backups are excluded. Run `python reference/dp67/analyze_dp67.py --verify` to regenerate temporary evidence from the retained installer and compare bytes without the private backups or original ZIP.

Original media, extracted components and static analysis live under `reference/`. Android does not load or execute those programs, and they are not application runtime dependencies.

## Identity and location

| Kind | Location |
| --- | --- |
| WiView 1.0.2 | `reference/wiview/1.0.2/LeafWiView-1.0.2.CAB` |
| WiView 1.0.3 | `reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB` |
| Manufacturer guides | `reference/wiview/guides/` |
| Provenance | `reference/wiview/provenance/` |
| Current input lock | `reference/sources.lock.json` |
| Both extracted analyses | `reference/analysis/wiview-1.0.2/`, `wiview-1.0.3/` |
| Historical duplicate extraction | `reference/analysis/legacy-extraction/` |
| Version / protocol evidence | `reference/analysis/version-comparison.json`, `protocol-evidence.json` |
| Inventory and hashes | `reference/inventory.json` |
| Back-firmware status | `reference/firmware/README.md` |

On 2026-11-11 the real firmware supplied from `D:\leaf` was included: 310 source entries deduplicated into 89 original files, comprising main and recovery UPDs, CE BINs, FPGA UBFs and configuration, with core reconstruction and static analysis. See the [firmware provenance and inventory](../reference/firmware/README.en.md). WiView client EXEs / DLLs are classified separately. Hashes identify original bytes, not manufacturer signature authentication. No vendor program was executed and no back was flashed.

Version 1.0.2 came from a community Leaf Capture 11 disc backup on Internet Archive; the user supplied 1.0.3. Historical acquisition records keep their former paths. The source lock and inventory specify current locations. See the bilingual reference guide for provenance.

## Environment and integrity

Run from the repository root. Python 3.12 is supported and dependencies are pinned in `requirements-research.txt`.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-research.txt
.\.venv\Scripts\python.exe tools/acquire_sources.py verify
```

If public inputs are missing, `fetch` retrieves pinned public URLs and verifies them. No remote source was invented for the user-provided 1.0.3 file; use the original submission or the published reference bundle.

```powershell
.\.venv\Scripts\python.exe tools/acquire_sources.py fetch
```

## Re-extract and build evidence

CAB extraction uses Windows `expand.exe`; it does not run the installer or ARM program:

```powershell
.\.venv\Scripts\python.exe tools/analyze_wiview.py
.\.venv\Scripts\python.exe tools/analyze_wiview.py --cab "reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB" --output reference/analysis/wiview-1.0.3
.\.venv\Scripts\python.exe tools/compare_versions.py
.\.venv\Scripts\python.exe tools/build_evidence.py
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Outputs include PE imports / exports, section hashes, version resources and offset-annotated ASCII / UTF-16 strings. Comparison distinguishes code changes from export timestamps. The evidence script validates pinned sample hashes, ARM instructions and the service GUID. Exact fields are in JSON; command-string counts are not feature-support counts.

Inspect a bounded disassembly or literal reference:

```powershell
.\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --va 0x2a988 --bytes 0x240
.\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --xref 0x10e43c
```

Addresses are preferred sample virtual addresses, not Android addresses or direct file offsets.

## Replay an offline stream

```powershell
.\.venv\Scripts\python.exe tools/replay_stream.py research/fixtures/synthetic-control-and-attachment.hex --hex --chunk-size 1
```

This fixture is constructed from static framing evidence and contains no real back session. Arbitrary attachment bytes test framing, not JPEG quality. Decodable synthetic JPEGs in Android repository tests are a separate fixture class and still are not real captures.

## Adding research

Record source, version, size, SHA-256 and tool versions. Keep unknown values as bytes and label encoding assumptions. Distinguish synthetic fixtures, static evidence, real captures and confirmed operations. Do not modify manufacturer inputs to satisfy evidence scripts. A `.mos` filename, RawFrameBuffer string or unverified command prefix does not establish original-file transfer or live view.

Before publishing real captures, inspect photographs, paths, copyright and notes for personal data. System pairing PINs and release signing material do not belong in fixtures.
