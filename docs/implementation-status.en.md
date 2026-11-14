# Capabilities and physical acceptance

[简体中文](implementation-status.md) · [Project home](../README.en.md) · [Full gap matrix](feature-gap-matrix.en.md)

Updated 2026-11-14. **0.3.1 (versionCode 4) is an experimental pre-release**; app ID `com.leaf2android`. Changes are implemented, while candidate fixes for real Valeo settings timeouts still require device retests. Final builds and checks follow this release's reports.

## Scope and evidence

The app uses Aptus / Valeo family branding. The user has corrected the premise: Aptus II itself has no Bluetooth and **is not a current wireless target**. The original Galaxy Z Fold7 / Android 16 / One UI 8.5 + Aptus II 5 technical-camera plan is historical, not a pending Bluetooth acceptance target. Phone and mechanical-shutter acceptance are recorded separately. Family branding does not establish compatibility for every model, firmware, Wireless option or link.

Independent classic Aptus 4.701 analysis locates the Aptus 22 model, nine PDA command classes, complete ShootSetup / DefaultShootSetup, marking and GetImage extensions; see the [firmware Bluetooth survey](firmware-bluetooth-survey.en.md). It is shared firmware, not a dedicated Aptus 22 flash image or proof of installed user firmware. PDAInterface in 5.041 likewise proves shared code, not Bluetooth hardware in Aptus II. Software dialects, static call chains and actual wireless effects are recorded separately.

Early implementation proceeded under the user's assumption that wireless works. On 2026-11-11 the user reported real connection and photo reading on Valeo 22Wi and Aptus 22; the project now has physical-use feedback. This does not confirm every field, notification sequence, parameter operation, original-file transfer or live-view operation. Final use remains a phone directly connected to the back, without a computer relay or bridge.

## User-reported hardware record

| Model | Report date | Bluetooth connection | Photo reading | Evidence scope |
| --- | --- | --- | --- | --- |
| Valeo 22Wi | 2026-11-11 | Reported working by the user | Reported working by the user | User's use report; firmware, phone / system, app version and logs not supplied |
| Aptus 22 | 2026-11-11 | Reported working by the user | Reported working by the user | User's use report; firmware, phone / system, app version and logs not supplied |
| Aptus II, including the earlier II 5 target | 2026-11-11 | Not a Bluetooth target | Wireless reading not applicable | User corrected that this family has no Bluetooth; shared firmware code is not hardware-support evidence |

This is model-specific user feedback, not an independent maintainer retest or complete acceptance. The report does not establish a 20-photo sequence, file association, folder details, ISO or other parameter reads / writes, back marking, note read-back, screen lock / background, original files or live view. The test phone is not assumed to be the originally planned Z Fold7. Aptus II 5 and family-wide compatibility are not inferred.

### 2026-11-14: settings timeout incident

The user reports working Valeo photo reads with disconnection on settings changes, without identifying the field. The screenshot shows the app closing after a reply timeout, not whether Ack was absent, parsing / matching failed, image requests interleaved or the physical link failed earlier. Version 0.3.1 adds whole-operation locking, request summaries and a separate DP-67 writer; **a real-device resolution is not claimed**.

Offline testing also exposed background Sync from CameraShootSetupChangeNotification clearing a just-completed success or read-back mismatch. Automatic refresh now retains error / notice; only a new manual operation clears them. This concerns overwritten app display / verification results, not a proven back-disconnection cause. See the [independent public-evidence review](valeo-connection-research.en.md).

## Support table

Existing behavior, newly evidenced standard functions and laboratory entries are listed below. The user has not verified new parameters, processing, marking, storage or session controls on Valeo 22Wi or Aptus 22. Laboratory probes record evidence without treating unexplained replies as proven effects.

