package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.limits.ScheduleEditor
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Asking, answering and editing: request cards and the allowed-hours editor. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorsTest : ScreenTest() {
    private fun request(
        requesterId: String = "sam",
        status: RequestStatus = RequestStatus.PENDING,
        decidedByName: String? = null,
        decisionNote: String? = null,
        myDecision: String? = null,
    ) = LimitRequest(
        id = "r1", requesterId = requesterId, requesterName = "Sam", type = RequestType.EXTRA_TIME,
        packageName = "com.instagram.android", appName = "Instagram", minutes = 30, dailyMinutes = 0, weeklyMinutes = 0,
        message = "Finishing a message to my sister", status = status, approverIds = listOf("bee", "robin"),
        decidedByName = decidedByName, decisionNote = decisionNote, dayKey = "2026-10-13",
        createdAtMillis = System.currentTimeMillis() - 60_000, myDecision = myDecision, usedMinutes = 29, allowedMinutes = 30,
    )

    private fun countText(value: String, substring: Boolean = false) =
        compose.onAllNodesWithText(value, substring).fetchSemanticsNodes().size

    @Test
    fun `a Bumblebee sees who asks, why, how the day stood, and can answer`() {
        show { RequestCard(request(), myUid = "bee") }
        node(R.string.request_they_ask, "Sam").assertExists()
        compose.onNodeWithText("Finishing a message to my sister", substring = true).assertExists()
        compose.onNodeWithText(text(R.string.request_status_waiting)).assertExists()
        compose.onNodeWithText(text(R.string.request_usage, app.resources.minutesText(29), app.resources.minutesText(30))).assertExists()
        node(R.string.request_withdraw).assertDoesNotExist()
        assertAccessible(requireHeading = false)

        // Saying yes asks for confirmation, and offers to give a little less.
        node(R.string.request_say_yes).performClick()
        node(R.string.request_decide_yes_title, "Sam").assertExists()
        node(R.string.request_decide_less).assertExists()
        compose.onNode(hasText(text(R.string.request_say_yes)) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText(text(R.string.action_back)) and hasAnyAncestor(isDialog())).performClick()
        node(R.string.request_decide_yes_title, "Sam").assertDoesNotExist()

        // "Not today" has no minutes to choose, only an optional kind word.
        node(R.string.request_not_today).performClick()
        node(R.string.request_decide_no_title).assertExists()
        node(R.string.request_decide_less).assertDoesNotExist()
        node(R.string.request_decide_note).assertExists()
    }

    @Test
    fun `a Bumblebee never learns which other Bumblebee answered`() {
        // The backend may know who decided; another Bumblebee's card must not show it.
        show { RequestCard(request(status = RequestStatus.APPROVED, decidedByName = "Robin", decisionNote = "Go for it"), myUid = "bee") }
        node(R.string.request_answered_by_other).assertExists()
        assertEquals(0, countText("Robin", substring = true))
        assertEquals(0, countText("Go for it", substring = true))
        node(R.string.request_say_yes).assertDoesNotExist()
    }

    @Test
    fun `a Bumblebee who answered sees their own answer`() {
        show { RequestCard(request(status = RequestStatus.DENIED, decidedByName = "Bee", myDecision = "no"), myUid = "bee") }
        node(R.string.request_you_said_no).assertExists()
        node(R.string.request_not_today).assertDoesNotExist()
    }

    @Test
    fun `the asker sees who answered and their note, and can withdraw while waiting`() {
        show { RequestCard(request(status = RequestStatus.APPROVED, decidedByName = "Bee", decisionNote = "Enjoy"), myUid = "sam") }
        node(R.string.request_you_asked).assertExists()
        compose.onNodeWithText(text(R.string.request_decided_with_note, text(R.string.request_decided_yes, "Bee"), "Enjoy")).assertExists()
        node(R.string.request_withdraw).assertDoesNotExist()
        node(R.string.request_say_yes).assertDoesNotExist()
    }

    @Test
    fun `a waiting request of your own can be withdrawn, not answered`() {
        show { RequestCard(request(), myUid = "sam") }
        node(R.string.request_withdraw).assertExists()
        node(R.string.request_say_yes).assertDoesNotExist()
        node(R.string.request_not_today).assertDoesNotExist()
    }

    @Test
    fun `the same hours every day - presets, adding hours and resting all day`() {
        var schedule by mutableStateOf(Schedule.everyDay(TimeWindow.ALL_DAY))
        show { ScheduleEditor(schedule) { schedule = it } }
        compose.onNodeWithText(text(R.string.schedule_every_day)).assertIsSelected()

        // "10:00–16:00" every day.
        compose.onNodeWithText(text(R.string.schedule_window, clockText(10 * 60), clockText(16 * 60))).performClick()
        assertEquals(Schedule.everyDay(TimeWindow(10 * 60, 16 * 60)), schedule)

        // Another hour of use, added after the last one.
        node(R.string.schedule_add_window).performClick()
        assertEquals(listOf(TimeWindow(10 * 60, 16 * 60), TimeWindow(17 * 60, 18 * 60)), schedule.windowsOn(3))
        assertTrue(schedule.sameEveryDay)

        // Removing the first window, then resting all day.
        compose.onAllNodesWithContentDescription(text(R.string.schedule_remove_window)).onFirst().performClick()
        assertEquals(listOf(TimeWindow(17 * 60, 18 * 60)), schedule.windowsOn(1))
        node(R.string.schedule_close_day).performClick()
        assertEquals(emptyList<TimeWindow>(), schedule.windowsOn(7))
        node(R.string.schedule_closed).assertExists()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `per weekday, changing one day leaves the others as they were`() {
        var schedule by mutableStateOf(Schedule.everyDay(TimeWindow(9 * 60, 21 * 60)))
        show { ScheduleEditor(schedule) { schedule = it } }
        node(R.string.schedule_per_day).performClick()
        // Monday is picked first; rest all day on Monday only.
        node(R.string.schedule_close_day).performClick()
        assertEquals(emptyList<TimeWindow>(), schedule.windowsOn(1))
        (2..7).forEach { assertEquals(listOf(TimeWindow(9 * 60, 21 * 60)), schedule.windowsOn(it)) }
        assertTrue(!schedule.sameEveryDay)
        assertAccessible(requireHeading = false)
    }
}
