# Leaf2Android

[简体中文](README.md) · [Install and use](docs/quick-start.en.md) · [Architecture](docs/architecture.en.md) · [Capabilities](docs/implementation-status.en.md) · [Full gap matrix](docs/feature-gap-matrix.en.md) · [0.3.2 changes](docs/releases/v0.3.2.en.md) · [v0.3.2 release](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.2)

A native Android implementation of the Leaf WiView workflow originally used on HP iPAQ / Windows Mobile devices. It connects a phone directly to a Leaf **Aptus / Valeo** digital back using Kotlin, Jetpack Compose and coroutines. The application does not execute the original Windows CE software or require a computer relay.

**0.3.2 (versionCode 5) is available as a pre-release; updated 2026-11-15.** It corrects ordinary capture-notification handling and adds conditional tap-to-sample gray balance and back-action access on the photo page; see the [release notes](docs/releases/v0.3.2.en.md). Existing shooting settings, 1024 MiB default cache, styles, languages and laboratory remain. Aptus / Valeo branding does not establish family-wide support. The user corrected that **Aptus II has no Bluetooth and is not a current wireless target**; the II 5 plan is historical. Galaxy Z Fold7 / Android 16 / One UI 8.5 acceptance is recorded separately.

**Hardware report update, 2026-11-11: the user reports successful Bluetooth connection and photo reading on both Valeo 22Wi and Aptus 22.** This is a report for two specific models, not evidence for Aptus II 5 or the whole family. Back firmware, the actual phone / system, app version and test logs were not supplied with the report.

**2026-11-15 update: the user reports very stable Valeo Bluetooth on v0.3.1 and working shooting settings including ISO and gray balance.** Automatic capture preview still receives `Error error_code="0x01"`, while the connection remains READY and manual reading of new photos works. This is user feedback, not an independent maintainer retest, a 20-photo sequence or acceptance of every setting; complete firmware and environment details remain missing. The 11-14 settings timeout remains historical in the [device record](docs/implementation-status.en.md). The earlier [public review](docs/valeo-connection-research.en.md) did not establish a same-device DP-67 / WiView reliability comparison.

Original-client and firmware [notification analysis](docs/capture-notifications-analysis.en.md) shows that ordinary PictureTaken / ImageChange and similar notifications do not need automatic `Ack`; Stop has a dedicated confirmation. Unknown commands in the examined firmware can produce `0x01`, which must not be labelled busy. Version 0.3.2 removes these extra acknowledgements. They may explain an Error being consumed during automatic GetImage, **but this is not a capture from the user's device, and the new automatic-preview behavior still needs a Valeo retest**.

Classic `FirmwareAptus.UPD` **4.701** has now been independently checked: it contains the Aptus 22 model, nine PDAInterface command classes, complete ShootSetup, marking and image extensions. It is shared firmware containing that model, not a dedicated Aptus 22 flash image or proof of firmware installed on the user's back. See the [firmware Bluetooth survey](docs/firmware-bluetooth-survey.en.md). PDAInterface in 5.041 likewise proves shared code; its settings below describe a software dialect, not Bluetooth hardware in Aptus II.

## Download and first run

Download the signed `Leaf2Android-0.3.2.apk` from the [v0.3.2 pre-release page](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.2). Source, references and SHA-256 follow the final release assets. Historical [0.3.1](docs/releases/v0.3.1.en.md) and [0.3.0](docs/releases/v0.3.0.en.md) notes are retained.

1. Transfer the APK to the phone and install it. The app ID is `com.leaf2android`; Android 12 / API 31 or later is required.
2. Without a back, open the offline demo to inspect zoom, the JPEG histogram, local marks and both styles.
3. Grant Nearby devices permission on the connection page, enable Bluetooth, discover the back and enter its configured PIN in Android’s pairing dialog.
4. Once connection and synchronization succeed, release the technical camera’s mechanical shutter manually. Capture notifications queue JPEG preview requests.

The generated demo image remains labelled as offline and does not imply a successful back connection. See the [installation guide](docs/quick-start.en.md) for signing changes and recovery steps.

## Implementation and boundaries