| Feature | Code in this version | Device acceptance |
| --- | --- | --- |
| Discovery, pairing, SPP | Public API; secure and optional insecure RFCOMM | User-reported success on Valeo 22Wi and Aptus 22; PIN, security mode, phone / system and firmware details pending |
| Synchronization and folders | RequestSync → CurrentTime → dir; errors are not ready | Photo reading reported on both models; full handshake, folder fields and date syntax not separately accepted |
| Shooting compatibility | Select AUTO / WIVIEW / DP67 while disconnected; AUTO requires Valeo in the Bluetooth name and shootsetup in Sync | Serialization only, not pairing / CEVersion changes; renamed backs can select explicitly; not hardware-capability detection |
| WiView legacy / ISO | Complete ShootSetup and device options, retaining untouched values | Its compression / orientation / Color additions are not applied to DP-67; pre / post Sync, effects untested |
| DP-67 / ISO | Twelve core fields, retaining Prefix only if present; edit original presets into .1 slots | No compression, Orientation or Color writes; confirm slot update and verify new active, expected values and unchanged preset; effects untested |
| Existing preset selection | DP-67 originals send DefaultShootSetup only; modified setups send complete CSV | Not FactorySettings; retain original contains(".1") name rule and read back the selection |
| Whole-operation locking and results | operationLock spans pre-Sync, set and read-back, excluding automatic GetImage; notification Ack bypasses it; automatic refresh retains results | No automatic write retries, timeout still closes; summaries name tag / attributes only; real Valeo retest pending |
| 5.041 camera settings | Recognized Sync.CameraSettings supports independent ISO, Orientation, CameraType, SensorCrop, FileCompression, Prefix and ShootingFolder writes | Device-provided choices; name-only SensorCrop, None / Lossless compression, prefix limits below; full read-back; physical effects untested |
| 5.041 image-processing settings | Complete ImageSettings editing and existing ActiveImageSettings selection; preserve untouched fields | Includes Grain / MoireReduction / Saturation only when Sync advertises options; fresh read and full read-back; physical effects untested |
| Automatic post-capture display | Notifications, filename matching, attachment length, serialized queue | At least 20 real photos must be matched |
| JPEG cache and playback | Original bytes, SHA-256, 1024 MiB default with 64 / 256 / 1024 choices | One-time old 256 → 1024 migration retains 64 and later choices; byte comparison, resolution and speed still untested |
| Zoom and exposure inspection | Pinch / double-tap, JPEG histogram and indicators | Real detail range pending; no RAW pixels used |
| Photo information and XMP | GetPictureParams attributes, strict Base64 / UTF-8 XMP decoding and preservation; marking changes only the third Flags character | No empty replacement when xmp_tag header is absent; damaged or unsafe XMP refuses editing; actual attributes and units untested |
| Stars and local notes | Persistent phone-side data, explicitly local | No back dependency |
| Back notes | SetPictureParams; matching success and no error code required | Write / read-back effects pending |
| Back Good / Unmarked | Independent of phone stars; retain XMP and change only the third Flags character when present; Sync shootsetup / camerasettings enables Marking-only evidenced in classic 4.701 / 5.041 | Standard path refuses without XMP or a recognized dialect; current-connection preview and marking read-back required; effects untested |
| Back storage management | Create folders, delete empty folders, rename folders, delete photos, internal copy / move; explicit confirmation, no overwrite and directory read-back | New operations untested; not complete MOS transfer to the phone |
| Back zoom / Alarm | Implemented original 1 / 2 / 4 / 8 processed previews and highlight / shadow / both Alarm, returning JPEG | Distinct from local zoom / JPEG warnings; original-image protocol coordinates are entered manually, with no guessed Compose mapping; effects untested |
| Gray-balance sample / confirm / restore | Implemented sampled preview and returned Light display; separate confirmation for the current photo or future captures, plus preview restore | Future captures use complete legacy ShootSetup.Light / 5.041 ImageSettings.Light and Sync comparison; no arbitrary Kelvin / tint; effects untested |
| Back pixel / exposure picking | Implemented ImageFunction coordinate request and histogramlocation / histogramvalue display | fstops carries coordinates, not aperture; labelled back-provided with RAW / JPEG provenance unconfirmed; effects untested |
| Back histograms | Implemented variable-bin histogram / r / g / b parsing and display | Back-provided, with actual reply bin counts; no fixed 256-bin or RAW assumption; effects untested |
| Back clock synchronization | Write phone-local time to CurrentTime, then read it separately | Does not invent protocol time-zone support; actual clock and capture dates need testing |
| Default future-capture notes | Read, write and read back default Annotation / Copyright | Separate from notes on the current photo; does not rewrite existing images; effects untested |
| EnableTakenPicture experiment | yes / no template moved to the laboratory, retaining request and original reply | no has not been proved to stop notifications; no unproven pause button in the main UI; not shutter release |
| Power-source request | FireWire / CameraBattery, enabled only with powersourceselection=support | Request Ack only; no claim of a confirmed power state; effects untested |
| Back control page | Bottom navigation's Back page groups settings and session actions | Controls require the recognized dialect, current values, complete structures and device choices |
| Laboratory templates and candidates | Open at the top of Back; 16 client templates, firmware research entries and one raw control header | Enter unknown inputs and confirm target / request before sending; ranges, semantics and wireless effects remain unverified |
| Experiment records and export | Received / error / cancelled / send-only recorded separately; original .bin, length / SHA-256 / first 256-byte hex, ZIP export | Nonzero Ack is not converted to normal success; known header fields are masked, binary attachments unchanged |
| Modern / classic PDA | Shared session, photo, zoom and display state | Real displays, rotation and One UI split-screen pending |
| Chinese / English | System default with manual selection; independent of style | Check system locale, switching / recreation and notifications against test reports |
| Foreground session | connectedDevice service, disconnect notification, permission and disconnect handling | Lock-screen, background and long sessions pending |
| Complete MOS originals | Not implemented | Compare CF originals after the operation is established |
| Continuous live view | Not implemented | No verified continuous-sensor interface |
| Remote mechanical release | Hardware unsupported in the current setup | No actuator; manual release |

