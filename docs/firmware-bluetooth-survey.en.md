# Firmware Bluetooth interface survey

[中文](firmware-bluetooth-survey.md) · [Experiment template inventory](../reference/analysis/lab-candidates.json)

Protocol research uses both original WiView clients and camera-back firmware. It includes static PowerPC analysis of classic Aptus 4.701 and shared firmware 5.041: PDAInterface catalogs, attribute parsers, state writes and response builders. The classic Aptus Windows CE BIN container and record checksums have also been inspected.

The hardware correction is essential: **Aptus II has no Bluetooth hardware.** Shared PDAInterface code inside an image named `FirmWareAptusII.UPD` does not establish wireless connectivity on Aptus II. Classic Aptus firmware is therefore examined independently below. Static reachability is not a successful test on every physical back.

## Classic Aptus source and model association

| Item | Exact artifact |
| --- | --- |
| Main image | [FirmwareAptus.UPD](../reference/firmware/originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD), 4.701, 4,235,648 bytes |
| SHA-256 | `a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a` |
| Extracted PPC payload | [FirmwareAptus-4.701.payload.bin](../reference/firmware/analysis/controls-aptus-classic/FirmwareAptus-4.701.payload.bin), 4,235,352 bytes; SHA-256 `52718040da6175f4a4a2c3c57df2078ad140613d3515ab26ede7ac787029dfa8` |
| Paired CE container | `FirmWareAptus.BIN`, interface version 1.8.6, 7,730,663 bytes; SHA-256 `4a0d32186d95bd0aaf0a741d161b98712e30beac595d7d5d10dabffaa86397c0`; all 27 load-record checksums valid |
| Origins | Four supplied Capture One / Leaf Capture extraction sets, with eight byte-identical Aptus/AptusS UPD aliases; see [source mapping](../reference/firmware/provenance/source-aliases.json) |

`Firmware.config` contains only `EnforceUploading=0`, without a model mapping. Stronger association comes from a compiled model descriptor: vtable entries `0x3f59b8/0x3f59bc` point respectively to `0x2108c4`, returning constant `0x0c`, and `0x2108cc`, returning `Leaf Aptus 22` through `0x47dbdc` → `0x3f5b7c`. RTTI `21CAptus22_ISO_Adjuster` is present at `0x4121ac`. This establishes a classic shared image containing Aptus 22 model/ISO-adjuster code. It does not establish an Aptus 22-exclusive image or recommend flashing it. Legacy 4.700 is separately retained; its addresses are not mixed with this report.

[Independent evidence.json](../reference/firmware/analysis/controls-aptus-classic/evidence.json) records source/payload hashes, CE records, model association, the full command catalog, field tables and both clients' construction references. `Original UPD offset = VA - 0x74000 + 0x128`. Reproduce from the project root:

```powershell
.\.venv\Scripts\python.exe reference/firmware/analysis/controls-aptus-classic/extract_evidence.py
```

The script reads/disassembles bytes and generates evidence. It does not execute vendor code, send Bluetooth commands or write a camera back.

## Complete PDAInterface catalog

Classic 4.701 catalog function `0xe640c` and shared 5.041 catalog function `0x1026a4` each register nine commands with interface ID 1. Constructor calls and name getters below are checked against the pinned bytes.

| Tag | 4.701 registration → constructor → name getter | 5.041 constructor → name getter | WiView 1.0.2 / 1.0.3 |
| --- | --- | --- | --- |
| ReSyncRequest | `0xe644c → 0xa78f8 → 0xa7ab0` | `0xafd5c → 0xaff14` | Construction references in both |
| GetImage | `0xe649c → 0x90a38 → 0x91830` | `0x97c40 → 0x98a34` | Both |
| GetPictureParams | `0xe64d4 → 0x8261c → 0x838a0` | `0x87adc → 0x88db0` | Both |
| FileCommand | `0xe650c → 0xa4b04 → 0xa634c` | `0xacf78 → 0xae794` | Both |
| ImageFunction | `0xe6544 → 0xa7b88 → 0xa898c` | `0xaffec → 0xb0d98` | Both |
| SetPictureParams | `0xe657c → 0x83eec → 0x84810` | `0x8931c → 0x89be8` | Both |
| RequestSync | `0xe65b0 → 0x84ea0 → 0x877a4` | `0x8a1b4 → 0x8d6e8` | Both |
| SetCameraParam | `0xe65e0 → 0x9236c → 0x942d0` | `0x99754 → 0x9bdb0` | Both |
| GetCameraParam | `0xe6610 → 0xd31b0 → 0xd48e8` | `0xe3a68 → 0xe51a0` | Both |

The middle five image/file commands are conditional; the complete device/mode mapping remains unresolved. Shared code alone does not prove availability on every back. Extended classic GetImage parameters also depend on capability check `0x95230`.

This comparison goes beyond string searches: executable ARM sections in both original WiView PE files were scanned for LDR literal references, confirming referenced construction text for all nine tags. `Ack` and 1.0.3's `Nak` are responses, not extra business commands. No additional independent GetFile/LiveView tag was found in these catalogs. Additional firmware research concerns attributes, dialects and backend effects within known commands.

