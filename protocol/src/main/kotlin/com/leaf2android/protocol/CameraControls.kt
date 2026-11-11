package com.leaf2android.protocol

/** The names and order are from WiView 1.0.3 CShootSetup command construction, not invented ISO attributes. */
enum class ShootField(val wireName: String, val choiceKey: String? = wireName.lowercase()) {
    NAME("SetupName", null), FOLDER("ShootingFolder", null), ISO("ISO"), QUALITY("ImageQuality"),
    FRAMING("Framing"), TONING("Toning"), LIGHT("Light"), SHARPNESS("SharpTable"),
    INPUT_PROFILE("InputProfile"), OUTPUT_PROFILE("OutputProfile"), CAMERA_TYPE("CameraType"),
    ICC("ICCFlag", null), PREFIX("Prefix", null), COMPRESSION("FileCompression"),
    NL_COMPRESSION("NLCompression", "nlcompression"), ORIENTATION("Orientation"), COLOR("Color"),
    SENSOR_CROP("SensorCrop"), GRAIN("Grain"), MOIRE("MoireReduction"), SATURATION("Saturation");
    companion object { val legacy: List<ShootField> get() = entries.filter { it !in setOf(SENSOR_CROP, GRAIN, MOIRE, SATURATION) } }
}

data class ShootSetup(val values: Map<String, String>) {
    operator fun get(field: ShootField): String? = values.entries.firstOrNull { it.key.equals(field.wireName, true) }?.value
    val name: String get() = get(ShootField.NAME).orEmpty()
    val missingFields: List<String> get() = ShootField.legacy.filter {
        it != ShootField.NL_COMPRESSION && get(it) == null
    }.map { it.wireName } + if (get(ShootField.COMPRESSION) == "Nearly Lossless" && get(ShootField.NL_COMPRESSION) == null) listOf("NLCompression") else emptyList()

    fun changed(changes: Map<ShootField, String>): ShootSetup {
        val result = values.toMutableMap()
        changes.forEach { (field, value) ->
            result.keys.firstOrNull { it.equals(field.wireName, true) }?.let(result::remove)
            result[field.wireName] = value
        }
        return ShootSetup(result)
    }

    fun serialize(): String {
        if (missingFields.isNotEmpty()) throw ProtocolException(
            "后背未提供完整拍摄配置，缺少：${missingFields.joinToString()}。已禁止写入以保留其他设置。",
            "The back did not provide a complete shooting setup: ${missingFields.joinToString()}. Writing is disabled to preserve the other settings.")
        if (get(ShootField.ICC) !in setOf("0", "1")) throw ProtocolException("ICCFlag 必须为 0 或 1", "ICCFlag must be 0 or 1.")
        if (get(ShootField.COLOR) !in setOf("color", "BW")) throw ProtocolException("未知 Color 值", "Unknown Color value.")
        return ShootField.legacy.filter { it != ShootField.NL_COMPRESSION || get(ShootField.COMPRESSION) == "Nearly Lossless" }
            .joinToString(",") { field -> "${field.wireName}=${setupValue(get(field).orEmpty())}" }
    }

    companion object {
        fun parse(text: String): ShootSetup {
            val values = linkedMapOf<String, String>()
            text.split(',').forEach { item ->
                val split = item.indexOf('=')
                if (split <= 0) throw ProtocolException("无法解析后背的拍摄配置", "The back's shooting setup cannot be parsed.")
                val key = item.substring(0, split)
                if (values.keys.any { it.equals(key, true) }) throw ProtocolException("拍摄配置含重复字段：$key", "Duplicate shooting setup field: $key")
                values[key] = item.substring(split + 1)
            }
            return ShootSetup(values)
        }
    }
}

data class SetupGlobals(val orientation: String?, val color: String?) {
    fun attributesFor(name: String): String {
        if (name.endsWith(".1")) return ""
        if (orientation !in setOf("Portrait CW", "Portrait CCW", "Auto", "Landscape") || color !in setOf("color", "Color", "BW"))
            throw ProtocolException("此配置需要后背下发的全局方向和色彩设置；缺失或无法识别时禁止写入", "This setup requires the back's global orientation and color. Missing or unknown values cannot be written.")
        return " Orientation=\"$orientation\" Color=\"${if (color == "BW") "BW" else "Color"}\""
    }
}

