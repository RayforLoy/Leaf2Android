# WiView 客户端协议缺口审计 / Client protocol gap audit

审计日期：2026-11-11。对照基线：Leaf2Android v0.2.0、尚未加入本轮扩展的 `Commands.kt` / `LeafRepository.kt`。

本报告只做静态分析：读取两版 CAB 提取的 ARM32 Windows CE 程序、PE `.pdata` 函数边界、机器指令和字符串的代码引用；没有运行原厂程序，也没有通过静态证据冒充后背实测。用户已报告 **Valeo 22Wi、Aptus 22 可以连接和读取照片**；这项反馈没有验证下表中的参数写入、文件管理、其他固件或其他型号。

可复核机器证据保存在 [`wiview-control-evidence.json`](../reference/analysis/wiview-control-evidence.json)，包含两版函数范围、函数字节 SHA-256、逐条 ARM 指令、字面量引用和命令构造结论。以下 VA 都是相应 PE 的虚拟地址，不是原 CAB 文件偏移。

| CAB 标注版本 | 提取模块 | SHA-256 |
| --- | --- | --- |
| 1.0.2 | `reference/analysis/wiview-1.0.2/extracted/00LeafCE.001` | `2f769acbb0c4aa410e50e1638dfb50f7a9b40d11debd5b04f66170ac4623b50d` |
| 1.0.3 | `reference/analysis/wiview-1.0.3/extracted/00LeafCE.001` | `ac6d0699870d7f9a1e00fe8bf507a35cfc38f3d2b6619e5dbae30eb248b87fe9` |

## 1. 最重要的结论

ISO 的读写已有实际代码证据，v0.2.0 没有提供该控制。原厂通过 **完整 `ShootSetup` 配置** 写入 ISO；没有找到原厂构造 `SetCameraParam ISO="…"` 的证据。读取当前配置和允许值的有证据路径是 `RequestSync` → `Sync`，不是猜测的 `GetParamList="ShootSetup"`。

同一完整配置还包含光源／白平衡 `Light`、画质、取景框、色调、锐化、ICC 配置、文件名前缀、压缩、方向和颜色模式。应优先使用设备下发的允许值，只更改用户指定字段，保留其余原值，写入后重新 `RequestSync` 核对。`ShutterSpeed` 虽可读取，却没有被这两版 WiView 的完整配置构造器写出，不能据此开放机械快门时间或光圈控制。

服务端缩放预览、曝光警示、灰平衡取样／确认、图像像素拾取、存储目录和文件管理也有构造代码。它们不能被标为连续 Live View 或 MOS 原片下载。

## 2. ISO 和完整拍摄配置

### 2.1 读取当前值与允许值

`ParseSyncResponse`：1.0.2 `0x2e474..0x2f940`；1.0.3 `0x2f0c4..0x306d4`。

| `Sync` 属性 | 结构与用途 | 1.0.3 关键代码 |
| --- | --- | --- |
| `iso` | `;` 分隔的允许值列表；不是当前 ISO | `0x2f434..0x2f47c` |
| `shootsetup` | `;` 分隔的完整配置列表；每项为 `,` 分隔的 `key=value` | `0x30034..0x3011c` |
| `defaultshootsetup` | 配置名称，用于与每项 `SetupName` 匹配并选择当前配置 | `0x2ff88..0x2ffe4`、`0x30440..0x30488` |
| `factorydefaultshootsetup` | 单独保存的出厂配置名称／字符串 | `0x301fc..0x30240` |
| `imagequality`、`toning`、`light`、`orientation`、`sharptable`、`shutterspeed`、`inputprofile`、`outputprofile`、`cameratype`、`filecompression`、`nlcompression` | 每个属性分别为 `;` 分隔列表 | 同一 Sync 解析器；调用列表函数 `0x2c070` |
| `framing` | `;` 分隔记录，每条必须为 **5 个**逗号字段：名称、4 个整数 | `0x2bea4..0x2c028`，数量检查 `0x2bf1c` |
| `color` | 下发颜色选项；解析器识别 `color`、`BW` | `0x2f504..0x2f61c` |
| `defaultcolor`、`defaultorientation` | 默认单值；与列表区分 | `0x2f620..0x2f684`、`0x2f8c8..0x2f994` |
| `ICCFlag`、`Prefix` | 没有找到独立顶层允许值列表；在完整配置里解析 | `0x2c69c..0x2c7a8` |

