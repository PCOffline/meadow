package dev.pcoffline.meadow.usage

import dev.pcoffline.meadow.usage.ScreenEvent.Kind.PAUSED
import dev.pcoffline.meadow.usage.ScreenEvent.Kind.RESUMED
import dev.pcoffline.meadow.usage.ScreenEvent.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app that was on screen as a read starts is counted, and found once for each start (#159). */
class OpenAtTest {
    private val hour = 60 * 60_000L
    private val now = 100 * hour
    private val from = now - 4 * hour
    private var reads = 0

    private fun openAt(log: List<ScreenEvent>) = OpenAt { a, b ->
        reads++
        log.filter { it.time in a until b }
    }

    @Test
    fun `an app on screen since before the read is put first`() {
        val log = listOf(ScreenEvent(from - 5 * hour, RESUMED, "feed"))
        val events = openAt(log).withOpenApp(from, now, now, emptyList())
        assertEquals(listOf(ScreenEvent(from, RESUMED, "feed")), events)
        // Counted from the stretch's start to now.
        assertEquals(mapOf("feed" to 3 * hour), ForegroundTime.totals(events, from + hour, now))
    }

    @Test
    fun `an app left before the read starts adds nothing`() {
        val log = listOf(ScreenEvent(from - 5 * hour, RESUMED, "feed"), ScreenEvent(from - 4 * hour, PAUSED, "feed"))
        val later = listOf(ScreenEvent(from + hour, RESUMED, "maps"))
        assertEquals(later, openAt(log).withOpenApp(from, now, now, later))
    }

    @Test
    fun `a screen that went off before the read starts adds nothing`() {
        val log = listOf(ScreenEvent(from - 5 * hour, RESUMED, "feed"), ScreenEvent(from - hour, SCREEN_OFF, "android"))
        assertEquals(emptyList<ScreenEvent>(), openAt(log).withOpenApp(from, now, now, emptyList()))
    }

    @Test
    fun `each start is read once, also when nothing was open`() {
        val open = openAt(listOf(ScreenEvent(from - 5 * hour, RESUMED, "feed")))
        repeat(50) { open.withOpenApp(from, now + it, now + it, emptyList()) }
        val closed = openAt(emptyList())
        repeat(50) { closed.withOpenApp(from, now + it, now + it, emptyList()) }
        assertEquals(2, reads)
    }

    @Test
    fun `a stretch that ended a while ago is left as it is`() {
        val open = openAt(listOf(ScreenEvent(from - 5 * hour, RESUMED, "feed")))
        assertEquals(emptyList<ScreenEvent>(), open.withOpenApp(from, now - hour, now, emptyList()))
        assertEquals(0, reads)
    }

    @Test
    fun `only a few starts are kept`() {
        val open = openAt(emptyList())
        repeat(OpenAt.KEPT + 1) { open.withOpenApp(from - it * hour, now, now, emptyList()) }
        // The first start was dropped to keep the others, so it is read again.
        open.withOpenApp(from, now, now, emptyList())
        assertEquals(OpenAt.KEPT + 2, reads)
    }
}
