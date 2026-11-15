# 拍后通知、额外 Ack 与灰平衡选点证据

[English](capture-notifications-analysis.en.md)

2026-11-15 的用户反馈是：v0.3.1 在 Valeo 上蓝牙连接稳定、ISO／光源设置可用，但每次拍后自动跟随后出现 `^<Error error_code="0x01" />`；会话仍 READY，手动下载可读最新图。此次仅分析已归档 DP-67、两版 WiView、经典 4.701 和共享 5.041 固件，不执行厂商代码，不连接或操作后背。用户反馈不是本次静态分析的实测结果。

最直接的候选原因是**新片通知后的多余裸 Ack**，而非把 `0x01` 直接解释为“忙”。原版在 PictureTaken／ImageChange 相关处理链中没有建立必须发送通知 Ack 的依据；固件中存在“无当前命令处理器”生成同一 `0x01` 的明确分支。若多余 Ack 的 Error 恰被等待中的 GetImage 接收，就能解释自动下载报错、手动下载正常的现象。该因果链仍须日志与设备验证。

## 资料与重现

[机器证据](../reference/dp67/analysis/capture-notifications/evidence.json) 记录全部输入路径、SHA-256、代码范围、字面量引用及结论边界；[DP-67 来源记录](../reference/dp67/provenance.json) 保留原始 ZIP、安装器、CAB 的哈希。本次读取以下 5 个精确版本，地址不能跨版本套用：

| 输入 | SHA-256 |
| --- | --- |
| DP-67 主程序 `LEAFDP~2.001`，1.2.1.2 | `d0e67119db4a8e49a2cf8ec264143ccdf5da2d67623f835df9ace5fdf9c620b7` |
| WiView 1.0.2 `00LeafCE.001` | `2f769acbb0c4aa410e50e1638dfb50f7a9b40d11debd5b04f66170ac4623b50d` |
| WiView 1.0.3 `00LeafCE.001` | `ac6d0699870d7f9a1e00fe8bf507a35cfc38f3d2b6619e5dbae30eb248b87fe9` |
| 经典 `FirmwareAptus.UPD`，4.701 | `a2287716927a18c9bbd7834be327f592289f8a48aa2859b4d252b813cc82741a` |
| `FirmWareAptusII.UPD`，5.041，共享代码对照 | `80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6` |

4.701 是已收录经典 Aptus 系列证据，并不是用户 Valeo 的固件转储。5.041 含共享 PDA 代码，**不能证明 Aptus II 有蓝牙硬件**。厂商原始字节未改动，未新增或解码私人 PDA 备份。

```powershell
.venv\Scripts\python.exe reference\dp67\analyze_dp67.py --verify
```

该只读命令检查独立库存，在临时目录重新提取安装器、运行静态脚本并逐字节对照所有分析文件。新增分析由 [analyze_capture.py](../reference/dp67/analyze_capture.py) 生成；它只读取已收录原件。没有修改固件独立分析目录或原件。

## PictureTaken／ImageChange 的路径与 Ack

DP-67 的接收器 `0x24a44..0x25128` 在 PictureTaken 分支读取 `busy`，选择事件并投递处理消息。应用处理器 `0x17360..0x17c6c` 读取完整 `file_name`，按 `/` 拆分，要求第一段是 `.`，并据此定位／创建缓存文件对象。`CDOSFile::Cache`（`0x30e70..0x3114c`）把该对象的完整名称作为唯一文件属性传入同一个 `CProtocolLayer::GetImage`（`0x26f00..0x27340`）。没有建立自动下载另行把完整路径缩成 basename 的依据，也没有固定延时后才下载的完整证据。

WiView 1.0.3 的接收路由 `0x29fd0..0x2a8a8`：PictureTaken 调 `0x28718`，ImageChange 调 `0x29a94`。二者后面共用的 `0x28664..0x28718` **是按 attachment 长度读取并丢弃附件的函数，不是发送 Ack**。这两个通知处理链没有已证实的通知 Ack 构造。1.0.2 的对应路由与解析函数也已单独保留。

Ack 必须按语境区分：

