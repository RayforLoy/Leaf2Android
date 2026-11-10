# Leaf2Android 0.2.0: installation, use and build

[简体中文](quick-start.md) · [Project home](../README.en.md) · [Capabilities](implementation-status.en.md)

## Choose an attachment

Use the [v0.2.0 pre-release](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0) or local `dist/`.

| File | Purpose |
| --- | --- |
| `Leaf2Android-0.2.0.apk` | Release test app signed with the publisher's release key |
| `Leaf2Android-0.2.0-debug.apk` | Development APK; signature may differ from release |
| `Leaf2Android-0.2.0-source.zip` | Buildable source and documentation |
| Reference-material archive | Original WiView media, analysis and provenance; not embedded runtime libraries |
| `*.sha256` / checksum manifest | Corresponding attachment checksums |

The release page lists its actual attachments. App ID: `com.leaf2android`; minimum Android 12 / API 31; compile and target Android 16 / API 36. This pre-release does not establish compatibility with the entire Aptus / Valeo family.

## Install and understand signatures

Transfer the APK to the phone, open it in a file manager and follow Android's prompt to allow that installation source. With a connected development device:

```powershell
adb install -r .\dist\Leaf2Android-0.2.0.apk
```

An in-place update requires the same application ID and signing key. If moving from debug to release produces `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, save needed previews and separately record local notes / marks before uninstalling the old package and installing the new one. Uninstalling removes private app data. Earlier incorrectly named packages have a different ID from `com.leaf2android`; their data is not migrated automatically.

Publishers must retain the release keystore. Do not commit keys, passwords or a personal `local.properties`. Vendor EXE, DLL and CAB files are not included in the APK.

## Language and style

The first launch uses the modern style and follows the system language. Settings allow System, Simplified Chinese or English, independently of Modern / classic PDA. Both styles use the shared photo and session repository and preserve the zoom position.

Only interface text changes language. Actual device names, original paths, user notes and back-provided metadata are not translated. Back-protocol encoding is a separate setting; selecting English does not alter wireless encoding.

## Try the offline demo

Open the demo from the shooting page or settings. The app generates and labels it as offline, keeping connection state disconnected.

- Pinch and pan; double-tap to zoom / reset; Fit to screen resets the viewport.
- Tools include a JPEG histogram, JPEG highlight / shadow indicators, notes and preview export.
- Stars and local notes stay on the phone and do not automatically change the back.
- Histograms and indicators use JPEG samples, not RAW pixels.
- Narrow windows use one column, wide windows two. Real Fold inner / outer displays, rotation and One UI split-screen still need acceptance.

## Connect directly to Aptus / Valeo

1. Enable Wireless on the back and close other WiView clients using it.
2. Grant Nearby devices permission on the connection page and enable phone Bluetooth.
3. Discover and identify the back. Pair using Android's dialog and the back's configured PIN, then connect. The app does not guess a PIN.
4. The app uses Bluetooth Classic SPP / RFCOMM UUID `00001101-0000-1000-8000-00805f9b34fb`. Startup attempts RequestSync, CurrentTime and the root directory, and reports ready only after synchronization.
5. Release the technical camera's mechanical shutter manually. A saved-photo notification with a usable filename queues the preview. Follow new photos selects the newest received preview.
6. Playback supports back subfolders, name / receive-time sorting and local-star filtering. Uncached previews need an active connection.

The original hardware target is the Aptus II 5. Family branding does not automatically establish another model's compatibility. Record exact back model, firmware, phone system and advertised service during first testing. Full sessions and actual previews are not yet accepted on real hardware.

## Photo information, notes and files

Information shows received preview dimensions, size, SHA-256 and parsed attributes. Back metadata can be requested while connected. Uninterpreted XMP attachments are not presented as successfully parsed metadata.

Save local note writes only to the phone. Write back note uses `SetPictureParams` and needs a matching success response. Nonzero or unrecognized error codes are not success; effects still need a read-back check on real firmware. Quotes, control characters and `& < > ^` are rejected until escaping is verified.

Windows-1252 is the protocol default. GBK and UTF-8 may be selected while disconnected for testing. ASCII paths are unaffected. Unencodable text is rejected rather than replaced with question marks.

A `.mos` filename does not turn a GetImage preview into a complete original. Complete MOS download and continuous live view are not implemented. The current mechanical shutter has no remote actuator.

## Recovery and diagnostics

For an expired bond or wrong PIN, forget the old bond in Android settings and pair again. Ensure another client is not occupying the back. Try insecure RFCOMM only if required by old firmware; it cannot promise to bypass Android link requirements.

Host-side waits are approximately 35 seconds for Bluetooth, 20 for ordinary commands and 60 for images. These are implementation settings, not measured back specifications. Timeouts close the stream and require manual reconnect to avoid assigning late replies to another photo.

Settings can display or export logs. The system pairing PIN is not recorded, but paths, camera properties and user metadata may be present. Inspect before sharing. Reports should include app version, phone system, exact back / firmware, steps and sanitized logs. Do not publish signing material or private photos.

## Build

Use a full JDK 21, SDK Platform 36 and Build Tools 36.0.0. JVM target is 17. Dependencies and wrapper checksum are pinned. Select JDK 21 for Android Studio's Gradle JDK.

```powershell
.\tools\build-android.ps1 -Jdk 'C:\path\to\jdk-21' -Sdk "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The script runs protocol tests, Lint and APK assembly and writes `dist/`. The last command requires a device or emulator. Install `system-images;android-36;default;x86_64` and run `tools/start-test-emulator.ps1` for the project AVD.

Release signing can be prepared outside the repository, or supplied using `LEAF2ANDROID_KEYSTORE_PATH`, `LEAF2ANDROID_STORE_PASSWORD`, `LEAF2ANDROID_KEY_ALIAS` and `LEAF2ANDROID_KEY_PASSWORD`:

```powershell
.\tools\prepare-release-signing.ps1
.\tools\build-android.ps1 -Release -Jdk 'C:\path\to\jdk-21'
# Standard task: .\gradlew.bat :app:assembleRelease
```

References: [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [RFCOMM](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices), [foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device), [app languages](https://developer.android.com/guide/topics/resources/app-languages), [Android 16 layouts](https://developer.android.com/about/versions/16/behavior-changes-16).
