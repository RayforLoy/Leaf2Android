# Leaf2Android 0.2.0 安装、使用与构建

[English](quick-start.en.md) · [项目首页](../README.md) · [功能支持表](implementation-status.md)

## 选择安装包

从 [v0.2.0 预发布页](https://github.com/RayforLoy/Leaf2Android/releases/tag/v0.2.0) 下载附件，或使用本地 `dist/`。

| 文件 | 用途 |
| --- | --- |
| `Leaf2Android-0.2.0.apk` | 发布密钥签名的 release 测试应用 |
| `Leaf2Android-0.2.0-debug.apk` | 开发调试包，签名可能与 release 不同 |
| `Leaf2Android-0.2.0-source.zip` | 可构建源码与文档 |
| 参考资料压缩包 | 原版 WiView、静态分析与来源记录，不作为 APK 内嵌运行库 |
| `*.sha256` / 校验清单 | 对应附件的 SHA-256 |

附件以对应发布页为准。应用 ID 为 `com.leaf2android`，最低 Android 12 / API 31，编译与目标版本 Android 16 / API 36。该预发布不代表 Aptus / Valeo 全系列兼容已验收。

## 安装与签名

将 APK 传到手机，在文件管理器中打开安装，根据 Android 提示允许当前来源安装应用。连接开发环境后也可执行：

```powershell
adb install -r .\dist\Leaf2Android-0.2.0.apk
```

同应用 ID 的覆盖更新还要求签名一致。若从 debug 切换 release 时出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先保存需要的预览并另行记录本机备注 / 标记，再卸载旧包后安装。卸载会删除应用私有数据。此前误名测试包与 `com.leaf2android` 是不同应用，不会自动迁移数据。

发布者须保留 release keystore；私钥、密码和个人 `local.properties` 不提交仓库。APK 不包含原厂 EXE、DLL 或 CAB。

## 语言与风格

首次启动使用现代界面，语言跟随系统。设置中可选择跟随系统、简体中文或 English；风格可独立选择现代或经典 PDA。两者使用共享照片与会话仓库，保留放大位置。

语言只控制界面文案，不翻译设备实际名称、原始照片路径、用户备注或后背元数据。后背协议编码另行选择，不能用“界面切换英文”推断无线编码已经改变。

## 先检查离线演示

从拍摄页或设置中加载演示图。图像由程序生成并标为离线，连接状态仍是未连接。

- 双指缩放和拖动；双击放大 / 复位，适合屏幕按钮可复位。
- 工具栏提供 JPEG 直方图、JPEG 明暗警示、备注和预览保存。
- 星标与本机备注保存在手机，不会自动写回后背。
- 直方图和警示使用 JPEG 采样，不代表读取了 RAW 像素。
- 窄屏单栏、宽屏双栏；真实 Fold 内外屏、旋转和 One UI 分屏仍待验收。

## 手机直接连接 Aptus / Valeo

1. 后背开启 Wireless，关闭其他占用它的 WiView 客户端。
2. 连接页授予“附近设备”权限，开启手机蓝牙。
3. 搜索并识别后背，可先系统配对，在 Android 对话框输入后背当前 PIN，再连接。应用不会猜测 PIN。
4. 使用经典蓝牙 SPP / RFCOMM UUID `00001101-0000-1000-8000-00805f9b34fb`。启动依次尝试 RequestSync、CurrentTime、根目录，同步完成才显示就绪。
5. 技术相机机械快门手动释放。文件名明确且已保存的拍摄通知会触发预览队列，跟随新片开启时自动切换到新预览。
6. 回放可浏览后背子目录、按名称 / 接收时间排序和筛选本机星标。未缓存预览需保持后背连接。

原始实机目标是 Aptus II 5。系列名称不代表其他型号自动兼容；首次测试请记录准确型号、固件、手机系统及实际蓝牙服务。当前完整会话与真实预览尚未验收。

## 照片信息、备注与文件

信息页显示收到的预览尺寸、大小、SHA-256 和已取得的属性；连接时可读取后背照片信息。未解释的 XMP 附件不显示为已成功解析的信息。

保存本机备注只写手机。写入后背备注使用 `SetPictureParams`，须匹配成功回复；非零或无法解释的错误码不能算成功，之后仍需回读核对。引号、控制字符及 `& < > ^` 暂不允许发送，避免使用未经验证的转义规则。

协议默认 Windows-1252，可断开后选择 GBK 或 UTF-8 供实测。ASCII 路径不受影响；无法编码的文本拒绝发送，不替换为问号。

即使照片名为 `.mos`，GetImage 预览也不会被当作完整原片。完整 MOS 下载和连续实时取景尚未实现，当前机械快门也没有遥控执行器。

## 恢复与诊断

配对失效或 PIN 错误时，在 Android 蓝牙设置忘记旧配对并重配，同时确认没有其他客户端占用。仅在旧固件需要时，断开后尝试不安全 RFCOMM；该选项不能保证绕过 Android 的链路要求。

连接等待约 35 秒，普通指令约 20 秒，图像约 60 秒；这些是主机配置，不是后背实测规格。超时关闭会话后需手动重连，避免迟到回复与下一张照片错配。

设置中可查看 / 导出日志。应用不记录系统配对 PIN，日志仍可能包含路径、相机属性和用户元数据，分享前检查。问题报告请附版本、手机系统、准确后背型号 / 固件、步骤与脱敏日志；不要公开签名材料或私人照片。

## 构建

完整 JDK 21、SDK Platform 36、Build Tools 36.0.0；JVM 目标 17。依赖与 Wrapper 校验值固定。Android Studio 的 Gradle JDK 也应选 21。

```powershell
.\tools\build-android.ps1 -Jdk 'C:\path\to\jdk-21' -Sdk "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

构建脚本运行协议测试、Lint 和 APK 构建，并输出到 `dist/`。最后一条需要设备或模拟器。安装 `system-images;android-36;default;x86_64` 后，可运行 `tools/start-test-emulator.ps1`。

release 可由本地准备脚本建立仓库外的签名配置，或设置 `LEAF2ANDROID_KEYSTORE_PATH`、`LEAF2ANDROID_STORE_PASSWORD`、`LEAF2ANDROID_KEY_ALIAS`、`LEAF2ANDROID_KEY_PASSWORD`：

```powershell
.\tools\prepare-release-signing.ps1
.\tools\build-android.ps1 -Release -Jdk 'C:\path\to\jdk-21'
# 标准 Gradle 任务：.\gradlew.bat :app:assembleRelease
```

参考：[蓝牙权限](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)、[RFCOMM](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)、[前台服务](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)、[应用语言](https://developer.android.com/guide/topics/resources/app-languages)、[Android 16 布局](https://developer.android.com/about/versions/16/behavior-changes-16)。
