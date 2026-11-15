package com.leaf2android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun GrayPointMarker(
    point: ImageFraction,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    translationX: Float = 0f,
    translationY: Float = 0f,
) {
    Canvas(modifier.testTag("gray-point-marker")) {
        val mapped = PhotoViewportGeometry(size.width.toDouble(), size.height.toDouble(), imageWidth, imageHeight,
            zoom.toDouble(), translationX.toDouble(), translationY.toDouble()).canvasPointAt(point) ?: return@Canvas
        val centre = Offset(mapped.x.toFloat(), mapped.y.toFloat())
        val radius = 11.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.85f), radius + 3.dp.toPx(), centre, style = Stroke(5.dp.toPx()))
        drawCircle(Color(0xFFFFD66E), radius, centre, style = Stroke(2.dp.toPx()))
        drawLine(Color(0xFFFFD66E), centre - Offset(radius * 1.5f, 0f), centre + Offset(radius * 1.5f, 0f), 2.dp.toPx())
        drawLine(Color(0xFFFFD66E), centre - Offset(0f, radius * 1.5f), centre + Offset(0f, radius * 1.5f), 2.dp.toPx())
    }
}
