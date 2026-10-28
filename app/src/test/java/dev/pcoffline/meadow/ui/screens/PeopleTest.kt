package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Chat
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.LocalSnackbarClearance
import dev.pcoffline.meadow.ui.SnackbarClearance
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.People
import dev.pcoffline.meadow.ui.circle.PeopleContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** People: everyone in the circle, what's waiting on them, and each one's chat. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PeopleTest : ScreenTest() {
    private val robin = DemoGarden.robin

    private fun chatWithMaya(read: Boolean): Chat {
        val now = System.currentTimeMillis()
        return DemoGarden.chat.copy(
            lastMessage = "How was the walk?", lastMessageAtMillis = now - 1_000, lastSenderId = DemoGarden.maya.uid,
            lastReadMillis = if (read) mapOf(robin.uid to now) else emptyMap(),
        )
    }

    @Test
    fun `everyone opens their chat, showing their bio and not the latest message`() {
        val opened = mutableListOf<String>()
        show { PeopleContent(robin, DemoGarden.people, onOpenChat = { opened += it }) }
        compose.onNodeWithText(DemoGarden.maya.bio).performScrollTo().assertExists()
        compose.onNodeWithText(DemoGarden.theo.bio).performScrollTo().assertExists()
        compose.onNodeWithText(DemoGarden.chat.lastMessage).assertDoesNotExist()
        compose.onNodeWithText(DemoGarden.chatWithAna.lastMessage).assertDoesNotExist()
        compose.onNodeWithText("Maya").performScrollTo().performClick()
        compose.onNodeWithText(DemoGarden.ana.bio).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("maya_robin", "ana_robin"), opened)
        assertAccessible()
    }

    @Test
    fun `someone with no bio shows what they are to you`() {
        show {
            PeopleContent(
                robin,
                People(bumblebees = listOf(DemoGarden.maya.copy(bio = "")), lookingAfter = listOf(DemoGarden.ana.copy(bio = "")), chats = listOf(chatWithMaya(read = false))),
            )
        }
        node(R.string.circle_bee_role).performScrollTo().assertExists()
        node(R.string.circle_garden_role).performScrollTo().assertExists()
        compose.onNodeWithText("How was the walk?").assertDoesNotExist()
    }

    @Test
    fun `what's waiting for you comes first, then your own asks`() {
        show { PeopleContent(robin, DemoGarden.people) }
        val waiting = compose.onNodeWithText(text(R.string.people_waiting)).getBoundsInRoot()
        val asks = compose.onNodeWithText(text(R.string.people_your_asks)).performScrollTo().getBoundsInRoot()
        val bees = compose.onNodeWithText(text(R.string.circle_your_bees)).performScrollTo().getBoundsInRoot()
        assertTrue(waiting.top < asks.top && asks.top < bees.top)
        // Ana's question can be answered right here.
        compose.onNodeWithText(text(R.string.request_say_yes)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(text(R.string.request_withdraw)).performScrollTo().assertExists()
    }

    @Test
    fun `nothing waiting, no section for it`() {
        show { PeopleContent(robin, DemoGarden.people.copy(incomingRequests = emptyList(), outgoingRequests = emptyList())) }
        node(R.string.people_waiting).assertDoesNotExist()
        node(R.string.people_your_asks).assertDoesNotExist()
    }

    @Test
    fun `an unread chat has a mark that opens it`() {
        val opened = mutableListOf<String>()
        show { PeopleContent(robin, People(bumblebees = listOf(DemoGarden.maya), chats = listOf(chatWithMaya(read = false))), onOpenChat = { opened += it }) }
        compose.onNodeWithContentDescription(text(R.string.chats_unread)).performScrollTo().performClick()
        assertEquals(listOf("maya_robin"), opened)
    }

    @Test
    fun `a read chat has no unread mark`() {
        show { PeopleContent(robin, People(bumblebees = listOf(DemoGarden.maya), chats = listOf(chatWithMaya(read = true)))) }
        compose.onNodeWithContentDescription(text(R.string.chats_unread)).assertDoesNotExist()
    }

    @Test
    fun `a Bumblebee on the way out says when, with a way to keep them`() {
        val leaving = robin.copy(pendingRemovals = mapOf(DemoGarden.maya.uid to System.currentTimeMillis() + 3_600_000))
        show { PeopleContent(leaving, People(bumblebees = listOf(DemoGarden.maya, DemoGarden.theo))) }
        compose.onNodeWithText("Leaving in", substring = true).performScrollTo().assertExists()
        node(R.string.circle_bee_keep).performScrollTo().assertExists()
        assertAccessible()
    }

    @Test
    fun `gardens you look after lead to your answers as a Bumblebee`() {
        var stats = 0
        show { PeopleContent(robin, People(lookingAfter = listOf(DemoGarden.ana)), onOpenBeeStats = { stats++ }) }
        node(R.string.bee_stats_open).performScrollTo().performClick()
        assertEquals(1, stats)
    }

    @Test
    fun `an offer to be your Bumblebee says so, and offers you've made wait among gardens`() {
        val offerToRobin = DemoGarden.inviteToRobin.copy(offer = true)
        val offerFromRobin = DemoGarden.inviteFromRobin.copy(offer = true)
        show { PeopleContent(robin, People(incomingInvites = listOf(offerToRobin), outgoingInvites = listOf(offerFromRobin))) }
        node(R.string.circle_offer_title, "Ana").assertExists()
        node(R.string.people_waiting).assertExists()
        node(R.string.circle_looking_after).performScrollTo().assertExists()
        compose.onNodeWithText("Offered", substring = true).performScrollTo().assertExists()
        assertAccessible()
    }

    @Test
    fun `with nobody yet, inviting comes first and there are no empty sections`() {
        show { PeopleContent(robin.copy(approverIds = emptyList(), approveeIds = emptyList()), People()) }
        node(R.string.invite_title).assertExists()
        node(R.string.invite_code_title).assertExists()
        node(R.string.circle_your_bees).assertDoesNotExist()
        node(R.string.circle_looking_after).assertDoesNotExist()
        node(R.string.people_invite_more).assertDoesNotExist()
        // No Bumblebees to ask.
        compose.onNodeWithContentDescription(text(R.string.requests_ask_button)).assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `a friend code or a pasted invite link can be entered, and nothing else`() {
        show { PeopleContent(robin, People()) }
        val field = hasSetTextAction() and hasText(text(R.string.invite_code_placeholder))
        val invite = compose.onNode(hasText(text(R.string.invite_code_button)) and hasClickAction())
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(field)
        invite.assertIsNotEnabled()
        compose.onNode(field).performTextInput("see you at the park")
        invite.assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("ab12-cd34")
        invite.assertIsEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Be my Bumblebee? https://meadow.example/i/AbCdEfGh12345678")
        invite.assertIsEnabled()
    }

    @Test
    fun `snackbars sit above the ask button while it's there`() {
        val clearance = SnackbarClearance()
        var here by mutableStateOf(true)
        show {
            CompositionLocalProvider(LocalSnackbarClearance provides clearance) {
                if (here) PeopleContent(robin, People())
            }
        }
        val button = compose.onNodeWithContentDescription(text(R.string.requests_ask_button)).getBoundsInRoot()
        // The button's height and the gap under it: the snackbar's own margin then keeps them apart.
        assertEquals((button.height + 20.dp).value, clearance.height.value, 0.5f)

        here = false
        compose.waitForIdle()
        assertEquals(0f, clearance.height.value)
    }

    @Test
    fun `a chat with someone whose garden you look after leads to it`() {
        val gardens = mutableListOf<String>()
        show { ChatContent(robin, DemoGarden.chatWithAna, emptyList(), onOpenGarden = { gardens += it }) }
        compose.onNodeWithContentDescription(text(R.string.chat_see_garden, "Ana")).performClick()
        assertEquals(listOf("ana"), gardens)
        // Ana isn't Robin's Bumblebee: nothing to remove.
        compose.onNodeWithContentDescription(text(R.string.action_more)).assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `a chat with a Bumblebee can remove them`() {
        show { ChatContent(robin, DemoGarden.chat, DemoGarden.messages, requestCard = { _, _ -> }) }
        compose.onNodeWithContentDescription(text(R.string.chat_see_garden, "Maya")).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        node(R.string.chat_remove_bee).assertExists()
        node(R.string.chat_keep_bee).assertDoesNotExist()
    }

    @Test
    fun `a chat with a Bumblebee who's leaving can keep them`() {
        val leaving = robin.copy(pendingRemovals = mapOf(DemoGarden.maya.uid to System.currentTimeMillis() + 3_600_000))
        show { ChatContent(leaving, DemoGarden.chat, DemoGarden.messages, requestCard = { _, _ -> }) }
        compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        node(R.string.chat_keep_bee).assertExists()
        node(R.string.chat_remove_bee).assertDoesNotExist()
    }

    // Answering, asking and removing are checked on a phone (DecidingJourneyTest, AskJourneyTest,
    // CircleJourneyTest): Robolectric never settles with a dialog open.
}
