# 协议证据与离线解析

[English](protocol.en.md) · [项目首页](../README.md) · [研究复现](research.md)

状态：静态代码已有依据，未取得目标后背的真实通信抓包；完整会话协议尚未确认。

## 证据位置

下表地址为样本 PE 的首选虚拟地址，不是文件偏移，也不是安卓运行地址。主程序原始成员名为 `00LeafCE.001`。

| 行为 | 1.0.2 地址 | 1.0.3 地址 |
| --- | --- | --- |
| 控制头读取函数 | `0x2a1a4` | `0x2a988` |
| 比较起始字节 `^` | `0x2a208` | `0x2a9ec` |
| 比较结束字节 `>` | `0x2a224` | `0x2aa08` |
| 二进制图像读取函数 | `0x31274` | `0x320cc` |
| 异步 GetImage 回复处理 | `0x315cc` | `0x32424` |

两版 `BtSdkCE30.dll` 的串口服务 GUID 位于 RVA `0x1e09c`。1.0.3 中 GUID 在 `CSppClient::CreateConnection` 的 `0x111120` 处载入；Leaf 封装模块在 `0x101f70` 处载入对应导入项。

[protocol-evidence.json](../reference/analysis/protocol-evidence.json) 保存输入哈希、函数范围、分隔符比较指令、GUID 与命令字符串偏移。`tools/build_evidence.py` 会重新核对指定样本的哈希、ARM 指令和 GUID，地址不匹配时失败。

查看接收代码：

```powershell
& .\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --va 0x2a988 --bytes 0x240
```

## 已确认的基本格式

原客户端控制头的读取规则是：找到 `^`，读取到随后的首个 `>`；中途遇到新的 `^` 会更新同步起点。不能依据这些代码推定它完整遵循标准 XML 的所有规则。

两版存在以下完整字面量：

```text
^<RequestSync />
^<ReSyncRequest />
^<Ack />
^<GetCameraParam GetParamList="battery_percent" />
```

`^<GetImage file_name="` 是动态命令的前缀。回复处理函数匹配 `Ack`、`file_name` 和 `attachment`，核对文件名，然后读取声明长度的二进制附件。下面仅为人工构造的格式说明：

```text
^<Ack file_name="Shots/test.mos" attachment="5" />[紧接着 5 字节二进制数据]
```

附件里的 `^`、`>`、NUL 和非 ASCII 字节属于附件内容。读取完附件后才能重新扫描控制消息。

原程序还检查 `size` 字段，但在已分析的图像回复路径中记录“packetization is disabled”。因此离线解析器只处理非分包附件路径，没有实现未确认的分包确认、校验或重传协议。

## 启动同步的静态调用线索

1.0.3 的相机初始化函数 `0x25cc4` 先构造并提交 mode 0 命令 `RequestSync`，随后构造并提交 mode 1 命令 `GetCameraParam GetParamList="CurrentTime"`。在同步成功分支中，它调用 `InitDiskContent` (`0x26118`)；非 tethered 分支再构造 mode 2 的根目录 `FileCommand dir` 查询。

命令构造函数位于 `0x2ed40`，三个分支均有具体字面量或拼接代码。上述顺序是原客户端的局部调用依据；蓝牙刚连接时的完整通知交互、是否有前置命令、目标固件返回字段和错误恢复仍需实机确认。

原程序还含 `CEVersion` 命令前缀，但尚未确认它在目标会话中的调用条件和取值。内部日志版本 `1.15` / `1.17`、发行包标签 `1.0.2` / `1.0.3` 保持分别记录，不能据此直接填入握手字段。

## 安卓当前版本的会话实现

`protocol/` 将上述规则移植为纯 Kotlin，`LeafProtocol` 使用可替换的 `LeafTransport`；Android 实现为标准 SPP socket。按用户最新要求，以目标后背无线可用为假设先行实现。

- 启动 RequestSync，等待 Sync；再查询 CurrentTime、根目录。没有猜测 CEVersion。
- 目录依据 1.0.3 `CFolderCommand::ProcessResponse` (`0x30c70` 起)：`dir` 按分号拆项，D 目录、F 文件，跳过前两字符，再按逗号拆名称与日期。第二字符语义未确认；测试使用冒号只是合成样本，不认定为规范。
- 同时只有一个等待回复的指令；GetImage 必须匹配 file_name、attachment。错名回复不用于当前照片；超时关闭会话。
- Ack 不能单独证明成功：出现非零或无法解释的 error_code 时报告失败，不提示备注写入成功。
- PictureTaken 的 file_name / busy / imageSaved 有 `0x28718` 起的静态依据。通知触发预览队列；忙碌或明确未保存时暂不请求。异步通知 Ack 的完整时序仍待实机确认。
- GetPictureParams、SetPictureParams 已接入。未确认 XMP 附件不猜测解码；确认回复不等同于目标固件写入已经验收。
- 默认 Windows-1252，可选择 GBK、UTF-8 实测；CP_ACP 不能证明一种编码适用于所有后背。
- 字段暂拒绝控制字符、引号及 `& < > ^`，不使用未经验证的实体转义。
- Android 会话的单附件上限为 16 MiB，以控制重复缓冲和解码内存；研究工具默认 64 MiB。两者都是主机配置，不是后背规格。

0.2.0 的双语言和资料整理不改变以上协议边界。未实现未知的 MOS 文件读取、Live View、marking / Good 写入值与破坏性文件操作。状态见 [当前功能表](implementation-status.md)。

## 原有离线研究工具边界

`leafresearch/framing.py` 实现流式分帧；`tools/replay_stream.py` 接受本地原始字节或十六进制测试文件。它们不打开蓝牙，不发送任何后背命令。

- 控制头是可检查的、只有属性的子集。属性值保留原字节，暂不猜测代码页和实体转换。
- 附件长度按十进制字节数解析，附件内跳过分隔符扫描。
- 主机默认限制控制头 64 KiB、附件 64 MiB；这些是实现侧上限，可配置，不是后背规格。
- 断开或超时后需要显式重置解析状态；流结束时报告未完整接收的控制头或附件。
- 错误长度、重复属性和超限输入会失败，不据此推测相机行为。

测试夹具位于 [research/fixtures](../research/fixtures)。全部为静态格式驱动的人工数据，附件不是可供画质验证的相片。

## 待确认

| 项目 | 当前结论 |
| --- | --- |
| Aptus II 5 实际广播的 SDP 服务 | 未检查实机 |
| 配对 PIN、链路认证、Android 16 加密要求 | 未验证 |
| 连接后命令顺序、CEVersion 的取值与协商 | 有命令线索，完整握手未确认 |
| 非 ASCII 文件名、备注、XMP 附件编码 | 未验证 |
| JPEG 回复样本及实际分辨率 | 未取得目标机型样本 |
| 异步通知、并发命令及超时恢复时序 | 有处理代码，需抓包和实测 |
| MOS 完整文件读取 | 未确认相关无线操作 |
| 连续 Live View | 未确认相关无线操作 |

上述待确认项不能由人工回放测试替代。

