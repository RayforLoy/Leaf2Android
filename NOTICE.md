# Project and reference materials / 项目与参考资料

Leaf2Android source code, documentation, test fixtures and generated demo images are maintained separately from the original Leaf manufacturer materials in `reference/`.

Original WiView CABs, extracted Windows CE programs/libraries, installer data and user guides retain their original filenames and bytes. Their provenance, classifications and SHA-256 values are recorded in `reference/sources.lock.json`, `reference/inventory.json` and the bilingual reference README files. They are not Android application dependencies and are not embedded in the APK.

Camera-back firmware supplied from `D:\leaf` is preserved separately in `reference/firmware`, with original byte hashes, deduplicated source aliases and static analysis. Original device-specific lens calibration, personal MOS photos and unrelated installers are excluded. See `reference/firmware/README.en.md`. Firmware presence does not establish compatibility with every Aptus / Valeo model, and no firmware was flashed during this work.

No new source-code license is selected by this release. Manufacturer materials are not relabelled as project-authored code, and this notice does not assign rights to those materials.

Leaf2Android 自行维护的源码、文档、测试夹具和程序生成的演示图，与 `reference/` 中的原厂资料分开管理。原版 CAB、提取的 Windows CE 程序和库、安装数据及指南保留原文件名和字节内容，来源与校验值见参考资料清单。它们不作为安卓依赖，也不包含在 APK 中。

用户从 `D:\leaf` 提供的真实后背固件独立保存在 `reference/firmware`，附原始字节校验值、去重来源映射和静态分析；未收录设备专属镜头校准、个人 MOS 照片和无关安装器。本次未向后背刷写固件，也不宣称全部 Aptus / Valeo 已兼容。本次发布没有替用户选择新的源码许可证，也没有将原厂资料标为项目原创。
