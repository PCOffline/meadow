package dev.pcoffline.meadow.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The colour that an icon shows most. */
class IconColourTest {
    private fun icon(vararg parts: Pair<Int, Int>): IntArray = parts.flatMap { (argb, count) -> List(count) { argb } }.toIntArray()

    private fun assertHue(expected: Float, colour: IconColour) {
        assertTrue("$colour has colour", !colour.grey)
        val d = ((colour.h - expected + 540f) % 360f) - 180f
        assertTrue("hue ${colour.h} is near $expected", kotlin.math.abs(d) < 8f)
    }

    @Test
    fun `a red icon is red, and a blue one blue`() {
        assertHue(29f, IconColour.of(icon(0xFFFF0000.toInt() to 100)))
        assertHue(264f, IconColour.of(icon(0xFF0000FF.toInt() to 100)))
    }

    @Test
    fun `a coloured logo on white or black shows its colour`() {
        assertHue(29f, IconColour.of(icon(0xFFFFFFFF.toInt() to 900, 0xFFFF0000.toInt() to 100)))
        assertHue(264f, IconColour.of(icon(0xFF000000.toInt() to 900, 0xFF0000FF.toInt() to 100)))
    }

    @Test
    fun `see-through pixels count for nothing`() {
        assertHue(29f, IconColour.of(icon(0x200000FF to 900, 0xFFFF0000.toInt() to 100)))
    }

    @Test
    fun `black, white and grey icons have no colour`() {
        assertEquals(IconColour.Grey, IconColour.of(icon(0xFFFFFFFF.toInt() to 50, 0xFF000000.toInt() to 50, 0xFF808080.toInt() to 50)))
        assertEquals(IconColour.Grey, IconColour.of(IntArray(0)))
        assertEquals(IconColour.Grey, IconColour.of(icon(0x00FF0000 to 100)))
    }

    @Test
    fun `a speck of colour isn't enough`() {
        assertEquals(IconColour.Grey, IconColour.of(icon(0xFFFFFFFF.toInt() to 990, 0xFFFF0000.toInt() to 10)))
    }

    @Test
    fun `the colour that covers more wins`() {
        assertHue(264f, IconColour.of(icon(0xFFFF0000.toInt() to 30, 0xFF0000FF.toInt() to 70)))
    }
}
