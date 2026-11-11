package com.leaf2android.protocol

import org.junit.Test
import kotlin.test.*

class ImageOperationsTest {
    private fun frame(header: String) = HeaderParser.parse(header.toByteArray(Charsets.US_ASCII))

    @Test fun evidenceBasedPreviewGroupsHaveExactWireFormatAndPhotoCorrelation() {
        val command = ImageOperations.preview("./shots/one.mos", ImageZoom.FOUR, ImagePoint(120, 80), ExposureEdge.BOTH, ImagePoint(75, 45))
        assertEquals("^<GetImage file_name=\"./shots/one.mos\" zoom=\"4\" XCoord=\"120\" YCoord=\"80\" Alarm=\"yes\" edge=\"both\" light=\"75,45\" />", command.text)
        assertEquals(60_000L, command.timeoutMs)
        assertTrue(command.requiresAttachment)
        assertTrue(command.accepts(frame("^<Ack file_name='./shots/one.mos' attachment='100' />"), WireEncoding.WESTERN))
        assertFalse(command.accepts(frame("^<Ack file_name='./shots/two.mos' attachment='100' />"), WireEncoding.WESTERN))
        assertFalse(command.accepts(frame("^<Ack file_name='./shots/one.mos' />"), WireEncoding.WESTERN))
        assertEquals(Commands.image("./shots/one.mos").text, ImageOperations.preview("./shots/one.mos").text)
    }

    @Test fun fitOmitsCoordinatesAndEachBackZoomUsesTheVendorFactor() {
        assertEquals("^<GetImage file_name=\"a.mos\" Alarm=\"yes\" edge=\"white\" />", ImageOperations.preview("a.mos", alarm = ExposureEdge.WHITE).text)
        listOf(ImageZoom.ONE to 1, ImageZoom.TWO to 2, ImageZoom.FOUR to 4, ImageZoom.EIGHT to 8).forEach { (zoom, factor) ->
            assertTrue(ImageOperations.preview("a.mos", zoom, ImagePoint(1, 2)).text.contains("zoom=\"$factor\" XCoord=\"1\" YCoord=\"2\""))
        }
        assertFailsWith<ProtocolException> { ImageOperations.preview("a.mos", ImageZoom.TWO) }
        assertFailsWith<ProtocolException> { ImageOperations.preview("a.mos", point = ImagePoint(1, 2)) }
    }

    @Test fun coordinatesAndPathsAreValidatedBeforeSending() {
        assertEquals(ImagePoint.MAX_COORDINATE, ImagePoint(ImagePoint.MAX_COORDINATE, 0).x)
        assertFailsWith<ProtocolException> { ImagePoint(-1, 0) }
        assertFailsWith<ProtocolException> { ImagePoint(0, ImagePoint.MAX_COORDINATE + 1) }
        listOf("../a.mos", "./shots/", "a\".mos", "C:/a.mos").forEach { path ->
            assertFailsWith<ProtocolException> { ImageOperations.preview(path) }
            assertFailsWith<ProtocolException> { ImageOperations.pixelProbe(path, ImagePoint(0, 0)) }
            assertFailsWith<ProtocolException> { ImageOperations.restoreGrayBalance(path) }
        }
    }

    @Test fun pixelProbeUsesCoordinatesDespiteFstopsSpellingAndKeepsUnknownValueFormat() {
        val command = ImageOperations.pixelProbe("./a.mos", ImagePoint(1200, 900))
        assertEquals("^<ImageFunction file_name=\"./a.mos\" fstops=\"1200;900\" />", command.text)
        val reply = frame("^<Ack histogramlocation='17' histogramvalue='0.3;1.2;2.4' />")
        assertTrue(command.accepts(reply, WireEncoding.WESTERN))
        val result = ImageOperations.parsePixelProbe(reply)
        assertEquals(17, result.histogramLocation)
        assertEquals("0.3;1.2;2.4", result.histogramValue)
        assertEquals(BackDataSource.BACK_UNVERIFIED, result.source)
        listOf("^<Ack />", "^<Ack histogramlocation='bad' histogramvalue='2' />", "^<Nak />").forEach {
            assertFailsWith<ProtocolException> { ImageOperations.parsePixelProbe(frame(it)) }
        }
    }

    @Test fun grayBalanceCommitAndRestoreAreSeparateFromCaptureExposure() {
        assertEquals("^<SetPictureParams file_name=\"./one.mos\" light=\"Back supplied light\" imageUpdate=\"yes\" />",
            ImageOperations.commitGrayBalance("./one.mos", "Back supplied light").text)
        assertEquals("^<SetPictureParams file_name=\"./one.mos\" imageUpdate=\"no\" />", ImageOperations.restoreGrayBalance("./one.mos").text)
        assertFailsWith<ProtocolException> { ImageOperations.commitGrayBalance("a.mos", "") }
        assertFailsWith<ProtocolException> { ImageOperations.commitGrayBalance("a.mos", "bad\" light") }
    }

    @Test fun histogramsHaveIndependentArbitraryBinCountsAndBackProvenance() {
        val histograms = ImageOperations.parseHistograms(frame("^<Ack histogram='0;1;5' histogramr='2;3' histogramg='9' histogramb='1;2;3;4;5;' />"))
        assertEquals(listOf(0L, 1L, 5L), histograms.luminance)
        assertEquals(listOf(2L, 3L), histograms.red)
        assertEquals(listOf(9L), histograms.green)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), histograms.blue)
        assertTrue(histograms.hasData)
        assertEquals(BackDataSource.BACK_UNVERIFIED, histograms.source)
        val absent = ImageOperations.parseHistograms(frame("^<Ack histogramr='' />"))
        assertNull(absent.luminance)
        assertEquals(emptyList(), absent.red)
        assertFalse(absent.hasData)
    }

    @Test fun malformedOrUnboundedHistogramCannotBecomeMisleadingChartData() {
        listOf("1;;2", "-1;2", "1;1.2", "1;NaN", "9223372036854775808").forEach { invalid ->
            assertFailsWith<ProtocolException> { ImageOperations.parseHistograms(frame("^<Ack histogram='$invalid' />")) }
        }
        val tooMany = List(ImageOperations.MAX_HISTOGRAM_BINS + 1) { "1" }.joinToString(";")
        assertFailsWith<ProtocolException> { ImageOperations.parseHistograms(frame("^<Ack histogram='$tooMany' />")) }
        assertEquals(listOf(Long.MAX_VALUE), ImageOperations.parseHistograms(frame("^<Ack histogram='${Long.MAX_VALUE}' />")).luminance)
        assertFailsWith<ProtocolException> { ImageOperations.parseHistograms(frame("^<Nak />")) }
    }
}
