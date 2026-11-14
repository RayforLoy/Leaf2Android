# Valeo／iPAQ 连接稳定性：第一手反馈核查

[English](valeo-connection-research.en.md) · [项目首页](../README.md)

研究日期：**2026-11-14**。本次只读检索原厂文档和公开使用反馈，不运行原厂程序、不连接或改写后背，也不以论坛意见作为已完成修复。

## 当前能成立的结论

- **网上确有 Valeo＋iPAQ 连接掉线的第一手反馈。** 最明确的一例是 Valeo 22Wi 原用户 bcroslin；但没有给出 PDA 型号、固件或应用版本，不能标成 WiView 1.0.3 的特定缺陷。[2009-07-21 原帖 #14](https://forum.luminous-landscape.com/index.php?topic=33290.msg299291#msg299291)
- **未找到 DP-67 比 WiView 更稳定的同机、同固件、同蓝牙条件 A/B 记录。** “DP-67”还可能指软件、iPAQ 显控设备或专用线缆，改变连接方式不能当作只更换软件的对照。原厂 DP-67 软件本身支持 Serial 和 Bluetooth。[原厂 DP-67 手册，印刷页 53–54](https://mercurycamera.com/wp-content/uploads/2018/10/leaf_DP67_User_Guide.pdf#page=64)
- **未找到“读图正常，写 ISO／ShootSetup 立即导致断线”的公开直接证实，也未找到对应 WiView 1.0.3 已确认修复说明。** 下表中的一般掉线、首次配对失败和重启后的配置不一致分别记录，不合并成同一个故障。

## 本轮用户反馈：与网络材料分开

项目用户于本轮报告：**读取照片正常，修改设置时才断开**。随后截图显示现有应用的超时信息，含 `back response timed out; session disconnected...`。这表明应用在等待应答超时后主动关闭会话。

截图本身不能区分：后背没有发 Ack、应用没有识别／匹配到回复、回复被其他事务干扰、后背尚未完成处理，或更早就已物理失联。尚无本报告可核验的逐条 TX／RX、完整请求、准确超时阶段及实机修复复测结果；不写成“后背主动踢掉连接”或“WiView 的已知 bug”。

项目已调整设置事务与自动取图的排队方式；**本次网络研究没有证明它是真实断线原因**。后续 [DP-67 二进制分析](dp67-analysis.md) 已确认 12 个核心字段、`.1` 克隆槽和不附全局属性的写入封装，App 已加入独立兼容路径；真实设备上的效果仍待验证。本报告没有把普通忙灯、越界断连或配对失败当作该写入超时的解释，也没有宣称已修复。

## 可追溯的用户记录

下列引文刻意保持很短。同一页面的中英文引文合计仍少于 25 个英文单词。日期取帖子正文；没有精确日期时明确留空。

| 记录 | 日期、设备与版本 | 原话及报告内容 | 能否证明方案有效／本轮同类故障 |
| --- | --- | --- | --- |
| **R1：明确第一手掉线** — bcroslin | 2009-07-19 自述曾拥有 Valeo 22Wi 约一年；07-21 #14 回答 iPAQ 设置 ISO／WB 的使用问题。PDA／固件／软件版本未给 | “connection would drop”。报告偶发不可靠 | 没说“按下写 ISO 就掉线”；没有补丁或前后复测。[原帖](https://forum.luminous-landscape.com/index.php?topic=33290.msg299291#msg299291) |
| **R2：相反的第一手经验** — yaya／Yair | 2009-07-20 #11、#13；回忆 2004 年携 Valeo 22Wi、Contax、iPAQ 旅行 16 天，后文明确提 DP-67。版本未给 | “worked quite well” | 说明有成功使用经验；没有与 WiView 1.0.3 对照。[同帖 #11、#13](https://forum.luminous-landscape.com/index.php?topic=33290) |
| **R3：设置可写，本次会话有效；重启不一致** — rhizomeblur | 自报 Valeo 17wi、自制 CF magazine、Mamiya AFD／Graflex。主题始于 2014-08-18；回复仅显示相对日期，未核实日历日。应用／PDA／固件版本未给 | “very tenuous”。同时描述蓝牙脆弱及重启后 ISO／机身模式不一致 | 写配置可在当次会话生效；是重启／持久化问题，未报告写设置立即断开，未见解决跟进。[回复永久链接](https://www.flickr.com/groups/2421066@N24/discuss/72157646580953491/72157650855787129/) |
| **R4：DP67 与 WiView 均未解决首次连接问题** — philmar1986 | 2015-03-23／26；Valeo 22Wi＋HP iPAQ 4700 先用 DP67，后换另一台 Windows Mobile 6.1 PDA 用 WiView。具体软件／后背固件版本未给 | “it still says invalid Pin” | 重装 DP67、换 PDA／WiView 后问题仍在；不是已连接后的掉线，也不是同 PDA A/B。帖子没有已验证解决方案。[原帖及两次跟进](https://forum.luminous-landscape.com/index.php?topic=98944.0) |

R1、R2 是同一讨论页的不同使用者，不是受控实验。R3 的自制存储是重要条件，不能推广到原厂 Digital Magazine；该用户另有一篇概述蓝牙易断的 [Flickr 回复](https://www.flickr.com/groups/616912@N22/discuss/72157639428580426/)，**不另算独立使用者**。

## 经常被转述、但证明力有限的帖子

| 记录 | 原话、上下文与日期 | 限制 |
| --- | --- | --- |
| paul_jones 的掉线说法 | 2009-10-03 #2 用 “ive been told” 转述 iPAQ 经常失联 | 虽自称曾有 Valeo，这句明确是听说，不计入第一手掉线记录。[原帖 #2](https://forum.luminous-landscape.com/index.php?topic=38107) |
| James R Russell 的稳定说法 | 2009-10-04 #7 说兼容 iPAQ 配合较新的 Version 8 软件 “fairly stable”，同时偏好电脑联机 | 未给 DP-67 版本；Version 8 指代也不足以当作 DP-67 可执行文件版本。没有同机升级／降级前后结果。[原帖 #7](https://forum.luminous-landscape.com/index.php?topic=38107) |

这些文字可解释“网上有人这么说”的来源，不能证明“DP-67 一定更稳”或“WiView 必然有同一故障”。历史电脑联机意见只作记录，不改变本项目手机直连的目标。

## 原厂及配套文档能确认什么

### DP-67 原厂手册

Creo《Leaf DP-67 Portable Application User Guide》，**731-00157A-EN Rev B，2004 年 3 月修订**：印刷页 53–54 明确提供 Serial／Bluetooth；页 58 说明超过无线范围时会断连，返回范围后重同步。这是范围变化的正常行为，**不是写参数导致超时的说明**。该手册没有 DP-67／WiView 稳定性比较。[原厂文档存档 PDF](https://mercurycamera.com/wp-content/uploads/2018/10/leaf_DP67_User_Guide.pdf#page=64)

### WiView 1.0.3 发行说明

Kodak《Leaf Capture 11.1.1 including Leaf WiView 1.0.3》，**739-00498A-EN，2008-08-13 修订**：列出 Windows Mobile 6、VGA 和 hx4700 等兼容支持。本次核对其 Fixed／Known Issues，未找到 Valeo 写 ISO／ShootSetup 后断连的条目。历史列表中的 “Increased stability” 属 Leaf Capture 11 总列表，不能改写成 WiView 1.0.3 对此问题的修复。[原厂文档文字存档](https://manuzoid.com/manuals/rbLMB-Kodak%20Aptus%2065S%20Release%20note)

### CameraType 不受支持的另一种问题

原厂《Leaf Capture V8 User Guide》印刷页 74–75 描述电脑联机时 CameraType 不受支持，要求选择合适类型后重试连接；本次未确认该手册修订日。它是特定连接校验提示，**不是 WiView 写入超时的修复证明**。[原厂手册文字存档](https://manualzilla.com/doc/6922553/leaf-capture-v8-user-guide)

### 线缆绕过蓝牙与配置存储

Mercury 的自家 Digital Graflok 套件说明提到 DP-67 专用线缆可 “bypass bluetooth”，并称 ISO／文件名等更改会存到其 CF 配置。PDF 未列修订日，URL 的 `2017/12` 不能当确定发布日期。它是配套厂家使用说明，不是 Leaf 软件对比测试；没有证明能修复本轮设置超时。[套件说明 PDF，第 3–4 页](https://mercurycamera.com/wp-content/uploads/2017/12/Digital-Graflock-23-Back-Instructions.pdf#page=3)

## 所谓解决方案的验证状态

| 做法／说法 | 本次查到的验证结果 |
| --- | --- |
| 重装程序或在 DP67／WiView 之间切换 | R4 有明确失败跟进；不能作为通用修复 |
| 降回某个 DP-67 版本更稳定 | 未找到同硬件／同蓝牙／同固件 A/B，也未找到针对写参数超时的成功复测 |
| 使用 Serial 专用线缆 | 文档确认这种传输方式存在；不证明“只换软件”能解决问题，也未见本轮症状前后对照 |
| 较新 Version 8／电脑联机 | 只有未给完整版本的经验意见；与手机直连不同，不当作项目解决方案 |
| 修改协议发送和回复处理 | 需本项目真实请求／回复及设备复测；网络材料没有证明某个精确写法已修好当前问题 |

## 检索边界与可复核问题

检索覆盖 Leaf WiView、Wi-view、DP-67／DP67、Valeo 17wi／22wi、iPAQ，以及 connection、drop、stable、ISO、ShootSetup、settings、timeout、unsupported 的组合；针对 Phase One／Capture One、GetDPI、Luminous Landscape 和中文关键字单独检索。未找到可引用的 GetDPI／Phase One 原帖能证明所问的 A/B 或写 ISO 立即掉线。公开索引与旧站可访问性有限，**未找到不等于不存在**。

排除 Nissan Leaf／LeafSpy／Leaf Pro、汽车零件厂 Valeo、显示器 DP，以及只有转载或销售描述的页面；不把一般蓝牙建议填充为这台后背的实测方案。

后续针对性验证应记录：同一后背／固件和 PDA 的精确应用版本、同一连接方式、只改一个字段的完整请求、最后一帧 TX／RX、超时发生在写 Ack 还是回读阶段、断开前后实际设置。只有这些记录才可能支持“DP-67 写法更兼容”或“事务互插导致超时”等结论；目前都属于待验证问题。
