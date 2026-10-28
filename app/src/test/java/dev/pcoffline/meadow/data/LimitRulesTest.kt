package dev.pcoffline.meadow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Must agree with isLimitStricter in the backend's schedule.test.ts. */
class LimitRulesTest {
    private val weekdays = listOf(1, 2, 3, 4, 5)
    private val cap = TimeCap(weekdays, 600, 960, 30)

    private fun limit(daily: Int, dayMinutes: Map<Int, Int> = emptyMap(), caps: List<TimeCap> = emptyList()) =
        AppLimit("app", "App", daily, 0, dayMinutes = dayMinutes, caps = caps)

    @Test
    fun `a day's own limit replaces the everyday one, lower is stricter, higher or none is looser`() {
        assertFalse(LimitRules.isLooser(limit(30), limit(30, mapOf(6 to 20))))
        assertTrue(LimitRules.isLooser(limit(30), limit(30, mapOf(6 to 120))))
        assertTrue(LimitRules.isLooser(limit(30), limit(30, mapOf(6 to 0))))
        assertFalse(LimitRules.isLooser(limit(30, mapOf(6 to 120)), limit(30)))
        assertTrue(LimitRules.isLooser(limit(30, mapOf(6 to 20)), limit(30)))
        assertFalse(LimitRules.isLooser(limit(0, mapOf(1 to 60)), limit(45)))
        assertEquals(120, limit(30, mapOf(6 to 120)).dailyMinutesOn(6))
        assertEquals(30, limit(30, mapOf(6 to 120)).dailyMinutesOn(5))
    }

    @Test
    fun `a cap is kept by a tighter cap over at least its hours, or a daily limit no longer than it`() {
        assertFalse(LimitRules.isLooser(limit(60), limit(60, caps = listOf(cap))))
        assertTrue(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(60)))
        assertFalse(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(60, caps = listOf(cap.copy(minutes = 20)))))
        assertTrue(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(60, caps = listOf(cap.copy(minutes = 45)))))
        assertFalse(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(60, caps = listOf(cap.copy(from = 540, to = 1020)))))
        assertTrue(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(60, caps = listOf(cap.copy(from = 660)))))
        assertTrue(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(60, caps = listOf(cap.copy(days = listOf(1, 2, 3))))))
        assertFalse(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(25)))
        assertFalse(LimitRules.isLooser(limit(60, caps = listOf(cap)), limit(60, weekdays.associateWith { 30 })))
    }

    @Test
    fun `only hours, days' own limits and caps need the backend to compare`() {
        assertTrue(limit(30, mapOf(6 to 60), listOf(cap)).sameUncomparable(limit(20, mapOf(6 to 60), listOf(cap))))
        assertFalse(limit(30, mapOf(6 to 60)).sameUncomparable(limit(30, mapOf(6 to 50))))
        assertFalse(limit(30).sameUncomparable(limit(30, caps = listOf(cap))))
    }

    @Test
    fun `a day without a limit of its own holds nothing back`() {
        assertFalse(limit(0, mapOf(6 to 0)).holdsBack)
        assertTrue(limit(0, mapOf(6 to 10)).holdsBack)
        assertTrue(limit(0, caps = listOf(cap)).holdsBack)
        assertFalse(limit(0).holdsBack)
    }

    @Test
    fun `caps and days are stored as the backend expects, and anything malformed is skipped`() {
        assertEquals(mapOf("days" to weekdays, "from" to 600, "to" to 960, "minutes" to 30), TimeCap(listOf(5, 4, 3, 2, 1, 1), 600, 960, 30).toFirestore())
        val raw = listOf(
            mapOf("days" to listOf(5L, 1L, 2L, 3L, 4L), "from" to 600L, "to" to 960L, "minutes" to 30L),
            mapOf("days" to listOf(1L), "from" to 600L, "to" to 600L, "minutes" to 30L),
            mapOf("days" to listOf(9L), "from" to 600L, "to" to 960L, "minutes" to 30L),
            mapOf("days" to listOf(1L), "from" to 600L, "to" to 660L, "minutes" to 60L),
            "nonsense",
        )
        assertEquals(listOf(cap), TimeCap.listFromFirestore(raw))
        assertEquals(emptyList<TimeCap>(), TimeCap.listFromFirestore(null))
        assertEquals(mapOf("6" to 120, "7" to 60), DayMinutes.toFirestore(mapOf(7 to 60, 6 to 120)))
        assertEquals(mapOf(6 to 120), DayMinutes.fromFirestore(mapOf("6" to 120L, "9" to 30L, "1" to -5L, "x" to 1L, "2" to "lots")))
        assertEquals(emptyMap<Int, Int>(), DayMinutes.fromFirestore(null))
    }
}
