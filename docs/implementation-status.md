# 功能支持与实机验收

[English](implementation-status.en.md) · [项目首页](../README.md) · [完整缺口对照](feature-gap-matrix.md)

更新：2026-11-14。**0.3.1（versionCode 4）实验性预发布**；应用 ID `com.leaf2android`。下列变更已接入代码，真实 Valeo 设置超时的候选修正仍待设备复测；最终构建和测试以本轮报告为准。

## 范围与证据

应用显示的产品系列名称为 Aptus / Valeo。用户已纠正 Aptus II 本身没有蓝牙，**不作为当前无线目标**；原始 Galaxy Z Fold7 / Android 16 / One UI 8.5 + Aptus II 5 + 技术相机计划只作历史记录，不再列为“蓝牙待验收”。手机与机械快门的验收分别记录；系列名称不证明所有型号、固件、Wireless 选件或链路均兼容。

经典 Aptus 4.701 的独立复核已定位 Aptus 22 模型、9 类 PDA 命令、完整 ShootSetup / DefaultShootSetup、Marking 和 GetImage 扩展，见 [固件蓝牙调查](firmware-bluetooth-survey.md)。该共享镜像不是 Aptus 22 专用刷写包，也未证明安装在用户设备上。5.041 的 PDAInterface 同样只能证明共享代码，不能证明 Aptus II 具有蓝牙硬件。软件方言、静态调用链与真实无线效果分别记录。

早期按用户要求，以后背无线正常为开发假设先完成程序和发布包。2026-11-11 已收到 Valeo 22Wi、Aptus 22 的实际连接与读图用户回报，项目不再是完全没有设备使用反馈的状态。该回报仍不等于全部协议字段、通知时序、参数操作、原片传输或连续取景已确认。最终用途仍为手机直接连接后背，不依赖电脑中转或桥接设备。

## 用户报告的硬件记录

| 型号 | 报告日期 | 蓝牙连接 | 读取照片 | 证据范围 |
| --- | --- | --- | --- | --- |
| Valeo 22Wi | 2026-11-11 | 用户报告正常 | 用户报告正常 | 本次用户使用回报；未附固件、手机 / 系统、应用版本和日志 |
| Aptus 22 | 2026-11-11 | 用户报告正常 | 用户报告正常 | 本次用户使用回报；未附固件、手机 / 系统、应用版本和日志 |
| Aptus II（含早期提及的 II 5） | 2026-11-11 | 非蓝牙目标 | 不适用无线读图 | 用户纠正该系列本身无蓝牙；共享固件代码不作为硬件支持证据 |

“用户报告正常”是型号级使用记录，不冒充维护者独立复测或完整验收。报告没有确认至少 20 张连拍、照片对应关系、目录细项、ISO 等参数读取 / 写入、后背标记、备注回读、锁屏 / 后台、原片或实时取景。也没有把测试手机认定为原先计划的 Z Fold7，更不能推定 Aptus II 5 或整个 Aptus / Valeo 系列已通过。

### 2026-11-14：设置超时事件

用户补充 Valeo 读图正常、修改设置才断开，未说明具体字段；截图是 App 等待应答超时后主动关闭会话。缺 Ack、解析 / 匹配、自动取图互插或更早的物理失联尚未由截图区分。0.3.1 增加完整操作互斥、请求摘要和独立 DP-67 写入路径，**不将这些变更写成已解决真实设备问题**。

离线测试另发现 CameraShootSetupChangeNotification 引发的后台 Sync 会清掉用户刚得到的成功或回读不一致结果。代码已改为自动刷新保留 error / notice，新手动操作才清除；这是 App 显示／验证结果被覆盖的问题，不是已证实的后背断线根因。网络证据与未确认项见 [独立调查](valeo-connection-research.md)。

## 功能表

以下记录已有行为、本轮按证据接入的标准功能及实验室入口。新增参数、图像处理、标记、存储和会话控制尚未由用户在 Valeo 22Wi 或 Aptus 22 上验证。实验室提供候选探测和证据记录，不把未解释的回复认定为功能生效。

