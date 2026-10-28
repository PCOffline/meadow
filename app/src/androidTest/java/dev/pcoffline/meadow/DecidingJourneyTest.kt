package dev.pcoffline.meadow

import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.messaging.RemoteMessage
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.notifications.MeadowMessagingService
import dev.pcoffline.meadow.ui.minutesText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, from the Bumblebee's side: Sam asks this phone's Bee three times. Bee gives fewer
 * minutes than asked, says "not today" with a kind word, and answers the third straight from its
 * notification, which also opens the requests. Bee's own tally of answers adds them up.
 */
@RunWith(AndroidJUnit4::class)
class DecidingJourneyTest : EndToEndTest() {
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private var sam: EmulatorFriend? = null

    @Before
    fun allowNotifications() {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    @After
    fun closeSam() {
        sam?.close()
    }

    private fun EmulatorFriend.ask(appName: String, packageName: String, minutes: Int, message: String): String =
        call(
            "createRequest",
            mapOf(
                "type" to "extra_time", "packageName" to packageName, "appName" to appName, "minutes" to minutes,
                "message" to message, "dayKey" to Days.todayKey(), "usedMinutes" to 30, "allowedMinutes" to 30,
            ),
        )!!["requestId"] as String

    @Test
    fun aBumblebeeGivesLessSaysNotTodayAndAnswersFromANotification() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }
        for ((pkg, name) in listOf("com.instagram.android" to "Instagram", "com.google.android.youtube" to "YouTube", "com.whatsapp" to "WhatsApp")) {
            sam.call("saveLimit", mapOf("packageName" to pkg, "appName" to name, "dailyMinutes" to 30, "weeklyMinutes" to 0))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            val me = acceptInviteFrom(sam, "Sam")

            // 30 minutes asked; Bee gives 10, with a note.
            val first = sam.ask("Instagram", "com.instagram.android", 30, "Homework research")
            tab(R.string.tab_people).performClick()
            waitForText("Homework research", substring = true)
            compose.onAllNodesWithText(text(R.string.request_say_yes)).onFirst().performScrollTo().performClick()
            waitForText(text(R.string.request_decide_yes_title, "Sam"))
            compose.onNode(hasText(app.resources.minutesText(10)) and hasAnyAncestor(isDialog())).performClick()
            typeIntoDialog(hasSetTextAction(), "Ten is plenty")
            inDialog(text(R.string.request_say_yes)).performClick()
            waitFor(R.string.request_sent_yes)
            waitForBackend("requests/$first") { it.string("status") == "approved" && it.int("minutes") == 10 }
            assertEquals("Ten is plenty", backendDoc("requests/$first")!!.string("decisionNote"))
            assertEquals(10, backendDoc("users/${sam.uid}/grants/$first")!!.int("minutes"))
            // Answered, it lives on in the chat with Sam.
            openChatWith("Sam")
            waitFor(R.string.request_you_said_yes)
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Not today, with a kind word; no time is given.
            val second = sam.ask("YouTube", "com.google.android.youtube", 15, "One more video")
            waitForText("One more video", substring = true)
            compose.onAllNodesWithText(text(R.string.request_not_today)).onFirst().performScrollTo().performClick()
            waitFor(R.string.request_decide_no_title)
            typeIntoDialog(hasSetTextAction(), "Bedtime")
            inDialog(text(R.string.action_send)).performClick()
            waitFor(R.string.request_sent_no)
            waitForBackend("requests/$second") { it.string("status") == "denied" && it.string("decisionNote") == "Bedtime" }
            assertEquals(null, backendDoc("users/${sam.uid}/grants/$second"))

            // The third arrives as a notification: tapping it opens People, and its
            // "Say yes" answers without opening Meadow at all.
            val third = sam.ask("WhatsApp", "com.whatsapp", 5, "Replying to my coach")
            tab(R.string.tab_garden).performClick()
            MeadowMessagingService().onMessageReceived(
                RemoteMessage.Builder("meadow@fcm.googleapis.com")
                    .setData(mapOf("type" to "request_new", "to" to me, "requestId" to third, "title" to "Sam", "body" to "Replying to my coach"))
                    .build(),
            )
            var posted: android.app.Notification? = null
            eventually("the request's notification") {
                posted = notifications.activeNotifications.firstOrNull { it.id == third.hashCode() }?.notification
                posted != null
            }
            posted!!.contentIntent.send()
            waitFor(R.string.people_waiting)
            posted!!.actions.single { it.title.toString() == text(R.string.request_say_yes) }.actionIntent.send()
            waitForBackend("requests/$third") { it.string("status") == "approved" }
            assertEquals(5, backendDoc("users/${sam.uid}/grants/$third")!!.int("minutes"))

            // Bee's tally: three asks from Sam, two yeses, one not today.
            node(R.string.bee_stats_open).performScrollTo().performClick()
            waitFor(R.string.bee_stats_title)
            waitForText(text(R.string.bee_stats_person_line, 3, 2, 1))
        }
    }
}
