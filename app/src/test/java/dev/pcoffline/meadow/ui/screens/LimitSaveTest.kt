package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.Connectivity
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Saving a limit: what the editor does once it's saved, planted or asked about. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1600dp-xxhdpi")
class LimitSaveTest : ScreenTest() {
    private val pkg = "com.instagram.android"

    private fun editorSaving(state: LimitEditorState, outcome: ChangeOutcome, onDone: () -> Unit) = show {
        Column {
            BackTopBar(text(R.string.limit_editor_existing), onBack = {})
            LimitEditor(pkg, state.copy(appName = "Instagram", loaded = true), onDone = onDone, save = { _, _, _ -> outcome })
        }
    }

    /** Types [keys] for the daily limit, on the keypad, and saves it. */
    private fun typeDaily(from: String, vararg keys: String) {
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} $from").performClick()
        keys.forEach { compose.onNode(hasText(it)).performClick() }
        node(R.string.action_ok).performClick()
    }

    @Test
    fun `a saved change goes back, and its note offers to share the limit as saved`() {
        var done = 0
        compose.setContent {
            dev.pcoffline.meadow.ui.theme.MeadowTheme {
                dev.pcoffline.meadow.ui.SnackbarScope {
                    LimitEditor(
                        pkg, LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), appName = "Instagram", loaded = true),
                        onDone = { done++ }, save = { _, _, _ -> ChangeOutcome.APPLIED },
                    )
                }
            }
        }
        typeDaily("30m", "2", "5")
        node(R.string.action_save).performScrollTo().performClick()
        compose.waitUntil(5_000) { done == 1 }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(R.string.share_title))).fetchSemanticsNodes().isNotEmpty() }
        node(R.string.limit_editor_updated).assertExists()
        node(R.string.share_title).performClick()
        val shared = dev.pcoffline.meadow.ui.components.ShareCommitment.shared.value
        assertTrue(shared?.text.orEmpty().contains("Instagram"))
        assertEquals(25, shared?.limit?.dailyMinutes)
        dev.pcoffline.meadow.ui.components.ShareCommitment.close()
    }

    @Test
    fun `a new limit goes back to the garden once planted`() {
        var done = 0
        editorSaving(LimitEditorState(), ChangeOutcome.APPLIED) { done++ }
        node(R.string.limit_editor_plant).performScrollTo().performClick()
        compose.waitUntil(5_000) { done == 1 }
    }

    @Test
    fun `a question for a Bumblebee closes the editor`() {
        // Asking needs a connection.
        Connectivity.set(true)
        var done = 0
        editorSaving(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1, pendingKnown = true), ChangeOutcome.REQUESTED) { done++ }
        typeDaily("30m", "4", "5")
        node(R.string.limit_editor_ask).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { done == 1 }
    }
}
