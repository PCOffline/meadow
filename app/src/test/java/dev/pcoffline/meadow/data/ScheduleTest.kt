package dev.pcoffline.meadow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Must agree with schedule.ts in the backend. */
class ScheduleTest {
    /** 12 October 2026 is a Monday. */
    private fun monday(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 12, hour, minute, 0, 0, ZoneOffset.UTC)

    private val untilTen = Schedule.everyDay(TimeWindow(0, 22 * 60))

    @Test
    fun `allowed hours are checked against the time of day`() {
        assertTrue(untilTen.allows(monday(21, 59)))
        assertFalse(untilTen.allows(monday(22)))
        val officeHours = Schedule.everyDay(TimeWindow(10 * 60, 16 * 60))
        assertFalse(officeHours.allows(monday(9, 59)))
        assertTrue(officeHours.allows(monday(10)))
        assertFalse(officeHours.allows(monday(16)))
    }

    @Test
    fun `a day that isn't listed is open and a day with no hours is closed`() {
        val closedMondays = Schedule(mapOf(1 to emptyList()))
        assertFalse(closedMondays.allows(monday(12)))
        assertTrue(closedMondays.allows(monday(12).plusDays(1)))
        assertEquals(Duration.ofHours(12), closedMondays.untilChange(monday(12)))
    }

    @Test
    fun `the time until hours change follows them past midnight`() {
        assertEquals(Duration.ofHours(1), untilTen.untilChange(monday(21)))
        assertEquals(Duration.ofHours(1), untilTen.untilChange(monday(23)))
        val lateNights = Schedule.everyDay(TimeWindow(0, 60), TimeWindow(20 * 60, TimeWindow.MINUTES_IN_DAY))
        assertEquals(Duration.ofHours(2), lateNights.untilChange(monday(23)))
        assertNull(Schedule().untilChange(monday(12)))
    }

    @Test
    fun `fewer hours are stricter, more hours are looser`() {
        val office = Schedule.everyDay(TimeWindow(10 * 60, 16 * 60))
        assertTrue(Schedule.isStricter(null, office))
        assertFalse(Schedule.isStricter(office, null))
        assertTrue(Schedule.isStricter(office, Schedule.everyDay(TimeWindow(11 * 60, 15 * 60))))
        assertFalse(Schedule.isStricter(office, Schedule.everyDay(TimeWindow(9 * 60, 16 * 60))))
        assertTrue(Schedule.isStricter(office, Schedule(office.days + (6 to emptyList()))))
        assertTrue(Schedule.isStricter(office, office))
    }

    @Test
    fun `windows are merged and survive a round trip through Firestore`() {
        val raw = mapOf("1" to listOf(mapOf("from" to 600L, "to" to 700L), mapOf("from" to 650L, "to" to 960L)), "9" to emptyList<Any>())
        val schedule = Schedule.fromFirestore(raw)!!
        assertEquals(listOf(TimeWindow(600, 960)), schedule.windowsOn(1))
        assertEquals(listOf(TimeWindow.ALL_DAY), schedule.windowsOn(2))
        assertEquals(schedule, Schedule.fromFirestore(schedule.toFirestore()))
        assertNull(Schedule.fromFirestore(null))
    }

    @Test
    fun `a window must end after it starts`() {
        assertThrows(IllegalArgumentException::class.java) { TimeWindow(600, 600) }
        assertThrows(IllegalArgumentException::class.java) { TimeWindow(-1, 60) }
        assertThrows(IllegalArgumentException::class.java) { TimeWindow(0, 1441) }
    }
}
