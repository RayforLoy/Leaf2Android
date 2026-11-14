# Leaf DP-67 1.2.1 静态研究资料

[English](README.en.md)

此目录保留用户提供的 `Leaf DP-67 app.zip` 中可确认的厂商安装器、其内嵌 CAB、7 个 CAB 成员和可重复生成的分析记录。安装器资源标为 V1.2.1，主程序 `LEAFDP~2.001` 的文件版本为 **1, 2, 1, 2**。研究日期：2026-11-14。

完整 ZIP 含 4 份 PDA 备份，本目录没有收录完整 ZIP、PBF、Mac 元数据或临时文件。仅读取 PBF 标识的前 12 字节 `POCKETPCIMAG` 作分类，未解码备份内容、设备配置或凭据。来源只记录文件名及 SHA-256，没有编造公开下载地址。整个过程没有运行或加载厂商 EXE/DLL。

| 路径 | 内容 |
| --- | --- |
| `original/LeafDP67-1.2.1-Setup.exe` | 原始 ARM Windows CE 安装器，1,314,304 字节 |
| `original/LeafDP67-1.2.1.CAB` | 从安装器资源 `BIN/103` 原样提取，1,288,384 字节 |
| `extracted/` | CAB 的 7 个原始成员，历史厂商文件名不变 |
| `analysis/binary-analysis.json` | 成员大小、哈希、PE 属性、导入导出及 SDK 节比较所需数据 |
| `analysis/connection-and-setting-evidence.json` | 设置写入、修改槽、Prefix、Framing、Ack 和连接证据及边界 |
| `analysis/disassembly/` | 15 个限定函数的 ARM 反汇编，含 VA 和文件偏移 |
| `provenance.json` | ZIP、安装器、CAB 的来源、哈希与排除项 |
| `inventory.json` | 本目录除自身与 Python 缓存外的逐文件大小和 SHA-256 |
| `analyze_dp67.py` | 静态提取、分析和只读复核脚本 |

完整结论见 [DP-67 分析记录](../../docs/dp67-analysis.md)。发现的 12 核心字段、可选 Prefix 和 `.1` 修改槽差异支持开展 Valeo 兼容实验；尚未通过真实后背写设置或蓝牙抓包证明这些差异就是断连原因。

在项目根目录安装 `requirements-research.txt` 所列依赖后，Windows 上运行：

```powershell
.venv\Scripts\python.exe reference\dp67\analyze_dp67.py --zip "C:\Users\Legion\Downloads\Leaf DP-67 app.zip"
.venv\Scripts\python.exe reference\dp67\analyze_dp67.py --verify
```

第二条命令无需用户 ZIP：它检查完整库存，在系统临时目录从已归档安装器重新提取 CAB、生成分析，再与已保存证据逐字节比较。该复核不修改本目录。也可加 `--zip <原始 ZIP>` 同时复核来源。CAB 解压只调用系统 `expand.exe`，不会执行安装器。
