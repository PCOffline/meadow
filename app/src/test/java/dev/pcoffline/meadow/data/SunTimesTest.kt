package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.theme.DarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SunTimesTest {
    private val london = ZoneId.of("Europe/London")
    private fun minutes(hour: Int, minute: Int) = hour * 60 + minute
    private fun assertNear(expected: Int, actual: Int) = assertTrue("$actual is not within 5 minutes of $expected", abs(expected - actual) <= 5)

    @Test
    fun `london's sunrise and sunset match the published times`() {
        val june = SunTimes.at(LocalDate.of(2026, 6, 21), 51.51, -0.13, london)
        assertNear(minutes(4, 43), june.riseMinute)
        assertNear(minutes(21, 21), june.setMinute)
        val december = SunTimes.at(LocalDate.of(2026, 12, 21), 51.51, -0.13, london)
        assertNear(minutes(8, 4), december.riseMinute)
        assertNear(minutes(15, 54), december.setMinute)
    }

    @Test
    fun `far in the north in june, it is day at midnight`() {
        val sun = SunTimes.at(LocalDate.of(2026, 6, 21), 70.0, 25.0, ZoneId.of("Europe/Oslo"))
        assertEquals(SunTimes.Polar.DAY, sun.polar)
        assertFalse(sun.isNight(0))
    }

    @Test
    fun `far in the north in december, it is night at noon`() {
        val sun = SunTimes.at(LocalDate.of(2026, 12, 21), 75.0, 25.0, ZoneId.of("Europe/Oslo"))
        assertTrue(sun.isNight(minutes(12, 0)))
    }

    @Test
    fun `without a place, night is from 19 00 to 5 00`() {
        val sun = SunTimes.DEFAULT
        assertTrue(sun.isNight(minutes(4, 59)))
        assertFalse(sun.isNight(minutes(5, 0)))
        assertFalse(sun.isNight(minutes(18, 59)))
        assertTrue(sun.isNight(minutes(19, 0)))
        assertEquals(0.5f, sun.progress(minutes(12, 0)), 0.001f)
        assertEquals(0.5f, sun.progress(minutes(0, 0)), 0.001f)
    }

    @Test
    fun `with no place and a time zone with no main place, it uses the default`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(SunTimes.DEFAULT, SunTimes.today(context, null, ZoneId.of("UTC"), LocalDate.of(2026, 6, 21)))
    }

    @Test
    fun `with no place, it uses the time zone's main place`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sun = SunTimes.today(context, null, london, LocalDate.of(2026, 6, 21))
        assertNotEquals(SunTimes.DEFAULT, sun)
        assertNear(minutes(21, 21), sun.setMinute)
    }

    @Test
    fun `the weather's place comes before the time zone's`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // Edinburgh, in London's time zone, has a later summer sunset.
        val sun = SunTimes.today(context, Place(55.95, -3.19), london, LocalDate.of(2026, 6, 21))
        assertNear(minutes(22, 3), sun.setMinute)
    }

    @Test
    fun `clock is dark a minute after sunset and light a minute after sunrise`() {
        val sun = SunTimes.at(LocalDate.of(2026, 12, 21), 51.51, -0.13, london)
        val afterSet = sun.setMinute + 1
        val afterRise = sun.riseMinute + 1
        assertTrue(DarkMode.CLOCK.isDarkAt(afterSet / 60, systemDark = false, afterSet % 60, sun))
        assertFalse(DarkMode.CLOCK.isDarkAt(afterRise / 60, systemDark = true, afterRise % 60, sun))
        assertFalse(DarkMode.CLOCK.isDarkAt((sun.setMinute - 1) / 60, systemDark = false, (sun.setMinute - 1) % 60, sun))
    }
}
