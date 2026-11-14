# Static DP-67 comparison for Valeo setting disconnects

[中文](dp67-analysis.md)

The user reports that reading images from Valeo works, while changing settings disconnects the session. Static DP-67 analysis found differences directly relevant to setting writes: a shorter ShootSetup field set and a separate `.1` slot for modified presets. These findings support a DP-67 compatibility profile. **No physical-back test has established these differences as the cause, or demonstrated improved stability.** Reports that a friend's original client works are user reports, not tests performed by this project.

## Sources and method

On 2026-11-14, only the vendor installer in the supplied ZIP was statically examined. No vendor EXE or DLL was run or loaded. The installer is an ARM Windows CE PE with an embedded `BIN/103` CAB. The main client identifies as Leaf DP-67, file version 1, 2, 1, 2.

| Input | Bytes | SHA-256 |
| --- | ---: | --- |
| User `Leaf DP-67 app.zip`; hash only, not retained | 2,454,492 | `c166a2bda7601c7f6dcb21bcf9f163e84894dfd00611ec681d9c2caaddb98140` |
| [Original Setup.exe](../reference/dp67/original/LeafDP67-1.2.1-Setup.exe) | 1,314,304 | `c0b8de1e4700b27764aa4d755bde730736c649f8de5e72196daa160eaa980126` |
| [Embedded CAB](../reference/dp67/original/LeafDP67-1.2.1.CAB) | 1,288,384 | `41d6966acb4b2cb676a6bfef3b534ce802feb37cbeaf6ac9beaac196d29d13f4` |
| [Main client LEAFDP~2.001](../reference/dp67/extracted/LEAFDP~2.001) | 945,152 | `d0e67119db4a8e49a2cf8ec264143ccdf5da2d67623f835df9ace5fdf9c620b7` |

The ZIP also contains 4 PBF backups. Only the first 12 bytes of their magic, `POCKETPCIMAG`, were read for classification; backup contents were neither decoded nor published. The complete ZIP, backups and Mac metadata are excluded. See [provenance.json](../reference/dp67/provenance.json). All addresses below are ARM virtual addresses in this exact client, image base `0x10000`, and must not be applied to another version. Disassembly also includes file offsets. Literal pools within `.pdata` ranges may appear as data-like instructions.

## Setting-write differences

DP-67's `CControlApplication::SaveShootSetup` is at `0x19f08..0x1a4a0`. Compared with the evidenced constructors in both WiView versions:

| Detail | DP-67 1.2.1.2 | WiView 1.0.2 / 1.0.3 |
| --- | --- | --- |
| ShootSetup CSV | 12 core fields, optional 13th Prefix | 16 fields, plus NLCompression in the Nearly Lossless branch |
| Orientation, Color, FileCompression | Not emitted by this writer | Emitted by this writer |
| NLCompression | Not emitted by this writer | Conditional Nearly Lossless field |
| Global Orientation / Color outside the CSV | Directory wrapper does not append them | May append them according to setup-name classification |
| Selecting an ordinary preset | `DefaultShootSetup=original name` | DefaultShootSetup branch with that version's global-attribute rules |
| Modified-slot classification | Name **contains** `.1` | WiView's suffix rule must not replace DP-67's rule |

DP-67 constructs these keys in order. The script asserts the literal load at each construction address:

```text
SetupName,ShootingFolder,ISO,ImageQuality,Framing,Toning,Light,
SharpTable,InputProfile,OutputProfile,CameraType,ICCFlag[,Prefix]
```

Each item remains `key=value`; values should preserve a setup or allowed option returned in the back's Sync, without invented defaults. The 12 core-key loads are at `0x19fd8..0x1a29c`; Prefix is conditional at `0x1a2dc..0x1a2f0`. The `.1` check is at `0x19f70..0x19f88`. An ordinary name reaches the DefaultShootSetup branch at `0x1a3c0..0x1a3e0`. See [save-shoot-setup.arm.txt](../reference/dp67/analysis/disassembly/save-shoot-setup.arm.txt). WiView comparison comes from the [existing client evidence](../reference/analysis/wiview-control-evidence.json), not inferred firmware strings.

`CDOSDirectory::SetCameraParam` (`0x2aa34..0x2ab94`) converts exactly the supplied name/value arrays and forwards them to the protocol layer. Its call at `0x2ab08` reaches `0x279e8`; it appends no global Orientation or Color. See the [wrapper](../reference/dp67/analysis/disassembly/directory-set-camera-wrapper.arm.txt). Reusing the newer, longer WiView writer therefore does not produce an identical request.

## `.1` slots, Prefix and Framing

`SetImgProp` (`0x19cdc..0x19f08`) compares the existing and requested value. When a change is needed and the original name lacks `.1`, it calls the clone helper at **`0x19920..0x19cdc`**, from `0x19e44`, and changes the active name to `original+.1`. The helper reuses an existing modified row with that exact name or grows the array. It then copies the source's other 12 values into a 13-element row and writes the modified name. The selected property is changed in the returned slot, and the active name is updated. This preserves the factory source preset.

