# Feature gap matrix: 0.2.0 baseline → 0.3.1

[简体中文](feature-gap-matrix.md) · [Project home](../README.en.md) · [Capabilities and devices](implementation-status.en.md) · [User guide](quick-start.en.md) · [0.3.1 notes](releases/v0.3.1.en.md)

Updated 2026-11-14. **0.3.1 (versionCode 4) is an experimental pre-release.** This matrix compares the 0.2.0 baseline with current standard paths, laboratory entries and physical effects. Candidate changes for Valeo settings timeouts still require real-device retests.

Basic reports confirm **successful Bluetooth connection and photo reading on Valeo 22Wi and Aptus 22**. A further 2026-11-14 report describes Valeo settings timeouts; see the [device and issue record](implementation-status.en.md). Complete firmware, phone / system, app-version and log details are still missing. Standard functions, laboratory candidates and user hypotheses are recorded separately; new hardware effects remain unverified. The user corrected that Aptus II has no Bluetooth and is not a current wireless target; the early Aptus II 5 plan is historical. 5.041 PDAInterface is shared code, not Bluetooth-hardware evidence or family-wide acceptance.

## Reading the states

- **Existing:** present in 0.2.0 and retained or extended; not a claim that every behavior passed physical testing.
- **Added standard function:** an evidenced protocol connected through repository and UI, with current values, options and applicable read-back; physical effects still need testing.
- **Request only:** an evidenced request has a matching Ack, but no verified current-state read-back; the UI does not claim the switch has changed.
- **Laboratory candidate:** templates or raw headers enable active testing, distinguishing client calls, firmware handlers and fields; unknown inputs are manual and replies are observations, not semantic success.
- **User hypothesis:** no proven manufacturer wireless syntax; users may explicitly create USER HYPOTHESIS probes without treating generic candidate headers as extracted interfaces.
- **Partial / research placeholder:** standard functionality covers only the stated scope; no automatic manufacturer command is created without an evidenced template or known tag, while users can enter a single raw header.
- **Unimplemented / hardware limit:** no established usable protocol, or the mechanical shutter has no actuator.

## Independent classic Aptus / Aptus 22 check

Primary evidence now includes classic **FirmwareAptus.UPD 4.701**, 4,235,648 bytes, SHA-256 `a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a`. It contains the Aptus 22 model and ISO-adjustment class but remains shared firmware, not a dedicated Aptus 22 flash image. All 27 companion CE 1.8.6 load records pass checksums. Installed user firmware is not inferred.

Classic and shared 5.041 PDAInterface both register nine business-command classes corresponding to actual WiView constructors. Static model / mode branches prevent universal support claims, and the catalogs reveal no independent GetFile / LiveView tag. Complete classic ShootSetup, independent marking, image extensions and the Gain reply chain are traced separately without borrowing 5.041 addresses. See the [firmware survey](firmware-bluetooth-survey.en.md) and [independent classic evidence](../reference/firmware/analysis/controls-aptus-classic/evidence.json).

## Connection, synchronization and recovery

| Function | 0.2.0 baseline | 0.3.1 and remaining boundary |
| --- | --- | --- |
| Bluetooth discovery, pairing and direct connection | Existing | Retain public Android APIs and classic SPP / RFCOMM; two models have connection / photo-reading reports only; first pairing, wrong PIN and Android bond recovery need records |
| RequestSync and CurrentTime reads | Existing | Complete settings, choices and dialect parsing; DP-67 refresh uses Sync + CurrentTime only, without extra automatic getters or mixing unlike structures |
| Directory reads | Existing | Retain subject / path matching; reread affected directories after management operations |
| Capture notifications and preview queue | Existing | Retain serialized requests, filenames and attachment-length checks; at least 20 real photos still need content / name / order verification |
| Timeout, disconnect and foreground session | Basic handling | Retain timeout closure, manual reconnect and no automatic retry; summaries identify tag / attribute names without values and do not establish physical-link causality |
| Complete setting transactions | Per-request serialization existed | **0.3.1:** operationLock covers pre-Sync / set / read-back, excluding automatic GetImage; notification Ack bypasses it to avoid blocking back progress |
| Background refresh retains results | Automatic refresh existed | **0.3.1 app correction:** CameraShootSetupChangeNotification Sync retains success / mismatch error / notice until a new manual operation; not a proven back-disconnection cause |
| ReSyncRequest, NAK and original-client retransmission | Incomplete | **Partial / laboratory:** standard disconnect recovery remains; ReSyncRequest / Ack templates and raw observations can record NAK and other frames, without claiming complete retransmission timing |
| Image size / packetized mode | Not established | **No automatic recovery:** standard flow rejects unverified size packets; laboratory header / attachment observations do not establish a solved packetization or retransmission protocol |

