package com.leaf2android.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class ProtocolException(private val chineseMessage: String, private val englishMessage: String = chineseMessage) : java.io.IOException(englishMessage) {
    fun messageForLanguage(chinese: Boolean): String = if (chinese) chineseMessage else englishMessage
}

/** Attribute bytes are kept intact; the CAB uses CP_ACP, not UTF-16 on the wire. */
data class WireFrame(
    val header: ByteArray,
    val tag: String,
    val attributes: Map<String, ByteArray>,
    val attachment: ByteArray = byteArrayOf(),
) {
    fun value(name: String, charset: Charset = Charsets.ISO_8859_1): String? =
        attributes.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.toString(charset)
    fun values(charset: Charset): Map<String, String> = attributes.mapValues { it.value.toString(charset) }
}

/** Bounded subset of Leaf's attribute-only controls, deliberately not a general XML parser. */
object HeaderParser {
    private val name = Regex("[A-Za-z_][A-Za-z0-9_.:-]*")
    fun parse(bytes: ByteArray): WireFrame {
        val text = bytes.toString(Charsets.ISO_8859_1)
        if (!text.startsWith("^<") || !text.endsWith('>')) throw ProtocolException("控制头缺少 ^< 或 >", "The control header is missing ^< or >.")
        val tagMatch = name.find(text, 2)?.takeIf { it.range.first == 2 }
            ?: throw ProtocolException("控制头缺少命令名称", "The control header has no command name.")
        var i = tagMatch.range.last + 1
        val end = text.length - 1
        val attributes = linkedMapOf<String, ByteArray>()
        while (i < end) {
            val before = i
            while (i < end && text[i] in " \t\r\n") i++
            if (i == end || text.substring(i, end) == "/") break
            if (before == i) throw ProtocolException("属性前缺少空格", "Whitespace is missing before an attribute.")
            val match = name.find(text, i)?.takeIf { it.range.first == i }
                ?: throw ProtocolException("属性名称无效", "The attribute name is invalid.")
            val key = match.value
            if (attributes.keys.any { it.equals(key, true) }) throw ProtocolException("属性重复：$key", "Duplicate attribute: $key")
            i = match.range.last + 1
            while (i < end && text[i] in " \t\r\n") i++
            if (i >= end || text[i++] != '=') throw ProtocolException("属性缺少等号", "An attribute is missing its equals sign.")
            while (i < end && text[i] in " \t\r\n") i++
            if (i >= end || text[i] !in "\"'") throw ProtocolException("属性缺少引号", "An attribute is missing quotes.")
            val quote = text[i++]
            val stop = text.indexOf(quote, i)
            if (stop < 0 || stop >= end) throw ProtocolException("属性未闭合", "An attribute is not terminated.")
            val value = text.substring(i, stop)
            if (value.any { it in "^<>" }) throw ProtocolException("属性含有控制分隔符", "An attribute contains a control delimiter.")
            attributes[key] = value.toByteArray(Charsets.ISO_8859_1)
            i = stop + 1
        }
        return WireFrame(bytes, tagMatch.value, attributes)
    }
}

/** ReadXmlData/ReadImage in both CABs: '^' ... first '>', then exactly attachment bytes. */
class StreamFramer(
    private val maxHeader: Int = 64 * 1024,
    private val maxAttachment: Int = 64 * 1024 * 1024,
    private val progress: (Int, Int) -> Unit = { _, _ -> },
) {
    private var header = ByteArrayOutputStream()
    private var pending: WireFrame? = null
    private var body = ByteArrayOutputStream()
    private var expected = 0
    var discardedBytes = 0L; private set
    var resyncs = 0L; private set

    init { require(maxHeader >= 4 && maxAttachment >= 0) }

    fun reset() {
        header = ByteArrayOutputStream()
        pending = null
        body = ByteArrayOutputStream()
        expected = 0
    }

    fun feed(data: ByteArray, count: Int = data.size, emit: (WireFrame) -> Unit) {
        require(count in 0..data.size)
        var i = 0
        while (i < count) {
            val frame = pending
            if (frame != null) {
                val n = minOf(expected - body.size(), count - i)
                body.write(data, i, n)
                i += n
                progress(body.size(), expected)
                if (body.size() == expected) {
                    val complete = frame.copy(attachment = body.toByteArray())
                    reset()
                    emit(complete)
                }
                continue
            }
            val b = data[i++].toInt() and 255
            if (b == '^'.code) {
                if (header.size() > 0) { discardedBytes += header.size(); resyncs++ }
                header.reset(); header.write(b)
                continue
            }
            if (header.size() == 0) { discardedBytes++; continue }
            header.write(b)
            if (header.size() > maxHeader) fail("控制头超过 64 KiB 上限", "The control header exceeds the 64 KiB host limit.")
            if (b != '>'.code) continue
            val parsed = try { HeaderParser.parse(header.toByteArray()) } catch (e: ProtocolException) {
                reset(); throw e
            }
            val length = parsed.value("attachment")
            val size = if (length == null) 0L else {
                if (length.isEmpty() || length.length > 10 || length.any { it !in '0'..'9' }) fail("附件长度无效", "The attachment length is invalid.")
                length.toLongOrNull() ?: fail("附件长度溢出", "The attachment length overflowed.")
            }
            if (size > maxAttachment) fail("附件超过 ${maxAttachment / 1024 / 1024} MiB 上限", "The attachment exceeds the ${maxAttachment / 1024 / 1024} MiB host limit.")
            header.reset()
            if (size == 0L) emit(parsed) else {
                pending = parsed
                expected = size.toInt()
                body = ByteArrayOutputStream(minOf(expected, 1024 * 1024))
                progress(0, expected)
            }
        }
    }

    fun finish() {
        if (pending != null) throw ProtocolException("附件截断：还缺 ${expected - body.size()} 字节", "The attachment is truncated: ${expected - body.size()} bytes are missing.")
        if (header.size() > 0) throw ProtocolException("控制头截断", "The control header is truncated.")
    }

    private fun fail(message: String, englishMessage: String): Nothing { reset(); throw ProtocolException(message, englishMessage) }
}
