package com.leaf2android.protocol

import org.junit.Test
import kotlin.test.*

class CommandsTest {
    @Test fun exactEvidenceBasedCommands() {
        assertEquals("^<RequestSync />", Commands.sync.text)
        assertEquals("^<GetCameraParam GetParamList=\"CurrentTime\" />", Commands.currentTime.text)
        assertEquals("^<FileCommand FileCommand=\"dir\" Subject=\"./\" />", Commands.directory().text)
        assertEquals("^<GetImage file_name=\"./shots/0001.mos\" />", Commands.image("./shots/0001.mos").text)
        assertFalse(Commands.image("x.mos").bytes(WireEncoding.WESTERN).last() == 0.toByte())
    }
    @Test fun unsafeAttributeInputRejected() {
        listOf("bad\"name", "bad<name", "bad>name", "bad^name", "bad&name", "bad\nname").forEach { value ->
            assertFailsWith<ProtocolException> { Commands.image(value) }
            assertFailsWith<ProtocolException> { Commands.notes("image.mos", "", value) }
        }
    }
    @Test fun cannotSilentlyCorruptUnmappableText() {
        assertFailsWith<ProtocolException> { Commands.notes("a.mos", "", "中文").bytes(WireEncoding.WESTERN) }
        val bytes = Commands.notes("a.mos", "", "中文").bytes(WireEncoding.GBK)
        assertTrue(bytes.toString(WireEncoding.GBK.charset).contains("中文"))
    }
    @Test fun imageResponseMustMatchNameAndAttachment() {
        val request = Commands.image("./a.mos")
        fun accepts(header: String) = request.accepts(HeaderParser.parse(header.toByteArray()), WireEncoding.WESTERN)
        assertFalse(accepts("^<Ack />"))
        assertFalse(accepts("^<Ack file_name='./b.mos' attachment='2' />"))
        assertTrue(accepts("^<Ack file_name='./a.mos' attachment='2' />"))
        assertTrue(accepts("^<Error error_code='4' />"))
    }
    @Test fun rootAndFileDirectoryEntries() {
        val header = HeaderParser.parse("^<Ack subject='./' dir='D:Shots;F:one.mos,2007-01-02 12:00:00;' />".toByteArray())
        val parsed = DirectoryParser.parse(header, "./", WireEncoding.WESTERN)
        assertEquals("./Shots/", parsed[0].path); assertTrue(parsed[0].isDirectory)
        assertEquals("./one.mos", parsed[1].path); assertFalse(parsed[1].isDirectory)
        assertEquals("2007-01-02 12:00:00", parsed[1].cameraDate)
    }
    @Test fun directoryTraversalAndUnknownFormatRejected() {
        listOf("D:..", "F:../a", "X:foo", "F:x/y").forEach { entry ->
            val header = HeaderParser.parse("^<Ack dir='$entry' />".toByteArray())
            assertFailsWith<ProtocolException> { DirectoryParser.parse(header, "./", WireEncoding.WESTERN) }
        }
        assertFailsWith<ProtocolException> { DirectoryParser.parse(HeaderParser.parse("^<Ack />".toByteArray()), "./", WireEncoding.WESTERN) }
    }
}
