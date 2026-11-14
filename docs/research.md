# 原版资料与协议研究复现

[English](research.en.md) · [项目首页](../README.md) · [参考资料清单](../reference/README.md)

原厂安装介质、解包组件和静态分析统一存放在 `reference/`。Android 应用不会载入或执行这些程序。它们也不是应用的构建运行依赖。

2026-11-14 新增用户提供的 DP-67 1.2.1 介质：[独立分析与复现说明](dp67-analysis.md)、[参考子目录](../reference/dp67/)。只归档厂商安装器、内嵌 CAB、提取组件和证据，不发布完整 ZIP 与四份 PDA 备份。仅用已归档安装器也可执行 `python reference/dp67/analyze_dp67.py --verify` 重生成临时证据并逐字节核对，不需要私人备份或原 ZIP。

## 文件与身份

| 类别 | 位置 |
| --- | --- |
| WiView 1.0.2 | `reference/wiview/1.0.2/LeafWiView-1.0.2.CAB` |
| WiView 1.0.3 | `reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB` |
| 原厂指南 | `reference/wiview/guides/` |
| 来源记录 | `reference/wiview/provenance/` |
| 当前输入锁定 | `reference/sources.lock.json` |
| 两版解包与分析 | `reference/analysis/wiview-1.0.2/`、`wiview-1.0.3/` |
| 历史重复解包 | `reference/analysis/legacy-extraction/` |
| 版本差异 / 协议锚点 | `reference/analysis/version-comparison.json`、`protocol-evidence.json` |
| 库存与校验 | `reference/inventory.json` |
| 后背固件状态 | `reference/firmware/README.md` |

2026-11-11 已收录用户在 `D:\leaf` 找到的真实固件：310 个来源条目去重为 89 份原始文件，包含主 UPD、恢复 UPD、CE BIN、FPGA UBF 和配置，并保留核心重建与分析。见 [固件来源与清单](../reference/firmware/README.md)。WiView CAB 中的客户端 EXE / DLL 与这些后背固件分别分类。哈希锁定原字节，不代替厂商签名认证；本次不执行厂商程序、不刷写后背。

1.0.2 来自 Internet Archive 的 Leaf Capture 11 社区光盘文件备份；1.0.3 由用户提供。历史获取记录中的旧路径反映当时获取位置，当前有效位置以 source lock 和 inventory 为准。详见双语参考说明。

## 环境与校验

在项目根目录运行。工具支持 Python 3.12；依赖固定在 `requirements-research.txt`。

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-research.txt
.\.venv\Scripts\python.exe tools/acquire_sources.py verify
```

若公开输入缺失，可使用 `fetch` 获取锁定的公开 URL 并校验。用户提供的 1.0.3 没有被臆造远程下载地址，须使用原始提交文件或发布的参考包。

```powershell
.\.venv\Scripts\python.exe tools/acquire_sources.py fetch
```

## 重新解包和建立证据

CAB 解包使用 Windows `expand.exe`，不启动安装器或 ARM 程序：

```powershell
.\.venv\Scripts\python.exe tools/analyze_wiview.py
.\.venv\Scripts\python.exe tools/analyze_wiview.py --cab "reference/wiview/1.0.3/Leaf WiView11_1_0_3.CAB" --output reference/analysis/wiview-1.0.3
.\.venv\Scripts\python.exe tools/compare_versions.py
.\.venv\Scripts\python.exe tools/build_evidence.py
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

分析输出包含 PE 导入 / 导出、段哈希、版本资源、ASCII / UTF-16 字符串与偏移。比较脚本区分代码变化与导出表时间戳变化，协议证据脚本验证固定样本的哈希、ARM 指令与服务 GUID。完整字段见生成的 JSON，不从字符串数量推断后背支持数量。

查看明确范围的反汇编或字符串引用：

```powershell
.\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --va 0x2a988 --bytes 0x240
.\.venv\Scripts\python.exe tools/arm_view.py reference/analysis/wiview-1.0.3/extracted/00LeafCE.001 --xref 0x10e43c
```

地址是样本首选虚拟地址，不是 Android 地址，也不是直接的文件偏移。

## 离线流回放

```powershell
.\.venv\Scripts\python.exe tools/replay_stream.py research/fixtures/synthetic-control-and-attachment.hex --hex --chunk-size 1
```

样本由静态格式人工构造，不包含真实后背通信。任意二进制附件用于验证分帧，不作为 JPEG 画质样本。Android 仓库测试里的可解码合成 JPEG 是另一类测试数据，仍不代表实拍。

## 新增研究结果的要求

记录来源、版本、大小、SHA-256 和分析工具版本。未知字段保留原字节并注明编码假设。合成、静态分析、真实抓包与真实操作结果分别标识。不要修改原厂输入来让证据脚本通过，也不要把 `.mos` 文件名、RawFrameBuffer 字符串或未确认前缀当作完整原片 / Live View 证据。

真实通信样本发布前，检查并处理照片、路径、版权字段和备注等个人数据；系统配对 PIN 和发布签名材料不应进入样本。
