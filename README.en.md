# Leaf2Android

[简体中文](README.md) · [Install and use](docs/quick-start.en.md) · [Architecture](docs/architecture.en.md) · [Capabilities](docs/implementation-status.en.md) · [Protocol](docs/protocol.en.md) · [v0.2.0 release](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0)

A native Android implementation of the Leaf WiView workflow originally used on HP iPAQ / Windows Mobile devices. It connects a phone directly to a Leaf **Aptus / Valeo** digital back using Kotlin, Jetpack Compose and coroutines. The application does not execute the original Windows CE software or require a computer relay.

**0.2.0 is an experimental pre-release.** This update standardizes the project name, organizes reference material and adds Chinese / English localization with a system-language default. “Aptus / Valeo” is a product-family label, **not a claim that every model or firmware is compatible**. The original acceptance target is a Galaxy Z Fold7 running Android 16 / One UI 8.5 with an Aptus II 5 on a technical camera with a mechanical shutter. Real Bluetooth acceptance for that combination is still pending.

## Download and first run

Download the signed `Leaf2Android-0.2.0.apk` from the [v0.2.0 pre-release page](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0). Source, reference resources and SHA-256 checksums are supplied separately; the release page is authoritative for its attachments.

1. Transfer the APK to the phone and install it. The app ID is `com.leaf2android`; Android 12 / API 31 or later is required.
2. Without a back, open the offline demo to inspect zoom, the JPEG histogram, local marks and both styles.
3. Grant Nearby devices permission on the connection page, enable Bluetooth, discover the back and enter its configured PIN in Android’s pairing dialog.
4. Once connection and synchronization succeed, release the technical camera’s mechanical shutter manually. Capture notifications queue JPEG preview requests.

The generated demo image remains labelled as offline and does not imply a successful back connection. See the [installation guide](docs/quick-start.en.md) for signing changes and recovery steps.

## Implementation and boundaries

| Feature | Implementation | Current boundary |
| --- | --- | --- |
| Discovery, pairing and direct Bluetooth | Public Android APIs; Bluetooth Classic SPP / RFCOMM | The target service, PIN and Android 16 link still need testing |
| Synchronization, folders and capture previews | Serialized commands, notification queue, filename matching and exact attachment lengths | Based on static code from two WiView CABs; complete sessions need a real back |
| Playback and cache | Unmodified preview bytes, SHA-256, folder browsing, name / receive-time sorting | Cached previews are not MOS originals |
| Zoom and exposure inspection | Pinch zoom, double-tap zoom / reset, RGB histogram and highlight / shadow indicators | Derived from actual JPEG preview pixels, not RAW data |
| Marks and notes | Local stars / notes; separate back-note write and metadata-read actions | Local changes do not automatically modify the back; back-side effects remain untested |
| Two styles and two languages | Modern / classic PDA; Chinese / English; follows system language by default | Language and style are independent; session, photo and zoom state are shared |
| Session maintenance and recovery | connectedDevice foreground service, disconnect notification, timeout closure and manual reconnect | Lock-screen, background and long-session behavior need real-phone acceptance |
| Complete MOS transfer | Not enabled | No verified full-file read command yet |
| Continuous live view | Not enabled | Automatic display after capture is a separate function |
| Remote mechanical shutter release | Unsupported by this setup | There is no app-controlled shutter actuator; release remains manual |

Development proceeds under the user’s assumption that the back’s wireless hardware works. Implemented code, synthetic fixtures and emulator passes do not replace a real-back test. See the [support table](docs/implementation-status.en.md) and [machine-readable verification](research/reports/verification-status.json).

## Interface, language and data

- Modern is the default style; classic PDA can be selected in settings. Both use the same session and photo repository, with one-column narrow and two-column wide layouts.
- Language follows the system by default. Settings allow System, Simplified Chinese or English. Actual device names, original paths, user notes and original metadata are not translated or rewritten.
- **UI language and back-protocol encoding are separate settings.** The original client used its PDA system code page. Windows-1252 is the default, with GBK and UTF-8 options for testing. Non-ASCII paths and notes still require firmware verification.
- Follow new photos controls whether a newly received preview becomes the selected photo. Disable it when reviewing older photos.
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
│  ├─ firmware/             Back-firmware collection status; WinCE EXE/DLLs are not back firmware
│  └─ sources.lock.json     Sources, versions and pinned checksums
├─ docs/                    Chinese and English guides, architecture, protocol and acceptance
└─ dist/                    Local build / release attachments, not APK runtime dependencies
```

The working directory is `D:\Leaf2Android`. After migration, the former `D:\Leaf4Android` directory is expected to be empty. Manufacturer CAB names, internal member names and sample versions are preserved for reproducibility. No image confirmed to be actual digital-back firmware has been found; see [reference material](reference/README.en.md).

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

Android tests need a device or emulator. Protocol and repository tests use synthetic replies and JPEGs to cover framing, coalescing, invalid lengths, truncation, timeouts, serialized requests, file association and state retention. They are not real capture results. The verification report records current test counts and build evidence; old counts are not presented as the result of a new release.

The original inputs are WiView **1.0.2** and **1.0.3**. Static code shows a Bluetooth Classic SPP client using UUID `00001101-0000-1000-8000-00805f9b34fb`. Controls use `^...>` and may be followed immediately by the raw byte count declared in `attachment`. A `.mos` filename does not establish that GetImage returns a complete original. See the [version analysis](docs/version-analysis.en.md), [protocol boundaries](docs/protocol.en.md) and [research guide](docs/research.en.md).

## Diagnostics, contributions and releases

Settings can display or export recent logs. The app does not fill in or record the Android system pairing PIN. Logs can still contain filenames, back status and metadata; inspect before sharing. Do not commit personal photographs, pairing PINs, signing keys or access tokens.

Compatibility reports should include app version, complete phone model / Android / One UI, exact back model and firmware, whether SPP was discovered, reproduction steps and sanitized logs. Hardware support claims need real records. Protocol changes should cite evidence and reproducible fixtures, distinguish synthetic data from real captures, and avoid guessed UUIDs, handshake values or success states.

Repository: [RayforLoy/Leaf2Android](https://github.com/RayforLoy/Leaf2Android). Source, reference resources and signed APKs are organized separately. Actual attachments are listed on the [v0.2.0 pre-release page](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0). Original manufacturers retain the rights and licenses applying to their files; this project does not relicense those programs. Source licensing is determined by the license actually supplied with the repository.
