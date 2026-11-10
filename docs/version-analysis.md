# WiView 1.0.2 与 1.0.3 静态分析

[English](version-analysis.en.md) · [项目首页](../README.md) · [参考资料](../reference/README.md)

原始分析日期：2026-10-18；参考资料路径于 2026-11-10 统一整理。分析对象为两份实际 CAB。以下区分文件事实、代码依据和待实机确认事项。

## 输入与校验

| 项目 | WiView 1.0.2 | WiView 1.0.3 |
| --- | --- | --- |
| 来源 | Internet Archive 的 Leaf Capture 11 光盘文件备份 | 用户提供 |
| 本地位置 | `reference/wiview/1.0.2/LeafWiView-1.0.2.CAB` | `reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB` |
| 大小 | 2,683,140 字节 | 2,729,244 字节 |
| SHA-256 | `5f38aab055b2cb46560e59aa87d019a76a0e7e4a2d6866fbbc58b8e5eac56f85` | `0f0a34891b3b4f7c70c6681092da38cd5d0bb9cadc82ff7b6dcb793c399b4cce` |

1.0.2 来自 [Leaf Capture 11 光盘文件备份](https://archive.org/details/leaf-capture-11)，已与该站文件元数据的大小、SHA-1、MD5 比对一致。该备份由社区用户上传，其来源声明与版本线索已记录，未独立完成厂商签名认证。1.0.3 的来源为本次用户提交。哈希用于标识和复现分析输入。

原始文件没有被执行。全部操作是 CAB 解包、PE 结构读取、字符串提取和 ARM 反汇编。

## 组件对照

每份 CAB 都有 7 个成员，其中 6 个为 ARM Windows CE 可执行文件，另一个为 MSCE 安装描述。

| CAB 成员 / 识别出的模块 | 1.0.2 大小 | 1.0.3 大小 | 比较结果 |
| --- | ---: | ---: | --- |
| `00LeafCE.001` / 主程序 | 2,319,872 | 2,357,760 | 主程序代码、数据、资源均变化 |
| `LEAFWI~2.002` / `LeafWiBTStack.dll` | 15,360 | 15,360 | `.text`、`.data`、`.pdata`、`.reloc` 相同；`.rdata` 不同 |
| `LEAFWI~1.003` / `LeafWiLauncher.dll` | 10,240 | 18,432 | 代码段相同；资源从 1,536 增至 9,728 字节 |
| `BtCoreIf.005` / `BtCoreIf.dll` | 123,392 | 123,392 | 整个文件相同 |
| `BTSDKC~1.004` / `BtSdkCE30.dll` | 188,928 | 188,928 | 整个文件相同 |
| `CabSetup.999` / `CabSetup.dll` | 23,552 | 23,552 | 代码、数据、资源相同；`.rdata` 不同 |

底层两份 WIDCOMM 库的版本资源均为 `1.4.1 Build 74`。主程序内含 2007-10-02 与 2008-03-13 的构建日期字符串；它们是样本内的版本线索。

发行包标签与程序内部日志版本还需区分：格式字符串 `Leaf WiView version %d.%d%d` 在两个样本中分别接收参数 `(1, 1, 5)` 和 `(1, 1, 7)`，静态重建为 `1.15` 和 `1.17`。这与文件名中的 `1.0.2` / `1.0.3` 不同；目前未运行程序核对 About 页面，也未将这两个日志值用作握手参数。

两版蓝牙封装代码段逐字节一致，支持复用同一套安卓传输实现。文件其他部分不同，不能据此宣称两个版本完全相同。

进一步检查发现，蓝牙封装、启动器和 CabSetup 的 `.rdata` 差异都只位于导出表时间戳字段。将各自时间戳的 4 字节置零后，其 `.rdata` 完全一致；比较脚本记录了这项归一化结果。

## 已有代码依据的通信方式

- `LeafWiBTStack.dll` 实际导入并调用 `CSppClient::CreateConnection`，使用 WIDCOMM 的 SPP 客户端。
- `BtSdkCE30.dll` 的串口服务 GUID 导出为 `00001101-0000-1000-8000-00805f9b34fb`；其 `CSppClient::CreateConnection` 调用路径使用了该 GUID。
- 两版控制消息接收函数均逐字节寻找 `0x5E`（`^`），随后以 `0x3E`（`>`）结束控制头。
- `GetImage` 回复处理读取 `attachment` 字段，再调用二进制读取函数读取指定字节数。分隔符不应在附件中扫描。
- 两版都有同步、目录、图像、图像参数、相机参数及异步照片通知相关代码和字符串。

原客户端控制文本在发送前经过系统代码页转换。初期实现应保留输入字节；非 ASCII 文件名和备注的编码需要实测确认。

## 1.0.3 的具体变化

1. 新增命令字符串 `^<Nak />`。其构造函数根据内部状态在 `Ack` 与 `Nak` 之间选择，并在一个分支中重置该状态。已确认有调用代码，尚未确认后背对它的实际响应和适用时序。
2. 新增 `^<SetCameraParam PowerSource="`，并出现 `FireWire`、`CameraBattery`、`powersourceselection`、`support`、`notsupport`。这为供电来源能力判断与设置提供了线索，Aptus II 5 是否报告该能力待实测。
3. 新增 `AsyncCommand`、`polling_timeout`、`IfGUIIsActive`、`ReadyToShoot` 等通知或状态处理线索。其中 `AsyncCommand` 处理函数实际读取并转换 `polling_timeout` 字段。
4. 新增固件更新提示及界面缓冲相关字符串，主程序和启动器资源也有变化。原厂 [1.0.3 安装指南](https://web.archive.org/web/20081230142347id_/http://www.leaf-photography.com/imgs/uploads/user%20guides/leafwiview_103_installation.pdf) 的印刷第 1 页明确介绍 Windows Mobile 6 与 VGA 支持；已核对 PDF 文本及渲染页面。

比较得到 32 个共有命令前缀和 2 个新增前缀。前缀是字符串锚点，部分命令需要动态拼接参数；数量不代表完整命令数量或后背支持的功能数量。精确清单见 [版本比较数据](../reference/analysis/version-comparison.json)。

## 对移植工作的影响

以 1.0.3 为主基线，以 1.0.2 为兼容性对照。安卓端重建传输和协议层，原 WIDCOMM/Windows CE DLL 仅作为研究依据。

`GetImage` 中的文件名可以指向 `.mos` 拍摄文件，这不足以证明返回的是完整 MOS 文件。程序含 JPEG 处理代码；完整原片读取与连续传感器取景仍需分别确认。`RawFrameBuffer` 字符串也没有被当作原片传输证据。

此阶段没有修改后背固件、配对设置或照片。Aptus II 5 的无线服务、完整握手、拍后预览和 Android 16 的连接结果均为待验证。

