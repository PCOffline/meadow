package dev.pcoffline.meadow.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The colour arithmetic the themes are built with: hues, a lean towards another hue, and readable text. */
class ColourMathTest {
    @Test
    fun `a hue reads from whichever channel is strongest, and grey has none`() {
        assertEquals(0f, hueOf(Color.Red), 0.5f)
        assertEquals(120f, hueOf(Color.Green), 0.5f)
        assertEquals(240f, hueOf(Color.Blue), 0.5f)
        // Magenta: red is strongest, and its hue wraps round below 360.
        assertEquals(300f, hueOf(Color(1f, 0f, 1f)), 0.5f)
        assertEquals(0f, hueOf(Color.Gray), 0f)
    }

    @Test
    fun `a lean turns the short way round, by no more than it may`() {
        // From red towards magenta is 60 degrees back, past 0.
        assertEquals(330f, hueOf(leanTowards(Color.Red, Color(1f, 0f, 1f), share = 0.5f, maxDegrees = 90f)), 1f)
        // From blue towards green is 120 degrees back, but only 20 are allowed.
        assertEquals(220f, hueOf(leanTowards(Color.Blue, Color.Green, share = 1f, maxDegrees = 20f)), 1f)
        // Grey has no hue to turn, and stays grey.
        val grey = leanTowards(Color.Gray, Color.Blue, share = 1f, maxDegrees = 90f)
        assertEquals(grey.red, grey.blue, 0.01f)
    }

    @Test
    fun `a colour is darkened only as much as text needs, and one that can't get there ends at the end`() {
        val light = Color(0xFFCCCCCC)
        val readable = readable(light, listOf(Color.White))
        assertTrue(contrast(readable, Color.White) >= TEXT_TARGET)
        assertTrue(contrast(readable, Color.White) < TEXT_TARGET + 1f)
        // Nothing reads at 25:1, so it goes all the way to black.
        assertEquals(Color.Black, readable(light, listOf(Color.White), min = 25f))
        assertEquals(21f, contrast(Color.Black, Color.White), 0.1f)
        assertEquals(21f, contrast(Color.White, Color.Black), 0.1f)
    }
}
