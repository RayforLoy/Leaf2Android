package com.leaf2android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.leaf2android.data.*
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File

/** The wire peer is artificial. This validates the Android JPEG/cache pipeline, not Bluetooth hardware. */
class RepositorySessionTest {
    private class Peer : LeafTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val commands = Channel<String>(Channel.UNLIMITED)
        override suspend fun connect() { }
        override suspend fun read(buffer: ByteArray): Int {
            val bytes = input.receiveCatching().getOrNull() ?: return -1
            bytes.copyInto(buffer); return bytes.size
        }
        override suspend fun write(bytes: ByteArray) { commands.send(bytes.toString(Charsets.US_ASCII)) }
        override fun close() { input.close() }
        suspend fun reply(text: String) { input.send(text.toByteArray()) }
    }
    @Test fun twentySyntheticCapturesReachCorrectJpegCache() = runBlocking {
        withTimeout(30_000) {
            val repository = LeafRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            val peer = Peer()
            val images = (0 until 20).associateWith { i ->
                val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.rgb(i * 10, 100, 50))
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it); bitmap.recycle() }.toByteArray()
            }
            val server = launch(Dispatchers.Default) {
                for (command in peer.commands) when {
                    command == Commands.sync.text -> peer.reply("^<Sync cameratype='Synthetic fixture' />")
                    command == Commands.currentTime.text -> peer.reply("^<Ack currenttime='2008-01-01' />")
                    command == Commands.directory().text -> peer.reply("^<Ack subject='./' dir='' />")
                    command.startsWith("^<GetImage") -> {
                        val path = HeaderParser.parse(command.toByteArray()).value("file_name")!!
                        val i = path.substringAfterLast('-').substringBefore('.').toInt()
                        val bytes = images.getValue(i)
                        peer.reply("^<Ack file_name='$path' attachment='${bytes.size}' />")
                        bytes.toList().chunked(100).forEach { peer.input.send(it.toByteArray()) }
                    }
                    command == "^<Ack />" -> Unit
                    else -> error("Unexpected command: $command")
                }
            }
            try {
                withContext(Dispatchers.Main) { repository.connect("00:00:00:00:00:00", "Synthetic test peer", peer) }
                while (!repository.state.value.connected) {
                    assertNotEquals(repository.state.value.error?.resolve(), SessionPhase.FAILED, repository.state.value.phase); delay(20)
                }
                repeat(20) { i -> peer.reply("^<PictureTaken file_name='./fixture-$i.mos' imageSaved='yes' />") }
                while (repository.state.value.previewCount < 20) {
                    assertNotEquals(repository.state.value.error?.resolve(), SessionPhase.FAILED, repository.state.value.phase); delay(20)
                }
                val photos = repository.state.value.photos
                repeat(20) { i ->
                    val photo = photos.single { it.path == "./fixture-$i.mos" }
                    assertEquals(80, photo.width); assertEquals(60, photo.height)
                    assertEquals(PhotoStore.hash(images.getValue(i)), photo.sha256)
                    assertArrayEquals(images.getValue(i), File(photo.cacheFile!!).readBytes())
                }
            } finally { withContext(Dispatchers.Main) { repository.disconnect() }; server.cancelAndJoin() }
        }
    }
}