| 功能 | 本版代码 | 设备验收 |
| --- | --- | --- |
| 发现、配对、SPP | 公开 API，安全与可选不安全 RFCOMM | Valeo 22Wi、Aptus 22 连接已有用户成功回报；PIN、安全模式、手机 / 系统和固件明细待补 |
| 同步与目录 | RequestSync → CurrentTime → dir；失败不报就绪 | 两型号可读图已有用户回报；完整握手、目录字段与日期语法未单独验收 |
| 拍摄兼容模式 | 离线选择 AUTO / WIVIEW / DP67；AUTO 要求蓝牙名含 Valeo 且 Sync 有 shootsetup | 只改变拍摄配置格式，不改配对 / CEVersion；改名设备可显式选模式，非硬件能力判定 |
| WiView legacy / ISO | 完整 ShootSetup 和设备选项；保留未编辑值后全量提交 | 不把其压缩、方向、Color 扩展带入 DP-67；写前 / 后 Sync，效果待测 |
| DP-67 / ISO | 12 核心字段，当前已有 Prefix 才保留；原预设编辑至 .1 修改槽 | 不写压缩、Orientation、Color；确认槽位更新，核对 new active、目标值与原预设未变；真实效果待测 |
| 已有拍摄配置选择 | DP-67 原预设只 DefaultShootSetup；已有修改配置提交完整 CSV | 不等于 FactorySettings；沿用 contains(".1") 名称判定，需回读当前项 |
| 整操作互斥与提示 | operationLock 覆盖预 Sync、set、回读；自动 GetImage 不插入，通知 Ack 绕过锁；自动刷新保留操作结果 | 不自动重试写入；超时仍断流，新摘要仅列 tag / 属性名；真实 Valeo 超时仍待复测 |
| 5.041 相机设置 | 按已识别的 Sync.CameraSettings 独立写 ISO、Orientation、CameraType、SensorCrop、FileCompression、Prefix、ShootingFolder | 值由设备下发；SensorCrop 仅纯名称列表，压缩仅 None / Lossless，前缀限制见下文；全量回读，硬件效果待验证 |
| 5.041 图像处理配置 | 完整 ImageSettings 编辑、已有 ActiveImageSettings 选择；保留未改字段 | 包含 Grain / MoireReduction / Saturation，仅设备 Sync 下发允许项时可编辑；写前重读、写后全量核对；效果待验证 |
| 拍后自动显示 | 拍摄通知、文件名核对、附件长度、串行队列 | 至少 20 张真实拍摄对应关系待测 |
| JPEG 缓存与回放 | 原字节、SHA-256，默认 1024 MiB，可选 64 / 256 / 1024 | 旧 256 首次迁 1024，旧 64 保留，之后手选不再重置；实际字节、分辨率与速度待测 |
| 放大与曝光检查 | 双指 / 双击、JPEG 直方图及明暗警示 | 实拍细节范围待核对；不使用 RAW 像素 |
| 图像信息与 XMP | GetPictureParams 属性、严格 Base64 / UTF-8 XMP 解码，保留无关字段；标记仅改 Flags 第三位 | 无 xmp_tag 头属性不合成空包；损坏或不安全 XMP 拒绝改写；实际属性、单位待测 |
| 星标、本机备注 | 本机持久保存，明确标为本机操作 | 不依赖后背 |
| 后背备注 | SetPictureParams，匹配成功回复且无错误码后才确认 | 写入 / 回读效果待测 |
| 后背 Good / Unmarked | 与手机星标分开；有 XMP 时保留并只改 Flags 第三字符；Sync 有 shootsetup / camerasettings 时允许有经典 4.701 / 5.041 证据的 Marking-only | 未识别方言且无 XMP 则标准路径拒写；本连接刷新预览并回读标记；硬件效果未验证 |
| 后背存储管理 | 新建目录、空目录删除、目录改名、照片删除、内部 copy / move；明确确认、不覆盖、目录回读 | 新增操作未实机验证；不是向手机下载完整 MOS |
| 后背 zoom / Alarm | 已实现原厂 1 / 2 / 4 / 8 倍处理预览，Alarm 支持亮 / 暗 / 两者，接收 JPEG | 与本机缩放 / JPEG 警示分开；手填原图协议坐标，不猜 Compose 映射；效果待测 |
| 灰平衡取样、确认、恢复 | 已实现取样预览、显示返回 Light；分别确认写当前照片或用于后续拍摄，另可恢复预览 | 后续拍摄使用完整 legacy ShootSetup.Light / 5.041 ImageSettings.Light 并 Sync 核对；不开放任意 Kelvin / tint；效果待测 |
| 后背像素 / 曝光拾取 | 已实现 ImageFunction 坐标请求和 histogramlocation / histogramvalue 显示 | fstops 在此为坐标，不是光圈；标注来自后背且 RAW / JPEG 来源未确认；效果待测 |
| 后背直方图 | 已实现 histogram / r / g / b 的任意桶数解析与显示 | 标明来自后背，桶数按回复；不假定 RAW 来源或固定 256 桶；效果待测 |
| 后背时间同步 | 将手机本地时间写入 CurrentTime，再单独读取核对 | 不把手机时区协议化为未证实能力；真实时间与拍摄日期待测 |
| 后续拍摄默认备注 | 默认 Annotation / Copyright 读取、写入并回读 | 与当前照片的备注分开；不回写既有照片；效果待测 |
| EnableTakenPicture 实验 | 已移入实验室的 yes / no 模板，记录请求与原回复 | 尚未证明 no 停发通知，主界面不提供未经证实的暂停按钮；不是释放快门 |
| 电源来源请求 | FireWire / CameraBattery，仅 powersourceselection=support 时开放 | 仅标 Ack 已收到请求，不宣称已确认供电状态；效果待测 |
| 后背控制页 | 底栏“后背 / Back”集中提供上述设置与会话操作 | 控件依当前方言、当前值、完整配置和设备允许项开放 |
| 实验室模板与候选 | Back 页顶部打开；16 个客户端模板、固件研究条目、单原始头命令 | 未知参数手填，确认目标与请求后发送；范围、语义与无线效果仍待验证 |
| 实验观察与导出 | 收到 / 错误 / 取消 / 仅发送分别记录；原始附件 .bin、长度 / SHA-256 / 前 256 B hex，ZIP 导出 | 不把非零 Ack 转成普通成功；脱敏已知头字段，原始二进制附件不改写 |
| 现代 / 经典 PDA | 共用会话、照片、放大与显示选项 | 内外屏、旋转和 One UI 分屏待测 |
| 中文 / English | 默认跟随系统，也可手选；与风格独立 | 设备系统语言、切换 / 重建及后台通知需按报告核对 |
| 前台会话 | connectedDevice 服务、通知断开、权限失效和断线处理 | 锁屏、后台、长时间拍摄待测 |
| MOS 原片 | 未实现完整文件读取 | 获得依据后比对 CF 原文件 |
| 连续实时取景 | 未实现 | 无已确认连续传感器接口 |
| 遥控机械快门 | 当前组合硬件不支持 | 没有快门执行器，手动释放 |

