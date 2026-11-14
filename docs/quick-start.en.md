# Leaf2Android 0.3.1: installation, use and build

[简体中文](quick-start.md) · [Project home](../README.en.md) · [Capabilities](implementation-status.en.md) · [Full gap matrix](feature-gap-matrix.en.md)

## Choose an attachment

**0.3.1 (versionCode 4) is an experimental pre-release; updated 2026-11-14.** Use the [v0.3.1 page](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.1) or local `dist/`; see the [release notes](releases/v0.3.1.en.md). DP-67 writes and transaction changes still need real Valeo testing; settings timeouts are not claimed solved.

| File | Purpose |
| --- | --- |
| `Leaf2Android-0.3.1.apk` | Release test app signed with the publisher's release key |
| `Leaf2Android-0.3.1-debug.apk` | Development APK; signature may differ from release |
| `Leaf2Android-0.3.1-source.zip` | Buildable source and documentation |
| Reference-material archive | Original WiView media, analysis and provenance; not embedded runtime libraries |
| `*.sha256` / checksum manifest | Corresponding attachment checksums |

The release page lists its actual attachments. App ID: `com.leaf2android`; minimum Android 12 / API 31; compile and target Android 16 / API 36. This pre-release does not establish compatibility with the entire Aptus / Valeo family.

## Install and understand signatures

Transfer the APK to the phone, open it in a file manager and follow Android's prompt to allow that installation source. With a connected development device:

```powershell
adb install -r .\dist\Leaf2Android-0.3.1.apk
```

