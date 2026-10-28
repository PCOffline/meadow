package dev.pcoffline.meadow.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every app's bar in Growth is its share of the screen time, whether it has a limit or not. */
class ShareOfTest {
    @Test
    fun `a bar is the app's share of the screen time, the number written under it`() {
        assertEquals(0.35f, shareOf(ms = 35, total = 100), 0.0001f)
        assertEquals(1f, shareOf(ms = 100, total = 100), 0.0001f)
    }

    @Test
    fun `with no screen time every bar is empty`() {
        assertEquals(0f, shareOf(ms = 0, total = 0), 0f)
    }
}
