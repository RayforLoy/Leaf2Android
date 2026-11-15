# Leaf2Android 0.3.2 安装、使用与构建

[English](quick-start.en.md) · [项目首页](../README.md) · [功能支持表](implementation-status.md) · [完整缺口对照](feature-gap-matrix.md)

## 选择安装包

**0.3.2（versionCode 5）已发布为预发布版本；更新于 2026-11-15。** 从 [v0.3.2 页面](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.3.2) 下载附件，或使用本地 `dist/`；变更见 [本版说明](releases/v0.3.2.md)。普通通知修正和点选灰平衡尚待用户新版实机复测。

| 文件 | 用途 |
| --- | --- |
| `Leaf2Android-0.3.2.apk` | 发布密钥签名的 release 测试应用 |
| `Leaf2Android-0.3.2-debug.apk` | 开发调试包，签名可能与 release 不同 |
| `Leaf2Android-0.3.2-source.zip` | 可构建源码与文档 |
| 参考资料压缩包 | 原版 WiView、静态分析与来源记录，不作为 APK 内嵌运行库 |
| `*.sha256` / 校验清单 | 对应附件的 SHA-256 |

附件以对应发布页为准。应用 ID 为 `com.leaf2android`，最低 Android 12 / API 31，编译与目标版本 Android 16 / API 36。该预发布不代表 Aptus / Valeo 全系列兼容已验收。

## 安装与签名

将 APK 传到手机，在文件管理器中打开安装，根据 Android 提示允许当前来源安装应用。连接开发环境后也可执行：

```powershell
adb install -r .\dist\Leaf2Android-0.3.2.apk
```

同应用 ID 的覆盖更新还要求签名一致。若从 debug 切换 release 时出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先保存需要的预览并另行记录本机备注 / 标记，再卸载旧包后安装。卸载会删除应用私有数据。此前误名测试包与 `com.leaf2android` 是不同应用，不会自动迁移数据。

发布者须保留 release keystore；私钥、密码和个人 `local.properties` 不提交仓库。APK 不包含原厂 EXE、DLL 或 CAB。

## 语言与风格

首次启动使用现代界面，语言跟随系统。设置中可选择跟随系统、简体中文或 English；风格可独立选择现代或经典 PDA。两者使用共享照片与会话仓库，保留放大位置。

语言只控制界面文案，不翻译设备实际名称、原始照片路径、用户备注或后背元数据。后背协议编码另行选择，不能用“界面切换英文”推断无线编码已经改变。

## 预览缓存与升级

预览缓存默认 **1024 MiB**，可在设置中选择 64 / 256 / 1024 MiB。首次升级到本轮规则时，旧存储值 256 MiB 会迁为 1024 MiB，原有 64 MiB 保留；迁移只执行一次。之后手动改回 256 MiB 或其他值，不会在下次启动被再次改成 1024。它只影响已收到的预览，不会增加后背图像分辨率，也不是 MOS 原片容量。实验室附件的 256 MiB 缓存预算保持独立。

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

2026-11-15 用户反馈 v0.3.1 的 Valeo 连接非常稳定，ISO、灰平衡等拍摄设置可用；仍有自动预览 `Error error_code="0x01"`，但状态保持 READY，手动读取新图正常。11-14 的设置超时属于历史反馈；本版通知修正不等于已在用户设备证明解决，完整环境和连拍记录仍待补充。见 [设备记录](implementation-status.md)。

经典 Aptus **4.701** 已作为独立主证据复核，含 Aptus 22 模型与 9 类 PDA 命令，见 [固件蓝牙调查](firmware-bluetooth-survey.md)。该镜像不是 Aptus 22 专用可刷包，不推定用户实际安装版本。5.041 配置只在设备 Sync 返回对应结构时使用。

## 自动预览收到拒绝时

普通 PictureTaken / ImageChange 等异步通知不自动回复 Ack；Stop 才有专属确认。对原版客户端和 4.701 / 5.041 固件的 [静态复核](capture-notifications-analysis.md) 表明，多余 Ack 可能被当作未知命令返回 0x01，且该 Error 可能被正在等待的 GetImage 接收。它是候选解释，不是用户无线抓包；0x01 不能叫 busy。

本版移除普通通知及实验室后台处理中的自动 Ack，不引入额外 parametersReady 硬门控或盲重试，也不改用户已报告稳定的拍摄配置。完整 Error 拒绝与传输超时分开：READY 时可按明确文件路径手动刷新；超时仍关闭会话，须手动重连。错误会标明请求 tag / 属性名，不含请求值，回复头按已知敏感字段脱敏。错误码接受 DWORD 十进制或 0x / 0X 十六进制，0x00 为零，未知格式保守拒绝。

