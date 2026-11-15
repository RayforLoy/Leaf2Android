package com.leaf2android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.leaf2android.data.ShootingCompatibility
import com.leaf2android.data.UiStyle
import com.leaf2android.data.Viewport
import com.leaf2android.localization.L10n
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class PhotoGrayPickUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** The dimensions and orientation are deliberate fixture evidence, not device-discovery defaults. */
    private class GeometryPeer(private val orientation: String) : LeafTransport {
        private val input = Channel<ByteArray>(Channel.UNLIMITED)
        private val commands = Channel<String>(Channel.UNLIMITED)
        val sampleRequest = CompletableDeferred<WireFrame>()
        val futureWrite = CompletableDeferred<WireFrame>()
        val photoWrite = CompletableDeferred<WireFrame>()
        val metadataReads = AtomicInteger()
        private var light = "Daylight"
        override suspend fun connect() = Unit
        override suspend fun read(buffer: ByteArray): Int {
            val bytes = input.receiveCatching().getOrNull() ?: return -1
            bytes.copyInto(buffer)
            return bytes.size
        }
        override suspend fun write(bytes: ByteArray) { commands.send(bytes.toString(Charsets.US_ASCII)) }
        override fun close() { input.close() }
        private suspend fun reply(header: String) { input.send(header.toByteArray(Charsets.US_ASCII)) }
        private fun setup() = "SetupName=Default,ShootingFolder=./Shots/,ISO=100,ImageQuality=RAW,Framing=Full,Toning=Normal,Light=$light,SharpTable=Normal,InputProfile=Back,OutputProfile=RGB,CameraType=Technical,ICCFlag=1,Prefix=IMG,FileCompression=None,Orientation=Landscape,Color=color"
        suspend fun serve() {
            val portrait = orientation.startsWith("Portrait")
            val bitmap = Bitmap.createBitmap(if (portrait) 60 else 80, if (portrait) 80 else 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
            val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle() }.toByteArray()
            for (command in commands) {
                val frame = HeaderParser.parse(command.toByteArray(Charsets.US_ASCII))
                when (frame.tag) {
                    "RequestSync" -> reply("^<Sync picturedimensions='4000,3000' iso='100;200' defaultshootsetup='Default' shootsetup='${setup()}' defaultorientation='Landscape' defaultcolor='color' />")
                    "GetCameraParam" -> reply("^<Ack CurrentTime='2008:01:01:00:00:00' />")
                    "FileCommand" -> reply("^<Ack subject='${frame.value("Subject")}' dir='F:a.mos' />")
                    "GetPictureParams" -> {
                        metadataReads.incrementAndGet()
                        reply("^<Ack file_name='${frame.value("file_name")}' orientation='$orientation' marking='Unmarked' />")
                    }
                    "GetImage" -> {
                        val sampling = frame.value("light") != null
                        if (sampling) sampleRequest.complete(frame)
                        reply("^<Ack file_name='${frame.value("file_name")}' attachment='${jpeg.size}'${if (sampling) " light='SampledBalance'" else ""} />")
                        input.send(jpeg)
                    }
                    "SetCameraParam" -> {
                        val setup = ShootSetup.parse(requireNotNull(frame.value("ShootSetup")))
                        light = requireNotNull(setup[ShootField.LIGHT])
                        futureWrite.complete(frame)
                        reply("^<Ack error_code='0' />")
                    }
                    "SetPictureParams" -> { photoWrite.complete(frame); reply("^<Ack error_code='0' />") }
                    else -> error("Unexpected geometry fixture command: $command")
                }
            }
        }
    }

    @Test fun landscapePointIsPreviewedThenRequiresSeparateConfirmationsForFutureAndCurrentPhoto() = exerciseGrayPoint("Landscape", true)

    @Test fun clockwisePortraitPointDoesNotRepeatTheOriginalPdaDisplayRotation() = exerciseGrayPoint("Portrait CW", false)

    @Test fun counterclockwisePortraitPointDoesNotRepeatTheOriginalPdaDisplayRotation() = exerciseGrayPoint("Portrait CCW", false)

    private fun exerciseGrayPoint(orientation: String, confirmWrites: Boolean) {
        val app = rule.activity.application as LeafApplication
        val previous = app.repository.settings.value
        val previousViewport = app.repository.viewport("./a.mos")
        val peer = GeometryPeer(orientation)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = scope.launch { peer.serve() }
        try {
            rule.runOnUiThread {
                app.repository.disconnect()
                app.repository.updateSettings { it.copy(style = UiStyle.MODERN, shootingCompatibility = ShootingCompatibility.WIVIEW, showHistogram = false) }
                L10n.setLanguage("en")
            }
            rule.waitForIdle()
            rule.runOnUiThread { app.repository.connect("00:00:00:00:00:00", "Verified-geometry synthetic peer", peer) }
            rule.waitUntil(10_000) { app.repository.state.value.connected }
            rule.runOnUiThread { app.repository.setViewport("./a.mos", Viewport()); app.repository.select("./a.mos") }
            rule.waitUntil(10_000) {
                app.repository.state.value.selected?.let { it.path == "./a.mos" && it.cacheFile != null && it.connectionGeneration == app.repository.state.value.connectionGeneration } == true
            }
            rule.onNodeWithTag("page-SHOOT").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("photo-image").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("photo-open-image-tools").performClick()
            fun metadataIsCurrent(): Boolean {
                val state = app.repository.state.value
                val photo = state.selected ?: return false
                return photo.metadataPreviewSha256 == photo.sha256 && state.operation.isEmpty() &&
                    photo.metadata.entries.any { it.key.equals("orientation", true) && it.value == orientation }
            }
            // CW exercises the new automatic read; the other two start with a verified mapping.
            if (orientation != "Portrait CW") {
                rule.onNodeWithTag("read-gray-pick-metadata").performScrollTo().performClick()
                rule.waitUntil(10_000) { metadataIsCurrent() }
            }
            rule.onNodeWithTag("start-gray-pick").performScrollTo().performClick()
            rule.onNodeWithTag("gray-pick-mode").assertIsDisplayed()
            rule.waitUntil(10_000) { metadataIsCurrent() }
            val portrait = orientation.startsWith("Portrait")
            val jpegWidth = if (portrait) 60 else 80
            val jpegHeight = if (portrait) 80 else 60
            val canvas = rule.onNodeWithTag("photo-canvas").fetchSemanticsNode().size
            val fit = min(canvas.width.toFloat() / jpegWidth, canvas.height.toFloat() / jpegHeight)
            val tap = Offset(canvas.width / 2f - jpegWidth * fit / 4f, canvas.height / 2f + jpegHeight * fit / 4f)
            rule.onNodeWithTag("photo-canvas").performTouchInput { click(tap) }
            rule.waitUntil(10_000) { app.repository.state.value.backPreview?.sampledPoint != null && !app.repository.state.value.controlsBusy }
            val sampledPoint = app.repository.state.value.backPreview!!.sampledPoint!!
            // Compose rounds the test node's physical bounds; the pure geometry tests cover exact fractions.
            val expectedX = if (portrait) 750 else 1000
            val expectedY = if (portrait) 3000 else 2250
            assertTrue(sampledPoint.x in expectedX - 2..expectedX + 2)
            assertTrue(sampledPoint.y in expectedY - 2..expectedY + 2)
            val sample = runBlocking { peer.sampleRequest.await() }
            assertEquals("${sampledPoint.x},${sampledPoint.y}", sample.value("light"))
            assertNull(sample.value("zoom"))
            assertEquals("An existing verified mapping must not trigger another metadata read", 1, peer.metadataReads.get())
            assertFalse(peer.futureWrite.isCompleted)
            assertFalse(peer.photoWrite.isCompleted)
            rule.onNodeWithTag("apply-gray-future").performScrollTo()
            capture("gray-point-${orientation.lowercase().replace(' ', '-')}-en")
            if (!confirmWrites) return
            rule.onNodeWithTag("apply-gray-future").performScrollTo().performClick()
            assertFalse(peer.futureWrite.isCompleted)
            rule.onNodeWithTag("confirm-gray-future").performClick()
            rule.waitUntil(10_000) { app.repository.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
            assertEquals("100", app.repository.state.value.currentIso)
            val future = runBlocking { peer.futureWrite.await() }
            assertEquals("SampledBalance", ShootSetup.parse(future.value("ShootSetup")!!)[ShootField.LIGHT])
            assertFalse(peer.photoWrite.isCompleted)
            rule.runOnUiThread { L10n.setLanguage("zh") }
            rule.waitForIdle()
            rule.onNodeWithTag("apply-gray-current").performScrollTo().performClick()
            assertFalse(peer.photoWrite.isCompleted)
            capture("gray-current-confirm-zh")
            rule.onNodeWithTag("confirm-gray-current").performClick()
            rule.waitUntil(10_000) { peer.photoWrite.isCompleted && !app.repository.state.value.controlsBusy }
            val photo = runBlocking { peer.photoWrite.await() }
            assertEquals("./a.mos", photo.value("file_name"))
            assertEquals("SampledBalance", photo.value("light"))
            assertEquals("yes", photo.value("imageUpdate"))
            assertEquals("100", app.repository.state.value.currentIso)
            assertTrue(app.repository.state.value.connected)
        } finally {
            rule.runOnUiThread {
                app.repository.disconnect()
                app.repository.clearMessage()
                app.repository.updateSettings { previous }
                app.repository.setViewport("./a.mos", previousViewport)
                L10n.setLanguage("")
            }
            server.cancel()
            scope.cancel()
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/Download/Leaf2Android-$name.png")).use { it.readBytes() }
    }

    @Suppress("DEPRECATION")
    @Test fun bitmapFactoryKeepsTheEncodedJpegRasterInsteadOfApplyingExifRotation() {
        val file = File.createTempFile("leaf-geometry-", ".jpg", rule.activity.cacheDir)
        try {
            val source = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            source.recycle()
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath)!!
            assertEquals(80, decoded.width)
            assertEquals(60, decoded.height)
            decoded.recycle()
        } finally { file.delete() }
    }
}
