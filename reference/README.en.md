# Leaf2Android reference material

[简体中文](README.md)

Organized on 2026-11-10. This directory holds the original Leaf WiView installation media, manufacturer guides, acquisition provenance, extracted client components, and static protocol analysis. The Android source is in the project-level `app/` and `protocol/` directories. Synthetic wire fixtures remain in `research/fixtures/`.

## Layout and classification

```text
reference/
├── README.md / README.en.md
├── sources.lock.json              # Origins, byte sizes and SHA-256 pins for six inputs
├── inventory.json                 # Current file inventory, classification and provenance
├── relocation-manifest.json       # Original/current paths and byte hashes at relocation
├── build_inventory.py             # Rebuild the inventory using the Python standard library
├── firmware/
│   └── README.md                  # Explicit notice that no back-firmware image is present
├── wiview/
│   ├── 1.0.2/                     # CAB, Mac Installer.data and original ReadMe
│   ├── 1.0.3/                     # User-supplied 1.0.3 CAB
│   ├── guides/                    # 2007 and 2008 manufacturer installation guides
│   └── provenance/                # Original download log and CD-archive metadata
└── analysis/
    ├── wiview-1.0.2/              # PE reports, strings and original extracted/ members
    ├── wiview-1.0.3/
    ├── legacy-extraction/         # Earlier 1.0.2 extraction retained for traceability
    ├── protocol-evidence.json     # Rechecked ARM instructions and service-GUID anchors
    └── version-comparison.json    # Component, section and command-anchor comparison
```

**Camera-back firmware and PDA client software are different artifacts.** No identifiable Aptus / Valeo back-firmware image was present in the supplied material. `00LeafCE.001`, the Leaf Bluetooth wrapper, and WIDCOMM DLLs are Windows CE client components, not camera-back firmware. See [firmware/README.md](firmware/README.md).

## Original inputs and checksums

The six entries in `sources.lock.json` define the input bytes used for acquisition and verification. Hash agreement identifies a particular file; it does not independently authenticate a community-uploaded CD backup.

| Input | Bytes | SHA-256 |
| --- | ---: | --- |
| [WiView 1.0.2 CAB](wiview/1.0.2/LeafWiView-1.0.2.CAB) | 2,683,140 | `5f38aab055b2cb46560e59aa87d019a76a0e7e4a2d6866fbbc58b8e5eac56f85` |
| [WiView 1.0.3 CAB](<wiview/1.0.3/Leaf WiView11_1_0_3.CAB>) | 2,729,244 | `0f0a34891b3b4f7c70c6681092da38cd5d0bb9cadc82ff7b6dcb793c399b4cce` |
| [Mac installation data](wiview/1.0.2/WiView-1.0.2-mac-Installer.data) | 835,922 | `6a864adb792c657d093135602c5413ba5a8ed98c87ba0730d9415cd9e30b23d4` |
| [Original ReadMe](wiview/1.0.2/WiView-1.0.2-ReadMe.txt) | 5,570 | `89e57de9c04d2a08bff0f59b0a43e6878de8940b8d21f5f4181e84e4cf8cc912` |
| [2007 installation guide](wiview/guides/LeafWiView-Installation-2007.pdf) | 743,496 | `777b294d5dc1f96f014a3bc9d6871fe3d3e920281105941705b5de82bbde4f23` |
| [2008 / 1.0.3 installation guide](wiview/guides/LeafWiView-1.0.3-Installation-2008.pdf) | 563,679 | `dc2d731c083df87cc05722691b4807501818e6c075b199b6462e8c97336a8aef` |

WiView 1.0.2, the Mac data, ReadMe, and 2007 guide came from the [community Leaf Capture 11 CD backup](https://archive.org/details/leaf-capture-11). The 2008 guide came from an [Internet Archive snapshot of the original Leaf website](https://web.archive.org/web/20081230142347id_/http://www.leaf-photography.com/imgs/uploads/user%20guides/leafwiview_103_installation.pdf). The 1.0.3 CAB was supplied by the user; no public download URL has been invented for it. The lock file preserves retrieval times, original URLs, archive checksums, and provenance notes.

`wiview/provenance/downloads.json` is the original acquisition log. Its old relative paths describe the locations at acquisition time; use `sources.lock.json` for current paths. Original manufacturer inputs, extracted members, download logs, and archive metadata were moved with per-file SHA-256 checks and were not edited. Inventories and generated analysis reports are managed project metadata.

## Reproducing verification and analysis

Run from the project root, `D:\Leaf2Android`. Full static analysis uses the pinned `pefile` and `capstone` packages in `requirements-research.txt`. CAB extraction invokes Windows `expand.exe`; it does not run the installer or load vendor code.

```powershell
python -m venv --system-site-packages .venv
& .\.venv\Scripts\python.exe -m pip install -r requirements-research.txt
& .\.venv\Scripts\python.exe tools/acquire_sources.py verify
& .\.venv\Scripts\python.exe tools/analyze_wiview.py
& .\.venv\Scripts\python.exe tools/analyze_wiview.py --cab "reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB" --output reference/analysis/wiview-1.0.3
& .\.venv\Scripts\python.exe tools/compare_versions.py
& .\.venv\Scripts\python.exe tools/build_evidence.py
& .\.venv\Scripts\python.exe -m unittest discover -s tests -v
python reference/build_inventory.py
```

If a public input is absent, `tools/acquire_sources.py fetch` downloads its pinned URL and verifies it. The user-supplied CAB must still be supplied at the path in the lock file. The tools reject paths outside the project, truncation, mismatched byte sizes or SHA-256 values, and CAB traversal or duplicate member names.

Inspect a bounded ARM function or replay a synthetic stream:

```powershell
& .\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --va 0x2a988 --bytes 0x240
& .\.venv\Scripts\python.exe tools/replay_stream.py research/fixtures/synthetic-control-and-attachment.hex --hex --chunk-size 1
```

Offline replay reads an artificial fixture, opens no Bluetooth connection, and is not evidence of receiving an actual camera image.

## Findings and evidence limits

- Both client versions contain identical underlying WIDCOMM Bluetooth components. Static calls use the standard SPP GUID `00001101-0000-1000-8000-00805f9b34fb`.
- Control headers run from `^` to the first `>`. An `attachment` value declares the exact following binary byte count; delimiters inside the attachment are payload.
- CAB package labels are 1.0.2 and 1.0.3. Internally constructed log versions are 1.15 and 1.17; these must not be guessed as handshake values.
- Strings and instruction anchors are static evidence, not target-back testing. The complete handshake, firmware differences, non-ASCII text, complete MOS reads, and continuous Live View still need actual-device evidence.
- [Protocol notes](../docs/protocol.md) and the [version comparison](../docs/version-analysis.md) explain addresses and implementation boundaries. See the [implementation/support table](../docs/implementation-status.md) for the app's current status.

Historical vendor files retain their original names, versions, authorship, and provenance. A project rename does not rewrite those files. They are separate reference material, are not embedded in the Android APK, and are not covered by an assumed license for newly written Leaf2Android code. Preserve applicable provenance and rights notices when distributing source or reference archives.
