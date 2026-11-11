# Leaf 5.041 wireless control evidence

[中文](CONTROLS.zh-CN.md) · [Firmware archive](../README.en.md) · [Addresses and disassembly](controls-5.041/extraction.json)

This report applies only to `FirmWareAptusII.UPD`, 6,021,144 bytes, wrapper version 5.041, SHA-256 `80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6`. Addresses are PPC virtual addresses; `original UPD offset = VA - 0x74000 + 0x128`. Vendor code was never executed and no camera was connected. A supported static code path is not a successful hardware test, and does not establish compatibility with Valeo or every Aptus firmware version.

## Wireless command entry

`PDAInterface` message-catalog creation at `0x1026a4` constructs the following objects with interface ID 1:

| Call address | Constructor | Command | Condition |
| --- | --- | --- | --- |
| `0x102738` | `0x97c40` | GetImage | Model-dependent image branch |
| `0x102770` | `0x87adc` | GetPictureParams | Model-dependent image branch |
| `0x102818` | `0x8931c` | SetPictureParams | Model-dependent image branch |
| `0x10284c` | `0x8a1b4` | RequestSync | Outside that image branch |
| `0x10287c` | `0x99754` | SetCameraParam | Outside that image branch |
| `0x1028ac` | `0xe3a68` | GetCameraParam | Outside that image branch |

Image conditions at `0x102704..0x102720` call `0xa11b0` / `0xa11e0`; their complete model mapping remains unresolved. These calls belong to the explicitly named PDAInterface, rather than assuming that internal `PXAInterface` behavior is externally available. See [pda-message-catalog.ppc.txt](controls-5.041/pda-message-catalog.ppc.txt).

## Direct ISO setting and readback

The call chain links the setter and Sync readback to the same core state:

1. `SetCameraParam` parsing at `0x99edc..0x99f10` matches exact `ISO`, stores text at message object `+0x2f8` and sets flag value 2. It enumerates actual attributes; no additional `SetParamList` is required by this parser.
2. `0x9a2a8..0x9a2bc` calls `0x9a530`, then `0x9a548..0x9a54c` calls core setter `0x1c6284`.
3. `0x1c62c4..0x1c62f4` converts the number, calls the camera driver and stores the returned ISO state at core object `+0x2bf4`. Request text or Ack alone is not the confirmed resulting ISO.
4. `RequestSync` at `0x8a620..0x8a624` calls `0x8c23c`. `0x1cbe40..0x1cbe4c` reads the same `+0x2bf4`, passing it to `0x1c61d8` for driver validation/text conversion.
5. `0x8c330..0x8c360` places the text in the `CameraSettings` CSV; `0x8abe4..0x8abf0` emits that Sync field.

`Sync.ISO` is the semicolon-separated options list, built through `0x8bcfc` → `0x1c8000`. Current ISO comes from `Sync.CameraSettings`; the first list item must not be treated as current. Use device-provided choices.

Evidence: [set-camera-params](controls-5.041/set-camera-params.ppc.txt), [iso-storage](controls-5.041/iso-storage.ppc.txt), [sync-camera-params](controls-5.041/sync-camera-params.ppc.txt).

## CameraSettings and independent fields

`CameraSettings` serializes current `ShootingFolder=./...,ISO=...,Orientation=...,CameraType=...,Prefix=...,SensorCrop=...`, with a branch appending `FileCompression`. `DefaultCameraSettings` is separate. Preserve exact casing of keys and returned values.

| Setter key | Write chain / state | Sync readback | Bounded finding |
| --- | --- | --- | --- |
| ISO | `0x9a530` → `0x1c6284` → `+0x2bf4` | `0x1cbe40` → `0x1c61d8` | Use actual `Sync.ISO` choices |
| Prefix | `0x9a4b0` → `0x1c54fc` → `+0x25dc` | `0x1c555c`, same state | No safe length/character check established; the filename algorithm budgets 24 characters and up to 6 numeric digits, which does not prove arbitrary input safe |
| Orientation | `0x9b06c` → `0x1c63c8` → `+0x2cfc` | `0x1c6588` / `0x1c65d8` | Known labels: `Portrait CW`, `Portrait CCW`, `Landscape`, `Landscape 180`, `Auto`; choose from the current device's options |
| CameraType | `0x9a4f0` → `0x1c5ed0` → `+0x2bf8` | `0x1c5e98`, same state | `Sync.CameraType` is model-filtered by `0x1c82ec`; it includes view-camera names, which do not imply an electronic shutter actuator |
| ShootingFolder | `0x9a570` → `0x1c6310` | `0x1c6398` | Removes leading `./`, checks whether the folder is full, then sets it; arbitrary directory/path creation is not established |
| SensorCrop | `0x9bbdc` → `0x1ce5d4` | `0x1ce4d4` | Matches available crop names; an unmatched value may still return zero, so verify actual Sync state |
| FileCompression | `0x9a654` → `0x9b36c` → `0xfccb4` | `0x8ce28`, appended to CSV | The reachable matching loop recognizes `None` / `Lossless`; retained nearly-lossless code is not proven reachable, so its quality labels are not enabled by this finding |

`CameraParamChange` builder `0x9b55c` contains ISO, ImageSettings, ActiveImageSettings, Prefix and other notification keys. Neither notifications nor Ack substitute for an explicit Sync readback. Wireless notification routing/order was not tested. Additional evidence: [camera-settings-storage.ppc.txt](controls-5.041/camera-settings-storage.ppc.txt).

## ImageSettings dialect

The 5.041 Sync fields are:

