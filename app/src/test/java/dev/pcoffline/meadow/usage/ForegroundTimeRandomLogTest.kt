package dev.pcoffline.meadow.usage

import dev.pcoffline.meadow.usage.ScreenEvent.Kind.PAUSED
import dev.pcoffline.meadow.usage.ScreenEvent.Kind.RESUMED
import dev.pcoffline.meadow.usage.ScreenEvent.Kind.SCREEN_OFF
import dev.pcoffline.meadow.usage.ScreenEvent.Kind.STARTUP
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen time from usage logs that no person would plan: pauses with no resume, resumes twice,
 * screens of one app in any order, the screen going off and the phone starting at any moment.
 * Each rule here holds for every log, so each test replays many random ones. A failure names its
 * log's number, which makes the same log again.
 */
class ForegroundTimeRandomLogTest {
    private val apps = listOf("a", "b", "c", "d")
    private val screens = listOf(null, "Main", "Detail")

    /** A log of [length] events at rising times, from [seed]. */
    private fun log(seed: Long, length: Int = 60): List<ScreenEvent> {
        val random = Random(seed)
        var time = 0L
        return List(length) {
            // Often the same millisecond as the last event, as Android writes them.
            time += if (random.nextInt(4) == 0) 0 else 1 + random.nextInt(5_000).toLong()
            when (random.nextInt(10)) {
                in 0..4 -> ScreenEvent(time, RESUMED, apps[random.nextInt(apps.size)], screens[random.nextInt(screens.size)])
                in 5..7 -> ScreenEvent(time, PAUSED, apps[random.nextInt(apps.size)], screens[random.nextInt(screens.size)])
                8 -> ScreenEvent(time, SCREEN_OFF, "android")
                else -> ScreenEvent(time, STARTUP, "android")
            }
        }
    }

    private fun everyLog(check: (seed: Long, events: List<ScreenEvent>, end: Long) -> Unit) {
        for (seed in 1L..LOGS) {
            val events = log(seed)
            check(seed, events, events.last().time + 10_000)
        }
    }

    @Test
    fun `no app has more time than the stretch, and all of them together have no more`() = everyLog { seed, events, end ->
        val totals = ForegroundTime.totals(events, 0, end)
        assertTrue("log $seed: $totals", totals.values.all { it > 0 })
        assertTrue("log $seed: ${totals.values.sum()} of $end", totals.values.sum() <= end)
    }

    @Test
    fun `two stretches side by side have the time of the whole`() = everyLog { seed, events, end ->
        val random = Random(seed)
        val cut = (random.nextDouble() * end).toLong()
        val whole = ForegroundTime.totals(events, 0, end)
        val parts = ForegroundTime.totals(events, 0, cut).toMutableMap()
        ForegroundTime.totals(events, cut, end).forEach { (app, ms) -> parts[app] = (parts[app] ?: 0L) + ms }
        assertEquals("log $seed, cut at $cut", whole, parts)
    }

    @Test
    fun `the app in front after a log is the one whose time goes on`() = everyLog { seed, events, end ->
        val last = events.last().time
        val inFront = ForegroundTime.foregroundAfter(null, events)
        val after = ForegroundTime.totals(events, last, end)
        assertEquals("log $seed", inFront?.let { mapOf(it to end - last) } ?: emptyMap<String, Long>(), after)
    }

    @Test
    fun `a log read in two parts leaves the same app in front`() = everyLog { seed, events, _ ->
        // The watcher reads the log a little at a time, and keeps what was in front between reads.
        val cut = Random(seed).nextInt(events.size)
        val firstPart = ForegroundTime.foregroundAfter(null, events.take(cut))
        val inParts = ForegroundTime.foregroundAfter(firstPart, events.drop(cut))
        assertEquals("log $seed, cut after $cut events", ForegroundTime.foregroundAfter(null, events), inParts)
    }

    @Test
    fun `an event that the phone reports twice counts once`() = everyLog { seed, events, end ->
        val twice = events.flatMap { listOf(it, it) }
        assertEquals("log $seed", ForegroundTime.totals(events, 0, end), ForegroundTime.totals(twice, 0, end))
        assertEquals("log $seed", ForegroundTime.foregroundAfter(null, events), ForegroundTime.foregroundAfter(null, twice))
    }

    @Test
    fun `the same log later in the day has the same time`() = everyLog { seed, events, end ->
        val later = 1_794_300_000_000L
        val moved = events.map { it.copy(time = it.time + later) }
        assertEquals("log $seed", ForegroundTime.totals(events, 0, end), ForegroundTime.totals(moved, later, end + later))
    }

    @Test
    fun `a clock that goes back gives no app less than nothing`() {
        for (seed in 1L..LOGS) {
            // The same events with their times in any order, as after the clock is set back.
            val random = Random(seed)
            val events = log(seed).map { it.copy(time = random.nextInt(200_000).toLong()) }
            val totals = ForegroundTime.totals(events, 0, 200_000)
            assertTrue("log $seed: $totals", totals.values.all { it > 0 })
        }
    }

    private companion object {
        const val LOGS = 2_000L
    }
}