完整配置解析器：1.0.2 `0x2b634..0x2bd84`；1.0.3 `0x2c250..0x2c99c`。当前 ISO 取匹配 `defaultshootsetup` 的配置内 `ISO`，不能从 `iso` 列表第一个值推断。找不到唯一完整当前配置时应停止写入，保留诊断信息。

`framing` 的逗号后四个整数是选项附带数据；配置的 `Framing` 值取其名称，不能把整条五字段记录再次放进配置。

### 2.2 完整写入及选中现有配置

构造器：1.0.2 `0x31c78..0x31fb4`；1.0.3 `0x32ad4..0x32e10`。下列字段顺序由 CString 拼接调用逐项确认：

```text
^<SetCameraParam ShootSetup="SetupName={name},ShootingFolder={folder},ISO={iso},ImageQuality={quality},Framing={framing},Toning={toning},Light={light},SharpTable={sharp},InputProfile={input},OutputProfile={output},CameraType={camera},ICCFlag={0|1},Prefix={prefix},FileCompression={compression},Orientation={orientation},Color={color|BW}" />
```

启用 Nearly Lossless 分支时，压缩段改为：

```text
,FileCompression=Nearly Lossless,NLCompression={nl_value}
```

`ICCFlag` 明确序列化为 `0` 或 `1`；配置内部颜色序列化为小写 `color` 或 `BW`。字段名大小写须保留。原厂另有条件分支，可在完整配置属性闭合后附加独立 `Orientation`、`Color` 属性；分类函数 `0x723e8` 检查配置名末尾 `.1`。顶层 `Color` 格式化函数 `0x2be78` 输出首字母大写 `Color` 或 `BW`；顶层 `Orientation` 格式化函数 `0x24cec` 输出 `Portrait CW`、`Portrait CCW`、`Auto` 或 `Landscape`，与配置内部字段区分。不要把该分支与出厂恢复命令混在一起。

同一个构造器的布尔参数为真时，只发送现有配置名：

```text
^<SetCameraParam DefaultShootSetup="{existing_SetupName}" />
```

它也可经过上述附加方向／颜色分支。实际 UI 调用 `0x6dc78..0x6dfd8` 在选中已有配置时选择该路径，编辑配置时发送完整配置，并在确认后更新当前配置引用。`CameraShootSetupChangeNotification` 的处理 `0x18800..0x189f4` 也会解析配置并更新当前项。后背是否已激活所写配置，仍须以后续 `Sync.defaultshootsetup` 和配置值确认。

### 2.3 白平衡／灰平衡的三个不同字段上下文

1. `ShootSetup` 内的 `Light`：选择设备下发的光源／白平衡项，作用于拍摄配置。
2. `GetImage light="x,y"`：对预览图像坐标进行灰平衡取样；不是 Kelvin/tint 参数。回复中的 `light` 为需保存的结果字符串。
3. `SetPictureParams light="…" imageUpdate="yes"`：确认照片灰平衡结果。省略 light 并用 `imageUpdate="no"` 是恢复预览的独立路径。

构造器 `0x34a28..0x34adc`；`CShootViewEU::OnConfirm` 在 `0x77460..0x77484` 将待确认光源字符串写入当前配置的 Light，再全量提交；在 `0x775d0..0x775f4` 对当前照片发送同一字符串。`RestoreGrayBalance` `0x7c0b4..0x7c194` 用空 Light 指针构造恢复请求。该调用链支持灰平衡语义；并不能推导任意 Kelvin 数值都合法。

### 2.4 Prefix 的原厂 UI 上限

