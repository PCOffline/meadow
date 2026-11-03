package dev.pcoffline.meadow

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, limits in a chat (#93): Kai (on this phone) and Sam look after each other.
 * - Sam suggests a looser limit; Kai applies it after seeing what changes, and no question goes to
 *   Sam, because Sam's suggestion is the approval.
 * - Sam shares a limit; Kai copies it, and the editor says it replaces Kai's limit.
 * - Kai shares a limit from the share sheet, and suggests one from Sam's page: both reach the chat
 *   as cards.
 */
@RunWith(AndroidJUnit4::class)
class LimitSharingJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @After
    fun closePeople() {
        people.forEach { it.close() }
    }

    /**
     * Opens the chat with Sam. Kai and Sam look after each other, so People lists Sam twice, once
     * for each role. Both rows open the same chat.
     */
    private fun openChatWithSam() {
        tab(R.string.tab_people).performClick()
        val sam = hasText("Sam") and hasClickAction()
        eventually("Sam in People") { compose.onAllNodes(sam).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(sam).onFirst().performClick()
        waitForText(text(R.string.chat_placeholder))
    }

    private fun samsInbox(sam: EmulatorFriend): List<String> =
        await(sam.db.collection("users/${sam.uid}/inbox").get()).documents.map { it.id }

    @Test
    fun aBumblebeesSuggestionAppliesWithoutAskingAndASharedLimitCopiesIntoTheEditor() {
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also(people::add)
        val token = kai.call("createInviteLink")!!["token"] as String
        sam.call("acceptInviteLink", mapOf("token" to token))
        // Kai looks after Sam too, and Sam shares limits: Kai can suggest from Sam's page.
        await(sam.db.document("users/${sam.uid}/limits/$pebbles").set(mapOf("packageName" to pebbles, "appName" to "Pebbles", "dailyMinutes" to 45, "weeklyMinutes" to 0)))
        await(sam.db.document("users/${sam.uid}/private/account").update("privacy.shareLimits", true))
        val back = sam.call("createInviteLink")!!["token"] as String
        kai.call("acceptInviteLink", mapOf("token" to back))
        signInAs(kai)
        val limit = "users/${kai.uid}/limits/$pebbles"
        val chat = ChatRepository.chatIdFor(kai.uid, sam.uid)

        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            runBlocking { AppGraph.limits.save(AppLimit(pebbles, "Pebbles", 60, 0)) }
            eventually("Sam counted as Kai's Bumblebee") { AppGraph.store.approverCount.value == 1 }
            waitForBackend(limit) { it.int("dailyMinutes") == 60 }

            // Sam suggests an hour and a half: looser, yet it needs no question to Sam.
            sam.sendLimit(chat, mapOf("packageName" to pebbles, "appName" to "Pebbles", "dailyMinutes" to 90, "weeklyMinutes" to 0), "suggested")
            openChatWithSam()
            waitFor(R.string.limit_card_suggested)
            node(R.string.limit_apply).performScrollTo().performClick()
            // What changes, before anything is saved.
            waitFor(R.string.limit_apply_title)
            inDialog(text(R.string.limit_apply)).performClick()
            waitForText(text(R.string.limit_apply_done, "Pebbles"))
            waitForBackend(limit) { it.int("dailyMinutes") == 90 }
            // The note sits over the bottom of the screen, where Save will be.
            waitForSnackbarToGo(R.string.limit_apply_done, "Pebbles")
            assertTrue("Sam was asked about a suggestion Sam made", samsInbox(sam).isEmpty())

            // Sam shares a limit of their own: Copy opens Kai's editor from it, over Kai's limit.
            sam.sendLimit(chat, mapOf("packageName" to pebbles, "appName" to "Pebbles", "dailyMinutes" to 20, "weeklyMinutes" to 0), "shared")
            waitFor(R.string.limit_card_shared)
            node(R.string.limit_menu_copy).performScrollTo().performClick()
            waitForText(text(R.string.limit_copy_replaces, "Pebbles"))
            // Stricter, so it saves at once.
            node(R.string.action_save).performScrollTo().performClick()
            waitFor(R.string.limit_editor_updated)
            waitForBackend(limit) { it.int("dailyMinutes") == 20 }

            // Kai shares the saved limit with Sam, from the note that says it's saved: the share
            // sheet sends a shared card to the chat.
            node(R.string.share_title).performClick()
            waitFor(R.string.share_commitment_in_meadow)
            compose.onNode(hasText("Sam") and hasClickAction()).performClick()
            waitForBackendDocs("chats/$chat/messages") { messages ->
                messages.any { it.string("kind") == "limit" && it.string("mode") == "shared" && it.string("senderId") == kai.uid }
            }
            waitForSnackbarToGo(R.string.share_commitment_sent, "Sam")

            // Kai suggests a change from Sam's page: the limit menu, the editor, then Send.
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            openChatWithSam()
            compose.onNodeWithContentDescription(text(R.string.chat_see_garden, "Sam")).performClick()
            waitFor(R.string.limit_menu_hint)
            compose.onAllNodes(hasClickAction() and hasText("Pebbles", substring = true)).onLast().performScrollTo().performClick()
            node(R.string.limit_menu_suggest_change).performClick()
            waitFor(R.string.limit_suggest_title)
            node(R.string.limit_suggest_send).performScrollTo().performClick()
            waitForBackendDocs("chats/$chat/messages") { messages ->
                messages.any { it.string("kind") == "limit" && it.string("mode") == "suggested" && it.string("senderId") == kai.uid }
            }
        }
    }
}