## Shooting and image-processing settings

| Function | 0.2.0 baseline | 0.3.1 and remaining boundary |
| --- | --- | --- |
| Current ISO and allowed steps | No usable control | **Added:** current value comes from matched legacy ShootSetup or 5.041 CameraSettings; choices come from Sync, not hardcoded steps or the first option |
| Legacy ISO write | Missing | **Added:** complete selected WiView / DP-67 serialization, preserving untouched values; pre / post Sync verification, not an ISO-only success verdict |
| AUTO / WIVIEW / DP67 | No separate selection | **0.3.1:** select while disconnected; AUTO needs Valeo in the Bluetooth name and shootsetup in Sync. Explicit modes cover renamed backs without changing pairing or hardware capability |
| Existing shooting setup selection | Missing | **Added:** use device-provided setups and read back; DP-67 originals send DefaultShootSetup only, while names containing .1 send complete CSV; not FactorySettings |
| Complete WiView legacy setup editing | Missing | **Added:** ISO, ImageQuality, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, CameraType, ICCFlag, Prefix, ShootingFolder, FileCompression / NLCompression, Orientation and Color; WiView path only |
| DP-67 core settings and Prefix | Missing | **0.3.1:** SetupName, ShootingFolder, ISO, ImageQuality, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, CameraType and ICCFlag; retain Prefix only if already provided, without invented empty fields |
| DP-67 .1 modified slot | Missing | **0.3.1:** clone original-name+.1 unless .1 is already contained; show existing-slot updates before sending and verify active / expected values plus unchanged original preset |
| Extensions omitted by DP-67 | No separate limit | **0.3.1:** no FileCompression / NLCompression, Orientation, Color or global attributes; hidden in standard controls, with manual laboratory probes still possible rather than verified support |
| Light / white-balance preset | Missing | **Added:** select a device-provided capture illuminant; this is not arbitrary Kelvin / tint or photo-coordinate gray sampling |
| Framing, ICCFlag and Color | Missing | **Added:** advertised Framing names and ICCFlag 0 / 1; WiView distinguishes setup / top-level orientation and Color, while DP-67 omits those extensions |
| Independent 5.041 camera settings | No dialect controls | **Added:** recognized Sync.CameraSettings enables independent ISO, Orientation, CameraType, SensorCrop, FileCompression, Prefix and ShootingFolder writes, followed by complete Sync comparison; independent server evidence is not inferred from old CABs |
| SensorCrop and independent compression | Not connected | **Added within limits:** standard SensorCrop accepts advertised name-only choices rather than compound records; standard 5.041 compression accepts advertised None / Lossless. Other values can be tested through raw laboratory requests, without claiming a proven near-lossless branch |
| Prefix and ShootingFolder | Missing | **Added:** check actual folder, encoding and delimiters before writing; retain the legacy input limit. Independent 5.041 Prefix cannot increase current UTF-8 byte length and is capped at 23 UTF-16 code units; an implementation limit, not a hardware maximum |
| Complete ImageSettings | Not connected | **Added:** separate 5.041 processing presets include ImageQuality, Color, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, ICCFlag, Grain, MoireReduction and Saturation; require device choices for editable fields, preserve the rest and read back |
| ActiveImageSettings selection | Not connected | **Added:** choose an existing processing preset and verify the active name; do not rename ShootSetup to ImageSettings; ISO is not in this structure |
| ShutterSpeed, aperture and mechanical release | No remote control | **Read-only / hardware limit:** a readable field is not a writer; this technical-camera mechanical shutter has no app-controlled actuator and remains manual |

Incomplete configuration, absent choices, an unknown dialect, state changed during editing, unsafe serialization or a read-back mismatch is not confirmed success. An ISO-only change or Ack does not replace full comparison. The bottom-navigation **Back** page groups configuration and session controls.

The DP-67 path follows [user-package static analysis](dp67-analysis.en.md): shorter fields, cloning, contains(".1") and wrapper behavior are evidenced in bytes, not a proven real Valeo timeout cause. The app does not change CEVersion or copy the original 12-second, up-to-three-attempt / failure-resync strategy. Timeout closure and no automatic write retry remain.

