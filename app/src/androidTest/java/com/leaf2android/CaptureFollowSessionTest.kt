package com.leaf2android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.leaf2android.data.*
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Models the firmware's unknown-command Error 0x01; no camera or Bluetooth radio is used. */
class CaptureFollowSessionTest {
    private class Peer : LeafTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val commands = Channel<String>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        val images = linkedMapOf<String, ByteArray>()
        var stopExpected = false
        var rejectImages = false
        val stopAck = CompletableDeferred<String>()
        override suspend fun connect() = Unit
        override suspend fun read(buffer: ByteArray): Int {
            val next = input.receiveCatching().getOrNull() ?: return -1
            next.copyInto(buffer); return next.size
        }
        override suspend fun write(bytes: ByteArray) { commands.send(bytes.toString(Charsets.US_ASCII)) }
        override fun close() { input.close() }
        suspend fun reply(text: String) { input.send(text.toByteArray()) }
        fun addImage(path: String, value: Int) {
            val bitmap = Bitmap.createBitmap(24, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(value * 8, 80, 100)) }
            images[path] = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle() }.toByteArray()
        }
        suspend fun serve() {
            for (text in commands) {
                sent += text
                val frame = HeaderParser.parse(text.toByteArray())
                when (frame.tag) {
                    "RequestSync" -> reply("^<Sync model='Synthetic strict PDA' picturedimensions='4000,3000' />")
                    "GetCameraParam" -> reply("^<Ack error_code='0x00' CurrentTime='2004:01:01:00:00:00' />")
                    "FileCommand" -> reply("^<Ack subject='./' dir='' />")
                    "GetImage" -> {
                        val path = frame.value("file_name")!!
                        val bytes = images[path] ?: error("Wrong image association: $path")
                        if (rejectImages) reply("^<Error error_code='0x02' />") else {
                            reply("^<Ack file_name='$path' attachment='${bytes.size}' />")
                            input.send(bytes)
                        }
                    }
                    "Ack" -> if (stopExpected) stopAck.complete(text) else reply("^<Error error_code='0x01' />")
                    else -> reply("^<Error error_code='0x01' />")
                }
            }
        }
    }
    private fun session(block: suspend (LeafRepository, Peer) -> Unit) = runBlocking {
        withTimeout(25_000) {
            val repo = LeafRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            val peer = Peer()
            val server = launch(Dispatchers.Default) { peer.serve() }
            val settings = repo.settings.value
            try {
                withContext(Dispatchers.Main) {
                    repo.updateSettings { it.copy(autoFollow = true) }
                    repo.connect("00:00:00:00:00:32", "Synthetic strict PDA", peer)
                }
                until { repo.state.value.connected }
                block(repo, peer)
            } finally {
                withContext(Dispatchers.Main) { repo.disconnect(); repo.updateSettings { settings } }
                server.cancelAndJoin()
            }
        }
    }
    private suspend fun until(predicate: () -> Boolean) { while (!predicate()) delay(15) }

    @Test fun twentyHexZeroSavedNotificationsFollowCorrectImagesWithoutUnsupportedAcks() = session { repo, peer ->
        val stamp = System.nanoTime()
        val paths = (0 until 20).map { "./capture-$stamp-$it.mos" }
        paths.forEachIndexed { index, path -> peer.addImage(path, index) }
        paths.forEach { peer.reply("^<PictureTaken file_name='$it' imageSaved='yes' error_code='0x00' />") }
        until { repo.state.value.previewCount == 20 }
        assertEquals(paths.last(), repo.state.value.selectedPath)
        assertTrue(repo.state.value.connected); assertNull(repo.state.value.error)
        assertFalse(peer.sent.any { HeaderParser.parse(it.toByteArray()).tag == "Ack" })
        paths.forEach { path ->
            val photo = repo.state.value.photos.single { it.path == path }
            assertEquals(PhotoStore.hash(peer.images.getValue(path)), photo.sha256)
            assertArrayEquals(peer.images.getValue(path), File(photo.cacheFile!!).readBytes())
        }
    }

    @Test fun ordinaryAsyncHeadersAndLaboratoryEventsDoNotGenerateAck() = session { repo, peer ->
        listOf("StorageNotification", "PowerManagement", "PictureParamsChange", "FileSystemChange", "ReadyToShoot", "AsyncCommand", "BeepNotification", "IfGuiIsActive", "CameraParamChange").forEach {
            peer.reply("^<$it />")
        }
        until { repo.state.value.notice?.resolve()?.contains("IfGuiIsActive") == true || repo.logs.value.any { it.contains("CameraParamChange") } }
        withContext(Dispatchers.Main) { repo.enterLaboratory() }
        peer.reply("^<PictureTaken file_name='./lab-only.mos' imageSaved='yes' />")
        until { repo.state.value.labEvents.any { it.contains("lab-only.mos") } }
        assertFalse(peer.sent.any { HeaderParser.parse(it.toByteArray()).tag == "Ack" })
        assertFalse(peer.sent.any { it.contains("lab-only.mos") })
        withContext(Dispatchers.Main) { repo.leaveLaboratory() }
        assertTrue(repo.state.value.connected)
    }

    @Test fun stopUsesTheOriginalDedicatedAckAndEndsTheSession() = session { repo, peer ->
        peer.stopExpected = true
        peer.reply("^<Stop />")
        assertEquals("^<Ack/>", peer.stopAck.await())
        until { !repo.state.value.connected }
        assertEquals(SessionPhase.FAILED, repo.state.value.phase)
    }

    @Test fun busyAndUnsavedEventsDoNotReadUntilAnExplicitSavedNoticeArrives() = session { repo, peer ->
        val path = "./deferred-${System.nanoTime()}.mos"
        peer.addImage(path, 1)
        peer.reply("^<PictureTaken file_name='$path' busy='yes' />")
        until { repo.state.value.notice?.resourceId == R.string.the_back_is_processing_the_photo_waiting_for_a_d4e74 }
        assertFalse(peer.sent.any { it.startsWith("^<GetImage") })
        peer.reply("^<PictureTaken file_name='$path' imageSaved='no' />")
        peer.reply("^<PictureTaken file_name='$path' imageSaved='yes' error_code='0X00' />")
        until { repo.state.value.previewCount == 1 }
        assertEquals(path, repo.state.value.selectedPath)
        assertEquals(1, peer.sent.count { it.startsWith("^<GetImage") })
        assertNull(repo.state.value.error)
    }
    @Test fun aReadRefusalKeepsTheSessionAndSuccessfulReadsClearOnlyThePreviewError() = session { repo, peer ->
        val path = "./read-error-${System.nanoTime()}.mos"
        peer.addImage(path, 1); peer.rejectImages = true
        withContext(Dispatchers.Main) { repo.select(path) }
        until { repo.state.value.error != null }
        assertTrue(repo.state.value.connected)
        assertTrue(repo.state.value.error!!.resolve().contains("GetImage"))
        peer.rejectImages = false
        withContext(Dispatchers.Main) { repo.refreshSelected() }
        until { repo.state.value.previewCount == 1 }
        assertNull(repo.state.value.error)
        // The first preview may still be persisting; a second file avoids assuming the dedup queue is idle.
        val secondPath = "./other-${System.nanoTime()}.mos"
        peer.addImage(secondPath, 2)
        withContext(Dispatchers.Main) { repo.showError("Keep this unrelated control error"); repo.select(secondPath) }
        until { repo.state.value.previewCount == 2 }
        assertEquals("Keep this unrelated control error", repo.state.value.error!!.resolve())
        assertTrue(repo.state.value.connected)
    }
}