- `ImageSettings`: semicolon-separated complete setups, each serialized as comma-separated `key=value` fields: `SetupName`, `ImageQuality`, `Color`, `Framing`, `Toning`, `Light`, `SharpTable`, `InputProfile`, `OutputProfile`, `ICCFlag`, `Grain`, `MoireReduction`, `Saturation`; see `0x8be64..0x8c23c`.
- `ActiveImageSettings`: current SetupName; `0x8bb90` → `0x1c558c` reads core `+0x2e0c`.
- `FactoryDefaultImageSettings`: a separate factory setup-name source, not the current setup.

An independent `ActiveImageSettings` setter uses `0x9adf4` → `0x1cbcd8` to resolve an existing named setup and applies the complete structure through `0x1c55bc`. `ImageSettings` uses `0x9aaec`, splits a complete CSV, requires a first token containing `SetupName`, builds the structure and also calls `0x1c55bc`. That core function stores the name at the same `+0x2e0c` and applies the processing fields. Complete setup writes can change several parameters; preserve unknown data and verify with Sync.

The old WiView 1.0.3 `ShootSetup` / `DefaultShootSetup` dialect must not simply be renamed. A retained `_SetShootSetup` debug message in this firmware does not prove a same-named wireless attribute. No successful device capture has been synthesized.

## GetCameraParam limitation

The request attribute is exactly **`GetParamList`**, at `0xe3c38..0xe3c48`, split on semicolons. Its parser at `0xe4728` recognizes ISO and DefaultShootSetup among other names, but the inspected response builder at `0xe3d54..0xe46c0` does not emit either field. Name recognition by a parser does not establish a usable reader. ISO readback therefore uses the evidenced RequestSync.CameraSettings path.

The response builder does contain branches for `Histogram_scale`, `Color`, `Toning`, `Light`, `SharpTable`, `battery_percent`, `battery_type`, `battery_time`, `exp_left`, `exp_max`, `CameraOrientation`, `CurrentTime`, `TotalFrames`, `Copyright` and `Annotation`. Model state and actual response samples remain to be tested. See [get-camera-params.ppc.txt](controls-5.041/get-camera-params.ppc.txt).

## Marking is independent of XMP

`SetPictureParams` attribute parsing at `0x8947c..0x895a4` requires `file_name`, then tries `annotation`, else `Marking`, else `imageUpdate`. Annotation and marking therefore require separate requests. `XMP_tag` is checked independently afterwards; its absence does not clear an already accepted Marking flag.

Downstream `0x896a0..0x896b4` invokes the XMP writer only if its flag is set. Independently, `0x896b8..0x896d0` calls marking setter `0x1c7db0`. At `0x1c7e00..0x1c7e30`, exact `Good` sets flag bit 0 and other nonempty text clears it. Both old-client `Unmarked` and firmware spelling `UnMarked` therefore clear it. Applications should send known states only, not offer arbitrary marking text.

`GetPictureParams` at `0x87fa0..0x87fac` calls `0x1c7e74`, which reads that same bit and returns `Good` / `UnMarked`. `0x88400..0x88420` emits it as field-table index 16, `Marking`. This provides a separate readback chain for confirming a write.

The field table also names file_name, Orientation, Color, frame, Toning, Light, Histogram, HistogramR/G/B, text, Hwhite/Hdark, HwhiteLoc/HdarkLoc, NewPicture, Average, zoom, alarm_dark/alarm_white, XMP_tag, Date, ZoomEnable and Dimension. Actual field output depends on image type, processing state and option branches; a field's presence in the table does not guarantee data for every image.

Evidence: [set-picture-params](controls-5.041/set-picture-params.ppc.txt), [marking-storage](controls-5.041/marking-storage.ppc.txt), [get-picture-params](controls-5.041/get-picture-params.ppc.txt).

## Remaining advanced-setting gaps

| Item | Static behavior identified | Unresolved |
| --- | --- | --- |
| NewPinCode | `0x9b0dc` requires a different current PIN and `strlen <= 16`; `0x3647cc` also checks at most 16 bytes and writes the 16-byte PIN area, then clears old link keys | Allowed characters, minimum length, Android compatibility and recovery are not device-tested; arbitrary Unicode is not established |
| ResetCounter | `0x9a210` calls `0xfcd70`, then `0x9bd8c` clears core `+0x2fb4/+0x2fb8`; nearby filename code also uses that state | It must not be described as resetting lifetime TotalFrames; exact numbering and persistence need samples |
| Brightness | `0x9b2f8` converts text to integer and passes it to `0x95ee8` / `0x95f18`, issuing low-level command 0x0b | Safe range, camera-screen meaning and usable readback are unproven; no slider should be exposed solely from its name |
| UUID / SDP / pairing | Supplied analysis locates GAP/SPP setup and AptusComm / AptusSComm names | End-to-end SDP/UUID/authentication and Android public-interface access still require verification |
| MOS / live view | Original firmware and useful strings are available for continued research | Complete original-file reading and continuous sensor frames are not proven; post-capture refresh is not live view |

Additional disassembly: [pin-storage](controls-5.041/pin-storage.ppc.txt), [brightness-driver](controls-5.041/brightness-driver.ppc.txt).

## Reproduction

Run `.\.venv\Scripts\python.exe reference\firmware\tools\extract_control_evidence.py` from the project root. It first verifies the pinned whole-image SHA-256 and version, then emits bounded disassembly, 71 Sync/Picture field entries plus compression/orientation labels, and output hashes. The generated evidence contains no fabricated successful device responses.
