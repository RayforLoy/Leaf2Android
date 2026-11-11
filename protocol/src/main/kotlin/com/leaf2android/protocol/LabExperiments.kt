package com.leaf2android.protocol

import java.time.LocalDateTime

enum class LabImpact(val titleEn: String, val titleZh: String) {
    BACK_WRITE("Writes back settings", "写入后背设置"),
    PHOTO_METADATA("Changes photo metadata", "改写照片元数据"),
    STORAGE_ERASE("Erases the selected storage target", "清除选定存储目标"),
    STORAGE_RECOVERY("Runs storage recovery; effects unverified", "执行存储恢复；影响未验证"),
    PAIRING("May change PIN and invalidate pairing keys", "可能更改 PIN 并使配对密钥失效"),
    NUMBERING("Changes counters or subsequent file numbering", "更改计数器或后续文件编号"),
    COMPATIBILITY("Changes the advertised legacy-client compatibility value", "更改所声明的旧客户端兼容值"),
    FACTORY_SETTINGS("Requests factory settings; reset scope unverified", "请求出厂设置；恢复范围未验证"),
    NOTIFICATIONS("Changes a notification parameter; on/off meaning unverified", "更改通知参数；开关含义未验证"),
    SESSION("Affects protocol/session state", "影响协议或会话状态"),
    READ_PROBE("Requests parameters; output is not guaranteed", "请求参数；不保证有输出"),
}

enum class LabInputKind { TEXT, INTEGER, TIME, PIN, BASE64_XMP, ATTRIBUTE_NAME }

data class LabInput(
    val id: String,
    val titleEn: String,
    val titleZh: String,
    val kind: LabInputKind = LabInputKind.TEXT,
    val required: Boolean = true,
    val allowEmpty: Boolean = false,
    val formatEn: String = "",
    val formatZh: String = "",
    /** Suggestions identify documented syntax/values, never an automatically selected input. */
    val suggestions: List<String> = emptyList(),
) {
    val secret: Boolean get() = kind == LabInputKind.PIN
}

data class LabTemplate(
    val id: String,
    val titleEn: String,
    val titleZh: String,
    val provenance: String,
    val wireTemplate: String,
    val requiresInputs: List<LabInput>,
    val impacts: Set<LabImpact>,
    val notesEn: String,
    val notesZh: String,
    /** Empty means send-only; * observes the first complete frame without proving request/response causality. */
    val defaultExpectedTag: String = "Ack",
    /** Only pinned, statically reconstructed constants are supplied here. Other inputs have no defaults. */
    val knownValues: Map<String, String> = emptyMap(),
) {
    val writesBack: Boolean get() = impacts.any { it in setOf(LabImpact.BACK_WRITE, LabImpact.PHOTO_METADATA,
        LabImpact.STORAGE_ERASE, LabImpact.STORAGE_RECOVERY, LabImpact.PAIRING, LabImpact.NUMBERING,
        LabImpact.COMPATIBILITY, LabImpact.FACTORY_SETTINGS, LabImpact.NOTIFICATIONS) }
    val erasesStorage: Boolean get() = LabImpact.STORAGE_ERASE in impacts
    val changesPairing: Boolean get() = LabImpact.PAIRING in impacts
}

/** Static command templates for USER-INITIATED experiments only. No template is automatically sent.
 * Client construction evidence does not establish wireless reachability, a safe hardware range,
 * successful readback, or compatibility with every Aptus/Valeo model and firmware.
 */
object LabExperiments {
    const val MAX_HEADER_BYTES = 60 * 1024 // Host safety bound, below the existing 64 KiB control-header cap.
    const val MIN_TIMEOUT_MS = 1_000L
    const val MAX_TIMEOUT_MS = 300_000L // Host session bound, not a firmware timing specification.
    const val CE_VERSION_1_0_2 = "17171968" // 0x01060600, literal pool 1.0.2:0x341e8.
    const val CE_VERSION_1_0_3 = "17301504" // 0x01080000, mov r1 at 1.0.3:0x35198.
    private val headerName = Regex("[A-Za-z_][A-Za-z0-9_.:-]{0,127}")
    private val clockShape = Regex("[0-9]{4}(:[0-9]{2}){5}")

