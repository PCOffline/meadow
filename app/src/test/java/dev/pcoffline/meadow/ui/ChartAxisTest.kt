package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.ui.components.axisTopMinutes
import org.junit.Assert.assertEquals
import org.junit.Test

/** The time graphs' scale (#81): a round top value, so its label reads plainly. */
class ChartAxisTest {
    @Test
    fun `an hour or more rounds up to whole hours`() {
        assertEquals(60, axisTopMinutes(60f))
        assertEquals(120, axisTopMinutes(61f))
        assertEquals(240, axisTopMinutes(200f))
    }

    @Test
    fun `more than six hours rounds up to an even number of hours`() {
        assertEquals(480, axisTopMinutes(430f))
        assertEquals(600, axisTopMinutes(541f))
    }

    @Test
    fun `under an hour rounds up to a quarter hour`() {
        assertEquals(15, axisTopMinutes(10f))
        assertEquals(30, axisTopMinutes(20f))
        assertEquals(45, axisTopMinutes(45f))
    }

    @Test
    fun `nothing used still has a scale`() {
        assertEquals(60, axisTopMinutes(0f))
    }

    @Test
    fun `the edges of each step stay in their step`() {
        assertEquals(15, axisTopMinutes(15f))
        assertEquals(30, axisTopMinutes(15.01f))
        assertEquals(360, axisTopMinutes(360f))
        assertEquals(480, axisTopMinutes(361f))
        assertEquals(1440, axisTopMinutes(1440f))
        assertEquals(60, axisTopMinutes(-5f))
    }

}
