package dev.pcoffline.meadow.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.components.butterfly
import dev.pcoffline.meadow.ui.components.wingbeat
import dev.pcoffline.meadow.ui.theme.ButterflyLook
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.butterfliesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The butterflies as drawn: crisp at any size, and their wings really beat. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ButterflyDrawingTest {
    private val side = 200

    /** [look] drawn large in the middle of a clear square, as its pixels. */
    private fun draw(look: ButterflyLook, fold: Float): IntArray {
        val image = ImageBitmap(side, side)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat())) {
            butterfly(look, Offset(side / 2f, side / 2f), length = side * 0.38f, fold = fold)
        }
        val pixels = image.toPixelMap()
        return IntArray(side * side) { i -> pixels[i % side, i / side].toArgb() }
    }

    private fun IntArray.painted() = count { it != 0 }

    @Test
    fun `every butterfly draws, and its two sides mirror each other`() {
        GardenTheme.entries.flatMap(::butterfliesFor).forEach { look ->
            val pixels = draw(look, fold = 0f)
            assertTrue("$look", pixels.painted() > side * side / 10)
            val left = (0 until side).sumOf { y -> (0 until side / 2).count { x -> pixels[y * side + x] != 0 } }
            val right = (0 until side).sumOf { y -> (side / 2 until side).count { x -> pixels[y * side + x] != 0 } }
            assertTrue("$look: $left against $right", kotlin.math.abs(left - right) < side * 2)
        }
    }

    @Test
    fun `a theme's two butterflies look different`() {
        GardenTheme.entries.forEach { theme ->
            val (one, two) = butterfliesFor(theme).map { draw(it, fold = 0f) }
            assertNotEquals(theme.name, one.toList(), two.toList())
        }
    }

    @Test
    fun `raised wings cover less of the sky than spread ones`() {
        val look = butterfliesFor(GardenTheme.MEADOW).first()
        assertTrue(draw(look, fold = 1f).painted() < draw(look, fold = 0f).painted() * 0.6f)
    }

    @Test
    fun `a wingbeat goes from spread to raised and back, lifting slowly and beating down quickly`() {
        assertEquals(0f, wingbeat(0f), 1e-4f)
        assertEquals(0f, wingbeat(1f), 1e-4f)
        val samples = (0..100).map { wingbeat(it / 100f) }
        assertTrue(samples.all { it in 0f..1f })
        val top = samples.indexOf(samples.max())
        // Raised past halfway through the beat: the downstroke is the shorter half.
        assertTrue("top at $top", top > 50)
        assertEquals(1f, samples.max(), 0.01f)
    }
}
