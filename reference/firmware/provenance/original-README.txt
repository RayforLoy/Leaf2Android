已提取的 Leaf 固件目录

目录来源：
CaptureOne.Mac.7.1.4：84 个文件，138723283 字节。
CaptureOne.Mac.7.1.6：84 个文件，138723283 字节。
CaptureOne.Win.11.3.2：84 个文件，138723283 字节。

三份目录中的 FirmWareAptusII.UPD 均为 5.041，大小 6,021,144 字节。
SHA-256：80304f7b69c370f2cea9804d2098d20a775bc09de30899d608376d559d130bc6

Capture One Mac 7.1.4 的应用内版本标注为 7.1.4.92。该包没有包含 5.048。
Windows 7.1.4 尚未取得，未作结论。

各目录保留完整 Firmware 子目录结构，文件内容未修改；每个来源的 manifest.json 记录文件列表、哈希、来源链接和 UPD 内部版本。
版本识别：UPD 文件以 LEAF FRM 开头，其偏移 20 的大端 32 位整数 5041 对应 5.041。BootRecovery.UPD 的特殊版本值单独保留，不作为普通版本编号。

上述三份 Capture One 目录可作为已核验的 5.041 备份；5.048 独立更新包尚未取得。

Leaf Capture Windows 11.2.9（新增静态提取）
来源：D:\leaf\Leaf Capture 11.2.9 Windows .exe
目录：LeafCapture.Win.11.2.9
58 个文件，46,988,349 字节；保留 MSI 中 Firmware 目录下的原始文件名及子目录。
FirmwareAFi.UPD：主固件 4.960。
FirmwareAptus.UPD、FirmwareAptusS.UPD：主固件 4.701。
FirmWareAptusII.BIN：界面资源版本 1.9.6。
FirmWareAptus.BIN：界面资源版本 1.8.6。
另含 FirmWareRBFs 下的 UBF 板级/FPGA 资源。
未发现主固件 5.048；不提供比当前设备 5.041 更新的主固件。
FirmwareAFi.UPD 是否适用于 Aptus-II 5，不能仅从文件名确认。
文件均直接从安装包提取并校验 SHA-256，未运行安装程序、未连接或刷写后背。
完整文件清单、内部版本及哈希见 LeafCapture.Win.11.2.9-manifest.json。