两版原厂 UI 都把 Prefix 编辑长度设为 **23 个 UTF-16 代码单元**，不是 23 字节，也不是固件文件名的硬件安全上限。1.0.2 的 `0x6ef04..0x6ef20`、1.0.3 的 `0x6ff20..0x6ff3c` 先从配置 `+0x40` 取 Prefix，再以 `r3=0x17` 调用文本输入框构造。1.0.3 构造器 `0x491ac` 在 `0x49234` 将限制保存到对话框 `+0x680`，初始化 `0x4965c..0x49664` 把它交给文本控件的限制 setter `0x8e6a4`。字符插入 `0x8f1a4..0x8f1bc` 比较当前 CString 长度，达到限制便拒绝继续插入。

Prefix 确认处理 `0x6ff88..0x6ffd0` 将结果直接复制回配置；该路径没有额外的 ASCII-only 检查。键盘模式参数 7 只证明原客户端启用了对应键盘组，不能推出任意 Unicode 在协议／后背上安全可用。协议分隔符和路径字符仍须另行拒绝。

因此，23 可作为旧客户端输入限制使用。针对独立审计的固件 5.041，文件名算法的 24 字符预算及数字计数器并未建立 Prefix 任意长度的安全范围；在缺少更多硬件证据时，除限制 23 外，保持“不增加当前已知 Prefix 的 UTF-8 字节长度”的保守规则。该规则是当前实现边界，不应宣称为原厂硬件规格。

## 3. 后背标记与 XMP

两版已检查的 Marking 构造都附带 XMP：

```text
^<SetPictureParams file_name="{path}" Marking="Good" XMP_tag="{base64_xmp}" />
^<SetPictureParams file_name="{path}" Marking="Unmarked" XMP_tag="{base64_xmp}" />
```

| 路径 | 1.0.2 | 1.0.3 |
| --- | --- | --- |
| 内部生成标记命令 | `0x305fc..0x307b0` | `0x31390..0x31544` |
| 接收已生成 XMP 的构造器 | `0x30c88..0x30d64` | `0x31a18..0x31af4` |

`XMP_tag` 是标准 Base64，不是把原始 XML 放进引号。1.0.3 `0x9911c` 序列化 XMP 后调用 `0x27530`；分块编码 `0x272fc` 引用标准字母表 `ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/` 并使用 `=` 补位。反向路径 `0x9b420` 调用 `0x275a4` 解码。

`SetFlag` `0x98da4..0x98ff8` 处理 namespace `http://www.creo.com/global/products/digital_photography_leaf/default.htm/` 的 `Flags`：有足够长度时只改第三个字符，Good 设 `+`，取消设 `-`；缺失或太短时构造 `--+`／`---`。必须保留其他 XMP 信息和其他标记位。

GetPictureParams 的有证据返回路径为 **Ack 的 `xmp_tag` 头属性**：`0x2e3f8` 匹配属性名，`0x2e440..0x2e44c` 将该属性值传入 `ParseXMPTag` `0x2d598`，继而经 `0x9b420` 调用 Base64 解码 `0x275a4`。没有把二进制附件当作 XMP 的依据；当前解析器对此未证实形式明确失败，缺少该头属性则返回“没有 XMP”，不会合成空包覆盖后背元数据。

**客户端构造代码没有证明省略 XMP_tag 的写法可用。** 如服务器侧独立审计证实可选，方可依据该额外证据实现 Marking-only 路径。v0.2.0 的星标只是手机本地状态，不能作为后背标记写入成功的依据。

## 4. 图像预览与曝光检查

### 4.1 服务端预览请求

构造器：1.0.2 `0x35088..0x352a0`；1.0.3 `0x36070..0x36288`。

```text
^<GetImage file_name="{path}" zoom="{1|2|4|8}" XCoord="{x}" YCoord="{y}" Alarm="yes" edge="{white|dark|both}" light="{x},{y}" />
```

各扩展组是可选项：zoom 内部枚举 0/1/2/3 分别写 `1/2/4/8`；枚举 4 不发 zoom 和坐标。Alarm 开启时才发 `Alarm="yes"` 和 edge；light 仅灰平衡取样时发送。坐标与相片方向关联，原厂 UI 存在旋转坐标换算，不可直接把屏幕坐标作为后背坐标。

