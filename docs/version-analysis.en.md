# Static comparison: WiView 1.0.2 and 1.0.3

[简体中文](version-analysis.md) · [Project home](../README.en.md) · [Reference material](../reference/README.en.md)

Original analysis: 2026-10-18. Reference locations were reorganized on 2026-11-10. Both inputs are actual CAB files. File facts, code evidence and unverified hardware behavior are kept distinct.

## Inputs and integrity

| Item | WiView 1.0.2 | WiView 1.0.3 |
| --- | --- | --- |
| Source | Community Leaf Capture 11 disc-file backup on Internet Archive | User-provided |
| Location | `reference/wiview/1.0.2/LeafWiView-1.0.2.CAB` | `reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB` |
| Bytes | 2,683,140 | 2,729,244 |
| SHA-256 | `5f38aab055b2cb46560e59aa87d019a76a0e7e4a2d6866fbbc58b8e5eac56f85` | `0f0a34891b3b4f7c70c6681092da38cd5d0bb9cadc82ff7b6dcb793c399b4cce` |

Version 1.0.2 came from the [Leaf Capture 11 backup](https://archive.org/details/leaf-capture-11). Size, SHA-1 and MD5 matched the archive metadata. The community uploader's provenance claim and version clues are recorded; independent manufacturer-signature authentication was not completed. Version 1.0.3 is the user's submitted file. Hashes identify reproducible analysis inputs.

The vendor programs were not executed. Work consisted of CAB extraction, PE inspection, string extraction and ARM disassembly.

## Components

Each CAB has seven members: six ARM Windows CE executables and one MSCE installation descriptor. These are PDA client components, not camera-back firmware.

| Member / identified component | 1.0.2 bytes | 1.0.3 bytes | Comparison |
| --- | ---: | ---: | --- |
| `00LeafCE.001` / main program | 2,319,872 | 2,357,760 | Code, data and resources changed |
| `LEAFWI~2.002` / `LeafWiBTStack.dll` | 15,360 | 15,360 | Identical `.text`, `.data`, `.pdata`, `.reloc`; `.rdata` differs |
| `LEAFWI~1.003` / `LeafWiLauncher.dll` | 10,240 | 18,432 | Identical code; resources grow from 1,536 to 9,728 bytes |
| `BtCoreIf.005` / `BtCoreIf.dll` | 123,392 | 123,392 | Entire file identical |
| `BTSDKC~1.004` / `BtSdkCE30.dll` | 188,928 | 188,928 | Entire file identical |
| `CabSetup.999` / `CabSetup.dll` | 23,552 | 23,552 | Identical code, data and resources; `.rdata` differs |

Both WIDCOMM libraries report version `1.4.1 Build 74`. Main-program build-date strings include 2007-10-02 and 2008-03-13; these are clues within the samples.

Distribution labels differ from internal log versions. The format `Leaf WiView version %d.%d%d` receives `(1, 1, 5)` and `(1, 1, 7)`, reconstructing `1.15` and `1.17`. These are not the filename labels `1.0.2` / `1.0.3`. The About screen was not executed, and the log versions were not guessed into a handshake.

The Bluetooth wrapper code is byte-identical, supporting reuse of one transport implementation. Other file sections differ, so the complete releases are not identical.

Closer inspection shows the wrapper, launcher and CabSetup `.rdata` differences occur only in export-table timestamps. Zeroing each four-byte timestamp makes those sections identical. The comparison script records this normalization.

## Communication evidence

- `LeafWiBTStack.dll` imports and calls WIDCOMM `CSppClient::CreateConnection`.
- `BtSdkCE30.dll` exports the serial-service GUID `00001101-0000-1000-8000-00805f9b34fb`, which is used by the connection path.
- Both control readers look for `0x5E` (`^`) and terminate the header at `0x3E` (`>`).
- GetImage response handling reads `attachment`, then invokes a binary reader for exactly that byte count. Attachment delimiters must not be scanned as controls.
- Both clients contain synchronization, folder, image, image-parameter, camera-parameter and asynchronous photo-notification paths.

The original client converts control text using the PDA system code page. Received bytes should be preserved, and non-ASCII paths / notes need device verification.

## Specific 1.0.3 changes

1. Adds `^<Nak />`. Its constructor chooses Ack / Nak using internal state and resets that state in one branch. Calls are present, but back responses and applicable timing remain unverified.
2. Adds `^<SetCameraParam PowerSource="`, alongside FireWire, CameraBattery, powersourceselection, support and notsupport. Use the fields actually advertised by a wireless back; Aptus II is not a Bluetooth target.
3. Adds AsyncCommand, polling_timeout, IfGUIIsActive and ReadyToShoot handling clues. The AsyncCommand handler reads and converts polling_timeout.
4. Adds firmware-update prompts and UI-buffer strings, and changes main / launcher resources. The manufacturer's [1.0.3 guide](https://web.archive.org/web/20081230142347id_/http://www.leaf-photography.com/imgs/uploads/user%20guides/leafwiview_103_installation.pdf) introduces Windows Mobile 6 and VGA support on printed page 1; PDF text and rendering were checked during the original analysis.

The comparison finds 32 shared command prefixes and two added prefixes. Prefixes are string anchors, some needing dynamic arguments. These counts are neither a complete command count nor the number of supported back features. See [version-comparison.json](../reference/analysis/version-comparison.json).

## Consequences for the port

Use 1.0.3 as the main baseline and 1.0.2 for comparison. Rebuild Android transport and protocol behavior; original WIDCOMM / Windows CE libraries remain research inputs.

A GetImage filename may refer to `.mos`, which does not prove a complete MOS response. JPEG-processing code is present. Original-file reading and continuous sensor view need separate evidence; RawFrameBuffer text was not accepted as proof of raw transfer.

This static analysis did not change back firmware, pairing settings or photos. On 2026-11-11 the user reported successful connection/photo reading on Valeo 22Wi and Aptus 22 and clarified that Aptus II has no Bluetooth. New control effects still require hardware validation. See the independent classic 4.701 [firmware survey](firmware-bluetooth-survey.en.md).
