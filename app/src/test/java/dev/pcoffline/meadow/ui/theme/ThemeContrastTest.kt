package dev.pcoffline.meadow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every theme, by day and by night, keeps text readable (WCAG AA 1.4.3: 4.5:1) and the edges
 * that identify controls visible (1.4.11: 3:1) on every surface they can be drawn on. Screens
 * are free to pair any text role with any surface, so every pair is checked, not just the
 * Material defaults. [RenderedContrastTest] then checks what screens actually draw.
 */
class ThemeContrastTest {
    private val looks = GardenTheme.entries.flatMap { theme -> listOf(false, true).map { theme to it } }

    private fun name(theme: GardenTheme, dark: Boolean) = "${theme.name}/${if (dark) "night" else "day"}"

    /** Collects every failure, so one run shows all that needs fixing. */
    private fun failures(check: (MutableList<String>, GardenTheme, Boolean) -> Unit): List<String> =
        mutableListOf<String>().also { out -> looks.forEach { (theme, dark) -> check(out, theme, dark) } }

    private fun MutableList<String>.require(label: String, foreground: Color, background: Color, min: Float) {
        val ratio = contrast(foreground, background)
        if (ratio < min) add("$label is %.2f:1, needs %.1f:1".format(ratio, min))
    }

    private fun assertNone(problems: List<String>) =
        assertTrue("Contrast problems:\n" + problems.joinToString("\n") { "- $it" }, problems.isEmpty())

    private val ColorScheme.textRoles
        get() = mapOf(
            "onBackground" to onBackground, "onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant,
            "primary" to primary, "secondary" to secondary, "tertiary" to tertiary, "error" to error,
        )

    private fun ColorScheme.surfaceRoles(garden: GardenColors) = mapOf(
        "background" to background, "surface" to surface, "surfaceVariant" to surfaceVariant,
        "surfaceContainerLowest" to surfaceContainerLowest, "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest, "primaryContainer" to primaryContainer,
        "secondaryContainer" to secondaryContainer, "tertiaryContainer" to tertiaryContainer,
        "errorContainer" to errorContainer, "cardTint" to garden.cardTint,
    )

    @Test
    fun `every text colour is readable on every surface`() = assertNone(
        failures { out, theme, dark ->
            val scheme = colorSchemeFor(theme, dark)
            val surfaces = scheme.surfaceRoles(gardenColorsFor(theme, dark))
            for ((text, fg) in scheme.textRoles) for ((surface, bg) in surfaces) {
                // With headroom over WCAG's 4.5:1, so text still passes once drawn (see TEXT_TARGET).
                out.require("${name(theme, dark)} $text on $surface", fg, bg, TEXT_TARGET)
            }
        },
    )

    @Test
    fun `colours made for each other are readable together`() = assertNone(
        failures { out, theme, dark ->
            val s = colorSchemeFor(theme, dark)
            val pairs = mapOf(
                "onPrimary on primary (filled buttons, checkboxes)" to (s.onPrimary to s.primary),
                "onSecondary on secondary" to (s.onSecondary to s.secondary),
                "onTertiary on tertiary" to (s.onTertiary to s.tertiary),
                "onError on error (badges)" to (s.onError to s.error),
                "onPrimaryContainer on primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                "onSecondaryContainer on secondaryContainer (selected chips, tab bar)" to (s.onSecondaryContainer to s.secondaryContainer),
                "onTertiaryContainer on tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
                "onErrorContainer on errorContainer" to (s.onErrorContainer to s.errorContainer),
                "inverseOnSurface on inverseSurface (snackbars)" to (s.inverseOnSurface to s.inverseSurface),
                "inversePrimary on inverseSurface (snackbar actions)" to (s.inversePrimary to s.inverseSurface),
            )
            val g = gardenColorsFor(theme, dark)
            for ((label, colors) in pairs + ("onBetter on better (less screen time)" to (g.onBetter to g.better))) {
                out.require("${name(theme, dark)} $label", colors.first, colors.second, 4.5f)
            }
        },
    )

    @Test
    fun `control edges stand out from every surface`() = assertNone(
        failures { out, theme, dark ->
            val scheme = colorSchemeFor(theme, dark)
            // Outlined text fields, switches, radio buttons and segmented buttons are found by their edge.
            for ((surface, bg) in scheme.surfaceRoles(gardenColorsFor(theme, dark))) {
                out.require("${name(theme, dark)} outline on $surface", scheme.outline, bg, 3f)
            }
        },
    )

    @Test
    fun `filled controls stand out from the page`() = assertNone(
        failures { out, theme, dark ->
            val s = colorSchemeFor(theme, dark)
            // Filled buttons, checked checkboxes and switches, and progress are found by their fill.
            for ((surface, bg) in s.surfaceRoles(gardenColorsFor(theme, dark))) {
                out.require("${name(theme, dark)} primary fill on $surface", s.primary, bg, 3f)
            }
        },
    )

    @Test
    fun `charts and progress stand out from every surface`() = assertNone(
        failures { out, theme, dark ->
            val s = colorSchemeFor(theme, dark)
            val garden = gardenColorsFor(theme, dark)
            // Bars and progress fills carry information (WCAG 1.4.11), unlike the decorative flowers.
            val fills = mapOf("growth" to garden.growth, "growthHigh" to garden.growthHigh, "error (a full bar)" to s.error)
            for ((fill, color) in fills) for ((surface, bg) in s.surfaceRoles(garden)) {
                out.require("${name(theme, dark)} $fill on $surface", color, bg, 3f)
            }
        },
    )

    @Test
    fun `black or white is always readable on any colour`() {
        for (argb in 0..0xFFFFFF step 0x010307) {
            val color = Color(0xFF000000.toInt() or argb)
            val ratio = contrast(contentOn(color), color)
            assertTrue("%06X gets %.2f:1".format(argb, ratio), ratio >= 4.5f)
        }
    }

    @Test
    fun `readable only moves a colour as much as it must`() {
        val cream = Color(0xFFFBF6EC)
        assertEquals(Color(0xFF3E3A32), readable(Color(0xFF3E3A32), listOf(cream)))
        val nudged = readable(Color(0xFFE9B949), listOf(cream))
        assertTrue(contrast(nudged, cream) >= TEXT_TARGET)
        assertTrue(contrast(nudged, cream) < TEXT_TARGET + 0.7f)
        val night = Color(0xFF1F2420)
        val lifted = readable(Color(0xFF5B7F55), listOf(night), toward = Color.White)
        assertTrue(contrast(lifted, night) >= TEXT_TARGET)
        assertTrue(lifted.red > Color(0xFF5B7F55).red)
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
