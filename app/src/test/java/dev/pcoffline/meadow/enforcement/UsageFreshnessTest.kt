package dev.pcoffline.meadow.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How often the day's usage log is read: it is heavy for the phone, so not more than needed. */
class UsageFreshnessTest {
    @Test
    fun `usage just measured serves a forced read too`() {
        // Several askers at once (a watcher for each window change) share one read.
        assertTrue(LimitEngine.freshEnough(ageMs = 0, force = true))
        assertTrue(LimitEngine.freshEnough(ageMs = LimitEngine.FORCED_REFRESH_MS - 1, force = true))
        assertFalse(LimitEngine.freshEnough(ageMs = LimitEngine.FORCED_REFRESH_MS, force = true))
    }

    @Test
    fun `without force, usage serves for longer`() {
        assertTrue(LimitEngine.freshEnough(ageMs = LimitEngine.REFRESH_MS - 1, force = false))
        assertFalse(LimitEngine.freshEnough(ageMs = LimitEngine.REFRESH_MS, force = false))
    }

    @Test
    fun `after the clock is set back, usage is measured again`() {
        assertFalse(LimitEngine.freshEnough(ageMs = -5_000, force = false))
        assertFalse(LimitEngine.freshEnough(ageMs = -1, force = true))
    }
}
