package dev.pcoffline.meadow.enforcement

import dev.pcoffline.meadow.data.AppLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBudgetTest {
    private val minute = 60_000L

    private fun budget(
        daily: Int = 30,
        weekly: Int = 0,
        usedToday: Long = 0,
        usedWeek: Long = usedToday,
        bonusToday: Int = 0,
        bonusWeek: Int = bonusToday,
        emergencyUntil: Long = 0,
    ) = AppBudget(AppLimit("app", "App", daily, weekly), usedToday, usedWeek, bonusToday, bonusWeek, emergencyUntil)

    @Test
    fun `today's allowance includes extra time and follows the tighter limit`() {
        assertEquals(45 * minute, budget(usedToday = 10 * minute, bonusToday = 15).allowedTodayMs)
        // Weekly only: what today allows is what's spent plus what's left this week.
        assertEquals(50 * minute, budget(daily = 0, weekly = 120, usedToday = 20 * minute, usedWeek = 90 * minute).allowedTodayMs)
        assertEquals(null, budget(daily = 0, weekly = 0).allowedTodayMs)
    }

    @Test
    fun `remaining time counts down from the daily limit`() {
        val b = budget(usedToday = 10 * minute)
        assertEquals(20 * minute, b.remainingMs)
        assertFalse(b.exhausted)
    }

    @Test
    fun `used up means exhausted`() {
        assertTrue(budget(usedToday = 30 * minute).exhausted)
        assertTrue(budget(usedToday = 45 * minute).exhausted)
        assertEquals(0L, budget(usedToday = 45 * minute).remainingMs)
    }

    @Test
    fun `extra time from a Bumblebee is added`() {
        val b = budget(usedToday = 30 * minute, bonusToday = 15)
        assertFalse(b.exhausted)
        assertEquals(15 * minute, b.remainingMs)
    }

    @Test
    fun `the tighter of daily and weekly wins`() {
        val b = budget(daily = 60, weekly = 120, usedToday = 10 * minute, usedWeek = 100 * minute)
        assertEquals(20 * minute, b.remainingMs)
        assertTrue(b.weeklyIsTighter)
    }

    @Test
    fun `no limits means unlimited`() {
        val b = budget(daily = 0, weekly = 0, usedToday = 999 * minute)
        assertEquals(Long.MAX_VALUE, b.remainingMs)
        assertFalse(b.exhausted)
    }

    @Test
    fun `an emergency unlock keeps an exhausted app open`() {
        val b = budget(usedToday = 40 * minute, emergencyUntil = System.currentTimeMillis() + 10 * minute)
        assertTrue(b.emergency)
        assertFalse(b.exhausted)
        val ended = budget(usedToday = 40 * minute, emergencyUntil = System.currentTimeMillis() - 1)
        assertFalse(ended.emergency)
        assertTrue(ended.exhausted)
    }

    @Test
    fun `used fraction is capped at one`() {
        assertEquals(0.5f, budget(usedToday = 15 * minute).usedFraction, 0.001f)
        assertEquals(1f, budget(usedToday = 90 * minute).usedFraction, 0.001f)
    }

    @Test
    fun `outside its hours an app rests even with time left`() {
        val b = AppBudget(AppLimit("app", "App", 30, 0), 0, 0, 0, 0, outsideHours = true, untilScheduleChangeMs = 60 * minute)
        assertEquals(0L, b.remainingMs)
        assertTrue(b.exhausted)
        assertTrue(b.restingByHours)
    }

    @Test
    fun `the end of the allowed hours counts down like a limit`() {
        val b = AppBudget(AppLimit("app", "App", 30, 0), 10 * minute, 10 * minute, 0, 0, untilScheduleChangeMs = 5 * minute)
        assertEquals(5 * minute, b.remainingMs)
        assertFalse(b.restingByHours)
    }

    @Test
    fun `extra time from a Bumblebee can be used outside the hours`() {
        val b = AppBudget(
            AppLimit("app", "App", 30, 0), 0, 0, 15, 15,
            outsideHours = true, untilScheduleChangeMs = 60 * minute, outsideHoursAllowanceMs = 15 * minute,
        )
        assertEquals(15 * minute, b.remainingMs)
        assertFalse(b.exhausted)
    }

    @Test
    fun `a day's own limit replaces the everyday one`() {
        val b = AppBudget(AppLimit("app", "App", 30, 0), 40 * minute, 40 * minute, 0, 0, dailyMinutesToday = 120)
        assertEquals(80 * minute, b.remainingMs)
        assertEquals(120 * minute, b.dailyAllowedMs)
        val free = AppBudget(AppLimit("app", "App", 30, 0), 40 * minute, 40 * minute, 0, 0, dailyMinutesToday = 0)
        assertEquals(null, free.allowedTodayMs)
        assertFalse(free.exhausted)
    }

    @Test
    fun `a used-up cap rests the app until its hours end, and it sleeps rather than wilts`() {
        val b = AppBudget(AppLimit("app", "App", 60, 0), 30 * minute, 30 * minute, 0, 0, capRemainingMs = 0, untilCapEndsMs = 90 * minute)
        assertEquals(0L, b.remainingMs)
        assertTrue(b.exhausted)
        assertTrue(b.restingByCap)
        assertTrue(b.sleeping)
        assertEquals(90 * minute, b.wakesInMs)
        assertFalse(b.restingByHours)
        // The tightest of everything decides.
        val tight = AppBudget(AppLimit("app", "App", 60, 0), 50 * minute, 50 * minute, 0, 0, capRemainingMs = 20 * minute, untilCapEndsMs = 60 * minute)
        assertEquals(10 * minute, tight.remainingMs)
        assertFalse(tight.restingByCap)
        // With the day used up as well, it's simply used up: it wilts.
        val done = AppBudget(AppLimit("app", "App", 60, 0), 60 * minute, 60 * minute, 0, 0, capRemainingMs = 0, untilCapEndsMs = 60 * minute)
        assertFalse(done.sleeping)
        // An emergency unlock keeps it awake.
        val unlocked = b.copy(emergencyUntilMillis = System.currentTimeMillis() + 60 * minute)
        assertFalse(unlocked.restingByCap)
        assertFalse(unlocked.sleeping)
    }

    @Test
    fun `outside its hours it sleeps until they start`() {
        val b = AppBudget(AppLimit("app", "App", 30, 0), 0, 0, 0, 0, outsideHours = true, untilScheduleChangeMs = 60 * minute)
        assertTrue(b.sleeping)
        assertEquals(60 * minute, b.wakesInMs)
        assertEquals(null, b.copy(untilScheduleChangeMs = Long.MAX_VALUE / 2).wakesInMs)
    }
}
