package com.leaf2android.protocol

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class WireEncoding(val title: String, val charset: Charset) {
    WESTERN("西欧 Windows-1252（原 PDA 常用）", Charset.forName("windows-1252")),
    GBK("简体中文 GBK（待实机核对）", Charset.forName("GBK")),
    UTF8("UTF-8（待实机核对）", Charsets.UTF_8),
}

data class LeafCommand(
    val text: String,
    val responseTag: String = "Ack",
    val fileName: String? = null,
    val directory: String? = null,
    val timeoutMs: Long = 20_000,
    val requiresAttachment: Boolean = false,
    val acceptAnyFrame: Boolean = false,
) {
    fun bytes(encoding: WireEncoding): ByteArray {
        val encoder = encoding.charset.newEncoder().onUnmappableCharacter(CodingErrorAction.REPORT)
            .onMalformedInput(CodingErrorAction.REPORT)
        val buffer = try { encoder.encode(java.nio.CharBuffer.wrap(text)) } catch (_: Exception) {
            throw ProtocolException("当前协议字符集无法表示文件名或备注，请在设置中选择匹配后背的字符集", "The protocol encoding cannot represent the filename or note. Choose the encoding used by the back in Settings.")
        }
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }
    fun accepts(frame: WireFrame, encoding: WireEncoding): Boolean {
        if (frame.tag.equals("Error", true) || frame.tag.equals("Nak", true)) return true
        if (acceptAnyFrame) return true
        if (!frame.tag.equals(responseTag, true)) return false
        if (fileName != null && frame.value("file_name", encoding.charset) != fileName) return false
        if (requiresAttachment && frame.value("attachment") == null) return false
        val subject = frame.value("subject", encoding.charset)
        if (directory != null && subject != null && subject.trimEnd('/') != directory.trimEnd('/')) return false
        return true
    }
}

/** All emitted operations have corresponding literal/construction evidence in CAB 1.0.3. */
object Commands {
    val sync = LeafCommand("^<RequestSync />", responseTag = "Sync")
    val currentTime = camera("CurrentTime")
    val battery = camera("battery_percent")
    val exposures = camera("exp_max;exp_left")
    val totalFrames = camera("TotalFrames")
    val histogramScale = camera("Histogram_scale")
    val ack = "^<Ack />".toByteArray(Charsets.US_ASCII)
    private fun camera(param: String) = LeafCommand("^<GetCameraParam GetParamList=\"$param\" />")

    fun directory(path: String = "./"): LeafCommand {
        val normalized = if (path.endsWith('/')) path else "$path/"
        return LeafCommand("^<FileCommand FileCommand=\"dir\" Subject=\"${safe(normalized)}\" />", directory = normalized)
    }
    fun image(path: String) = LeafCommand("^<GetImage file_name=\"${safe(path)}\" />", fileName = path,
        timeoutMs = 60_000, requiresAttachment = true)
    fun pictureParams(path: String) = LeafCommand("^<GetPictureParams file_name=\"${safe(path)}\" XMP_tag=\"yes\" />", fileName = path)

    // The vendor concatenates literal values, so characters requiring escaping are rejected until verified.
    fun notes(path: String, copyright: String, annotation: String) = LeafCommand(
        "^<SetPictureParams file_name=\"${safe(path)}\" copyright=\"${safe(copyright)}\" annotation=\"${safe(annotation)}\" />",
        fileName = path,
    )
    fun safe(value: String): String {
        if (value.length > 4096 || value.any { it < ' ' || it in "\"^<>&" })
            throw ProtocolException("文本过长或含有暂不支持的控制字符、引号、&、<、>、^", "The text is too long or contains unverified control characters, quotes, &, <, > or ^.")
        return value
    }
}

data class DirectoryEntry(val name: String, val path: String, val isDirectory: Boolean, val cameraDate: String? = null)

object DirectoryParser {
    /** CFolderCommand 0x30c70: dir='D?name;F?name,date'; ignores the second character. */
    fun parse(frame: WireFrame, parent: String, encoding: WireEncoding): List<DirectoryEntry> {
        val list = frame.value("dir", encoding.charset) ?: throw ProtocolException("目录回复没有 dir 属性，请导出诊断日志", "The folder response has no dir attribute. Export diagnostics.")
        val prefix = if (parent.endsWith('/')) parent else "$parent/"
        return list.split(';').filter { it.isNotBlank() }.map { item ->
            if (item.length < 3 || item[0] !in "DF") throw ProtocolException("无法识别目录条目：${item.take(80)}", "Unrecognized folder entry: ${item.take(80)}")
            val fields = item.drop(2).split(',')
            val name = fields[0]
            if (name.isBlank() || name == "." || name == ".." || name.any { it in "\\/*?" })
                throw ProtocolException("后背返回了无效文件名", "The back returned an invalid filename.")
            Commands.safe(name)
            DirectoryEntry(name, prefix + name + if (item[0] == 'D') "/" else "", item[0] == 'D', fields.getOrNull(1))
        }.distinctBy { it.path }
    }
}
