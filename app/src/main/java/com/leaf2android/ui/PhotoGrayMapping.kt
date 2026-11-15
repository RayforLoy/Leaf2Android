package com.leaf2android.ui

import com.leaf2android.data.LeafPhoto
import com.leaf2android.protocol.ImagePoint
import kotlin.math.abs
import kotlin.math.max

/** The original PDA's camera-image coordinate grid; this is not a MOS sensor-pixel assertion. */
internal data class PhotoGrayMapping(val width: Int, val height: Int) {
    fun protocolPoint(fraction: ImageFraction): ImagePoint? {
        // The outer boundary and any letterbox/cropped-off point are not sampling locations.
        if (!fraction.withinImage || fraction.x <= 0 || fraction.x >= 1 || fraction.y <= 0 || fraction.y >= 1) return null
        val x = (fraction.x * width).toInt()
        val y = (fraction.y * height).toInt()
        if (x !in 0 until width || y !in 0 until height) return null
        return runCatching { ImagePoint(x, y) }.getOrNull()
    }
}

/**
 * Only a normal full-frame GetImage preview with an explicitly read photo orientation is mapped.
 * The repository binds that metadata to the current preview hash and invalidates it on each new preview.
 * BitmapFactory displays the JPEG raster unchanged. The old PDA instead rotated portrait JPEGs back to
 * landscape before applying its inverse click transform (WiView 1.0.3 0x54d98 / 0x777f0 / 0x955a4).
 * Applying that inverse again here would rotate the point twice. Defaults never select an orientation.
 */
internal fun photoGrayMapping(photo: LeafPhoto, camera: Map<String, String>): PhotoGrayMapping? {
    if (photo.isDemo || photo.width <= 0 || photo.height <= 0) return null
    if (photo.sha256.isEmpty() || photo.metadataPreviewSha256 != photo.sha256) return null
    val orientation = photo.metadata.entries.firstOrNull { it.key.equals("orientation", true) }?.value ?: return null
    val portrait = when {
        orientation.equals("Landscape", true) -> false
        orientation.equals("Portrait CW", true) || orientation.equals("Portrait CCW", true) -> true
        else -> return null
    }
    val raw = camera.entries.firstOrNull { it.key.equals("picturedimensions", true) }?.value ?: return null
    val values = raw.split(',').map { it.trim() }
    if (values.size != 2 || values.any { !it.matches(Regex("[0-9]{1,7}")) }) return null
    // Phone-side command-coordinate boundary, not a claimed back sensor specification.
    val dimensions = values.map { it.toIntOrNull()?.takeIf { n -> n in 1..1_000_000 } ?: return null }
    val width = if (portrait) dimensions.min() else dimensions.max()
    val height = if (portrait) dimensions.max() else dimensions.min()
    // Allow at most one preview-pixel rounding error; a square/cropped view is not stretched to fit.
    val crossDifference = abs(photo.width.toLong() * height - photo.height.toLong() * width)
    if (crossDifference > max(width, height).toLong()) return null
    return PhotoGrayMapping(width, height)
}
