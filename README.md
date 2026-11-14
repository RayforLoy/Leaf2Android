# Leaf2Android

[English](README.en.md) · [安装与使用](docs/quick-start.md) · [应用架构](docs/architecture.md) · [功能与验收状态](docs/implementation-status.md) · [完整缺口对照](docs/feature-gap-matrix.md) · [0.3.1 变更](docs/releases/v0.3.1.md) · [v0.3.1 发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.1)

将 Leaf WiView 的 HP iPAQ / Windows Mobile 工作流迁移到原生 Android，让手机直接连接 Leaf **Aptus / Valeo** 数码后背。应用使用 Kotlin、Jetpack Compose 和协程，不执行原厂 Windows CE 程序，不依赖电脑中转。

**0.3.1（versionCode 4）实验性预发布；更新于 2026-11-14。** 本轮扩大默认预览缓存，隔离完整设置事务，增加请求诊断与 Valeo 的 DP-67 兼容路径。它们是针对“读图正常、改设置超时”的候选修正，**尚未证明已解决真实后背问题**，见 [本版说明](docs/releases/v0.3.1.md)。既有双风格、中英文、图像检查和实验室保留。Aptus / Valeo 是系列名称，不代表全系列已验收；用户已纠正 **Aptus II 本身无蓝牙，不是当前无线目标**，II 5 早期计划仅为历史。Galaxy Z Fold7 / Android 16 / One UI 8.5 的实机验收另行记录。

**硬件回报更新（2026-11-11）：用户已报告 Valeo 22Wi 和 Aptus 22 均能正常蓝牙连接、读取照片。** 这是两个具体型号的使用回报，不推定 Aptus II 5 或整个系列兼容；后背固件、实际手机 / 系统环境、应用版本和测试日志尚未随报告提供。

2026-11-14 的 Valeo 反馈进一步定位为读图可用、修改设置时超时，未说明具体改哪项。截图只能证明 App 超时后主动关闭会话，不能区分未收到 Ack、解析 / 匹配失败、事务互插或较早物理失联。网络有一般 iPAQ 掉线经验，尚无 WiView 1.0.3 同机 A/B 证明 DP-67 更稳，也无写 ISO 立即断线的直接证实；见 [独立研究](docs/valeo-connection-research.md)。

已重新核对经典 `FirmwareAptus.UPD` **4.701**：镜像含 Aptus 22 模型，独立追踪 PDAInterface 的 9 类命令、完整 ShootSetup、Marking 和图像扩展。它是含 Aptus 22 模型的共享固件，不是 Aptus 22 专用可刷包，也未证明安装在用户后背上。详见 [固件蓝牙复核](docs/firmware-bluetooth-survey.md)。5.041 的 PDAInterface 同样只证明共享代码；本文的 5.041 设置指软件方言，不能证明 Aptus II 具有蓝牙硬件。

## 下载与开始使用

从 [v0.3.1 预发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.1) 下载签名的 `Leaf2Android-0.3.1.apk`。附件提供源码、参考资源和 SHA-256，实际文件以发布页为准。[0.3.0 历史说明](docs/releases/v0.3.0.md) 保留。

1. 将 APK 传到手机并安装。应用 ID 为 `com.leaf2android`，最低 Android 12 / API 31。
2. 未连接后背时，打开离线演示，检查缩放、JPEG 直方图、本机标记和两种界面风格。
3. 在连接页授予“附近设备”权限、开启蓝牙，搜索后背，在 Android 系统对话框中输入后背当前 PIN。
4. 连接和同步成功后，手动释放技术相机机械快门；拍摄通知会触发 JPEG 预览请求。

演示图由程序生成，明确标为离线，不代表后背连接成功。签名切换和故障处理见 [安装与使用](docs/quick-start.md)。

## 已实现与待验证

