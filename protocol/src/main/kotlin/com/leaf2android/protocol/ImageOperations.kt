package com.leaf2android.protocol

/** Coordinates in the original back image, after any required orientation transform.
 * Screen, Compose-canvas and decoded-thumbnail coordinates must not be passed without a verified mapping.
 * The bound is a host sanity limit, not an advertised sensor dimension.
 */
data class ImagePoint(val x: Int, val y: Int) {
    init {
        if (x !in 0..MAX_COORDINATE || y !in 0..MAX_COORDINATE) throw ProtocolException(
            "图像坐标超出手机端校验范围；这不是后背的硬件范围", "Image coordinates exceed the host validation bound; this is not a back hardware limit.")
    }
    companion object { const val MAX_COORDINATE = 1_000_000 }
}

enum class ImageZoom(val factor: Int?) { FIT(null), ONE(1), TWO(2), FOUR(4), EIGHT(8) }
enum class ExposureEdge(val wireValue: String) { WHITE("white"), DARK("dark"), BOTH("both") }

/** The client proves these values came from the back, but does not establish RAW versus JPEG provenance. */
enum class BackDataSource { BACK_UNVERIFIED }

data class BackPixelProbe(
    val histogramLocation: Int,
    val histogramValue: String,
    val source: BackDataSource = BackDataSource.BACK_UNVERIFIED,
)

data class BackHistograms(
    val luminance: List<Long>? = null,
    val red: List<Long>? = null,
    val green: List<Long>? = null,
    val blue: List<Long>? = null,
    val source: BackDataSource = BackDataSource.BACK_UNVERIFIED,
) {
    val hasData: Boolean get() = listOf(luminance, red, green, blue).any { !it.isNullOrEmpty() }
}

/** WiView 1.0.2 0x35088 / 1.0.3 0x36070 and related constructors.
 * These operations request or modify processed previews. They do not actuate a shutter or download a MOS original.
 */
object ImageOperations {
    const val MAX_HISTOGRAM_BINS = 4096 // Host limit; the client does not prove a fixed hardware bin count.
    private const val MAX_HISTOGRAM_TEXT = 64 * 1024

    fun preview(
        path: String,
        zoom: ImageZoom = ImageZoom.FIT,
        point: ImagePoint? = null,
        alarm: ExposureEdge? = null,
        grayBalanceAt: ImagePoint? = null,
    ): LeafCommand {
        if ((zoom == ImageZoom.FIT) != (point == null)) throw ProtocolException(
            "后背放大请求需要原图坐标；适合屏幕请求不带坐标", "Back zoom requests require original-image coordinates; fit requests omit coordinates.")
        val image = imagePath(path)
        val command = buildString {
            append("^<GetImage file_name=\"").append(image).append('"')
            if (zoom != ImageZoom.FIT) {
                append(" zoom=\"").append(zoom.factor).append("\" XCoord=\"").append(point!!.x)
                    .append("\" YCoord=\"").append(point.y).append('"')
            }
            if (alarm != null) append(" Alarm=\"yes\" edge=\"").append(alarm.wireValue).append('"')
            if (grayBalanceAt != null) append(" light=\"").append(grayBalanceAt.x).append(',').append(grayBalanceAt.y).append('"')
            append(" />")
        }
        return LeafCommand(command, fileName = path, timeoutMs = 60_000, requiresAttachment = true)
    }

    /** Despite the vendor's `fstops` field name, both integers are image-click coordinates (caller 0x7a454). */
    fun pixelProbe(path: String, point: ImagePoint): LeafCommand = LeafCommand(
        "^<ImageFunction file_name=\"${imagePath(path)}\" fstops=\"${point.x};${point.y}\" />",
    )

    fun parsePixelProbe(frame: WireFrame, encoding: WireEncoding = WireEncoding.WESTERN): BackPixelProbe {
        requireAck(frame)
        val location = frame.value("histogramlocation")?.toIntOrNull() ?: throw ProtocolException(
            "后背像素拾取回复缺少有效的 histogramlocation", "The back pixel-probe response has no valid histogramlocation.")
        val value = frame.value("histogramvalue", encoding.charset)?.takeIf { it.isNotEmpty() && it.length <= 4096 }
            ?: throw ProtocolException("后背像素拾取回复缺少有效的 histogramvalue", "The back pixel-probe response has no valid histogramvalue.")
        return BackPixelProbe(location, value)
    }

    /** Use the exact light string supplied by the back or its advertised light options; do not invent Kelvin/tint values. */
    fun commitGrayBalance(path: String, light: String): LeafCommand {
        if (light.isBlank()) throw ProtocolException("后背没有提供可确认的灰平衡值", "The back did not supply a gray-balance value to commit.")
        return LeafCommand("^<SetPictureParams file_name=\"${imagePath(path)}\" light=\"${Commands.safe(light)}\" imageUpdate=\"yes\" />")
    }

    fun restoreGrayBalance(path: String): LeafCommand = LeafCommand(
        "^<SetPictureParams file_name=\"${imagePath(path)}\" imageUpdate=\"no\" />",
    )

    /** GetPictureParams histogram fields are independent semicolon-separated integer lists; bins are not assumed to be 256. */
    fun parseHistograms(frame: WireFrame): BackHistograms {
        requireAck(frame)
        return BackHistograms(
            histogram(frame, "histogram"), histogram(frame, "histogramr"),
            histogram(frame, "histogramg"), histogram(frame, "histogramb"),
        )
    }

    private fun histogram(frame: WireFrame, field: String): List<Long>? {
        val value = frame.value(field) ?: return null
        if (value.length > MAX_HISTOGRAM_TEXT) invalidHistogram(field)
        if (value.isEmpty()) return emptyList()
        val entries = value.removeSuffix(";").split(';')
        if (entries.size > MAX_HISTOGRAM_BINS) invalidHistogram(field)
        return entries.map { entry ->
            if (entry.isEmpty() || entry.any { it !in '0'..'9' }) invalidHistogram(field)
            entry.toLongOrNull() ?: invalidHistogram(field)
        }
    }

    private fun invalidHistogram(field: String): Nothing = throw ProtocolException(
        "后背直方图 $field 格式无效或超过手机端校验上限", "Back histogram $field is invalid or exceeds a host validation limit.")

    private fun imagePath(path: String): String {
        val safe = CameraPaths.path(path)
        if (safe.endsWith('/')) throw ProtocolException("请选择照片文件", "Select a photo file.")
        return safe
    }

    private fun requireAck(frame: WireFrame) {
        if (!frame.tag.equals("Ack", true)) throw ProtocolException("图像操作没有收到确认回复", "The image operation did not receive an Ack response.")
    }
}
