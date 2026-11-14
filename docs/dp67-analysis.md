# DP-67 与 Valeo 设置断连的静态对照

[English](dp67-analysis.en.md)

用户报告 Valeo 读图正常、修改设置后断连。此次 DP-67 分析发现了直接影响设置写入的差异：原程序使用较短的 ShootSetup 字段集，并把修改后的配置放到 `.1` 槽。这些证据支持增加 DP-67 兼容模式；**尚无真实后背测试证明它们就是断连原因，亦不能据此宣称稳定性已改善**。朋友使用原版正常属于用户报告，不是本项目的实测结果。

## 来源与方法

2026-11-14 仅静态读取用户 ZIP 中的厂商安装器，没有执行或加载厂商 EXE/DLL。安装器是 ARM Windows CE PE，内嵌 `BIN/103` CAB。主程序版本为 1, 2, 1, 2，产品名 Leaf DP-67。

| 输入 | 大小（字节） | SHA-256 |
| --- | ---: | --- |
| 用户 `Leaf DP-67 app.zip`，只记录哈希、不收录 | 2,454,492 | `c166a2bda7601c7f6dcb21bcf9f163e84894dfd00611ec681d9c2caaddb98140` |
| [原始 Setup.exe](../reference/dp67/original/LeafDP67-1.2.1-Setup.exe) | 1,314,304 | `c0b8de1e4700b27764aa4d755bde730736c649f8de5e72196daa160eaa980126` |
| [内嵌 CAB](../reference/dp67/original/LeafDP67-1.2.1.CAB) | 1,288,384 | `41d6966acb4b2cb676a6bfef3b534ce802feb37cbeaf6ac9beaac196d29d13f4` |
| [主程序 LEAFDP~2.001](../reference/dp67/extracted/LEAFDP~2.001) | 945,152 | `d0e67119db4a8e49a2cf8ec264143ccdf5da2d67623f835df9ace5fdf9c620b7` |

ZIP 另外包含 4 份 PBF 备份；只检查其标识的前 12 字节 `POCKETPCIMAG` 作分类，没有解码或发布内容。没有收录完整 ZIP、备份或 Mac 元数据。原始来源、解包方式及排除项见 [provenance.json](../reference/dp67/provenance.json)。地址均为上述精确主程序的 ARM 虚拟地址，镜像基址 `0x10000`；不适用于其他版本。各反汇编也列出文件偏移，`.pdata` 范围内的字面量池可能被反汇编器显示为数据状指令。

## 设置写入差异

DP-67 的 `CControlApplication::SaveShootSetup` 位于 `0x19f08..0x1a4a0`。与两版 WiView 的已核实构造相比：

| 项目 | DP-67 1.2.1.2 | WiView 1.0.2／1.0.3 |
| --- | --- | --- |
| ShootSetup CSV | 12 核心字段，可选第 13 项 Prefix | 16 项，Nearly Lossless 分支再加 NLCompression |
| Orientation、Color、FileCompression | 此保存器不写 | 此保存器写入 |
| NLCompression | 此保存器不写 | Nearly Lossless 条件写入 |
| 独立于 CSV 的全局 Orientation／Color | 目录包装器不追加 | 按配置名分类可追加 |
| 选普通预设 | `DefaultShootSetup=原配置名` | 有 DefaultShootSetup 分支，保留其本版全局属性规则 |
| 修改槽 | 保存器以名称是否**包含** `.1` 分支 | WiView 的后缀判定规则不能直接替代 DP-67 规则 |

DP-67 按以下顺序构造 CSV，逐项字符串引用已由脚本断言：

```text
SetupName,ShootingFolder,ISO,ImageQuality,Framing,Toning,Light,
SharpTable,InputProfile,OutputProfile,CameraType,ICCFlag[,Prefix]
```

每项仍是 `key=value`，取值应沿用后背 Sync 返回的配置或选项，不补猜测默认值。`0x19fd8..0x1a29c` 是 12 核心键的构造位置；`0x1a2dc..0x1a2f0` 是 Prefix 条件分支。`0x19f70..0x19f88` 检查 `.1`；普通名称转到 `0x1a3c0..0x1a3e0`，使用 DefaultShootSetup。完整保存器见 [save-shoot-setup.arm.txt](../reference/dp67/analysis/disassembly/save-shoot-setup.arm.txt)。WiView 对照来自 [既有客户端证据](../reference/analysis/wiview-control-evidence.json)，不是据固件字符串推测。

`CDOSDirectory::SetCameraParam`（`0x2aa34..0x2ab94`）只把调用者提供的名称／值数组转换后转交协议层；`0x2ab08` 调用 `0x279e8`，没有追加全局 Orientation 或 Color，见 [包装器](../reference/dp67/analysis/disassembly/directory-set-camera-wrapper.arm.txt)。因此直接沿用较新的完整 WiView 保存器，不能视作与 DP-67 完全相同的请求。

## `.1` 修改槽、Prefix 与 Framing

`SetImgProp`（`0x19cdc..0x19f08`）比较旧值和新值；需要修改且原名没有 `.1` 时，在 `0x19e44` 调用 **`0x19920..0x19cdc`** 的克隆助手，再把名称改为 `原名+.1`。助手寻找同名修改槽；找到就复用，否则扩展数组，然后复制原配置 12 个其他值到 13 元素的新行，写入新名字。修改作用于返回的槽，并更新当前活动名称。它不是把厂家原预设直接改名覆盖。