| 功能 | 代码与交互 | 当前边界 |
| --- | --- | --- |
| 蓝牙发现、配对、直连 | Android 公开 API、经典蓝牙 SPP / RFCOMM | Valeo 22Wi、Aptus 22 已有用户连接成功回报；配对、安全模式和手机 / 固件细节待补 |
| 同步、目录、拍后预览 | 串行命令、通知队列、文件名核对、精确附件长度 | 两型号已报告读取照片；目录、拍后自动刷新和完整通知时序未单独验收 |
| 拍摄配置与 ISO | AUTO / WIVIEW / DP67 三种兼容设置；从 Sync 读取当前值和允许项，写前 / 写后核对 | AUTO 仅在蓝牙名含 Valeo 且 Sync 有 shootsetup 时走 DP-67；改名设备可离线手选，硬件效果待测 |
| DP-67 兼容写入 | 12 核心字段，加当前配置已有的 Prefix；原预设编辑进 .1 修改槽，回读新 active 与原预设 | 不附 WiView 压缩、Orientation / Color，标准界面隐藏这些字段；已有同名修改槽会明确提示更新 |
| 设置事务与诊断 | operationLock 覆盖预同步、设置及回读，自动 GetImage 不插入；通知 Ack 不取此锁 | 超时摘要指出 tag / 属性名而无值；仍断流、手动重连，不自动重试写入；真实 Valeo 待复测 |
| 5.041 相机设置 | 识别 Sync.CameraSettings 后独立写 ISO、方向、机身类型、SensorCrop、压缩、前缀和拍摄目录，并全量回读 | ISO 等允许项来自设备；裁切只接受纯名称列表，压缩仅 None / Lossless；不外推到所有固件 |
| 5.041 图像处理配置 | 完整 ImageSettings 编辑、选择已有 ActiveImageSettings | Grain / MoireReduction / Saturation 等只在 Sync 提供选项时可编辑；保留未改值并回读 |
| 回放与缓存 | 默认 1024 MiB，原字节 / SHA-256、目录浏览、名称 / 接收时间排序 | 首次升级将旧 256 迁至 1024，保留原 64，之后手选 256 等不再重置；预览不是 MOS 原片 |
| 放大与曝光检查 | 双指缩放、双击放大 / 复位、RGB 直方图和明暗警示 | 来自实际 JPEG 预览像素，不是 RAW 测光 |
| 标记与备注 | 本机星标 / 备注与后背 Good / Unmarked 分开；严格解码 Base64 / UTF-8 XMP，标记只改 Flags 第三个字符 | 保留其余 XMP；Sync 有 shootsetup / camerasettings 时可用已证 Marking-only；无已识别方言且缺 XMP 则标准路径拒写 |
| 后背文件管理 | 新建目录、空目录删除、目录改名、照片删除、后背内部 copy / move | 不覆盖目标，操作前确认具体路径并回读目录；不是下载原片 |
| 后背处理预览 | 已接入 zoom 1 / 2 / 4 / 8 与 Alarm 亮暗警示，返回 JPEG | 与本机双指放大 / JPEG 警示分开；手填原图协议坐标，不猜屏幕映射；效果待实测 |
| 灰平衡与曝光检查 | 取样后可分别确认写当前照片或用于后续拍摄，另有恢复预览、坐标像素拾取和任意桶数后背直方图 | 后续拍摄路径仅用本连接当前照片关联的后背取样值，完整配置提交并回读；fstops 为坐标，RAW / JPEG 来源未确认；效果待测 |
| 时间、默认备注、电源请求 | 手机本地时间同步并读回 CurrentTime；默认 Annotation / Copyright 写入与回读；电源来源请求 | PowerSource 须设备声明 support，且只报告 Ack；EnableTakenPicture 已移入实验室，不提供未经证实的暂停开关 |
| 实验室 | 16 个 WiView 模板、固件研究条目和单原始控制头；手动确认、响应 / 错误 / 附件记录与 ZIP 导出 | 标准功能与候选探测分开；未知取值不默认，收到 Ack 不代表生效，不自动扫描或刷写 |
| 双界面、双语言 | 现代 / 经典 PDA；中文 / English，默认跟随系统 | 语言与风格分别设置，共享会话、照片和放大位置 |
| 会话维持与恢复 | connectedDevice 前台服务、通知断开、超时关闭、手动重连 | 锁屏、后台和长时间拍摄待真实手机验收 |
| MOS 完整原片 | 未启用 | 尚无已确认的完整文件读取指令 |
| 连续实时取景 | 未启用 | 拍后自动显示与连续取景分别对待 |
| 遥控机械快门 | 当前组合不支持 | 没有可由应用驱动的快门执行器，继续手动释放 |

早期按“后背无线可用”的假设先行实现，现已取得上述两型号的连接和读图用户回报。该回报不等于已验证至少 20 张连拍、ISO 等参数操作、备注写入、MOS 原片、实时取景或长时间会话。代码、合成样本和模拟器结果也不能替代这些实机验收。详见 [硬件记录与功能支持表](docs/implementation-status.md)；[机器可读验证记录](research/reports/verification-status.json) 另记录工具实际执行的验证。