This pre-release is not completion of full original-PDA replacement acceptance.

## Setup and dialect boundaries

WiView legacy complete ShootSetup covers ISO, ImageQuality, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, CameraType, ICCFlag, Prefix, ShootingFolder, FileCompression / NLCompression, Orientation and Color. DP-67 has a separate set: SetupName, ShootingFolder, ISO, ImageQuality, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, CameraType and ICCFlag; retain Prefix only when present. Missing core fields or device options are not guessed; ICCFlag remains evidenced 0 / 1.

Legacy ISO is in the complete setup. Independent 5.041 CameraSettings requires that structure and a non-DP-67 path. DP-67 omits WiView compression and global Orientation / Color without changing CEVersion; standard controls hide those fields, with manual laboratory probes still available. DP-67 control refresh sends Sync + CurrentTime only, avoiding extra automatic getters.

Editing an original DP-67 preset clones to `original-name.1` unless its name already contains `.1`, retaining that modified slot. Confirmation shows the destination and possible existing-slot update. Fresh state is checked before submitting; read-back verifies new active, expected values, visible untouched fields / globals and unchanged original preset. The original 12-second × 3 retry is not copied; timeout still closes, with manual reconnect and no automatic write retry. See [DP-67 static analysis](dp67-analysis.en.md).

Pre-Sync, write and read-back share operationLock; automatic image requests cannot interleave while notification Ack bypasses the lock. This queues phone operations rather than locking the back or other clients. Timeout summaries name the unanswered tag and attributes without values, distinguishing write Ack from read-back wait, not establishing physical-loss causality.

5.041 camera settings and ImageSettings are separate structures; ISO is not an ImageSettings field. Processing settings cover ImageQuality, Color, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, ICCFlag, Grain, MoireReduction and Saturation. Missing complete structures or device choices are not filled with guesses. Independent SensorCrop accepts name-only lists, not guessed compound crop records. Independent compression accepts advertised None / Lossless, separate from legacy Nearly Lossless. Independent Prefix is capped at 23 UTF-16 code units and cannot increase the known current prefix's UTF-8 byte length; this is an implementation limit, not a manufacturer hardware specification. A shooting folder is checked before writing.

Standard photo marking, notes, gray balance and storage operations require a preview from the connected back read in this connection. Connection generation is not persisted, so reconnect, upgrade or process restart requires a fresh preview before standard writes. XMP comes from xmp_tag, retaining other fields and Flags characters while changing only the third character. Separate full-XMP / annotation+XMP templates and the raw console use user-entered targets and do not inherit every standard semantic or photo-provenance check.

Applying gray balance to future captures requires separate confirmation. Only a Light sample returned by the current back in this connection and still tied to the current photo may bypass the static illuminant list through this path. CSV delimiters remain rejected, the full configuration's other fields are preserved, and Sync verifies the result. Arbitrary typed Light values cannot bypass option validation.

Formatting, recovery, FactorySettings, Brightness, ResetCounter and PIN now have laboratory templates. Unknown ranges, scope, counter semantics and Android bond recovery remain experimental, not verified standard settings. A readable ShutterSpeed is not a mechanical shutter-speed or aperture writer. See the [feature gap matrix](feature-gap-matrix.en.md) for layers and unresolved reasons; the original [client gap audit](protocol-gap-audit.md) uses a 0.2.0 baseline.

## Laboratory observation boundaries

The laboratory accepts one encoded control header up to 60 KiB and a 1–300 second timeout. Set a reply tag, exact file_name and attachment matching if needed. `*` observes the first complete frame without proving causality; an empty tag sends only. Original replies, nonzero Ack, Error and timeout are retained without normal success validation. Ordinary post-capture image reads / configuration refresh pause during experiments. Leaving after sending disconnects; cancellation also disconnects and does not promise to undo a write already received by the back.

