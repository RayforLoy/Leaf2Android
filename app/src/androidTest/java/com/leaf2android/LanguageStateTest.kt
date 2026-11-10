package com.leaf2android

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.leaf2android.data.*
import com.leaf2android.localization.*
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** The connection peer is artificial; these tests do not validate a real back or Bluetooth radio. */
class LanguageStateTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as LeafApplication

    @Before fun prepareLanguage() {
        rule.runOnUiThread { app.repository.disconnect(); L10n.setLanguage("en") }
        rule.waitForIdle()
    }
    @After fun restoreSystem() {
        rule.runOnUiThread { app.repository.disconnect(); app.repository.clearMessage(); L10n.setLanguage("") }
        rule.waitForIdle()
    }

    @Test fun englishAndChineseResourcesCoverTheSettingsAndRetainedErrors() {
        rule.onNodeWithTag("page-SETTINGS").performClick()
        rule.onNodeWithText("Interface language").assertIsDisplayed()
        val protocolError = try {
            HeaderParser.parse("broken".toByteArray()); error("Malformed frame was accepted")
        } catch (e: ProtocolException) { e }
        rule.runOnUiThread { app.repository.showError(UiText.protocol(protocolError)) }
        rule.onNodeWithTag("language-zh").performScrollTo().performClick()
        rule.waitUntil(10_000) { L10n.languageTag().startsWith("zh") }
        rule.waitForIdle()
        rule.onNodeWithText("界面语言").assertIsDisplayed()
        rule.onNodeWithText(protocolError.messageForLanguage(true)).assertIsDisplayed()
        rule.onNodeWithTag("style-pda").performScrollTo().performClick()
        rule.onNodeWithTag("language-en").performScrollTo().performClick()
        rule.waitUntil(10_000) { L10n.languageTag().startsWith("en") }
        rule.waitForIdle()
        rule.onNodeWithText("Interface language").assertIsDisplayed()
        rule.onNodeWithText(protocolError.messageForLanguage(false)).assertIsDisplayed()
        assertEquals(UiStyle.PDA, app.repository.settings.value.style)
    }

    @Test fun followSystemPersistsAcrossActivityRecreation() {
        rule.onNodeWithTag("page-SETTINGS").performClick()
        // This also covers English -> system on an English system, where no Activity recreation is needed.
        rule.onNodeWithTag("language-system").performScrollTo().performClick()
        rule.waitUntil(10_000) { L10n.languageTag().isEmpty() }
        rule.onNodeWithTag("language-system").assertIsSelected()
        rule.onNodeWithTag("language-zh").performScrollTo().performClick()
        rule.waitUntil(10_000) { L10n.languageTag().startsWith("zh") }
        rule.onNodeWithTag("language-system").performScrollTo().performClick()
        rule.waitUntil(10_000) { L10n.languageTag().isEmpty() }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertEquals("", L10n.languageTag())
        val heading = if (Resources.getSystem().configuration.locales[0].language == "zh") "界面语言" else "Interface language"
        rule.onNodeWithText(heading).assertIsDisplayed()
        rule.onNodeWithTag("language-system").assertIsSelected()
    }

    @Test fun languageChangesKeepAnActiveSyntheticSessionPhotoNotesAndZoom() = runBlocking {
        val peer = SyntheticPeer()
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val jpeg = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(Color.rgb(90, 140, 70))
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it); bitmap.recycle() }.toByteArray()
        }
        val server = serverScope.launch {
            for (command in peer.commands) when {
                command == Commands.sync.text -> peer.reply("^<Sync cameratype='Synthetic fixture' />")
                command == Commands.currentTime.text -> peer.reply("^<Ack currenttime='2008-01-01' />")
                command == Commands.directory().text -> peer.reply("^<Ack subject='./' dir='' />")
                command.startsWith("^<GetImage") -> {
                    peer.reply("^<Ack file_name='./language-fixture.mos' attachment='${jpeg.size}' />")
                    peer.input.send(jpeg)
                }
                command == "^<Ack />" -> Unit
                else -> error("Unexpected command: $command")
            }
        }
        try {
            rule.runOnUiThread {
                app.repository.updateSettings { it.copy(style = UiStyle.MODERN) }
                app.repository.connect("00:00:00:00:00:00", "Synthetic language-test peer", peer)
            }
            rule.waitUntil(10_000) { app.repository.state.value.connected }
            peer.reply("^<PictureTaken file_name='./language-fixture.mos' imageSaved='yes' />")
            rule.waitUntil(10_000) { app.repository.state.value.selectedPath == "./language-fixture.mos" && app.repository.state.value.previewCount == 1 }
            rule.onNodeWithTag("page-SHOOT").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("photo-image").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription(tr(R.string.fit_to_screen_407f7)).performClick()
            rule.onNodeWithTag("photo-canvas").performTouchInput { doubleClick() }
            rule.waitUntil(5_000) { app.repository.viewport("./language-fixture.mos").zoom > 1f }
            val viewport = app.repository.viewport("./language-fixture.mos")
            rule.runOnUiThread {
                app.repository.localNote("./language-fixture.mos", "User note 用户备注")
                if (app.repository.state.value.selected?.starred != true) app.repository.star("./language-fixture.mos")
            }
            rule.onNodeWithTag("page-SETTINGS").performClick()
            rule.onNodeWithTag("language-zh").performScrollTo().performClick()
            rule.waitUntil(10_000) { L10n.languageTag().startsWith("zh") }
            rule.waitForIdle()
            assertTrue(app.repository.state.value.connected)
            assertEquals("./language-fixture.mos", app.repository.state.value.selectedPath)
            assertEquals(viewport, app.repository.viewport("./language-fixture.mos"))
            assertEquals("User note 用户备注", app.repository.state.value.selected?.localNote)
            assertTrue(app.repository.state.value.selected!!.starred)
            rule.activityRule.scenario.recreate()
            rule.waitForIdle()
            assertTrue(app.repository.state.value.connected)
            rule.onNodeWithTag("language-en").performScrollTo().performClick()
            rule.waitUntil(10_000) { L10n.languageTag().startsWith("en") }
            rule.waitForIdle()
            assertTrue(app.repository.state.value.connected)
            assertEquals("Session ready", app.repository.state.value.phase.label)
            assertEquals(viewport, app.repository.viewport("./language-fixture.mos"))
        } finally {
            rule.runOnUiThread { app.repository.disconnect() }
            server.cancelAndJoin(); serverScope.cancel()
        }
    }

    private class SyntheticPeer : LeafTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val commands = Channel<String>(Channel.UNLIMITED)
        override suspend fun connect() = Unit
        override suspend fun read(buffer: ByteArray): Int {
            val bytes = input.receiveCatching().getOrNull() ?: return -1
            bytes.copyInto(buffer); return bytes.size
        }
        override suspend fun write(bytes: ByteArray) { commands.send(bytes.toString(Charsets.US_ASCII)) }
        override fun close() { input.close() }
        suspend fun reply(text: String) { input.send(text.toByteArray()) }
    }
}