| 语境 | 原版证据 | 对实现的含义 |
| --- | --- | --- |
| PictureTaken／ImageChange 通知 | 本轮未建立裸 Ack 发送要求 | 不应因“收到通知”一律回 Ack |
| Stop 通知 | WiView 1.0.3 `0x29c28..0x29ccc`；`0x29c40` 取 `^<Ack/>`，`0x29c7c` 写链路；1.0.2 对应 `0x29504..0x295a8` | 保留这条有明确构造的专属响应 |
| 图像数据／分块校验 | DP-67 `0x257c0..0x264dc`；WiView 1.0.3 `0x31dc8`、`0x31e8c` 有 Ack／Nak | 属于独立机制；当前 App 仍未启用分包确认，不能据此给普通通知回 Ack |

JSON 列出原程序所有已找到的直接 Ack／Nak 字面量引用。该静态枚举没有声称穷尽所有可能动态拼接字符串。

## `error_code="0x01"` 的固件调用链

经典 4.701 的注册名查找 `0xe727c..0xe7320` 遍历已登记的 9 类 PDA 命令；目录中没有 Ack。接收处理器 `0xe6c5c` 解析命令名后在 `0xe6cec` 调用该查找；当当前 handler 指针为空时，`0xe6cfc..0xe6d28` 以消息枚举 **12** 调用 `0xe7b60` 创建 OutgoingMessage。创建器在 `0xe7bd8` 转入命令选择器 `0xe3c18`；跳表 `0x3e002c` 的枚举 12 项指向 `0xe3e50`，在 `0xe3e50..0xe3e80` 明确组合 **Error + error_code + 0x01**。

共享 5.041 有独立核对的同类链：接收器 `0x102ddc` → 查找 `0x1033fc`；空 handler 分支 `0x102e7c..0x102ea8` 以枚举 12 调用 `0x103ce0`；`0x103d58` 转入选择器 `0xfff64`，跳表 `0x46df20` 的对应项到 `0x10019c..0x1001cc`，组合相同 Error。这是共享方言对照，不是 Valeo 固件同版本证明。

同时存在其他错误构造，所以**数字本身不唯一标识原因**：经典通用构造器 `0xb0b74` 将枚举 2 映为 `0x01`、枚举 3 映为 `0x02`。GetImage 自身文件查找失败分支 `0x912c0..0x912c8` 传的是枚举 3；5.041 对应 `0x98504..0x9850c`。经典图像处理失败／零缓冲等另走 `0xb0ca4`，使用 `error` 属性，例如 `1ffc`／`8002`。这些都不能把本次 `0x01` 直接翻译成“文件没写完”或“相机忙”。

因此有证据的修复方向是去除无依据的通知 Ack，并分别测试通知处理与图像分块确认。若错误仍出现，应保留实际请求、原始 Error、后续 GetImage／Ack／Sync 的时间顺序，继续定位；不要根据本轮分析把所有 `0x01` 自动当作可重试忙状态。

## 保存状态与重叠下载边界

WiView 1.0.3 的 PictureTaken 解析器明确读取 `busy`、`parametersReady`、`imageSaved`、完整 `file_name`。`busy=yes` 转到独立 busy 通知；它不是 Error 代码的推断。`parametersReady=yes` 和 `imageSaved=yes` 默认均为假，保存通知模型在 `0x171b4..0x171fc` 把图片状态记为 0／1／2，imageSaved 优先；状态 getter／setter 分别在 `0x4809c`、`0x48b88`。saved 分支额外调用的 `0x25570` 是双字记录的运算，不能把它误称为提交下载队列。UI 对不同状态有分支。

缓存下载函数 `GetBitmapBase`（`0x51234..0x516fc`）在本地缓存／请求变体判断后，于 `0x5143c` 构造 GetImage；这条已追构造路径没有检查图片状态必须等于 2。本轮没有完整还原所有 UI 调度，因此不宣称“原版永远等 imageSaved 才开始任何预览”，也未建立通用的 parametersReady 强制门控。旧 DP-67 未建立相同字段要求；缺失新字段不能被默认解释为旧后背永远未准备好。ImageChange 有独立刷新路径，也不是无条件发起另一个重叠二进制下载的依据。