该预发布不等于已完成原 PDA 的完整替代验收。

## 配置与方言边界

WiView legacy 的完整 ShootSetup 包括 ISO、ImageQuality、Framing、Toning、Light、SharpTable、InputProfile、OutputProfile、CameraType、ICCFlag、Prefix、ShootingFolder、FileCompression / NLCompression、Orientation、Color。DP-67 使用独立字段集：SetupName、ShootingFolder、ISO、ImageQuality、Framing、Toning、Light、SharpTable、InputProfile、OutputProfile、CameraType、ICCFlag；Prefix 仅在当前配置存在时保留。缺核心字段或未提供允许值时不补猜测，ICCFlag 仍按已证 0 / 1。

legacy ISO 位于完整配置；5.041 CameraSettings 独立路径仅在对应结构存在且当前非 DP-67 时使用。DP-67 不附 WiView 压缩和全局 Orientation / Color，也不改 CEVersion；标准面板隐藏这些字段，实验室仍能手动探测。DP-67 控制刷新只发 Sync + CurrentTime，避免自动追加尚不明确的 getter。

编辑原 DP-67 预设时，名称不含 `.1` 就克隆至 `原名.1`，含 `.1` 则保留现有修改槽；确认界面显示目的槽位和已有则更新。提交前刷新并核对当前配置，提交后确认新 active、应写值、可见未改字段／全局值及原预设保持。不复制原程序的 12 秒×3 重发；超时保留断流，须手动重连，不自动重试。字段顺序、clone 与包装依据见 [DP-67 静态分析](dp67-analysis.md)。

