package com.leaf2android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.leaf2android.data.UiStyle
import com.leaf2android.data.Viewport
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import androidx.core.view.WindowCompat
import com.leaf2android.localization.L10n
import com.leaf2android.localization.tr
import org.junit.Before
import org.junit.After

class UiStateTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun useEnglish() {
        rule.runOnUiThread { L10n.setLanguage("en") }
        rule.waitForIdle()
    }
    @After fun followSystemAgain() {
        rule.runOnUiThread { L10n.setLanguage("") }
        rule.waitForIdle()
    }

    @Test fun demoStyleChangeAndActivityRecreationKeepSelectionAndViewport() {
        val app = rule.activity.application as LeafApplication
        rule.runOnUiThread { app.repository.updateSettings { it.copy(style = UiStyle.MODERN, showHistogram = false) }; app.repository.loadDemo() }
        rule.waitUntil(10_000) {
            val selected = app.repository.state.value.selected
            selected?.isDemo == true && selected.path == "demo/offline-preview.jpg" && selected.cacheFile != null
        }
        val path = app.repository.state.value.selectedPath!!
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("photo-image").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(tr(R.string.fit_to_screen_407f7)).performClick()
        rule.onNodeWithTag("photo-canvas").performTouchInput { doubleClick() }
        rule.waitUntil(5_000) { app.repository.viewport(path).zoom > 1f }
        val viewport = app.repository.viewport(path)
        rule.onNodeWithTag("page-SETTINGS").performClick()
        rule.onNodeWithTag("style-pda").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(UiStyle.PDA, app.repository.settings.value.style)
        rule.runOnUiThread { assertTrue(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars) }
        assertEquals(path, app.repository.state.value.selectedPath)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertEquals(viewport, app.repository.viewport(path))
        rule.onNodeWithTag("page-SHOOT").performClick()
        rule.onNodeWithTag("photo-canvas").assertIsDisplayed()
        rule.onNodeWithContentDescription(tr(R.string.jpeg_histogram)).performClick()
        rule.onNodeWithText(tr(R.string.histogram_source_label)).assertIsDisplayed()
        rule.onNodeWithTag("page-SETTINGS").performClick()
        rule.onNodeWithTag("style-modern").performScrollTo().performClick()
        rule.waitForIdle()
        rule.runOnUiThread { assertFalse(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars) }
    }

    @Test fun offlineDemoNeverClaimsBluetoothConnection() {
        val app = rule.activity.application as LeafApplication
        rule.runOnUiThread { app.repository.disconnect(); app.repository.loadDemo() }
        rule.waitUntil(10_000) {
            val selected = app.repository.state.value.selected
            selected?.isDemo == true && selected.path == "demo/offline-preview.jpg" && selected.cacheFile != null
        }
        rule.onNodeWithTag("page-SHOOT").performClick()
        rule.onNodeWithText(tr(R.string.phase_disconnected)).assertIsDisplayed()
        assertFalse(app.repository.state.value.connected)
        assertTrue(app.repository.state.value.selected!!.isDemo)
    }
}
