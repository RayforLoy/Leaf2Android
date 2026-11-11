package com.leaf2android.data

import android.content.Context
import com.leaf2android.protocol.*
import org.json.JSONObject

data class FirmwareResearchItem(val id: String, val titleEn: String, val titleZh: String, val level: String, val evidence: String, val notesEn: String, val notesZh: String, val template: LabTemplate?, val hypothesis: String? = null)

object FirmwareLabTemplates {
    private val represented = setOf("brightness", "reset_counter", "new_pin", "factory_settings", "quick_format", "storage_recovery")
    fun load(context: Context): List<FirmwareResearchItem> {
        val json = JSONObject(context.assets.open("lab-firmware-candidates.json").bufferedReader().use { it.readText() })
        val candidates = json.getJSONArray("candidates")
        return (0 until candidates.length()).mapNotNull { index ->
            val item = candidates.getJSONObject(index)
            val id = item.getString("id")
            if (id in represented) return@mapNotNull null
            val raw = item.optString("wire_template").takeIf { it.isNotBlank() && it != "null" }
            val titleEn = item.getString("label_en"); val titleZh = item.getString("label_zh")
            val level = item.getString("level")
            val evidence = item.getJSONArray("evidence").let { list -> (0 until list.length()).joinToString("\n") { list.getString(it) } }
            val notesEn = item.getJSONArray("unknowns").let { list -> (0 until list.length()).joinToString("\n") { list.getString(it) } }
            val notesZh = when (id) {
                "gain_query" -> "读取 Gain 的请求与应答链已确认；返回列表、单位、存储项和机型含义仍需验证。"
                "gain_sync" -> "Sync 可提供增益相关字段；字段是否出现及校准含义取决于机型，不能据此推定写入格式。"
                "user_gain_write" -> "固件解析器识别 UserGain；允许名称或值格式、持久性及是否影响校准尚未确定。"
                "manual_lens" -> "仅在共享 5.041 固件确认此属性；经典 4.701 未见该解析项。单位、量程和作用未知，不预设 0/1，也不当作快门。"
                "get_image_fp" -> "经典 4.701 接受 FP/fp 特殊缩放值；具体分辨率、画质和启用条件待测，不代表完整 MOS 或连续取景。"
                "factory_gain_field" -> "仅见 FactoryGain 回复字段，没有确认其写入命令。可手工探测 getter，但不是已证实的接口。"
                "calibration_internal" -> "仅发现内部 ISO 调整类；没有确认独立蓝牙校准命令，不自动生成厂商指令。"
                "boot_request_internal" -> "BootRequest 仅见 PXA 内部目录；未见 PDA 注册或外部语法。可建立明确标为假设的空指令探测，可能影响会话或重启。"
                "get_file_raw_unconfirmed" -> "PDA 目录未见 GetFile，未发现准确语法。可建立用户假设探测；复制或移动后背文件不能证明可下载 MOS。"
                "live_view_unconfirmed" -> "PDA 目录未见 LiveView，未确认流启动语法。可建立用户假设探测；反复读取拍后 JPEG 不等于连续传感器取景。"
                else -> "此项含义、参数或无线可达性尚待验证；只记录应答，不声明生效。"
            }
            val effects = item.getJSONObject("effects")
            val write = effects.opt("writes_state") != false
            val inputs = raw?.let { Regex("\\{([A-Za-z_][A-Za-z0-9_]*)\\}").findAll(it).map { match ->
                val key = match.groupValues[1]
                LabInput(key, if (key == "photo_path") "Back photo path" else if (key == "integer") "Explicit signed integer" else "Explicit device value", if (key == "photo_path") "后背照片路径" else if (key == "integer") "明确的有符号整数" else "明确的设备取值", if (key == "integer") LabInputKind.INTEGER else LabInputKind.TEXT)
            }.distinctBy { it.id }.toList() }.orEmpty()
            val template = raw?.let { LabTemplate("firmware-$id", titleEn, titleZh, "$level\n$evidence", it, inputs,
                setOf(if (write) LabImpact.BACK_WRITE else LabImpact.READ_PROBE), notesEn, notesZh, if (item.optString("tag") == "RequestSync") "Sync" else "Ack") }
            val hypothesis = when (id) {
                "factory_gain_field" -> "^<GetCameraParam GetParamList=\"FactoryGain\" />"
                "boot_request_internal" -> "^<BootRequest />"
                "get_file_raw_unconfirmed" -> "^<GetFile />"
                "live_view_unconfirmed" -> "^<LiveView />"
                else -> null
            }
            FirmwareResearchItem(id, titleEn, titleZh, level, evidence, notesEn, notesZh, template, hypothesis)
        }
    }
    fun build(template: LabTemplate, inputs: Map<String, String>, encoding: WireEncoding, expectedTag: String): LeafCommand {
        var raw = template.wireTemplate
        template.requiresInputs.forEach { input ->
            val value = inputs[input.id] ?: throw ProtocolException("请填写实验输入：${input.id}", "Provide experiment input: ${input.id}.")
            if (!input.allowEmpty && value.isBlank()) throw ProtocolException("实验输入不能为空", "Experiment inputs cannot be blank.")
            if (input.kind == LabInputKind.INTEGER && value.toIntOrNull() == null) throw ProtocolException("需要明确的 32 位十进制整数，不代表硬件量程", "Provide a signed 32-bit integer; this is not a hardware range.")
            raw = raw.replace("{${input.id}}", Commands.safe(value))
        }
        return LabExperiments.command(raw, encoding, expectedTag)
    }
}