## 从“后背”页设置拍摄参数

连接后打开底栏 **“后背 / Back”** 页，刷新 Sync，检查当前配置和设备给出的选项。控件依后背实际方言、完整配置和允许值开放；没有某个控件不等于同系列所有固件都不支持。不要把 ISO 选项列表第一项当成当前值，也不要按其他型号的档位猜测输入。

- **WiView legacy：** 当前 ISO 位于与 `DefaultShootSetup` 匹配的完整 `ShootSetup` 中。可选择已有配置或编辑当前项，只改变指定字段并保留其余原值；它不是通用独立 ISO 写法。DP-67 的较小格式见下节。
- **5.041 方言：** 识别到对应 `Sync.CameraSettings` 后，可独立写 ISO、Orientation、CameraType、SensorCrop、FileCompression、Prefix 和 ShootingFolder。SensorCrop 只接受设备下发的纯名称列表，压缩只接受设备下发的 `None` / `Lossless`，不混用 legacy 的 Nearly Lossless。
- ISO 档位来自设备，不硬编码。写入前重新读取当前状态，防止用较早的整份配置覆盖后背上的新改动；写入后再次 Sync，核对整份预期配置、未改字段和必要的全局值。只看到 ISO 变化不能代替完整核对。
- 缺少唯一完整配置、允许项不明确、字符无法安全编码或回读不一致时，停止或报告未确认，不用默认值补齐缺失字段。

WiView legacy 完整配置包括 ISO、画质、取景框、色调、Light、锐化、输入 / 输出 ICC、机身类型、ICCFlag、文件名前缀、拍摄目录、普通 / Nearly Lossless 压缩及 NLCompression、方向和 Color；此列表不作为 DP-67 的字段列表。Light 使用设备光源选项，不能任填 Kelvin / tint；选择已有配置不是恢复 FactorySettings。

5.041 独立 Prefix 编辑最多 23 个 UTF-16 代码单元，且新值的 UTF-8 字节长度不能超过当前已知前缀；这是保守实现限制，不是原厂硬件长度规格。拍摄目录须存在，不会用设置目录的操作隐式创建目录。

## 选择 DP-67／WiView 兼容路径

断开后，在设置的 **“拍摄配置兼容模式”** 选择：

| 选项 | 行为 |
| --- | --- |
| 自动（AUTO） | 蓝牙名称含 Valeo，且 Sync 下发 shootsetup 时启用 DP-67；否则保留 WiView 路径 |
| WiView / Aptus（WIVIEW） | 显式使用 WiView 拍摄配置路径；5.041 独立设置仍须实际 Sync 结构支持 |
| DP-67 / Valeo（DP67） | 显式使用 DP-67 小字段集；适合名称已被改过而 AUTO 未匹配的后背，缺完整配置仍拒写 |

此项改变拍摄配置写入格式，不更改蓝牙配对、SPP 服务或后背硬件，也不修改 CEVersion。AUTO 是可复核的软件选择规则，不是已验证的全型号识别。DP-67 刷新控制页只请求 Sync 与 CurrentTime，不自动追加其他 getter；需要研究的读取可在实验室显式发送。

DP-67 完整提交的 **12 个核心字段**为 SetupName、ShootingFolder、ISO、ImageQuality、Framing、Toning、Light、SharpTable、InputProfile、OutputProfile、CameraType、ICCFlag。只有当前配置已提供 Prefix 时才保留该字段，不为缺少的字段造空值。使用后背允许项，保留未编辑的现有值；配置不完整、状态已变或值不能安全编码时停止写入。

**DP-67 不附带 WiView 的 FileCompression / NLCompression、Orientation 或 Color，拍摄配置面板隐藏这些写字段。** 它们仍可由用户在实验室明确探测，不等于本轮已验证支持。

编辑原预设会提交到 `原名.1` 修改槽；沿用原 DP-67 的名称判定，名称已包含 `.1` 时保留现有修改槽。确认框会显示将写入的槽位，并提示同名槽位已存在时会被更新。提交后必须读回目标值、新 active 名称及原预设未被改写；不把修改槽更新称为备份或可自动撤销。

