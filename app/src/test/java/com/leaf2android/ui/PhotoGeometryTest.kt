package com.leaf2android.ui

import org.junit.Assert.*
import org.junit.Test

class PhotoGeometryTest {
    @Test fun portraitCanvasRejectsTheLetterboxAndKeepsTheImageCentre() {
        val geometry = PhotoViewportGeometry(400.0, 800.0, 600, 400)
        assertNull(geometry.imageFractionAt(CanvasPoint(200.0, 30.0)))
        assertFraction(0.5, 0.5, geometry.imageFractionAt(CanvasPoint(200.0, 400.0)))
        assertFraction(0.0, 0.5, geometry.imageFractionAt(CanvasPoint(0.0, 400.0)))
    }

    @Test fun landscapeCanvasRejectsTheSideBars() {
        val geometry = PhotoViewportGeometry(800.0, 400.0, 400, 600)
        assertNull(geometry.imageFractionAt(CanvasPoint(10.0, 200.0)))
        assertFraction(0.5, 0.5, geometry.imageFractionAt(CanvasPoint(400.0, 200.0)))
    }

    @Test fun zoomAndTranslationAreInvertedWithoutScalingTheTranslationTwice() {
        val geometry = PhotoViewportGeometry(800.0, 600.0, 800, 600, zoom = 2.0, translationX = 100.0, translationY = -60.0)
        assertFraction(0.5, 0.5, geometry.imageFractionAt(CanvasPoint(500.0, 240.0)))
        assertFraction(0.25, 0.75, geometry.imageFractionAt(CanvasPoint(100.0, 540.0)))
    }

    @Test fun markerAndTapUseTheSameTransformAcrossViewportSizes() {
        for (geometry in listOf(
            PhotoViewportGeometry(420.0, 900.0, 1600, 1200, 3.5, 51.0, -35.0),
            PhotoViewportGeometry(1000.0, 500.0, 1600, 1200, 1.5, -82.0, 46.0),
        )) {
            val point = ImageFraction(0.52, 0.48)
            val canvas = geometry.canvasPointAt(point)!!
            assertFraction(point.x, point.y, geometry.imageFractionAt(canvas))
        }
    }

    @Test fun offCanvasAndInvalidGeometryNeverProduceCoordinates() {
        val normal = PhotoViewportGeometry(400.0, 400.0, 400, 400)
        assertNull(normal.imageFractionAt(CanvasPoint(-1.0, 200.0)))
        assertNull(normal.imageFractionAt(CanvasPoint(Double.NaN, 200.0)))
        for (invalid in listOf(normal.copy(canvasWidth = 0.0), normal.copy(imageHeight = 0), normal.copy(zoom = Double.NaN), normal.copy(translationX = Double.POSITIVE_INFINITY))) {
            assertNull(invalid.imageFractionAt(CanvasPoint(100.0, 100.0)))
            assertNull(invalid.canvasPointAt(ImageFraction(0.5, 0.5)))
        }
    }

    @Test fun allFourExplicitOrientationsKeepContinuousEdges() {
        val point = ImageFraction(0.25, 0.75)
        assertEquals(OriginalImagePoint(1000, 2250), originalImagePoint(point, 4000, 3000, ImageQuarterTurn.NONE))
        assertEquals(OriginalImagePoint(1000, 750), originalImagePoint(point, 4000, 3000, ImageQuarterTurn.CLOCKWISE))
        assertEquals(OriginalImagePoint(3000, 750), originalImagePoint(point, 4000, 3000, ImageQuarterTurn.HALF_TURN))
        assertEquals(OriginalImagePoint(3000, 2250), originalImagePoint(point, 4000, 3000, ImageQuarterTurn.COUNTERCLOCKWISE))
        assertEquals(OriginalImagePoint(4000, 3000), originalImagePoint(ImageFraction(1.0, 1.0), 4000, 3000, ImageQuarterTurn.NONE))
    }

    @Test fun clockwiseAndCounterclockwiseAreInverses() {
        val original = ImageFraction(0.2, 0.7)
        val rotated = rotateImageFraction(original, ImageQuarterTurn.CLOCKWISE)!!
        assertFraction(original.x, original.y, rotateImageFraction(rotated, ImageQuarterTurn.COUNTERCLOCKWISE))
        assertNull(originalImagePoint(ImageFraction(1.1, 0.5), 4000, 3000, ImageQuarterTurn.NONE))
    }

    private fun assertFraction(x: Double, y: Double, actual: ImageFraction?) {
        assertNotNull(actual)
        assertEquals(x, actual!!.x, 0.000001)
        assertEquals(y, actual.y, 0.000001)
    }
}
