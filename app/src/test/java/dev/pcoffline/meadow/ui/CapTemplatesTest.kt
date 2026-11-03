package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.ui.limits.CapTemplates
import dev.pcoffline.meadow.ui.limits.closesAfterSave
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class CapTemplatesTest {
    @Test
    fun `weekdays are the five days from the first day of the week`() {
        assertEquals(listOf(1, 2, 3, 4, 5), CapTemplates.weekdays(DayOfWeek.MONDAY))
        assertEquals(listOf(1, 2, 3, 4, 7), CapTemplates.weekdays(DayOfWeek.SUNDAY))
        assertEquals(listOf(1, 2, 3, 6, 7), CapTemplates.weekdays(DayOfWeek.SATURDAY))
    }

    @Test
    fun `weekends are the other two days`() {
        assertEquals(listOf(6, 7), CapTemplates.weekends(DayOfWeek.MONDAY))
        assertEquals(listOf(5, 6), CapTemplates.weekends(DayOfWeek.SUNDAY))
        assertEquals(listOf(4, 5), CapTemplates.weekends(DayOfWeek.SATURDAY))
    }

    @Test
    fun `the hour templates run in order, do not overlap and stay inside one day`() {
        CapTemplates.HOURS.forEach { assertTrue("$it", it.from < it.to && it.to <= 24 * 60) }
        CapTemplates.HOURS.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", a.to <= b.from) }
    }

    @Test
    fun `the editor stays open after a saved change to a limit, so it can be shared`() {
        val limit = AppLimit("a", "A", 30, 0)
        assertFalse(closesAfterSave(existing = limit, outcome = ChangeOutcome.APPLIED))
        assertTrue("a new limit goes back to the garden", closesAfterSave(existing = null, outcome = ChangeOutcome.APPLIED))
        assertTrue("a question waits for a Bumblebee", closesAfterSave(existing = limit, outcome = ChangeOutcome.REQUESTED))
    }
}
