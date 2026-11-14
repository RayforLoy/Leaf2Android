package com.leaf2android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.leaf2android.data.ShootingCompatibility
import com.leaf2android.localization.L10n
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Exercises the actual control UI with an artificial wire peer, never Bluetooth camera hardware. */
class BackControlsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    /** Models the DP-67 modified slot, including fields inherited from its original preset. */
    private class Dp67Peer : LeafTransport {
        val factoryCsv = "SetupName=Default,ShootingFolder=./Shots/,ISO=100,ImageQuality=RAW,Framing=Full,Toning=Normal,Light=Daylight,SharpTable=Normal,InputProfile=Back,OutputProfile=RGB,CameraType=Technical,ICCFlag=1,Prefix=IMG,FileCompression=None,Orientation=Landscape,Color=color"
        val setter = CompletableDeferred<WireFrame>()
        private val input = Channel<ByteArray>(Channel.UNLIMITED)
        private val commands = Channel<String>(Channel.UNLIMITED)
        override suspend fun connect() = Unit
        override suspend fun read(buffer: ByteArray): Int {
            val bytes = input.receiveCatching().getOrNull() ?: return -1
            bytes.copyInto(buffer)
            return bytes.size
        }
        override suspend fun write(bytes: ByteArray) { commands.send(bytes.toString(Charsets.US_ASCII)) }
        override fun close() { input.close() }
        private suspend fun reply(header: String) { input.send(header.toByteArray(Charsets.US_ASCII)) }
        suspend fun serve() {
            val original = ShootSetup.parse(factoryCsv)
            val setups = linkedMapOf(original.name to original)
            var active = original.name
            for (command in commands) {
                val frame = HeaderParser.parse(command.toByteArray(Charsets.US_ASCII))
                when (frame.tag) {
                    "RequestSync" -> {
                        val csv = setups.values.joinToString(";") { setup ->
                            setup.values.entries.joinToString(",") { (key, value) -> "$key=$value" }
                        }
                        reply("^<Sync iso='50;100;200' defaultshootsetup='$active' shootsetup='$csv' defaultorientation='Landscape' defaultcolor='color' />")
                    }
                    "GetCameraParam" -> reply("^<Ack CurrentTime='2008:01:01:00:00:00' />")
                    "FileCommand" -> reply("^<Ack subject='${frame.value("Subject")}' dir='' />")
                    "SetCameraParam" -> {
                        val update = ShootSetup.parse(requireNotNull(frame.value("ShootSetup")))
                        // A modified slot inherits the original preset's fields that DP-67 does not write.
                        setups[update.name] = ShootSetup(original.values + update.values)
                        active = update.name
                        setter.complete(frame)
                        reply("^<Ack error_code='0' />")
                    }
                    "Ack" -> Unit
                    else -> error("Unexpected DP-67 UI fixture command: $command")
                }
            }
        }
    }
    @Test fun valeoAutomaticDialectEditsModifiedSlotAndPreservesItAcrossBothLanguages() {
        val app = rule.activity.application as LeafApplication
        val previousCompatibility = app.repository.settings.value.shootingCompatibility
        val peer = Dp67Peer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = scope.launch { peer.serve() }
        try {
            rule.runOnUiThread {
                app.repository.disconnect()
                app.repository.updateSettings { it.copy(shootingCompatibility = ShootingCompatibility.AUTO) }
                L10n.setLanguage("en")
            }
            rule.waitForIdle()
            rule.runOnUiThread { app.repository.connect("00:00:00:00:00:00", "Leaf Valeo Wi", peer) }
            rule.waitUntil(10_000) { app.repository.state.value.connected }
            assertEquals(ShootSetupDialect.DP67, app.repository.shootingDialect)
            rule.onNodeWithTag("page-BACK").performClick()
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasText(rule.activity.getString(R.string.dp67_active_hint)))
            rule.onNodeWithText(rule.activity.getString(R.string.dp67_active_hint)).assertIsDisplayed()
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("shooting-field-ISO"))
            rule.onNodeWithTag("shooting-field-ISO").performScrollTo().performClick()
            rule.onNodeWithText("200", useUnmergedTree = true).performClick()
            listOf("FileCompression", "Orientation", "Color").forEach { field ->
                rule.onNodeWithTag("shooting-field-$field").assertDoesNotExist()
            }
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("apply-shoot-settings"))
            rule.onNodeWithTag("apply-shoot-settings").performScrollTo().performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.dp67_modified_slot, "Default.1")).performScrollTo().assertIsDisplayed()
            assertFalse("Reviewing the modified slot must not send a setting", peer.setter.isCompleted)
            rule.onNodeWithText(rule.activity.getString(R.string.apply_and_read_back)).performClick()
            rule.waitUntil(10_000) { app.repository.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
            val written = runBlocking { peer.setter.await() }
            assertEquals(setOf("ShootSetup"), written.attributes.keys)
            assertEquals(listOf("SetupName", "ShootingFolder", "ISO", "ImageQuality", "Framing", "Toning", "Light", "SharpTable", "InputProfile", "OutputProfile", "CameraType", "ICCFlag", "Prefix"), ShootSetup.parse(written.value("ShootSetup")!!).values.keys.toList())
            fun assertPreservedState() {
                val state = app.repository.state.value
                val configuration = app.repository.shootingConfiguration()
                assertTrue(state.connected)
                assertNull(state.error)
                assertEquals("200", state.currentIso)
                assertEquals("Default.1", configuration.activeName)
                assertEquals("200", configuration.active!![ShootField.ISO])
                assertEquals(ShootSetup.parse(peer.factoryCsv), configuration.setups.single { it.name == "Default" })
            }
            assertPreservedState()
            for (language in listOf("zh", "en")) {
                rule.runOnUiThread { L10n.setLanguage(language) }
                rule.waitForIdle()
                rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("shooting-field-ISO"))
                rule.onNodeWithTag("shooting-field-ISO").performScrollTo().assert(hasText("200"))
                rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("select-shooting-setup"))
                rule.onNodeWithTag("select-shooting-setup").performScrollTo().assert(hasText("Default.1"))
                assertPreservedState()
                capture("valeo-dp67-$language")
            }
        } finally {
            rule.runOnUiThread {
                app.repository.disconnect()
                app.repository.clearMessage()
                app.repository.updateSettings { it.copy(shootingCompatibility = previousCompatibility) }
                L10n.setLanguage("")
            }
            server.cancel()
            scope.cancel()
        }
    }
    @Test fun isoSelectionAppliesTheCompleteSetupAndSurvivesLanguageChanges() {
        val app = rule.activity.application as LeafApplication
        val peer = BackControlsSessionTest.Peer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = scope.launch { peer.serve() }
        try {
            rule.runOnUiThread { app.repository.disconnect(); L10n.setLanguage("en") }
            rule.waitForIdle()
            rule.runOnUiThread { app.repository.connect("00:00:00:00:00:00", "Synthetic peer / no camera hardware", peer) }
            rule.waitUntil(10_000) { app.repository.state.value.connected }
            rule.onNodeWithTag("page-BACK").performClick()
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("shooting-field-ISO"))
            rule.onNodeWithTag("shooting-field-ISO").performScrollTo().performClick()
            rule.onNodeWithText("200", useUnmergedTree = true).performClick()
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("apply-shoot-settings"))
            rule.onNodeWithTag("apply-shoot-settings").performScrollTo().performClick()
            rule.onNodeWithText("Apply and verify").performClick()
            rule.waitUntil(10_000) { app.repository.state.value.notice?.resourceId == R.string.shooting_settings_confirmed }
            assertEquals("200", app.repository.state.value.currentIso)
            assertTrue(peer.writes.any { it.contains("ShootSetup=\"") && it.contains("ISO=200,") })
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("shooting-field-ISO"))
            rule.onNodeWithTag("shooting-field-ISO").performScrollTo().assertIsDisplayed()
            capture("back-controls-en")
            rule.runOnUiThread { L10n.setLanguage("zh") }
            rule.waitForIdle()
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("shooting-field-ISO"))
            rule.onNodeWithTag("shooting-field-ISO").performScrollTo().assertIsDisplayed()
            assertEquals("200", app.repository.state.value.currentIso)
            capture("back-controls-zh")
        } finally {
            rule.runOnUiThread { app.repository.disconnect(); app.repository.clearMessage(); L10n.setLanguage("") }
            server.cancel(); scope.cancel()
        }
    }
    @Test fun laboratoryRequiresAnExplicitSendAndLabelsTheReplyAsUnverified() {
        val app = rule.activity.application as LeafApplication
        val peer = BackControlsSessionTest.Peer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = scope.launch { peer.serve() }
        try {
            rule.runOnUiThread { app.repository.disconnect(); L10n.setLanguage("en") }
            rule.waitForIdle()
            rule.runOnUiThread { app.repository.connect("00:00:00:00:00:00", "Synthetic laboratory UI / no camera hardware", peer) }
            rule.waitUntil(10_000) { app.repository.state.value.connected }
            rule.onNodeWithTag("page-BACK").performClick()
            rule.onNodeWithTag("back-controls-list").performScrollToNode(hasTestTag("open-laboratory"))
            rule.onNodeWithTag("open-laboratory").performClick()
            assertTrue(app.repository.state.value.labRecords.isEmpty())
            rule.onNodeWithTag("lab-raw-command").performScrollTo().performTextInput("^<GetCameraParam GetParamList='Mystery' />")
            rule.onNodeWithTag("lab-review-send").performScrollTo().performClick()
            assertTrue(app.repository.state.value.labRecords.isEmpty())
            rule.onNodeWithTag("lab-confirm-send").performClick()
            rule.waitUntil(10_000) { app.repository.state.value.labRecords.isNotEmpty() && !app.repository.state.value.labBusy }
            rule.onNodeWithText("Frame received; effect unverified", substring = true).performScrollTo().assertIsDisplayed()
            capture("laboratory-en")
            rule.onNodeWithContentDescription("Finish and return").performClick()
            assertFalse(app.repository.state.value.connected)
        } finally {
            rule.runOnUiThread { app.repository.disconnect(); app.repository.clearMessage(); L10n.setLanguage("") }
            server.cancel(); scope.cancel()
        }
    }
    private fun capture(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/Download/Leaf2Android-$name.png")).use { it.readBytes() }
    }
}