同一用户操作的预同步、写入和回读共享 operationLock，自动图像队列不能插入；通知 Ack 不等待此锁。该锁管理手机端排队，不承诺锁住后背或其他客户端。超时请求摘要列 tag 与属性名、不列值，以区分写 Ack 和回读等待；它不是物理掉线原因判断。

5.041 的相机设置与 ImageSettings 是两种结构，ISO 不属于 ImageSettings。图像处理配置包括 ImageQuality、Color、Framing、Toning、Light、SharpTable、InputProfile、OutputProfile、ICCFlag、Grain、MoireReduction、Saturation；完整结构不齐或没有设备允许值时不猜值。独立 SensorCrop 只接受纯名称列表，不猜复合裁切记录。独立压缩仅采用下发的 None / Lossless，不混入 legacy 的 Nearly Lossless。独立 Prefix 最多 23 个 UTF-16 代码单元，且不增加当前已知前缀的 UTF-8 字节长度；这是实现限制，不宣称是原厂硬件上限。拍摄目录写前核对目录存在。

标准照片标记、备注、灰平衡和照片存储操作要求预览属于当前后背，且在本次连接中重新读取。连接 generation 不持久化：重连、升级或进程重启后须刷新预览才能标准写入。XMP 从 xmp_tag 头属性读取，保留其他字段和 Flags 字符，只定向更改第三个字符。实验室另有整份 XMP / annotation+XMP 模板及原始控制台，按用户填写目标执行，不继承标准操作的全部语义和照片来源检查。

灰平衡用于后续拍摄有独立确认入口。只有本连接当前后背返回、且仍关联当前照片的取样 Light 才可通过该路径写入，不受静态光源候选列表限制；仍拒绝 CSV 分隔符，保留整份配置的其他字段并在提交后 Sync 回读。任意手填 Light 不能借此绕过允许值检查。

格式化、recovery、FactorySettings、Brightness、ResetCounter 和 PIN 等已提供实验室模板；未知量程、作用范围、计数语义和 Android 配对恢复仍待验证，不列为已验证的标准设置。读到 ShutterSpeed 不意味着可以设置机械快门速度或光圈。当前分层与未解析原因见 [功能缺口对照](feature-gap-matrix.md)；原始 [客户端协议缺口审计](protocol-gap-audit.md) 以 0.2.0 为基线。

## 实验室观察边界

实验室支持编码后不超过 60 KiB 的单控制头与 1–300 秒超时；可设置回应标签、精确 file_name 和附件匹配。`*` 只观察首个完整帧，不证明与请求有因果关系；空标签仅发送。原回复、非零 Ack、Error 和超时分别保留，不套标准操作的成功判定。实验期间暂停普通拍后读图 / 配置刷新；发送过实验后退出会断开，取消也通过断开终止等待，不保证撤销后背已收到的写入。