The 2026-11-14 report identifies working reads and settings timeouts without naming the field; the screenshot establishes app-initiated timeout closure only. No public same-device WiView 1.0.3 A/B or direct immediate-ISO-write failure was found; see the [independent review](valeo-connection-research.en.md). Physical effects of operation locking and compatibility changes remain unverified.

## Preview, exposure inspection and gray balance

| Function | 0.2.0 baseline | 0.3.1 and remaining boundary |
| --- | --- | --- |
| Basic GetImage post-capture preview | Existing | Retained; two models have photo-reading reports; a `.mos` path does not establish a complete MOS payload |
| Preview-cache default and upgrade | Cache existed | **0.3.1:** 1024 MiB default; one-time old 256 → 1024 migration retains 64; later choices such as 256 survive restarts. Laboratory attachment budget remains separate |
| Phone zoom, JPEG histogram and indicators | Existing | Retain local processing and provenance labels, separate from back processing |
| Back zoom and XCoord / YCoord | Missing; phone zoom only | **Added:** original 1 / 2 / 4 / 8 processed JPEG previews; original-image protocol coordinates entered manually, with no guessed Compose-tap or thumbnail mapping |
| Back Alarm / edge | Missing; local JPEG indicators only | **Added:** white / dark / both requests return processed previews; actual thresholds and image effects need testing |
| Gray-balance sampling | Missing | **Added:** sample using original-image coordinates and inspect returned Light; do not invent Kelvin / tint |
| Gray-balance confirmation and restore | Missing | **Added:** explicit confirmation writes the current photo; restore and preview refresh are separate; no automatic future-capture setup update. Ack is not a measured color effect |
| Apply a gray sample to future-capture setup | Missing | **Added, physical effects unverified:** separate confirmation submits complete legacy ShootSetup.Light / 5.041 ImageSettings.Light and verifies Sync; only the current back's current-connection sample still tied to the current photo may bypass the static illuminant list; reject CSV delimiters and preserve all other configuration fields |
| Back pixel picking / exposure analysis | Missing | **Added:** ImageFunction fstops=`x;y`, displaying histogramlocation / histogramvalue; coordinates, not aperture; label as back-provided with RAW / JPEG source unconfirmed |
| Back histograms and exposure attributes | Primarily local JPEG display | **Added:** variable-bin histogram / r / g / b parsing and display; preserve other received attributes without inventing units; not every photo is promised every field |
| Complete screen-to-original mapping | Not established | **No automatic mapping:** some original orientation formulas are evidenced, but fit, cropping, zoom viewport, actual orientation and dimensions are not fully verified; manual protocol coordinates do not pretend to be tap-to-sample |

Back histograms and pixel picking are not claimed as RAW metering. Phone-local JPEG provenance is known. Sources remain labelled separately in the UI and diagnostics.

## Photo information, marking and notes

| Function | 0.2.0 baseline | 0.3.1 and remaining boundary |
| --- | --- | --- |
| Photo attributes and current-photo notes | Reads and note writes existed | Retain GetPictureParams and explicit back-note writes; errors or unexplained replies are not success; effects need real-firmware checking |
| Local stars / local notes | Existing | Remain independent, persistent phone data; not back marks or capture-default notes |
| XMP decoding and preservation | Missing | **Added:** strict Base64 / UTF-8 decoding from the evidenced xmp_tag header; no empty XMP when absent and no guessing binary attachments as XMP; reject damaged, unsafe or ambiguous structures |
| Back Good / Unmarked | Missing; local stars only | **Added:** read original XMP, change only the third Leaf Flags character and retain other characters, namespaces and fields; read marking back |
| Back marking without XMP | Client constructors require XMP | **Added standard function:** classic 4.701 and shared 5.041 evidence independent marking; Sync shootsetup / camerasettings enables Marking-only. Existing XMP is preserved; without XMP or a recognized dialect, the standard path refuses and manual laboratory probes remain available |
| Full XMP-only / annotation+XMP packet writes | Missing | **Added laboratory templates:** original-client syntax and supplied Base64 / XML checks; users provide the complete packet. Standard controls retain targeted Flags edits rather than claiming an arbitrary-field editor or verified full-packet effects |
| Photo provenance checks | Older caches lack new provenance constraints | **New standard constraint:** marking, notes, gray balance, deletion and internal copy / move require previews read from the current back in this connection; reconnect / upgrade / restart requires refresh. Laboratory entered-target requests can bypass these standard checks |

Marking changes the third **character** of Flags, not a server-internal bit number used as an XMP index. Unrelated XML fields are preserved semantically; reserialized XMP is not promised to remain byte-identical. Preview caches separately preserve received JPEG bytes unchanged.