扩展回复仍需核对 `file_name`、附件长度；`light` 回复字符串由 `0x3640c..0x36754` 保存。收到 `size` 时原厂明确记录 “Packet size received but packetization is disabled”。本审计没有把未知分包模式声明为已解决。

`GetPictureParams` 的回复解析器（1.0.2 `0x2cc2c..0x2de54`；1.0.3 `0x2d87c..0x2eaa4`）分别将 `histogram`、`histogramr`、`histogramg`、`histogramb` 按分号转整数列表，没有在所审代码中证明固定 256 桶。`marking` 与 `Good` 比较；`newpicture` 被跳过；`zoom`、`alarm_dark`、`alarm_white` 与 `Yes` 比较。此处的 zoom 是状态字段，不能误用为 `GetImage` 请求的倍率。其他曝光／警示统计字段宜保留原值并逐步确认来源。

### 4.2 `ImageFunction fstops` 不是光圈／快门控制

```text
^<ImageFunction file_name="{path}" fstops="{x};{y}" />
```

构造：1.0.2 `0x320a8..0x32174`；1.0.3 `0x32f04..0x32fd0`。`HandleImageClickHistogramPick` `0x7a454..0x7a6f4` 在 `0x7a4dc..0x7a4f4` 转换点击坐标，然后通过 r2/r3 传入这两个整数。回复解析 `0x33094..0x331a4` 读取 `histogramlocation` 整数和 `histogramvalue` 字符串。

因此不能被字段名 `fstops` 误导为 f-stop 设置。可以实现后背像素拾取／曝光分析，但需明确结果来自后背接口；其 RAW/JPEG 来源未在本次客户端审计中证实。

### 4.3 原厂图像坐标与方向变换

1.0.3 的 `DoZoomAndPan` `0x76c6c`、`HandleImageClickGrayBalance` `0x777f0`、`HandleImageClickHistogramPick` `0x7a454` 使用一致的方向转换。`StringToOrientation` `0x2bc9c` 映射 Portrait CCW→0、Portrait CW→1、Landscape→2；Auto→3 不能用作已确定的单张方向。

尺寸来源是 `Sync.picturedimensions`，值为两个逗号分隔整数。解析 `0x2fe34..0x2ff84` 将大者保存为 W、小者保存为 H。必须使用 `GetPictureParams.orientation` 的**实际照片方向**，不能用 `Sync.defaultorientation` 代替。

| 实际照片方向 | 原厂输入图像点 `(x,y)` → 协议点 |
| --- | --- |
| Portrait CW | `(H−y,x)` |
| Portrait CCW | `(y,W−x)` |
| Landscape | `(x,y)` |

关键代码：zoom `0x76d4c..0x76da4`、灰平衡 `0x77864..0x7789c`、像素拾取 `0x7a4c0..0x7a4f4`。原厂使用 **W−x／H−y，没有减一**，不能未经设备证据改成零基像素中心的 `W−1−x`／`H−1−y`。

这些公式的输入已经是原厂视图传入的图像空间整数坐标；当前审计没有完整确认屏幕缩放、fit、裁切、放大 viewport 到原图空间的全部换算。也没有从旧客户端证实固件 5.041 的 Landscape 180 对应公式。当前 App 保留手动填写原图坐标，不把 Compose 点位或 JPEG 缩略图坐标猜成后背坐标；缺少实际方向、原始尺寸或缩放关系时不自动推算。

## 5. 文件操作的精确路径构造

