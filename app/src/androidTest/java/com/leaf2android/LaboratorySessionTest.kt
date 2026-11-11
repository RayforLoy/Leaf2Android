package com.leaf2android

import androidx.test.platform.app.InstrumentationRegistry
import com.leaf2android.data.*
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Artificial wire peer: no real camera command, pairing change or storage write is performed. */
class LaboratorySessionTest {
    private class Peer : LeafTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val output = Channel<String>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        override suspend fun connect() = Unit
        override suspend fun read(buffer: ByteArray): Int { val b = input.receiveCatching().getOrNull() ?: return -1; b.copyInto(buffer); return b.size }
        override suspend fun write(bytes: ByteArray) { output.send(bytes.toString(Charsets.US_ASCII)) }
        override fun close() { input.close() }
        suspend fun reply(header: String) { input.send(header.toByteArray()) }
        suspend fun serve() {
            for (text in output) {
                sent += text
                val frame = HeaderParser.parse(text.toByteArray())
                when (frame.tag) {
                    "RequestSync" -> reply("^<Sync model='Synthetic laboratory peer' />")
                    "FileCommand" -> reply("^<Ack subject='./' dir='' />")
                    "GetCameraParam" -> reply("^<Ack CurrentTime='2008:01:01:00:00:00' mystery='observed' />")
                    "SetCameraParam" -> reply("^<Ack PinCode='1234' error_code='0' />")
                    "RejectProbe" -> reply("^<Error error_code='9' detail='unsupported experiment' />")
                    "UnverifiedProbe" -> reply("^<UnregisteredReply value='observed' />")
                    "GetImage" -> { reply("^<Ack file_name='./probe.mos' attachment='4' />"); input.send(byteArrayOf(94,62,0,127)) }
                    "Ack" -> Unit
                    "SilenceProbe" -> Unit
                    else -> error("Unexpected synthetic command: $text")
                }
            }
        }
    }
    private fun session(block: suspend (LeafRepository, Peer) -> Unit) = runBlocking {
        withTimeout(20_000) {
            val repo = LeafRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            val peer = Peer()
            val server = launch(Dispatchers.Default) { peer.serve() }
            try {
                withContext(Dispatchers.Main) { repo.connect("00:00:00:00:00:00", "Synthetic laboratory peer", peer) }
                until { repo.state.value.connected }
                withContext(Dispatchers.Main) { repo.enterLaboratory() }
                block(repo, peer)
            } finally { withContext(Dispatchers.Main) { repo.disconnect() }; server.cancelAndJoin() }
        }
    }
    private suspend fun until(predicate: () -> Boolean) { while (!predicate()) delay(20) }
    private suspend fun run(repo: LeafRepository, raw: String, tag: String = "Ack", file: String? = null, attachment: Boolean = false): LabRecord {
        val previous = repo.state.value.labRecords.firstOrNull()?.id
        withContext(Dispatchers.Main) { repo.runLabExperiment(raw, tag, file, attachment, 2000) }
        until { repo.state.value.labRecords.firstOrNull()?.id != previous && !repo.state.value.labBusy }
        return repo.state.value.labRecords.first()
    }
    @Test fun rejectedAndUnknownRepliesAreEvidenceRatherThanSuccessOrForcedSessionFailure() = session { repo, _ ->
        val rejected = run(repo, "^<RejectProbe />")
        assertEquals(LabOutcome.RECEIVED, rejected.outcome)
        assertEquals("Error", rejected.responseTag)
        assertEquals("9", rejected.responseErrorCode)
        assertTrue(repo.state.value.connected)
        assertNull(repo.state.value.notice)
        val unknown = run(repo, "^<UnverifiedProbe />", "*")
        assertEquals("UnregisteredReply", unknown.responseTag)
        assertTrue(unknown.json().getBoolean("first_frame_is_uncorrelated"))
        assertFalse(unknown.json().getBoolean("response_proves_effect"))
        withContext(Dispatchers.Main) { repo.leaveLaboratory() }
        assertFalse(repo.state.value.connected)
    }
    @Test fun pinBytesAreSentButNeverStoredInHeadersHexOrTransportLogs() = session { repo, peer ->
        val record = run(repo, "^<SetCameraParam NewPinCode='1234' PinEncryption='None' />")
        assertTrue(peer.sent.any { it.contains("1234") })
        assertFalse(record.request.contains("1234"))
        assertFalse(record.response!!.contains("1234"))
        assertNull(record.requestHex); assertNull(record.responseHex)
        assertFalse(repo.logs.value.joinToString().contains("1234"))
        assertFalse(record.json().toString().contains("1234"))
    }
    @Test fun attachmentsArePreservedAndExportedAndTheNextExperimentCanRun() = session { repo, _ ->
        val record = run(repo, "^<GetImage file_name='./probe.mos' />", file = "./probe.mos", attachment = true)
        val expected = byteArrayOf(94,62,0,127)
        assertArrayEquals(expected, File(record.attachmentFile!!).readBytes())
        assertEquals(PhotoStore.hash(expected), record.attachmentSha256)
        assertEquals("5e3e007f", record.attachmentHex)
        assertEquals(0, repo.state.value.expected)
        val sentOnly = run(repo, "^<Ack />", "")
        assertEquals(LabOutcome.SENT_ONLY, sentOnly.outcome)
        val ready = CompletableDeferred<File>()
        withContext(Dispatchers.Main) { repo.exportLaboratory { ready.complete(it) } }
        ZipFile(ready.await()).use { zip ->
            assertArrayEquals(expected, zip.getInputStream(zip.getEntry("attachments/${record.id}.bin")).readBytes())
            val json = zip.getInputStream(zip.getEntry("experiments.json")).reader().readText()
            assertTrue(json.contains("transport_log_redacted"))
        }
    }
    @Test fun laboratoryPausesAutomaticCaptureRequestsAndCanLeaveWithoutSending() = session { repo, peer ->
        peer.reply("^<PictureTaken file_name='./not-auto-downloaded.mos' imageSaved='yes' />")
        until { repo.state.value.labEvents.any { it.contains("not-auto-downloaded") } }
        assertFalse(peer.sent.any { it.startsWith("^<GetImage") })
        withContext(Dispatchers.Main) { repo.leaveLaboratory() }
        assertTrue(repo.state.value.connected)
        assertFalse(repo.state.value.labMode)
    }
    @Test fun timeoutIsRecordedAsAWaitFailureAndClosesTheUncertainStream() = session { repo, _ ->
        val result = run(repo, "^<SilenceProbe />")
        assertEquals(LabOutcome.ERROR, result.outcome)
        assertNotNull(result.error)
        assertFalse(repo.state.value.connected)
        assertEquals(SessionPhase.FAILED, repo.state.value.phase)
    }
}
