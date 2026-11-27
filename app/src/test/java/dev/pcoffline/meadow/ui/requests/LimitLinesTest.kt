package dev.pcoffline.meadow.ui.requests

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** The rows of a limit in a request, and what a change does to each of them. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "en")
class LimitLinesTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<Application>().resources

    init {
        Locale.setDefault(Locale.UK)
    }

    private val tenToFour = TimeWindow(600, 960)
    private val base = AppLimit("com.insta", "Instagram", 30, 180, schedule = Schedule.everyDay(tenToFour))

    /** label | change | was → value */
    private fun rows(new: AppLimit, old: AppLimit?) =
        res.limitLines(new, old).map { "${it.label} | ${it.change} | ${it.was?.let { w -> "$w → " }.orEmpty()}${it.value}" }

    @Test
    fun `without the old limit, each part shows as it is`() {
        assertEquals(
            listOf("Daily | SAME | 30m", "Weekly | SAME | 3h", "Hours | SAME | 10:00–16:00"),
            rows(base, null),
        )
    }

    @Test
    fun `a changed part shows what it was, and a part that stays shows once`() {
        val new = base.copy(dailyMinutes = 45)
        assertEquals(
            listOf("Daily | CHANGED | 30m → 45m", "Weekly | SAME | 3h", "Hours | SAME | 10:00–16:00"),
            rows(new, base),
        )
        val line = res.limitLines(new, base).first()
        assertEquals("Daily: from 30m to 45m", line.description)
        assertEquals("Weekly: 3h, no change", res.limitLines(new, base)[1].description)
    }

    @Test
    fun `a part turned off or on says no limit`() {
        assertEquals(listOf("Daily | CHANGED | 30m → No limit"), rows(AppLimit("a", "A", 0, 0, caps = listOf(cap)), AppLimit("a", "A", 30, 0, caps = listOf(cap))).take(1))
        assertEquals(listOf("Weekly | CHANGED | No limit → 3h"), rows(base, base.copy(weeklyMinutes = 0)).filter { it.startsWith("Weekly") })
    }

    @Test
    fun `hours by weekday show only the days that change, and the rest share one row`() {
        val old = base.copy(schedule = Schedule((1..7).associateWith { if (it == 7) emptyList() else listOf(tenToFour) }))
        val new = base.copy(schedule = Schedule((1..7).associateWith { if (it == 6) listOf(TimeWindow(600, 1200)) else if (it == 7) listOf(TimeWindow.ALL_DAY) else listOf(tenToFour) }))
        assertEquals(
            listOf(
                "Other days | SAME | 10:00–16:00",
                "Hours, Sat | CHANGED | 10:00–16:00 → 10:00–20:00",
                "Hours, Sun | CHANGED | Not allowed → All day",
            ),
            rows(new, old).filter { it.startsWith("Hours") || it.startsWith("Other") },
        )
    }

    @Test
    fun `hours that start or stop show as all day on the other side`() {
        assertEquals(listOf("Hours | CHANGED | 10:00–16:00 → All day"), rows(base.copy(schedule = null), base).filter { it.startsWith("Hours") })
        assertEquals(emptyList<String>(), rows(base.copy(schedule = null), base.copy(schedule = null)).filter { it.startsWith("Hours") })
    }

    @Test
    fun `days with their own daily limit show by day`() {
        val old = base.copy(dayMinutes = mapOf(6 to 60, 7 to 60))
        val new = base.copy(dayMinutes = mapOf(6 to 120, 7 to 60))
        assertEquals(
            listOf("Daily, Sat | CHANGED | 1h → 2h", "Daily, Sun | SAME | 1h"),
            rows(new, old).filter { it.startsWith("Daily,") },
        )
    }

    private val cap = TimeCap(listOf(1, 2, 3, 4, 5), 1080, 1320, 30)

    @Test
    fun `a new cap and a removed one say so in words`() {
        val gone = TimeCap(listOf(7), 480, 720, 60)
        val lines = res.limitLines(base.copy(caps = listOf(cap)), base.copy(caps = listOf(gone)))
        val caps = lines.filter { it.change == LineChange.ADDED || it.change == LineChange.REMOVED }
        assertEquals(listOf(LineChange.ADDED, LineChange.REMOVED), caps.map { it.change })
        assertEquals("New cap: up to 30m 18:00–22:00 on weekdays", caps[0].description)
        assertEquals("Removed cap: up to 1h 08:00–12:00 on Sun", caps[1].description)
        assertEquals(LineChange.SAME, res.limitLines(base.copy(caps = listOf(cap)), base.copy(caps = listOf(cap))).last().change)
    }
}
