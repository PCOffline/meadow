package dev.pcoffline.meadow.ui.theme

import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.baseSky
import dev.pcoffline.meadow.ui.components.celestialProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chosen mode sets the whole look (#74), and Clock follows the time of day (#83). */
class DayNightTest {
    @Test
    fun `Clock is light by day and dark by night, whatever the phone says`() {
        assertFalse(DarkMode.CLOCK.isDarkAt(hour = 12, systemDark = true))
        assertFalse(DarkMode.CLOCK.isDarkAt(hour = 5, systemDark = true))
        assertFalse(DarkMode.CLOCK.isDarkAt(hour = 20, systemDark = true))
        assertTrue(DarkMode.CLOCK.isDarkAt(hour = 21, systemDark = false))
        assertTrue(DarkMode.CLOCK.isDarkAt(hour = 0, systemDark = false))
        assertTrue(DarkMode.CLOCK.isDarkAt(hour = 4, systemDark = false))
    }

    @Test
    fun `the other modes ignore the hour`() {
        (0..23).forEach { h ->
            assertTrue(DarkMode.SYSTEM.isDarkAt(h, systemDark = true))
            assertFalse(DarkMode.SYSTEM.isDarkAt(h, systemDark = false))
            assertFalse(DarkMode.LIGHT.isDarkAt(h, systemDark = true))
            assertTrue(DarkMode.DARK.isDarkAt(h, systemDark = false))
        }
    }

    @Test
    fun `Clock is saved with a key the backend accepts`() {
        assertEquals(DarkMode.CLOCK, DarkMode.fromKey(DarkMode.CLOCK.key))
        assertTrue("Firestore rules allow up to 10 characters", DarkMode.entries.all { it.key.length <= 10 })
    }

    @Test
    fun `a light page never has a night sky over it`() {
        (0..23).forEach { h -> assertFalse("light at $h:00", baseSky(h, dark = false, greeting = R.string.greeting_night).night) }
    }

    @Test
    fun `a dark page always has a night sky over it`() {
        (0..23).forEach { h -> assertTrue("dark at $h:00", baseSky(h, dark = true, greeting = R.string.greeting_afternoon).night) }
    }

    @Test
    fun `on a light page at night the sun stays set, rather than rising again`() {
        // At 20:59 the sun is about to set; after 21:00 it stays down at the end of its arc.
        listOf(21, 23, 1, 4).forEach { h -> assertEquals("at $h:00", 1f, celestialProgress(h, 0, night = false), 0f) }
        assertTrue(celestialProgress(12, 0, night = false) < 1f)
    }

    @Test
    fun `on a dark page the moon keeps its own timing`() {
        assertEquals(0f, celestialProgress(21, 0, night = true), 0.001f)
        assertEquals(0.5f, celestialProgress(1, 0, night = true), 0.001f)
    }


    @Test
    fun `the rest screen's sky follows the chosen look too`() {
        assertTrue(dev.pcoffline.meadow.enforcement.restingSky(dark = true).night)
        assertFalse(dev.pcoffline.meadow.enforcement.restingSky(dark = false).night)
    }

}
