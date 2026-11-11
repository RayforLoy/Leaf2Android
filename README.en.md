# Leaf2Android

[简体中文](README.md) · [Install and use](docs/quick-start.en.md) · [Architecture](docs/architecture.en.md) · [Capabilities](docs/implementation-status.en.md) · [Full gap matrix](docs/feature-gap-matrix.en.md) · [0.3.0 changes](docs/releases/v0.3.0.en.md) · [v0.3.0 release](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.0)

A native Android implementation of the Leaf WiView workflow originally used on HP iPAQ / Windows Mobile devices. It connects a phone directly to a Leaf **Aptus / Valeo** digital back using Kotlin, Jetpack Compose and coroutines. The application does not execute the original Windows CE software or require a computer relay.

**0.3.0 is an experimental pre-release.** Cross-checked client and firmware evidence now supports implemented shooting and image-processing settings, ISO, back previews and exposure inspection, gray balance, marking, storage management and session controls. Code implementation does not establish hardware effects; new functions still need physical testing. See the [release notes](docs/releases/v0.3.0.en.md). “Aptus / Valeo” is family branding, **not a claim of universal model or firmware compatibility**. The user has corrected the hardware premise: **Aptus II itself has no Bluetooth and is not a current wireless target**. The early Aptus II 5 plan is historical; Galaxy Z Fold7 / Android 16 / One UI 8.5 phone acceptance still needs its own record.

**Hardware report update, 2026-11-11: the user reports successful Bluetooth connection and photo reading on both Valeo 22Wi and Aptus 22.** This is a report for two specific models, not evidence for Aptus II 5 or the whole family. Back firmware, the actual phone / system, app version and test logs were not supplied with the report.

Classic `FirmwareAptus.UPD` **4.701** has now been independently checked: it contains the Aptus 22 model, nine PDAInterface command classes, complete ShootSetup, marking and image extensions. It is shared firmware containing that model, not a dedicated Aptus 22 flash image or proof of firmware installed on the user's back. See the [firmware Bluetooth survey](docs/firmware-bluetooth-survey.en.md). PDAInterface in 5.041 likewise proves shared code; its settings below describe a software dialect, not Bluetooth hardware in Aptus II.

## Download and first run

After publication, download the signed `Leaf2Android-0.3.0.apk` from the [v0.3.0 pre-release page](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.0). Source, references and SHA-256 checksums are supplied separately. The page or assets may be unavailable before publication; the actual release page is authoritative.

1. Transfer the APK to the phone and install it. The app ID is `com.leaf2android`; Android 12 / API 31 or later is required.
2. Without a back, open the offline demo to inspect zoom, the JPEG histogram, local marks and both styles.
3. Grant Nearby devices permission on the connection page, enable Bluetooth, discover the back and enter its configured PIN in Android’s pairing dialog.
4. Once connection and synchronization succeed, release the technical camera’s mechanical shutter manually. Capture notifications queue JPEG preview requests.

The generated demo image remains labelled as offline and does not imply a successful back connection. See the [installation guide](docs/quick-start.en.md) for signing changes and recovery steps.

## Implementation and boundaries

