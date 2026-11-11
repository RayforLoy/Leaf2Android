# Leaf 5.041 无线控制静态证据

[English](CONTROLS.en.md) · [固件目录](../README.md) · [原始地址与反汇编](controls-5.041/extraction.json)

本报告只适用于 SHA-256 为 `80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6` 的 `FirmWareAptusII.UPD`，文件 6,021,144 字节，封装版本 5.041。所有地址均为 PPC 虚拟地址；原 UPD 文件偏移等于 `VA - 0x74000 + 0x128`。分析读取本地原字节，没有运行厂商程序或连接后背。以下“支持”指存在可达静态代码，不代表已经通过无线实机测试，也不能推广到 Valeo 或所有 Aptus 固件。

## 无线命令入口

`PDAInterface` 的消息目录创建函数 `0x1026a4` 分配并构造以下对象，接口 ID 为 1：

| 构造位置 | 构造函数 | 命令 | 条件 |
| --- | --- | --- | --- |
| `0x102738` | `0x97c40` | GetImage | 机型分支 |
| `0x102770` | `0x87adc` | GetPictureParams | 机型分支 |
| `0x102818` | `0x8931c` | SetPictureParams | 机型分支 |
| `0x10284c` | `0x8a1b4` | RequestSync | 无上述图像分支限制 |
| `0x10287c` | `0x99754` | SetCameraParam | 无上述图像分支限制 |
| `0x1028ac` | `0xe3a68` | GetCameraParam | 无上述图像分支限制 |

图像分支条件位于 `0x102704..0x102720`，由 `0xa11b0` / `0xa11e0` 判断，尚未完整映射到具体型号。不能把板内 `PXAInterface` 的行为直接视为 PDA 接口行为；这里的构造调用来自明确标名的 PDAInterface。证据：[pda-message-catalog.ppc.txt](controls-5.041/pda-message-catalog.ppc.txt)。

## 独立 ISO 写入与读回

链路如下，读写最终关联同一核心状态：

1. SetCameraParam 属性解析 `0x99edc..0x99f10` 精确识别 `ISO`，将文本放消息对象 `+0x2f8`，设置标志 bit 1（值 2）。此解析器枚举实际属性，无额外 `SetParamList` 要求。
2. `0x9a2a8..0x9a2bc` 调用 `0x9a530`，再由 `0x9a548..0x9a54c` 调用核心 setter `0x1c6284`。
3. `0x1c62c4..0x1c62f4` 转换数值，调用驱动虚函数，将驱动返回的 ISO 状态存入核心对象 `+0x2bf4`；应用不能把请求原文或 Ack 单独当作最终 ISO。
4. RequestSync 处理 `0x8a620..0x8a624` 调用 `0x8c23c`。`0x1cbe40..0x1cbe4c` 读同一 `+0x2bf4`，交由 `0x1c61d8` 校验 / 转换为 ISO 文本。
5. `0x8c330..0x8c360` 将其放入 `CameraSettings` CSV，`0x8abe4..0x8abf0` 输出该 Sync 字段。

`Sync.ISO` 是可选 ISO 值列表，源自 `0x8bcfc` → `0x1c8000`，由分号分隔。当前 ISO 从 `Sync.CameraSettings` 中读取；不要把列表中的第一项当成当前值。使用设备返回选项，禁止把某一型号的 ISO 列表写死。

证据：[set-camera-params](controls-5.041/set-camera-params.ppc.txt)、[iso-storage](controls-5.041/iso-storage.ppc.txt)、[sync-camera-params](controls-5.041/sync-camera-params.ppc.txt)。

## CameraSettings 与独立字段

`CameraSettings` 包含当前设置的 `ShootingFolder=./...,ISO=...,Orientation=...,CameraType=...,Prefix=...,SensorCrop=...`，并按分支追加 `FileCompression`。`DefaultCameraSettings` 是独立字段。设置键、CSV 键与返回选项应保留原始大小写。

