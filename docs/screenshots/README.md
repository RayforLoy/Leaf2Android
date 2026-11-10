# 第一版界面检查

以下截图来自 v0.2.0 正式签名的 release APK，在 AOSP Android 16 / API 36 模拟器上使用 420 dpi。通过调整模拟屏幕尺寸检查窄屏、宽屏与低高度横屏，以及英文/中文、现代/经典 PDA 的切换；不是 Z Fold7 真机，也不代表 One UI 8.5 或物理折叠已通过验收。

These screenshots show the signed v0.2.0 release build on an API 36 AOSP emulator (420 dpi). English/Chinese, modern/PDA styles and window resizing are checked; they are not physical Fold7, One UI or camera-back acceptance evidence.

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

`tools/capture-emulator-ui.py` 可重现，脚本仅允许本项目 AVD `Leaf2Android_API36`，会清除该模拟器中的测试应用数据。各图旁的 XML 是同一页面的 UI 结构记录。自动化状态保持、离线状态和合成 JPEG 接收测试见 `app/src/androidTest/`。
