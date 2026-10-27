package dev.pcoffline.meadow.ui.screens

import dev.pcoffline.meadow.data.SharedPage
import dev.pcoffline.meadow.ui.circle.FriendPage
import dev.pcoffline.meadow.ui.chat.ChatListContent
import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Chat
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.chat.ChatRow
import dev.pcoffline.meadow.ui.circle.CircleContent
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.home.GardenState
import dev.pcoffline.meadow.ui.stats.Period
import dev.pcoffline.meadow.ui.stats.Selection
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.stats.StatsUiState
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.usage.UsageHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MainScreensTest : ScreenTest() {

    @Test
    fun `the garden greets you and shows how each limit is doing`() {
        val opened = mutableListOf<String>()
        show { GardenContent(DemoGarden.robin, DemoGarden.garden, onOpenLimit = { opened += it }) }
        compose.onNodeWithText("Robin", substring = true).assertExists()
        node(R.string.garden_tile_resting_today).assertExists()
        compose.onNodeWithText("extra today", substring = true).assertExists()
        compose.onNodeWithText("Instagram").performScrollTo().performClick()
        assertEquals(listOf("demo.instagram"), opened)
        assertAccessible()
    }

    @Test
    fun `the garden is accessible in every theme, by day and night`() {
        var look by mutableStateOf(GardenTheme.MEADOW to DarkMode.LIGHT)
        compose.setContent {
            dev.pcoffline.meadow.ui.theme.MeadowTheme(look.first, look.second) { AppBackground { GardenContent(DemoGarden.robin, DemoGarden.garden) } }
        }
        for (theme in GardenTheme.entries) for (mode in listOf(DarkMode.LIGHT, DarkMode.DARK)) {
            look = theme to mode
            compose.waitForIdle()
            assertAccessible()
        }
    }

    @Test
    fun `an empty garden invites you to plant a limit`() {
        var planted = 0
        show { GardenContent(DemoGarden.robin, GardenState(loaded = true), onPlantLimit = { planted++ }) }
        node(R.string.garden_empty_title).performClick()
        assertEquals(1, planted)
        assertAccessible()
    }

    @Test
    fun `the garden points to unfinished setup`() {
        var setup = 0
        show { GardenContent(DemoGarden.robin, DemoGarden.garden.copy(essentialsReady = false), onOpenSetup = { setup++ }) }
        node(R.string.garden_notice_setup_action).performClick()
        assertEquals(1, setup)
        assertAccessible()
    }

    @Test
    fun `stats switch periods and step back in time`() {
        val periods = mutableListOf<Period>()
        val shifts = mutableListOf<Int>()
        show { StatsContent(DemoGarden.stats, onSelect = { periods += it }, onShift = { shifts += it }) }
        compose.onNodeWithText("YouTube").assertExists()
        node(R.string.stats_month).performClick()
        compose.onNodeWithContentDescription(text(R.string.stats_earlier)).performClick()
        compose.onNodeWithContentDescription(text(R.string.stats_later)).performClick() // Last week: a later one exists.
        assertEquals(listOf(Period.MONTH), periods)
        assertEquals(listOf(-1, 1), shifts)
        assertAccessible()
    }

    @Test
    fun `stats can't go past this period`() {
        show { StatsContent(DemoGarden.stats.copy(selection = DemoGarden.stats.selection.copy(offset = 0))) }
        compose.onNodeWithContentDescription(text(R.string.stats_later)).assertIsNotEnabled()
    }

    @Test
    fun `tapping an app in stats opens its limit, unless it can't have one`() {
        val opened = mutableListOf<String>()
        val stats = DemoGarden.stats.stats!!
        val withPhone = stats.copy(apps = stats.apps.map { if (it.label == "Maps") it.copy(canLimit = false) else it })
        show { StatsContent(DemoGarden.stats.copy(stats = withPhone), onOpenApp = { opened += it }) }
        compose.onNodeWithText("YouTube").performScrollTo().performClick()
        assertEquals(listOf("demo.youtube"), opened)
        // Further down a lazy list: scrolled to, so it's composed at all.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Maps"))
        compose.onNodeWithText("Maps").assertHasNoClickAction()
        assertAccessible()
    }

    @Test
    fun `stats stop going back where the kept history ends`() {
        val edge = Selection(Period.DAY, -UsageHistory.KEEP_DAYS.toInt())
        show { StatsContent(DemoGarden.stats.copy(selection = edge)) }
        compose.onNodeWithContentDescription(text(R.string.stats_earlier)).assertIsNotEnabled()
        assertTrue(Selection(Period.DAY, -UsageHistory.KEEP_DAYS.toInt() + 1).hasEarlier(Days.today()))
        assertTrue(!Selection(Period.MONTH, -5).hasEarlier(Days.today()))
        assertTrue(Selection(Period.WEEK, -1).hasEarlier(Days.today()))
    }

    @Test
    fun `an unread chat says so, not only in bold`() {
        val chat = Chat(
            id = "maya_robin", participants = listOf("maya", "robin"), names = mapOf("maya" to "Maya"), avatars = emptyMap(),
            lastMessage = "How was the walk?", lastMessageAtMillis = System.currentTimeMillis(), lastSenderId = "maya",
            lastReadMillis = emptyMap(),
        )
        var opened = 0
        show { ChatRow(chat, uid = "robin", onOpen = { opened++ }) }
        compose.onNodeWithContentDescription(text(R.string.chats_unread)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `no chats says so, and loading or a chat doesn't`() {
        var chats by mutableStateOf<List<Chat>?>(null)
        show { ChatListContent(DemoGarden.robin, chats) }
        node(R.string.chats_empty_title).assertDoesNotExist()
        chats = emptyList()
        node(R.string.chats_empty_title).assertExists()
        assertAccessible()
        chats = listOf(DemoGarden.chat)
        node(R.string.chats_empty_title).assertDoesNotExist()
    }

    @Test
    fun `offline, a friend's page waits quietly for parts that can't come yet`() {
        var offline by mutableStateOf(false)
        show { FriendPage(SharedPage(DemoGarden.maya, null, null, null, null), waiting = true, offline = offline) }
        node(R.string.loading_garden).assertExists()
        offline = true
        node(R.string.loading_garden).assertDoesNotExist()
        // Not knowing isn't the same as nothing shared.
        node(R.string.friend_nothing_shared, DemoGarden.maya.displayName).assertDoesNotExist()
    }

    @Test
    fun `a read chat has no unread mark`() {
        val now = System.currentTimeMillis()
        val chat = Chat(
            id = "maya_robin", participants = listOf("maya", "robin"), names = mapOf("maya" to "Maya"), avatars = emptyMap(),
            lastMessage = "How was the walk?", lastMessageAtMillis = now - 1_000, lastSenderId = "maya",
            lastReadMillis = mapOf("robin" to now),
        )
        show { ChatRow(chat, uid = "robin", onOpen = {}) }
        compose.onNodeWithContentDescription(text(R.string.chats_unread)).assertDoesNotExist()
    }

    @Test
    fun `a Bumblebee already on the way out has no empty menu`() {
        val leaving = DemoGarden.robin.copy(pendingRemovals = mapOf(DemoGarden.maya.uid to System.currentTimeMillis() + 3_600_000))
        show { CircleContent(leaving, listOf(DemoGarden.maya, DemoGarden.theo), emptyList(), emptyList(), emptyList()) }
        // Only Theo, who isn't leaving, still offers the menu with "Remove" in it.
        compose.onAllNodesWithContentDescription(text(R.string.action_more)).assertCountEquals(1)
        assertAccessible()
    }

    @Test
    fun `stats without usage access explain why and lead to setup`() {
        var setup = 0
        show { StatsContent(StatsUiState(hasPermission = false), onOpenSetup = { setup++ }) }
        node(R.string.stats_no_access_title).assertExists()
        node(R.string.stats_no_access_action).performClick()
        assertEquals(1, setup)
        assertAccessible()
    }

    @Test
    fun `the circle lists Bumblebees and the gardens you look after`() {
        val chats = mutableListOf<String>()
        val friends = mutableListOf<String>()
        show {
            CircleContent(
                DemoGarden.robin, listOf(DemoGarden.maya, DemoGarden.theo), listOf(DemoGarden.ana), emptyList(), emptyList(),
                onOpenChat = { chats += it }, onOpenFriend = { friends += it },
            )
        }
        compose.onNodeWithContentDescription(text(R.string.circle_chat_with, "Maya")).performClick()
        assertEquals(listOf("maya_robin"), chats)
        compose.onNodeWithText("Ana", substring = true).performScrollTo().performClick()
        assertEquals(listOf("ana"), friends)
        node(R.string.invite_title).assertExists()
        assertAccessible()
    }

    @Test
    fun `a garden without an email can invite too`() {
        show { CircleContent(DemoGarden.robin.copy(approverIds = emptyList(), approveeIds = emptyList()), emptyList(), emptyList(), emptyList(), emptyList()) }
        node(R.string.invite_title).assertExists()
        node(R.string.invite_code_title).assertExists()
        // Nobody in the circle yet: no empty sections, just ways to fill them.
        node(R.string.circle_your_bees).assertDoesNotExist()
        node(R.string.circle_looking_after).assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `a friend code or a pasted invite link can be entered, and nothing else`() {
        show { CircleContent(DemoGarden.robin, emptyList(), emptyList(), emptyList(), emptyList()) }
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
    fun `chat shows the conversation and sends what you type`() {
        val sent = mutableListOf<String>()
        show {
            ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, onSend = { text, done -> sent += text; done() }, requestCard = { id, _ -> Text("Request $id") })
        }
        compose.onNodeWithText("Thank you! 💛").assertExists()
        compose.onNodeWithText("Request r1").assertExists()
        val send = compose.onNodeWithContentDescription(text(R.string.action_send))
        send.assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("See you tomorrow")
        send.assertIsEnabled().performClick()
        assertEquals(listOf("See you tomorrow"), sent)
        compose.onNode(hasSetTextAction() and hasText("See you tomorrow")).assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `a message that fails to send stays in the box with the reason`() {
        val failed = "Couldn't send that just now"
        var error by mutableStateOf<String?>(null)
        var sending by mutableStateOf(false)
        val tries = mutableListOf<String>()
        show {
            ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, sending = sending, error = error, onSend = { text, _ ->
                tries += text
                sending = true
            }, requestCard = { id, _ -> Text("Request $id") })
        }
        val send = compose.onNodeWithContentDescription(text(R.string.action_send))
        compose.onNode(hasSetTextAction()).performTextInput("See you tomorrow")
        send.performClick()
        // While it's on its way the text stays, and it can't be sent twice.
        send.assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("See you tomorrow")).assertExists()

        sending = false
        error = failed
        compose.onNodeWithText(failed, substring = true).assertExists()
        compose.onNode(hasSetTextAction() and hasText("See you tomorrow")).assertExists()
        send.assertIsEnabled()
        assertEquals(listOf("See you tomorrow"), tries)
        assertAccessible()
    }

    @Test
    fun `text typed while a message sends isn't cleared when it's sent`() {
        var finish: () -> Unit = {}
        show {
            ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, onSend = { _, done -> finish = done }, requestCard = { id, _ -> Text("Request $id") })
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("First")
        compose.onNodeWithContentDescription(text(R.string.action_send)).performClick()
        field.performTextInput(" and second")
        compose.runOnIdle { finish() }
        compose.onNode(hasSetTextAction() and hasText("First and second")).assertExists()
    }

    @Test
    fun `a full chat still shows and marks read each new message`() {
        // Only the latest 300 messages are loaded, so a new one leaves the count at 300.
        val all = (1..301).map { i -> ChatMessage("m$i", DemoGarden.maya.uid, "Message $i", MessageKind.TEXT, null, i * 1_000L, pending = false) }
        var messages by mutableStateOf(all.take(300))
        var seen = 0
        show { ChatContent(DemoGarden.robin, DemoGarden.chat, messages, onSeen = { seen++ }) }
        compose.onNodeWithText("Message 300").assertIsDisplayed()
        assertEquals(1, seen)

        messages = all.drop(1)
        compose.waitForIdle()
        compose.onNodeWithText("Message 301").assertIsDisplayed()
        assertEquals(2, seen)
    }

    @Test
    fun `a closed chat says so and can't be written to`() {
        show { ChatContent(DemoGarden.robin, chat = null, messages = emptyList()) }
        node(R.string.chat_closed_title).assertExists()
        // A disabled field has no text-input action, so find it by its placeholder.
        compose.onNode(hasText(text(R.string.chat_placeholder))).assertIsNotEnabled()
        assertAccessible()
    }

    @Test
    fun `a resting app offers to ask a Bumblebee, go home, or an emergency unlock`() {
        var home = 0
        show { RestContent("demo.tiktok", DemoGarden.rest, onHome = { home++ }) }
        node(R.string.rest_title, "TikTok").assertExists()
        node(R.string.rest_ask).assertExists()
        node(R.string.rest_emergency).assertExists()
        node(R.string.rest_home).performClick()
        assertEquals(1, home)
        assertAccessible()
    }

    @Test
    fun `a resting app without Bumblebees explains there's no one to ask`() {
        show { RestContent("demo.tiktok", DemoGarden.rest.copy(hasBumblebees = false)) }
        node(R.string.rest_no_bees).assertExists()
        assertTrue(compose.onAllNodesWithText(text(R.string.rest_ask)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `an app with extra time can be opened again`() {
        val opened = mutableListOf<String>()
        val withExtra = DemoGarden.tiktok.copy(bonusTodayMinutes = 15)
        show { RestContent("demo.tiktok", DemoGarden.rest.copy(budget = withExtra), onOpen = { opened += it }) }
        node(R.string.rest_title_yes).assertExists()
        node(R.string.rest_open, "TikTok").performClick()
        assertEquals(listOf("demo.tiktok"), opened)
        assertAccessible()
    }
}