## 灰平衡点的坐标域

WiView 1.0.3 的 GetImage 扩展构造器在 `0x36240` 使用确切格式 **`light="%d,%d"`**，不是空格分隔。它请求后背计算灰平衡取样；界面“选白色位置”不等于客户端自己计算 Kelvin 或保证该位置曝光合适。

原版的映射顺序已追到：

1. Sync 的 `picturedimensions` 按逗号取两整数；`0x2feb4..0x2ff64` 把较大值存作网格宽 W，较小值存作高 H。同步目标对象在全局对象 `+8`，故解析器 `+0x104/+0x108` 对应全局 `+0x10c/+0x110`。
2. `0x38f10/0x38f28` 返回 W−1／H−1；完整预览初始化在 `0x78a90..0x78ab4` 再加 1，构成 `[0,W)×[0,H)` 的初始 ROI。
3. `OnImageClick`（`0x765dc..0x768fc`）按当前 ROI 的宽／高乘点击坐标、除显示图像矩形宽／高，再加 ROI 起点。变换后的点才在 `0x76860` 进入灰平衡处理器，不能把手机屏幕像素直接发给后背。
4. 照片方向解析：Portrait CCW＝0、Portrait CW＝1、Landscape＝2、Auto＝3。灰平衡处理器 `0x777f0..0x77bc0` 中 Landscape 保持 `(x,y)`，CW 变为 `(H−y,x)`，CCW 变为 `(y,W−x)`；所读指令没有额外 `−1`。不能把另一种像素边界惯例标作原版行为。

竖图显示链也已继续追到。`0x5522c` 取照片实际方向，`0x5523c` 把它与已解码 bitmap 传给包装器 `0x54d98..0x54f3c`。包装器读取 bitmap 宽高：Landscape＝2 预期宽不小于高；Portrait CCW／CW 预期高不小于宽，反向时分别记录明确的尺寸不符日志。正常纵向 bitmap 在 `0x54ee8..0x54f18` 被临时旋成横向：CW＝1 传 270°，CCW＝0 传 90°给 `0x955a4`，随后交换宽高。这是**旧 PDA 为显示额外做的旋转**。

`0x955a4..0x95814` 创建旋后 bitmap 并调用像素内核 `0x94de4`。DIB 构造器 `0x92cfc` 在 `0x92d1c` 写正高度；按该 24 位 bottom-up DIB 布局，内核 `0x94ee8..0x94f94` 的 90°分支对应视觉顺时针 `(源高−1−源y,源x)`，`0x950f4..0x951b0` 的 270°分支对应逆时针 `(源y,源宽−1−源x)`。前述灰平衡处理器恢复的正是这层临时显示旋转。

由这条代码链可以推导：**安卓若原样显示下载 JPEG，就不应重复套旧 PDA 的显示旋转或逆旋转**。在普通完整 FIT、照片明确报 Landscape／Portrait CW／Portrait CCW、PictureDimensions 为正、照片方向与当前 JPEG 内容绑定、预览宽高方向及比例一致、没有额外 EXIF／界面旋转或未知裁切时，实际 JPEG 内点的归一化位置可直接映射到：

| 照片方向 | 相机取样网格 |
| --- | --- |
| Landscape | W × H |
| Portrait CW 或 Portrait CCW | H × W |

这些值是**原 PDA 使用的相机图像坐标网格**，没有证明就是 MOS 原始传感器像素坐标。安卓采用归一化位置乘网格宽高、向下取整并限制合法边界，是明确的边界处理选择；像素旋转内核有 `−1`，旧灰点变换使用 W／H，不能声称两者所有边缘取整逐位相同。留白区域不产生请求。

Auto、方向缺失、尺寸或 JPEG 方向矛盾、任意 zoom ROI、未知裁切、自动 EXIF 旋转均不在该有限桥接范围。宽高比一致只是核对条件，不能证明一个原本未知的裁切不存在。这些情况保留选点提示或手动坐标输入；硬件取样效果尚未由本分析验证。
