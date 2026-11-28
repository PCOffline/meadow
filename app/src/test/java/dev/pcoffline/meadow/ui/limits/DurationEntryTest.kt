package dev.pcoffline.meadow.ui.limits

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationEntryTest {
    private fun typed(vararg keys: String) = keys.fold(DurationEntry("")) { e, k -> e.type(k) }

    @Test
    fun `digits fill hours and minutes from the right`() {
        assertEquals(5, typed("5").minutes)
        assertEquals(50, typed("5", "0").minutes)
        assertEquals(300, typed("5", "0", "0").minutes)
        assertEquals(5 * 60 + 30, typed("5", "3", "0").minutes)
        assertEquals(24 * 60, typed("2", "4", "00").minutes)
        assertEquals("05", typed("5", "0", "0").hoursText)
        assertEquals("00", typed("5", "0", "0").minutesText)
    }

    @Test
    fun `minutes over 59 count as more hours`() {
        val entry = typed("9", "0")
        assertEquals("00", entry.hoursText)
        assertEquals("90", entry.minutesText)
        assertEquals(90, entry.minutes)
    }

    @Test
    fun `up to four digits, and leading zeros drop`() {
        assertEquals("1234", typed("1", "2", "3", "4", "5").digits)
        assertEquals("", typed("0", "00").digits)
        assertEquals(0, typed("0").minutes)
    }

    @Test
    fun `back takes off the last digit`() {
        assertEquals(5, typed("5", "0").back().minutes)
        assertEquals(0, typed("5").back().back().minutes)
    }

    @Test
    fun `the amount a dialog opens with is replaced by the first key`() {
        val opened = DurationEntry.of(90)
        assertEquals("01", opened.hoursText)
        assertEquals("30", opened.minutesText)
        assertEquals(90, opened.minutes)
        assertEquals(4, opened.type("4").minutes)
        assertEquals(0, opened.back().minutes)
        assertEquals("", DurationEntry.of(0).digits)
    }
}