选择原预设只发 DefaultShootSetup；选择已有 `.1` 修改配置则发完整 DP-67 CSV。预同步、设置与回读作为一整个操作排队，自动 GetImage 不插入其中；0.3.2 不为普通通知自动发 Ack，只保留 Stop 专属确认。原版 DP-67 的 12 秒×3 重发策略没有照搬，本应用写入不自动重试，超时仍断流。静态依据与包来源见 [DP-67 分析](dp67-analysis.md)。

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

在照片主界面点“照片操作”，可打开后背标记、后背备注、复制 / 移动和删除；本机星标与本机备注仍独立。目录的新建、空目录删除和目录改名保留在“后背 / Back”页。没有任意单张 MOS 文件改名功能。

1. 先刷新目录并选中准确照片；复制 / 移动还须选择后背上的目标目录。
2. 检查设备名称、完整源路径和目标路径，不覆盖已有目标。普通刷新不会删除文件。
3. 删除会经过二次确认，显示实际后背和完整照片路径；确认前检查是否选中正确照片。
4. 只有目录回读证实照片已消失，才移除手机源列表和对应缓存；若删除当前照片，有邻片则选择邻片。请求拒绝或回读失败时保留本地记录，重新读目录确认后背实际状态。
5. 移动须回读确认源消失且目标存在，之后才移除源记录 / 缓存并读取目标预览；不把仅收到 Ack 当作成功。复制保留源。

这些存储操作仍待用户硬件验证。删除会改变后背存储，实验室 recovery 不是已验证的撤销保证；失败也不证明后背完全未执行，继续操作前先核对实际目录。

## 后背处理预览、灰平衡与曝光检查

照片主界面的“后背图像工具”可直接打开处理预览、灰平衡、像素拾取与后背直方图；“后背 / Back”页入口也保留。用户报告的 v0.3.1 灰平衡拍摄设置可用，不等于本版点选取样或写当前照片已验收。

1. **处理预览：** 选择原厂 `1 / 2 / 4 / 8` 缩放，可选亮、暗或两者 Alarm。收到的是后背处理的 JPEG，与本机双指缩放 / JPEG 警示分开。
2. **点选白 / 灰区：** 显式进入灰平衡点选模式，再点希望中性的区域。双指和双击仍用于缩放；点到留白或图像外边界不会发送请求。
3. **自动坐标有前提：** 当前主预览、同照片元数据的明确方向、`Sync.PictureDimensions`、预览 SHA 绑定和同比例证据必须一致。Landscape 使用参考尺寸的 max×min，Portrait CW / CCW 使用 min×max；Auto、未知方向或矛盾尺寸不发送自动点选请求，仍可手输坐标。坐标属于后背参考网格，不能称为 MOS / RAW 传感器像素。
4. **查看后背取样：** App 请求处理预览并显示后背返回的 Light，不在手机上按 JPEG 编造 Kelvin / tint。取样本身不提交；分别确认后才写当前照片或用于后续拍摄，恢复预览是另一个操作。
5. **保持取样来源：** 每次刷新预览都会清除旧照片元数据 / 取样；重新读取必要信息后再点。取样点、Light、照片 SHA 和本次连接严格关联，重连、同名覆盖或换图后不能复用旧点。
6. **后续拍摄：** 使用完整 legacy ShootSetup.Light / 5.041 ImageSettings.Light。只有本连接当前后背返回、仍关联当前照片的取样可绕过静态光源列表；仍拒 CSV 分隔符，保留其他配置字段并 Sync 回读。任意手填 Light 不享有该例外。
7. **曝光拾取与直方图：** ImageFunction 的 fstops 是图像坐标，不是光圈；后背直方图按回复桶数显示，RAW / JPEG 来源未确认。与来源明确的本机 JPEG 直方图区分。

当前照片灰平衡的 Ack 表示请求得到确认，真实色彩变化仍须重新读图检查。点选映射、处理效果和其他新入口需实机复测；不等同连续实时取景或完整 MOS 传输。

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

Ack 模板仅保留为明确的人工实验，不是普通通知的要求；被核对 PDA 接口可能将其作为未知命令拒绝。实验室后台也不自动为普通通知发 Ack。

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

本轮超时诊断摘要会指出未获回复的请求 tag 和属性名，不含属性值；结合阶段日志区分预同步、设置 Ack 或回读。它比通用“断开”提示更具体，仍不能单独判断后背没发回复、解析失败还是更早的物理失联。

自动 CameraShootSetupChangeNotification 刷新现在保留最近手动操作的 error / notice，避免后台 Sync 擦掉成功或回读不一致结果；新手动操作才清除旧提示。这修正的是 App 显示／验证结果被覆盖的问题，不是已证实的后背掉线根因。

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