| Feature | Implementation | Current boundary |
| --- | --- | --- |
| Discovery, pairing and direct Bluetooth | Public Android APIs; Bluetooth Classic SPP / RFCOMM | User-reported connection success on Valeo 22Wi and Aptus 22; pairing, security mode and phone / firmware details pending |
| Synchronization, folders and capture previews | Serialized commands, notification queue, filename matching and exact attachment lengths; no automatic Ack for ordinary notifications | Both models have reading reports; Valeo v0.3.1 automatic preview gets Error while manual reading works; 0.3.2 correction awaits retest |
| Shooting setup and ISO | AUTO / WIVIEW / DP67 compatibility; current values and choices from Sync, with read-before / after-write checks | User reports working Valeo v0.3.1 ISO / gray-balance shooting settings, not every field or model; this release retains that stable path |
| DP-67 writes | Twelve core fields plus an existing Prefix; editing a preset uses its .1 slot, verifying new active setup and original preset | No WiView compression, Orientation / Color additions; hidden in this standard path; existing modified-slot updates are disclosed before sending |
| Setting transactions and diagnostics | operationLock spans pre-Sync, set and read-back, excluding automatic GetImage; only dedicated Stop confirmation can bypass it | Refusals name request tag / attributes and redact replies; decimal / hex DWORD parsing accepts 0x00 as zero and conservatively rejects unknown syntax; timeout closes and writes do not blindly retry |
| 5.041 camera settings | Recognized Sync.CameraSettings enables independent ISO, orientation, camera type, SensorCrop, compression, prefix and shooting-folder writes, followed by full read-back | Choices come from the device; crop accepts name-only lists and compression only None / Lossless; no firmware-wide extrapolation |
| 5.041 image-processing settings | Edit complete ImageSettings or select an existing ActiveImageSettings | Grain / MoireReduction / Saturation and other choices require Sync options; preserve untouched values and read back |
| Playback and cache | 1024 MiB default, unchanged bytes / SHA-256, folder browsing and sorting | One-time old 256 → 1024 migration, preserving 64 and later choices such as 256; previews are not MOS originals |
| Zoom and exposure inspection | Pinch zoom, double-tap zoom / reset, RGB histogram and highlight / shadow indicators | Derived from actual JPEG preview pixels, not RAW data |
| Marks and notes | Local stars / notes are separate from back Good / Unmarked; strict Base64 / UTF-8 XMP parsing, with only the third Flags character changed for marking | Preserve other XMP; evidenced Marking-only when Sync supplies shootsetup / camerasettings; the standard path refuses without XMP or a recognized dialect |
| Back storage management | Photo actions opens marks, notes, copy / move and deletion from the photo page; folder controls remain on Back | Deletion confirms device / path twice and removes source list / cache or selects a neighbor only after directory verification; move verifies both paths before removing source and reading target; no arbitrary MOS rename |
| Back-processed previews | Back image tools on the photo page opens zoom 1 / 2 / 4 / 8, Alarm and gray balance | Separate from local zoom / JPEG warnings; taps require consistent current orientation, PictureDimensions, SHA and aspect evidence, otherwise manual coordinates remain; effects untested |
| Gray balance and exposure inspection | Explicit sampling mode lets users tap a white / gray area, then separately confirm the returned Light for the current photo or future captures | Coordinates use the back reference grid, not claimed RAW sensor pixels; refresh, reconnect or same-name replacement invalidates old metadata / samples; new tap and current-photo effects await testing |
| Clock, default notes and power requests | Phone-local clock write and CurrentTime read-back; default Annotation / Copyright write and read-back; power-source request | PowerSource needs advertised support and reports Ack only; EnableTakenPicture is now in the laboratory, without an unproven pause switch |
| Laboratory | 16 WiView templates, firmware research entries and one raw control header; manual confirmation, response / error / attachment records and ZIP export | Separate standard functions from probes; unknown values have no defaults, Ack does not prove effects, and no automatic scanning or flashing occurs |
| Two styles and two languages | Modern / classic PDA; Chinese / English; follows system language by default | Language and style are independent; session, photo and zoom state are shared |
| Session maintenance and recovery | connectedDevice foreground service, disconnect notification, timeout closure and manual reconnect | Lock-screen, background and long-session behavior need real-phone acceptance |
| Complete MOS transfer | Not enabled | No verified full-file read command yet |
| Continuous live view | Not enabled | Automatic display after capture is a separate function |
| Remote mechanical shutter release | Unsupported by this setup | There is no app-controlled shutter actuator; release remains manual |

There are reports of connection / reading on two models, plus stable Valeo v0.3.1 use and selected shooting settings. They do not establish a 20-photo sequence, every parameter, tap sampling, current-photo gray-balance commit, storage actions or long-session acceptance. Code, synthetic fixtures and emulator runs do not replace physical checks. See the [hardware record and support table](docs/implementation-status.en.md); [machine-readable verification](research/reports/verification-status.json) separately records checks actually executed by tooling.

