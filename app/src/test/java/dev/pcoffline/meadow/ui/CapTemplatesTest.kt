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
    fun `the hour templates do not overlap and stay inside one day`() {
        val windows = CapTemplates.HOURS.map { it.from to it.to }
        assertEquals(listOf(6 * 60 to 12 * 60, 12 * 60 to 17 * 60, 17 * 60 to 22 * 60, 22 * 60 to 24 * 60), windows)
    }

    @Test
    fun `the editor stays open after a saved change to a limit, so it can be shared`() {
        val limit = AppLimit("a", "A", 30, 0)
        assertFalse(closesAfterSave(existing = limit, outcome = ChangeOutcome.APPLIED))
        assertTrue("a new limit goes back to the garden", closesAfterSave(existing = null, outcome = ChangeOutcome.APPLIED))
        assertTrue("a question waits for a Bumblebee", closesAfterSave(existing = limit, outcome = ChangeOutcome.REQUESTED))
    }
}
