package dev.pcoffline.meadow

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        // Sam shares everything, progress included (it waits to be chosen), each part as Sam's
        // phone shares it, in a document of its own.
        await(
            sam.db.document("users/${sam.uid}/private/account")
                .update("privacy", mapOf("shareLimits" to true, "shareTime" to true, "shareApps" to true, "shareProgress" to true)),
        )
        await(
            sam.db.document("users/${sam.uid}/usage/$today")
                .set(mapOf("dayKey" to today, "totalMinutes" to 42, "updatedAt" to FieldValue.serverTimestamp())),
        )
        await(
            sam.db.document("users/${sam.uid}/appUsage/$today").set(
                mapOf(
                    "dayKey" to today,
                    "apps" to listOf(mapOf("packageName" to "com.instagram.android", "appName" to "Instagram", "minutes" to 42, "limitMinutes" to 30)),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ),
        )
        await(
            sam.db.document("users/${sam.uid}/progress/current").set(
                mapOf("weekChange" to -20, "calmerWeeks" to 2, "keptDays" to 6, "limitDays" to 7, "updatedAt" to FieldValue.serverTimestamp()),
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            val me = acceptInviteFrom(sam, "Sam")

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

            // Sam's garden: each part Sam shares.
            node(R.string.tab_circle).performClick()
            openGardenOf("Sam")
            waitForText(text(R.string.friend_screen_time, app.resources.minutesText(42)))
            waitForText(text(R.string.progress_kept, 6, 7))
            waitForText(text(R.string.friend_limits))

            // Sam keeps today's time and progress private, then limits too. Coming back each time,
            // only what's still shared is shown: not even this phone's cached copy of the rest.
            val privacy = "users/${sam.uid}/private/account"
            await(sam.db.document(privacy).update("privacy", mapOf("shareLimits" to true, "shareTime" to false, "shareApps" to false, "shareProgress" to false)))
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            openGardenOf("Sam")
            waitForText(text(R.string.friend_limits))
            eventually("today's time and progress gone") {
                !showing(text(R.string.friend_screen_time, app.resources.minutesText(42))) && !showing(text(R.string.progress_title))
            }
            await(sam.db.document(privacy).update("privacy", mapOf("shareLimits" to false, "shareTime" to false, "shareApps" to false, "shareProgress" to false)))
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            openGardenOf("Sam")
            waitForText(text(R.string.friend_step_down, "Sam"))
            eventually("Sam's page read") { !showing(text(R.string.loading_garden)) }
            assertEquals(0, compose.onAllNodesWithText(text(R.string.friend_limits)).fetchSemanticsNodes().size)

            // Stepping down ends it on Sam's side too, and goes back to the circle, saying so.
            compose.onNode(hasText(text(R.string.friend_step_down, "Sam"))).performScrollTo().performClick()
            node(R.string.friend_step_down_confirm).performClick()
            waitFor(R.string.friend_stepped_down)
            // Sam's garden is gone from the circle.
            eventually("Sam's garden gone") { !showing(text(R.string.circle_garden_role)) }
            waitForBackend("users/${sam.uid}/private/account") { it.strings("approverIds").isEmpty() }
            waitForBackend("users/$me/private/account") { it.strings("approveeIds").isEmpty() }
        }
    }
}
