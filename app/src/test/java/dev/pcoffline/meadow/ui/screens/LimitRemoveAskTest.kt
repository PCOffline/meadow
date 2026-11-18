package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * With Bumblebees, removing a limit asks them, with a note. On the default screen: with a tall
 * phone's qualifiers, Robolectric never settles with this dialog open.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LimitRemoveAskTest : ScreenTest() {
    private val limit = AppLimit("com.instagram.android", "Instagram", dailyMinutes = 30, weeklyMinutes = 0)

    private fun inDialog(id: Int) = compose.onNode(hasText(text(id)) and hasAnyAncestor(isDialog()))

    @Test
    fun `with Bumblebees, removing asks them with a note, and a failure keeps the limit`() {
        var done = 0
        show {
            Column {
                BackTopBar(text(R.string.limit_editor_existing), onBack = {})
                LimitEditor(limit.packageName, LimitEditorState(existing = limit, appName = "Instagram", bumblebeeCount = 1, loaded = true), onDone = { done++ })
            }
        }
        node(R.string.limit_editor_ask_remove).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        inDialog(R.string.limit_remove_body_ask).assertExists()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("I don't use it any more")
        // These tests have no backend, so asking fails, and the editor says so.
        inDialog(R.string.limit_remove_ask).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, done)
    }
}
