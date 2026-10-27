package dev.pcoffline.meadow.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The block screen never traps the phone between Home and itself. */
class HomeLoopTest {
    @Test
    fun `a floating window is the app's that owns it`() {
        assertEquals("com.video", PipOwner.of(windowPackage = "com.video", lastLimitedLeft = "com.reels", leftAgoMs = 100))
    }

    @Test
    fun `another app's floating window isn't blamed on an app that rested a while ago`() {
        assertNull(PipOwner.of(windowPackage = null, lastLimitedLeft = "com.reels", leftAgoMs = 60_000))
    }

    @Test
    fun `a floating window of unknown owner, appearing as a limited app is left, is that app's`() {
        assertEquals("com.reels", PipOwner.of(windowPackage = null, lastLimitedLeft = "com.reels", leftAgoMs = 500))
        assertNull(PipOwner.of(windowPackage = null, lastLimitedLeft = null, leftAgoMs = 500))
    }

    @Test
    fun `Home is used for an app at most once in a while`() {
        val guard = HomeGuard(intervalMs = 30_000)
        assertTrue(guard.allow("com.reels", now = 1_000))
        assertFalse(guard.allow("com.reels", now = 2_000))
        assertFalse(guard.allow("com.reels", now = 30_999))
        assertTrue(guard.allow("com.reels", now = 31_000))
    }

    @Test
    fun `each app has its own allowance`() {
        val guard = HomeGuard(intervalMs = 30_000)
        assertTrue(guard.allow("com.reels", now = 1_000))
        assertTrue(guard.allow("com.video", now = 1_500))
    }
}