| 操作 | 原厂命令格式 | 1.0.2 构造入口 | 1.0.3 构造入口 |
| --- | --- | --- | --- |
| 删除照片 | `^<FileCommand FileCommand="del" Subject="{完整照片路径}" />` | `0x2c394` / `0x2c730` | `0x2cfe0` / `0x2d380` |
| 移动照片 | `^<FileCommand FileCommand="move" Subject="{完整照片路径}" Target="{目标目录}/{原文件名}" />` | `0x3236c` | `0x331c8` |
| 复制照片 | `^<FileCommand FileCommand="copy" Subject="{完整照片路径}" Target="{目标目录}/{原文件名}" />` | `0x326dc` | `0x33538` |
| 删除目录 | `^<FileCommand FileCommand="rmdir" Subject="{目录}/" />` | `0x329a4` | `0x33800` |
| 重命名目录 | `^<FileCommand FileCommand="rename" Subject="{旧目录}/" Target="{旧目录父路径}/{新名}/" />` | `0x32b5c` | `0x339b8` |
| 新建目录 | `^<FileCommand FileCommand="mkdir" Subject="{父目录}/{新名}/" />` | `0x32ec0` | `0x33d1c` |
| 快速格式化 | `^<FileCommand FileCommand="qformat" Subject="{存储根}/" />` | `0x2c1a4` | `0x2cdf0` |
| 恢复存储 | `^<FileCommand FileCommand="recovery" Subject="{存储根}/" />` | `0x2c1a4` | `0x2cdf0` |

目录结尾的 `/` 是构造代码明确拼接的，不应重复追加。move/copy 的 Target 包含原文件名。已核对的 rename 构造是目录操作，尚未证明任意照片改名。部分路径有 `Retransmission="TRUE"` 重试形式，不代表首次请求必须携带该属性。

删除和格式化需要用户明确操作具体后背路径；不能因首次连接自动执行。recovery 的数据恢复范围和副作用、存储根名称、非空目录删除规则未由客户端完整证明。copy/move 是后背内部文件操作，不是将 MOS 原片读到手机。

## 6. 完整缺口矩阵

下表“缺口”指 **v0.2.0 基线**，不是声称本轮任务完成后仍缺失。“可实现”代表有构造／解析证据并适合开发；目标机型实际支持程度仍需请求结果和回读确认。

| 功能 | 基线状态 | 静态确认与后续处理 |
| --- | --- | --- |
| RequestSync、CurrentTime 读取 | 已有 | 保留；扩展 Sync 的完整配置、列表和能力解析 |
| ISO | 缺失 | 可实现；设备允许值 + 完整 ShootSetup + Sync 回读 |
| 白平衡／Light | 缺失 | 可实现设备光源选择；灰平衡取样／提交需区分照片和后续拍摄 |
| 拍摄配置选择 | 缺失 | 可实现 `DefaultShootSetup="名称"`；不要发整份配置给此字段 |
| ImageQuality、Framing、Toning、SharpTable、ICC profiles、CameraType、压缩、方向、Color | 缺失 | 可实现设备允许项；其余字段保持原值；ICCFlag 0/1，Framing 取名称 |
| ShootingFolder、Prefix | 缺失 | 完整配置中有写入；先验证目录与字符分隔规则；不猜默认值 |
| ShutterSpeed／光圈／机械快门 | 无遥控 | ShutterSpeed 只读；未发现这两版客户端发出的对应执行器写入 |
| GetImage 基础拍后预览 | 已有 | 用户报告 Valeo 22Wi、Aptus 22 可读取；不等于所有型号验证 |
| 后背 zoom、XCoord、YCoord | 缺失；仅本机放大 | 可实现 `1/2/4/8`，方向和传感器／预览坐标须核对 |
| 后背 Alarm / edge | 缺失；仅本机 JPEG 警示 | 可实现 white/dark/both 请求，须与本机警示分别标识 |
| 后背像素拾取／曝光分析 | 缺失 | 可实现 `ImageFunction fstops="x;y"`；显示源自后背，勿标为 RAW 测量 |
| 后背直方图和曝光信息 | 部分参数读取，显示主要为本机 JPEG | GetPictureParams 解析可见 histogram/r/g/b、hwhite/hdark/locations/average 等；保留原始字段，传感器来源待确认 |
| 照片信息、copyright / annotation | 已有读取和备注写入 | 可补 XMP 解码及完整元数据保留；单字段 annotation/copyright 构造也已证实 |
| 后背 Good / Unmarked | 缺失；只有本机星标 | 构造已确认；客户端带 Base64 XMP，不能擅自忽略；等固件可选性结论 |
| XMP-only 和 annotation+XMP 写入 | 缺失 | 1.0.3 `0x354c0`、`0x3568c`；需保留无关字段，安全序列化 |
| 目录浏览 | 已有 | 可以扩展下列管理操作；目录刷新应核对回包 subject |
| 删除照片、新建目录、删除目录、移动／复制照片、目录改名 | 缺失 | 可实现表5精确格式；操作成功后刷新，错误不修改本地映射 |
| 快速格式化／recovery | 缺失 | 有命令构造；破坏范围／目标根需设备证据，不能默默开放任意字符串 |
| 后背时间同步 | 只读 | 可实现 `CurrentTime="YYYY:MM:DD:hh:mm:ss"`；1.0.3 `0x32950` |
| 默认 Annotation/Copyright | 缺失；当前只针对单张照片 | 可实现 GetParamList=`Copyright;Annotation` 和 SetCameraParam 相应字段；`0x3675c`、`0x34ee8` |
| Brightness | 缺失 | `Brightness="整数"` 构造确认 `0x34b90`，数值范围和显示对象需继续确认；不是曝光补偿 |
| PowerSource | 缺失 | 1.0.3 新增，`FireWire`/`CameraBattery`，`0x34c78`；Sync `powersourceselection=support` 才展示 |
| 拍摄通知开关 | 缺失 | `EnableTakenPicture="yes|no"`，`0x34e08`；不是释放快门 |
| NewPinCode | 缺失 | `NewPinCode="…" PinEncryption="None"`，`0x35a18`；长度／字符／失效重配对行为需确认 |
| 总帧数及计数器重置 | 总数可读，重置缺失 | `ResetCounter="yes"`，`0x35d24`；明确显示操作含义，不能当存储格式化 |
| CEVersion | 不冒充原厂 CE | 构造器 `0x35168` 的兼容值为 `0x01080000`／十进制17301504；不是 App 版本或后背固件版本 |
| FactorySettings | 缺失 | `0x35878` 带 `FactorySettings="TRUE"` 及方向、颜色，可带DefaultShootSetup；不属于普通配置选择 |
| ReSyncRequest、NAK、重传 | 有基础断线处理，未完整实现原厂恢复 | 有构造代码；1.0.3 新增 NAK 路径；完整时序、分包恢复须独立验证 |
| MOS 原片读取、连续 Live View | 未提供 | 本次两版客户端没有确认可用接口；内部复制文件和拍后预览不能替代这项证据 |

