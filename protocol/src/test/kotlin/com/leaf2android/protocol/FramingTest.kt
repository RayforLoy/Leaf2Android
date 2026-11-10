package com.leaf2android.protocol

import org.junit.Test
import kotlin.test.*

class FramingTest {
    private fun frames(bytes: ByteArray, chunk: Int = bytes.size, framer: StreamFramer = StreamFramer()): List<WireFrame> {
        val out = mutableListOf<WireFrame>()
        bytes.toList().chunked(chunk).forEach { framer.feed(it.toByteArray(), emit = out::add) }
        framer.finish()
        return out
    }
    @Test fun everyBoundaryAndBinaryDelimiters() {
        val body = byteArrayOf(0, 94, 62, -1, 60, 34, 94, 62, 1)
        val wire = "^<Ack file_name=\"./shot.mos\" attachment=\"9\" />".toByteArray() + body + "^<PictureTaken file_name=\"./next.mos\" />".toByteArray()
        for (chunk in 1..wire.size) {
            val parsed = frames(wire, chunk)
            assertEquals(2, parsed.size, "chunk=$chunk")
            assertContentEquals(body, parsed[0].attachment)
            assertEquals("PictureTaken", parsed[1].tag)
        }
    }
    @Test fun coalescedControlsAndZeroAttachment() {
        assertEquals(listOf("Sync", "Ack", "ReadyToShoot"), frames("^<Sync />^<Ack attachment='0' />^<ReadyToShoot />".toByteArray()).map { it.tag })
    }
    @Test fun noiseAndResync() {
        val f = StreamFramer()
        val parsed = frames("noise^<broken ^<Ack />".toByteArray(), 1, f)
        assertEquals("Ack", parsed.single().tag); assertEquals(1, f.resyncs); assertTrue(f.discardedBytes > 5)
    }
    @Test fun malformedAndHugeAttachmentLengths() {
        listOf("-1", "+1", "1.0", "", "10000000000", "67108865", "abc").forEach { length ->
            assertFailsWith<ProtocolException>(length) { frames("^<Ack attachment=\"$length\" />".toByteArray()) }
        }
    }
    @Test fun duplicateAttrsAndMissingQuote() {
        listOf("^<Ack a='1' a='2' />", "^<Ack a='1' A='2' />", "^<Ack a=1 />", "^<Ack a='one />").forEach { header ->
            assertFailsWith<ProtocolException> { frames(header.toByteArray()) }
        }
    }
    @Test fun truncationDetectedAtEof() {
        assertFailsWith<ProtocolException> { frames("^<Ack attachment='10' />123".toByteArray()) }
        assertFailsWith<ProtocolException> { frames("^<Ack a='123'".toByteArray()) }
    }
    @Test fun resetDropsPreviousAttachment() {
        val f = StreamFramer()
        f.feed("^<Ack attachment='10' />123".toByteArray()) { fail("partial frame emitted") }
        f.reset()
        assertEquals("Sync", frames("^<Sync />".toByteArray(), 1, f).single().tag)
    }
    @Test fun sizeLimitsAndProgress() {
        assertFailsWith<ProtocolException> { frames("^<Ack long='1234567890' />".toByteArray(), 1, StreamFramer(maxHeader = 12)) }
        val progress = mutableListOf<Pair<Int, Int>>()
        frames("^<Ack attachment='3' />abc".toByteArray(), 1, StreamFramer(progress = { n, total -> progress += n to total }))
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress)
    }
    @Test fun rawValuesArePreserved() {
        val wire = "^<Ack note='".toByteArray() + byteArrayOf(0xD6.toByte(), 0xD0.toByte()) + "&amp;' />".toByteArray()
        assertContentEquals(byteArrayOf(0xD6.toByte(), 0xD0.toByte()) + "&amp;".toByteArray(), frames(wire).single().attributes["note"])
    }
    @Test fun completedFrameSurvivesLaterMalformedControl() {
        val out = mutableListOf<WireFrame>()
        assertFailsWith<ProtocolException> { StreamFramer().feed("^<Ack />^<Bad length=1 />".toByteArray(), emit = out::add) }
        assertEquals("Ack", out.single().tag)
    }
}
