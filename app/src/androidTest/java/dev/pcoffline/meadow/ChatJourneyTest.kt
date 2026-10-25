package dev.pcoffline.meadow

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestore
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.WRITE_TIMEOUT_MS
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, with two people: the person on this phone becomes Sam's Bumblebee and chats with
 * Sam. A message written offline stays on the phone, counts as sent and reaches Sam once back
 * online. When they part ways the chat closes: nothing more can be written, but what was said
 * stays readable.
 */
@RunWith(AndroidJUnit4::class)
class ChatJourneyTest : EndToEndTest() {
    private var sam: EmulatorFriend? = null

    @After
    fun closeSam() {
        sam?.close()
    }

    @Test
    fun aChatKeepsOfflineMessagesAndClosesWhenTheyPartWays() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            val me = acceptInviteFrom(sam, "Sam")
            val chatId = ChatRepository.chatIdFor(me, sam.uid)
            sam.send(chatId, "Thanks for looking after me")

            node(R.string.tab_circle).performClick()
            openGardenOf("Sam")
            node(R.string.action_chat).performScrollTo().performClick()
            waitForText("Thanks for looking after me")

            // Offline, the message shows as sending and the box clears once it's queued.
            val db = FirebaseFirestore.getInstance()
            await(db.disableNetwork())
            val input = hasSetTextAction() and hasText(text(R.string.chat_placeholder))
            typeInto(input, "Written in a tunnel")
            compose.onNodeWithContentDescription(text(R.string.action_send)).performClick()
            waitForText("Written in a tunnel")
            waitFor(R.string.chat_sending)
            compose.waitUntil(WRITE_TIMEOUT_MS + 10_000) { compose.onAllNodes(input).fetchSemanticsNodes().isNotEmpty() }

            // Back online, it reaches the server as this person's message.
            await(db.enableNetwork())
            waitForBackendDocs("chats/$chatId/messages") { messages ->
                messages.any { it.string("text") == "Written in a tunnel" && it.string("senderId") == me }
            }
            compose.waitUntil(20_000) { compose.onAllNodesWithText(text(R.string.chat_sending)).fetchSemanticsNodes().isEmpty() }

            // Stepping down (as from another of this person's phones) closes the open chat.
            runBlocking { AppGraph.backend.call("stepDown", mapOf("approveeUid" to sam.uid)) }
            waitFor(R.string.chat_closed_title)
            compose.onNodeWithContentDescription(text(R.string.action_send)).assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).assertIsNotEnabled()
            for (said in listOf("Thanks for looking after me", "Written in a tunnel")) {
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(said))
                waitForText(said)
            }
        }
    }
}
