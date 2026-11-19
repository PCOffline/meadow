package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.circle.PeopleContent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the people screen opens: inviting a Bumblebee by code or link, my own friend code, and
 * asking for more time. On the default screen: with a tall phone's qualifiers, Robolectric never
 * settles with these open.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PeopleDialogsTest : ScreenTest() {
    private val robin = DemoGarden.robin

    /** Taps the button named [id], scrolling the list to it first: the default screen is short. */
    private fun tap(id: Int) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(text(id)))
        compose.onNodeWithContentDescription(text(id)).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun failed() = compose.waitUntil(5_000) { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `an invite needs a whole code, and a link to share is made for it`() {
        show { PeopleContent(robin, DemoGarden.people) }
        tap(R.string.people_invite_more)
        node(R.string.invite_code_button).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("ab")
        node(R.string.invite_code_button).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("ab-cd 12")
        node(R.string.invite_code_button).assertIsEnabled()
        // These tests have no backend, so sending the invite fails, and the sheet says so.
        node(R.string.invite_code_button).performSemanticsAction(SemanticsActions.OnClick)
        failed()
    }

    @Test
    fun `a pasted invite link is kept to open, without a code`() {
        show { PeopleContent(robin, DemoGarden.people) }
        tap(R.string.people_invite_more)
        node(R.string.invite_link_button).performSemanticsAction(SemanticsActions.OnClick)
        failed()
        compose.onNode(hasSetTextAction()).performTextInput("https://meadow.example/i/abcdefghijklmnop")
        node(R.string.invite_code_button).assertIsEnabled()
    }

    @Test
    fun `my friend code is there to copy and share`() {
        show { PeopleContent(robin, DemoGarden.people) }
        tap(R.string.circle_join_title)
        compose.onNode(hasText(robin.friendCode)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.join_code_copy)).performSemanticsAction(SemanticsActions.OnClick)
        val clip = app.getSystemService(android.content.ClipboardManager::class.java).primaryClip
        assertEquals(robin.friendCode, clip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `more time is asked for from the people screen`() {
        AppGraph.store.setLimits(listOf(dev.pcoffline.meadow.data.AppLimit("com.example.pebbles", "Pebbles", 30, 0)))
        show { PeopleContent(robin, DemoGarden.people) }
        compose.onNodeWithContentDescription(text(R.string.requests_ask_button)).performSemanticsAction(SemanticsActions.OnClick)
        node(R.string.ask_title).assertExists()
        node(R.string.action_cancel).performSemanticsAction(SemanticsActions.OnClick)
        node(R.string.ask_title).assertDoesNotExist()
    }
}
