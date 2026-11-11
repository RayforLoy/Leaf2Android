# 固件蓝牙接口调查

[English](firmware-bluetooth-survey.en.md) · [实验模板清单](../reference/analysis/lab-candidates.json)

本项目同时分析原版 WiView 客户端和后背固件。协议实现不只来自客户端字符串：已静态追踪经典 Aptus 4.701 和共享 5.041 的 PowerPC 程序、PDAInterface 消息目录、属性解析、状态写入与回复构造；配套经典 Aptus Windows CE BIN 也已检查容器和记录校验。

必须纠正硬件边界：**Aptus II 没有蓝牙硬件。** 名为 `FirmWareAptusII.UPD` 的 5.041 镜像里包含共享 PDAInterface 代码，不能据此推导 Aptus II 能无线连接。下面重新使用经典 Aptus 镜像复核 Aptus 22 相关路径。所有“可达”均指静态注册 / 调用链，不能替代相应后背的实际测试。

## 经典 Aptus 资料与机型关联

| 项目 | 确切资料 |
| --- | --- |
| 主镜像 | [FirmwareAptus.UPD](../reference/firmware/originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD)，4.701，4,235,648 字节 |
| SHA-256 | `a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a` |
| 实际提取的 PPC 载荷 | [FirmwareAptus-4.701.payload.bin](../reference/firmware/analysis/controls-aptus-classic/FirmwareAptus-4.701.payload.bin)，4,235,352 字节；SHA-256 `52718040da6175f4a4a2c3c57df2078ad140613d3515ab26ede7ac787029dfa8` |
| 配套 CE 容器 | `FirmWareAptus.BIN`，接口版本 1.8.6，7,730,663 字节；SHA-256 `4a0d32186d95bd0aaf0a741d161b98712e30beac595d7d5d10dabffaa86397c0`；27 个载入记录全部校验通过 |
| 来源 | 四套既有 Capture One / Leaf Capture 来源，共 8 个 Aptus / AptusS 同字节 UPD 别名，见 [来源映射](../reference/firmware/provenance/source-aliases.json) |

`Firmware.config` 只有 `EnforceUploading=0`，没有机型映射。机型关联来自镜像中的实际模型描述：虚表 `0x3f59b8/0x3f59bc` 分别指向返回常量 `0x0c` 的函数 `0x2108c4` 和返回 `Leaf Aptus 22` 名称的函数 `0x2108cc`；名称指针为 `0x47dbdc` → `0x3f5b7c`。镜像还编入 `21CAptus22_ISO_Adjuster` 类型名，地址 `0x4121ac`。因此可称“含 Aptus 22 模型与 ISO 调整类的经典共享固件”，不能称它是 Aptus 22 专用镜像或据此建议刷写。4.700 Legacy 原件另行保留，不混用本报告的 4.701 地址。

[独立 evidence.json](../reference/firmware/analysis/controls-aptus-classic/evidence.json) 保存源哈希、载荷哈希、CE 记录、机型关联、完整命令目录、字段表及两版客户端构造引用。地址映射为 `原 UPD 偏移 = VA - 0x74000 + 0x128`。可从项目根执行：

```powershell
.\.venv\Scripts\python.exe reference/firmware/analysis/controls-aptus-classic/extract_evidence.py
```

此脚本仅读取 / 反汇编字节并生成证据，不运行厂商程序，不发送蓝牙命令，不写后背。

## PDAInterface 完整注册目录

经典 4.701 的目录函数为 `0xe640c`；共享 5.041 为 `0x1026a4`。两者都注册以下 9 种命令，接口 ID 为 1。表中的构造函数和名称 getter 均经源字节核对。

| Tag | 4.701 注册调用 → 构造函数 → 名称 getter | 5.041 构造函数 → 名称 getter | 两版 WiView |
| --- | --- | --- | --- |
| ReSyncRequest | `0xe644c → 0xa78f8 → 0xa7ab0` | `0xafd5c → 0xaff14` | 均有实际构造引用 |
| GetImage | `0xe649c → 0x90a38 → 0x91830` | `0x97c40 → 0x98a34` | 均有 |
| GetPictureParams | `0xe64d4 → 0x8261c → 0x838a0` | `0x87adc → 0x88db0` | 均有 |
| FileCommand | `0xe650c → 0xa4b04 → 0xa634c` | `0xacf78 → 0xae794` | 均有 |
| ImageFunction | `0xe6544 → 0xa7b88 → 0xa898c` | `0xaffec → 0xb0d98` | 均有 |
| SetPictureParams | `0xe657c → 0x83eec → 0x84810` | `0x8931c → 0x89be8` | 均有 |
| RequestSync | `0xe65b0 → 0x84ea0 → 0x877a4` | `0x8a1b4 → 0x8d6e8` | 均有 |
| SetCameraParam | `0xe65e0 → 0x9236c → 0x942d0` | `0x99754 → 0x9bdb0` | 均有 |
| GetCameraParam | `0xe6610 → 0xd31b0 → 0xd48e8` | `0xe3a68 → 0xe51a0` | 均有 |

中间 5 个图像 / 文件命令处于条件分支；条件尚未完整映射到设备与工作模式，不能只凭共享目录承诺所有型号全部具备。经典 GetImage 的扩展参数还受 `0x95230` 能力检查控制。

比较不只搜索字符串：对 WiView 1.0.2 / 1.0.3 PE 的可执行 ARM 节扫描了 LDR 字面量引用，确认上述 9 个 tag 都有构造文本引用。`Ack` 及 1.0.3 的 `Nak` 是应答，不另算业务命令。本次没有发现“比 WiView 多一个独立 GetFile / LiveView tag”的无线目录；新增研究价值主要在命令内部属性、版本方言和后台处理链。

