package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Chat
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.UserProfile
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

    private fun chatWithMaya(read: Boolean): Chat = chatWith(DemoGarden.maya, read)

    /** Robin's chat with [friend], whose latest message Robin has [read] or not. */
    private fun chatWith(friend: UserProfile, read: Boolean): Chat {
        val now = System.currentTimeMillis()
        return DemoGarden.chat.copy(
            id = ChatRepository.chatIdFor(robin.uid, friend.uid), participants = listOf(friend.uid, robin.uid),
            lastMessageAtMillis = now - 1_000, lastSenderId = friend.uid,
            lastReadMillis = if (read) mapOf(robin.uid to now) else emptyMap(),
        )
    }

    @Test
    fun `everyone opens their chat, showing their bio and not the latest message`() {
        val opened = mutableListOf<String>()
        show { PeopleContent(robin, DemoGarden.people, onOpenChat = { opened += it }) }
        compose.onNodeWithText(DemoGarden.maya.bio).performScrollTo().assertExists()
        compose.onNodeWithText(DemoGarden.theo.bio).performScrollTo().assertExists()
        compose.onNodeWithText("Thank you! 💛").assertDoesNotExist()
        compose.onNodeWithText("Maya").performScrollTo().performClick()
        compose.onNodeWithText(DemoGarden.ana.bio).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("maya_robin", "ana_robin"), opened)
        assertAccessible()
    }

    @Test
    fun `someone with no bio shows only their name, since the section says who they are`() {
        show {
            PeopleContent(
                robin,
                People(bumblebees = listOf(DemoGarden.maya.copy(bio = "")), lookingAfter = listOf(DemoGarden.ana.copy(bio = "")), chats = listOf(chatWithMaya(read = false))),
            )
        }
        for (name in listOf("Maya", "Ana")) {
            val card = compose.onNode(hasText(name) and hasClickAction()).performScrollTo().fetchSemanticsNode()
            // Their emoji picture, then their name, and no line about them.
            val words = card.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }.filter { text -> text.any(Char::isLetter) }
            assertEquals(listOf(name), words)
        }
        assertAccessible()
    }

    @Test
    fun `unread chats come first in each section, then everyone by name`() {
        val (maya, theo, ana) = listOf(DemoGarden.maya, DemoGarden.theo, DemoGarden.ana)
        val zoe = ana.copy(uid = "zoe", displayName = "Zoe", bio = "")
        val bob = ana.copy(uid = "bob", displayName = "Bob", bio = "")
        show {
            PeopleContent(
                robin,
                People(
                    bumblebees = listOf(maya, theo, ana),
                    lookingAfter = listOf(zoe, bob),
                    chats = listOf(chatWith(maya, read = true), chatWith(theo, read = false), chatWith(ana, read = true), chatWith(zoe, read = false)),
                ),
            )
        }
        // Everyone fits on the screen, so each card's place is where the list put it.
        fun top(name: String) = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top
        assertEquals(listOf("Theo", "Ana", "Maya"), listOf("Ana", "Maya", "Theo").sortedBy(::top))
        assertEquals(listOf("Zoe", "Bob"), listOf("Bob", "Zoe").sortedBy(::top))
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
    fun `your answers as a Bumblebee are under the gardens you look after`() {
        var stats = 0
        show { PeopleContent(robin, People(lookingAfter = listOf(DemoGarden.ana)), onOpenBeeStats = { stats++ }) }
        val ana = compose.onNode(hasText("Ana") and hasClickAction()).performScrollTo().getBoundsInRoot()
        val link = node(R.string.bee_stats_open).performScrollTo()
        assertTrue(link.getBoundsInRoot().top >= ana.bottom)
        link.performClick()
        assertEquals(1, stats)
    }

    @Test
    fun `with no gardens, there are no answers to see`() {
        show { PeopleContent(robin.copy(approveeIds = emptyList()), People(bumblebees = listOf(DemoGarden.maya))) }
        node(R.string.bee_stats_open).assertDoesNotExist()
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
    fun `with nobody yet, a welcome card invites a first Bumblebee, above both sections`() {
        show { PeopleContent(robin.copy(approverIds = emptyList(), approveeIds = emptyList()), People()) }
        val welcome = node(R.string.people_welcome_body).assertExists().getBoundsInRoot()
        assertTrue(welcome.bottom <= node(R.string.circle_your_bees).getBoundsInRoot().top)
        node(R.string.people_bees_empty).performScrollTo().assertExists()
        node(R.string.people_gardens_empty).performScrollTo().assertExists()
        node(R.string.join_code_title).assertDoesNotExist()
        // No Bumblebees to ask.
        compose.onNodeWithContentDescription(text(R.string.requests_ask_button)).assertDoesNotExist()
        assertAccessible()
        // Its button opens the invite.
        compose.onNode(hasText(text(R.string.people_invite_more)) and hasClickAction()).performClick()
        node(R.string.invite_link_button).assertExists()
    }

    @Test
    fun `with someone in the circle, there's no welcome card`() {
        show { PeopleContent(robin, People(bumblebees = listOf(DemoGarden.maya))) }
        node(R.string.people_welcome_body).assertDoesNotExist()
    }

    @Test
    fun `with people in a section, it has no empty note`() {
        show { PeopleContent(robin, People(bumblebees = listOf(DemoGarden.maya), lookingAfter = listOf(DemoGarden.ana))) }
        node(R.string.people_bees_empty).assertDoesNotExist()
        node(R.string.people_gardens_empty).assertDoesNotExist()
    }

    @Test
    fun `the plus by Your Bumblebees opens the invite, beside its section name`() {
        show { PeopleContent(robin, People(bumblebees = listOf(DemoGarden.maya))) }
        val title = node(R.string.circle_your_bees).getBoundsInRoot()
        val plus = compose.onNodeWithContentDescription(text(R.string.people_invite_more))
        val bounds = plus.getBoundsInRoot()
        assertTrue(bounds.top < title.bottom && bounds.bottom > title.top)
        // The icon is drawn smaller; what a finger can hit is 48dp.
        plus.assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        plus.performClick()
        node(R.string.invite_title).assertExists()
        node(R.string.invite_link_button).assertExists()
        node(R.string.invite_code_title).assertExists()
        assertAccessible()
    }

    @Test
    fun `the plus by gardens shows your friend code to share`() {
        show { PeopleContent(robin, People(lookingAfter = listOf(DemoGarden.ana))) }
        compose.onNodeWithContentDescription(text(R.string.circle_join_title)).performClick()
        node(R.string.join_code_body).assertExists()
        compose.onNodeWithText(robin.friendCode).assertExists()
        compose.onNodeWithContentDescription(text(R.string.join_code_copy)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.join_code_share)).assertExists()
    }

    @Test
    fun `a friend code or a pasted invite link can be entered, and nothing else`() {
        show { PeopleContent(robin, People()) }
        compose.onNodeWithContentDescription(text(R.string.people_invite_more)).performClick()
        val field = hasSetTextAction() and hasText(text(R.string.invite_code_placeholder))
        val invite = compose.onNode(hasText(text(R.string.invite_code_button)) and hasClickAction())
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
