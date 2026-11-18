package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.enforcement.RestState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * From the rest screen: asking for more time, and an emergency unlock. On the default screen:
 * with a tall phone's qualifiers, Robolectric never settles with these dialogs open.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class RestDialogsTest : ScreenTest() {
    private val resting = RestState(
        appName = "TikTok",
        budget = AppBudget(AppLimit("demo.tiktok", "TikTok", 30, 0), 30 * 60_000L, 30 * 60_000L, 0, 0),
        signedIn = true,
        hasBumblebees = true,
    )

    private fun inDialog(id: Int) = compose.onNode(hasText(text(id)) and hasAnyAncestor(isDialog()))

    @Test
    fun `more time is asked for in a dialog that closes again`() {
        show { RestContent("demo.tiktok", resting) }
        node(R.string.rest_ask).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(R.string.ask_title).assertExists()
        inDialog(R.string.action_cancel).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(R.string.ask_title).assertDoesNotExist()
    }

    @Test
    fun `an emergency unlock opens for this app, and closes again`() {
        show { RestContent("demo.tiktok", resting) }
        node(R.string.rest_emergency).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(R.string.emergency_dialog_title).assertExists()
        compose.onNode(hasText(text(R.string.emergency_dialog_just_app, "TikTok")) and hasAnyAncestor(isDialog())).assertExists()
        inDialog(R.string.action_cancel).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(R.string.emergency_dialog_title).assertDoesNotExist()
    }
}
