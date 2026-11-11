# 经典 Aptus 4.701 / Classic Aptus 4.701

本目录保存经典共享固件 `FirmwareAptus.UPD` 的独立静态证据，不混用共享 5.041 地址。镜像编译包含 `Leaf Aptus 22` 模型描述与 `CAptus22_ISO_Adjuster`；它不是已证明的 Aptus 22 专用刷机包。Aptus II 没有蓝牙，其他镜像中的共享无线代码不改变硬件限制。

This directory contains independent static evidence for classic shared `FirmwareAptus.UPD` 4.701. Compiled Aptus 22 model/ISO-adjuster code establishes model association, not a model-exclusive flashing package. Aptus II has no Bluetooth hardware; shared wireless code elsewhere does not alter that boundary.

- [中文调查报告](../../../../docs/firmware-bluetooth-survey.md) / [English survey](../../../../docs/firmware-bluetooth-survey.en.md)
- [evidence.json](evidence.json): source/payload hashes, nine PDA registrations, model association, field tables, verified CE records and both WiView clients' actual construction references.
- `*.ppc.txt`: bounded classic 4.701 disassembly, with original UPD file offsets.
- [FirmwareAptus-4.701.payload.bin](FirmwareAptus-4.701.payload.bin): exact original bytes after the 0x128-byte wrapper; derived, not a new flashable image.
- [extract_evidence.py](extract_evidence.py): reproducible static extraction; vendor code is never executed.

Source SHA-256: `a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a`.

Payload SHA-256: `52718040da6175f4a4a2c3c57df2078ad140613d3515ab26ede7ac787029dfa8`.

```powershell
Set-Location D:\Leaf2Android
.\.venv\Scripts\python.exe reference/firmware/analysis/controls-aptus-classic/extract_evidence.py
```

No Bluetooth session or physical-device effect is asserted by these files. No vendor executable, camera firmware, storage format or calibration operation was run.
