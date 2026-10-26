package dev.pcoffline.meadow

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, the circle from Kai's phone, with Sam played by the test. Invites by friend code
 * both ways: declined, accepted, cancelled; a Bumblebee removed and kept after all; and invite
 * links: shared, mistyped and Kai's own. Each step is checked on screen and in the backend.
 */
@RunWith(AndroidJUnit4::class)
class CircleJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @Before
    fun catchSharing() {
        Intents.init()
        // The share sheet is Android's, outside the app: it's checked as asked for, not opened.
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun closePeople() {
        Intents.release()
        people.forEach { it.close() }
    }

    private fun friendCodeOf(person: EmulatorFriend) = backendDoc("users/${person.uid}/private/account")!!.string("friendCode")!!

    /** Waits for [matcher] somewhere in the circle, scrolling to it: the list is longer than the screen. */
    private fun seeInCircle(matcher: SemanticsMatcher) =
        eventually("$matcher in the circle") { runCatching { compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher) }.isSuccess }

    private fun tapInCircle(matcher: SemanticsMatcher) {
        seeInCircle(matcher)
        compose.onNode(matcher).performClick()
    }

    /** Whether an invite from [from] to [to] is still waiting, read from the backend. */
    private fun pendingInvite(from: EmulatorFriend, to: EmulatorFriend) = backendDocs("invites").any {
        it.string("fromUid") == from.uid && it.string("toUid") == to.uid && it.string("status") == "pending"
    }

    private fun kaiAndSam(): Pair<EmulatorFriend, EmulatorFriend> {
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also(people::add)
        signInAs(kai)
        return kai to sam
    }

    @Test
    fun anInviteByCodeCanBeTurnedDownThenAccepted() {
        val (kai, sam) = kaiAndSam()
        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            node(R.string.tab_circle).performClick()
            waitFor(R.string.circle_title)

            // Sam asks by Kai's code; Kai says not now, and nothing changes.
            sam.call("sendInvite", mapOf("friendCode" to friendCodeOf(kai)))
            seeInCircle(hasText(text(R.string.circle_invite_title, "Sam")))
            tapInCircle(hasText(text(R.string.circle_invite_decline)))
            eventually("the invite answered") { !pendingInvite(sam, kai) }
            assertEquals(emptyList<String>(), backendDoc("users/${sam.uid}/private/account")!!.strings("approverIds"))

            // Asked again, Kai says yes: the welcome shows though the invite is gone, and Sam's
            // garden is one Kai looks after.
            sam.call("sendInvite", mapOf("friendCode" to friendCodeOf(kai)))
            tapInCircle(hasText(text(R.string.circle_invite_accept)))
            waitForText(text(R.string.circle_invite_accepted, "Sam"))
            waitForBackend("users/${sam.uid}/private/account") { it.strings("approverIds") == listOf(kai.uid) }
            tapInCircle(hasText(text(R.string.circle_garden_role)) and hasClickAction())
            waitFor(R.string.friend_today)
        }
    }

    @Test
    fun aBumblebeeInvitedByCodeCanBeCancelledRemovedAndKept() {
        val (kai, sam) = kaiAndSam()
        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            node(R.string.tab_circle).performClick()
            waitFor(R.string.circle_title)
            val code = hasSetTextAction() and hasText(text(R.string.invite_code_placeholder))

            // Kai invites Sam by code, then thinks better of it.
            typeInto(code, friendCodeOf(sam))
            node(R.string.invite_code_button).performClick()
            waitFor(R.string.invite_code_sent)
            assertTrue(pendingInvite(kai, sam))
            tapInCircle(hasText(text(R.string.action_cancel)))
            eventually("the invite cancelled") { !pendingInvite(kai, sam) }

            // Invited again, Sam says yes.
            typeInto(code, friendCodeOf(sam))
            node(R.string.invite_code_button).performClick()
            var inviteId = ""
            eventually("the invite reaching Sam") {
                inviteId = await(sam.db.collection("invites").whereEqualTo("toUid", sam.uid).whereEqualTo("status", "pending").limit(1).get())
                    .documents.firstOrNull()?.id.orEmpty()
                inviteId.isNotEmpty()
            }
            sam.call("respondInvite", mapOf("inviteId" to inviteId, "accept" to true))
            waitForBackend("users/${kai.uid}/private/account") { it.strings("approverIds") == listOf(sam.uid) }
            seeInCircle(hasText(text(R.string.circle_bee_role)))

            // Removing Sam takes a day, so it can't dodge a "not today"; until then, Kai can keep Sam.
            tapInCircle(hasContentDescription(text(R.string.action_more)))
            node(R.string.action_remove).performClick()
            inDialog(text(R.string.circle_remove_title, "Sam")).assertExists()
            inDialog(text(R.string.action_remove)).performClick()
            waitForText(text(R.string.circle_bee_removing, "Sam"))
            waitForBackend("users/${kai.uid}/private/account") { it.optJSONObject("pendingRemovals")?.toString()?.contains(sam.uid) == true }
            tapInCircle(hasText(text(R.string.circle_bee_keep)))
            waitForText(text(R.string.circle_bee_kept, "Sam"))
            waitForBackend("users/${kai.uid}/private/account") { it.optJSONObject("pendingRemovals")?.toString()?.contains(sam.uid) != true }
            assertEquals(listOf(sam.uid), backendDoc("users/${kai.uid}/private/account")!!.strings("approverIds"))

            // The chat with Sam is a tap away.
            tapInCircle(hasContentDescription(text(R.string.circle_chat_with, "Sam")))
            waitForText(text(R.string.chat_placeholder))
        }
    }

    @Test
    fun inviteLinksAreSharedAndOnlyFriendsCanUseThem() {
        val (kai, _) = kaiAndSam()
        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            node(R.string.tab_circle).performClick()
            waitFor(R.string.circle_title)

            // Sharing a link makes one and hands it to the share sheet.
            node(R.string.invite_link_button).performClick()
            waitForBackendDocs("inviteLinks") { links -> links.any { it.string("ownerUid") == kai.uid } }
            eventually("the link handed to the share sheet") {
                runCatching { intended(allOf(hasAction(Intent.ACTION_CHOOSER), hasExtra(Intent.EXTRA_INTENT, hasExtra(Intent.EXTRA_TEXT, containsString("/i/"))))) }.isSuccess
            }

            // Pasting something that isn't a link says so.
            val join = hasSetTextAction() and hasText(text(R.string.join_link_placeholder))
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(join)
            typeInto(join, "see you at the park")
            node(R.string.join_link_button).performClick()
            waitFor(R.string.error_bad_invite_link)

            // Kai's own link can't make Kai their own Bumblebee.
            val token = kai.call("createInviteLink")!!["token"] as String
            compose.onNode(hasSetTextAction() and hasText("see you at the park")).performTextReplacement("https://${BuildConfig.LINK_HOST}/i/$token")
            node(R.string.join_link_button).performClick()
            waitFor(R.string.accept_invite_own)
            inDialog(text(R.string.action_ok)).performClick()
            assertTrue(backendDoc("users/${kai.uid}/private/account")!!.strings("approverIds").isEmpty())

            // The friend code can be copied to send another way.
            compose.onNodeWithContentDescription(text(R.string.join_code_copy)).performClick()
            waitFor(R.string.join_code_copied)
        }
    }
}