    private val path = LabInput("fileName", "Back photo path", "后背照片路径")
    private val xmp = LabInput("xmp", "Original Base64 XMP", "原始 Base64 XMP", LabInputKind.BASE64_XMP,
        formatEn = "Base64 header-attribute form only; existing XMP limits and XML protections apply.",
        formatZh = "只接受 Base64 头属性格式；沿用 XMP 大小限制与 XML 保护。")
    private val storage = LabInput("storageRoot", "Exact back storage target", "后背准确存储目标",
        formatEn = "Enter the device-specific target explicitly. No CF/root target is assumed.",
        formatZh = "必须手动提供设备实际目标；不默认 CF 或根目录。")

    val templates: List<LabTemplate> = listOf(
        LabTemplate("brightness", "Brightness integer experiment", "Brightness 整数实验",
            "WiView 1.0.2 ARM 0x33d34..0x33d90; 1.0.3 0x34b90..0x34bec.",
            "^<SetCameraParam Brightness=\"{brightness}\" />",
            listOf(LabInput("brightness", "Signed decimal integer", "有符号十进制整数", LabInputKind.INTEGER)),
            setOf(LabImpact.BACK_WRITE),
            "The integer constructor is proved. Range, display target and readback are unverified; this is not an exposure-compensation control.",
            "整数构造已证实；量程、显示对象及回读未验证，不能当作曝光补偿控制。"),
        LabTemplate("current-time", "Set CurrentTime", "写入 CurrentTime",
            "WiView 1.0.2 ARM 0x31af4..0x31be0; 1.0.3 0x32950..0x32a3c; format at 0x10f338.",
            "^<SetCameraParam CurrentTime=\"{currentTime}\" />",
            listOf(LabInput("currentTime", "Local date and time", "本地日期与时间", LabInputKind.TIME,
                formatEn = "YYYY:MM:DD:hh:mm:ss; enter explicitly; no automatic clock change.", formatZh = "YYYY:MM:DD:hh:mm:ss；必须手动填写，不自动改时钟。")),
            setOf(LabImpact.BACK_WRITE), "Writes the back clock. An Ack alone does not confirm the resulting time.", "写入后背时钟；仅收到 Ack 不代表实际时间已核对。"),
        LabTemplate("reset-counter", "ResetCounter=yes experiment", "ResetCounter=yes 实验",
            "WiView 1.0.2 ARM 0x34d3c..0x34d7c; 1.0.3 0x35d24..0x35d64; 5.041 PPC 0x9a210 -> 0x9bd8c.",
            "^<SetCameraParam ResetCounter=\"yes\" />", emptyList(), setOf(LabImpact.BACK_WRITE, LabImpact.NUMBERING),
            "May change subsequent file numbering. It is not a proved reset of lifetime TotalFrames; persistence and exact meaning need measurement.",
            "可能改变后续文件编号；未证明会清零终身 TotalFrames，持久性与准确含义需要测量。"),
        LabTemplate("new-pin-code", "NewPinCode pairing experiment", "NewPinCode 配对实验",
            "WiView 1.0.2 ARM 0x34a30..0x34a8c; 1.0.3 0x35a18..0x35a74; 5.041 PPC PIN storage 0x3647cc.",
            "^<SetCameraParam NewPinCode=\"{newPinCode}\" PinEncryption=\"None\" />",
            listOf(LabInput("newPinCode", "New PIN (secret)", "新 PIN（保密）", LabInputKind.PIN,
                formatEn = "No default PIN. Template host limit: 1–16 encoded bytes; allowed device characters and recovery are unverified.",
                formatZh = "没有默认 PIN；模板手机端限制为编码后 1–16 字节，设备允许字符及恢复方式未验证。")),
            setOf(LabImpact.BACK_WRITE, LabImpact.PAIRING),
            "Can invalidate existing pairing/link keys. Do not log the PIN; every TX/RX/export path must apply LabRedaction before truncation or serialization.",
            "可能使现有配对／密钥失效。禁止记录 PIN；所有 TX/RX/导出路径须先用 LabRedaction，再截断或序列化。"),
        LabTemplate("ce-version-1-0-2", "WiView 1.0.2 CEVersion compatibility value", "WiView 1.0.2 CEVersion 兼容值",
            "WiView 1.0.2 ARM 0x341ac loads 0x01060600 from literal pool 0x341e8; integer append at 0x341b4.",
            "^<SetCameraParam CEVersion=\"$CE_VERSION_1_0_2\" />", emptyList(), setOf(LabImpact.BACK_WRITE, LabImpact.COMPATIBILITY),
            "Pinned original-client compatibility value, not the Android app or back firmware version. No automatic advertising or connection handshake is inferred.",
            "固定样本的原客户端兼容值，不是安卓 App 或后背固件版本；不自动声明，也不据此推定握手。",
            knownValues = mapOf("CEVersion" to CE_VERSION_1_0_2)),
        LabTemplate("ce-version-1-0-3", "WiView 1.0.3 CEVersion compatibility value", "WiView 1.0.3 CEVersion 兼容值",
            "WiView 1.0.3 ARM 0x35198 mov r1,#0x1080000; integer append at 0x351a0.",
            "^<SetCameraParam CEVersion=\"$CE_VERSION_1_0_3\" />", emptyList(), setOf(LabImpact.BACK_WRITE, LabImpact.COMPATIBILITY),
            "Pinned original-client compatibility value, not the Android app or back firmware version. Compatibility effects need manual measurement.",
            "固定样本的原客户端兼容值，不是安卓 App 或后背固件版本；兼容性影响需要手动测量。",
            knownValues = mapOf("CEVersion" to CE_VERSION_1_0_3)),
        LabTemplate("factory-settings", "FactorySettings request", "FactorySettings 请求",
            "WiView 1.0.2 ARM 0x34890..0x34990; 1.0.3 0x35878..0x35978.",
            "^<SetCameraParam [DefaultShootSetup=\"{defaultShootSetup}\" ]Orientation=\"{orientation}\" Color=\"{color}\" FactorySettings=\"TRUE\" />",
            listOf(LabInput("orientation", "Explicit orientation", "明确的方向", suggestions = listOf("Portrait CW", "Portrait CCW", "Auto", "Landscape")),
                LabInput("color", "Explicit color mode", "明确的色彩模式", suggestions = listOf("Color", "BW")),
                LabInput("defaultShootSetup", "Optional existing setup name", "可选：现有配置名", required = false, allowEmpty = true)),
            setOf(LabImpact.BACK_WRITE, LabImpact.FACTORY_SETTINGS),
            "A distinct factory-settings operation, not ordinary setup selection. Both orientation and color must be supplied; the reset scope and persistence remain unverified.",
            "这是独立的出厂设置操作，不是普通配置选择。方向、颜色必须明确提供；恢复范围及持久性仍未验证。"),
        LabTemplate("quick-format", "qformat selected storage", "qformat 选定存储",
            "WiView 1.0.2 ARM 0x2c1a4..0x2c2d0; 1.0.3 0x2cdf0..0x2cf1c, qformat branch 0x2cec4.",
            "^<FileCommand FileCommand=\"qformat\" Subject=\"{storageRoot}/\" />", listOf(storage), setOf(LabImpact.BACK_WRITE, LabImpact.STORAGE_ERASE),
            "Destructive storage operation. Target-root naming and target-specific format behavior are unverified. Never choose a target or run this automatically.",
            "会破坏目标存储数据；存储根名称与具体格式化行为未验证。禁止自动选择目标或自动执行。"),
        LabTemplate("storage-recovery", "recovery selected storage", "recovery 选定存储",
            "WiView 1.0.2 ARM 0x2c1a4..0x2c2d0; 1.0.3 0x2cdf0..0x2cf1c, recovery branch 0x2ce84.",
            "^<FileCommand FileCommand=\"recovery\" Subject=\"{storageRoot}/\" />", listOf(storage), setOf(LabImpact.BACK_WRITE, LabImpact.STORAGE_RECOVERY),
            "May write storage structures. Recovery scope, data preservation and target naming are unverified; do not promise successful recovery.",
            "可能写入存储结构；恢复范围、数据保留和目标名称均未验证，不承诺能恢复文件。"),
        LabTemplate("xmp-only", "Write supplied XMP packet", "写入所提供的 XMP 包",
            "WiView 1.0.2 ARM 0x344d8..0x34598; 1.0.3 0x354c0..0x35580.",
            "^<SetPictureParams file_name=\"{fileName}\" XMP_tag=\"{xmp}\" />", listOf(path, xmp), setOf(LabImpact.PHOTO_METADATA),
            "Writes the supplied full metadata packet. Preserve the original packet and unrelated fields; no empty replacement or arbitrary attachment format is assumed.",
            "写入整份所提供的元数据包；应保留原包及无关字段，不生成空包覆盖，也不假设任意附件格式。"),
        LabTemplate("annotation-xmp", "Write annotation and supplied XMP", "写入备注及所提供的 XMP",
            "WiView 1.0.2 ARM 0x346a4..0x34780; 1.0.3 0x3568c..0x35768.",
            "^<SetPictureParams file_name=\"{fileName}\" annotation=\"{annotation}\" XMP_tag=\"{xmp}\" />",
            listOf(path, LabInput("annotation", "Photo annotation (empty clears it)", "照片备注（空值表示清除）", allowEmpty = true), xmp), setOf(LabImpact.PHOTO_METADATA),
            "Annotation and the full XMP packet may both change. No automatic packet rewrite or metadata readback success is implied.",
            "备注和整份 XMP 包都可能改变；不自动重写所提供的包，也不暗示元数据回读已成功。"),
        LabTemplate("enable-taken-picture", "EnableTakenPicture parameter experiment", "EnableTakenPicture 参数实验",
            "WiView 1.0.2 ARM 0x33e1c..0x33e6c; 1.0.3 0x34e08..0x34e58.",
            "^<SetCameraParam EnableTakenPicture=\"{value}\" />",
            listOf(LabInput("value", "Explicit parameter value", "明确的参数值", suggestions = listOf("yes", "no"))), setOf(LabImpact.BACK_WRITE, LabImpact.NOTIFICATIONS),
            "yes/no constructors exist. The inspected firmware parser sets a flag based on attribute presence; no has not been proved to stop notifications. This does not release a shutter.",
            "原客户端有 yes/no 构造；已查固件按属性存在置标志，尚未证明 no 会停发通知。该参数不会被当作释放快门。"),
        LabTemplate("ack", "Send original Ack", "发送原始 Ack",
            "WiView 1.0.2 ARM 0x31038..0x31074; 1.0.3 0x31dc8..0x31e04.",
            "^<Ack />", emptyList(), setOf(LabImpact.SESSION),
            "Protocol acknowledgement. The template defaults to send-only; any subsequent frame must not be presented as a proven reply to this Ack.",
            "协议确认消息；模板默认只发送。后续收到的帧不能被显示为已经证明是此 Ack 的回复。", defaultExpectedTag = ""),
        LabTemplate("resync-request", "Send original ReSyncRequest", "发送原始 ReSyncRequest",
            "WiView 1.0.2 ARM 0x30fa4..0x30fe8; 1.0.3 0x31d34..0x31d78.",
            "^<ReSyncRequest />", emptyList(), setOf(LabImpact.SESSION),
            "Original recovery request construction exists. End-to-end resynchronization and its response sequence remain unverified; the expected tag is an observation setting.",
            "原厂恢复请求构造存在；端到端重同步和回复时序仍未验证，预期标签只是观测设置。"),
        LabTemplate("get-camera-param", "Probe GetCameraParam list", "探测 GetCameraParam 列表",
            "WiView 1.0.3 ARM constructors 0x34000,0x346d8,0x35b00,0x35dec,0x3675c; 5.041 GetParamList parser PPC 0xe3c38.",
            "^<GetCameraParam GetParamList=\"{getParamList}\" />",
            listOf(LabInput("getParamList", "Semicolon-separated parameter names", "分号分隔的参数名",
                suggestions = listOf("CurrentTime", "battery_percent", "exp_max;exp_left", "TotalFrames", "Histogram_scale", "Copyright;Annotation"))),
            setOf(LabImpact.READ_PROBE),
            "Suggestions come from actual original-client constructions. Other user-entered names are probes only. Parser recognition does not prove output; even ISO/DefaultShootSetup recognition does not establish a usable reader on 5.041.",
            "建议项来自原客户端的实际构造；其他手填名称只是探测。解析器识别字段不等于会输出，5.041 识别 ISO/DefaultShootSetup 也未证明能由此读回。"),
        LabTemplate("set-camera-param-probe", "Probe one SetCameraParam attribute", "探测单个 SetCameraParam 属性",
            "SetCameraParam attribute syntax: original WiView constructors; arbitrary attribute names/combinations are USER PROBES, not confirmed handlers.",
            "^<SetCameraParam {attribute}=\"{value}\" />",
            listOf(LabInput("attribute", "Attribute name (probe)", "属性名（探测）", LabInputKind.ATTRIBUTE_NAME,
                suggestions = listOf("Brightness", "CurrentTime", "EnableTakenPicture", "CEVersion", "ResetCounter")), LabInput("value", "Explicit value", "明确的取值", allowEmpty = true)),
            setOf(LabImpact.BACK_WRITE),
            "Only the request syntax is guaranteed here. A suggested name is not proof that this single-attribute combination is reachable or has the intended effect; additional attributes may be required.",
            "此处只保证请求语法；即使是建议名称，也不证明该单属性组合无线可达或含义符合预期，可能还需要其他属性。"),
    )