WiView legacy full setups retain ISO, quality, framing, toning, Light, sharpening, ICC, camera type, prefix, folder, compression, orientation and Color. **DP-67 is a separate smaller writer:** no compression / orientation / Color additions and no CEVersion change. Selecting an original preset sends DefaultShootSetup only; selecting a modified setup sends complete DP-67 CSV. Confirmation identifies the destination slot, and read-back checks the active name, expected values and unchanged original preset. The original DP-67 12-second × 3 retry behavior is not copied. See [static analysis](docs/dp67-analysis.en.md) and the [user guide](docs/quick-start.en.md).

Standard writes start with a fresh read and compare visible expected values and required globals afterward; a mismatch is not success. Independent 5.041 prefix edits cannot increase the known current UTF-8 length and are capped at 23 UTF-16 code units, an implementation limit. The **Back** page groups controls; the laboratory can still manually probe fields hidden by standard compatibility paths.

A readable ShutterSpeed is not a mechanical shutter-speed or aperture writer. Brightness, ResetCounter, PIN, FactorySettings, qformat / recovery, gain, ManualLens and other candidates are available for user-initiated laboratory testing. Unresolved ranges, semantics, wireless reachability and recovery remain labelled experimental rather than verified standard functions. Complete MOS, continuous live view and full original retransmission recovery remain unimplemented. See the [full gap matrix](docs/feature-gap-matrix.en.md).