Standard photo writes use a non-persisted connection generation, not only an address or filename; reconnecting to the same back still requires refresh. Laboratory raw / full-packet targets, requests and results are recorded separately without inheriting every provenance check or standard read-back verdict.

## Back storage management

| Function | 0.2.0 baseline | 0.3.1 and remaining boundary |
| --- | --- | --- |
| Create folder | Missing | **Added:** exact mkdir path after checking the destination is unused; not arbitrary file creation |
| Delete folder | Missing | **Added for empty folders only:** inspect contents, explicitly confirm, rmdir and reread the parent; no recursive non-empty deletion |
| Rename folder | Missing | **Added:** rename within the same parent, refusing an existing destination and rereading the parent |
| Delete photo | Missing | **Added:** explicit full-path confirmation, del and directory read-back; recovery is not a promised undo |
| Internal photo copy | Missing | **Added:** copy destination includes the original filename; refuse an existing destination and reread it; not a phone original-file transfer |
| Internal photo move | Missing | **Added:** verify destination exists and source disappeared; refuse overwrite; reread actual state after disconnect or mismatch |
| Arbitrary photo rename | Not established | **No standard implementation / user probe:** audited rename operates on folders; users may probe explicitly through the raw console without extending folder evidence into an implemented photo-rename claim |
| qformat / formatting and recovery | Original constructors; no controls | **Added laboratory templates:** manually enter the actual storage target and confirm; roots, erase scope, recovery side effects, recoverability and completion timing remain unverified; a reply is not recovery success |

Standard storage operations show exact targets, require confirmation and reject mismatching directory read-back. Laboratory requests also require manual confirmation, but raw observations do not inherit standard directory verification. Connecting or refreshing never automatically formats, recovers or probes a target.

## System settings and advanced laboratory commands

| Function | 0.2.0 baseline | 0.3.1 and remaining boundary |
| --- | --- | --- |
| Clock synchronization | CurrentTime read-only | **Added:** write phone-local time, then read separately; no assumed protocol time-zone transfer |
| Default Annotation / Copyright | Current-photo notes only | **Added:** read, write and read back future-capture defaults; leave existing photos unchanged |
| EnableTakenPicture yes / no | Missing | **Added laboratory template:** no has not been proved to stop notifications; the main UI's unproven pause control is removed. Record replies and subsequent notifications separately from Follow new photos or shutter release |
| PowerSource | Missing | **Added, request only:** FireWire / CameraBattery only with powersourceselection=support; Ack does not prove the current physical power source |
| Brightness | Missing | **Added laboratory template:** explicit signed 32-bit integer is a phone-format bound only; range, target and read-back remain unknown, without an exposure-compensation label or guessed default |
| NewPinCode | Missing | **Added laboratory template:** no default PIN; host template accepts 1–16 encoded bytes, not a promise of valid device characters or minimum length. Android bond invalidation / recovery need testing; known PIN headers are masked without altering transmitted bytes |
| TotalFrames and ResetCounter | Total readable; no reset | **Standard read-only plus laboratory ResetCounter=yes:** core state also participates in numbering, so the request is not called a lifetime shutter-count reset; exact semantics and persistence remain unresolved |
| FactorySettings | Missing | **Added laboratory template:** explicit orientation, color and optional existing setup name; distinct from ordinary preset selection, with reset / calibration scope and recovery effects unverified |

## Additional firmware research entries

| Entry | Evidence level and app entry | Unresolved |
| --- | --- | --- |
| Gain query | **Firmware parser-to-reply chain, laboratory:** GetCameraParam Gain returns UserGainStorage / UserGainList, traced independently in classic and shared firmware | Storage / list semantics, units and model differences |
| Sync gain state | **Returned fields, laboratory:** UserGainList, ActiveUserGain, UserGainStorage and FactoryGain | A returned field is not a same-name setter; no automatic calibration write |
| UserGain | **Firmware handler, laboratory write candidate:** user supplies the explicit value | Accepted format, calibration / preset selection, persistence and reliable read-back |
| ManualLens | **Shared 5.041 handler only, laboratory:** absent from inspected classic 4.701 parser; explicit integer required | Units, range, effect and model support unknown; not mechanical-shutter control |
| GetImage FP / fp | **Classic parser, laboratory:** special zoom literal | Image area, resolution and enabling conditions unknown; not complete MOS or continuous live view |
| Independent FactoryGain getter | **Returned field plus user hypothesis:** candidate GetParamList probe explicitly labelled USER HYPOTHESIS | Neither this getter nor a FactoryGain setter is proven |
| BootRequest | **Internal PXA catalog plus user hypothesis:** manually create a generic empty header with first-frame observation | Not registered in the inspected PDA catalog; not a proven Bluetooth reboot command |
| Internal ISO / calibration class | **Internal functionality / placeholder:** no automatic command without a known tag; the raw console accepts a user's single header | An internal class is not independent wireless calibration syntax or proof of calibration effects |

