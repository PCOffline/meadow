package dev.pcoffline.meadow

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.ui.minutesText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, with a Bumblebee: Kai (on this phone) has Sam. Loosening a limit asks Sam first,
 * can be withdrawn, and only applies once Sam says yes; asking to remove it and hearing "not
 * today" leaves it in place. Every step is checked on screen and in the backend.
 */
@RunWith(AndroidJUnit4::class)
class LimitChangeJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @After
    fun closePeople() {
        people.forEach { it.close() }
    }

    private fun openTheLimit() {
        waitForText("Pebbles")
        compose.onNode(hasText("Pebbles") and hasClickAction()).performScrollTo().performClick()
        waitFor(R.string.limit_editor_existing)
    }

    /** The ids of the requests in Sam's inbox. */
    private fun samsInbox(sam: EmulatorFriend): List<String> =
        await(sam.db.collection("users/${sam.uid}/inbox").get()).documents.map { it.id }

    private fun nextRequest(sam: EmulatorFriend, seen: Set<String>): String {
        var id = ""
        eventually("a new request in Sam's inbox") {
            id = samsInbox(sam).firstOrNull { it !in seen }.orEmpty()
            id.isNotEmpty()
        }
        return id
    }

    @Test
    fun looseningWaitsForAYesAndRemovingCanHearNotToday() {
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also(people::add)
        val token = kai.call("createInviteLink")!!["token"] as String
        sam.call("acceptInviteLink", mapOf("token" to token))
        signInAs(kai)
        val limit = "users/${kai.uid}/limits/$pebbles"

        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            // Planting is tightening, so it applies at once even with a Bumblebee.
            runBlocking { AppGraph.limits.save(AppLimit(pebbles, "Pebbles", 30, 0)) }
            eventually("Sam counted as Kai's Bumblebee") { AppGraph.store.approverCount.value == 1 }

            // Doubling the daily time asks Sam instead of saving.
            openTheLimit()
            compose.onNode(hasText(app.resources.minutesText(60)) and hasClickAction()).performScrollTo().performClick()
            waitFor(R.string.limit_editor_loosens_title)
            typeInto(hasSetTextAction() and hasText(text(R.string.limit_editor_note)), "Visiting Grandma")
            node(R.string.limit_editor_ask).performScrollTo().performClick()
            waitFor(R.string.limit_editor_requested)
            val first = nextRequest(sam, emptySet())
            val asked = backendDoc("requests/$first")!!
            assertEquals("change_limit", asked.string("type"))
            assertEquals(60, asked.int("dailyMinutes"))
            assertEquals("Visiting Grandma", asked.string("message"))
            assertEquals(30, backendDoc(limit)!!.int("dailyMinutes"))
            waitForSnackbarToGo(R.string.limit_editor_requested)

            // The limit says it's waiting, and the question can be taken back.
            openTheLimit()
            waitFor(R.string.limit_editor_pending_title)
            node(R.string.request_withdraw).performScrollTo().performClick()
            waitFor(R.string.request_withdrawn)
            waitForBackend("requests/$first") { it.string("status") == "cancelled" }
            waitForSnackbarToGo(R.string.request_withdrawn)

            // Asked again, Sam says yes: the new limit reaches the backend and this phone.
            compose.onNode(hasText(app.resources.minutesText(60)) and hasClickAction()).performScrollTo().performClick()
            node(R.string.limit_editor_ask).performScrollTo().performClick()
            waitFor(R.string.limit_editor_requested)
            val second = nextRequest(sam, setOf(first))
            sam.call("decideRequest", mapOf("requestId" to second, "approve" to true))
            waitForBackend(limit) { it.int("dailyMinutes") == 60 }
            eventually("the new limit on this phone") { AppGraph.store.limits.value.single { it.packageName == pebbles }.dailyMinutes == 60 }
            // Answered, the request lives on in the chat with Sam.
            openChatWith("Sam")
            waitForText(text(R.string.request_decided_yes, "Sam"))
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Asking to remove it: Sam says not today, with a word why, and it stays.
            tab(R.string.tab_garden).performClick()
            openTheLimit()
            node(R.string.limit_editor_ask_remove).performScrollTo().performClick()
            typeIntoDialog(hasSetTextAction(), "I've got it under control")
            inDialog(text(R.string.limit_remove_ask)).performClick()
            waitFor(R.string.limit_editor_requested)
            val third = nextRequest(sam, setOf(first, second))
            assertEquals("remove_limit", backendDoc("requests/$third")!!.string("type"))
            sam.call("decideRequest", mapOf("requestId" to third, "approve" to false, "note" to "Not tonight"))
            waitForBackend("requests/$third") { it.string("status") == "denied" }
            openChatWith("Sam")
            waitForText(text(R.string.request_decided_with_note, text(R.string.request_decided_no, "Sam"), "Not tonight"))
            assertNotNull(backendDoc(limit))
            assertNotNull(AppGraph.store.limits.value.singleOrNull { it.packageName == pebbles })
        }
    }
}