Open the full-screen laboratory at the top of **Back**. Select a template or enter one control header, supply actual parameters, matching mode and timeout, review the target and request, then confirm sending. Experiments pause ordinary post-capture image reads / configuration refresh. Leaving after sending an experiment disconnects the session to keep late frames out of normal work. See the [laboratory guide](docs/quick-start.en.md#laboratory-manual-testing).

Records retain redacted request / response headers, errors, unmatched frames and original attachments. Known PIN, annotation, copyright and XMP header fields are masked by default without changing transmitted bytes. Binary attachments remain original and are not automatically redacted. GetFile / LiveView / BootRequest / FactoryGain hypothesis probes are explicitly USER HYPOTHESIS, not extracted manufacturer syntax.

## Interface, language and data

- Modern is the default style; classic PDA can be selected in settings. Both use the same session and photo repository, with one-column narrow and two-column wide layouts.
- Language follows the system by default. Settings allow System, Simplified Chinese or English. Actual device names, original paths, user notes and original metadata are not translated or rewritten.
- **UI language and back-protocol encoding are separate settings.** The original client used its PDA system code page. Windows-1252 is the default, with GBK and UTF-8 options for testing. Non-ASCII paths and notes still require firmware verification.
- Follow new photos controls whether a newly received preview becomes the selected photo. Disable it when reviewing older photos.
- Standard photo operations require a preview from the connected back read in this connection. Reconnect, upgrade or process restart requires a fresh read before writes; other-back caches and demos cannot be used directly. Laboratory raw requests use the user's entered target and do not inherit every standard semantic check. Phone stars remain independent.
- Local stars, notes and previews are in the app’s private storage. Uninstalling removes them. Saving a JPEG preview exports only the preview bytes actually received.
- Preview cache defaults to 1024 MiB. Upgrade performs old 256 → 1024 migration once, retaining an existing 64 MiB choice. Later user selections of 256 / 64 / 1024 survive subsequent launches. Laboratory attachments retain their separate budget.

## Repository layout

```text
Leaf2Android/
├─ app/                     Android Bluetooth, session service, storage and localized Compose UI
├─ protocol/                Pure Kotlin framing, commands, serialized session and tests
├─ leafresearch/            Python offline stream parser
├─ tools/                   Research, build, verification, packaging and emulator tools
├─ tests/                   Python framing and research-tool tests
├─ research/fixtures/       Explicitly synthetic protocol fixtures
├─ research/reports/        Current application / APK verification
├─ reference/
│  ├─ wiview/               Original WiView media, guides and provenance
│  ├─ analysis/             Extracted client components, strings, ARM analysis and evidence
│  ├─ firmware/             Deduplicated firmware originals, core reconstruction and analysis, separate from WinCE clients
│  └─ sources.lock.json     Sources, versions and pinned checksums
├─ docs/                    Chinese and English guides, architecture, protocol and acceptance
└─ dist/                    Local build / release attachments, not APK runtime dependencies
```

The working directory is `D:\Leaf2Android`; historical project consolidation and migration are complete. The former `D:\Leaf4Android` workspace may still receive automatically attached files, which must be retained; it is not a directory to keep clearing, and personal attachments are excluded from the public repository. Manufacturer CAB names, internal member names and sample versions are preserved for reproducibility. See [reference material](reference/README.en.md) for back-firmware availability, models and provenance; Windows CE client EXEs / DLLs are not camera-back firmware.

This firmware synchronization collects 89 content-unique original files (about 83.7 MB) plus core reconstruction / analysis. Personal MOS files, calibration files and the roughly 113 MB installer are excluded from the published reference bundle. The inventory and hashes under `reference/firmware` are authoritative. Archiving does not add phone-based firmware flashing or identify the firmware installed on the user's tested backs.

DP-67 references retain the original `Setup.exe` and extracted CAB with extraction / provenance notes. The complete ZIP containing private PBF data is excluded; see [DP-67 analysis](docs/dp67-analysis.en.md).

## Build and signing

Use a full **JDK 21**, Android SDK Platform **36** and Build Tools **36.0.0**. The app uses minSdk 31, compileSdk / targetSdk 36 and JVM bytecode target 17. Dependencies are pinned in Gradle files, and the wrapper distribution checksum is pinned too.

Windows PowerShell:

```powershell
.\tools\build-android.ps1 -Jdk 'C:\path\to\jdk-21' -Sdk "$env:LOCALAPPDATA\Android\Sdk"
```

Or use Gradle directly:

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
```

On macOS / Linux use `./gradlew` for the same tasks and configure `JAVA_HOME` and the SDK. Research CAB extraction needs Windows `expand.exe`; the Android application has no such dependency. Select JDK 21 for Android Studio’s Gradle JDK.

Signed release:

```powershell
.\tools\prepare-release-signing.ps1
.\tools\build-android.ps1 -Release -Jdk 'C:\path\to\jdk-21'
```

The preparation script stores local release signing material under `%USERPROFILE%\.android\Leaf2Android`, outside the repository. A secure local environment or CI secrets may supply these variables instead:

| Variable | Meaning |
| --- | --- |
| `LEAF2ANDROID_KEYSTORE_PATH` | External release keystore path |
| `LEAF2ANDROID_STORE_PASSWORD` | Keystore password |
| `LEAF2ANDROID_KEY_ALIAS` | Signing alias |
| `LEAF2ANDROID_KEY_PASSWORD` | Private-key password |

The standard release task is `:app:assembleRelease`. Retain the same key to provide in-place updates to an existing release installation. Debug and release signatures may differ; consult the installation guide before switching.

## Verification and research reproduction

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-research.txt
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Android tests need a device or emulator. Protocol and emulator coverage includes UI ISO selection, confirmation and complete setup read-back, language switching, Android XMP DOM, refused writes / mismatches and partial-move detection. Final passing counts, the new future-capture gray-balance path, builds, Python research-tool checks and APK verification follow this version's matching-hash [machine reports](research/reports/verification-status.json). Protocol and repository tests use synthetic replies and JPEGs, not real captures.

The original inputs are WiView **1.0.2** and **1.0.3**. Static code shows a Bluetooth Classic SPP client using UUID `00001101-0000-1000-8000-00805f9b34fb`. Controls use `^...>` and may be followed immediately by the raw byte count declared in `attachment`. A `.mos` filename does not establish that GetImage returns a complete original. See the [version analysis](docs/version-analysis.en.md), [protocol boundaries](docs/protocol.en.md) and [research guide](docs/research.en.md).

## Diagnostics, contributions and releases

Settings can display or export recent logs. The app does not fill in or record the Android system pairing PIN. Logs can still contain filenames, back status and metadata; inspect before sharing. Do not commit personal photographs, pairing PINs, signing keys or access tokens.

Compatibility reports should include app version, complete phone model / Android / One UI, exact back model and firmware, whether SPP was discovered, reproduction steps and sanitized logs. Hardware support claims need real records. Protocol changes should cite evidence and reproducible fixtures, distinguish synthetic data from real captures, and avoid guessed UUIDs, handshake values or success states.

Repository: [RayforLoy/Leaf2Android](https://github.com/RayforLoy/Leaf2Android). Source, reference resources and signed APKs are organized separately. Actual attachments are listed on the [v0.3.2 pre-release page](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.2). Original manufacturers retain the rights and licenses applying to their files; this project does not relicense those programs. Source licensing is determined by the license actually supplied with the repository.