证据：[SetImgProp](../reference/dp67/analysis/disassembly/set-image-property.arm.txt)、[正确的克隆助手](../reference/dp67/analysis/disassembly/clone-modified-slot.arm.txt)。`0x2f7fc..0x2f82c` 已证实以字段索引 6（Light）调用该路径，见 [Light 调用点](../reference/dp67/analysis/disassembly/light-property-caller.arm.txt)。ISO 在保存器中确认为列 2，但本轮没有完整映射通用对话框的 ISO 调用点；不能声称已追到 ISO UI 的同一条直接调用。

Prefix 的判断依据是应用对象 `+0x1bb` 的全局标志。同步解析器在 `0x16a58..0x16b04` 识别 ShootSetup 内 Prefix 或 ICCFlag 后设置它；没有发现独立 `PrefixSupport` 顶层属性。原程序的标志跨配置，因而“仅当选中配置原先含 Prefix 才回写”是安卓兼容实现的保守取舍，并非对原版全局标志的逐指令复制。见 [同步解析器](../reference/dp67/analysis/disassembly/sync-property-parser.arm.txt)。

Framing 列表解析器 `0x18120..0x1840c` 先按分号拆分，再按逗号拆分每条，`0x18264` 要求恰好 5 项：名称与 4 个整数。`0x18298..0x18314` 转换并存储整数。写入 ShootSetup.Framing 的应是名称，不能把整条坐标记录当作名称。见 [Framing 解析器](../reference/dp67/analysis/disassembly/framing-list-parser.arm.txt)。

## Ack、超时和同步

`CProtocolLayer::SetCameraParam`（`0x279e8..0x27cc8`）要求已有同步状态，否则返回失败。`0x27afc` 设置最多 3 次尝试；`0x27b0c..0x27b1c` 组合单次事务参数 12,000 ms；`0x27b28` 选择预期回复命令 **13＝Ack**，`0x27b40` 调用事务层。若事务失败且仍处于同步状态，`0x27b50..0x27b8c` 可先 ReSync 后再次尝试。正常路径没有在每次设置前无条件发 RequestSync。见 [SetCameraParam](../reference/dp67/analysis/disassembly/set-camera-param.arm.txt) 与 [事务层](../reference/dp67/analysis/disassembly/execute-transaction.arm.txt)。

接收路由 `0x24a44..0x25128` 在 `0x24cec..0x24d04` 按命令类型匹配等待中的回复，另接受 Error；`0x24d40..0x24d48` 单独处理异步 PictureTaken。命令表中 Sync＝11、PictureTaken＝12、Ack＝13、Error＝14。这里未建立事务 ID 证据，不能把照片通知或任意 Sync 当作写入成功，也不应根据原版重试次数自动重复有副作用的请求。见 [接收路由](../reference/dp67/analysis/disassembly/receive-command.arm.txt) 与 [ReSync](../reference/dp67/analysis/disassembly/resync.arm.txt)。

SetPictureParams 与 ImageFunction 的协议包装器也已保留，分别为 `0x27708..0x279e8`、`0x27f9c..0x28270`，用于核对同类事务机制。本轮没有完成这些高层所有参数组合的逐项对照，不能据此断言任意设置操作均与 WiView 等价。也没有完整还原每条图像分块、心跳、节流或物理重连路径；对用户当前“读图正常、改设置断连”的现象，优先采用有证据的设置构造差异作兼容实验。

## 蓝牙服务、SDK 与 CEVersion 边界

从 DP-67 `BTSDKC~1.002` 的 SPP GUID 导出读取到 **`00001101-0000-1000-8000-00805f9b34fb`**。DP-67 的两个 Widcomm 模块与两版 WiView 均为 1.4.1 Build 74；排除导出表时间戳后，六个 PE 节 `.text/.rdata/.data/.pdata/.rsrc/.reloc` 的哈希逐一相同。整文件哈希不同，Leaf 的 `0BtStack` 包装器也不是同一文件。因此没有证据把本次差异归结为“DP-67 换了更稳定的核心蓝牙 SDK”，也没有宣称整个蓝牙栈完全相同。

主程序中对 `CEVersion` 与 `EnableTakenPicture` 的 ASCII、UTF-16LE 搜索均为 0；已观察的 ReSync 路径包含 Synchronize／RequestSync。本结论是限定静态搜索，不证明所有间接构造均不存在。**Leaf2Android 当前启动流程也未自动发送这两个字段**，它们只存在于实验室手动模板，因此此次兼容修复不据此新增版本宣告或通知开关。

## 复核与下一步验证

[机器证据](../reference/dp67/analysis/connection-and-setting-evidence.json) 同时包含字段顺序、15 个函数边界、字面量引用、修改槽／Prefix／Framing、事务参数、命令表和 SDK 节对照。逐文件哈希见 [独立库存](../reference/dp67/inventory.json)。依赖为项目 `requirements-research.txt`、Windows 系统 `expand.exe`：

```powershell
.venv\Scripts\python.exe reference\dp67\analyze_dp67.py --verify
```

该命令在临时目录从已归档 Setup.exe 重生成所有厂商提取产物与分析并逐字节比较，不依赖或解码私人备份，也不修改 reference。取得最初用户 ZIP 时可用 `--zip "C:\Users\Legion\Downloads\Leaf DP-67 app.zip"` 重新提取；或在 `--verify` 后加 `--zip` 同时核对来源。

安卓侧据此增加的 DP-67 设置方言属于**候选兼容修复，等待真机验证**。验证重点是保存前后完整请求日志、正确 Ack 与随后的 Sync、修改槽的活动名称／ISO／Light 回读，以及失败后会话是否仍可读图。原程序 12 秒、最多 3 次并不构成安卓侧自动重发写请求的依据。未进行物理后背操作、无线抓包或稳定性测量。
