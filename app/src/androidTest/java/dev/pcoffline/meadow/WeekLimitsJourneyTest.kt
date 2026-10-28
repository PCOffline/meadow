package dev.pcoffline.meadow

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.TimeCap
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, the limits that fit a week: Kai (on this phone) has Sam. A cap within hours is
 * stricter, so it's saved at once through the backend, and the commitment goes to Sam's chat from
 * the note that says it's saved. Weekends without a limit of their own ask Sam first, and arrive
 * on this phone once Sam says yes.
 */
@RunWith(AndroidJUnit4::class)
class WeekLimitsJourneyTest : EndToEndTest() {
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

    private fun samsInbox(sam: EmulatorFriend): List<String> =
        await(sam.db.collection("users/${sam.uid}/inbox").get()).documents.map { it.id }

    private fun day(name: String) = compose.onNodeWithContentDescription(name)

    @Test
    fun aCapAppliesAtOnceAndGoesToTheChatWhileFreeWeekendsAskFirst() {
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

            // Up to 30 minutes between 10:00 and 16:00 on weekdays: stricter, so no asking.
            openTheLimit()
            node(R.string.limit_editor_cap_add).performScrollTo().performClick()
            inDialog(text(R.string.action_ok)).performClick()
            node(R.string.limit_editor_loosens_title).assertDoesNotExist()
            node(R.string.action_save).performScrollTo().performClick()
            waitFor(R.string.limit_editor_updated)
            waitForBackend(limit) { it.has("caps") && it.int("dailyMinutes") == 60 }
            eventually("the cap on this phone") {
                AppGraph.store.limits.value.single { it.packageName == pebbles }.caps == listOf(TimeCap(listOf(1, 2, 3, 4, 5), 600, 960, 30))
            }
            assertTrue("Sam was asked about a stricter limit", samsInbox(sam).isEmpty())

            // The note that says it's saved shares it, straight into the chat with Sam.
            node(R.string.share_title).performClick()
            waitFor(R.string.share_commitment_title)
            node(R.string.share_commitment_in_meadow).assertExists()
            compose.onNode(hasText("Sam") and hasClickAction()).performClick()
            waitForText(text(R.string.share_commitment_sent, "Sam"))
            waitForBackendDocs("chats/$chat/messages") { messages -> messages.any { "Pebbles" in it.string("text").orEmpty() } }
            node(R.string.share_commitment_title).assertDoesNotExist()
            waitForSnackbarToGo(R.string.share_commitment_sent, "Sam")

            // No limit at weekends is looser than an hour a day, so Sam is asked.
            openTheLimit()
            node(R.string.limit_editor_days_add).performScrollTo().performClick()
            day("Saturday").performScrollTo().performClick()
            day("Sunday").performScrollTo().performClick()
            compose.onNode(hasText(text(R.string.limit_editor_days_no_limit))).performScrollTo().performClick()
            waitFor(R.string.limit_editor_loosens_title)
            tapWhenEnabled(R.string.limit_editor_ask)
            waitFor(R.string.limit_editor_requested)
            var request = ""
            eventually("a request in Sam's inbox") { samsInbox(sam).firstOrNull()?.also { request = it } != null }
            val asked = backendDoc("requests/$request")!!
            assertEquals("change_limit", asked.string("type"))
            val days = asked.getJSONObject("dayMinutes").getJSONObject("mapValue").getJSONObject("fields")
            assertEquals(setOf("6", "7"), days.keys().asSequence().toSet())
            assertEquals(0, days.int("6"))
            // Nothing changes until Sam says yes.
            assertTrue(backendDoc(limit)!!.optJSONObject("dayMinutes") == null)

            sam.call("decideRequest", mapOf("requestId" to request, "approve" to true))
            waitForBackend(limit) { it.has("dayMinutes") }
            eventually("no limit at weekends on this phone") {
                AppGraph.store.limits.value.single { it.packageName == pebbles }.dayMinutes == mapOf(6 to 0, 7 to 0)
            }
        }
    }
}
