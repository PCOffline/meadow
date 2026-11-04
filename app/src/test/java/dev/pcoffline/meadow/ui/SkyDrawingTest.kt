package dev.pcoffline.meadow.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.components.cloud
import dev.pcoffline.meadow.ui.components.moon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The sky's clouds and moon, as drawn on a clear square. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SkyDrawingTest {
    private val side = 300

    private fun draw(block: DrawScope.() -> Unit): PixelMap {
        val image = ImageBitmap(side, side)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat()), block)
        return image.toPixelMap()
    }

    @Test
    fun `a see-through cloud is evenly see-through, with no paler patches where its puffs overlap`() {
        val pixels = draw { cloud(Offset(side / 2f, side / 2f), scale = 4f, alpha = 0.5f) }
        val alphas = (0 until side).flatMap { x -> (0 until side).map { y -> pixels[x, y].alpha } }.filter { it > 0f }
        assertTrue("the cloud is drawn", alphas.size > 2_000)
        assertTrue("no part is more opaque than the cloud: ${alphas.max()}", alphas.max() <= 0.51f)
    }

    @Test
    fun `the moon's dark side shows the glow behind it, not a disc of another colour`() {
        val center = Offset(side / 2f, side / 2f)
        val radius = 40f
        val pixels = draw { moon(center, radius) }
        val lit = pixels[(center.x - radius * 0.7f).toInt(), center.y.toInt()]
        val dark = pixels[(center.x + radius * 0.45f).toInt(), (center.y - radius * 0.25f).toInt()]
        val glow = pixels[(center.x - radius * 1.4f).toInt(), center.y.toInt()]
        assertEquals(1f, lit.alpha, 0.01f)
        assertEquals("the bite is the same faint glow as around the moon", glow.alpha, dark.alpha, 0.02f)
    }
}
