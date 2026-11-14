# 界面检查 / UI verification

以下常规与离线实验室截图来自 v0.3.0 正式签名的 release APK，在 AOSP Android 16 / API 36 模拟器上使用 420 dpi。通过调整模拟屏幕尺寸检查窄屏、宽屏与低高度横屏，以及英文/中文、现代/经典 PDA 的切换；不是 Z Fold7 真机，也不代表 One UI 8.5 或物理折叠已通过验收。

The regular and offline-laboratory screenshots show the signed v0.3.0 release build on an API 36 AOSP emulator (420 dpi). English/Chinese, modern/PDA styles and window resizing are checked; they are not physical Fold7, One UI or camera-back acceptance evidence.

图像为程序生成的离线演示 JPEG，未连接后背。

| 截图 | 模拟像素尺寸 |
| --- | --- |
| [现代界面：英文初始页](modern-empty.png) | 1080 × 2520 |
| [现代界面：英文窄屏](modern-narrow.png) | 1080 × 2520 |
| [经典 PDA：英文窄屏](pda-narrow.png) | 1080 × 2520 |
| [现代界面：英文宽屏](modern-wide.png) | 2208 × 1840 |
| [经典 PDA：英文宽屏](pda-wide.png) | 2208 × 1840 |
| [现代界面：英文低高度横屏](modern-landscape.png) | 2520 × 1080 |
| [中文：现代窄屏](modern-narrow-zh.png) | 1080 × 2520 |
| [中文：经典 PDA 窄屏](pda-narrow-zh.png) | 1080 × 2520 |
| [中文：现代宽屏](modern-wide-zh.png) | 2208 × 1840 |
| [英文：离线实验室](laboratory-empty-en.png) | 1080 × 2520 |
| [中文：离线实验室](laboratory-empty-zh.png) | 1080 × 2520 |

以下五图来自 v0.3.1 debug instrumentation，用合成协议对端验证真实 Compose 控件：ISO 选择、完整配置发送及回读、DP-67 修改槽与中英文状态，以及手动实验请求。它们不是真实蓝牙后背截图，也不证明硬件控制已成功。

The following v0.3.1 debug instrumentation captures use a synthetic wire peer to exercise the actual Compose controls. They do not show a physical Bluetooth back or establish hardware-control support.

- [Back controls, English](back-controls-en.png)
- [后背控制，中文](back-controls-zh.png)
- [Laboratory with a synthetic reply](laboratory-en.png)
- [DP-67 compatibility, English](valeo-dp67-en.png)
- [DP-67 兼容与修改槽，中文](valeo-dp67-zh.png)

本轮另用同一签名从 0.3.0 覆盖升级到 0.3.1，检查旧 256 MiB → 1024 MiB、已有 64 MiB 保留、后来改回 256 MiB 后重启保留、新安装默认 1024 MiB，以及兼容模式跨重启／语言切换保留。以下是签名 v0.3.1 release 的设置截图；检查只作用于本项目 AVD，不连接真实后背。

The signed v0.3.1 release captures below accompany same-signature 0.3.0→0.3.1 cache migration and compatibility-setting persistence checks on the owned AVD only.

- [Maximum default preview cache](settings-cache-en.png)
- [Compatibility settings, English](settings-compatibility-en.png)
- [兼容模式设置，中文](settings-compatibility-zh.png)

升级检查可通过 `tools/check-cache-upgrade-ui.py` 重现；它要求 `dist/` 中有两个版本的签名 APK，并且只会在 `Leaf2Android_API36` 清除测试应用的数据。结果见 [cache-upgrade-verification.json](../../research/reports/cache-upgrade-verification.json)。

`tools/capture-emulator-ui.py` 可重现，脚本仅允许本项目 AVD `Leaf2Android_API36`，会清除该模拟器中的测试应用数据。各图旁的 XML 是同一页面的 UI 结构记录。自动化状态保持、离线状态和合成 JPEG 接收测试见 `app/src/androidTest/`。