data class ShootingConfiguration(val setups: List<ShootSetup>, val activeName: String?, val choices: Map<ShootField, List<String>>, val globals: SetupGlobals) {
    val active: ShootSetup? get() = setups.singleOrNull { it.name == activeName }
    fun validateChanges(setup: ShootSetup, changes: Map<ShootField, String>, backProvidedLight: String? = null): ShootSetup {
        globals.attributesFor(setup.name)
        changes.forEach { (field, value) ->
            if (field !in ShootField.legacy) throw ProtocolException("此字段不属于旧版 ShootSetup", "This field does not belong to the legacy ShootSetup.")
            setupValue(value)
            when (field) {
                ShootField.NAME -> throw ProtocolException("请从后背已有配置中选择配置名", "Select a setup already supplied by the back.")
                ShootField.ICC -> requireChoice(value, listOf("0", "1"), field)
                ShootField.PREFIX -> if (value.length > 23 || value.any { it in "/\\:*?" }) throw ProtocolException("文件名前缀超过原厂界面的 23 字符上限或含路径字符", "The filename prefix exceeds the original UI's 23-character limit or contains path characters.")
                ShootField.FOLDER -> CameraPaths.folder(value)
                else -> if (field != ShootField.LIGHT || value != backProvidedLight) requireChoice(value, choices[field].orEmpty(), field)
            }
        }
        return setup.changed(changes).also { it.serialize() }
    }
    companion object {
        fun from(attributes: Map<String, String>): ShootingConfiguration {
            val a = attributes.mapKeys { it.key.lowercase() }
            val setups = a["shootsetup"].orEmpty().split(';').filter(String::isNotEmpty).map(ShootSetup::parse)
            if (setups.map { it.name }.distinct().size != setups.size) throw ProtocolException("后背返回重复的拍摄配置名", "The back returned duplicate shooting setup names.")
            val choices = ShootField.entries.associateWith { field ->
                val raw = field.choiceKey?.let(a::get).orEmpty()
                if (field == ShootField.FRAMING) raw.split(';').filter(String::isNotEmpty).map { item ->
                    val parts = item.split(',')
                    if (parts.size != 5 || parts.drop(1).any { it.toIntOrNull() == null }) throw ProtocolException("后背 Framing 列表格式无效", "The back returned an invalid Framing list.")
                    parts[0]
                }.distinct() else raw.split(';').filter(String::isNotEmpty).distinct()
            }
            return ShootingConfiguration(setups, a["defaultshootsetup"], choices, SetupGlobals(a["defaultorientation"], a["defaultcolor"]))
        }
    }
}

private fun requireChoice(value: String, allowed: List<String>, field: ShootField) {
    if (value !in allowed) throw ProtocolException("后背未声明支持 ${field.wireName}=$value", "The back did not advertise ${field.wireName}=$value.")
}

private fun setupValue(value: String): String {
    Commands.safe(value)
    if (value.any { it in ",;=" }) throw ProtocolException("拍摄配置值不能包含逗号、分号或等号", "Shooting setup values cannot contain commas, semicolons or equals signs.")
    return value
}

/** Paths remain camera-side paths. No host filesystem paths or parent traversal are emitted. */
object CameraPaths {
    fun path(value: String): String {
        Commands.safe(value)
        if (value.isBlank() || value.startsWith('/') || value.contains('\\') || value.contains(':') ||
            value.removePrefix("./").split('/').any { it == "." || it == ".." })
            throw ProtocolException("无效的后背路径", "Invalid back path.")
        return value
    }
    fun folder(value: String): String = path(value).let { if (it.endsWith('/')) it else "$it/" }
    fun child(parent: String, name: String, directory: Boolean): String {
        setupValue(name)
        if (name.isBlank() || name in setOf(".", "..") || name.any { it in "/\\:*?" }) throw ProtocolException("无效的照片或目录名", "Invalid photo or folder name.")
        return path(folder(parent) + name + if (directory) "/" else "")
    }
}

enum class BackMark(val wireValue: String) { GOOD("Good"), UNMARKED("Unmarked") }

object ControlCommands {
    fun shootSetup(setup: ShootSetup, globals: SetupGlobals) = LeafCommand("^<SetCameraParam ShootSetup=\"${setup.serialize()}\"${globals.attributesFor(setup.name)} />")
    fun selectSetup(name: String, globals: SetupGlobals) = LeafCommand("^<SetCameraParam DefaultShootSetup=\"${setupValue(name)}\"${globals.attributesFor(name)} />")
    fun marking(path: String, mark: BackMark, xmp: String? = null): LeafCommand {
        if (xmp != null && (xmp.length > 48 * 1024 || !Regex("[A-Za-z0-9+/]*={0,2}").matches(xmp)))
            throw ProtocolException("XMP 编码无效或超过控制头上限", "The encoded XMP is invalid or exceeds the control-header limit.")
        return LeafCommand("^<SetPictureParams file_name=\"${CameraPaths.path(path)}\" Marking=\"${mark.wireValue}\"" +
            (xmp?.let { " XMP_tag=\"$it\"" } ?: "") + " />", fileName = path)
    }
    fun makeDirectory(parent: String, name: String) = file("mkdir", CameraPaths.child(parent, name, true))
    fun removeDirectory(path: String): LeafCommand {
        val folder = CameraPaths.folder(path)
        if (folder in setOf("./", "/")) throw ProtocolException("不能删除根目录", "The root folder cannot be removed.")
        return file("rmdir", folder)
    }
    fun renameDirectory(path: String, newName: String): LeafCommand {
        val folder = CameraPaths.folder(path)
        if (folder == "./") throw ProtocolException("不能重命名根目录", "The root folder cannot be renamed.")
        val parent = folder.trimEnd('/').substringBeforeLast('/', ".") + "/"
        return file("rename", folder, CameraPaths.child(parent, newName, true))
    }
    fun deleteImage(path: String): LeafCommand {
        if (path.endsWith('/')) throw ProtocolException("请选择照片文件", "Select a photo file.")
        return file("del", path)
    }
    fun transferImage(path: String, targetFolder: String, move: Boolean): LeafCommand {
        if (path.endsWith('/')) throw ProtocolException("请选择照片文件", "Select a photo file.")
        return file(if (move) "move" else "copy", path, CameraPaths.child(targetFolder, path.substringAfterLast('/'), false))
    }
    private fun file(kind: String, subject: String, target: String? = null): LeafCommand {
        val source = CameraPaths.path(subject)
        return LeafCommand("^<FileCommand FileCommand=\"$kind\" Subject=\"$source\"" +
            (target?.let { " Target=\"${CameraPaths.path(it)}\"" } ?: "") + " />")
    }
}
