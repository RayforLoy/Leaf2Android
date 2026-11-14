package com.leaf2android.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class SessionTest {
    private class FakeTransport : LeafTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val output = Channel<String>(Channel.UNLIMITED)
        var closed = false
        override suspend fun connect() { closed = false }
        override suspend fun read(buffer: ByteArray): Int {
            val next = input.receiveCatching().getOrNull() ?: return -1
            next.copyInto(buffer); return next.size
        }
        override suspend fun write(bytes: ByteArray) { output.send(bytes.toString(Charsets.ISO_8859_1)) }
        override fun close() { closed = true; input.close() }
        suspend fun send(text: String) { input.send(text.toByteArray()) }
    }
    private fun session(block: suspend CoroutineScope.(LeafProtocol, FakeTransport, AtomicReference<Throwable>) -> Unit) = runBlocking {
        withTimeout(5_000) {
            val transport = FakeTransport(); val failure = AtomicReference<Throwable>()
            val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val protocol = LeafProtocol(transport, background, WireEncoding.WESTERN, failed = { failure.set(it) })
            try { protocol.open(); block(protocol, transport, failure) }
            finally { protocol.close(); background.cancel() }
        }
    }
    @Test fun startupUsesSyncThenClockThenDirectory() = session { p, t, _ ->
        val server = launch {
            assertEquals("^<RequestSync />", t.output.receive()); t.send("^<Ack />^<Sync cameratype='Aptus II 5' />")
            assertEquals(Commands.currentTime.text, t.output.receive()); t.send("^<Ack currenttime='2008-01-01' />")
            assertEquals(Commands.directory().text, t.output.receive()); t.send("^<Ack subject='./' dir='D:Shots;' />")
        }
        assertEquals("Sync", p.execute(Commands.sync).tag)
        assertEquals("2008-01-01", p.execute(Commands.currentTime).value("currenttime"))
        assertEquals("Shots", DirectoryParser.parse(p.execute(Commands.directory()), "./", WireEncoding.WESTERN).single().name)
        server.join()
    }
    @Test fun notificationsDoNotConsumeImageReply() = session { p, t, _ ->
        val event = async { p.events.first() }
        val request = async { p.execute(Commands.image("./a.mos")) }
        t.output.receive()
        t.send("^<PictureTaken file_name='./b.mos' />^<Ack file_name='./a.mos' attachment='4' />^>12")
        assertEquals("./b.mos", event.await().value("file_name"))
        assertContentEquals("^>12".toByteArray(), request.await().attachment)
    }
    @Test fun commandsAreSerializedEvenWhenCalledConcurrently() = session { p, t, _ ->
        val first = async { p.execute(Commands.battery) }
        assertEquals(Commands.battery.text, t.output.receive())
        val second = async { p.execute(Commands.exposures) }
        yield(); assertTrue(t.output.tryReceive().isFailure)
        t.send("^<Ack battery_percent='80' />"); assertEquals("80", first.await().value("battery_percent"))
        assertEquals(Commands.exposures.text, t.output.receive()); t.send("^<Ack exp_left='30' />")
        assertEquals("30", second.await().value("exp_left"))
    }
    @Test fun twentySyntheticImageResponsesKeepFileAssociation() = session { p, t, _ ->
        val server = launch {
            repeat(20) { i ->
                assertEquals(Commands.image("./$i.mos").text, t.output.receive())
                val data = "^<Ack file_name='./$i.mos' attachment='3' />".toByteArray() + byteArrayOf(i.toByte(), 94, 62)
                data.toList().chunked(3).forEach { t.input.send(it.toByteArray()) }
            }
        }
        repeat(20) { i ->
            val frame = p.execute(Commands.image("./$i.mos"))
            assertEquals("./$i.mos", frame.value("file_name")); assertEquals(i.toByte(), frame.attachment[0])
        }
        server.join()
    }
    @Test fun timeoutClosesSessionAndPreventsLateReplyReuse() = session { p, t, failure ->
        assertFailsWith<java.io.IOException> { p.execute(Commands.battery.copy(timeoutMs = 80)) }
        assertTrue(t.closed); assertNotNull(failure.get())
        assertFailsWith<java.io.IOException> { p.execute(Commands.exposures) }
    }
    @Test fun timeoutIdentifiesTheUnansweredSettingWithoutRevealingItsValue() = session { p, _, _ ->
        val failure = assertFailsWith<ProtocolException> {
            p.execute(LeafCommand("^<SetCameraParam NewPinCode='private-pin-7391' PinEncryption='None' />", timeoutMs = 80))
        }
        assertContains(failure.message.orEmpty(), "SetCameraParam [NewPinCode, PinEncryption]")
        assertContains(failure.message.orEmpty(), "Ack")
        assertFalse(failure.message.orEmpty().contains("private-pin-7391"))
        assertFalse(failure.messageForLanguage(true).contains("private-pin-7391"))
    }
    @Test fun truncatedImageFailsPendingRequest() = session { p, t, _ ->
        val request = async { runCatching { p.execute(Commands.image("a.mos")) } }
        t.output.receive(); t.send("^<Ack file_name='a.mos' attachment='6' />123"); t.input.close()
        assertIs<ProtocolException>(request.await().exceptionOrNull())
    }
    @Test fun explicitCameraErrorIsNotSuccess() = session { p, t, _ ->
        val request = async { runCatching { p.execute(Commands.battery) } }
        t.output.receive(); t.send("^<Error error_code='4' />")
        assertIs<ProtocolException>(request.await().exceptionOrNull())
    }
    @Test fun ackWithNonzeroErrorCodeIsNotSuccess() = session { p, t, _ ->
        val request = async { runCatching { p.execute(Commands.battery) } }
        t.output.receive(); t.send("^<Ack error_code='5' />")
        assertIs<ProtocolException>(request.await().exceptionOrNull())
    }
}
