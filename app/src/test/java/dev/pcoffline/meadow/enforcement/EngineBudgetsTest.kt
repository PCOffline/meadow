package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.Grant
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.SiteClock
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.usage.AppCatalog
import dev.pcoffline.meadow.usage.UsageHistory
import dev.pcoffline.meadow.usage.UsageTracker
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

/** How the engine works out each app's budget: the week so far, extra time, unlocks, hours and caps. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EngineBudgetsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val today = Days.today()
    private lateinit var store: LocalStore
    private lateinit var engine: LimitEngine

    @Before
    fun setUp() {
        listOf("meadow_store", "meadow_sites").forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        File(app.filesDir, UsageHistory.FILE).delete()
        store = LocalStore(app)
    }

    private fun engine(): LimitEngine {
        val catalog = AppCatalog(app)
        val tracker = UsageTracker(app)
        return LimitEngine(store, tracker, UsageHistory(app, tracker, catalog), catalog, SiteClock(app)).also { engine = it }
    }

    /** [minutes] of [APP] on each day of this week before today, as the phone kept them. */
    private fun earlierThisWeek(minutes: Long) {
        val start = store.weekRule.value.limitStart(today)
        val days = (0 until ChronoUnit.DAYS.between(start, today)).associate { Days.key(start.plusDays(it)) to mapOf(APP to minutes * 60_000L) }
        File(app.filesDir, UsageHistory.FILE).writeText(Json.encodeToString(days))
    }

    private val daysSoFar get() = ChronoUnit.DAYS.between(store.weekRule.value.limitStart(today), today)

    @Test
    fun `the week so far comes from the days the phone kept, and its extra time from this week's grants`() {
        earlierThisWeek(20)
        val lastWeek = Days.key(store.weekRule.value.limitStart(today).minusDays(1))
        store.setGrants(
            listOf(
                Grant("g1", APP, 15, Days.todayKey()),
                Grant("g2", APP, 10, lastWeek),
                Grant("g3", APP, 5, "not a day"),
                Grant("g4", "com.other", 30, Days.todayKey()),
            ),
        )
        store.setLimits(listOf(AppLimit(APP, "Pebbles", 0, 300)))
        val budget = engine().budget(APP, forceRefresh = true)!!
        assertEquals(daysSoFar * 20 * 60_000L, budget.usedWeekMs)
        assertEquals(15, budget.bonusTodayMinutes)
        assertEquals(15, budget.bonusWeekMinutes)
        // Measured once a day: a second look reads the same.
        assertEquals(budget.usedWeekMs, engine.budget(APP)!!.usedWeekMs)
    }

    @Test
    fun `an emergency unlock for another app doesn't open this one`() {
        store.setEmergency(Emergency(System.currentTimeMillis() + 60_000L, "com.other"))
        store.setLimits(listOf(AppLimit(APP, "Pebbles", 30, 0)))
        assertEquals(0L, engine().budget(APP)!!.emergencyUntilMillis)
        store.setEmergency(Emergency(System.currentTimeMillis() + 60_000L, null))
        assertTrue(engine.budget(APP)!!.emergency)
    }

    @Test
    fun `extra time on an app with hours can also be spent outside them`() {
        val never = Schedule((1..7).associateWith { emptyList<TimeWindow>() })
        store.setLimits(listOf(AppLimit(APP, "Pebbles", 30, 0, schedule = never)))
        store.setGrants(listOf(Grant("g1", APP, 20, Days.todayKey())))
        val budget = engine().budget(APP, forceRefresh = true)!!
        assertTrue(budget.outsideHours)
        assertEquals(20 * 60_000L, budget.outsideHoursAllowanceMs)
        assertEquals(20 * 60_000L, budget.remainingMs)
    }

    @Test
    fun `a cap on now counts its own minutes, and a cap on other days doesn't`() {
        val now = (1..7).toList()
        store.setLimits(
            listOf(
                AppLimit(APP, "Pebbles", 60, 0, caps = listOf(TimeCap(now, 0, 1440, 10))),
                AppLimit("com.other", "Other", 60, 0, caps = listOf(TimeCap(listOf(if (today.dayOfWeek == DayOfWeek.MONDAY) 2 else 1), 0, 1440, 10))),
            ),
        )
        val (capped, other) = engine().budgets(forceRefresh = true)
        assertEquals(10 * 60_000L, capped.capRemainingMs)
        assertEquals(10 * 60_000L, capped.remainingMs)
        assertNull(other.capRemainingMs)
    }

    @Test
    fun `the phone and the home screen are never limited, whatever is stored`() {
        store.setLimits(listOf(AppLimit("com.android.systemui", "System", 5, 0), AppLimit(app.packageName, "Meadow", 5, 0)))
        assertNull(engine().limitFor("com.android.systemui"))
        assertNull(engine.budget(app.packageName))
        assertTrue(engine.budgets().isEmpty())
    }

    @Test
    fun `the app in front counts its time since the last measure`() {
        store.setLimits(listOf(AppLimit(APP, "Pebbles", 30, 0)))
        engine().todayUsage(forceRefresh = true)
        engine.setForeground(APP)
        Thread.sleep(50)
        assertTrue((engine.todayUsage()[APP] ?: 0L) > 0L)
        assertTrue(engine.budget(APP)!!.usedTodayMs > 0L)
        engine.setForeground(APP)
        engine.setForeground(null)
        assertEquals(null, engine.todayUsage()[APP])
    }

    private companion object {
        const val APP = "com.example.pebbles"
    }
}
