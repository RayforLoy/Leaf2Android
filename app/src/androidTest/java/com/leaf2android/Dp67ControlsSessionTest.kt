package com.leaf2android

import androidx.test.platform.app.InstrumentationRegistry
import com.leaf2android.data.*
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

/** A deliberately old, synthetic DP-67 peer. No Bluetooth hardware is contacted. */
class Dp67ControlsSessionTest {
    private class Peer(initialModified: Boolean = false) : LeafTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val commands = Channel<String>(Channel.UNLIMITED)
        val writes = mutableListOf<String>()
        val factory = "SetupName=Default,ShootingFolder=./Shots/,ISO=100,ImageQuality=RAW,Framing=Full,Toning=Normal,Light=Daylight,SharpTable=Normal,InputProfile=Back,OutputProfile=RGB,CameraType=Technical,ICCFlag=1"
        val setups = linkedMapOf("Default" to ShootSetup.parse(factory))
        var active = "Default"
        var ignoreWrite = false

        init { if (initialModified) {
            setups["Default.1"] = setups.getValue("Default").changed(mapOf(ShootField.NAME to "Default.1"))
            active = "Default.1"
        } }
        override suspend fun connect() = Unit
        override suspend fun read(buffer: ByteArray): Int {
            val next = input.receiveCatching().getOrNull() ?: return -1
            next.copyInto(buffer); return next.size
        }
        override suspend fun write(bytes: ByteArray) { commands.send(bytes.toString(Charsets.US_ASCII)) }
        override fun close() { input.close() }
        private suspend fun reply(header: String) { input.send(header.toByteArray()) }

        suspend fun serve() {
            for (text in commands) {
                writes.add(text)
                val frame = HeaderParser.parse(text.toByteArray())
                when (frame.tag) {
                    "RequestSync" -> reply("^<Sync defaultshootsetup='$active' shootsetup='${setups.values.joinToString(";") { it.serialize(ShootSetupDialect.DP67) }}' iso='50;100;200' light='Daylight;Tungsten' framing='Full,0,0,100,100' />")
                    "GetCameraParam" -> {
                        check(frame.value("GetParamList") == "CurrentTime") { "Unexpected old-back query: $text" }
                        reply("^<Ack CurrentTime='2004:01:01:00:00:00' />")
                    }
                    "FileCommand" -> reply("^<Ack subject='./' dir='' />")
                    "SetCameraParam" -> {
                        val csv = frame.value("ShootSetup")
                        if (csv != null) {
                            check(frame.attributes.keys == setOf("ShootSetup")) { "Modern global attributes must not reach this peer" }
                            val setup = ShootSetup.parse(csv)
                            check(setup.values.size == 12 && setup.name.contains(".1"))
                            if (!ignoreWrite) { setups[setup.name] = setup; active = setup.name }
                        } else {
                            check(frame.attributes.keys == setOf("DefaultShootSetup"))
                            val selected = frame.value("DefaultShootSetup")!!
                            check(selected in setups)
                            if (!ignoreWrite) active = selected
                        }
                        reply("^<CameraShootSetupChangeNotification />^<Ack error_code='0' />")
                    }
                    "Ack" -> Unit
                    else -> error("Unexpected old-back operation: $text")
                }
            }
        }
    }

    private fun session(initialModified: Boolean = false, block: suspend (LeafRepository, Peer) -> Unit) = runBlocking {
        withTimeout(20_000) {
            val repository = LeafRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            val previous = repository.settings.value.shootingCompatibility
            val peer = Peer(initialModified)
            val server = launch(Dispatchers.Default) { peer.serve() }
            try {
                withContext(Dispatchers.Main) {
                    repository.updateSettings { it.copy(shootingCompatibility = ShootingCompatibility.AUTO) }
                    repository.connect("00:00:00:00:00:67", "Leaf Valeo Wi", peer)
                }
                waitFor { repository.state.value.connected }
                assertEquals(ShootSetupDialect.DP67, repository.shootingDialect)
                block(repository, peer)
            } finally {
                withContext(Dispatchers.Main) {
                    repository.disconnect()
                    repository.updateSettings { it.copy(shootingCompatibility = previous) }
                }
                server.cancelAndJoin()
            }
        }
    }
    private suspend fun waitFor(predicate: () -> Boolean) { while (!predicate()) delay(20) }
    private suspend fun editIso(repository: LeafRepository) {
        val base = repository.shootingConfiguration().active!!
        withContext(Dispatchers.Main) { repository.applyShootSetup(base, mapOf(ShootField.ISO to "200")) }
    }

    @Test fun valeoAutoUsesTheModifiedDp67SlotAndPreservesTheFactoryPreset() = session { repo, peer ->
        editIso(repo)
        waitFor { repo.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
        assertEquals("200", repo.state.value.currentIso)
        assertEquals("Default.1", repo.shootingConfiguration().activeName)
        assertEquals(peer.factory, peer.setups.getValue("Default").serialize(ShootSetupDialect.DP67))
        val setter = peer.writes.single { it.startsWith("^<SetCameraParam") }
        assertFalse(setter.contains("FileCompression")); assertFalse(setter.contains("Orientation")); assertFalse(setter.contains("Color"))
        assertEquals(12, ShootSetup.parse(HeaderParser.parse(setter.toByteArray()).value("ShootSetup")!!).values.size)
        assertTrue(repo.state.value.connected); assertNull(repo.state.value.error)
    }

    @Test fun anExistingModifiedSlotIsUpdatedAndAnExistingPresetCanBeSelected() = session(true) { repo, peer ->
        editIso(repo)
        waitFor { repo.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
        assertEquals(setOf("Default", "Default.1"), peer.setups.keys)
        assertEquals("200", peer.setups.getValue("Default.1")[ShootField.ISO])
        withContext(Dispatchers.Main) { repo.clearMessage(); repo.selectShootSetup("Default") }
        waitFor { repo.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
        assertEquals("100", repo.state.value.currentIso)
        assertTrue(peer.writes.contains("^<SetCameraParam DefaultShootSetup=\"Default\" />"))
        assertTrue(repo.state.value.connected)
    }

    @Test fun aLostSettingIsNotConfirmedAndTheSessionCanStillReadStatus() = session { repo, peer ->
        peer.ignoreWrite = true
        editIso(repo)
        waitFor { repo.state.value.error?.resourceId == R.string.shooting_write_not_confirmed }
        assertEquals("100", repo.state.value.currentIso)
        assertNull(repo.state.value.notice); assertTrue(repo.state.value.connected)
        withContext(Dispatchers.Main) { repo.refreshControls() }
        waitFor { !repo.state.value.controlsBusy && repo.state.value.error == null }
        assertTrue(repo.state.value.connected)
        assertTrue(peer.writes.filter { it.startsWith("^<GetCameraParam") }.all { it == Commands.currentTime.text })
    }
}