Select a 16 / 64 MiB per-attachment phone limit while disconnected; reconnect to apply it. Keep up to 30 records with a 256 MiB attachment-cache budget. ZIP exports include complete redacted headers, non-sensitive header hex, original .bin, logs and unmatched frames. Known PIN, annotation, copyright and XMP header fields are masked without changing transmitted bytes. Binary attachments remain original; inspect them before sharing. See the [laboratory guide](quick-start.en.md#laboratory-manual-testing).

Gain / UserGain / ManualLens / GetImage FP entries show firmware evidence levels. For GetFile / LiveView / BootRequest / FactoryGain, the user can create an explicitly labelled USER HYPOTHESIS empty header or candidate GetParamList probe, defaulting to first-frame observation. These are not extracted, proven firmware syntax. Internal calibration without a known tag does not automatically generate a command; the raw console still accepts a user-entered single header. There is no automatic fuzzing or blind firmware flashing.

## Firmware references

This work collects 89 content-unique originals (about 83.7 MB) plus core reconstruction / analysis from `D:\leaf`. The `reference/firmware` inventory is authoritative for sources, models, sizes and hashes. Personal MOS files, calibration files and the roughly 113 MB installer are excluded from published resources. Archiving and static analysis do not add phone-based flashing or identify firmware installed on the user's two tested backs.

The DP-67 user package contributes only original Setup.exe, extracted CAB and research records, excluding the complete ZIP with private PBF data. Installer analysis is not a device retest; see the [package analysis](dp67-analysis.en.md).

## Offline and emulator verification

Current counts, APK integrity, Lint, signing and alignment are recorded in [verification-status.json](../research/reports/verification-status.json) and [apk-verification.json](../research/reports/apk-verification.json). User hardware reports and tool-executed checks are different evidence types. Do not rewrite feedback as an independent maintainer retest, or treat an older report, APK or count as the current release result.

Protocol and API 36 emulator coverage includes actual UI ISO selection, confirmation and complete setup read-back, language switching, Android XMP DOM smoke testing, refused writes, read-back mismatches, unexpected resets of untouched fields, ISO notifications not polluting choices, and partial-move detection. Python, protocol and Android checks passed; detailed results follow the matching-version machine reports above. Signed in-place upgrade, cache migration and explicit DP67 persistence across restarts / languages are recorded in [cache-upgrade-verification.json](../research/reports/cache-upgrade-verification.json). These offline results do not establish that real Valeo settings timeouts are solved.

Protocol tests cover byte splits, coalescing, binary delimiters, duplicate attributes, invalid lengths, truncation, reset, serialized requests, interleaved notifications, timeout closure and errors. Repository tests receive decodable synthetic JPEGs through a fake transport and check filename, dimensions, SHA-256 and cached bytes. UI tests cover style, language and state retention.

Synthetic images, peers and window sizes do not replace real Bluetooth, physical folding or One UI tests. They provide no measured back image quality, live-view refresh rate or original-file transfer result.

## Physical acceptance template

| Area | Record |
| --- | --- |
| Phone | Full model, Android, One UI, security update and app version |
| Back | Exact model, firmware, Wireless menu, Bluetooth name and SDP service |
| Pairing | First pairing, wrong PIN, forgotten / invalid-bond recovery |
| Session | Back reboot, timeout, disconnect, reconnect, screen lock and background |
| Capture | At least 20 photos, matched individually by content, name, order and automatic display |
| Setup / ISO | AUTO / WIVIEW / DP67, original values, choices, request summary and read-back; DP-67 .1 target, new active, unchanged preset and existing-slot confirmation; timeout is not success |
| Operations and background events | Capture / setup notifications must not insert GetImage into pre-Sync / set / read-back; notification Ack remains timely; refresh retains success / mismatch results |
| Cache migration | Old 256 → 1024, retain 64; after migration choose 256 and restart without reset; separate laboratory attachment budget |
| Back marking / notes | Keep Good / Unmarked separate from phone stars; read back and compare unrelated XMP namespaces and remaining Flags characters; verify caches cannot be written directly after reconnect, upgrade or process restart |
| Storage operations | Use disposable test photos and empty folders; record source / destination, confirmation, no overwrite and refreshed directory contents; read actual state after a disconnect |
| Server image inspection | Verify 1 / 2 / 4 / 8, Alarm, manually entered coordinates and actual orientation, gray-balance sample / photo confirmation / separate future-capture confirmation / restore, pixel picking and histogram provenance |
| Clock and default notes | Compare phone-local time with CurrentTime; read back defaults and capture a new photo; confirm existing photos are unchanged |
| Session requests | Record PowerSource Ack and separately observe power; test EnableTakenPicture yes / no in the laboratory without assuming no stops notifications |
| Laboratory | Record model / firmware, template or USER HYPOTHESIS, target / request, matching mode, original reply / error, attachment hashes and actual effects separately; inspect masked headers and original attachments before export |
| UI | Both styles; Chinese / English / System; inner / outer screens, orientation, split-screen and state |
| MOS | Once supported, size, SHA-256 and byte comparison with CF originals |
| Live view | Once supported, resolution, measured refresh rate and stability |

Record new compatibility evidence after changing Aptus / Valeo model or firmware; do not infer family-wide support from one model.
