package dev.pcoffline.meadow

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
 * End to end, limits in a chat (#93): Kai (on this phone) has Sam as a Bumblebee. Sam suggests a
 * looser limit; Kai applies it after seeing what changes, and no question goes to Sam, because
 * Sam's suggestion is the approval. Sam then shares a limit; Kai copies it, and the editor says it
 * replaces Kai's limit.
 */
@RunWith(AndroidJUnit4::class)
class LimitSharingJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @After
    fun closePeople() {
        people.forEach { it.close() }
    }

    private fun samsInbox(sam: EmulatorFriend): List<String> =
        await(sam.db.collection("users/${sam.uid}/inbox").get()).documents.map { it.id }

    @Test
    fun aBumblebeesSuggestionAppliesWithoutAskingAndASharedLimitCopiesIntoTheEditor() {
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also(people::add)
        val token = kai.call("createInviteLink")!!["token"] as String
        sam.call("acceptInviteLink", mapOf("token" to token))
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
            tab(R.string.tab_people).performClick()
            compose.onNode(hasText("Sam") and hasClickAction()).performClick()
            waitFor(R.string.limit_card_suggested)
            node(R.string.limit_apply).performScrollTo().performClick()
            // What changes, before anything is saved.
            waitFor(R.string.limit_apply_title)
            inDialog(text(R.string.limit_apply)).performClick()
            waitForText(text(R.string.limit_apply_done, "Pebbles"))
            waitForBackend(limit) { it.int("dailyMinutes") == 90 }
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
        }
    }
}
