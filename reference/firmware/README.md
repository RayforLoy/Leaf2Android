# Leaf 后背固件参考资料

[English](README.en.md) · [经典 Aptus 证据](analysis/controls-aptus-classic/README.md) · [双语无线接口调查](../../docs/firmware-bluetooth-survey.md) · [文件清单](inventory.json)

用户新提供的 `D:\leaf\firmware` 包含真实后背固件。此前“尚无后背固件”的说明已由本次有来源、版本与校验记录的资料取代。原始目录只读，本次仅复制和静态解析，没有执行厂商程序、连接设备或刷写固件。

**经典 Aptus 4.701 是本次 Aptus 22 相关无线研究的主要固件证据；5.041 仅作共享程序与新方言对照。Aptus II 型号本身没有蓝牙硬件，不再列作无线待验收对象。** 名为 `FirmWareAptusII.UPD` 的镜像含共享 PDAInterface 代码，不代表该型号具备无线硬件。

## 收录范围与来源

四套原始来源共 310 个条目、463,158,198 字节，全部与用户已有 manifest 的大小、SHA-256 一致。按内容去重后保留 89 个原始文件、83,696,381 字节；每个原始名称均在 [source-aliases.json](provenance/source-aliases.json) 中映射到一份字节相同的实体。

| 来源套件 | 原始文件数 | 新增唯一文件数 | 已识别内容 |
| --- | ---: | ---: | --- |
| Capture One Mac 7.1.6 | 84 | 73 | 经典 Aptus 4.701 / 4.700、Aptus II / AFi / Mamiya DM 命名的共享 5.041、CE 容器、恢复镜像及板级 UBF |
| Capture One Mac 7.1.4（应用内版本 7.1.4.92） | 84 | 0 | 与上述同名文件逐项相同 |
| Capture One Windows 11.3.2 | 84 | 0 | 与上述同名文件逐项相同 |
| Leaf Capture Windows 11.2.9 | 58 | 16 | 额外的 4.960 / 4.701 主固件、资源版本 1.9.6 的 Aptus II CE 镜像及不同 UBF |

来源安装器地址、原始清单及当时的提取方法保存在 [provenance/](provenance/) 中。本次核对的是本地提供文件与既有清单，未重新下载或验证远端安装器。多个型号文件内容相同不表示已经证明适配所有后背；文件名也不足以证明某个镜像适合刷写某型号。没有找到 5.048 固件。

## 目录结构

```text
originals/
  CaptureOne.Mac.7.1.6/      # 73 份唯一厂商文件，优先保留 Aptus II 主文件名称
  LeafCapture.Win.11.2.9/    # 16 份额外唯一文件
provenance/
  *-manifest.json           # 四份原始清单，逐字节保留
  original-README.txt        # 原有说明，编码和历史路径保持原样
  source-aliases.json        # 全部 310 个来源条目与唯一实体的映射
  analysis-selection.json   # 收录及省略的分析输出、哈希、原因
analysis/
  supplied/                 # 用户已有分析和工具，保持原字节
  controls-aptus-classic/    # 经典 4.701 的载荷、PDA 目录、CE 校验及原客户端对照
  controls-5.041/            # 共享 5.041 新方言的有界 PPC 反汇编与字段表
  CONTROLS.zh-CN.md
  CONTROLS.en.md
tools/
  import_sources.py
  extract_control_evidence.py
  build_inventory.py
inventory.json              # 本子目录的独立完整性清单，不含自身
```

`analysis/supplied/` 收录 64 个已有分析文件、19,421,568 字节，包括原始分析报告、提取脚本、清单、字符串、已有反汇编，以及重建的核心 `LeafCE.exe` 和 registry。其余 773 个分析文件、79,168,651 字节是可重建重复输出、OS 组件、第三方依赖和提取的 UI 资源；它们以路径、大小、SHA-256 与省略原因列在 `analysis-selection.json`，未全部复制。

原始来源记录中的旧项目名、`D:\Leaf4Android`、`D:\leaf` 等路径属于历史证据，未被改写。当前重现命令使用 `D:\Leaf2Android`。原版分析脚本可能依赖原目录布局和第三方工具；它们的历史状态得到保留。新增控制证据脚本直接读取本目录的标准固件路径。

## 核心镜像与分类

| 文件 | 大小（字节） | 判定 | SHA-256 |
| --- | ---: | --- | --- |
| `originals/CaptureOne.Mac.7.1.6/FirmwareAptus.UPD` | 4,235,648 | 经典共享 Aptus 主固件 4.701，编入 Aptus 22 模型；本轮主要无线证据 | `a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a` |
| `originals/CaptureOne.Mac.7.1.6/FirmWareAptus.BIN` | 7,730,663 | 经典后背 CE 容器，接口版本 1.8.6；27 个载入记录校验通过 | `4a0d32186d95bd0aaf0a741d161b98712e30beac595d7d5d10dabffaa86397c0` |
| `originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.UPD` | 6,021,144 | `LEAF FRM` 封装，版本 5.041；共享 PowerPC / pSOS+ 程序，作新方言对照，不证明 Aptus II 有无线硬件 | `80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6` |
| `originals/CaptureOne.Mac.7.1.6/FirmWareAptusII.BIN` | 9,810,419 | 后背 ARM / Windows CE 界面 ROM 载入记录镜像，非 PDA 客户端 | `2be7ce63460de2908c81c48d5bd26cdec834b9b4d7ffd2a33928160f2cd83549` |
| `originals/CaptureOne.Mac.7.1.6/BootRecovery.UPD` | 6,113,344 | 恢复镜像；头部 `0xfffffffe` 是特殊标记，不解释成普通固件版本 | `4f71ebe188f8dd301dc0afcaa52e059296f29f851114ea7237c41df23bada535` |
| `analysis/supplied/ce_reconstructed/fs/Windows/LeafCE.exe` | 14,135,296 | 从后背 CE ROM 重建的界面 PE，可供静态分析；包含重建头部，不是原始刷写镜像 | 见 `inventory.json` |

