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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** Allowed hours by weekday: several days set at once. */
class WeekdayHoursStory : Story("Set the allowed hours of several weekdays at once") {
    private fun text(id: Int) = compose.activity.getString(id)
    private fun day(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

    @Test
    fun story() {
        show(firstCaption = "The same hours every day, 09:00 to 21:00.") {
            var schedule by remember { mutableStateOf(Schedule.everyDay(TimeWindow(9 * 60, 21 * 60))) }
            Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                ScheduleEditor(schedule) { schedule = it }
            }
        }
        step("Per weekday: every day has the same hours, so every day is picked.") {
            compose.onNodeWithText(text(R.string.schedule_per_day)).performClick()
        }
        step("Tapping Saturday's line picks Saturday alone.") {
            compose.onNodeWithText(day(DayOfWeek.SATURDAY)).performClick()
        }
        step("Tapping the Sunday chip picks Sunday too.") {
            compose.onNodeWithContentDescription(day(DayOfWeek.SUNDAY)).performClick()
        }
        step("All day applies to Saturday and Sunday at once. The other days keep 09:00 to 21:00.") {
            compose.onNode(hasText(text(R.string.schedule_all_day_chip)) and isSelectable()).performClick()
        }
        step("Switched off, Saturday and Sunday aren't allowed at all.") {
            compose.onNodeWithText(text(R.string.schedule_days_allowed)).performClick()
        }
    }
}