WiView legacy 完整配置仍支持 ISO、画质、取景框、色调、Light、锐化、ICC、机身类型、前缀、目录、压缩、方向及 Color；**DP-67 是单独的较小写入格式**，不发送压缩、方向、Color 扩展，也不修改 CEVersion。选原预设仅发 DefaultShootSetup，选已有修改配置发完整 DP-67 CSV；修改前会提示目标槽位，回读验证 active、应写值和原预设未变。原 DP-67 的 12 秒×3 重发没有照搬。字段与保留规则见 [静态分析](docs/dp67-analysis.md) 和 [使用说明](docs/quick-start.md)。

所有标准设置写前重读、写后核对可见的预期值与必要全局值；不一致不报成功。5.041 独立前缀编辑不超过当前已知 UTF-8 长度且最多 23 个 UTF-16 代码单元，这是实现限制。底栏“后背 / Back”页集中提供控制；实验室仍可手动探测被标准兼容路径隐藏的字段。

读取到 ShutterSpeed 不等于可以设置机械快门速度或光圈。Brightness、ResetCounter、PIN、FactorySettings、qformat / recovery、增益和 ManualLens 等可进入实验室主动验证；量程、含义、无线可达性或恢复流程未确认的部分保持实验标识，不作为已验证标准功能。完整 MOS、连续取景和完整原厂重传恢复仍未实现。逐项边界见 [完整缺口对照](docs/feature-gap-matrix.md)。

