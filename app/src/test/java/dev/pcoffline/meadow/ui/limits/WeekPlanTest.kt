package dev.pcoffline.meadow.ui.limits

import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WeekPlanTest {
    private fun w(from: Int, to: Int) = TimeWindow(from, to)
    private val weekdays = (1..5).toList()

    @Test
    fun `hours that end at midnight and start again at midnight read as one stretch`() {
        // Fri 22:00–24:00 and Sat 00:00–02:00, 06:00–22:00.
        val schedule = Schedule(mapOf(5 to listOf(w(600, 1080), w(1320, 1440)), 6 to listOf(w(0, 120), w(360, 1320))))
        assertEquals(
            listOf(Stretch(5, 600, 1080), Stretch(5, 1320, 1560), Stretch(6, 360, 1320)),
            WeekPlan.stretches(schedule),
        )
        assertEquals(listOf(Stretch(6, 360, 1320)), WeekPlan.stretchesOn(schedule, 6))
    }

    @Test
    fun `sunday's late hours join monday's early hours`() {
        val schedule = Schedule(mapOf(7 to listOf(w(1380, 1440)), 1 to listOf(w(0, 60))))
        assertEquals(listOf(Stretch(7, 1380, 1500)), WeekPlan.stretches(schedule))
    }

    @Test
    fun `hours don't join when together they would last a day or more`() {
        // Fri 22:00–24:00 and Sat 00:00–23:00 would last 25 hours.
        val long = Schedule(mapOf(5 to listOf(w(1320, 1440)), 6 to listOf(w(0, 1380))))
        assertEquals(listOf(Stretch(5, 1320, 1440), Stretch(6, 0, 1380)), WeekPlan.stretches(long))
        // A day allowed all day joins nothing.
        val allDay = Schedule(mapOf(5 to listOf(w(0, 1440)), 6 to listOf(w(0, 120))))
        assertEquals(listOf(Stretch(6, 0, 120)), WeekPlan.stretches(allDay))
    }

    @Test
    fun `days are allowed all day, resting or set to hours`() {
        val schedule = Schedule(mapOf(1 to emptyList(), 2 to listOf(w(0, 1440)), 3 to listOf(w(600, 960))))
        assertEquals(DayMode.RESTING, WeekPlan.modeOf(schedule, 1))
        assertEquals(DayMode.ALL_DAY, WeekPlan.modeOf(schedule, 2))
        assertEquals(DayMode.HOURS, WeekPlan.modeOf(schedule, 3))
        assertEquals(DayMode.ALL_DAY, WeekPlan.modeOf(schedule, 4))
        assertEquals(DayMode.ALL_DAY, WeekPlan.modeOf(null, 4))
    }

    @Test
    fun `the first stretch on a day sets its hours, and days not drawn on stay allowed`() {
        val schedule = WeekPlan.add(null, weekdays, 600, 1080)!!
        weekdays.forEach { assertEquals(listOf(w(600, 1080)), schedule.days[it]) }
        assertNull(schedule.days[6])
        assertEquals(DayMode.ALL_DAY, WeekPlan.modeOf(schedule, 6))
    }

    @Test
    fun `a stretch past midnight allows the next day's early hours, unless that day allows all of it`() {
        val friday = WeekPlan.add(Schedule(mapOf(6 to listOf(w(360, 1320)))), listOf(5), 1320, 1560)!!
        assertEquals(listOf(w(1320, 1440)), friday.days[5])
        assertEquals(listOf(w(0, 120), w(360, 1320)), friday.days[6])
        // Saturday allows all of it: it stays so.
        val open = WeekPlan.add(null, listOf(5), 1320, 1560)!!
        assertNull(open.days[6])
    }

    @Test
    fun `stretches on one day that touch or overlap join`() {
        val schedule = WeekPlan.add(Schedule(mapOf(1 to listOf(w(600, 720)))), listOf(1), 720, 900)!!
        assertEquals(listOf(w(600, 900)), schedule.days[1])
    }

    @Test
    fun `a day takes up to eight stretches`() {
        var schedule: Schedule? = null
        repeat(MAX_WINDOWS) { schedule = WeekPlan.add(schedule, listOf(1), it * 120, it * 120 + 60) }
        assertEquals(MAX_WINDOWS, schedule!!.days[1]!!.size)
        assertThrows(TooManyStretches::class.java) { WeekPlan.add(schedule, listOf(1), 1380, 1430) }
    }

    @Test
    fun `removing a stretch takes both halves, and a day left with none allows all of it`() {
        val schedule = Schedule(mapOf(5 to listOf(w(600, 1080), w(1320, 1440)), 6 to listOf(w(0, 120), w(360, 1320))))
        val after = WeekPlan.remove(schedule, Stretch(5, 1320, 1560))!!
        assertEquals(listOf(w(600, 1080)), after.days[5])
        assertEquals(listOf(w(360, 1320)), after.days[6])
        val fridayGone = WeekPlan.remove(after, Stretch(5, 600, 1080))!!
        assertNull(fridayGone.days[5])
        // With no stretch left in the week, the limit has no hours.
        assertNull(WeekPlan.remove(fridayGone, Stretch(6, 360, 1320)))
    }

    @Test
    fun `redrawing a stretch puts the new one in its place`() {
        val schedule = Schedule(mapOf(1 to listOf(w(600, 960))))
        val redrawn = WeekPlan.replace(schedule, Stretch(1, 600, 960), listOf(1, 2), 480, 720)!!
        assertEquals(listOf(w(480, 720)), redrawn.days[1])
        assertEquals(listOf(w(480, 720)), redrawn.days[2])
    }

    @Test
    fun `a whole day is allowed, rested or given hours with one tap`() {
        val schedule = Schedule(mapOf(6 to listOf(w(360, 1320))))
        assertEquals(emptyList<TimeWindow>(), WeekPlan.setMode(schedule, 6, DayMode.RESTING)!!.days[6])
        assertNull(WeekPlan.setMode(schedule, 6, DayMode.ALL_DAY))
        assertEquals(listOf(w(600, 960)), WeekPlan.setMode(schedule, 6, DayMode.HOURS, listOf(w(600, 960)))!!.days[6])
    }

    @Test
    fun `a stretch or a day is duplicated to other days`() {
        val schedule = Schedule(mapOf(1 to listOf(w(600, 960)), 2 to emptyList()))
        val stretch = WeekPlan.duplicate(schedule, Stretch(1, 600, 960), listOf(1, 3))!!
        assertEquals(listOf(w(600, 960)), stretch.days[3])
        val days = WeekPlan.duplicateDay(schedule, 2, listOf(4, 5))!!
        assertEquals(emptyList<TimeWindow>(), days.days[4])
        assertEquals(emptyList<TimeWindow>(), days.days[5])
        assertEquals(listOf(w(600, 960)), days.days[1])
    }

    @Test
    fun `a drag moves in steps, and the grid zooms in for small steps`() {
        assertEquals(600, WeekPlan.snap(604, 10))
        assertEquals(610, WeekPlan.snap(605, 10))
        assertEquals(615, WeekPlan.snap(613, 5))
        assertEquals(0, WeekPlan.snap(-20, 15))
        assertEquals(2880, WeekPlan.snap(3000, 15))
        assertEquals(6, WeekPlan.hoursOnScreen(5))
        assertEquals(12, WeekPlan.hoursOnScreen(10))
        assertEquals(24, WeekPlan.hoursOnScreen(15))
        assertEquals(24, WeekPlan.hoursOnScreen(30))
    }

    @Test
    fun `a cap past midnight shows on its own day and the next morning`() {
        val cap = TimeCap(listOf(5), 1320, 1560, 30)
        assertEquals(listOf(cap to (1320 until 1440)), WeekPlan.capPartsOn(listOf(cap), 5))
        assertEquals(listOf(cap to (0 until 120)), WeekPlan.capPartsOn(listOf(cap), 6))
        assertEquals(emptyList<Pair<TimeCap, IntRange>>(), WeekPlan.capPartsOn(listOf(cap), 7))
    }

    @Test
    fun `Hours with nothing to bring back leaves the day allowed all day, and resting days stay resting`() {
        val schedule = Schedule(mapOf(1 to emptyList(), 2 to listOf(w(600, 960))))
        assertNull(WeekPlan.setMode(schedule, 2, DayMode.HOURS, emptyList())!!.days[2])
        // Removing a stretch on another day leaves a resting day as it is.
        assertEquals(emptyList<TimeWindow>(), WeekPlan.remove(schedule, Stretch(2, 600, 960))!!.days[1])
        assertEquals(emptyList<Stretch>(), WeekPlan.stretchesOn(schedule, 1))
        assertEquals(emptyList<Stretch>(), WeekPlan.stretches(null))
    }

    @Test
    fun `a stretch past midnight into a resting day gives that day its early hours`() {
        val schedule = WeekPlan.add(Schedule(mapOf(6 to emptyList())), listOf(5), 1320, 1560)!!
        assertEquals(listOf(w(0, 120)), schedule.days[6])
        assertEquals(listOf(Stretch(5, 1320, 1560)), WeekPlan.stretches(schedule))
    }
}
