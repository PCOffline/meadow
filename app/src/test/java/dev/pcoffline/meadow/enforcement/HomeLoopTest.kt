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
    fun `a floating window that was there before a limited app was left isn't that app's`() {
        // Another app's video call, floating for a minute before the limited app rests. How long
        // after the app was left the window appeared is negative: it was there first.
        assertNull(PipOwner.of(windowPackage = null, lastLimitedLeft = "com.reels", leftAgoMs = -60_000))
        assertNull(PipOwner.of(windowPackage = null, lastLimitedLeft = "com.reels", leftAgoMs = -PipOwner.JUST_BEFORE_MS - 1))
    }

    @Test
    fun `a floating window reported a moment before its app is left is still that app's`() {
        assertEquals("com.reels", PipOwner.of(windowPackage = null, lastLimitedLeft = "com.reels", leftAgoMs = -300))
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
