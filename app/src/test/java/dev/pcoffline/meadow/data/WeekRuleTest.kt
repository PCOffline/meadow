package dev.pcoffline.meadow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Choosing the day weeks start on, without it ever handing back a week's allowance early. */
class WeekRuleTest {
    // A Wednesday.
    private val wednesday = LocalDate.of(2026, 10, 28)

    @Test
    fun `weeks start on the chosen day`() {
        assertEquals(LocalDate.of(2026, 10, 26), WeekRule().start(wednesday))
        assertEquals(LocalDate.of(2026, 10, 25), WeekRule(DayOfWeek.SUNDAY).start(wednesday))
        assertEquals(LocalDate.of(2026, 10, 24), WeekRule(DayOfWeek.SATURDAY).start(wednesday))
        // The first day itself starts its own week.
        assertEquals(LocalDate.of(2026, 10, 24), WeekRule(DayOfWeek.SATURDAY).start(LocalDate.of(2026, 10, 24)))
    }

    @Test
    fun `a new first day shows at once, but weekly limits keep this week until it ends`() {
        val rule = WeekRule().changedTo(DayOfWeek.SATURDAY, wednesday)
        assertEquals(DayOfWeek.SATURDAY, rule.first)
        assertEquals(LocalDate.of(2026, 10, 24), rule.start(wednesday))
        // Starting the limit week on Saturday now would hand back what Monday to Wednesday used.
        assertEquals(LocalDate.of(2026, 10, 26), rule.limitStart(wednesday))
        assertEquals(LocalDate.of(2026, 10, 26), rule.limitStart(LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 11, 2), rule.pendingOn(wednesday))
        // From then on, the new weeks; the first one overlaps the old week's end, which only
        // counts those days twice: stricter, never looser.
        assertEquals(LocalDate.of(2026, 10, 31), rule.limitStart(LocalDate.of(2026, 11, 2)))
        assertNull(rule.pendingOn(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `whatever the change, no day of use drops out of a limit week`() {
        val days = (0L until 21L).map { wednesday.plusDays(it) }
        for (from in WeekRule.CHOICES) for (to in WeekRule.CHOICES) {
            val before = WeekRule(from)
            val after = before.changedTo(to, wednesday)
            val switch = after.limitsFrom ?: continue
            days.forEach { day ->
                if (day.isBefore(switch)) {
                    assertEquals("$from → $to on $day", before.limitStart(day), after.limitStart(day))
                } else {
                    assertEquals("$from → $to on $day", after.start(day), after.limitStart(day))
                }
            }
            // The first new week starts no later than the switch.
            assertTrue("$from → $to", !after.limitStart(switch).isAfter(switch))
        }
    }

    @Test
    fun `changing back to the day in force cancels the pending change`() {
        val rule = WeekRule().changedTo(DayOfWeek.SUNDAY, wednesday).changedTo(DayOfWeek.MONDAY, wednesday)
        assertEquals(WeekRule(DayOfWeek.MONDAY), rule)
        assertNull(rule.pendingOn(wednesday))
    }

    @Test
    fun `a second change while one waits still keeps the week in force`() {
        val rule = WeekRule().changedTo(DayOfWeek.SATURDAY, wednesday).changedTo(DayOfWeek.SUNDAY, wednesday)
        assertEquals(DayOfWeek.SUNDAY, rule.first)
        assertEquals(LocalDate.of(2026, 10, 26), rule.limitStart(wednesday))
        assertEquals(LocalDate.of(2026, 11, 2), rule.pendingOn(wednesday))
    }

    @Test
    fun `a region's first day maps to the nearest choice`() {
        WeekRule.CHOICES.forEach { assertEquals(it, WeekRule.nearest(it)) }
        assertEquals(DayOfWeek.MONDAY, WeekRule.nearest(DayOfWeek.FRIDAY))
    }
}
