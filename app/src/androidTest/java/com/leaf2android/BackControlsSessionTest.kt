package com.leaf2android

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.graphics.Color
import com.leaf2android.data.*
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

/** All replies are synthetic; these tests send no Bluetooth commands to camera hardware. */
class BackControlsSessionTest {
    internal class Peer(val modern: Boolean = false) : LeafTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val commands = Channel<String>(Channel.UNLIMITED)
        val writes = mutableListOf<String>()
        var beforeSyncReply: (suspend (Int) -> Unit)? = null
        var onWrite: ((String) -> Unit)? = null
        private var syncRequests = 0
        var iso = "100"
        var reject = false
        var ignoreWrite = false
        var resetLight = false
        var light = "Daylight"
        var keepSourceOnMove = false
        val rootEntries = linkedSetOf("D:Shots", "F:a.mos")
        val targetEntries = linkedSetOf<String>()
        override suspend fun connect() = Unit
        override suspend fun read(buffer: ByteArray): Int {
            val bytes = input.receiveCatching().getOrNull() ?: return -1
            bytes.copyInto(buffer); return bytes.size
        }
        override suspend fun write(bytes: ByteArray) {
            val command = bytes.toString(Charsets.US_ASCII)
            // Observe the actual write even while the synthetic back is holding a reply.
            onWrite?.invoke(command)
            commands.send(command)
        }
        override fun close() { input.close() }
        suspend fun reply(header: String) { input.send(header.toByteArray()) }
        fun setup() = "SetupName=Default,ShootingFolder=./Shots/,ISO=$iso,ImageQuality=RAW,Framing=Full,Toning=Normal,Light=$light,SharpTable=Normal,InputProfile=Back,OutputProfile=RGB,CameraType=Technical,ICCFlag=1,Prefix=IMG,FileCompression=None,Orientation=Landscape,Color=color"
        suspend fun serve() {
            for (command in commands) {
                writes.add(command)
                val frame = HeaderParser.parse(command.toByteArray())
                when (frame.tag) {
                    "RequestSync" -> {
                        syncRequests++
                        beforeSyncReply?.invoke(syncRequests)
                        reply(if (modern) "^<Sync ISO='50;100;200' CameraSettings='ShootingFolder=./Shots/,ISO=$iso,Orientation=Landscape,CameraType=Technical,Prefix=IMG,SensorCrop=Full,FileCompression=None' />"
                            else "^<Sync iso='50;100;200' defaultshootsetup='Default' shootsetup='${setup()}' light='Daylight;Tungsten' defaultorientation='Landscape' defaultcolor='color' />")
                    }
                    "GetCameraParam" -> reply("^<Ack CurrentTime='2008:01:01:00:00:00' />")
                    "SetCameraParam" -> {
                        if (reject) reply("^<Ack error_code='5' />") else {
                            if (!ignoreWrite) {
                                val setup = frame.value("ShootSetup")?.let(ShootSetup::parse)
                                iso = frame.value("ISO") ?: setup?.get(ShootField.ISO) ?: iso
                                light = setup?.get(ShootField.LIGHT) ?: light
                            }
                            if (resetLight) light = "Tungsten"
                            reply("^<Ack error_code='0' />")
                        }
                    }
                    "GetImage" -> {
                        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
                        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle() }.toByteArray()
                        peerImage(frame.value("file_name")!!, bytes, if (frame.value("light") != null) "SampledBalance" else null)
                    }
                    "FileCommand" -> {
                        val operation = frame.value("FileCommand")
                        val path = frame.value("Subject")!!
                        when (operation) {
                            "dir" -> reply("^<Ack subject='$path' dir='${if (path == "./") rootEntries.joinToString(";") else if (path == "./Target/") targetEntries.joinToString(";") else ""}' />")
                            "mkdir" -> { rootEntries.add("D:"+path.trimEnd('/').substringAfterLast('/')); reply("^<Ack />") }
                            "del" -> { rootEntries.remove("F:"+path.substringAfterLast('/')); reply("^<Ack />") }
                            "move", "copy" -> {
                                targetEntries.add("F:"+path.substringAfterLast('/'))
                                if (operation == "move" && !keepSourceOnMove) rootEntries.remove("F:"+path.substringAfterLast('/'))
                                reply("^<Ack />")
                            }
                            else -> error("Unexpected file operation: $command")
                        }
                    }
                    "Ack" -> Unit
                    else -> error("Unexpected command: $command")
                }
            }
        }
        private suspend fun peerImage(path: String, bytes: ByteArray, light: String? = null) {
            reply("^<Ack file_name='$path' attachment='${bytes.size}'" + (light?.let { " light='$it'" } ?: "") + " />")
            input.send(bytes)
        }
    }
    private fun session(modern: Boolean = false, block: suspend (LeafRepository, Peer) -> Unit) = runBlocking {
        withTimeout(20_000) {
            val repository = LeafRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            val peer = Peer(modern)
            val server = launch(Dispatchers.Default) { peer.serve() }
            try {
                withContext(Dispatchers.Main) { repository.connect("00:00:00:00:00:00", "Synthetic control peer", peer) }
                waitFor { repository.state.value.connected }
                block(repository, peer)
            } finally { withContext(Dispatchers.Main) { repository.disconnect() }; server.cancelAndJoin() }
        }
    }
    private suspend fun waitFor(predicate: () -> Boolean) { while (!predicate()) delay(20) }
    private suspend fun edit(repository: LeafRepository, value: String) {
        val active = ShootingConfiguration.from(repository.state.value.camera).active!!
        withContext(Dispatchers.Main) { repository.applyShootSetup(active, mapOf(ShootField.ISO to value)) }
    }
    @Test fun legacyIsoUsesFullSetupAndConfirmsCurrentIsoInsteadOfOptionList() = session { repo, peer ->
        assertEquals("100", repo.state.value.currentIso)
        val before = peer.setup()
        edit(repo, "200")
        waitFor { repo.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
        assertEquals("200", repo.state.value.currentIso)
        val setter = peer.writes.single { it.startsWith("^<SetCameraParam") }
        assertEquals("^<SetCameraParam ShootSetup=\"${before.replace("ISO=100", "ISO=200")}\" Orientation=\"Landscape\" Color=\"Color\" />", setter)
        assertEquals(3, peer.writes.count { it == Commands.sync.text })
    }
    @Test fun directIsoDialectUsesCameraSettingsAndReadsBack() = session(true) { repo, peer ->
        withContext(Dispatchers.Main) { repo.applyDirectIso("100", "200") }
        waitFor { repo.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
        assertEquals("200", repo.state.value.currentIso)
        assertTrue(peer.writes.contains("^<SetCameraParam ISO=\"200\" />"))
        assertFalse(peer.writes.any { it.contains("ShootSetup=") })
    }
    @Test fun captureNotificationAcknowledgesImmediatelyWithoutInterruptingIsoTransaction() = session { repo, peer ->
        val observedWrites = Channel<WireFrame>(Channel.UNLIMITED)
        val preSyncHeld = CompletableDeferred<Unit>()
        val releasePreSync = CompletableDeferred<Unit>()
        // A fresh path also excludes a persisted preview from an earlier instrumented test.
        val photoPath = "./iso-transaction-${System.nanoTime()}.mos"
        peer.onWrite = { command ->
            check(observedWrites.trySend(HeaderParser.parse(command.toByteArray(Charsets.US_ASCII))).isSuccess)
        }
        peer.beforeSyncReply = { number ->
            if (number == 2) { // Initial connection Sync is #1; this is the setter's preflight Sync.
                peer.reply("^<PictureTaken file_name='$photoPath' imageSaved='yes' />")
                preSyncHeld.complete(Unit)
                releasePreSync.await()
            }
        }
        suspend fun nextWrite() = withTimeout(3_000) { observedWrites.receive() }
        try {
            edit(repo, "200")
            withTimeout(3_000) { preSyncHeld.await() }
            assertEquals("RequestSync", nextWrite().tag)
            // Ack must bypass the operation lock, before the back releases its pending Sync.
            assertEquals("Ack", nextWrite().tag)
            assertFalse(releasePreSync.isCompleted)
            waitFor { repo.state.value.photos.any { it.path == photoPath } }
            withContext(Dispatchers.Main) {
                repo.refreshStatus() // A second user action must wait for the whole transaction too.
                yield() // Let the notification's queued image worker reach its operation lock.
            }
            assertNull("No other command may run while preflight Sync is outstanding", observedWrites.tryReceive().getOrNull())
            releasePreSync.complete(Unit)
            assertEquals("SetCameraParam", nextWrite().tag)
            assertEquals("RequestSync", nextWrite().tag)
            // Both automatic image transfer and the complete queued status action run afterwards.
            val afterReadback = List(4) { nextWrite() }
            assertEquals(1, afterReadback.count { it.tag == "GetImage" && it.value("file_name") == photoPath })
            assertEquals(3, afterReadback.count { it.tag == "GetCameraParam" })
            waitFor {
                repo.state.value.currentIso == "200" && repo.state.value.photos.any {
                    it.path == photoPath && it.cacheFile != null && it.connectionGeneration == repo.state.value.connectionGeneration
                } && !repo.state.value.controlsBusy
            }
            assertTrue(repo.state.value.connected)
            assertNull(repo.state.value.error)
        } finally {
            releasePreSync.complete(Unit)
            peer.onWrite = null
            peer.beforeSyncReply = null
        }
    }
    @Test fun readbackMismatchAndRejectedWriteAreNotReportedAsSuccess() = session { repo, peer ->
        peer.ignoreWrite = true
        edit(repo, "200")
        waitFor { repo.state.value.error?.resourceId == R.string.shooting_write_not_confirmed }
        assertEquals("100", repo.state.value.currentIso)
        assertNull(repo.state.value.notice)
        withContext(Dispatchers.Main) { repo.clearMessage() }
        peer.reject = true
        edit(repo, "200")
        waitFor { repo.state.value.error != null }
        assertNull(repo.state.value.notice)
        assertEquals("100", peer.iso)
    }
    @Test fun changedOrUnsupportedIsoNeverSendsASetter() = session { repo, peer ->
        peer.iso = "50" // Another camera-side operation changed the active setup after the UI read it.
        edit(repo, "200")
        waitFor { repo.state.value.error?.resourceId == R.string.shooting_settings_changed }
        withContext(Dispatchers.Main) { repo.clearMessage() }
        edit(repo, "6400")
        waitFor { repo.state.value.error != null }
        assertFalse(peer.writes.any { it.startsWith("^<SetCameraParam") })
    }
    @Test fun fileCreationAndDeletionCheckTheDirectoryAndRejectExistingNames() = session { repo, peer ->
        withContext(Dispatchers.Main) { repo.makeDirectory("./", "Shots") }
        waitFor { repo.state.value.error?.resourceId == R.string.back_name_already_exists }
        assertFalse(peer.writes.any { it.contains("FileCommand=\"mkdir\"") })
        withContext(Dispatchers.Main) { repo.makeDirectory("./", "New") }
        waitFor { repo.state.value.notice?.resourceId == R.string.back_file_action_acknowledged }
        assertTrue(repo.state.value.entries.any { it.path == "./New/" })
        withContext(Dispatchers.Main) { repo.select("./a.mos") }
        waitFor { repo.state.value.selected?.connectionGeneration == repo.state.value.connectionGeneration && repo.state.value.selected?.cacheFile != null }
        withContext(Dispatchers.Main) { repo.deletePhoto("./a.mos") }
        waitFor { repo.state.value.entries.none { it.path == "./a.mos" } && !repo.state.value.controlsBusy }
        assertFalse(peer.rootEntries.contains("F:a.mos"))
    }
    @Test fun incompleteMoveDoesNotClaimSuccessWhenSourceRemains() = session { repo, peer ->
        withContext(Dispatchers.Main) { repo.select("./a.mos") }
        waitFor { repo.state.value.selected?.connectionGeneration == repo.state.value.connectionGeneration && repo.state.value.selected?.cacheFile != null }
        peer.keepSourceOnMove = true
        withContext(Dispatchers.Main) { repo.copyOrMovePhoto("./a.mos", "./Target/", true) }
        waitFor { repo.state.value.error?.resourceId == R.string.move_source_still_present }
        assertNull(repo.state.value.notice)
        assertTrue(peer.rootEntries.contains("F:a.mos"))
        assertTrue(peer.targetEntries.contains("F:a.mos"))
    }
    @Test fun unintendedChangeToAnUntouchedSettingFailsFullReadback() = session { repo, peer ->
        peer.resetLight = true
        edit(repo, "200")
        waitFor { repo.state.value.error?.resourceId == R.string.shooting_write_not_confirmed }
        assertNull(repo.state.value.notice)
        assertEquals("200", repo.state.value.currentIso)
        assertEquals("Tungsten", ShootingConfiguration.from(repo.state.value.camera).active!![ShootField.LIGHT])
    }
    @Test fun cameraParamNotificationsDoNotOverwriteAdvertisedChoiceLists() = session(true) { repo, peer ->
        peer.iso = "200"
        peer.reply("^<CameraParamChange ISO='200' />")
        waitFor { repo.state.value.currentIso == "200" }
        assertEquals(listOf("50", "100", "200"), DirectCameraSettings.from(repo.state.value.camera)!!.isoChoices)
    }
    @Test fun xmpRoundTripsOnAndroidAndKeepsOtherMetadata() {
        val xml = "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'><rdf:Description xmlns:leaf='${XmpMetadata.LEAF_NAMESPACE}' xmlns:dc='http://purl.org/dc/elements/1.1/' leaf:Flags='ab-keep' dc:title='keep me'/></rdf:RDF></x:xmpmeta>"
        val encoded = Base64.getEncoder().encodeToString(xml.toByteArray())
        val result = XmpMetadata.decode(encoded).withGoodFlag(true)
        assertEquals("ab+keep", XmpMetadata.decode(result).flags)
        assertTrue(Base64.getDecoder().decode(result).toString(Charsets.UTF_8).contains("keep me"))
        try { XmpMetadata.decode(Base64.getEncoder().encodeToString("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///not-used'>]><x/>".toByteArray())); fail("DTD accepted") }
        catch (_: ProtocolException) { }
    }
    @Test fun backProvidedGrayBalanceCanUpdateTheFutureSetupWithoutChangingIso() = session { repo, peer ->
        withContext(Dispatchers.Main) { repo.select("./a.mos") }
        waitFor { repo.state.value.selected?.connectionGeneration == repo.state.value.connectionGeneration && repo.state.value.selected?.cacheFile != null }
        withContext(Dispatchers.Main) { repo.requestBackPreview("./a.mos", ImageZoom.FIT, null, null, ImagePoint(0,0)) }
        waitFor { repo.state.value.backPreview?.sampledLight == "SampledBalance" && !repo.state.value.controlsBusy }
        withContext(Dispatchers.Main) { repo.applySampledLightToFuture("SampledBalance") }
        waitFor { repo.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
        assertEquals("SampledBalance", peer.light)
        assertEquals("100", peer.iso)
    }
}