| Feature | Implementation | Current boundary |
| --- | --- | --- |
| Discovery, pairing and direct Bluetooth | Public Android APIs; Bluetooth Classic SPP / RFCOMM | User-reported connection success on Valeo 22Wi and Aptus 22; pairing, security mode and phone / firmware details pending |
| Synchronization, folders and capture previews | Serialized commands, notification queue, filename matching and exact attachment lengths | Photo reading reported on both models; folders, automatic post-capture refresh and complete notification timing not separately accepted |
| Legacy shooting setup and ISO | Sync supplies full ShootSetup, current setup and choices; select an existing setup or submit a complete update preserving untouched values | No hardcoded ISO steps; fresh read before writing and Sync verification afterward; new writes untested on hardware |
| 5.041 camera settings | Recognized Sync.CameraSettings enables independent ISO, orientation, camera type, SensorCrop, compression, prefix and shooting-folder writes, followed by full read-back | Choices come from the device; crop accepts name-only lists and compression only None / Lossless; no firmware-wide extrapolation |
| 5.041 image-processing settings | Edit complete ImageSettings or select an existing ActiveImageSettings | Grain / MoireReduction / Saturation and other choices require Sync options; preserve untouched values and read back |
| Playback and cache | Unmodified preview bytes, SHA-256, folder browsing, name / receive-time sorting | Cached previews are not MOS originals |
| Zoom and exposure inspection | Pinch zoom, double-tap zoom / reset, RGB histogram and highlight / shadow indicators | Derived from actual JPEG preview pixels, not RAW data |
| Marks and notes | Local stars / notes are separate from back Good / Unmarked; strict Base64 / UTF-8 XMP parsing, with only the third Flags character changed for marking | Preserve other XMP; evidenced Marking-only when Sync supplies shootsetup / camerasettings; the standard path refuses without XMP or a recognized dialect |
| Back storage management | Create folders, delete empty folders, rename folders, delete photos, copy / move within the back | No destination overwrite; explicit path confirmation and directory read-back; not original-file downloading |
| Back-processed previews | Implemented zoom 1 / 2 / 4 / 8 and highlight / shadow Alarm, returning JPEG | Distinct from local pinch zoom / JPEG warnings; manually enter original-image protocol coordinates rather than guess a screen mapping; physical effects untested |
| Gray balance and exposure inspection | Separate confirmation to apply a sample to the current photo or future captures, plus preview restore, coordinate pixel picking and variable-bin back histograms | Future-capture writes require the back's current-connection sample still tied to the current photo, complete configuration submission and read-back; fstops carries coordinates; RAW / JPEG source unconfirmed; effects untested |
| Clock, default notes and power requests | Phone-local clock write and CurrentTime read-back; default Annotation / Copyright write and read-back; power-source request | PowerSource needs advertised support and reports Ack only; EnableTakenPicture is now in the laboratory, without an unproven pause switch |
| Laboratory | 16 WiView templates, firmware research entries and one raw control header; manual confirmation, response / error / attachment records and ZIP export | Separate standard functions from probes; unknown values have no defaults, Ack does not prove effects, and no automatic scanning or flashing occurs |
| Two styles and two languages | Modern / classic PDA; Chinese / English; follows system language by default | Language and style are independent; session, photo and zoom state are shared |
| Session maintenance and recovery | connectedDevice foreground service, disconnect notification, timeout closure and manual reconnect | Lock-screen, background and long-session behavior need real-phone acceptance |
| Complete MOS transfer | Not enabled | No verified full-file read command yet |
| Continuous live view | Not enabled | Automatic display after capture is a separate function |
| Remote mechanical shutter release | Unsupported by this setup | There is no app-controlled shutter actuator; release remains manual |

Early development assumed working wireless hardware; the two model-specific connection and photo-reading reports now provide physical-use feedback. They do not establish a 20-photo sequence, ISO or other parameter operations, back-note writes, complete MOS transfer, live view or long sessions. Code, synthetic fixtures and emulator runs do not replace those checks either. See the [hardware record and support table](docs/implementation-status.en.md); [machine-readable verification](research/reports/verification-status.json) separately records checks actually executed by tooling.

The legacy full setup covers ISO, quality, framing, toning, Light, sharpening, input / output ICC, camera type, ICCFlag, filename prefix, shooting folder, ordinary / Nearly Lossless compression with NLCompression, orientation and Color. Writes start with a fresh read and compare the complete expected setup, untouched fields and required globals afterward; a mismatch is not success. Independent 5.041 prefix edits cannot increase the known current UTF-8 length and are capped at 23 UTF-16 code units. This is an implementation limit, not a hardware specification. The bottom navigation's **Back** page groups camera, image-processing and session controls.

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

The working directory is `D:\Leaf2Android`. After migration, the former `D:\Leaf4Android` directory is expected to be empty. Manufacturer CAB names, internal member names and sample versions are preserved for reproducibility. See [reference material](reference/README.en.md) for back-firmware availability, models and provenance; Windows CE client EXEs / DLLs are not camera-back firmware.

This firmware synchronization collects 89 content-unique original files (about 83.7 MB) plus core reconstruction / analysis. Personal MOS files, calibration files and the roughly 113 MB installer are excluded from the published reference bundle. The inventory and hashes under `reference/firmware` are authoritative. Archiving does not add phone-based firmware flashing or identify the firmware installed on the user's tested backs.

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

Repository: [RayforLoy/Leaf2Android](https://github.com/RayforLoy/Leaf2Android). Source, reference resources and signed APKs are organized separately. Actual attachments are listed on the [v0.3.0 pre-release page](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.0). Original manufacturers retain the rights and licenses applying to their files; this project does not relicense those programs. Source licensing is determined by the license actually supplied with the repository.
