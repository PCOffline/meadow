package dev.pcoffline.meadow

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end (#114): a saved limit can be shared at any time, not only from the note after Save.
 * Kai (on this phone) shares Pebbles' limit with Sam, their Bumblebee: from the garden, with a long
 * press on its card, and from its editor's Share button. Each time the card in Sam's chat is the
 * limit as saved.
 */
@RunWith(AndroidJUnit4::class)
class ShareSavedLimitJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @After
    fun closePeople() {
        people.forEach { it.close() }
    }

    @Test
    fun aSavedLimitSharesFromTheGardenAndFromItsEditor() {
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also(people::add)
        val token = kai.call("createInviteLink")!!["token"] as String
        sam.call("acceptInviteLink", mapOf("token" to token))
        signInAs(kai)
        val chat = ChatRepository.chatIdFor(kai.uid, sam.uid)

        /** The limit cards Kai has shared in the chat with Sam, as their daily minutes. */
        fun sharedCards() = backendDocs("chats/$chat/messages")
            .filter { it.string("kind") == "limit" && it.string("mode") == "shared" && it.string("senderId") == kai.uid }
            .map { it.optJSONObject("limit")?.optJSONObject("mapValue")?.optJSONObject("fields")?.int("dailyMinutes") }

        /** In the share sheet, sends to Sam, and waits for the note to go. */
        fun sendToSam() {
            waitFor(R.string.share_commitment_in_meadow)
            compose.onAllNodes(hasText("Sam") and hasClickAction()).onFirst().performClick()
            waitForText(text(R.string.share_commitment_sent, "Sam"))
            waitForSnackbarToGo(R.string.share_commitment_sent, "Sam")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            runBlocking { AppGraph.limits.save(AppLimit(pebbles, "Pebbles", 45, 0)) }
            eventually("Sam counted as Kai's Bumblebee") { AppGraph.store.approverCount.value == 1 }
            waitForBackend("users/${kai.uid}/limits/$pebbles") { it.int("dailyMinutes") == 45 }

            // From the garden: a long press on Pebbles' card offers to share it.
            val card = hasText("Pebbles") and hasClickAction()
            eventually("Pebbles in the garden") { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(card).onFirst().performTouchInput { longClick() }
            node(R.string.limit_share).performClick()
            sendToSam()
            waitForBackendDocs("chats/$chat/messages") { sharedCards() == listOf(45) }

            // From its editor: the Share button at the top shares it as saved.
            compose.onAllNodes(card).onFirst().performClick()
            waitFor(R.string.limit_editor_existing)
            compose.onNodeWithContentDescription(text(R.string.limit_share)).performClick()
            sendToSam()
            waitForBackendDocs("chats/$chat/messages") { sharedCards() == listOf(45, 45) }
        }
    }
}
