package dev.pcoffline.meadow

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.ui.minutesText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, with two people: Sam (played by the test, on their own Firebase instance) invites
 * the person on this phone to be their Bumblebee. On the phone: say yes to the invite, say yes to
 * Sam's request for extra time, see Sam's screen time, stop seeing it once Sam stops sharing,
 * and step down. Each step is checked on both sides.
 */
@RunWith(AndroidJUnit4::class)
class BumblebeeJourneyTest : EndToEndTest() {
    private var sam: EmulatorFriend? = null

    @After
    fun closeSam() {
        sam?.close()
    }

    @Test
    fun aBumblebeeSaysYesLosesSightWhenAskedAndStepsDown() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }
        val today = Days.todayKey()
        sam.call("saveLimit", mapOf("packageName" to "com.instagram.android", "appName" to "Instagram", "dailyMinutes" to 30, "weeklyMinutes" to 0))
        await(
            sam.db.document("users/${sam.uid}/usage/$today").set(
                mapOf(
                    "dayKey" to today,
                    "totalMinutes" to 42,
                    "apps" to listOf(mapOf("packageName" to "com.instagram.android", "appName" to "Instagram", "minutes" to 42, "limitMinutes" to 30)),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ),
        )
        val token = sam.call("createInviteLink")!!["token"] as String

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            val me = FirebaseAuth.getInstance().currentUser!!.uid

            // The invite, pasted into the circle screen (below the fold, so scrolled to first).
            node(R.string.tab_circle).performClick()
            waitFor(R.string.circle_title)
            val joinField = hasSetTextAction() and hasText(text(R.string.join_link_placeholder))
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(joinField)
            typeInto(joinField, "Be my Bumblebee? https://${BuildConfig.LINK_HOST}/i/$token")
            node(R.string.join_link_button).performClick()
            waitForText(text(R.string.accept_invite_title, "Sam"))
            compose.onNode(hasText(text(R.string.accept_invite_yes)) and hasAnyAncestor(isDialog())).performClick()
            waitForBackend("users/${sam.uid}/private/account") { it.strings("approverIds") == listOf(me) }

            // Sam asks for 15 more minutes; the request arrives here, and a yes goes back.
            val requestId = sam.call(
                "createRequest",
                mapOf(
                    "type" to "extra_time", "packageName" to "com.instagram.android", "appName" to "Instagram", "minutes" to 15,
                    "message" to "Finishing a message to my sister", "dayKey" to today, "usedMinutes" to 29, "allowedMinutes" to 30,
                ),
            )!!["requestId"] as String
            node(R.string.tab_requests).performClick()
            waitForText(text(R.string.request_they_ask, "Sam"))
            waitForText("Finishing a message to my sister", substring = true)
            compose.onAllNodesWithText(text(R.string.request_say_yes)).onFirst().performScrollTo().performClick()
            waitForText(text(R.string.request_decide_yes_title, "Sam"))
            compose.onNode(hasText(text(R.string.request_say_yes)) and hasAnyAncestor(isDialog())).performClick()
            waitForBackend("requests/$requestId") { it.getJSONObject("status").getString("stringValue") == "approved" }
            val grant = backendDoc("users/${sam.uid}/grants/$requestId")!!
            assertEquals("15", grant.getJSONObject("minutes").getString("integerValue"))
            assertEquals(me, grant.getJSONObject("grantedBy").getString("stringValue"))
            waitFor(R.string.request_status_yes)

            // Sam's garden: today's screen time, while Sam shares it.
            node(R.string.tab_circle).performClick()
            val samsGarden = hasText("Sam", substring = true) and hasClickAction()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(samsGarden)
            compose.onNode(samsGarden).performClick()
            waitForText(text(R.string.friend_screen_time, app.resources.minutesText(42)))

            // Sam stops sharing. Coming back, nothing is shown: not even this phone's cached copy.
            await(sam.db.document("users/${sam.uid}/private/account").update("privacy", mapOf("shareUsage" to false)))
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(samsGarden)
            compose.onNode(samsGarden).performClick()
            waitForText(text(R.string.friend_no_usage_shared, "Sam"))
            assertEquals(0, compose.onAllNodesWithText(text(R.string.friend_screen_time, app.resources.minutesText(42))).fetchSemanticsNodes().size)

            // Stepping down ends it on Sam's side too.
            compose.onNode(hasText(text(R.string.friend_step_down, "Sam"))).performScrollTo().performClick()
            node(R.string.friend_step_down_confirm).performClick()
            waitForBackend("users/${sam.uid}/private/account") { it.strings("approverIds").isEmpty() }
            waitForBackend("users/$me/private/account") { it.strings("approveeIds").isEmpty() }
        }
    }
}
