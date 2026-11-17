package dev.pcoffline.meadow.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.limits.CapsEditor
import dev.pcoffline.meadow.ui.limits.DayGroup
import dev.pcoffline.meadow.ui.limits.DayLimitsEditor
import dev.pcoffline.meadow.ui.limits.ScheduleEditor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/**
 * While a limit saves, its editors are locked, and they open again after. A time is changed on a
 * clock, and the change is kept only when it's confirmed.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorLockTest : ScreenTest() {
    private var enabled by mutableStateOf(true)

    private fun inDialog(id: Int) = compose.onNode(hasText(text(id)) and hasAnyAncestor(isDialog()))

    private fun clockOk() = compose.onAllNodes(hasText(text(R.string.action_ok)) and hasAnyAncestor(isDialog()))[1]

    @Test
    fun `day limits and caps lock while saving, and open again after`() {
        var groups by mutableStateOf(listOf(DayGroup(listOf(6, 7), 120), DayGroup(listOf(3), 0)))
        var caps by mutableStateOf(listOf(TimeCap(listOf(1, 2, 3, 4, 5), 20 * 60, 23 * 60, 30)))
        showScrolling {
            Column {
                DayLimitsEditor(groups, DayOfWeek.MONDAY, enabled) { groups = it }
                CapsEditor(caps, DayOfWeek.MONDAY, enabled) { caps = it }
            }
        }
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_days_remove)).onFirst().assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.limit_editor_cap_remove)).assertIsEnabled()

        enabled = false
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_days_remove)).onFirst().assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.limit_editor_cap_remove)).assertIsNotEnabled()
        node(R.string.limit_editor_days_add).assertIsNotEnabled()
        node(R.string.limit_editor_cap_add).assertIsNotEnabled()
        compose.onAllNodes(hasText(text(R.string.limit_editor_days_no_limit))).onFirst().assertIsNotEnabled()

        enabled = true
        // A day with no limit gets one back, an hour.
        compose.onNode(hasText(text(R.string.limit_editor_days_no_limit)) and isSelected()).performClick()
        assertEquals(listOf(DayGroup(listOf(6, 7), 120), DayGroup(listOf(3), 60)), groups)
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_days_remove)).onFirst().performClick()
        assertEquals(listOf(DayGroup(listOf(3), 60)), groups)
        compose.onNodeWithContentDescription(text(R.string.limit_editor_cap_remove)).performClick()
        assertEquals(emptyList<TimeCap>(), caps)
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `allowed hours lock while saving, every day and per weekday`() {
        var schedule by mutableStateOf(Schedule.everyDay(TimeWindow(10 * 60, 16 * 60), TimeWindow(18 * 60, 20 * 60)))
        showScrolling { ScheduleEditor(schedule, enabled = enabled) { schedule = it } }
        compose.onAllNodesWithContentDescription(text(R.string.schedule_remove_window)).onFirst().assertIsEnabled()

        enabled = false
        compose.onAllNodesWithContentDescription(text(R.string.schedule_remove_window)).onFirst().assertIsNotEnabled()
        node(R.string.schedule_add_window).assertIsNotEnabled()

        enabled = true
        schedule = Schedule((1..7).associateWith { if (it <= 5) listOf(TimeWindow(9 * 60, 17 * 60)) else emptyList() })
        enabled = false
        node(R.string.schedule_per_day).assertExists()
        enabled = true
        node(R.string.schedule_per_day).assertIsEnabled()
    }

    @Test
    fun `a time changed on the clock is kept when confirmed, and left when cancelled`() {
        var schedule by mutableStateOf(Schedule.everyDay(TimeWindow(10 * 60, 24 * 60)))
        showScrolling { ScheduleEditor(schedule) { schedule = it } }

        compose.onNodeWithContentDescription(text(R.string.schedule_starts, clockText(10 * 60))).performClick()
        inDialog(R.string.action_cancel).performClick()
        inDialog(R.string.action_ok).assertDoesNotExist()

        compose.onNodeWithContentDescription(text(R.string.schedule_starts, clockText(10 * 60))).performClick()
        inDialog(R.string.action_ok).performClick()
        assertEquals(Schedule.everyDay(TimeWindow(10 * 60, 24 * 60)), schedule)

        // Midnight as an end is the end of the day.
        compose.onNodeWithContentDescription(text(R.string.schedule_ends, clockText(0))).performClick()
        inDialog(R.string.action_ok).performClick()
        assertEquals(Schedule.everyDay(TimeWindow(10 * 60, 24 * 60)), schedule)
        inDialog(R.string.action_ok).assertDoesNotExist()
    }

    @Test
    fun `a cap's hours are picked on the clock, and a cap with them saves`() {
        var caps by mutableStateOf(emptyList<TimeCap>())
        showScrolling { CapsEditor(caps, DayOfWeek.MONDAY, enabled = true) { caps = it } }
        node(R.string.limit_editor_cap_add).performClick()
        inDialog(R.string.cap_template_weekdays).performClick()
        // The clock opens over the cap: its OK is the second one. It starts where caps usually start.
        compose.onNodeWithContentDescription(text(R.string.cap_pick_start)).performClick()
        clockOk().performClick()
        compose.onNodeWithContentDescription(text(R.string.cap_pick_end)).performClick()
        clockOk().performClick()
        inDialog(R.string.action_ok).performClick()
        assertEquals(1, caps.size)
        assertEquals(60, caps.single().to - caps.single().from)
    }
}
