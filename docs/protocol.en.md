# Protocol evidence and offline parsing

[简体中文](protocol.md) · [Project home](../README.en.md) · [Reproduction](research.en.md)

Status: static code provides concrete evidence, but there is no verified capture from the target back. The complete real session protocol is not confirmed. UI localization in 0.2.0 does not expand protocol support.

## Evidence locations

Addresses below are preferred virtual addresses in the sample PE, not file offsets or Android addresses. The original main-program member is `00LeafCE.001`.

| Behavior | WiView 1.0.2 | WiView 1.0.3 |
| --- | --- | --- |
| Control reader | `0x2a1a4` | `0x2a988` |
| Caret `^` comparison | `0x2a208` | `0x2a9ec` |
| Terminator `>` comparison | `0x2a224` | `0x2aa08` |
| Binary image reader | `0x31274` | `0x320cc` |
| Asynchronous GetImage response | `0x315cc` | `0x32424` |

The serial-service GUID in each `BtSdkCE30.dll` is at RVA `0x1e09c`. In 1.0.3 it is loaded at `0x111120` in the `CSppClient::CreateConnection` path; Leaf's wrapper loads the corresponding import at `0x101f70`. The GUID is `00001101-0000-1000-8000-00805f9b34fb`, Bluetooth Classic SPP, not BLE.

[protocol-evidence.json](../reference/analysis/protocol-evidence.json) pins input hashes, function ranges, delimiter instructions, GUID and string offsets. `tools/build_evidence.py` rechecks the expected hashes, ARM instructions and GUID and fails on mismatches.

```powershell
.\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --va 0x2a988 --bytes 0x240
```

## Confirmed basic framing

The original reader finds `^`, then reads through the first following `>`. A new caret during control parsing restarts synchronization. This is not evidence for full general-purpose XML behavior.

Both versions contain complete literals such as:

```text
^<RequestSync />
^<ReSyncRequest />
^<Ack />
^<GetCameraParam GetParamList="battery_percent" />
```

`^<GetImage file_name="` is a dynamically completed command prefix. Response processing checks `Ack`, `file_name` and `attachment`, matches the filename and reads exactly the declared number of binary bytes. This example is **synthetic**:

```text
^<Ack file_name="Shots/test.mos" attachment="5" />[immediately followed by 5 raw bytes]
```

Carets, `>`, NUL and non-ASCII bytes inside the attachment are payload, not control delimiters. Control scanning resumes only after the attachment is complete.

The original code also checks `size`, but the analyzed image path logs that packetization is disabled. The implementation supports the non-packetized path; it does not invent packet acknowledgements, checksums or retransmission behavior.

Control text is converted through the PDA system code page before sending. UTF-16 literals in the EXE do not establish UTF-16 on the wire. The current app sends the selected Windows-1252 / GBK / UTF-8 encoding and preserves received attachment bytes. Non-ASCII behavior needs real evidence.

## Static startup sequence

In 1.0.3, camera initialization at `0x25cc4` constructs mode 0 RequestSync, then mode 1 `GetCameraParam GetParamList="CurrentTime"`. The successful synchronization branch calls `InitDiskContent` at `0x26118`; its non-tethered branch constructs the mode 2 root-directory request.

The constructor at `0x2ed40` contains literal / construction evidence for all three. This establishes a local original-client sequence, not the complete link startup, preceding commands, target-firmware fields or recovery behavior.

A CEVersion prefix is present, but its target-session call conditions and value are unverified. Internal log versions `1.15` / `1.17` and distribution labels `1.0.2` / `1.0.3` are kept separate and are not guessed into a handshake.

## Current Android session

`protocol/` implements these rules in pure Kotlin. `LeafProtocol` uses an injectable `LeafTransport`; the Android implementation is a public-API SPP socket. Wireless availability is a development assumption authorized by the user, not a hardware test result.

- Startup waits for Sync after RequestSync, then requests CurrentTime and the root directory. No CEVersion is guessed.
- Folder parsing follows 1.0.3 `CFolderCommand::ProcessResponse` at `0x30c70`: split `dir` by semicolons, D for directory and F for file, skip the first two characters, then split name / date fields by commas. The second character's meaning is not established. Colons in fixtures are a synthetic choice, not a declared standard.
- Only one command awaits a response at a time. GetImage needs matching `file_name` and `attachment`; wrong-name replies are not assigned to a photo. A timeout closes the stream so a late reply cannot be mistaken for the next request.
- Ack alone is not success: a nonzero or uninterpretable `error_code` reports failure rather than confirming a note write.
- `PictureTaken` fields `file_name`, `busy` and `imageSaved` have static anchors starting at `0x28718`. Busy or explicitly unsaved images are not requested yet. Complete asynchronous-Ack timing remains a device-test item.
- GetPictureParams and SetPictureParams are connected. Unknown XMP attachments are not guessed into metadata, and an acknowledgement is not a completed firmware acceptance test.
- Control values reject quotes, control characters and `& < > ^` until escaping is verified. Unencodable text is rejected rather than silently damaged.
- The Android session limits an attachment to 16 MiB; the research parser defaults to 64 MiB. Both use a 64 KiB host control-header limit. These are host choices, not measured back specifications.

Unknown MOS full-file operations, continuous live view, marking / Good write values and destructive file commands remain disabled. See [capabilities](implementation-status.en.md).

## Offline parser boundary

`leafresearch/framing.py` and `tools/replay_stream.py` accept local raw or hexadecimal streams. They do not open Bluetooth or send back commands.

- Only a bounded attribute-only control subset is parsed. Attribute bytes are preserved without guessing entity conversion.
- Attachment lengths are decimal byte counts; binary payload bypasses delimiter scanning.
- Disconnect or timeout requires explicit parser reset; EOF detects incomplete controls or attachments.
- Bad lengths, duplicates and host-limit overflows fail rather than inferring a camera response.
- Fixtures in [research/fixtures](../research/fixtures) are synthetic. The small arbitrary attachments are not photographs for image-quality evaluation.

## Still unverified

| Item | Status |
| --- | --- |
| Target Aptus II 5 SDP advertisement | No physical inspection |
| PIN, authentication, Android 16 encryption | Not verified |
| Complete startup, CEVersion negotiation | Partial static sequence only |
| Non-ASCII paths, notes, XMP encoding | Not verified |
| Actual JPEG replies / resolution | No target-model capture |
| Notification timing, concurrency, recovery | Static handlers exist; real sessions needed |
| Complete MOS read | No verified operation |
| Continuous sensor live view | No verified operation |

Artificial replay cannot replace any of these physical checks. Aptus / Valeo branding does not change this evidence boundary.
