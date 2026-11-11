# Leaf2Android 0.3.0 安装、使用与构建

[English](quick-start.en.md) · [项目首页](../README.md) · [功能支持表](implementation-status.md) · [完整缺口对照](feature-gap-matrix.md)

## 选择安装包

**0.3.0 预发布。** 从 [v0.3.0 预发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.0) 下载附件，或使用本地 `dist/`；功能与验证边界见 [本版说明](releases/v0.3.0.md)。本页新增后背操作已接入代码，硬件效果尚待验证。

| 文件 | 用途 |
| --- | --- |
| `Leaf2Android-0.3.0.apk` | 发布密钥签名的 release 测试应用 |
| `Leaf2Android-0.3.0-debug.apk` | 开发调试包，签名可能与 release 不同 |
| `Leaf2Android-0.3.0-source.zip` | 可构建源码与文档 |
| 参考资料压缩包 | 原版 WiView、静态分析与来源记录，不作为 APK 内嵌运行库 |
| `*.sha256` / 校验清单 | 对应附件的 SHA-256 |

附件以对应发布页为准。应用 ID 为 `com.leaf2android`，最低 Android 12 / API 31，编译与目标版本 Android 16 / API 36。该预发布不代表 Aptus / Valeo 全系列兼容已验收。

## 安装与签名

将 APK 传到手机，在文件管理器中打开安装，根据 Android 提示允许当前来源安装应用。连接开发环境后也可执行：

```powershell
adb install -r .\dist\Leaf2Android-0.3.0.apk
```

同应用 ID 的覆盖更新还要求签名一致。若从 debug 切换 release 时出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先保存需要的预览并另行记录本机备注 / 标记，再卸载旧包后安装。卸载会删除应用私有数据。此前误名测试包与 `com.leaf2android` 是不同应用，不会自动迁移数据。

发布者须保留 release keystore；私钥、密码和个人 `local.properties` 不提交仓库。APK 不包含原厂 EXE、DLL 或 CAB。

## 语言与风格

首次启动使用现代界面，语言跟随系统。设置中可选择跟随系统、简体中文或 English；风格可独立选择现代或经典 PDA。两者使用共享照片与会话仓库，保留放大位置。

语言只控制界面文案，不翻译设备实际名称、原始照片路径、用户备注或后背元数据。后背协议编码另行选择，不能用“界面切换英文”推断无线编码已经改变。

## 先检查离线演示

从拍摄页或设置中加载演示图。图像由程序生成并标为离线，连接状态仍是未连接。

- 双指缩放和拖动；双击放大 / 复位，适合屏幕按钮可复位。
- 工具栏提供 JPEG 直方图、JPEG 明暗警示、备注和预览保存。
- 星标与本机备注保存在手机，不会自动写回后背。
- 直方图和警示使用 JPEG 采样，不代表读取了 RAW 像素。
- 窄屏单栏、宽屏双栏；真实 Fold 内外屏、旋转和 One UI 分屏仍待验收。

## 手机直接连接 Aptus / Valeo

1. 后背开启 Wireless，关闭其他占用它的 WiView 客户端。
2. 连接页授予“附近设备”权限，开启手机蓝牙。
3. 搜索并识别后背，可先系统配对，在 Android 对话框输入后背当前 PIN，再连接。应用不会猜测 PIN。
4. 使用经典蓝牙 SPP / RFCOMM UUID `00001101-0000-1000-8000-00805f9b34fb`。启动依次尝试 RequestSync、CurrentTime、根目录，同步完成才显示就绪。
5. 技术相机机械快门手动释放。文件名明确且已保存的拍摄通知会触发预览队列，跟随新片开启时自动切换到新预览。
6. 回放可浏览后背子目录、按名称 / 接收时间排序和筛选本机星标。未缓存预览需保持后背连接。

