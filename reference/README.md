# Leaf2Android 参考资料

[English](README.en.md)

最后整理：2026-11-10。这里集中保存原版 Leaf WiView 安装介质、原厂说明、下载来源记录、安装包提取结果和静态协议研究。Android 应用源码位于项目根目录的 `app/`、`protocol/`；人工通信夹具位于 `research/fixtures/`。

## 目录与资料性质

```text
reference/
├── README.md / README.en.md
├── sources.lock.json              # 6 个原始输入的来源、大小和 SHA-256
├── inventory.json                 # 当前资料清单、分类、来源和校验值
├── relocation-manifest.json       # 整理前后路径及搬移时的原始字节校验
├── build_inventory.py             # 重建资料清单，Python 标准库即可运行
├── firmware/
│   └── README.md                  # 当前未取得真正后背固件的说明
├── wiview/
│   ├── 1.0.2/                     # CAB、Mac Installer.data 和原版 ReadMe
│   ├── 1.0.3/                     # 用户提供的 1.0.3 CAB
│   ├── guides/                    # 2007、2008 原厂安装指南
│   └── provenance/                # 原始下载记录和公开光盘存档元数据
└── analysis/
    ├── wiview-1.0.2/              # PE 清单、字符串及 extracted/ 原始成员
    ├── wiview-1.0.3/
    ├── legacy-extraction/         # 早期 1.0.2 提取副本，保留追溯
    ├── protocol-evidence.json     # 已重新核对的 ARM 指令与服务 GUID 锚点
    └── version-comparison.json    # 两版本组件、节和命令字符串比较
```

**后背固件与 PDA 客户端必须区分。** 本次资料里没有可识别的 Aptus / Valeo 后背固件镜像。提取出的 `00LeafCE.001`、Leaf 蓝牙封装和 WIDCOMM DLL 是 Windows CE 客户端组件，不是后背固件。详见 [firmware/README.md](firmware/README.md)。

## 原始介质与校验值

`sources.lock.json` 的 6 条记录是获取和核验原始输入的依据。校验值识别具体字节，不能独立证明社区光盘备份的原厂真实性。

| 输入 | 字节数 | SHA-256 |
| --- | ---: | --- |
| [WiView 1.0.2 CAB](wiview/1.0.2/LeafWiView-1.0.2.CAB) | 2,683,140 | `5f38aab055b2cb46560e59aa87d019a76a0e7e4a2d6866fbbc58b8e5eac56f85` |
| [WiView 1.0.3 CAB](<wiview/1.0.3/Leaf WiView11_1_0_3.CAB>) | 2,729,244 | `0f0a34891b3b4f7c70c6681092da38cd5d0bb9cadc82ff7b6dcb793c399b4cce` |
| [Mac 安装数据](wiview/1.0.2/WiView-1.0.2-mac-Installer.data) | 835,922 | `6a864adb792c657d093135602c5413ba5a8ed98c87ba0730d9415cd9e30b23d4` |
| [原版 ReadMe](wiview/1.0.2/WiView-1.0.2-ReadMe.txt) | 5,570 | `89e57de9c04d2a08bff0f59b0a43e6878de8940b8d21f5f4181e84e4cf8cc912` |
| [2007 安装指南](wiview/guides/LeafWiView-Installation-2007.pdf) | 743,496 | `777b294d5dc1f96f014a3bc9d6871fe3d3e920281105941705b5de82bbde4f23` |
| [2008 / 1.0.3 安装指南](wiview/guides/LeafWiView-1.0.3-Installation-2008.pdf) | 563,679 | `dc2d731c083df87cc05722691b4807501818e6c075b199b6462e8c97336a8aef` |

WiView 1.0.2、Mac 安装数据、ReadMe 和 2007 指南来自 [Leaf Capture 11 社区光盘备份](https://archive.org/details/leaf-capture-11)。2008 指南来自[原 Leaf 官网的互联网档案馆快照](https://web.archive.org/web/20081230142347id_/http://www.leaf-photography.com/imgs/uploads/user%20guides/leafwiview_103_installation.pdf)。1.0.3 CAB 由用户提供，没有为它虚构公开下载地址。锁定文件保留检索时间、原 URL、存档校验及来源说明。

`wiview/provenance/downloads.json` 是原始下载日志，里面的旧相对路径记录获取当时的位置，供历史追溯；当前路径以 `sources.lock.json` 为准。原始厂商介质、提取成员、下载日志和存档元数据在搬移时逐文件比对 SHA-256，未修改其内容。清单和分析报告属于可再生成的项目元数据。

## 可重复核验与分析

从项目根目录 `D:\Leaf2Android` 执行。完整静态分析使用 `requirements-research.txt` 中锁定的 `pefile`、`capstone`；CAB 提取调用 Windows 自带 `expand.exe`，不会启动安装器或加载厂商程序代码。

```powershell
python -m venv --system-site-packages .venv
& .\.venv\Scripts\python.exe -m pip install -r requirements-research.txt
& .\.venv\Scripts\python.exe tools/acquire_sources.py verify
& .\.venv\Scripts\python.exe tools/analyze_wiview.py
& .\.venv\Scripts\python.exe tools/analyze_wiview.py --cab "reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB" --output reference/analysis/wiview-1.0.3
& .\.venv\Scripts\python.exe tools/compare_versions.py
& .\.venv\Scripts\python.exe tools/build_evidence.py
& .\.venv\Scripts\python.exe -m unittest discover -s tests -v
python reference/build_inventory.py
```

缺少公开输入时，可执行 `tools/acquire_sources.py fetch` 按锁定地址下载并核验；用户提供的 CAB 仍须放在清单指定的本地路径。脚本拒绝越出项目目录、截断、大小或 SHA-256 不符的输入，提取器拒绝 CAB 中的路径穿越和重复成员名。

查看指定 ARM 函数及协议锚点：

```powershell
& .\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --va 0x2a988 --bytes 0x240
& .\.venv\Scripts\python.exe tools/replay_stream.py research/fixtures/synthetic-control-and-attachment.hex --hex --chunk-size 1
```

离线回放只读取人工样本，不打开蓝牙，也不代表收到过真实后背图像。

## 证据结论与边界

- 两版客户端的底层 WIDCOMM 蓝牙组件相同，静态调用使用标准 SPP GUID `00001101-0000-1000-8000-00805f9b34fb`。
- 控制头按 `^` 到首个 `>` 分帧，`attachment` 指定后续原始二进制字节数；附件内部的分隔符不能当作控制消息。
- 两 CAB 的包标签分别为 1.0.2、1.0.3；程序内部构造的日志版本为 1.15、1.17，不能用后者臆造握手字段。
- 静态字符串和指令锚点不是目标后背实测。完整握手、固件差异、非 ASCII 文本、完整 MOS 读取和连续实时取景仍待真实设备证据。
- [协议文档](../docs/protocol.md) 与 [版本分析](../docs/version-analysis.md) 解释具体函数地址及实现边界。当前应用支持表位于 [项目实施状态](../docs/implementation-status.md)。

这里的历史厂商软件保留原始名称、版本、作者和来源；项目改名不会改写厂商文件。它们是独立参考资料，不编入 Android APK，也不与 Leaf2Android 自行编写的代码混为一套授权声明。发布源码或参考资料时应保留相应来源和权利说明。
