package com.leaf2android.ui

import com.leaf2android.data.LeafPhoto
import com.leaf2android.data.PhotoSource
import com.leaf2android.protocol.ImagePoint
import org.junit.Assert.*
import org.junit.Test

class PhotoGrayMappingTest {
    private val photo = LeafPhoto("./photo.mos", width = 800, height = 600, sha256 = "fixture-preview-hash", metadataPreviewSha256 = "fixture-preview-hash", metadata = mapOf("orientation" to "Landscape"))
    private val camera = mapOf("picturedimensions" to "3000,4000", "defaultorientation" to "Portrait CW")

    @Test fun landscapeUsesTheReportedCameraGridRatherThanJpegPixelsOrDefaultOrientation() {
        val mapping = photoGrayMapping(photo, camera)!!
        assertEquals(ImagePoint(1000, 2250), mapping.protocolPoint(ImageFraction(0.25, 0.75)))
        assertEquals(4000, mapping.width)
        assertEquals(3000, mapping.height)
    }

    @Test fun unknownAndAutomaticOrientationNeverSendPointCoordinates() {
        for (orientation in listOf("Landscape 180", "Auto", "Unknown", "")) {
            assertNull(photoGrayMapping(photo.copy(metadata = mapOf("orientation" to orientation)), camera))
        }
        assertNull(photoGrayMapping(photo.copy(metadata = emptyMap()), camera))
    }

    @Test fun bothPortraitJpegRastersMapDirectlyWithoutRepeatingThePdaDisplayRotation() {
        for (orientation in listOf("Portrait CW", "Portrait CCW")) {
            val portrait = photo.copy(width = 600, height = 800, metadata = mapOf("orientation" to orientation))
            val mapping = photoGrayMapping(portrait, camera)!!
            assertEquals(ImagePoint(750, 3000), mapping.protocolPoint(ImageFraction(0.25, 0.75)))
            assertEquals(3000, mapping.width)
            assertEquals(4000, mapping.height)
            assertNull(photoGrayMapping(photo.copy(metadata = mapOf("orientation" to orientation)), camera))
        }
    }

    @Test fun missingMalformedOrUnboundedDimensionsAreRejected() {
        for (value in listOf("", "4000", "4000,3000,2000", "4000x3000", "-4000,3000", "0,3000", "1000001,3000", "0x1000,3000")) {
            assertNull(photoGrayMapping(photo, mapOf("picturedimensions" to value)))
        }
        assertNull(photoGrayMapping(photo, emptyMap()))
    }

    @Test fun aDifferentAspectRatioAndTheOfflineDemoAreNotMapped() {
        assertNull(photoGrayMapping(photo.copy(width = 600, height = 600), camera))
        assertNull(photoGrayMapping(photo.copy(source = PhotoSource.OFFLINE_DEMO), camera))
        assertNull(photoGrayMapping(photo.copy(width = 0), camera))
    }

    @Test fun unverifiedOrStaleMetadataCannotBeUsedAfterAnotherPreviewArrives() {
        assertNull(photoGrayMapping(photo.copy(metadataPreviewSha256 = null), camera))
        assertNull(photoGrayMapping(photo.copy(sha256 = "replacement-preview"), camera))
        assertNull(photoGrayMapping(photo.copy(sha256 = "", metadataPreviewSha256 = ""), camera))
    }

    @Test fun fullImageEdgesNeverGenerateAnOutOfBoundsSamplingRequest() {
        val mapping = photoGrayMapping(photo, camera)!!
        for (edge in listOf(ImageFraction(0.0, 0.5), ImageFraction(1.0, 0.5), ImageFraction(0.5, 0.0), ImageFraction(0.5, 1.0), ImageFraction(Double.NaN, 0.5))) {
            assertNull(mapping.protocolPoint(edge))
        }
        assertEquals(ImagePoint(3999, 2999), mapping.protocolPoint(ImageFraction(0.99999, 0.99999)))
    }

    @Test fun cameraAttributeCasingAndOnePixelOfPreviewRoundingAreAccepted() {
        assertNotNull(photoGrayMapping(photo.copy(width = 801), mapOf("PictureDimensions" to "4000,3000")))
        assertNull(photoGrayMapping(photo.copy(width = 810), camera))
    }
}