用户于 2026-11-11 报告 Valeo 22Wi 和 Aptus 22 可正常蓝牙连接、读取照片，未提供实际手机 / 系统、后背固件、应用版本和日志。不据此推定 20 张连拍或下列新写入已通过。用户另已纠正 Aptus II 本身无蓝牙，不是当前无线目标；原始 Aptus II 5 计划仅作历史记录。5.041 的 PDAInterface 是共享代码，不是 Aptus II 蓝牙硬件证据；系列名称也不代表所有型号兼容。

经典 Aptus **4.701** 已作为独立主证据复核，含 Aptus 22 模型与 9 类 PDA 命令，见 [固件蓝牙调查](firmware-bluetooth-survey.md)。该镜像不是 Aptus 22 专用可刷包，不推定用户实际安装版本。5.041 配置只在设备 Sync 返回对应结构时使用。

## 从“后背”页设置拍摄参数

连接后打开底栏 **“后背 / Back”** 页，刷新 Sync，检查当前配置和设备给出的选项。控件依后背实际方言、完整配置和允许值开放；没有某个控件不等于同系列所有固件都不支持。不要把 ISO 选项列表第一项当成当前值，也不要按其他型号的档位猜测输入。

- **legacy 方言：** 当前 ISO 位于与 `DefaultShootSetup` 匹配的完整 `ShootSetup` 中。可选择后背已有配置，或编辑当前配置。只改变指定字段，保留其余配置原值后完整提交；这不是 `SetCameraParam ISO="…"` 的通用直接写法。
- **5.041 方言：** 识别到对应 `Sync.CameraSettings` 后，可独立写 ISO、Orientation、CameraType、SensorCrop、FileCompression、Prefix 和 ShootingFolder。SensorCrop 只接受设备下发的纯名称列表，压缩只接受设备下发的 `None` / `Lossless`，不混用 legacy 的 Nearly Lossless。
- ISO 档位来自设备，不硬编码。写入前重新读取当前状态，防止用较早的整份配置覆盖后背上的新改动；写入后再次 Sync，核对整份预期配置、未改字段和必要的全局值。只看到 ISO 变化不能代替完整核对。
- 缺少唯一完整配置、允许项不明确、字符无法安全编码或回读不一致时，停止或报告未确认，不用默认值补齐缺失字段。

legacy 完整配置的本轮范围包括 ISO、画质、取景框、色调、Light、锐化、输入 / 输出 ICC、机身类型、ICCFlag、文件名前缀、拍摄目录、普通 / Nearly Lossless 压缩及 NLCompression、方向和 Color。Light 是设备提供的光源 / 白平衡项，不是任意 Kelvin / tint 数字；已有配置选择也不是恢复 FactorySettings。

5.041 独立 Prefix 编辑最多 23 个 UTF-16 代码单元，且新值的 UTF-8 字节长度不能超过当前已知前缀；这是保守实现限制，不是原厂硬件长度规格。拍摄目录须存在，不会用设置目录的操作隐式创建目录。

## 图像处理配置

当 Sync 提供 `ImageSettings` 时，“后背”页可选择已有 `ActiveImageSettings`，或编辑当前完整处理配置。其范围包括 ImageQuality、Color、Framing、Toning、Light、SharpTable、InputProfile、OutputProfile、ICCFlag，以及 Grain、MoireReduction、Saturation；需要设备允许项的字段只有在 Sync 下发选项时才可编辑。

ImageSettings 与 CameraSettings 分开，ISO 不属于图像处理配置。只更改选择的字段，保留其他值，写前重新读取、写后全量回读；不以猜测值补齐缺失配置。

这些新增写入尚未由用户在真实后背上验证。ISO 和图像处理参数不是机械快门速度、光圈或遥控释放控制。

## 照片写入前先核对来源

标准照片标记、备注、灰平衡、删除、复制 / 移动等操作要求预览属于当前后背且在本次连接中重新读取。每次重连、升级或进程重启后，已有缓存只能本地回看，标准写入前先刷新预览。演示、无来源旧缓存和其他后背缓存不能直接标准写入。实验室原始请求另按用户输入目标执行，不继承全部照片来源和语义检查。相同路径不证明是同一张照片。