See [SetImgProp](../reference/dp67/analysis/disassembly/set-image-property.arm.txt) and the [correct clone helper](../reference/dp67/analysis/disassembly/clone-modified-slot.arm.txt). A direct caller at `0x2f7fc..0x2f82c` demonstrably passes property index 6, Light; see the [Light caller](../reference/dp67/analysis/disassembly/light-property-caller.arm.txt). ISO is confirmed as model column 2 in the writer, but the generic dialog's ISO call path was not fully mapped in this bounded audit. No direct ISO UI call to that same helper is asserted.

Prefix emission uses a global flag at application-object offset `+0x1bb`. The Sync parser sets it when a ShootSetup Prefix or ICCFlag key is recognized at `0x16a58..0x16b04`; no separate top-level PrefixSupport attribute was found. Because the vendor flag spans setups, preserving Prefix only when the selected setup already contained it is a conservative Android implementation choice, not an instruction-for-instruction copy of that global flag. See the [Sync parser](../reference/dp67/analysis/disassembly/sync-property-parser.arm.txt).

The Framing parser, `0x18120..0x1840c`, splits the list on semicolons and each record on commas. At `0x18264` it requires exactly 5 items: a name and 4 integers. The integers are converted/stored at `0x18298..0x18314`. ShootSetup.Framing must contain the name, not the entire coordinate record. See the [Framing parser](../reference/dp67/analysis/disassembly/framing-list-parser.arm.txt).

## Ack, timeout and synchronization

`CProtocolLayer::SetCameraParam` (`0x279e8..0x27cc8`) requires an already-synchronized state or returns failure. At `0x27afc` it initializes a limit of 3 attempts. `0x27b0c..0x27b1c` constructs a 12,000 ms transaction parameter; `0x27b28` selects expected reply **13 = Ack**; `0x27b40` invokes the transaction layer. A failed transaction while still synchronized may ReSync and retry at `0x27b50..0x27b8c`. The normal path does not unconditionally RequestSync before every write. See [SetCameraParam](../reference/dp67/analysis/disassembly/set-camera-param.arm.txt) and [transaction execution](../reference/dp67/analysis/disassembly/execute-transaction.arm.txt).

Receive routing, `0x24a44..0x25128`, matches the pending reply by command type at `0x24cec..0x24d04`, accepting Error separately. `0x24d40..0x24d48` handles PictureTaken asynchronously. The command table identifies Sync = 11, PictureTaken = 12, Ack = 13, Error = 14. No transaction-ID mechanism was established here. A photo notification or arbitrary Sync is not evidence of a successful write, and the historical retry count does not justify automatically duplicating state-changing requests. See [receive routing](../reference/dp67/analysis/disassembly/receive-command.arm.txt) and [ReSync](../reference/dp67/analysis/disassembly/resync.arm.txt).

The SetPictureParams and ImageFunction protocol wrappers are also retained, at `0x27708..0x279e8` and `0x27f9c..0x28270`, for checking similar transaction behavior. This audit did not compare every high-level parameter combination, so it does not establish that every setting operation is equivalent to WiView. Nor did it completely reconstruct all image-chunk, heartbeat, throttling or physical reconnection paths. The evidenced setting constructors are the priority compatibility experiment for the reported working reads and failing writes.

## Bluetooth service, SDK and CEVersion limits

The SPP GUID export in DP-67's `BTSDKC~1.002` contains **`00001101-0000-1000-8000-00805f9b34fb`**. Its two Widcomm modules and both WiView versions identify as 1.4.1 Build 74. After excluding the export-directory timestamp, all six PE sections match by hash: `.text/.rdata/.data/.pdata/.rsrc/.reloc`. Whole-file hashes differ, and Leaf's `0BtStack` wrapper is a different file. These results do not support attributing stability to a newer DP-67 core Bluetooth SDK, or claiming that the entire Bluetooth stack is identical.

The main client has zero ASCII or UTF-16LE matches for `CEVersion` and `EnableTakenPicture`; the observed ReSync path includes Synchronize / RequestSync. This is bounded static evidence, not proof against every indirect construction. **Leaf2Android also does not automatically send either field at startup**; they exist only as manual lab templates. This comparison therefore does not introduce a version announcement or notification toggle into startup.

## Reproduction and device validation

The [machine-readable evidence](../reference/dp67/analysis/connection-and-setting-evidence.json) includes field order, 15 function ranges, literal references, slot/Prefix/Framing behavior, transaction parameters, the command table and SDK section comparisons. The [independent inventory](../reference/dp67/inventory.json) locks file sizes/hashes. Dependencies are project `requirements-research.txt` and the Windows system `expand.exe` utility:

```powershell
.venv\Scripts\python.exe reference\dp67\analyze_dp67.py --verify
```

The command regenerates all retained vendor extractions and analysis from Setup.exe in a temporary directory and compares them byte for byte. It needs no private backups and does not modify reference. Initial acquisition uses `--zip "C:\Users\Legion\Downloads\Leaf DP-67 app.zip"`; adding `--zip` to `--verify` also checks the original source archive.

The resulting Android DP-67 setting dialect is a **candidate compatibility fix awaiting hardware validation**. Validation should record the complete write request, its correct Ack and subsequent Sync, active modified-slot name and ISO/Light readback, and whether reads still work after a failure. The vendor's 12-second parameter and 3-attempt limit do not justify automatic Android write retries. No physical-back operation, wireless capture or stability measurement was performed in this analysis.