## 7. 编码、校验与实现边界

原厂命令先构造 UTF-16 字符串，再按 Windows CE 系统代码页转换为传输字节。ASCII 语法和数字可直接复用；设备下发的非 ASCII 名称、文件路径和备注须按当前后背字符集编码。现有 App 的 Windows-1252／GBK／UTF-8 选项不能被视为每台设备均已验证。

完整配置中的逗号和分号是结构分隔符；没有证据表明可用 XML 实体或反斜杠转义配置内部的分隔符。原值若含有无法安全序列化的字符，应保留数据并说明原因，禁止静默丢字段、替换值或发送半份配置。XMP 的 Base64 编码可避免嵌入 XML 引号／尖括号。

建议离线测试用明确标为人工的 Sync fixture，覆盖允许值与当前值分离、多配置、重复名称、缺字段、Nearly Lossless、完整回写保持未编辑字段、写入拒绝／回读不一致，以及路径更新。人工 fixture 验证编码器与状态机，不构成后背写入实测。

## English implementation summary

WiView 1.0.2 and 1.0.3 both construct ISO writes inside a complete `ShootSetup`. `Sync.iso` is the option list; the current ISO belongs to the setup selected by `Sync.defaultshootsetup`. Keep device-provided values and all untouched configuration fields, send the evidenced field sequence, and request a fresh Sync to confirm. `DefaultShootSetup` selects a named existing setup. A parsed `ShutterSpeed` does not establish a shutter-time writer.

The audited client supports additional processed-preview requests, gray balance, histogram pixel picking and back-storage operations. `ImageFunction fstops="x;y"` carries image coordinates, not an aperture command. Both audited Marking constructors send `Good`/`Unmarked` together with Base64 XMP; Marking-only requires independent server evidence. No verified full MOS download or continuous live-view command was established. All newly exposed writes still need model/firmware-specific hardware checks.
