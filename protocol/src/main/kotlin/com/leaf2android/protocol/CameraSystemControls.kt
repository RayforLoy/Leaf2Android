package com.leaf2android.protocol

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** 5.041 exposes CameraSettings instead of the older full ShootSetup dialect. */
data class DirectCameraSettings(val values: Map<String, String>, val choices: Map<ShootField, List<String>>) {
    val iso: String? get() = values.entries.firstOrNull { it.key.equals("ISO", true) }?.value
    val isoChoices: List<String> get() = choices[ShootField.ISO].orEmpty()
    operator fun get(field: ShootField): String? = values.entries.firstOrNull { it.key.equals(field.wireName, true) }?.value
    fun validate(field: ShootField, value: String) {
        Commands.safe(value)
        if (get(field) == null) throw ProtocolException("后背没有返回此设置的当前值", "The back did not return the current value of this setting.")
        when (field) {
            ShootField.FOLDER -> CameraPaths.folder(value)
            ShootField.PREFIX -> if (value.length > 23 || value.toByteArray().size > get(field).orEmpty().toByteArray().size || value.any { it in ",;=/\\:*?" })
                throw ProtocolException("尚未确认此前缀的最大长度；新值不能超过当前前缀长度", "The prefix limit is unverified; the new value cannot exceed the current prefix length.")
            ShootField.ISO, ShootField.ORIENTATION, ShootField.CAMERA_TYPE, ShootField.COMPRESSION, ShootField.SENSOR_CROP -> {
                if (value !in choices[field].orEmpty()) throw ProtocolException("后背未声明支持所选值", "The back did not advertise the selected value.")
            }
            else -> throw ProtocolException("此相机参数的独立写入尚未确认", "An independent writer for this camera parameter has not been verified.")
        }
    }
    companion object {
        fun from(attributes: Map<String, String>): DirectCameraSettings? {
            val a = attributes.mapKeys { it.key.lowercase(Locale.ROOT) }
            val raw = a["camerasettings"] ?: return null
            val values = ShootSetup.parse(raw).values
            val choices = listOf(ShootField.ISO, ShootField.ORIENTATION, ShootField.CAMERA_TYPE, ShootField.COMPRESSION, ShootField.SENSOR_CROP).associateWith { field ->
                field.choiceKey?.let(a::get).orEmpty().split(';').filter(String::isNotEmpty).distinct().let { list ->
                    if (field == ShootField.COMPRESSION) list.filter { it in setOf("None", "Lossless") } else list.filterNot { it.contains(',') }
                }
            }
            return DirectCameraSettings(values, choices)
        }
    }
}

object CameraSystemControls {
    val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu:MM:dd:HH:mm:ss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
    val defaultNotes = LeafCommand("^<GetCameraParam GetParamList=\"Copyright;Annotation\" />")
    fun directIso(configuration: DirectCameraSettings, value: String): LeafCommand {
        return directSetting(configuration, ShootField.ISO, value)
    }
    fun directSetting(configuration: DirectCameraSettings, field: ShootField, value: String): LeafCommand {
        configuration.validate(field, value)
        return LeafCommand("^<SetCameraParam ${field.wireName}=\"$value\" />")
    }
    fun clock(time: LocalDateTime) = LeafCommand("^<SetCameraParam CurrentTime=\"${time.format(clockFormat)}\" />")
    fun notes(annotation: String, copyright: String) = LeafCommand("^<SetCameraParam Annotation=\"${Commands.safe(annotation)}\" Copyright=\"${Commands.safe(copyright)}\" />")
    fun captureNotifications(enabled: Boolean) = LeafCommand("^<SetCameraParam EnableTakenPicture=\"${if (enabled) "yes" else "no"}\" />")
    fun powerSource(source: BackPowerSource) = LeafCommand("^<SetCameraParam PowerSource=\"${source.wireValue}\" />")
    fun imageSettings(configuration: ImageSettingsConfiguration, setup: ShootSetup, changes: Map<ShootField, String>, backProvidedLight: String? = null): LeafCommand =
        LeafCommand("^<SetCameraParam ImageSettings=\"${configuration.updatedCsv(setup, changes, backProvidedLight)}\" />")
    fun selectImageSettings(name: String) = LeafCommand("^<SetCameraParam ActiveImageSettings=\"${Commands.safe(name)}\" />")
}
enum class BackPowerSource(val wireValue: String) { FIREWIRE("FireWire"), CAMERA_BATTERY("CameraBattery") }

/** Separate 5.041 processing presets; camera ISO is not part of these image settings. */
data class ImageSettingsConfiguration(val setups: List<ShootSetup>, val activeName: String?, val choices: Map<ShootField, List<String>>) {
    val active: ShootSetup? get() = setups.singleOrNull { it.name == activeName }
    fun updatedCsv(base: ShootSetup, changes: Map<ShootField, String>, backProvidedLight: String? = null): String {
        if (fields.any { base[it] == null }) throw ProtocolException("缺少完整图像设置，禁止部分写入", "The complete image settings are missing; partial writes are disabled.")
        changes.forEach { (field, value) ->
            if (field == ShootField.NAME || field !in fields || ((field != ShootField.LIGHT || value != backProvidedLight) && value !in (if (field == ShootField.ICC) listOf("0", "1") else choices[field].orEmpty())))
                throw ProtocolException("后背未声明支持所选图像设置", "The back did not advertise the selected image setting.")
        }
        val result = base.changed(changes)
        val ordered = linkedMapOf("SetupName" to result.name)
        result.values.filterKeys { !it.equals("SetupName", true) }.forEach { (key, value) -> ordered[key] = value }
        return ordered.entries.joinToString(",") { (key, value) ->
            Commands.safe(key); Commands.safe(value)
            if (value.any { it in ",;=" } || key.any { !it.isLetterOrDigit() && it != '_' }) throw ProtocolException("图像设置含未经确认的分隔符", "The image settings contain unverified delimiters.")
            "$key=$value"
        }
    }
    companion object {
        val fields = listOf(ShootField.NAME, ShootField.QUALITY, ShootField.COLOR, ShootField.FRAMING, ShootField.TONING, ShootField.LIGHT,
            ShootField.SHARPNESS, ShootField.INPUT_PROFILE, ShootField.OUTPUT_PROFILE, ShootField.ICC, ShootField.GRAIN, ShootField.MOIRE, ShootField.SATURATION)
        fun from(attributes: Map<String, String>): ImageSettingsConfiguration? {
            val a = attributes.mapKeys { it.key.lowercase(Locale.ROOT) }
            val raw = a["imagesettings"] ?: return null
            val setups = raw.split(';').filter(String::isNotEmpty).map(ShootSetup::parse)
            if (setups.map { it.name }.distinct().size != setups.size) throw ProtocolException("后背图像设置名称重复", "The back returned duplicate image-settings names.")
            val choices = fields.associateWith { field ->
                field.choiceKey?.let(a::get).orEmpty().split(';').filter(String::isNotEmpty).mapNotNull { option ->
                    if (!option.contains(',')) option else if (field == ShootField.FRAMING) option.split(',').takeIf { it.size == 5 && it.drop(1).all { v -> v.toIntOrNull() != null } }?.first() else null
                }.distinct()
            }
            return ImageSettingsConfiguration(setups, a["activeimagesettings"], choices)
        }
    }
}
