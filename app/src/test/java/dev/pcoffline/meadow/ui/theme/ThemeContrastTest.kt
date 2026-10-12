package dev.pcoffline.meadow.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every theme, by day and by night, must keep text readable (WCAG AA: 4.5:1). */
class ThemeContrastTest {
    private fun check(label: String, foreground: Color, background: Color, min: Float = 4.5f) {
        val ratio = contrast(foreground, background)
        assertTrue("$label is only %.2f:1".format(ratio), ratio >= min)
    }

    @Test
    fun `text and buttons are readable in every theme`() {
        for (theme in GardenTheme.entries) for (dark in listOf(false, true)) {
            val s = colorSchemeFor(theme, dark)
            val name = "${theme.name}/${if (dark) "night" else "day"}"
            check("$name onBackground", s.onBackground, s.background)
            check("$name onSurface", s.onSurface, s.surface)
            check("$name onSurfaceVariant", s.onSurfaceVariant, s.background)
            check("$name onPrimary", s.onPrimary, s.primary)
            check("$name onSecondary", s.onSecondary, s.secondary)
            check("$name onTertiary", s.onTertiary, s.tertiary)
            check("$name primary text", s.primary, s.background)
            check("$name secondary text", s.secondary, s.background)
            check("$name error text", s.error, s.background)
            check("$name onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer)
            check("$name onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
            check("$name onErrorContainer", s.onErrorContainer, s.errorContainer)
        }
    }

    @Test
    fun `readable only darkens as much as it must`() {
        val cream = Color(0xFFFBF6EC)
        assertEquals(Color(0xFF3E3A32), readable(Color(0xFF3E3A32), cream))
        val nudged = readable(Color(0xFFE9B949), cream)
        assertTrue(contrast(nudged, cream) >= 4.5f)
        assertTrue(contrast(nudged, cream) < 5.2f)
    }

    @Test
    fun `stored keys map back to the same choice, with safe defaults`() {
        GardenTheme.entries.forEach { assertEquals(it, GardenTheme.fromKey(it.key)) }
        DarkMode.entries.forEach { assertEquals(it, DarkMode.fromKey(it.key)) }
        AvatarColor.entries.forEach { assertEquals(it, AvatarColor.fromKey(it.key)) }
        assertEquals(GardenTheme.MEADOW, GardenTheme.fromKey(null))
        assertEquals(DarkMode.SYSTEM, DarkMode.fromKey("nonsense"))
        assertEquals(AvatarColor.LEAF, AvatarColor.fromKey(""))
    }
}
