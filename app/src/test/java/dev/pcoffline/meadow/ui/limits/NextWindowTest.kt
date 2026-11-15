package dev.pcoffline.meadow.ui.limits

import dev.pcoffline.meadow.data.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What "Add hours" adds to a day's windows. */
class NextWindowTest {
    private fun w(from: Int, to: Int) = TimeWindow(from * 60, to * 60)

    @Test
    fun `with no hours, all day`() = assertEquals(TimeWindow.ALL_DAY, nextWindow(emptyList()))

    @Test
    fun `an hour, an hour after the last window`() = assertEquals(w(17, 18), nextWindow(listOf(w(10, 16))))

    @Test
    fun `hours that end at midnight leave room earlier in the day`() =
        assertEquals(w(0, 1), nextWindow(listOf(w(22, 24))))

    @Test
    fun `with no room after the last window, in the largest free time, touching no window`() {
        val next = nextWindow(listOf(w(1, 20), w(21, 22), w(23, 24)))!!
        assertEquals(TimeWindow(0, 40), next)
    }

    @Test
    fun `with no free time left, nothing`() {
        assertNull(nextWindow(listOf(TimeWindow.ALL_DAY)))
        assertNull(nextWindow(listOf(TimeWindow(0, 720), TimeWindow(721, TimeWindow.MINUTES_IN_DAY))))
    }

    @Test
    fun `a few free minutes between windows aren't offered`() {
        // 20 minutes free: a window kept clear of both sides would be under 15 minutes.
        assertNull(nextWindow(listOf(TimeWindow(0, 600), TimeWindow(620, TimeWindow.MINUTES_IN_DAY))))
        // 45 minutes free: 15 minutes, 15 from each side.
        assertEquals(TimeWindow(615, 630), nextWindow(listOf(TimeWindow(0, 600), TimeWindow(645, TimeWindow.MINUTES_IN_DAY))))
    }
}