经典 4.701 与共享 5.041 UPD 的原始文件地址映射均为 `文件偏移 = PowerPC 虚拟地址 - 0x74000 + 0x128`，但函数地址分别记录，不混用。BIN 使用 `B000FF\n` CE ROM 记录格式：经典 Aptus 容器的 27 条记录和共享 Aptus II 命名容器的 30 条记录分别校验通过。UBF 的版本字段描述板级 / FPGA 数据，不把它们当作整机固件版本。

经典模型关联来自编译描述表：虚表 `0x3f59b8/0x3f59bc` 分别指向返回 `0x0c` 的模型 ID getter 与返回 `Leaf Aptus 22` 的名称 getter；镜像也含 `CAptus22_ISO_Adjuster` 类型。`Firmware.config` 只有 `EnforceUploading=0`。这些证据确认经典共享镜像包含 Aptus 22 模型，不能据此称为 Aptus 22 专用刷机包。

WiView 的 PDA EXE / DLL 仍位于 [../analysis/wiview-1.0.3/](../analysis/wiview-1.0.3/) 等目录。它们与这里的后背端主程序和 CE ROM 明确分开。

## 静态分析结果与边界

经典 4.701 的 PDAInterface 完整目录有 9 个 tag，并逐一核对注册调用、构造函数、虚表与名称 getter。两版原 WiView 均有这 9 个 tag 的实际 ARM 构造引用。经典完整 `ShootSetup` / `DefaultShootSetup`、`RequestSync` 回读、不依赖 XMP 的 Marking 读写，以及 GetImage 扩展参数均已分别追踪。Gain 请求也沿 parser → 可达响应确认，输出 `UserGainStorage` / `UserGainList`，其校准与取值语义仍待设备验证。主证据见 [controls-aptus-classic/evidence.json](analysis/controls-aptus-classic/evidence.json)。

共享 5.041 的 PDAInterface 也有这 9 个 tag。本次沿调用链核实了其独立 ISO 设置与 `RequestSync` → `CameraSettings` 的 ISO 回读来源，以及不带 `XMP_tag` 的 Marking 读写。该结论只用于共享代码 / 方言对照，不扩展为 Aptus II 的蓝牙能力。

该版本 Sync 使用 `CameraSettings`、`DefaultCameraSettings`、`ImageSettings`、`ActiveImageSettings` 等字段。旧 WiView 的 `ShootSetup` 方言不能只靠名称假定与本版相同。`GetCameraParam` 虽会解析 `GetParamList=ISO`，现有响应构造证据没有表明它返回 ISO；ISO 回读应使用已核实的 Sync 路径。详见中英文控制分析报告。

这些结论来自保留镜像的静态代码，不等于已经通过 Fold7 与所有具备无线硬件的 Aptus / Valeo 组合实测。Aptus II 的硬件缺少蓝牙不是待验证功能。其他组合的服务发现、配对、无线传输、实际设置效果、原片传输和连续取景仍按具体设备与固件记录实测结果。

实验候选的提取语法与用户自行建立的假设分开记录：GetFile / LiveView / BootRequest 没有已提取的 PDA 请求模板；实验室可由用户明确建立 `USER HYPOTHESIS` 空头测试，不能回写为厂商已知语法。FactoryGain 仅有返回字段 / 候选读取，不存在已证实的独立 setter；未发现独立无线校准 tag 时不创造一个。

## 重现与验证

在项目根目录运行（Python 3，研究依赖见根目录 `requirements-research.txt`，反汇编使用 Capstone 5）：

```powershell
Set-Location D:\Leaf2Android
.\.venv\Scripts\python.exe reference\firmware\analysis\controls-aptus-classic\extract_evidence.py
.\.venv\Scripts\python.exe reference\firmware\tools\extract_control_evidence.py
.\.venv\Scripts\python.exe reference\firmware\tools\build_inventory.py --verify
python reference\build_inventory.py --verify
```

如需从仍保留的用户来源目录重新构建这份去重资料集：

```powershell
.\.venv\Scripts\python.exe reference\firmware\tools\import_sources.py D:\leaf\firmware
.\.venv\Scripts\python.exe reference\firmware\tools\build_inventory.py
```

导入脚本不删除输入，拒绝覆盖内容不同的已存在实体，每份收录文件均在复制后复核大小和 SHA-256。以上命令不运行固件。重建 CE 的既有工具来源和固定 commit 见 `analysis/supplied/third-party-tools.json`；其依赖没有被打包成项目运行时。

## 明确排除

`D:\leaf\LEAFDM SYS` 中可识别的是版本记录、灰平衡 / 图像设置与设备专属镜头校准，没有可执行固件；本次没有复制其中资料。个人 MOS、镜头校准、CCD 分析、Python 依赖缓存，以及 113,212,841 字节的完整 Leaf Capture 安装器也未收录。固件参考文件单个均小于 90 MiB；源码包与 release 资源由项目发布流程统一打包。