从“后背 / Back”页顶部打开全屏实验室，选择模板或手工输入一条控制头，填写实际参数、回应匹配方式和超时，检查目标与请求后确认发送。实验期间暂停普通拍后读图 / 配置刷新，执行过实验后退出会断开连接，避免迟到帧污染普通会话。操作与导出细节见 [实验室指南](docs/quick-start.md#实验室手动验证)。

实验记录保留脱敏请求 / 响应头、错误、未匹配帧及原始附件。PIN、备注、版权和 XMP 等已知头字段默认遮蔽，实际发送字节不变；二进制附件保留原字节，不承诺自动脱敏。GetFile / LiveView / BootRequest / FactoryGain 的假设探测明确标为 USER HYPOTHESIS，不是已提取的厂商语法。

## 界面、语言与数据

- 默认现代风格，可在设置中切换经典 PDA。两种风格使用同一会话和照片仓库，窄屏单栏、宽屏双栏。
- 语言默认跟随系统，可选择跟随系统、简体中文或 English。设备实际名称、照片路径、用户备注和原始元数据不会被翻译或改写。
- **界面语言与后背协议字符集独立。** 原客户端使用 PDA 系统代码页。默认 Windows-1252，另提供 GBK 和 UTF-8 供实测；非 ASCII 文件名与备注仍需核对固件行为。
- “跟随新片”决定新预览是否自动成为当前照片，查看旧照片时可关闭。
- 标准照片操作要求预览来自当前后背且在本次连接中重新读取。重连、升级或进程重启后须重新读图再写入；其他后背缓存及演示图不能直接用于标准写入。实验室原始请求按用户填写目标执行，不继承标准操作的全部语义校验。手机星标保持独立。
- 缓存、星标和本机备注保存在应用私有目录。卸载会移除这些数据；保存 JPEG 预览只导出实际收到的预览字节。
- 预览缓存默认 1024 MiB；从旧版本升级仅执行一次 256 → 1024 MiB 迁移，原 64 MiB 保留。完成迁移后，用户再选择 256 / 64 / 1024 不会被后续启动改回。实验室附件缓存仍有独立预算。

## 项目结构

```text
Leaf2Android/
├─ app/                     Android 蓝牙、会话服务、存储与双语言 Compose 界面
├─ protocol/                纯 Kotlin 分帧、命令、串行会话及测试
├─ leafresearch/            Python 离线流解析器
├─ tools/                   研究、构建、校验、打包和模拟器工具
├─ tests/                   Python 研究工具与分帧测试
├─ research/fixtures/       明确标识的合成协议样本
├─ research/reports/        当前应用 / APK 验证结果
├─ reference/
│  ├─ wiview/               原版 WiView 安装介质、指南和来源资料
│  ├─ analysis/             原程序解包、字符串、ARM 静态分析与证据
│  ├─ firmware/             去重固件原始资料、核心重建与分析；与 WinCE 客户端分开
│  └─ sources.lock.json     参考输入的来源、版本及固定校验值
├─ docs/                    中英文使用、架构、协议与验收文档
└─ dist/                    本机构建和发布附件，不是 APK 运行依赖
```

工作目录为 `D:\Leaf2Android`；迁移后旧目录 `D:\Leaf4Android` 应为空。原厂 CAB、内部成员和版本名称保留原名，保持研究可重复性。后背固件的收录状态、型号和来源以 [参考资料](reference/README.md) 为准；Windows CE 客户端 EXE / DLL 不能当作后背本体固件。

本轮固件同步按内容去重收录 89 份唯一原始文件（约 83.7 MB）及核心重建 / 分析。个人 MOS、校准文件和约 113 MB 的安装器不进入发布参考包；具体库存与校验以 `reference/firmware` 的清单为准。归档不等于手机刷写支持，也不证明某份固件已安装在用户测试后背上。

DP-67 参考资料收录原始 `Setup.exe` 及提取的 CAB，并保留提取与来源说明；含私人 PBF 的完整 ZIP 不收录，详见 [DP-67 分析](docs/dp67-analysis.md)。

## 构建与签名

需要完整 **JDK 21**、Android SDK Platform **36**、Build Tools **36.0.0**。minSdk 31，compileSdk / targetSdk 36，JVM 字节码目标 17。依赖固定在 Gradle 文件中，Gradle Wrapper 的发行包 SHA-256 也已固定。

Windows PowerShell：

```powershell
.\tools\build-android.ps1 -Jdk 'C:\path\to\jdk-21' -Sdk "$env:LOCALAPPDATA\Android\Sdk"
```

直接使用 Gradle：

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
```

macOS / Linux 可使用 `./gradlew` 执行相同任务，并配置 `JAVA_HOME` 和 SDK。研究工具的 CAB 解包需要 Windows `expand.exe`，安卓应用不依赖它。Android Studio 的 Gradle JDK 应选择 21。

签名 release：

```powershell
.\tools\prepare-release-signing.ps1
.\tools\build-android.ps1 -Release -Jdk 'C:\path\to\jdk-21'
```

准备脚本把本地发布密钥和配置放在 `%USERPROFILE%\.android\Leaf2Android`，不加入仓库。也可从安全的本地环境或 CI secret 设置以下变量：

| 变量 | 内容 |
| --- | --- |
| `LEAF2ANDROID_KEYSTORE_PATH` | 仓库外的 release keystore 路径 |
| `LEAF2ANDROID_STORE_PASSWORD` | keystore 密码 |
| `LEAF2ANDROID_KEY_ALIAS` | 签名 alias |
| `LEAF2ANDROID_KEY_PASSWORD` | 私钥密码 |

标准 release 任务为 `:app:assembleRelease`。发布者必须保管同一密钥，才能给同包名 release 提供覆盖更新。debug 与 release 签名可能不同，切换前参阅安装文档。

## 验证与研究复现

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-research.txt
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Android 测试需要设备或模拟器。本轮协议与模拟器测试范围包括界面选择 ISO 后确认及完整配置回读、语言切换、Android XMP DOM、拒写 / 回读不一致和部分 move 检测。最终通过项数、灰平衡用于后续拍摄的新路径、构建、Python 研究工具测试和 APK 核验以本轮对应版本与哈希的 [机器报告](research/reports/verification-status.json) 为准。协议和仓库测试使用合成回复与合成 JPEG，不是实机连拍结果。

原始输入为 WiView **1.0.2** 与 **1.0.3**。静态代码显示原客户端使用经典蓝牙 SPP，UUID 为 `00001101-0000-1000-8000-00805f9b34fb`。控制头为 `^...>`，其后可紧跟 `attachment` 声明长度的二进制数据。文件名以 `.mos` 结尾不足以证明 GetImage 返回完整原片。见 [版本分析](docs/version-analysis.md)、[协议边界](docs/protocol.md) 和 [研究复现](docs/research.md)。

## 诊断、贡献与发布

设置中可查看或导出近期日志。应用不代填或记录 Android 系统配对 PIN。日志仍可能包含文件名、后背状态与元数据，分享前应检查。请勿提交个人照片、配对 PIN、签名私钥或访问令牌。

兼容性反馈请包含应用版本、手机完整型号 / Android / One UI、后背准确型号与固件、SPP 是否发现、复现步骤和脱敏日志。硬件支持声明需有真实测试记录。协议改动应附依据与可重复样本，区分合成数据和真实采集，不用猜测的 UUID、握手值或成功状态替代证据。

仓库：[RayforLoy/Leaf2Android](https://github.com/RayforLoy/Leaf2Android)。源码、参考资源和签名 APK 分开组织，实际附件以 [v0.3.1 预发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.1) 为准。原厂文件的权利和原许可不因收录改变，本项目不为原厂程序重新授权；源码授权以仓库实际附带的许可文件为准。