## Laboratory execution and record boundaries

Open the laboratory at the top of **Back**. Sixteen WiView templates and firmware entries show provenance, unknowns and effects. Unknown inputs have no defaults. Send one raw control header up to 60 KiB, without outbound binary attachments, using a 1–300 second phone timeout. Users review the target / request and confirm; there is no automatic enumeration, fuzzing or firmware flashing.

Set a reply tag, exact file_name and required attachment. `*` observes the first complete frame, potentially an asynchronous notification, without proving causality; an empty tag sends only. Error, nonzero Ack, timeout, cancellation and received frames remain separate, without ordinary success handling. Normal post-capture reads / configuration refresh pause; leaving after sending or cancelling disconnects and cannot promise to undo writes already received by the back.

Per-attachment phone limits are 16 / 64 MiB, changed while disconnected and applied on reconnect. Original `.bin` records include length, SHA-256 and first 256-byte hex. Keep up to 30 records with a 256 MiB attachment-cache budget. ZIP includes complete redacted headers, non-sensitive header hex, original attachments, transport logs and unmatched frames. Known PIN / annotation / copyright / XMP header fields are masked without altering transmitted bytes; original binaries are not automatically redacted. See the [laboratory guide](quick-start.en.md#laboratory-manual-testing).

## Extension targets and internal compatibility

| Item | Current result | Missing evidence |
| --- | --- | --- |
| Complete MOS original download | **No standard implementation; laboratory user hypothesis** | No proven GetFile syntax; users may create a USER HYPOTHESIS empty header with `*`, not an extracted firmware interface. Internal copy / move or a `.mos` path is not transfer evidence |
| Continuous Live View | **No standard implementation; laboratory user hypothesis** | No established continuous-sensor protocol; manual USER HYPOTHESIS candidate headers default to first-frame observation. Repeated JPEGs, FP and post-capture refresh are not live view |
| Remote release of this technical-camera mechanical shutter | **Hardware unsupported** | No app-controlled actuator in this setup; retain manual release |
| Phone-based back firmware flashing | **Not provided** | Firmware archiving, reconstruction and analysis are not a safe upgrade protocol; this work synchronizes reference material only |

`CEVersion` is an **internal compatibility field**, not a missing photographic function. Laboratory constants are evidenced separately: 1.0.2 uses `0x01060600` / `17171968`, and 1.0.3 uses `0x01080000` / `17301504`. They are manual compatibility experiments, not automatically advertised handshake values or Android / back firmware versions.

## Evidence and remaining acceptance

- [Client protocol gap audit](protocol-gap-audit.md): WiView 1.0.2 / 1.0.3 functions, instructions and constructors; gap columns use the 0.2.0 baseline.
- [DP-67 package and setting analysis](dp67-analysis.en.md): twelve core fields, conditional Prefix, .1 slot, wrapper and retry comparison; Setup.exe / CAB retained, complete ZIP with private PBF excluded.
- [Classic Aptus / Aptus 22 and shared-firmware survey](firmware-bluetooth-survey.en.md): independent 4.701 bytes, model association, nine-command catalog, A / B / C layers and CE checksums.
- [5.041 server control evidence](../reference/firmware/analysis/CONTROLS.en.md): independent CameraSettings / ImageSettings, marking, PIN / Brightness and other evidence, tied to one hashed image rather than assumed installed user firmware.
- [Laboratory candidate inventory](../reference/analysis/lab-candidates.json): separate original calls, firmware handlers, reply fields, internal functionality and research without proven syntax.
- [Capabilities and device record](implementation-status.en.md): two model-specific reports, physical acceptance template and new-function limits.
- [verification-status.json](../research/reports/verification-status.json) and [apk-verification.json](../research/reports/apk-verification.json): only matching app versions and APK hashes are evidence for this release's executed build and tool checks.

Complete original-PDA replacement still requires physical acceptance. Record original values, choices, submission, read-back and untouched fields for settings. Check provenance, paths, metadata and new previews for photo operations. Observe real effects separately for request-only features. Without device logs or read-back evidence, retain “unverified” rather than invent success records.
