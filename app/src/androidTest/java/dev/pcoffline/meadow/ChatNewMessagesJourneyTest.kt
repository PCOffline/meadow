package dev.pcoffline.meadow

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.ChatRepository
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, with two people (#110): a chat opens at what's new since it was last read, under a
 * "New" line. Reading back, a message that arrives brings a "New message" button instead of
 * moving the list, and the button goes down to it.
 */
@RunWith(AndroidJUnit4::class)
class ChatNewMessagesJourneyTest : EndToEndTest() {
    private var sam: EmulatorFriend? = null

    @After
    fun closeSam() {
        sam?.close()
    }

    private fun shown(value: String) = compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aChatOpensAtWhatsNewAndOffersTheWayDownToANewMessage() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            val me = acceptInviteFrom(sam, "Sam")
            val chatId = ChatRepository.chatIdFor(me, sam.uid)

            // A long chat, read to its end.
            repeat(OLD) { sam.send(chatId, "Earlier $it") }
            openChatWith("Sam")
            eventually("the latest earlier message") { shown("Earlier ${OLD - 1}") }
            // Read: the chat keeps when, so the next visit knows what's new.
            waitForBackend("chats/$chatId") { it.optJSONObject("lastRead")?.optJSONObject("mapValue")?.optJSONObject("fields")?.has(me) == true }
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Sam writes while the chat is closed. It opens at the "New" line, with the first
            // new message in view, not at the oldest messages.
            sam.send(chatId, "Something new")
            sam.send(chatId, "And one more")
            openChatWith("Sam")
            eventually("the New line") {
                compose.onAllNodesWithContentDescription(text(R.string.chat_new_line_spoken), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            eventually("the first new message") { shown("Something new") }
            check(!shown("Earlier 0")) { "The chat opened at its oldest messages: ${screenText()}" }

            // Reading back to the start, a new message doesn't move the list: a button offers it.
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Earlier 0"))
            sam.send(chatId, "Are you there?")
            val button = hasContentDescription(text(R.string.chat_new_message_spoken))
            eventually("the New message button") { compose.onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
            check(shown("Earlier 0")) { "The list moved: ${screenText()}" }

            // The button goes down to it, and goes away.
            compose.onNode(button).performClick()
            eventually("the newest message") { shown("Are you there?") }
            eventually("the button gone") { compose.onAllNodes(button).fetchSemanticsNodes().isEmpty() }
        }
    }

    private companion object {
        /** Enough messages that the chat's start and its end don't fit on one screen. */
        const val OLD = 30
    }
}
