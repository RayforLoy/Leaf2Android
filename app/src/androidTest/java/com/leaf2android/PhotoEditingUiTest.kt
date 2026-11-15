package com.leaf2android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import com.leaf2android.data.ShootingCompatibility
import com.leaf2android.data.UiStyle
import com.leaf2android.data.Viewport
import com.leaf2android.localization.L10n
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic RFCOMM frames only; no camera, pairing, or physical Bluetooth writes. */
class PhotoEditingUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun withCurrentPhoto(block: (LeafApplication, BackControlsSessionTest.Peer) -> Unit) {
        val app = rule.activity.application as LeafApplication
        val previous = app.repository.settings.value
        val previousViewport = app.repository.viewport("./a.mos")
        val peer = BackControlsSessionTest.Peer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = scope.launch { peer.serve() }
        try {
            rule.runOnUiThread {
                app.repository.disconnect()
                app.repository.updateSettings { it.copy(style = UiStyle.MODERN, shootingCompatibility = ShootingCompatibility.WIVIEW, showHistogram = false) }
                L10n.setLanguage("en")
            }
            rule.waitForIdle()
            rule.runOnUiThread { app.repository.connect("00:00:00:00:00:00", "Photo editing synthetic peer", peer) }
            rule.waitUntil(10_000) { app.repository.state.value.connected }
            rule.runOnUiThread { app.repository.setViewport("./a.mos", Viewport()); app.repository.select("./a.mos") }
            rule.waitUntil(10_000) {
                app.repository.state.value.selected?.let { it.path == "./a.mos" && it.cacheFile != null && it.connectionGeneration == app.repository.state.value.connectionGeneration } == true
            }
            rule.onNodeWithTag("page-SHOOT").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("photo-image").fetchSemanticsNodes().isNotEmpty() }
            block(app, peer)
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

    @Test fun deleteShowsTheRealBackPathAndCancelDoesNotWriteThenVerifiedDeleteRemovesThePhoto() = withCurrentPhoto { app, peer ->
        rule.onNodeWithTag("photo-open-actions").performClick()
        capture("photo-actions-en")
        rule.onNodeWithTag("photo-action-delete").performScrollTo().performClick()
        rule.onNodeWithTag("photo-action-source").assertTextEquals("./a.mos")
        assertTrue(rule.onAllNodesWithText("Photo editing synthetic peer", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertFalse(peer.writes.any { it.contains("FileCommand=\"del\"") })
        capture("photo-delete-confirm-en")
        rule.onNodeWithTag("cancel-photo-action").performClick()
        assertTrue(peer.rootEntries.contains("F:a.mos"))
        assertTrue(app.repository.state.value.photos.any { it.path == "./a.mos" })
        rule.onNodeWithTag("photo-action-delete").performScrollTo().performClick()
        rule.onNodeWithTag("confirm-photo-delete").performClick()
        rule.waitUntil(10_000) { app.repository.state.value.photos.none { it.path == "./a.mos" } }
        assertFalse(peer.rootEntries.contains("F:a.mos"))
        assertNotEquals("./a.mos", app.repository.state.value.selectedPath)
        assertTrue(app.repository.state.value.connected)
    }

    @Test fun grayPickingIsExplicitAndMissingOriginalGeometryNeverSendsAPoint() = withCurrentPhoto { app, peer ->
        rule.onNodeWithTag("photo-canvas").performTouchInput { click() }
        rule.onNodeWithTag("gray-point-marker").assertDoesNotExist()
        rule.onNodeWithTag("photo-open-image-tools").performClick()
        rule.onNodeWithTag("start-gray-pick").performScrollTo().performClick()
        rule.onNodeWithTag("gray-pick-mode").assertIsDisplayed()
        rule.waitUntil(5_000) { peer.writes.count { it.startsWith("^<GetPictureParams") } == 1 }
        rule.onNodeWithText(rule.activity.getString(R.string.gray_pick_unavailable)).assertIsDisplayed()
        rule.onNodeWithTag("photo-canvas").performTouchInput { click() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("gray-point-marker").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(peer.writes.any { it.startsWith("^<GetImage") && it.contains("light=") })
        rule.onNodeWithTag("photo-canvas").performTouchInput { doubleClick() }
        rule.waitUntil(5_000) { app.repository.viewport("./a.mos").zoom > 1f }
        rule.onNodeWithTag("gray-pick-mode").assertIsDisplayed()
        assertFalse(peer.writes.any { it.startsWith("^<GetImage") && it.contains("light=") })
        val beforePinch = app.repository.viewport("./a.mos").zoom
        rule.onNodeWithTag("photo-canvas").performTouchInput {
            down(0, center - Offset(24f, 0f))
            down(1, center + Offset(24f, 0f))
            moveTo(0, center - Offset(72f, 0f))
            moveTo(1, center + Offset(72f, 0f))
            up(0)
            up(1)
        }
        rule.waitUntil(5_000) { app.repository.viewport("./a.mos").zoom > beforePinch }
        assertFalse(peer.writes.any { it.startsWith("^<GetImage") && it.contains("light=") })
        rule.runOnUiThread { L10n.setLanguage("zh") }
        rule.waitForIdle()
        rule.onNodeWithTag("gray-pick-mode").assertIsDisplayed()
        rule.onNodeWithTag("gray-point-marker").assertExists()
        assertEquals("A locale change must not request metadata again", 1, peer.writes.count { it.startsWith("^<GetPictureParams") })
        rule.onNodeWithTag("gray-pick-read-metadata").performClick()
        rule.waitUntil(5_000) { peer.writes.count { it.startsWith("^<GetPictureParams") } == 2 }
        assertFalse(peer.writes.any { it.startsWith("^<GetImage") && it.contains("light=") })
        rule.onNodeWithTag("cancel-gray-pick").performClick()
        rule.onNodeWithTag("gray-pick-mode").assertDoesNotExist()
        rule.onNodeWithTag("gray-point-marker").assertDoesNotExist()
        assertTrue(app.repository.state.value.connected)
    }

    @Test fun copyUsesAnExplicitBackFolderAndKeepsTheOriginalPhoto() = withCurrentPhoto { app, peer ->
        rule.runOnUiThread { L10n.setLanguage("zh") }
        rule.waitForIdle()
        rule.onNodeWithTag("photo-open-actions").performClick()
        capture("photo-actions-zh")
        rule.onNodeWithTag("photo-action-copy").performScrollTo().performClick()
        rule.onNodeWithTag("photo-action-source").assertTextEquals("./a.mos")
        rule.onNodeWithTag("photo-action-target").performScrollTo().performTextInput("./Target/")
        rule.onNodeWithTag("confirm-photo-action").performClick()
        rule.waitUntil(10_000) { app.repository.state.value.notice?.resourceId == R.string.back_file_action_acknowledged }
        assertTrue(peer.rootEntries.contains("F:a.mos"))
        assertTrue(peer.targetEntries.contains("F:a.mos"))
        assertTrue(peer.writes.any { it.contains("FileCommand=\"copy\"") && it.contains("Subject=\"./a.mos\"") && it.contains("Target=\"./Target/a.mos\"") })
        assertTrue(app.repository.state.value.connected)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/Download/Leaf2Android-$name.png")).use { it.readBytes() }
    }
}
