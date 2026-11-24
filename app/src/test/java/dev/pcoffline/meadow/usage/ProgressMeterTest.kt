package dev.pcoffline.meadow.usage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Grant
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.Progress
import dev.pcoffline.meadow.data.WeekRule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Progress read from the usage this phone kept: this week against last, calmer weeks, and days within limits. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ProgressMeterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = Days.today()
    private lateinit var store: LocalStore
    private val min = 60_000L

    @Before
    fun clean() {
        File(context.filesDir, UsageHistory.FILE).delete()
        context.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = LocalStore(context)
    }

    /** Keeps [minutes] of "a" for each finished day from [days] days ago, as the phone would have. */
    private fun recorded(days: Int, minutes: (LocalDate) -> Long) {
        val usage = (1..days).associate { back ->
            val day = today.minusDays(back.toLong())
            Days.key(day) to mapOf("a" to minutes(day) * min)
        }
        File(context.filesDir, UsageHistory.FILE).writeText(Json.encodeToString(usage))
    }

    private fun meter() = ProgressMeter(UsageHistory(context, UsageTracker(context), AppCatalog(context)), store)

    /** Whole weeks between the start of [day]'s week and the start of this week. */
    private fun weeksBack(day: LocalDate): Long {
        val rule = store.weekRule.value
        return ChronoUnit.WEEKS.between(rule.start(day), rule.start(today))
    }

    /** 30 minutes a day this week, an hour last week, and 10 minutes more for each week before. */
    private fun calmerEachWeek(day: LocalDate): Long = when (val back = weeksBack(day)) {
        0L -> 30
        else -> 60 + 10 * (back - 1)
    }

    /** Finished days of this week so far, with weeks from Monday as a new install has them. */
    private val daysThisWeek = ChronoUnit.DAYS.between(WeekRule().start(today), today).toInt()

    @Test
    fun `less each week reads as calmer weeks, and this week against the same days of last week`() {
        recorded(110, ::calmerEachWeek)
        val progress = meter().now(today, elapsedMs = 0)
        assertEquals(Progress.WEEKS_BACK, progress.calmerWeeks)
        // Today has nothing yet (no usage access), so this week is its finished days: half of last week's.
        assertEquals(if (daysThisWeek == 0) null else -50, progress.weekChange)
        // Without limits, no day can be judged.
        assertEquals(0, progress.limitDays)
        assertEquals(0, progress.keptDays)
    }

    @Test
    fun `a week with a day missing ends the calmer run and can't be compared`() {
        // The first day of the week three weeks back has no record at all.
        val missing = store.weekRule.value.start(today).minusWeeks(3)
        recorded(110, ::calmerEachWeek)
        val history = File(context.filesDir, UsageHistory.FILE)
        history.writeText(history.readText().replace(Regex("\"${Days.key(missing)}\":\\{[^}]*\\},?"), ""))
        // Last week was calmer than the week before; that one can't be compared with the week it lacks a day of.
        assertEquals(1, meter().now(today, elapsedMs = 0).calmerWeeks)
    }

    @Test
    fun `days within every daily limit are counted, with the minutes a Bumblebee gave`() {
        recorded(10, ::calmerEachWeek)
        store.setLimits(listOf(AppLimit("a", "A", dailyMinutes = 45, weeklyMinutes = 0)))
        // A day of last week, an hour long, was within its limit with 15 more minutes.
        val given = today.minusDays(daysThisWeek + 1L)
        store.setGrants(listOf(Grant("g", "a", 15, Days.key(given))))
        val progress = meter().now(today, elapsedMs = 0)
        assertEquals(Progress.KEPT_WINDOW, progress.limitDays)
        // This week's days (30 minutes) and the day with the Bumblebee's minutes; last week's other days were over.
        assertEquals(daysThisWeek + 1, progress.keptDays)
    }
}
