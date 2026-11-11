package com.leaf2android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.leaf2android.localization.L10n
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Exercises the actual control UI with an artificial wire peer, never Bluetooth camera hardware. */
class BackControlsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
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
