package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.limits.ScheduleEditor
import org.junit.Test
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Allowed hours that end at midnight, and a day with several hours. */
class HoursAtMidnightStory : Story("More hours after hours that end at midnight") {
    private fun text(id: Int) = compose.activity.getString(id)
    private fun day(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

    @Test
    fun story() {
        show(firstCaption = "Every day from 10:00 PM to 12:00 AM.") {
            var schedule by remember { mutableStateOf(Schedule.everyDay(TimeWindow(22 * 60, TimeWindow.MINUTES_IN_DAY))) }
            Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                ScheduleEditor(schedule) { schedule = it }
            }
        }
        step("Add hours adds 12:00 AM to 1:00 AM, the free time before them.") {
            compose.onNodeWithText(text(R.string.schedule_add_window)).performClick()
        }
        step("Add hours again adds an hour in the largest free time left.") {
            compose.onNodeWithText(text(R.string.schedule_add_window)).performClick()
        }
        step("Per weekday: each day keeps its whole name, and its hours wrap beside it.") {
            compose.onNodeWithText(text(R.string.schedule_per_day)).performClick()
        }
        step("Saturday alone: the same hours, ready to change for that day.") {
            compose.onNodeWithText(day(DayOfWeek.SATURDAY)).performClick()
        }
    }
}
