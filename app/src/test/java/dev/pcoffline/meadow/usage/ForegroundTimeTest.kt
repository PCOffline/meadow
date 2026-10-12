package dev.pcoffline.meadow.usage

import dev.pcoffline.meadow.usage.ScreenEvent.Kind.PAUSED
import dev.pcoffline.meadow.usage.ScreenEvent.Kind.RESUMED
import dev.pcoffline.meadow.usage.ScreenEvent.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTimeTest {
    private fun e(time: Long, kind: ScreenEvent.Kind, pkg: String = "a") = ScreenEvent(time, kind, pkg)

    @Test
    fun `counts time between resume and pause`() {
        val totals = ForegroundTime.totals(listOf(e(100, RESUMED), e(400, PAUSED)), start = 0, end = 1_000)
        assertEquals(mapOf("a" to 300L), totals)
    }

    @Test
    fun `an app still open is counted up to the end`() {
        val totals = ForegroundTime.totals(listOf(e(600, RESUMED)), start = 0, end = 1_000)
        assertEquals(400L, totals["a"])
    }

    @Test
    fun `time before the window starts is not counted`() {
        val totals = ForegroundTime.totals(listOf(e(-500, RESUMED), e(200, PAUSED)), start = 0, end = 1_000)
        assertEquals(200L, totals["a"])
    }

    @Test
    fun `switching apps closes the previous one`() {
        val events = listOf(e(0, RESUMED, "a"), e(100, RESUMED, "b"), e(250, PAUSED, "b"))
        assertEquals(mapOf("a" to 100L, "b" to 150L), ForegroundTime.totals(events, start = 0, end = 1_000))
    }

    @Test
    fun `a pause from a background app does not stop the foreground one`() {
        val events = listOf(e(0, RESUMED, "a"), e(50, PAUSED, "b"), e(100, PAUSED, "a"))
        assertEquals(mapOf("a" to 100L), ForegroundTime.totals(events, start = 0, end = 1_000))
    }

    @Test
    fun `screen off stops the clock`() {
        val events = listOf(e(0, RESUMED), e(300, SCREEN_OFF, "android"))
        assertEquals(300L, ForegroundTime.totals(events, start = 0, end = 1_000)["a"])
    }

    @Test
    fun `resuming the same app twice is one session`() {
        val events = listOf(e(0, RESUMED), e(100, RESUMED), e(200, PAUSED))
        assertEquals(200L, ForegroundTime.totals(events, start = 0, end = 1_000)["a"])
    }

    @Test
    fun `foreground replay follows resumes, pauses and screen off`() {
        assertEquals("b", ForegroundTime.foregroundAfter("a", listOf(e(1, RESUMED, "b"))))
        assertNull(ForegroundTime.foregroundAfter("a", listOf(e(1, PAUSED, "a"))))
        assertEquals("a", ForegroundTime.foregroundAfter("a", listOf(e(1, PAUSED, "b"))))
        assertNull(ForegroundTime.foregroundAfter("a", listOf(e(1, SCREEN_OFF, "x"))))
        assertEquals("a", ForegroundTime.foregroundAfter("a", emptyList()))
    }
}
