package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
    fun `a Bumblebee sees who asks, why, how the day stood, and can answer it`() {
        show { RequestCard(request(), myUid = "bee") }
        node(R.string.request_they_ask, "Sam").assertExists()
        compose.onNodeWithText("Finishing a message to my sister", substring = true).assertExists()
        compose.onNodeWithText(text(R.string.request_status_waiting)).assertExists()
        compose.onNodeWithText(text(R.string.request_usage, app.resources.minutesText(29), app.resources.minutesText(30))).assertExists()
        node(R.string.request_withdraw).assertDoesNotExist()
        assertAccessible(requireHeading = false)

        // Answering opens a dialog, tested on a phone (DialogContrastTest): Robolectric never settles with it open.
        node(R.string.request_say_yes).assertHasClickAction()
        node(R.string.request_not_today).assertHasClickAction()
    }

    @Test
    fun `an ask nobody answered in time can be asked again, by the asker only`() {
        var mine by mutableStateOf(true)
        var status by mutableStateOf(RequestStatus.EXPIRED)
        show { RequestCard(request(status = status), myUid = if (mine) "sam" else "bee") }
        node(R.string.request_ask_again).assertHasClickAction()
        assertAccessible(requireHeading = false)
        mine = false
        node(R.string.request_ask_again).assertDoesNotExist()
        mine = true
        status = RequestStatus.PENDING
        node(R.string.request_ask_again).assertDoesNotExist()
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
        // "Not today" is still its status, but no longer a button.
        compose.onNode(hasText(text(R.string.request_not_today)) and hasClickAction()).assertDoesNotExist()
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

    private fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

    private fun daySwitch(day: DayOfWeek) = compose.onNodeWithContentDescription(text(R.string.schedule_day_allowed, dayName(day)))

    @Test
    fun `the same hours every day - presets, adding and removing hours`() {
        var schedule by mutableStateOf(Schedule.everyDay(TimeWindow.ALL_DAY))
        showScrolling { ScheduleEditor(schedule) { schedule = it } }
        compose.onNodeWithText(text(R.string.schedule_every_day)).assertIsSelected()

        // "10:00–16:00" every day, shown as a start and an end to change.
        compose.onNodeWithText(text(R.string.schedule_window, clockText(10 * 60), clockText(16 * 60))).performClick()
        assertEquals(Schedule.everyDay(TimeWindow(10 * 60, 16 * 60)), schedule)
        compose.onNodeWithContentDescription(text(R.string.schedule_starts, clockText(10 * 60))).assertHasClickAction()
        compose.onNodeWithContentDescription(text(R.string.schedule_ends, clockText(16 * 60))).assertHasClickAction()
        // A single window can't be removed: that would leave no hours at all.
        compose.onAllNodesWithContentDescription(text(R.string.schedule_remove_window)).assertCountEquals(0)

        // Another hour of use, added after the last one.
        node(R.string.schedule_add_window).performClick()
        assertEquals(listOf(TimeWindow(10 * 60, 16 * 60), TimeWindow(17 * 60, 18 * 60)), schedule.windowsOn(3))
        assertTrue(schedule.sameEveryDay)

        // Removing the first window.
        compose.onAllNodesWithContentDescription(text(R.string.schedule_remove_window)).onFirst().performClick()
        assertEquals(Schedule.everyDay(TimeWindow(17 * 60, 18 * 60)), schedule)
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `per weekday, a day is switched off and on, leaving the others as they were`() {
        var schedule by mutableStateOf(Schedule.everyDay(TimeWindow(9 * 60, 21 * 60)))
        showScrolling { ScheduleEditor(schedule) { schedule = it } }
        node(R.string.schedule_per_day).performClick()

        daySwitch(DayOfWeek.WEDNESDAY).assertIsOn().performClick()
        assertEquals(emptyList<TimeWindow>(), schedule.windowsOn(3))
        (1..7).filter { it != 3 }.forEach { assertEquals(listOf(TimeWindow(9 * 60, 21 * 60)), schedule.windowsOn(it)) }
        node(R.string.schedule_day_off).assertExists()

        // On again, it takes the hours the other days have.
        daySwitch(DayOfWeek.WEDNESDAY).assertIsOff().performClick()
        assertEquals(Schedule.everyDay(TimeWindow(9 * 60, 21 * 60)).windowsOn(3), schedule.windowsOn(3))
        node(R.string.schedule_day_off).assertDoesNotExist()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `per weekday, the days start on the chosen first day of the week`() {
        showScrolling { ScheduleEditor(Schedule.everyDay(TimeWindow.ALL_DAY), firstDay = DayOfWeek.SATURDAY) {} }
        node(R.string.schedule_per_day).performClick()
        val tops = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
            .map { daySwitch(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `back to every day, all days take the first open day's hours`() {
        var schedule by mutableStateOf(
            Schedule((1..7).associateWith { if (it == 1) emptyList() else listOf(TimeWindow(8 * 60, 20 * 60)) }),
        )
        showScrolling { ScheduleEditor(schedule) { schedule = it } }
        compose.onNodeWithText(text(R.string.schedule_per_day)).assertIsSelected()
        node(R.string.schedule_every_day).performClick()
        assertEquals(Schedule.everyDay(TimeWindow(8 * 60, 20 * 60)), schedule)
    }

    @Test
    fun `while saving, nothing in the hours can be changed`() {
        showScrolling { ScheduleEditor(Schedule.everyDay(TimeWindow(9 * 60, 17 * 60)), enabled = false) {} }
        val actionable = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(actionable.isNotEmpty())
        actionable.forEach { assertTrue(SemanticsProperties.Disabled in it.config) }
    }

    @Test
    fun `a schedule with no hours at all opens day by day, every day not allowed`() {
        showScrolling { ScheduleEditor(Schedule.everyDay()) {} }
        compose.onNodeWithText(text(R.string.schedule_per_day)).assertIsSelected()
        compose.onAllNodesWithText(text(R.string.schedule_day_off)).assertCountEquals(7)
    }

    @Test
    fun `per weekday, a day can be allowed all day in one tap, and back to set hours`() {
        var schedule by mutableStateOf(Schedule.everyDay(TimeWindow(9 * 60, 17 * 60)))
        showScrolling { ScheduleEditor(schedule) { schedule = it } }
        node(R.string.schedule_per_day).performClick()
        val allDay = "${text(R.string.schedule_all_day_chip)}, ${dayName(DayOfWeek.WEDNESDAY)}"
        compose.onNodeWithContentDescription(allDay).assertIsNotSelected().performClick()
        assertEquals(listOf(TimeWindow.ALL_DAY), schedule.windowsOn(3))
        assertEquals(listOf(TimeWindow(9 * 60, 17 * 60)), schedule.windowsOn(4))
        // Back to set hours: the hours the other days have.
        compose.onNodeWithContentDescription(allDay).assertIsSelected().performClick()
        assertEquals(listOf(TimeWindow(9 * 60, 17 * 60)), schedule.windowsOn(3))
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `per weekday, one day's hours are copied to several days at once`() {
        var schedule by mutableStateOf(Schedule((1..7).associateWith { if (it == 1) listOf(TimeWindow(9 * 60, 12 * 60)) else listOf(TimeWindow(8 * 60, 20 * 60)) }))
        showScrolling { ScheduleEditor(schedule) { schedule = it } }
        compose.onNodeWithContentDescription(text(R.string.schedule_copy_description, dayName(DayOfWeek.MONDAY))).performClick()
        compose.onNodeWithText(text(R.string.schedule_copy_title, dayName(DayOfWeek.MONDAY))).assertExists()
        // Monday is the one being copied, so it can't be picked.
        compose.onNodeWithContentDescription(dayName(DayOfWeek.MONDAY)).assertIsNotEnabled()
        node(R.string.action_ok).assertIsNotEnabled()
        compose.onNodeWithContentDescription(dayName(DayOfWeek.SATURDAY)).performClick()
        compose.onNodeWithContentDescription(dayName(DayOfWeek.SUNDAY)).performClick()
        node(R.string.action_ok).performClick()
        assertEquals(listOf(TimeWindow(9 * 60, 12 * 60)), schedule.windowsOn(6))
        assertEquals(listOf(TimeWindow(9 * 60, 12 * 60)), schedule.windowsOn(7))
        assertEquals(listOf(TimeWindow(8 * 60, 20 * 60)), schedule.windowsOn(5))
    }

    @Test
    fun `copying hours changes only the days picked, and a day off has nothing to copy`() {
        val morning = listOf(TimeWindow(9 * 60, 12 * 60))
        val long = listOf(TimeWindow(8 * 60, 20 * 60))
        var schedule by mutableStateOf(Schedule((1..7).associateWith { if (it <= 2) morning else if (it == 7) emptyList() else long }))
        showScrolling { ScheduleEditor(schedule) { schedule = it } }
        compose.onNodeWithContentDescription(text(R.string.schedule_copy_description, dayName(DayOfWeek.SUNDAY))).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.schedule_copy_description, dayName(DayOfWeek.MONDAY))).performClick()
        // Tuesday has these hours already: it isn't picked, and can't be.
        compose.onNodeWithContentDescription(dayName(DayOfWeek.TUESDAY)).assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithContentDescription(dayName(DayOfWeek.WEDNESDAY)).assertIsNotSelected()
        compose.onNodeWithContentDescription(dayName(DayOfWeek.FRIDAY)).performClick()
        node(R.string.action_ok).performClick()
        assertEquals(morning, schedule.windowsOn(5))
        assertEquals(morning, schedule.windowsOn(2))
        assertEquals(long, schedule.windowsOn(3))
        assertEquals(emptyList<TimeWindow>(), schedule.windowsOn(7))
    }
}