| 独立设置键 | 写入链 / 状态 | Sync 回读来源 | 有界结论 |
| --- | --- | --- | --- |
| ISO | `0x9a530` → `0x1c6284` → `+0x2bf4` | `0x1cbe40` → `0x1c61d8` | 使用 `Sync.ISO` 的实际候选值 |
| Prefix | `0x9a4b0` → `0x1c54fc` → `+0x25dc` | `0x1c555c` 读同址 | 本段没有安全长度或字符验证；文件命名算法按 24 字符预算留最多 6 位数字，不能据此假定任意长输入安全 |
| Orientation | `0x9b06c` → `0x1c63c8` → `+0x2cfc` | `0x1c6588` / `0x1c65d8` | 固定名包括 `Portrait CW`、`Portrait CCW`、`Landscape`、`Landscape 180`、`Auto`；只用本设备返回选项，避免未知值回退 |
| CameraType | `0x9a4f0` → `0x1c5ed0` → `+0x2bf8` | `0x1c5e98` 读同址 | `Sync.CameraType` 由 `0x1c82ec` 按机型过滤，包含技术相机相关名称；必须用返回原文，不能把列表等同快门执行能力 |
| ShootingFolder | `0x9a570` → `0x1c6310` | `0x1c6398` | 剥离开头 `./`，检查目录是否满，再设置；本报告不证明可任意创建目录或路径 |
| SensorCrop | `0x9bbdc` → `0x1ce5d4` | `0x1ce4d4` | 与可用裁切名逐项比较；未匹配时也可能返回 0，必须检查 Sync 实际值 |
| FileCompression | `0x9a654` → `0x9b36c` → `0xfccb4` | `0x8ce28`，然后追加到 CSV | 当前匹配循环可达标签 `None` / `Lossless`；保留的近无损分支没有得到可达证据，不开放其三个质量标签 |

`CameraParamChange` 生成函数 `0x9b55c` 存在 ISO、ImageSettings、ActiveImageSettings、Prefix 等通知键。通知和 Ack 都不替代主动 Sync 回读；本次未在真实无线连接上验证其分发次序。附加证据：[camera-settings-storage.ppc.txt](controls-5.041/camera-settings-storage.ppc.txt)。

## ImageSettings 方言

5.041 Sync 使用下列字段：

- `ImageSettings`：以分号分隔的整套配置；每条由 `SetupName`、`ImageQuality`、`Color`、`Framing`、`Toning`、`Light`、`SharpTable`、`InputProfile`、`OutputProfile`、`ICCFlag`、`Grain`、`MoireReduction`、`Saturation` 的 `key=value` 逗号列表序列化，见 `0x8be64..0x8c23c`。
- `ActiveImageSettings`：当前配置的 SetupName，`0x8bb90` → `0x1c558c` 从核心 `+0x2e0c` 读回。
- `FactoryDefaultImageSettings`：单独的工厂配置名来源；不要拿它覆盖当前配置。

独立设置 `ActiveImageSettings` 时，`0x9adf4` 用名字经 `0x1cbcd8` 查找现有整配置，然后交 `0x1c55bc` 应用。设置 `ImageSettings` 时，`0x9aaec` 按逗号拆配置，要求首 token 含 `SetupName`，填入配置结构后也调用 `0x1c55bc`。该核心函数将名字存入相同 `+0x2e0c` 并逐项应用处理参数。完整配置写入可能改变多项处理参数，应保留未知字段并通过 Sync 验证。

旧 WiView 1.0.3 客户端的 `ShootSetup` / `DefaultShootSetup` 不能直接更名为这些字段。5.041 有旧日志 `_SetShootSetup`，不构成同名无线属性存在的证据。本报告保留方言差异，未制造设备抓包。

## GetCameraParam 的限制

该版本读取请求属性名是 **`GetParamList`**，见 `0xe3c38..0xe3c48`，按分号拆为参数列表。`0xe4728` 的参数解析器接受 ISO、DefaultShootSetup 等名称，但 `0xe3d54..0xe46c0` 的当前响应生成代码没有生成 ISO / DefaultShootSetup 字段。因此“解析器识别名称”不足以证明可用读取接口。已证明的 ISO 回读采用 RequestSync.CameraSettings。