    fun template(id: String): LabTemplate = templates.singleOrNull { it.id == id }
        ?: fail("没有此实验模板", "The experiment template does not exist.")

    fun build(
        id: String,
        inputs: Map<String, String>,
        encoding: WireEncoding,
        expectedTag: String? = null,
        expectedFileName: String? = null,
        requiresAttachment: Boolean = false,
        timeoutMs: Long = 20_000,
    ): LeafCommand {
        val template = template(id)
        if (inputs.keys.any { key -> template.requiresInputs.none { it.id == key } }) fail("实验输入含有未知字段", "Experiment inputs contain an unknown field.")
        template.requiresInputs.forEach { input ->
            val value = inputs[input.id]
            if (input.required && (value == null || (!input.allowEmpty && value.isBlank()))) fail("请填写实验所需输入：${input.id}", "Provide the required experiment input: ${input.id}.")
        }
        fun value(key: String): String = Commands.safe(inputs[key] ?: fail("缺少实验输入：$key", "Missing experiment input: $key."))
        fun xmpValue(): String = (inputs["xmp"] ?: fail("缺少 XMP", "XMP is missing.")).also { XmpMetadata.decode(it) }
        val text = when (id) {
            "brightness" -> "^<SetCameraParam Brightness=\"${value("brightness").toIntOrNull() ?: fail("Brightness 需要 32 位十进制整数；这不是硬件量程", "Brightness requires a signed 32-bit decimal integer; this is not a hardware range.")}\" />"
            "current-time" -> {
                val time = value("currentTime")
                if (!clockShape.matches(time) || runCatching { LocalDateTime.parse(time, CameraSystemControls.clockFormat) }.isFailure) fail(
                    "时间格式无效，应为 YYYY:MM:DD:hh:mm:ss", "Invalid date/time; use YYYY:MM:DD:hh:mm:ss.")
                "^<SetCameraParam CurrentTime=\"$time\" />"
            }
            "reset-counter", "ce-version-1-0-2", "ce-version-1-0-3", "ack", "resync-request" -> template.wireTemplate
            "new-pin-code" -> {
                val pin = value("newPinCode")
                if (LeafCommand(pin).bytes(encoding).size !in 1..16) fail("此模板的 PIN 限制为编码后 1–16 字节", "This template limits the PIN to 1–16 encoded bytes.")
                "^<SetCameraParam NewPinCode=\"$pin\" PinEncryption=\"None\" />"
            }
            "factory-settings" -> {
                val orientation = value("orientation"); val color = value("color")
                if (orientation !in listOf("Portrait CW", "Portrait CCW", "Auto", "Landscape") || color !in listOf("Color", "BW")) fail(
                    "此模板只接受原客户端构造的方向与颜色标签；其他值请显式使用原始探测", "This template accepts original-client orientation/color labels only; use an explicit raw probe for other values.")
                val setup = inputs["defaultShootSetup"]?.takeIf { it.isNotEmpty() }?.let { "DefaultShootSetup=\"${Commands.safe(it)}\" " }.orEmpty()
                "^<SetCameraParam ${setup}Orientation=\"$orientation\" Color=\"$color\" FactorySettings=\"TRUE\" />"
            }
            "quick-format", "storage-recovery" -> {
                val root = value("storageRoot").let { if (it.endsWith('/')) it else "$it/" }
                val operation = if (id == "quick-format") "qformat" else "recovery"
                "^<FileCommand FileCommand=\"$operation\" Subject=\"$root\" />"
            }
            "xmp-only" -> "^<SetPictureParams file_name=\"${value("fileName")}\" XMP_tag=\"${xmpValue()}\" />"
            "annotation-xmp" -> "^<SetPictureParams file_name=\"${value("fileName")}\" annotation=\"${value("annotation")}\" XMP_tag=\"${xmpValue()}\" />"
            "enable-taken-picture" -> {
                val parameter = value("value")
                if (parameter !in setOf("yes", "no")) fail("模板值必须为 yes 或 no；并不保证开关含义", "The template value must be yes or no; on/off behavior is not guaranteed.")
                "^<SetCameraParam EnableTakenPicture=\"$parameter\" />"
            }
            "get-camera-param" -> "^<GetCameraParam GetParamList=\"${value("getParamList")}\" />"
            "set-camera-param-probe" -> {
                val name = value("attribute")
                if (!headerName.matches(name)) fail("实验属性名不符合控制头语法", "The experimental attribute name does not match control-header syntax.")
                "^<SetCameraParam $name=\"${value("value")}\" />"
            }
            else -> fail("实验模板未实现构造", "The experiment template has no builder.")
        }
        return command(text, encoding, expectedTag ?: template.defaultExpectedTag, expectedFileName, requiresAttachment, timeoutMs)
    }

