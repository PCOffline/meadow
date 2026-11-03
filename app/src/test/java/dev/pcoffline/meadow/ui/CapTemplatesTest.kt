package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.ui.limits.CapTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapTemplatesTest {
    @Test
    fun `a weekend runs from its first day to its last, as ISO days`() {
        // java.util.Calendar numbers: 1 is Sunday, 6 Friday, 7 Saturday.
        assertEquals(listOf(6, 7), CapTemplates.weekendDays(onset = 7, cease = 1))
        assertEquals(listOf(5, 6), CapTemplates.weekendDays(onset = 6, cease = 7))
        assertEquals(listOf(7), CapTemplates.weekendDays(onset = 1, cease = 1))
        assertEquals(listOf(4, 5), CapTemplates.weekendDays(onset = 5, cease = 6))
    }

    @Test
    fun `the hour templates run in order, do not overlap and stay inside one day`() {
        CapTemplates.HOURS.forEach { assertTrue("$it", it.from < it.to && it.to <= 24 * 60) }
        CapTemplates.HOURS.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", a.to <= b.from) }
    }
}
