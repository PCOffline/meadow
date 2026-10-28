package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.SiteClock
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.usage.AppCatalog
import dev.pcoffline.meadow.usage.UsageHistory
import dev.pcoffline.meadow.usage.UsageTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime

/** A website's budget comes from the time measured on it, and only counts toward its own limit. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SiteBudgetTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val site = "site:youtube.com"
    private lateinit var store: LocalStore
    private lateinit var engine: LimitEngine
    private var clock = 0L
    private lateinit var sites: SiteClock

    @Before
    fun setUp() {
        listOf("meadow_store", "meadow_sites").forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        store = LocalStore(app)
        sites = SiteClock(app) { clock }
        val catalog = AppCatalog(app)
        val tracker = UsageTracker(app)
        engine = LimitEngine(store, tracker, UsageHistory(app, tracker, catalog), catalog, sites)
    }

    /** [minutes] on the site, ending a moment ago. */
    private fun watched(minutes: Int) {
        clock = System.currentTimeMillis() - minutes * 60_000L - 1_000
        sites.seen(site)
        repeat(minutes * 6) {
            clock += 10_000
            sites.seen(site)
        }
        sites.left()
    }

    @Test
    fun `a site's time counts toward its limit, and it rests when that's used up`() {
        // Just after midnight there may not be two minutes of today yet.
        org.junit.Assume.assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 5)))
        store.setLimits(listOf(AppLimit(site, "youtube.com", dailyMinutes = 2, weeklyMinutes = 0)))
        watched(1)
        val half = engine.budget(site, forceRefresh = true)
        assertNotNull(half)
        assertEquals(60_000L, half!!.usedTodayMs)
        assertFalse(half.exhausted)
        watched(1)
        val spent = engine.budget(site, forceRefresh = true)!!
        assertEquals(120_000L, spent.usedTodayMs)
        assertTrue(spent.exhausted)
    }

    @Test
    fun `a week's limit counts today's time on the site too`() {
        org.junit.Assume.assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 5)))
        store.setLimits(listOf(AppLimit(site, "youtube.com", dailyMinutes = 0, weeklyMinutes = 3)))
        watched(2)
        val budget = engine.budget(site, forceRefresh = true)!!
        assertEquals(120_000L, budget.usedWeekMs)
        assertEquals(60_000L, budget.remainingMs)
    }

    @Test
    fun `a cap within hours counts the site's time in its hours`() {
        org.junit.Assume.assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 5)) && LocalTime.now().isBefore(LocalTime.of(23, 50)))
        val today = java.time.LocalDate.now().dayOfWeek.value
        val cap = TimeCap(listOf(today), 0, 24 * 60, 1)
        store.setLimits(listOf(AppLimit(site, "youtube.com", dailyMinutes = 0, weeklyMinutes = 0, caps = listOf(cap))))
        watched(1)
        val budget = engine.budget(site, forceRefresh = true)!!
        assertEquals(cap, budget.cap)
        assertTrue(budget.exhausted)
    }

    @Test
    fun `an app's limit doesn't count time on websites`() {
        store.setLimits(listOf(AppLimit("com.android.chrome", "Chrome", dailyMinutes = 1, weeklyMinutes = 0)))
        watched(2)
        assertEquals(0L, engine.budget("com.android.chrome", forceRefresh = true)!!.usedTodayMs)
    }
}