## 后背标记与手机星标

手机星标只存在本机。后背 `Good / Unmarked` 是独立操作，不能用手机星标是否变化判断后背已写入。

从 GetPictureParams 的 `xmp_tag` 头属性严格解码 Base64 / UTF-8 XMP。写 Good / Unmarked 只定向改变 Leaf `Flags` 第三个字符，保留其余字符、命名空间和字段。经典 4.701 与共享 5.041 均已证实 Marking 不依赖 XMP；应用在 Sync 出现 shootsetup / camerasettings 时允许 Marking-only。有 XMP 仍保留后定向更新，损坏 XMP 不视为可编辑；无已识别方言且缺 XMP 则标准路径拒写，不生成空包覆盖。写后回读，硬件效果待验证；实验室可另行手动探测。

## 后背目录与照片管理

0.3.0 的文件操作范围是新建目录、删除空目录、目录改名、删除照片、后背内部复制 / 移动。不是创建任意文件，也不是把 MOS 原片传到手机。

1. 刷新目录，选择具体后背路径；复制 / 移动还需选择后背上的目标目录。
2. 确认源、目标和操作类型。已有目标不得被静默覆盖，非空目录不作为空目录删除。
3. 只有用户明确确认后才发送操作；连接、预览和目录刷新本身不会自动删除文件。
4. 操作后回读相关目录。错误或回读不一致不能被当作成功，也不能只改手机中的路径映射冒充后背已完成。

删除会改变后背存储，实验室 recovery 请求不是已验证的撤销保证。该组操作同样尚未由用户硬件验证。

## 后背处理预览、灰平衡与曝光检查

这些入口已实现，实际固件效果尚待验证。它们对当前照片发出后背请求，与手机本地 JPEG 处理分开显示。

1. **处理预览：** 选择原厂 zoom `1 / 2 / 4 / 8`，可请求 Alarm 亮、暗或两者的警示，接收后背处理的 JPEG。此处倍率不是手机双指缩放。
2. **原图坐标：** 按需要手工填写原图协议坐标。当前没有完整验证 Compose 点位、适合屏幕、裁切视口和照片方向到原图的全部映射，因此不把屏幕触点或 JPEG 缩略图坐标直接换算成后背坐标。实际方向或原图尺寸不明时，不猜坐标。
3. **灰平衡：** 取样后查看后背返回的预览和 Light 字符串；可分别明确确认写入当前照片，或单独确认用于后续拍摄，另有恢复预览操作。取样本身不自动提交。后续拍摄使用完整 legacy ShootSetup.Light / 5.041 ImageSettings.Light：仅本连接当前后背返回、仍关联当前照片的取样值可不受静态光源列表限制，仍拒 CSV 分隔符，保留其他字段并 Sync 回读；任意手填值不能绕过检查。
4. **像素 / 曝光拾取：** 对指定坐标发送 `ImageFunction fstops="x;y"`，显示后背返回的 histogramlocation / histogramvalue。fstops 在这里是坐标，不是光圈。
5. **后背直方图：** 按收到的 histogram / r / g / b 实际桶数显示，不假定 256 桶。标明“来自后背，RAW / JPEG 来源未确认”；手机本地 JPEG 直方图保持独立。

灰平衡确认 / 恢复的 Ack 表示后背收到相应请求；需要重新读取照片信息与预览并检查真实效果，不能把 Ack 当成已测得的颜色变化。上述功能均不等同于连续实时取景或完整 MOS 传输。

## 时间、后续拍摄备注与会话请求

“后背”页另提供以下操作：

| 操作 | 核对方式与边界 |
| --- | --- |
| 同步时间 | 发送手机当前本地时间，再读取 CurrentTime 核对；协议未宣称传输时区 |
| 默认 Annotation / Copyright | 读取、编辑、提交后回读，供后续拍摄使用；不修改当前或已有照片 |
| FireWire / CameraBattery | 仅 powersourceselection=support 时可请求；Ack 不代表已验证当前供电来源，需实际观察后背 |