## A: Defined behavior integrated into normal app flows

- Classic `SetCameraParam ShootSetup` / `DefaultShootSetup`: 4.701 parsing at `0x92564..0x92720`, full CSV processing at `0x92e28` → `0x1be884`. ISO enters `0x1bf22c` through setup-structure offset `+0x200`; this is not an independent ISO attribute in this version. `RequestSync` serializes complete setups at `0x86878..0x86bc8`; field table `0x3cca44` includes ShootSetup/DefaultShootSetup. The app edits returned complete settings/options and checks a fresh Sync.
- `GetImage` preview/detail requests: `0x90c20..0x90da8` parses file_name/PacketSize/zoom/light/Alarm. `0x91430` handles zoom and XCoord/YCoord; `0x915d0` / `0x9165c` handle light/highlight-shadow alarms. Attachment, Size and Light response paths are at `0x90ddc..0x91430`. These operate on captured photos.
- `GetPictureParams` / `SetPictureParams`: classic 4.701 supports marking independent of XMP_tag. `0x84270..0x84284` calls XMP processing only when flagged; `0x84288..0x842a0` independently calls `0x1c0bd0` to set/clear the Good bit. `0x82b00..0x82b0c` → `0x1c0c94` reads Good/UnMarked. Annotation and marking are parsed as alternatives and require separate requests.
- `ImageFunction fstops="x;y"` means image-pick coordinates, used for sample display, not aperture or exposure-stop control. Six back-local FileCommand operations have original constructions and app paths: copy, move, delete, create directory, remove empty directory and rename directory.
- GetCameraParam readers for time, battery, photo count and default annotation/copyright, plus evidenced corresponding writers, are integrated according to support state. Shared 5.041 CameraSettings/ImageSettings are also implemented, enabled only when actually returned by a device; they must not be imposed on classic Aptus.

Integrated means the source contains the normal operation and confirmation logic. This static survey does not constitute testing on all backs.

## B: Wireless entry established; experimental validation remains

| Feature | Evidence and established syntax | Remaining gaps |
| --- | --- | --- |
| Gain read | `GetCameraParam GetParamList="Gain"`; classic parser `0xd41a4` sets `0x20000000`, response `0xd3bdc..0xd3bf4` checks it and calls `0xd4448`, emitting UserGainStorage/UserGainList | Meaning of list/storage items, calibration implications and model-specific responses |
| UserGain write | Classic parser `0x929dc..0x92a04` → `0x9403c`; shared 5.041 also accepts UserGain | Values, profile/calibration selection, persistence and write confirmation |
| Gain status | Both Sync tables include UserGainList, ActiveUserGain, UserGainStorage, FactoryGain; classic indexes 27..30 | A FactoryGain response field is not a same-named setter |
| Brightness / ResetCounter / PIN / FactorySettings | Actual original-client constructors and matching classic parser keys exist | Brightness range, reset scope, PIN character/recovery behavior and factory-reset impact on stored/calibration data |
| qformat / recovery | Original FileCommand constructions identified | Device storage labels, format/recovery scope and data preservation; recovery is not proven read-only |
| ReSyncRequest | Registered and constructed in both versions | Full recovery/synchronization sequence and effects |
| GetImage zoom="FP" | Classic parser `0x91574..0x915b8` accepts FP/fp | Actual image extent/resolution; not an original-file download claim |
| ManualLens | Parser/integer storage path confirmed only in shared 5.041 | Absent from inspected classic 4.701 parser; units, range and effect unknown, not a mechanical-shutter actuator |

Shared 5.041 Gain response reachability is also established: `0xe4b14` → `0xe451c..0xe4534` → `0xe4e1c`. This is stronger than a string-only observation, while the value semantics remain open.

[lab-candidates.json](../reference/analysis/lab-candidates.json) distinguishes actual constructors, firmware parsers/handlers, response-only fields, internal APIs and unconfirmed research names. It records unknown values and state/storage/erase/reset/calibration effects. The user has requested manual lab testing; there is no automatic candidate enumeration or sending workflow.

## C: Internal features and missing entry evidence

`BootRequest` has a name getter and constructor, but classic direct call `0xe5d28` belongs to the PXA catalog, not the PDA catalog. It is not an established Bluetooth reboot command. `CAptus22_ISO_Adjuster` is an internal type, not a discovered wireless calibration command. CE GetFileSize/GetFileVersionInfo names are operating-system APIs, not wireless GetFile requests.

Complete MOS/RAW download and continuous sensor live view remain unproven. Neither inspected PDA catalog contains GetFile or LiveView; no alternative attribute, stream-start command or original-file response has been reconstructed here. Their lab records use `wire_template: null` research placeholders rather than invented sendable vendor syntax. Back-local copy/move, repeated captured-photo requests and FP image options do not establish these features.

This increment executed no vendor binary, calibration write, format, PIN change or automatic command scan. Future device records should bind the model/firmware to raw request/response bytes and observed effects, preserving successes, rejections, timeouts and unchanged-state results separately.
