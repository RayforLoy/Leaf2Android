package com.leaf2android.ui

import kotlin.math.min

/** A continuous image fraction. Edges are 0 and 1, rather than pixel centres. */
internal data class ImageFraction(val x: Double, val y: Double) {
    val withinImage: Boolean get() = x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0
}

internal data class CanvasPoint(val x: Double, val y: Double)
internal data class OriginalImagePoint(val x: Int, val y: Int)
internal enum class ImageQuarterTurn { NONE, CLOCKWISE, HALF_TURN, COUNTERCLOCKWISE }

/**
 * The exact transform used by PhotoViewer: centre-fit, then zoom around the centre, then translation.
 * Translations are canvas pixels; they are not image fractions and are not multiplied by zoom.
 * This only locates a point within the displayed JPEG. It establishes no JPEG-to-camera relationship.
 */
internal data class PhotoViewportGeometry(
    val canvasWidth: Double,
    val canvasHeight: Double,
    val imageWidth: Int,
    val imageHeight: Int,
    val zoom: Double = 1.0,
    val translationX: Double = 0.0,
    val translationY: Double = 0.0,
) {
    private val valid get() = canvasWidth.isFinite() && canvasWidth > 0 && canvasHeight.isFinite() && canvasHeight > 0 &&
        imageWidth > 0 && imageHeight > 0 && zoom.isFinite() && zoom > 0 && translationX.isFinite() && translationY.isFinite()
    private val fit get() = min(canvasWidth / imageWidth, canvasHeight / imageHeight)

    fun imageFractionAt(point: CanvasPoint): ImageFraction? {
        if (!valid || !point.x.isFinite() || !point.y.isFinite() || point.x !in 0.0..canvasWidth || point.y !in 0.0..canvasHeight) return null
        val fraction = ImageFraction(
            (point.x - canvasWidth / 2 - translationX) / (fit * imageWidth * zoom) + 0.5,
            (point.y - canvasHeight / 2 - translationY) / (fit * imageHeight * zoom) + 0.5,
        )
        return fraction.takeIf { it.withinImage }
    }

    fun canvasPointAt(point: ImageFraction): CanvasPoint? {
        if (!valid || !point.withinImage) return null
        return CanvasPoint(
            canvasWidth / 2 + translationX + (point.x - 0.5) * fit * imageWidth * zoom,
            canvasHeight / 2 + translationY + (point.y - 0.5) * fit * imageHeight * zoom,
        )
    }
}

/** Explicit rotation between two complete image coordinate frames; no EXIF or aspect-ratio guesses. */
internal fun rotateImageFraction(point: ImageFraction, rotation: ImageQuarterTurn): ImageFraction? {
    if (!point.withinImage) return null
    return when (rotation) {
        ImageQuarterTurn.NONE -> point
        ImageQuarterTurn.CLOCKWISE -> ImageFraction(1 - point.y, point.x)
        ImageQuarterTurn.HALF_TURN -> ImageFraction(1 - point.x, 1 - point.y)
        ImageQuarterTurn.COUNTERCLOCKWISE -> ImageFraction(point.y, 1 - point.x)
    }
}

/**
 * A caller must first establish that the input fraction covers this entire original image.
 * Uses the original client's continuous-edge convention (W-x / H-y, with no invented -1).
 */
internal fun originalImagePoint(
    point: ImageFraction,
    originalWidth: Int,
    originalHeight: Int,
    inputToOriginal: ImageQuarterTurn,
): OriginalImagePoint? {
    if (originalWidth <= 0 || originalHeight <= 0) return null
    val original = rotateImageFraction(point, inputToOriginal) ?: return null
    return OriginalImagePoint((original.x * originalWidth).toInt(), (original.y * originalHeight).toInt())
}