## A：已明确语义并接入正常应用流程

- 经典 `SetCameraParam ShootSetup` / `DefaultShootSetup`：4.701 属性解析 `0x92564..0x92720`，完整 CSV 写入 `0x92e28` → `0x1be884`。ISO 从完整结构的 `+0x200` 进入 `0x1bf22c`；它不是此版独立 `ISO` 属性。`RequestSync` 在 `0x86878..0x86bc8` 序列化完整设置，字段表 `0x3cca44` 包含 ShootSetup / DefaultShootSetup。应用按设备返回的完整配置与枚举编辑，并用新 Sync 核对。
- `GetImage` 拍后预览与细节：4.701 `0x90c20..0x90da8` 解析 file_name / PacketSize / zoom / light / Alarm；`0x91430` 处理缩放与 XCoord/YCoord，`0x915d0` / `0x9165c` 处理 light / 高低光警报。返回附件、Size、Light 的路径在 `0x90ddc..0x91430`。这些是已拍照片的图像请求。
- `GetPictureParams` / `SetPictureParams`：4.701 的 Marking 不依赖 XMP_tag。`0x84270..0x84284` 仅在 XMP 标志存在时走 XMP，`0x84288..0x842a0` 独立调用 `0x1c0bd0` 置 / 清 Good bit；读取 `0x82b00..0x82b0c` → `0x1c0c94` 返回 Good / UnMarked。备注与标记在解析器中是 else-if，分别请求。
- `ImageFunction fstops="x;y"` 是图片取样坐标，应用用于取样显示；不是光圈或曝光档位设置。`FileCommand` 的复制、移动、删除、建目录、空目录删除和目录重命名已有明确原构造与应用路径。
- GetCameraParam 的时钟、电量、照片计数、默认备注 / 版权等读取，以及已证明的对应设置，按支持状态接入。共享 5.041 的 CameraSettings / ImageSettings 方言也已实现，但只有设备实际返回该方言时才启用，不能套到经典 Aptus。

“接入”描述源码中的正常操作与验证逻辑，不表示本次静态调查已在所有后背上实测。

## B：无线入口存在，进入实验室研究

| 功能 | 证据与已知语法 | 尚未解决 |
| --- | --- | --- |
| Gain 读取 | `GetCameraParam GetParamList="Gain"`；经典 parser `0xd41a4` 设 `0x20000000`，回复 `0xd3bdc..0xd3bf4` 检查同 flag 并调用 `0xd4448`，输出 UserGainStorage / UserGainList | 列表项 / 存储名语义、校准含义、各型号返回值 |
| UserGain 写入 | 经典 parser `0x929dc..0x92a04` → `0x9403c`；共享 5.041 也存在独立 UserGain 属性 | 接受值、是否选择校准 / 配置、持久化与写后确认 |
| 增益状态 | 两版 Sync 表都有 UserGainList、ActiveUserGain、UserGainStorage、FactoryGain；经典表 index 27..30 | FactoryGain 返回字段不等于同名 setter |
| Brightness / ResetCounter / PIN / FactorySettings | 原客户端有真实构造；经典固件也接受对应 key，实验模板保留确切大小写 | 亮度量程、重置范围、PIN 字符与恢复、工厂设置对持久化 / 校准的影响 |
| qformat / recovery | 原版 FileCommand 构造已定位 | 目标存储名、格式化 / 恢复范围、数据保留效果；恢复请求不是已证明的只读操作 |
| ReSyncRequest | 在两版 PDA 目录和客户端都有真实构造 | 完整同步恢复时序和设备端行为 |
| GetImage zoom="FP" | 经典 parser `0x91574..0x915b8` 接受 FP / fp | 具体图像范围 / 分辨率；不能称原片下载 |
| ManualLens | 只在共享 5.041 SetCameraParam parser / 整数存储路径证实 | 经典 4.701 parser 未见该字段；单位、范围、作用未知，不能称遥控机械快门 |

共享 5.041 的 Gain 回复链也已确认：`0xe4b14` → `0xe451c..0xe4534` → `0xe4e1c`。这里已经超过“只有字符串”的证据层级，但不会把没有还原的值含义写成确定功能。

[lab-candidates.json](../reference/analysis/lab-candidates.json) 分开记录原构造、固件 parser / handler、仅返回字段、内部接口和未确认研究名称，列出取值缺口及写状态 / 写存储 / 擦除 / 重置 / 校准影响。用户已选择通过实验室手动验证；清单没有自动探测或自动发送流程。

## C：内部功能与未发现的入口

`BootRequest` 的名称与构造函数存在，但经典唯一直接构造调用 `0xe5d28` 在 PXA 目录中，未列入 PDA 目录。它不能当作已证明的蓝牙重启命令。`CAptus22_ISO_Adjuster` 是内部类型；不等于已找到无线校准命令。CE 中的 GetFileSize / GetFileVersionInfo 等是操作系统文件 API，也不是无线 GetFile。

尚未证明完整 MOS / RAW 下载或连续传感器取景：两个已核查 PDA 目录都没有 GetFile / LiveView tag；本次也未还原可替代它们的属性、起流命令或原片响应。实验清单对此使用 `wire_template: null` 的研究占位，不制造可发送的厂商语法。设备内部 copy / move、反复请求已拍照片以及 FP 图像选项，都不足以证明这些扩展。

本轮未运行任何厂商二进制、写入校准、格式化后背、修改 PIN 或自动扫描命令。后续设备实验记录应绑定后背型号、固件版本、原始请求 / 响应及实际效果，分别保留成功、拒绝、超时与无变化结果。