手机单附件限额可在断开时选 16 / 64 MiB，重连生效。记录最多 30 条，附件缓存预算 256 MiB。导出 ZIP 包含脱敏完整头、非敏感 header hex、原始 .bin、日志和未匹配帧。PIN、备注、版权和 XMP 头字段默认 mask，实际发送字节不变；二进制附件仍是原始内容，分享前检查。操作步骤见 [实验室指南](quick-start.md#实验室手动验证)。

Gain / UserGain / ManualLens / GetImage FP 等按固件证据等级列为候选。GetFile / LiveView / BootRequest / FactoryGain 可由用户点“建立假设探测”，生成明确标为 USER HYPOTHESIS 的空头或候选 GetParamList，默认观察首帧；这些不是从固件提取的已证语法。内部校准没有已知 tag 时不自动造命令，原始控制台仍允许用户明确填写单头。实验室不自动 fuzz、不盲刷固件。

## 固件资料

本轮将 `D:\leaf` 的固件参考资料按内容去重收录 89 份唯一原始文件（约 83.7 MB）和核心重建 / 分析，具体来源、型号、大小、哈希以 `reference/firmware` 清单为准。个人 MOS、校准文件和约 113 MB 安装器不纳入发布资料。收录与静态解析不等于手机刷写支持，也不等于用户两台后背已安装这些固件。

DP-67 用户包只收录原始 Setup.exe、提取 CAB 及研究记录，含私人 PBF 的完整 ZIP 排除；不把安装包分析当成真实后背复测，见 [包分析](dp67-analysis.md)。

## 离线与模拟器验证

当前测试计数、APK 校验、Lint、签名及对齐结果由 [verification-status.json](../research/reports/verification-status.json) 和 [apk-verification.json](../research/reports/apk-verification.json) 记录。上述用户硬件回报与工具执行的测试属于不同证据类型；不要把用户回报改写成维护者已复测，也不要把旧报告、旧 APK 或旧测试计数当作本次发布结果。

本轮协议与 API 36 模拟器测试覆盖真实界面选择 ISO、确认和完整配置回读、语言切换、Android XMP DOM 冒烟、拒写、回读不一致、未改字段意外重置、ISO 通知不污染候选及部分 move 检测。Python、协议与 Android 检查均已通过，具体结果见上述同版本机器报告；签名覆盖升级、缓存迁移和显式 DP67 跨重启 / 语言检查见 [cache-upgrade-verification.json](../research/reports/cache-upgrade-verification.json)。这些离线结果不代表真实 Valeo 设置超时已解决。

协议测试包含逐字节拆分、合包、二进制分隔符、重复属性、异常长度、截断、重置、串行请求、通知穿插、超时关闭和错误回复。仓库测试通过模拟传输接收实际可解码的合成 JPEG，检查文件名、尺寸、SHA-256 和缓存字节。界面测试检查风格、语言和状态保持。

合成图、模拟后背和模拟窗口尺寸不能替代真实蓝牙、物理折叠或 One UI 实测，也不产生真实画质、刷新率或原片传输结论。

## 实机验收模板

| 项目 | 要记录的事实 |
| --- | --- |
| 手机 | 完整型号、Android、One UI、安全更新、应用版本 |
| 后背 | 准确型号、固件、Wireless 菜单、蓝牙名称与 SDP 服务 |
| 配对 | 首次配对、错误 PIN、遗忘 / 失效后的恢复 |
| 会话 | 重启后背、超时、断线、重新连接、锁屏与后台 |
| 拍摄 | 至少 20 张，内容、名称、顺序及自动预览逐张对应 |
| 配置 / ISO | 记录 AUTO / WIVIEW / DP67、原值、设备允许值、完整请求摘要与回读；DP-67 核对 .1 目标、新 active、原预设未变和已有槽位确认；超时不算成功 |
| 操作与后台事件 | 同时到达的拍摄 / 配置通知不能使 GetImage 插入预同步 / set / 回读；通知 Ack 仍及时；自动刷新不清除成功或不一致结果 |
| 缓存迁移 | 旧 256 → 1024、旧 64 保留；迁移后改回 256 并重启不再被重置；与实验室附件预算分开 |
| 后背标记 / 备注 | Good / Unmarked 与手机星标分开；写后回读，比较未编辑 XMP 命名空间和 Flags 其余字符；确认重连、升级和进程重启缓存不能直接写入 |
| 存储操作 | 使用可删除的测试照片和空目录，记录源 / 目标、确认、无覆盖及操作后目录内容；断线后先回读实际状态 |
| 服务端图像检查 | 核对 1 / 2 / 4 / 8、Alarm、手填坐标与实际方向、灰平衡取样 / 当前照片确认 / 后续拍摄单独确认 / 恢复、像素拾取与直方图来源 |
| 时间与默认备注 | 手机本地时间与 CurrentTime；默认值写后回读并拍新片，核对已有照片未被改写 |
| 会话请求 | PowerSource 记录 Ack 并另观察供电；EnableTakenPicture 在实验室记录 yes / no 的实际结果，不把 no 认定为停发通知 |
| 实验室 | 型号 / 固件、模板或 USER HYPOTHESIS、目标 / 请求、匹配模式、原始回复 / 错误、附件哈希及实际效果分别记录；检查导出头脱敏及原始附件 |
| 界面 | 双风格、中文 / English / 跟随系统、内外屏、横竖屏、分屏与状态保持 |
| MOS | 功能确认后记录大小、SHA-256，与 CF 卡原文件逐字节比对 |
| Live View | 功能确认后记录分辨率、实际刷新率和稳定性 |

更换 Aptus / Valeo 型号或固件后，重新记录兼容性证据；不要从一个型号推断整个系列。
