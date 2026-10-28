package dev.pcoffline.meadow.ui.theme

import androidx.compose.ui.graphics.Color
import dev.pcoffline.meadow.ui.components.AfternoonSky
import dev.pcoffline.meadow.ui.components.tinted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Every theme keeps the same gold sun and green grass, only their hue leaning a little its way,
 * and tints its daytime sky without turning it grey.
 */
class SunAndGrassTest {
    private fun apart(a: Float, b: Float) = abs((a - b + 540f).mod(360f) - 180f)

    private fun lightness(c: Color) = (maxOf(c.red, c.green, c.blue) + minOf(c.red, c.green, c.blue)) / 2f

    @Test
    fun `a hue is read in degrees`() {
        assertEquals(0f, hueOf(Color.Red), 0.5f)
        assertEquals(120f, hueOf(Color.Green), 0.5f)
        assertEquals(240f, hueOf(Color.Blue), 0.5f)
        assertEquals(0f, hueOf(Color.Gray), 0.5f)
    }

    @Test
    fun `leaning turns the hue the short way, by no more than allowed, and keeps the lightness`() {
        val gold = Color(0xFFF4C04E)
        val leant = leanTowards(gold, Color(0xFFB5577A), share = 0.5f, maxDegrees = 8f)
        // Pink is behind gold on the wheel, so the hue goes down, and only 8 degrees.
        assertEquals(hueOf(gold) - 8f, hueOf(leant), 1f)
        assertEquals(lightness(gold), lightness(leant), 0.01f)
        assertEquals(hueOf(gold) + 3f, hueOf(leanTowards(gold, hueColor(hueOf(gold) + 30f), share = 0.1f, maxDegrees = 8f)), 1f)
    }

    @Test
    fun `every theme's sun and grass stay close to the same gold and green`() {
        val gold = hueOf(Color(0xFFF4C04E))
        val green = hueOf(Color(0xFF8DB57A))
        GardenTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val garden = gardenColorsFor(theme, dark)
                assertTrue("$theme sun", apart(hueOf(garden.sun), gold) <= 8.5f)
                assertTrue("$theme grass", apart(hueOf(garden.grass), green) <= 12.5f)
            }
        }
    }

    @Test
    fun `the sun is no longer a petal colour`() {
        GardenTheme.entries.forEach { theme ->
            val garden = gardenColorsFor(theme, false)
            assertTrue(theme.name, garden.petals.none { it == garden.sun })
        }
    }

    private fun saturation(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        return if (max == min) 0f else (max - min) / (1f - abs(max + min - 1f))
    }

    @Test
    fun `every theme's afternoon sky is a clear pale colour, not grey`() {
        GardenTheme.entries.forEach { theme ->
            val sky = tinted(AfternoonSky, gardenColorsFor(theme, dark = false))
            assertTrue("$theme sky is greyish: $sky", saturation(sky) >= 0.25f)
            assertTrue("$theme sky is too dark for the ink on it: $sky", lightness(sky) >= 0.8f)
        }
    }
}

private fun hueColor(degrees: Float) = Color.hsl(degrees.mod(360f), 0.8f, 0.6f)