EnableTakenPicture 已移到实验室；尚未证明 no 能停止通知，主界面不提供未经证实的暂停开关。它与手机“跟随新片”、手动释放机械快门分别记录。

## 实验室手动验证

从 **“后背 / Back”页顶部 → “打开实验室”** 进入全屏实验台。这里提供可主动验证的原厂调用、固件候选和用户假设；范围或语义未确认的项仍标实验，不当作已验证标准功能。打开页面、选择模板和生成请求都不会自动发送。

1. **记录设备环境。** 写下准确后背型号、固件、手机系统与应用版本，等待普通操作空闲再实验。实验期间会暂停普通拍后读图与配置刷新。
2. **选择模板或原始控制头。** 16 个 WiView 模板覆盖 Brightness、CurrentTime、ResetCounter、PIN、两版 CEVersion、FactorySettings、qformat / recovery、XMP-only / annotation+XMP、EnableTakenPicture、Ack / ReSyncRequest，以及 GetCameraParam / 单属性 SetCameraParam 探测。查看证据、影响和未解问题，手填未知参数后生成请求；没有默认 PIN、亮度量程或存储根。
3. **设定匹配与超时。** 支持单个编码后不超过 60 KiB 的控制头，不发送二进制请求附件，也不拼接多条命令。超时 1–300 秒为手机端设置。可指定 Ack、Sync 或其他合法回应标签，另设精确 file_name 和是否必须有附件。`*` 观察首个完整帧，可能是异步通知，不证明由本请求引起；空标签只发送。后两种模式不能同时设文件名 / 附件匹配。
4. **检查目标与完整请求再发送。** “检查待发送请求”会展示后背和请求，已知敏感字段遮蔽；写类仍需明确确认，清除存储另需确认。模板限制检查格式，不证明硬件合法量程或实际影响。用户原始请求不会继承所有标准配置 / 照片操作校验。
5. **分别查看收到、错误、取消或仅发送。** 原始响应头、Error、非零 Ack、超时与未匹配帧都保留，不套用标准功能的成功判定。取消通过断开结束等待，不保证撤销后背已经收到的写入。
6. **导出后再结束实验。** 已发实验后退出会断开连接，防迟到回复进入普通会话；重新连接、同步和读取预览后再用标准功能。

### 固件研究条目与用户假设

| 条目 | 可以验证什么 |
| --- | --- |
| Gain 读取、Sync 增益字段 | 经典 4.701 已证 parser → 回复链，返回 UserGainStorage / UserGainList；列表含义、单位和型号差异待测 |
| UserGain 写入 | 已有固件处理入口；接受值、校准含义、持久性及回读语义待验证，未知值由用户填写 |
| ManualLens | 仅共享 5.041 发现入口，经典 4.701 未见；不默认 0 / 1，不称机械快门控制 |
| GetImage FP / fp | 经典 parser 识别特殊 zoom；实际画幅 / 分辨率待测，不称 MOS 原片或连续取景 |
| FactoryGain / BootRequest / GetFile / LiveView | 可点击“建立假设探测”，生成明确标为 **USER HYPOTHESIS** 的候选 GetParamList 或通用空头，默认 `*` 观察；这不是已提取的厂商无线语法 |
| 内部校准 | 只有内部类、没有已知无线 tag 时不自动生成命令；用户仍可在原始控制台明确输入单头，结果按假设记录 |

Brightness、ResetCounter、PIN、FactorySettings、格式化与 recovery 的模板保留原厂构造或固件证据；未知量程、作用范围、恢复能力和副作用不会因模板存在变成已确认事实。实验室不自动遍历候选、不做 fuzz，也不自动刷固件。完整分层见 [功能缺口对照](feature-gap-matrix.md) 和 [固件蓝牙调查](firmware-bluetooth-survey.md)。

