package dev.pcoffline.meadow.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Today's usage is measured every few seconds while a limited app is open. Each measure asks the
 * phone only for what is new in its usage log, and still gives what a whole read would.
 */
class RecentEventsTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val midnight = 1_000 * hour

    /** The stretch Meadow reads for today: from three hours before midnight. */
    private val from = midnight - 3 * hour

    /** The phone's log: each event, and when the log gained it. */
    private val log = ArrayList<Pair<ScreenEvent, Long>>()
    private val asked = ArrayList<LongRange>()
    private var now = midnight + 10 * hour

    private val recent = RecentEvents { a, b ->
        asked += a until b
        whole(a, b)
    }

    /** What the log holds for [a, b) at this moment. */
    private fun whole(a: Long, b: Long) =
        log.filter { (event, gained) -> gained <= now && event.time >= a && event.time < b }.map { it.first }.sortedBy { it.time }

    private fun open(pkg: String, at: Long, gainedAt: Long = at) {
        log += ScreenEvent(at, ScreenEvent.Kind.RESUMED, pkg, "$pkg.Main") to gainedAt
    }

    @Test
    fun `a later measure asks only for what is new, and gives the same as a whole read`() {
        open("app.feed", midnight - hour)
        open("app.chat", midnight + 9 * hour)
        assertEquals(whole(from, now), recent.between(from, now, now))
        assertEquals(listOf(from until now), asked)

        val first = now
        now += 20_000
        open("app.feed", now - 5_000)
        asked.clear()

        assertEquals(whole(from, now), recent.between(from, now, now))
        // Only the last seconds of the earlier read, and what came after: not yesterday again.
        assertEquals(listOf(first - RecentEvents.OVERLAP_MS until now), asked)
    }

    @Test
    fun `hours inside the day are answered from what is kept, with no read`() {
        open("app.feed", midnight + 2 * hour)
        open("app.chat", midnight + 7 * hour)
        recent.between(from, now, now)
        asked.clear()

        // Usage inside allowed hours of 06:00 to 08:00, with its own three hours before.
        val a = midnight + 3 * hour
        val b = midnight + 8 * hour
        assertEquals(whole(a, b), recent.between(a, b, now))
        assertEquals(emptyList<LongRange>(), asked)
    }

    @Test
    fun `an event the log gains a moment late is still counted`() {
        open("app.feed", midnight + hour)
        recent.between(from, now, now)

        // It happened 3 seconds before that read, and the log gained it 2 seconds after.
        open("app.chat", now - 3_000, gainedAt = now + 2_000)
        now += 20_000

        assertEquals(whole(from, now), recent.between(from, now, now))
        assertTrue(recent.between(from, now, now)!!.any { it.packageName == "app.chat" })
    }

    @Test
    fun `the whole stretch is read again now and then, for an event gained long after`() {
        open("app.feed", midnight + hour)
        recent.between(from, now, now)
        open("app.chat", midnight + 2 * hour, gainedAt = now + minute)

        now += RecentEvents.WHOLE_EVERY_MS
        asked.clear()

        assertEquals(whole(from, now), recent.between(from, now, now))
        assertEquals(listOf(from until now), asked)
    }

    @Test
    fun `a new day is read whole`() {
        open("app.feed", midnight + hour)
        recent.between(from, now, now)

        now = midnight + 24 * hour + 5 * minute
        val tomorrow = from + 24 * hour
        open("app.chat", now - minute)
        asked.clear()

        assertEquals(whole(tomorrow, now), recent.between(tomorrow, now, now))
        assertEquals(listOf(tomorrow until now), asked)
    }

    @Test
    fun `after the clock is set back, nothing kept is trusted`() {
        open("app.feed", midnight + 9 * hour)
        recent.between(from, now, now)

        now -= 2 * hour
        asked.clear()

        assertEquals(whole(from, now), recent.between(from, now, now))
        assertEquals(listOf(from until now), asked)
    }

    @Test
    fun `a day that ended, or a long stretch, isn't kept here`() {
        // Yesterday as a whole: the caller reads it from the log itself.
        assertNull(recent.between(from - 24 * hour, midnight, now))
        // A week that ends now.
        assertNull(recent.between(now - 7 * 24 * hour, now, now))
        assertEquals(emptyList<LongRange>(), asked)
    }

    @Test
    fun `a day of measures, every few seconds, always gives what a whole read would`() {
        val random = Random(7)
        val apps = listOf("app.feed", "app.chat", "app.video", "app.home")
        var whole = 0
        now = midnight + minute
        repeat(2_000) {
            // Something is opened now and then, and the log gains it within 5 seconds.
            if (random.nextInt(3) == 0) {
                val at = now - random.nextInt(4_000)
                open(apps[random.nextInt(apps.size)], at, gainedAt = at + random.nextInt(5_000))
            }
            now += 1_000 + random.nextInt(20_000)
            asked.clear()
            val got = recent.between(from, now, now)!!
            if (asked.single().first == from) whole++
            // Everything the log holds by now, but for the last seconds, where it may still gain.
            val settled = now - 5_000
            assertEquals(whole(from, settled), got.filter { it.time < settled })
        }
        // About six hours of measures: a whole read only every ten minutes.
        assertTrue("$whole whole reads", whole in 30..45)
    }
}