该响应构造中明确存在 `Histogram_scale`、`Color`、`Toning`、`Light`、`SharpTable`、`battery_percent`、`battery_type`、`battery_time`、`exp_left`、`exp_max`、`CameraOrientation`、`CurrentTime`、`TotalFrames`、`Copyright`、`Annotation` 等分支；具体机型状态与返回格式仍需实机样本。证据：[get-camera-params.ppc.txt](controls-5.041/get-camera-params.ppc.txt)。

## 照片标记可独立于 XMP 写入

`SetPictureParams` 属性解析 `0x8947c..0x895a4` 要求 `file_name`，再尝试 `annotation`，否则 `Marking`，否则 `imageUpdate`。因此备注和标记需分别请求。`XMP_tag` 随后单独检查：缺失它不会清除已接受的 Marking 标志。

下游 `0x896a0..0x896b4` 只在 XMP 标志存在时调用 XMP 写入；`0x896b8..0x896d0` 独立调用标记 setter `0x1c7db0`。在 `0x1c7e00..0x1c7e30`，标记文本等于 `Good` 时设置 flag bit0，其他非空文本清位。因此 `Unmarked`（旧客户端）和 `UnMarked` 均清位，应用只应发送已知状态，不能开放任意标记文本。

`GetPictureParams` 在 `0x87fa0..0x87fac` 调用 `0x1c7e74`，按同一个 bit 返回 `Good` / `UnMarked`，并在 `0x88400..0x88420` 通过字段表 index 16 输出为 `Marking`。它是可用于写后验证的独立读取链。

其字段表还包含 file_name、Orientation、Color、frame、Toning、Light、Histogram、HistogramR/G/B、text、Hwhite/Hdark、HwhiteLoc/HdarkLoc、NewPicture、Average、zoom、alarm_dark/alarm_white、XMP_tag、Date、ZoomEnable、Dimension。字段是否输出受图片类型、处理状态和选项分支影响，不应只凭表中有名称就承诺每项总有数据。

证据：[set-picture-params](controls-5.041/set-picture-params.ppc.txt)、[marking-storage](controls-5.041/marking-storage.ppc.txt)、[get-picture-params](controls-5.041/get-picture-params.ppc.txt)。

## 高级设置仍有证据缺口

| 项目 | 已识别静态行为 | 未解决项 |
| --- | --- | --- |
| NewPinCode | `0x9b0dc` 检查不同于当前 PIN 且 `strlen <= 16`；底层 `0x3647cc` 同样检查最多 16 字节并写入 16 字节存储；还清除旧 link-key 区 | 允许字符、最小长度、Android 配对兼容性和失效后恢复未实测；没有证据证明应接受任意 Unicode |
| ResetCounter | `0x9a210` 调 `0xfcd70`，随后 `0x9bd8c` 清核心 `+0x2fb4/+0x2fb8`；相邻命名算法也引用这些状态 | 尚不能据此称为重置寿命 TotalFrames；具体文件编号与持久化效果需样本 |
| Brightness | `0x9b2f8` 将文本转整数，交 `0x95ee8` / `0x95f18` 发底层命令 0x0b | 未证明安全量程、后背屏幕含义或可用回读；不能凭名称开滑块 |
| UUID / SDP / 配对 | 原有分析定位 Bluetooth GAP/SPP 初始化与 AptusComm / AptusSComm 名称 | 本次未完成 SDP/UUID/认证的端到端推导；实际广播与 Android 公共接口可达性需硬件验证 |
| MOS / 实时取景 | 保留固件镜像和字符串可继续查验 | 本次没有证明完整文件读取或连续传感器画面协议，不能把拍后刷新标为实时取景 |

PIN / 亮度补充反汇编分别见 [pin-storage](controls-5.041/pin-storage.ppc.txt) 与 [brightness-driver](controls-5.041/brightness-driver.ppc.txt)。

## 重现

从项目根目录执行 `.\.venv\Scripts\python.exe reference\firmware\tools\extract_control_evidence.py`。脚本先校验整个 UPD 的固定 SHA-256 与版本，再生成有界反汇编、71 个 Sync/Picture 字段（另含压缩和方向标签）及每个输出哈希。输出仅是静态证据，不含合成的成功设备响应。