### 附件、记录与脱敏导出

手机单附件限额为 16 / 64 MiB，在断开时修改、重连生效；不是后背能力规格。收到的二进制保存为 `.bin`，记录实际长度、SHA-256 和前 256 字节 hex。最多保留 30 条实验记录，附件缓存预算 256 MiB。

导出 ZIP 包含完整脱敏请求 / 响应头、非敏感 header hex、原始二进制附件、transport 日志和未匹配帧。NewPinCode / PinCode / Pin、Annotation、Copyright、XMP_tag 等已知头字段默认 mask；含这些字段的原始 header hex 不导出，实际发送字节不改变。**二进制附件保持原始字节，不自动脱敏**；路径及未知字段也可能包含个人信息，分享前检查附件与记录。

## 照片信息、备注与文件

信息页显示收到的预览尺寸、大小、SHA-256 和已取得的属性；连接时可读取后背照片信息和已解码 XMP。标准解析使用已确认的头属性，未经证实的附件不猜作 XMP，不把解码失败显示为完整信息。实验室另提供整份 XMP / annotation+XMP 写入模板，沿用 Base64 / XML 检查；手工提供完整包不等于应用已验证所有字段语义或写后效果。

保存本机备注只写手机。写入后背备注使用 `SetPictureParams`，须匹配成功回复；非零或无法解释的错误码不能算成功，之后仍需回读核对。引号、控制字符及 `& < > ^` 暂不允许发送，避免使用未经验证的转义规则。

协议默认 Windows-1252，可断开后选择 GBK 或 UTF-8 供实测。ASCII 路径不受影响；无法编码的文本拒绝发送，不替换为问号。

即使照片名为 `.mos`，GetImage 预览也不会被当作完整原片。完整 MOS 下载和连续实时取景尚未实现，当前机械快门也没有遥控执行器。

## 恢复与诊断

配对失效或 PIN 错误时，在 Android 蓝牙设置忘记旧配对并重配，同时确认没有其他客户端占用。仅在旧固件需要时，断开后尝试不安全 RFCOMM；该选项不能保证绕过 Android 的链路要求。

连接等待约 35 秒，普通指令约 20 秒，图像约 60 秒；这些是主机配置，不是后背实测规格。超时关闭会话后需手动重连，避免迟到回复与下一张照片错配。

设置中可查看 / 导出日志。应用不记录系统配对 PIN，日志仍可能包含路径、相机属性和用户元数据，分享前检查。问题报告请附版本、手机系统、准确后背型号 / 固件、步骤与脱敏日志；不要公开签名材料或私人照片。

## 构建

完整 JDK 21、SDK Platform 36、Build Tools 36.0.0；JVM 目标 17。依赖与 Wrapper 校验值固定。Android Studio 的 Gradle JDK 也应选 21。

```powershell
.\tools\build-android.ps1 -Jdk 'C:\path\to\jdk-21' -Sdk "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

构建脚本运行协议测试、Lint 和 APK 构建，并输出到 `dist/`。最后一条需要设备或模拟器。安装 `system-images;android-36;default;x86_64` 后，可运行 `tools/start-test-emulator.ps1`。

release 可由本地准备脚本建立仓库外的签名配置，或设置 `LEAF2ANDROID_KEYSTORE_PATH`、`LEAF2ANDROID_STORE_PASSWORD`、`LEAF2ANDROID_KEY_ALIAS`、`LEAF2ANDROID_KEY_PASSWORD`：

```powershell
.\tools\prepare-release-signing.ps1
.\tools\build-android.ps1 -Release -Jdk 'C:\path\to\jdk-21'
# 标准 Gradle 任务：.\gradlew.bat :app:assembleRelease
```

参考：[蓝牙权限](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)、[RFCOMM](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)、[前台服务](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)、[应用语言](https://developer.android.com/guide/topics/resources/app-languages)、[Android 16 布局](https://developer.android.com/about/versions/16/behavior-changes-16)。
