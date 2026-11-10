# Capabilities and physical acceptance

[简体中文](implementation-status.md) · [Project home](../README.en.md)

Updated 2026-11-10. Version 0.2.0 pre-release; app ID `com.leaf2android`.

## Scope and evidence

The app uses Aptus / Valeo family branding. The original physical target remains a Galaxy Z Fold7 / Android 16 / One UI 8.5 with an Aptus II 5 and a technical-camera mechanical shutter. Real Bluetooth acceptance for that combination is pending. Family branding does not establish compatibility for every model, firmware, Wireless option or link.

The user authorized implementing and releasing the app under the assumption that the back's wireless hardware works. This does not confirm every field, notification sequence, original-file transfer or live-view operation. The final use remains a phone directly connected to the back, without a computer relay or bridge.

## Support table

| Feature | Code in this version | Device acceptance |
| --- | --- | --- |
| Discovery, pairing, SPP | Public API; secure and optional insecure RFCOMM | Service, PIN and Android 16 encryption requirements pending |
| Synchronization and folders | RequestSync → CurrentTime → dir; errors are not ready | Complete handshake, fields and date syntax pending |
| Automatic post-capture display | Notifications, filename matching, attachment length, serialized queue | At least 20 real photos must be matched |
| JPEG cache and playback | Original bytes, SHA-256, 64 / 256 / 1024 MiB quota | Actual resolution, speed and stability pending |
| Zoom and exposure inspection | Pinch / double-tap, JPEG histogram and indicators | Real detail range pending; no RAW pixels used |
| Photo information | GetPictureParams attributes; no guessed XMP decoding | Actual attributes, units and XMP pending |
| Stars and local notes | Persistent phone-side data, explicitly local | No back dependency |
| Back notes | SetPictureParams; matching success and no error code required | Write / read-back effects pending |
| Back marking / display parameters | Guessed marking / Good values not enabled | Values and effects unverified |
| Modern / classic PDA | Shared session, photo, zoom and display state | Real displays, rotation and One UI split-screen pending |
| Chinese / English | System default with manual selection; independent of style | Check system locale, switching / recreation and notifications against test reports |
| Foreground session | connectedDevice service, disconnect notification, permission and disconnect handling | Lock-screen, background and long sessions pending |
| Complete MOS originals | Not implemented | Compare CF originals after the operation is established |
| Continuous live view | Not implemented | No verified continuous-sensor interface |
| Remote mechanical release | Hardware unsupported in the current setup | No actuator; manual release |

This pre-release is not completion of full original-PDA replacement acceptance.

## Offline and emulator verification

Current counts, APK integrity, Lint, signing and alignment results are recorded in [verification-status.json](../research/reports/verification-status.json) and [apk-verification.json](../research/reports/apk-verification.json). Do not treat an older report, APK or count as the current release result.

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
| Operations | Folders, information, note write and read-back; button availability alone is insufficient |
| UI | Both styles; Chinese / English / System; inner / outer screens, orientation, split-screen and state |
| MOS | Once supported, size, SHA-256 and byte comparison with CF originals |
| Live view | Once supported, resolution, measured refresh rate and stability |

Record new compatibility evidence after changing Aptus / Valeo model or firmware; do not infer family-wide support from one model.
