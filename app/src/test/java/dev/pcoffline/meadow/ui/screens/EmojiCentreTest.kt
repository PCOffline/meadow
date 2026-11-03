package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.emojiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/** An emoji sits in the middle of its circle (#70). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EmojiCentreTest : ScreenTest() {
    @Test
    fun `an emoji is drawn in the middle of its circle`() {
        val tint = Color(0xFFFFFFFF)
        show { FlowerAvatar("🌻", Modifier.testTag("avatar"), size = 96.dp, tint = tint) }
        val shot = compose.onNodeWithTag("avatar").captureToImage().toPixelMap()
        var left = shot.width; var right = -1; var top = shot.height; var bottom = -1
        // Inside the circle only, away from its edge, where the page shows through.
        val r = shot.width / 2f * 0.8f
        for (y in 0 until shot.height) for (x in 0 until shot.width) {
            val dx = x - shot.width / 2f; val dy = y - shot.height / 2f
            if (dx * dx + dy * dy > r * r) continue
            if (shot[x, y].toArgb() != tint.toArgb()) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("something is drawn", right >= 0)
        val slack = 2 * 3 // 2 dp at xxhdpi
        assertTrue("across: ${(left + right) / 2f} of ${shot.width}", abs((left + right) / 2f - shot.width / 2f) <= slack)
        assertTrue("down: ${(top + bottom) / 2f} of ${shot.height}", abs((top + bottom) / 2f - shot.height / 2f) <= slack)
    }

    @Test
    fun `the emoji's line is cut to the glyph, without the font's own padding`() {
        val style = emojiStyle(24.sp)
        assertEquals(LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both), style.lineHeightStyle)
        assertEquals(24.sp, style.lineHeight)
        assertFalse(style.platformStyle?.paragraphStyle?.includeFontPadding ?: true)
    }
}
