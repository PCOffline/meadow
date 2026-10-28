package dev.pcoffline.meadow.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Time on limited websites: counted only while watched, kept on the phone, never for long. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SiteClockTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private var clock = 1_000_000_000_000L
    private val yt = "site:youtube.com"
    private val reddit = "site:reddit.com"

    private fun newClock() = SiteClock(app) { clock }

    private fun watch(clock: SiteClock, site: String, seconds: Int) {
        clock.seen(site)
        repeat(seconds / 10) {
            this.clock += 10_000
            clock.seen(site)
        }
    }

    @Before
    fun clean() {
        app.getSharedPreferences("meadow_sites", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `time is counted while a site is watched, and stops when it's left`() {
        val sites = newClock()
        val start = clock
        watch(sites, yt, 60)
        assertEquals(yt, sites.current())
        assertEquals(mapOf(yt to 60_000L), sites.usageBetween(start, clock))
        sites.left()
        assertNull(sites.current())
        clock += 120_000
        assertEquals(mapOf(yt to 60_000L), sites.usageBetween(start, clock))
    }

    @Test
    fun `a long silence ends the visit where it was last seen`() {
        val sites = newClock()
        val start = clock
        watch(sites, yt, 30)
        // The screen went off for ten minutes; then the site is seen again.
        clock += 10 * 60_000
        assertNull(sites.current())
        assertEquals(mapOf(yt to 30_000L), sites.usageBetween(start, clock))
        watch(sites, yt, 20)
        assertEquals(mapOf(yt to 50_000L), sites.usageBetween(start, clock))
        // Left after a silence: nothing more is counted for it.
        clock += 5 * 60_000
        sites.left()
        assertEquals(mapOf(yt to 50_000L), sites.usageBetween(start, clock))
    }

    @Test
    fun `switching sites ends one visit and starts the other`() {
        val sites = newClock()
        val start = clock
        watch(sites, yt, 40)
        watch(sites, reddit, 20)
        assertEquals(mapOf(yt to 40_000L, reddit to 20_000L), sites.usageBetween(start, clock))
        assertEquals(reddit, sites.current())
    }

    @Test
    fun `usage is clipped to the time asked about`() {
        val sites = newClock()
        val start = clock
        watch(sites, yt, 60)
        assertEquals(mapOf(yt to 30_000L), sites.usageBetween(start + 30_000, clock + 60_000))
        assertEquals(emptyMap<String, Long>(), sites.usageBetween(clock, start))
        assertEquals(emptyMap<String, Long>(), sites.usageBetween(start - 60_000, start))
    }

    @Test
    fun `visits survive a restart, even one Meadow was stopped in the middle of`() {
        val start = clock
        newClock().apply { watch(this, yt, 30); left() }
        val stopped = newClock()
        watch(stopped, reddit, 40)
        // Stopped without the visit ending: what was saved of it still counts.
        val restarted = newClock()
        val counted = restarted.usageBetween(start, clock)
        assertEquals(30_000L, counted[yt])
        // Saved every few seconds as it went: at most that much of it is lost.
        assertTrue("the visit in progress was lost: $counted", (counted[reddit] ?: 0L) >= 35_000L)
        assertNull(restarted.current())
    }

    @Test
    fun `only the last eight days are kept`() {
        val sites = newClock()
        val start = clock
        watch(sites, yt, 30)
        sites.left()
        clock += 9 * 24 * 60 * 60_000L
        watch(sites, reddit, 10)
        sites.left()
        assertEquals(mapOf(reddit to 10_000L), sites.usageBetween(start, clock))
    }

    @Test
    fun `a clock set back doesn't count time backwards`() {
        val sites = newClock()
        val start = clock
        watch(sites, yt, 30)
        clock -= 60_000
        sites.seen(yt)
        clock += 10_000
        sites.seen(yt)
        assertEquals(mapOf(yt to 40_000L), sites.usageBetween(start - 120_000, start + 120_000))
    }

    @Test
    fun `clearing forgets everything`() {
        val sites = newClock()
        val start = clock
        watch(sites, yt, 30)
        sites.clear()
        assertEquals(emptyMap<String, Long>(), sites.usageBetween(start, clock))
        assertFalse(newClock().usageBetween(start, clock).isNotEmpty())
        val stored = app.getSharedPreferences("meadow_sites", Context.MODE_PRIVATE).all
        assertTrue(stored.toString(), stored.isEmpty())
    }
}
