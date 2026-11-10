# Leaf2Android

[English](README.en.md) · [安装与使用](docs/quick-start.md) · [应用架构](docs/architecture.md) · [功能与验收状态](docs/implementation-status.md) · [协议研究](docs/protocol.md) · [v0.2.0 发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0)

将 Leaf WiView 的 HP iPAQ / Windows Mobile 工作流迁移到原生 Android，让手机直接连接 Leaf **Aptus / Valeo** 数码后背。应用使用 Kotlin、Jetpack Compose 和协程，不执行原厂 Windows CE 程序，不依赖电脑中转。

**0.2.0 是实验性预发布版本。** 本轮统一项目名称、整理参考资料，并增加默认跟随系统的中文 / English 界面。Aptus / Valeo 是应用中的产品系列名称，**不代表全部型号和固件已通过兼容性验收**。原始实机目标是 Galaxy Z Fold7 / Android 16 / One UI 8.5 + Aptus II 5 + 技术相机机械快门；目前尚未完成该组合的真实蓝牙验收。

## 下载与开始使用

从 [v0.2.0 预发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0) 下载签名的 `Leaf2Android-0.2.0.apk`。附件同时提供源码、参考资源和 SHA-256 校验值，具体文件以发布页面为准。

1. 将 APK 传到手机并安装。应用 ID 为 `com.leaf2android`，最低 Android 12 / API 31。
2. 未连接后背时，打开离线演示，检查缩放、JPEG 直方图、本机标记和两种界面风格。
3. 在连接页授予“附近设备”权限、开启蓝牙，搜索后背，在 Android 系统对话框中输入后背当前 PIN。
4. 连接和同步成功后，手动释放技术相机机械快门；拍摄通知会触发 JPEG 预览请求。

演示图由程序生成，明确标为离线，不代表后背连接成功。签名切换和故障处理见 [安装与使用](docs/quick-start.md)。

## 已实现与待验证

| 功能 | 代码与交互 | 当前边界 |
| --- | --- | --- |
| 蓝牙发现、配对、直连 | Android 公开 API、经典蓝牙 SPP / RFCOMM | 目标后背服务、PIN 和 Android 16 链路仍待实测 |
| 同步、目录、拍后预览 | 串行命令、通知队列、文件名核对、精确附件长度 | 依据两个 WiView CAB 的静态代码，完整会话待设备确认 |
| 回放与缓存 | 原样保存预览、SHA-256、目录浏览、名称 / 接收时间排序 | 缓存是预览，不是 MOS 原片 |
| 放大与曝光检查 | 双指缩放、双击放大 / 复位、RGB 直方图和明暗警示 | 来自实际 JPEG 预览像素，不是 RAW 测光 |
| 标记与备注 | 本机星标 / 备注；单独提供后背备注写入与信息回读 | 本机操作不会自动改写后背；后背操作效果待测 |
| 双界面、双语言 | 现代 / 经典 PDA；中文 / English，默认跟随系统 | 语言与风格分别设置，共享会话、照片和放大位置 |
| 会话维持与恢复 | connectedDevice 前台服务、通知断开、超时关闭、手动重连 | 锁屏、后台和长时间拍摄待真实手机验收 |
| MOS 完整原片 | 未启用 | 尚无已确认的完整文件读取指令 |
| 连续实时取景 | 未启用 | 拍后自动显示与连续取景分别对待 |
| 遥控机械快门 | 当前组合不支持 | 没有可由应用驱动的快门执行器，继续手动释放 |

项目按“后背无线可用”的假设先行实现。代码存在、合成样本通过和模拟器通过不能替代真实后背测试。详见 [功能支持表](docs/implementation-status.md) 与 [机器可读验证记录](research/reports/verification-status.json)。

## 界面、语言与数据

- 默认现代风格，可在设置中切换经典 PDA。两种风格使用同一会话和照片仓库，窄屏单栏、宽屏双栏。
- 语言默认跟随系统，可选择跟随系统、简体中文或 English。设备实际名称、照片路径、用户备注和原始元数据不会被翻译或改写。
- **界面语言与后背协议字符集独立。** 原客户端使用 PDA 系统代码页。默认 Windows-1252，另提供 GBK 和 UTF-8 供实测；非 ASCII 文件名与备注仍需核对固件行为。
- “跟随新片”决定新预览是否自动成为当前照片，查看旧照片时可关闭。
- 缓存、星标和本机备注保存在应用私有目录。卸载会移除这些数据；保存 JPEG 预览只导出实际收到的预览字节。

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
│  ├─ firmware/             后背固件收集状态；WinCE EXE/DLL 不是后背固件
│  └─ sources.lock.json     参考输入的来源、版本及固定校验值
├─ docs/                    中英文使用、架构、协议与验收文档
└─ dist/                    本机构建和发布附件，不是 APK 运行依赖
```

工作目录为 `D:\Leaf2Android`；迁移后旧目录 `D:\Leaf4Android` 应为空。原厂 CAB、内部成员和版本名称保留原名，保持研究可重复性。当前没有发现可确认为数码后背本体固件的镜像，详情见 [参考资料](reference/README.md)。

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

Android 测试需要设备或模拟器。协议和仓库测试使用合成回复与合成 JPEG，覆盖拆包、合包、异常长度、截断、超时、串行请求、图像对应和状态保持；这些不是实机连拍结果。当前实际测试计数和构建证据保存在验证报告中，不把旧版本计数当作本次结果。

原始输入为 WiView **1.0.2** 与 **1.0.3**。静态代码显示原客户端使用经典蓝牙 SPP，UUID 为 `00001101-0000-1000-8000-00805f9b34fb`。控制头为 `^...>`，其后可紧跟 `attachment` 声明长度的二进制数据。文件名以 `.mos` 结尾不足以证明 GetImage 返回完整原片。见 [版本分析](docs/version-analysis.md)、[协议边界](docs/protocol.md) 和 [研究复现](docs/research.md)。

## 诊断、贡献与发布

设置中可查看或导出近期日志。应用不代填或记录 Android 系统配对 PIN。日志仍可能包含文件名、后背状态与元数据，分享前应检查。请勿提交个人照片、配对 PIN、签名私钥或访问令牌。

兼容性反馈请包含应用版本、手机完整型号 / Android / One UI、后背准确型号与固件、SPP 是否发现、复现步骤和脱敏日志。硬件支持声明需有真实测试记录。协议改动应附依据与可重复样本，区分合成数据和真实采集，不用猜测的 UUID、握手值或成功状态替代证据。

仓库：[RayforLoy/Leaf2Android](https://github.com/RayforLoy/Leaf2Android)。源码、参考资源和签名 APK 分开组织，实际附件以 [v0.2.0 预发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0) 为准。原厂文件的权利和原许可不因收录改变，本项目不为原厂程序重新授权；源码授权以仓库实际附带的许可文件为准。