An in-place update requires the same application ID and signing key. If moving from debug to release produces `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, save needed previews and separately record local notes / marks before uninstalling the old package and installing the new one. Uninstalling removes private app data. Earlier incorrectly named packages have a different ID from `com.leaf2android`; their data is not migrated automatically.

Publishers must retain the release keystore. Do not commit keys, passwords or a personal `local.properties`. Vendor EXE, DLL and CAB files are not included in the APK.

## Language and style

The first launch uses the modern style and follows the system language. Settings allow System, Simplified Chinese or English, independently of Modern / classic PDA. Both styles use the shared photo and session repository and preserve the zoom position.

Only interface text changes language. Actual device names, original paths, user notes and back-provided metadata are not translated. Back-protocol encoding is a separate setting; selecting English does not alter wireless encoding.

## Preview cache and upgrades

Preview cache defaults to **1024 MiB**, with 64 / 256 / 1024 MiB settings. The first upgrade under this rule migrates an old stored 256 MiB value to 1024 while retaining 64. Migration runs once; choosing 256 or another value afterward survives subsequent launches. This stores received previews, without increasing back resolution or providing MOS-original capacity. The laboratory's separate 256 MiB attachment-cache budget is unchanged.

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

On 2026-11-11 the user reported working Bluetooth and photo reading on Valeo 22Wi and Aptus 22, without the actual phone / system, firmware, app version or logs. This does not establish a 20-photo sequence or the new writes below. The user also corrected that Aptus II itself has no Bluetooth and is not a current wireless target; the early Aptus II 5 plan is historical. 5.041 PDAInterface is shared code, not evidence of Aptus II Bluetooth hardware. Family branding is not universal compatibility.

The 2026-11-14 Valeo report describes working photo reads followed by settings timeouts, without identifying the changed field. The screenshot shows app-initiated closure after waiting too long; it does not establish an earlier physical disconnect, rejected write or software stability ranking. This release supplies candidate changes awaiting same-device retesting; see the [public-evidence review](valeo-connection-research.en.md).

Classic Aptus **4.701** has been independently checked as primary evidence, containing the Aptus 22 model and nine PDA command classes; see the [firmware Bluetooth survey](firmware-bluetooth-survey.en.md). It is not a dedicated Aptus 22 flash image or proof of installed user firmware. 5.041 settings are used only when the device's Sync supplies that structure.

## Configure shooting from the Back page

After connecting, open **Back** in the bottom navigation and refresh Sync. Inspect current settings and device-provided choices. Controls depend on the actual dialect, complete configuration and advertised options; an absent control is not proof that every firmware in the family lacks support. The first ISO option is not necessarily the current value. Do not borrow another model's ISO steps.

- **WiView legacy:** current ISO belongs to the complete `ShootSetup` matched by `DefaultShootSetup`. Select an existing preset or edit current fields while retaining the rest; this is not a universal independent ISO writer. The smaller DP-67 format is described below.
- **5.041 dialect:** recognized `Sync.CameraSettings` enables independent ISO, Orientation, CameraType, SensorCrop, FileCompression, Prefix and ShootingFolder writes. SensorCrop accepts only device-advertised name-only choices. Compression accepts advertised `None` / `Lossless`, separate from legacy Nearly Lossless.
- ISO choices come from the device, without hardcoded steps. Read again before writing to avoid overwriting newer back changes with a stale complete setup. Request Sync afterward and compare the complete expected configuration, untouched fields and required globals; an ISO-only change does not replace full verification.
- An ambiguous / incomplete setup, unclear allowed value, unsafe encoding or mismatching read-back must stop the operation or report it unconfirmed, not fill missing fields with invented defaults.

WiView legacy covers ISO, quality, framing, toning, Light, sharpening, input / output ICC, camera type, ICCFlag, prefix, folder, ordinary / Nearly Lossless compression with NLCompression, orientation and Color. This is not the DP-67 field list. Light uses device choices rather than arbitrary Kelvin / tint; preset selection is not FactorySettings.

Independent 5.041 Prefix edits are capped at 23 UTF-16 code units and cannot exceed the current known prefix's UTF-8 byte length. This is a conservative implementation limit, not a manufacturer hardware specification. The shooting folder must exist; changing that setting does not implicitly create it.

## Select DP-67 / WiView compatibility

While disconnected, choose **Shooting-setup compatibility** in settings:

| Option | Behavior |
| --- | --- |
| Automatic (AUTO) | Use DP-67 when the Bluetooth name contains Valeo and Sync supplies shootsetup; otherwise retain the WiView path |
| WiView / Aptus (WIVIEW) | Explicit WiView shooting-setup path; 5.041 independent settings still require the actual Sync structure |
| DP-67 / Valeo (DP67) | Explicit smaller DP-67 field set, including renamed backs not matched by AUTO; incomplete settings still refuse writing |

This changes shooting-setup serialization, not Bluetooth pairing, SPP services or hardware, and does not change CEVersion. AUTO is a reviewable software rule, not verified universal model detection. DP-67 control refresh requests Sync and CurrentTime only, without extra automatic getters; research reads can be sent explicitly in the laboratory.

The **12 core DP-67 fields** are SetupName, ShootingFolder, ISO, ImageQuality, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, CameraType and ICCFlag. Prefix is retained only if already present in the current setup; missing fields are not invented as empty values. Use device choices and preserve untouched values. Incomplete structures, changed state or unsafe encoding stop the write.

**DP-67 omits WiView FileCompression / NLCompression, Orientation and Color; the shooting-settings panel hides those write fields.** Users can still probe them explicitly in the laboratory, without assuming verified support.

Editing an original preset submits to `original-name.1`. The original DP-67 name rule is retained: an existing name containing `.1` keeps its modified slot. Confirmation shows the destination and warns that an existing same-name slot will be updated. Read-back must confirm values, new active name and unchanged original preset. Updating a modified slot is not a backup or automatic undo.

Selecting an original preset sends only DefaultShootSetup; selecting an existing `.1` modified setup sends complete DP-67 CSV. Pre-Sync, set and read-back are one queued operation, excluding automatic GetImage while notification Ack remains available. The original 12-second × 3 retry strategy is not copied: writes do not retry automatically and timeout still closes the stream. See [DP-67 analysis](dp67-analysis.en.md) for static evidence and package provenance.

## Image-processing settings

When Sync supplies `ImageSettings`, the Back page can select an existing `ActiveImageSettings` or edit the complete current processing preset. Fields include ImageQuality, Color, Framing, Toning, Light, SharpTable, InputProfile, OutputProfile, ICCFlag, Grain, MoireReduction and Saturation. Fields requiring device choices are editable only when Sync provides options.

ImageSettings and CameraSettings are separate; ISO is not an image-processing field. Change selected fields while preserving the rest, reread before writing and compare the complete read-back afterward. Missing settings are not filled with guesses.

These new writes have not been verified by the user on a real back. ISO and image-processing settings are not mechanical shutter-speed, aperture or remote-release controls.

## Check photo provenance before writing

Standard marking, notes, gray balance, deletion and copy / move require a preview from the connected back read in this connection. Reconnect, upgrade or process restart requires refreshing before standard writes; demos, older caches without provenance and other-back caches cannot be used directly. Laboratory raw requests use the entered target without all photo-provenance and semantic checks. An identical path does not prove photo identity.

## Back marking versus phone stars

Phone stars are local only. Back `Good / Unmarked` is a separate operation; changing a phone star does not prove a back-side write.

Strict Base64 / UTF-8 XMP decoding reads GetPictureParams's `xmp_tag` header. Good / Unmarked changes only the third Leaf `Flags` character while retaining other characters, namespaces and fields. Classic 4.701 and shared 5.041 independently evidence Marking without XMP. Sync shootsetup / camerasettings enables that path; existing XMP is still preserved and updated, and damaged XMP is not treated as editable. Without XMP or a recognized dialect, the standard path refuses rather than creating an empty packet. Read marking back; physical effects remain unverified. Manual laboratory probes are separate.

## Back folders and photos

The 0.3.1 scope is folder creation, empty-folder deletion, folder rename, photo deletion and internal copy / move. It does not create arbitrary files or transfer complete MOS originals to the phone.

1. Refresh folders and select the exact back path. Copy / move also needs a destination folder on the back.
2. Review source, destination and action. Existing destinations must not be silently overwritten; non-empty folders are not treated as empty.
3. Send only after explicit user confirmation. Connecting, previewing or refreshing a directory does not automatically delete anything.
4. Read the affected directories afterward. An error or mismatching read-back is not success; changing a phone-side path alone is not proof of completion on the back.

Deletion changes back storage. A laboratory recovery request is not a verified undo guarantee. These storage operations remain untested by the user on hardware.

## Back-processed previews, gray balance and exposure inspection

These controls are implemented; effects on actual firmware remain unverified. They request processing for the current photo on the back and are displayed separately from phone-side JPEG processing.

1. **Processed preview:** select the original zoom `1 / 2 / 4 / 8` and optionally request highlight, shadow or both Alarm indicators. The back returns a processed JPEG. This is separate from local pinch zoom.
2. **Original-image coordinates:** enter protocol coordinates manually as needed. The full mapping from Compose points, fit-to-screen, cropped viewports and photo orientation to the original image has not been verified. Screen taps and JPEG thumbnail coordinates are not automatically guessed into back coordinates. Do not guess when actual orientation or original dimensions are unknown.
3. **Gray balance:** inspect the sampled preview and returned Light string. Confirm separately to write the current photo or apply it to future captures; preview restore is another action. Sampling does not submit automatically. Future captures use complete legacy ShootSetup.Light / 5.041 ImageSettings.Light. Only a sample returned by this back in this connection and still tied to the current photo may bypass the static illuminant list; CSV delimiters remain rejected, other fields are preserved and Sync verifies the result. Arbitrary typed values cannot bypass validation.
4. **Pixel / exposure picking:** send `ImageFunction fstops="x;y"` for the chosen coordinates and display returned histogramlocation / histogramvalue. Here fstops carries coordinates, not aperture.
5. **Back histograms:** show the actual histogram / r / g / b bin counts rather than assuming 256. Label them “from back; RAW / JPEG source unconfirmed.” The phone's local JPEG histogram remains separate.

Gray-balance confirmation / restore Ack means the back received the request. Reread metadata and previews and check the physical effect; Ack is not a measured color change. None of these functions is continuous live view or complete MOS transfer.

## Clock, future-capture notes and session requests

The Back page also provides these actions:

| Action | Verification and boundary |
| --- | --- |
| Clock synchronization | Send phone-local time, then read CurrentTime; no protocol time-zone transport is claimed |
| Default Annotation / Copyright | Read, edit, submit and read back defaults for future captures; do not alter current or existing photos |
| FireWire / CameraBattery | Available only with powersourceselection=support; Ack does not establish the current physical power source |

EnableTakenPicture is now in the laboratory. no has not been proved to stop notifications, so the main UI does not offer an unproven pause switch. This parameter, Follow new photos and manual shutter release are recorded separately.

## Laboratory manual testing

At the top of **Back**, choose **Open laboratory** for a full-screen workbench. It exposes original-client calls, firmware candidates and user hypotheses for active testing. Unresolved scope or semantics remain experimental rather than verified standard functions. Opening the page, choosing a template or generating a request does not send automatically.

1. **Record the device context.** Note exact back model, firmware, phone system and app version. Wait for ordinary operations to finish. Experiments pause normal post-capture image reads and configuration refresh.
2. **Choose a template or raw header.** The 16 WiView templates cover Brightness, CurrentTime, ResetCounter, PIN, two CEVersion values, FactorySettings, qformat / recovery, XMP-only / annotation+XMP, EnableTakenPicture, Ack / ReSyncRequest and GetCameraParam / single-attribute SetCameraParam probes. Read evidence, effects and unknowns; supply actual inputs and generate the request. There is no default PIN, brightness range or storage root.
3. **Set matching and timeout.** Send one encoded control header up to 60 KiB, without outbound binary attachments or concatenated commands. The 1–300 second timeout is a phone setting. Select Ack, Sync or another valid reply tag, optionally exact file_name and required attachment. `*` observes the first complete frame, which may be an asynchronous notification and does not prove causality. An empty tag sends only. Those two modes cannot also match filenames / attachments.
4. **Review the target and complete request.** Review request before sending displays the back and header with known sensitive fields masked. Writes require explicit confirmation; storage erasure has an extra confirmation. Template checks validate format, not a safe hardware range or physical effect. Raw requests do not inherit all standard configuration / photo-operation checks.
5. **Inspect received, error, cancelled or send-only records separately.** Original headers, Error, nonzero Ack, timeout and unmatched frames are retained without standard success validation. Cancel disconnects to end the wait; it does not promise to undo a write already received by the back.
6. **Export before finishing.** Leaving after sending an experiment disconnects to keep late frames out of normal work. Reconnect, synchronize and reread previews before using standard functions.

### Firmware research entries and user hypotheses

| Entry | What can be tested |
| --- | --- |
| Gain query and Sync gain fields | Classic 4.701 parser-to-reply chain is evidenced, returning UserGainStorage / UserGainList; list semantics, units and model differences need testing |
| UserGain write | Firmware handler evidenced; accepted values, calibration meaning, persistence and read-back remain unresolved, with unknown values entered by the user |
| ManualLens | Handler found only in shared 5.041, not classic 4.701; no default 0 / 1 and no mechanical-shutter claim |
| GetImage FP / fp | Classic parser recognizes a special zoom; actual image area / resolution unverified, not complete MOS or continuous live view |
| FactoryGain / BootRequest / GetFile / LiveView | Create an explicit hypothesis probe generates a candidate GetParamList or generic empty header labelled **USER HYPOTHESIS**, defaulting to `*`; this is not extracted manufacturer wireless syntax |
| Internal calibration | An internal class without a known wireless tag does not generate a command; the user may still enter one header in the raw console, recording it as a hypothesis |

Brightness, ResetCounter, PIN, FactorySettings, formatting and recovery templates retain original-client or firmware evidence. Unknown ranges, scope, recovery and side effects do not become established facts because a template exists. The laboratory does not automatically enumerate candidates, fuzz or flash firmware. See the [feature gap matrix](feature-gap-matrix.en.md) and [firmware Bluetooth survey](firmware-bluetooth-survey.en.md).

### Attachments, records and redacted export

Choose a 16 / 64 MiB per-attachment phone limit while disconnected; reconnect to apply it. This is not a back specification. Received binaries are saved as `.bin`, with actual length, SHA-256 and the first 256-byte hex. Keep at most 30 experiment records with a 256 MiB attachment-cache budget.

The ZIP includes complete redacted request / response headers, non-sensitive header hex, original binary attachments, transport logs and unmatched frames. Known NewPinCode / PinCode / Pin, Annotation, Copyright and XMP_tag header fields are masked; their original header hex is omitted, without changing transmitted bytes. **Binary attachments remain original and are not automatically redacted.** Paths and unknown fields may also contain personal data; inspect records and attachments before sharing.

## Photo information, notes and files

Information shows received preview dimensions, size, SHA-256 and attributes; back metadata and decoded XMP can be requested while connected. Standard parsing uses the evidenced header rather than guessing attachments as XMP or displaying failed parsing as complete metadata. Separate full-XMP / annotation+XMP laboratory templates retain Base64 / XML checks. Supplying a complete packet does not establish that the app has verified every field's semantics or the write's effect.

Save local note writes only to the phone. Write back note uses `SetPictureParams` and needs a matching success response. Nonzero or unrecognized error codes are not success; effects still need a read-back check on real firmware. Quotes, control characters and `& < > ^` are rejected until escaping is verified.

Windows-1252 is the protocol default. GBK and UTF-8 may be selected while disconnected for testing. ASCII paths are unaffected. Unencodable text is rejected rather than replaced with question marks.

A `.mos` filename does not turn a GetImage preview into a complete original. Complete MOS download and continuous live view are not implemented. The current mechanical shutter has no remote actuator.

## Recovery and diagnostics

For an expired bond or wrong PIN, forget the old bond in Android settings and pair again. Ensure another client is not occupying the back. Try insecure RFCOMM only if required by old firmware; it cannot promise to bypass Android link requirements.

Host-side waits are approximately 35 seconds for Bluetooth, 20 for ordinary commands and 60 for images. These are implementation settings, not measured back specifications. Timeouts close the stream and require manual reconnect to avoid assigning late replies to another photo.

New timeout summaries identify the unanswered request tag and attribute names without values. Stage logs distinguish pre-Sync, write Ack and read-back. This is more precise than a generic disconnect message but does not by itself prove absent replies, parser failure or an earlier physical loss.

Automatic CameraShootSetupChangeNotification refresh now preserves the last manual operation's error / notice, so background Sync does not erase success or read-back mismatch. A new manual operation clears the previous result. This fixes overwritten app display / verification results, not an established real-back disconnection cause.

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
