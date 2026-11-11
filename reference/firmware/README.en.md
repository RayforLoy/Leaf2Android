# Leaf camera-back firmware references

[中文](README.md) · [Classic Aptus evidence](analysis/controls-aptus-classic/README.md) · [Wireless interface survey](../../docs/firmware-bluetooth-survey.en.md) · [Inventory](inventory.json)

The newly supplied `D:\leaf\firmware` archive contains genuine camera-back firmware. It supersedes this directory's earlier statement that no firmware had been supplied. The source tree was read only: files were copied and statically parsed; no vendor program, firmware image, device connection or flashing operation was executed.

**Classic Aptus 4.701 is the primary Aptus 22-related wireless firmware evidence. Version 5.041 is a shared-code/new-dialect comparison only. Aptus II has no Bluetooth hardware and is not awaiting wireless acceptance testing.** Shared PDAInterface code in an image named `FirmWareAptusII.UPD` does not establish a radio on that model.

## Scope and provenance

The four supplied source sets contain 310 entries totaling 463,158,198 bytes. Every size and SHA-256 matches its supplied manifest. Content deduplication retains 89 original files totaling 83,696,381 bytes. [source-aliases.json](provenance/source-aliases.json) maps every original source name to one byte-identical canonical file.

| Source set | Original files | Additional unique files | Identified contents |
| --- | ---: | ---: | --- |
| Capture One Mac 7.1.6 | 84 | 73 | Classic Aptus 4.701/4.700, shared 5.041 under Aptus II/AFi/Mamiya DM names, CE containers, recovery image and board-level UBF payloads |
| Capture One Mac 7.1.4, internal app version 7.1.4.92 | 84 | 0 | Every matching relative file is identical to the set above |
| Capture One Windows 11.3.2 | 84 | 0 | Every matching relative file is identical to the set above |
| Leaf Capture Windows 11.2.9 | 58 | 16 | Additional main firmware 4.960 / 4.701, Aptus II CE image with resource version 1.9.6, and distinct UBF files |

Installer source URLs, original manifests and the previously recorded extraction method are preserved in [provenance/](provenance/). This import verifies local supplied bytes against those manifests; it does not re-download or independently authenticate the remote installers. Identical images under several model names do not prove compatibility with every camera back. No 5.048 firmware was found.

## Layout

```text
originals/
  CaptureOne.Mac.7.1.6/      # 73 unique manufacturer files; Aptus II names preferred for the main pair
  LeafCapture.Win.11.2.9/    # 16 additional unique manufacturer files
provenance/
  *-manifest.json           # All four original manifests, exact bytes
  original-README.txt       # Original notes, including original encoding and historical paths
  source-aliases.json       # All 310 source entries mapped to canonical retained files
  analysis-selection.json  # Retained/omitted analysis paths, hashes and reasons
analysis/
  supplied/                # Previously supplied research, exact bytes
  controls-aptus-classic/   # Classic 4.701 payload, PDA catalog, CE checks and client comparison
  controls-5.041/           # Shared 5.041 dialect: bounded PPC disassembly and field tables
  CONTROLS.zh-CN.md
  CONTROLS.en.md
tools/
  import_sources.py
  extract_control_evidence.py
  build_inventory.py
inventory.json             # Independent inventory of this subtree, excluding itself
```

`analysis/supplied/` retains 64 existing analysis files totaling 19,421,568 bytes: reports, extraction scripts, manifests, strings, disassembly, the reconstructed core `LeafCE.exe` and registry. Another 773 analysis files totaling 79,168,651 bytes are reproducible extractions/reconstructions, OS components, third-party dependencies or extracted UI assets. Their paths, sizes, hashes and omission reasons remain in `analysis-selection.json`.

Historical paths such as `D:\Leaf4Android` and `D:\leaf` in supplied evidence are preserved. Current reproduction commands use `D:\Leaf2Android`. The supplied historical scripts may require their original tree layout and external tools; the new control-evidence extractor reads canonical files in this repository directly.

## Core images and classification

| File | Bytes | Identification | SHA-256 |
| --- | ---: | --- | --- |
| `originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD` | 4,235,648 | Classic shared Aptus 4.701, including Aptus 22 model code; primary wireless evidence in this increment | `a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a` |
| `originals/CaptureOne.Mac.7.1.6/FirmWareAptus.BIN` | 7,730,663 | Classic back-side CE container, interface version 1.8.6; all 27 load records validated | `4a0d32186d95bd0aaf0a741d161b98712e30beac595d7d5d10dabffaa86397c0` |
| `originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD` | 6,021,144 | `LEAF FRM` version 5.041, shared PowerPC/pSOS+ code for dialect comparison; not evidence of Aptus II radio hardware | `80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6` |
| `originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.BIN` | 9,810,419 | Camera-back ARM / Windows CE interface ROM load-record image; separate from the PDA client | `2be7ce63460de2908c81c48d5bd26cdec834b9b4d7ffd2a33928160f2cd83549` |
| `originals/CaptureOne.Mac.7.1.6/BootRecovery.UPD` | 6,113,344 | Recovery image; special `0xfffffffe` header marker is not an ordinary firmware version | `4f71ebe188f8dd301dc0afcaa52e059296f29f851114ea7237c41df23bada535` |
| `analysis/supplied/ce_reconstructed/fs/Windows/LeafCE.exe` | 14,135,296 | Reconstructed back-side UI PE for static research, including generated headers; not an original flashable image | See `inventory.json` |