    /** Build one bounded outbound CONTROL HEADER, using the existing selected wire encoding unchanged.
     * Unknown syntactically valid response tags are allowed. * means first complete frame, NOT proven response.
     * Empty responseTag MUST be dispatched through LeafProtocol.sendOnly by the caller, never execute().
     * No binary outbound attachment is supported by this API. Existing execute() timeout/close behavior is unchanged.
     */
    fun command(
        raw: String,
        encoding: WireEncoding,
        expectedTag: String = "Ack",
        expectedFileName: String? = null,
        requiresAttachment: Boolean = false,
        timeoutMs: Long = 20_000,
    ): LeafCommand {
        if (raw.length > MAX_HEADER_BYTES || raw.any { it < ' ' && it !in "\t\r\n" }) fail("实验控制头过长或含有禁止控制字符", "The experiment control header is too long or contains forbidden control characters.")
        if (timeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) fail("实验超时超出手机端范围", "The experiment timeout exceeds host limits.")
        if (expectedTag !in setOf("", "*") && !headerName.matches(expectedTag)) fail("预期回复标签不符合语法", "The expected response tag is not syntactically valid.")
        if (expectedTag in setOf("", "*") && (expectedFileName != null || requiresAttachment)) fail(
            "只发送或首帧观测模式不能同时设置文件名／附件匹配条件", "Send-only or first-frame observation cannot also set filename/attachment matching conditions.")
        expectedFileName?.let { Commands.safe(it); LeafCommand(it).bytes(encoding) }
        val command = LeafCommand(raw, responseTag = expectedTag, fileName = expectedFileName,
            timeoutMs = timeoutMs, requiresAttachment = requiresAttachment, acceptAnyFrame = expectedTag == "*")
        val bytes = command.bytes(encoding)
        if (bytes.size > MAX_HEADER_BYTES) fail("实验控制头编码后超过 60 KiB 手机端上限", "The encoded experiment control header exceeds the 60 KiB host limit.")
        val parsed = HeaderParser.parse(bytes) // Rejects concatenated controls, duplicate attributes and incomplete syntax.
        if (parsed.value("attachment")?.let { it != "0" } == true) fail(
            "此接口只发送控制头，不会发送所声明的二进制附件", "This API sends control headers only and cannot send the declared binary attachment.")
        return command
    }

    fun isSendOnly(command: LeafCommand): Boolean = command.responseTag.isEmpty() && !command.acceptAnyFrame
    private fun fail(chinese: String, english: String): Nothing = throw ProtocolException(chinese, english)
}

/** Apply to every normal AND experimental TX/RX log, errors carrying raw controls, and diagnostic exports.
 * Redact before truncation/JSON serialization. This is a log transformation; never mutate bytes sent to the back.
 */
object LabRedaction {
    private val attributes = Regex(
        "(?<![A-Za-z0-9_.:-])((?:NewPinCode|PinCode|Pin|Annotation|Copyright|XMP_tag)\\s*=\\s*)(?:\"[^\"]*(?:\"|$)|'[^']*(?:'|$)|[^\\s<>]*)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    fun redact(log: String): String = attributes.replace(log) { it.groupValues[1] + "\"[redacted]\"" }
}
