package com.leaf2android.protocol

/** Numeric wire fields are unsigned DWORD values, not signed host integers. */
object WireNumbers {
    private const val MAX_DWORD = 0xffff_ffffL

    /**
     * Parse decimal or explicitly prefixed hexadecimal without trimming or changing the source.
     * Unknown syntax and overflow stay unknown: callers must not treat null as a successful zero.
     */
    fun errorCode(value: String?): Long? {
        if (value.isNullOrEmpty()) return null
        val hexadecimal = value.startsWith("0x") || value.startsWith("0X")
        val start = if (hexadecimal) 2 else 0
        val radix = if (hexadecimal) 16 else 10
        if (start == value.length) return null
        var result = 0L
        for (index in start until value.length) {
            val digit = when (val char = value[index]) {
                in '0'..'9' -> char - '0'
                in 'a'..'f' -> char - 'a' + 10
                in 'A'..'F' -> char - 'A' + 10
                else -> return null
            }
            if (digit >= radix || result > (MAX_DWORD - digit) / radix) return null
            result = result * radix + digit
        }
        return result
    }
}

/** A complete refusal frame; unlike a timeout, receiving this does not imply a lost transport. */
class CommandRejectedException(
    val request: LeafCommand,
    val response: WireFrame,
    encoding: WireEncoding,
) : ProtocolException(
    rejectedMessage(request, response, encoding, chinese = true),
    rejectedMessage(request, response, encoding, chinese = false),
) {
    val errorCode: Long? = WireNumbers.errorCode(response.value("error_code", encoding.charset))
}

private fun rejectedMessage(
    request: LeafCommand,
    response: WireFrame,
    encoding: WireEncoding,
    chinese: Boolean,
): String {
    val header = HeaderParser.parse(request.bytes(encoding))
    val description = header.tag + header.attributes.keys.takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = " [", postfix = "]").orEmpty()
    val redacted = LabRedaction.redact(response.header.toString(encoding.charset))
    return if (chinese) "$description：后背拒绝指令；回复 $redacted"
    else "$description: the back rejected the command; response $redacted"
}