Both classic 4.701 and shared 5.041 UPD use `original file offset = PPC virtual address - 0x74000 + 0x128`; their function addresses are recorded separately. BIN containers use `B000FF\n` CE load records: all 27 classic Aptus records and all 30 records of the shared Aptus II-named container were validated separately. UBF version fields describe board/FPGA payloads, not whole-camera firmware versions.

Classic model association comes from the compiled descriptor: vtable entries `0x3f59b8/0x3f59bc` point to the model-ID getter returning `0x0c` and the model-name getter returning `Leaf Aptus 22`. The image also contains `CAptus22_ISO_Adjuster`. `Firmware.config` contains only `EnforceUploading=0`. This establishes Aptus 22 model code inside shared firmware, not an Aptus 22-exclusive flashing package.

The HP iPAQ / Windows CE WiView PDA EXE and DLL files remain under [../analysis/wiview-1.0.3/](../analysis/wiview-1.0.3/) and related version directories. They are distinct from the camera-side main program and CE ROM here.

## Static findings and limits

Classic 4.701 registers nine PDAInterface tags, each checked through registration call, constructor, vtable and name getter. Both original WiView versions contain actual ARM construction references for all nine. Classic full ShootSetup/DefaultShootSetup writes, RequestSync readback, marking independent of XMP, and GetImage extensions were traced separately. Gain also has a confirmed parser-to-response chain producing UserGainStorage/UserGainList; value/calibration semantics still need device verification. The primary record is [controls-aptus-classic/evidence.json](analysis/controls-aptus-classic/evidence.json).

Shared 5.041 registers the same nine tags. Its independent ISO setter, RequestSync.CameraSettings readback and marking independent of XMP are established in shared code. Those findings are dialect comparisons, not Aptus II Bluetooth capabilities.

This version's Sync uses `CameraSettings`, `DefaultCameraSettings`, `ImageSettings` and `ActiveImageSettings`. Its naming must be distinguished from the old WiView `ShootSetup` dialect. Although the `GetCameraParam` parser accepts `GetParamList=ISO`, the inspected response builder does not demonstrate ISO output. Use the evidenced Sync readback path. See the bilingual control reports for addresses and qualifications.

These static findings do not establish successful operation between Fold7 and every Aptus/Valeo back that has wireless hardware. Aptus II's lack of Bluetooth is a hardware boundary, not a pending feature test. Discovery, pairing, transfer, actual setting effects, original-file access and live view on other combinations require records tied to each device/firmware.

Extracted experiment syntax is separate from user-created hypotheses. GetFile/LiveView/BootRequest have no extracted PDA request templates; a user may explicitly create a `USER HYPOTHESIS` empty-header experiment, which must not become claimed vendor syntax. FactoryGain is a response field/candidate reader, with no established independent setter. No independent wireless calibration tag is invented.

## Reproduction and integrity

From the project root, using Python 3 and the root `requirements-research.txt` dependencies (Capstone 5 for disassembly):

```powershell
Set-Location D:\Leaf2Android
.\.venv\Scripts\python.exe reference\firmware\analysis\controls-aptus-classic\extract_evidence.py
.\.venv\Scripts\python.exe reference\firmware\tools\extract_control_evidence.py
.\.venv\Scripts\python.exe reference\firmware\tools\build_inventory.py --verify
python reference\build_inventory.py --verify
```

To reconstruct the deduplicated selection from the preserved local source archive:

```powershell
.\.venv\Scripts\python.exe reference\firmware\tools\import_sources.py D:\leaf\firmware
.\.venv\Scripts\python.exe reference\firmware\tools\build_inventory.py
```

The importer never deletes source files, refuses to overwrite a different existing payload, and verifies each retained file's size and SHA-256 after copying. These commands do not execute firmware. The external CE reconstruction tools and pinned commits are recorded in `analysis/supplied/third-party-tools.json`; their dependencies are not bundled as application runtime files.

## Deliberate exclusions

`D:\leaf\LEAFDM SYS` contains version records, gray-balance/image settings and device-specific lens calibration, with no executable firmware identified. None of that directory was copied. Personal MOS images, lens calibration, unrelated CCD analysis, Python dependency caches and the complete 113,212,841-byte Leaf Capture installer are also excluded. No retained individual firmware reference file exceeds 90 MiB. Source/release packaging is handled by the project's release process.
