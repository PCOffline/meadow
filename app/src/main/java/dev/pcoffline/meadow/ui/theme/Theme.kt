@file:OptIn(ExperimentalTextApi::class)

package dev.pcoffline.meadow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pcoffline.meadow.R

/** Garden palette beyond Material's roles: petals, leaves and soil for drawings and charts. */
@Immutable
data class GardenColors(
    val petalPink: Color,
    val petalLavender: Color,
    val petalHoney: Color,
    val petalPeach: Color,
    val petalSky: Color,
    val leaf: Color,
    val leafDark: Color,
    val stem: Color,
    val soil: Color,
    val sky: Color,
    val wilted: Color,
    val cardTint: Color,
) {
    val petals: List<Color> get() = listOf(petalPink, petalLavender, petalHoney, petalPeach, petalSky)
}

/** How Meadow looks. Picked in Profile; each theme has a light and a dark version. */
enum class GardenTheme(val key: String, @StringRes val label: Int, val emoji: String) {
    MEADOW("meadow", R.string.theme_meadow, "🌼"),
    BLOSSOM("blossom", R.string.theme_blossom, "🌸"),
    LAVENDER("lavender", R.string.theme_lavender, "🪻"),
    SUNFLOWER("sunflower", R.string.theme_sunflower, "🌻"),
    POND("pond", R.string.theme_pond, "🪷"),
    MUSHROOM("mushroom", R.string.theme_mushroom, "🍄");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: MEADOW
    }
}

enum class DarkMode(val key: String, @StringRes val label: Int) {
    SYSTEM("system", R.string.dark_mode_system),
    LIGHT("light", R.string.dark_mode_light),
    DARK("dark", R.string.dark_mode_dark);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** A handful of colours each theme is grown from; everything else is derived so themes stay consistent. */
private data class Seed(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    /** Flower colours, the theme's own flower first. */
    val petals: List<Color>,
    val leaf: Color,
)

private val Seeds = mapOf(
    GardenTheme.MEADOW to Seed(
        Color(0xFF5B7F55), Color(0xFFB4646C), Color(0xFF7A6CA8), Color(0xFFFBF6EC),
        listOf(Color(0xFFE8A0A6), Color(0xFFB4A7D6), Color(0xFFE9B949), Color(0xFFF2B48C), Color(0xFF9CC3D5)),
        Color(0xFF8FB283),
    ),
    GardenTheme.BLOSSOM to Seed(
        Color(0xFFB5577A), Color(0xFF7F8F5A), Color(0xFF8E6FB0), Color(0xFFFFF6F7),
        listOf(Color(0xFFF4B6C8), Color(0xFFF9D3DF), Color(0xFFE8A0A6), Color(0xFFD7B6E0), Color(0xFFFFE1A8)),
        Color(0xFF9DB57E),
    ),
    GardenTheme.LAVENDER to Seed(
        Color(0xFF7461A8), Color(0xFFB26B8A), Color(0xFF5E8C7A), Color(0xFFFAF7FD),
        listOf(Color(0xFFB4A7D6), Color(0xFFCFC2EC), Color(0xFF9C8AC9), Color(0xFFE8A0A6), Color(0xFFF2D58C)),
        Color(0xFF95AE8C),
    ),
    GardenTheme.SUNFLOWER to Seed(
        Color(0xFFA8741A), Color(0xFF6E8A3A), Color(0xFFB0614A), Color(0xFFFFF9EC),
        listOf(Color(0xFFF2C14E), Color(0xFFF6D77F), Color(0xFFE9A23B), Color(0xFFF2B48C), Color(0xFFE8A0A6)),
        Color(0xFF94B061),
    ),
    GardenTheme.POND to Seed(
        Color(0xFF3F7F86), Color(0xFFC07A86), Color(0xFF6C7FB0), Color(0xFFF5FAF8),
        listOf(Color(0xFFF2B8C6), Color(0xFF9CC3D5), Color(0xFFB7DCD3), Color(0xFFE9D27A), Color(0xFFC7B6E6)),
        Color(0xFF7FB39E),
    ),
    GardenTheme.MUSHROOM to Seed(
        Color(0xFFA4553F), Color(0xFF7A6A4F), Color(0xFF6F8B5E), Color(0xFFFAF5EE),
        listOf(Color(0xFFD9735B), Color(0xFFF0C9A0), Color(0xFFE9B949), Color(0xFFC9A98B), Color(0xFFB4A7D6)),
        Color(0xFF8DA36F),
    ),
)

private val Ink = Color(0xFF3E3A32)
private val NightInk = Color(0xFFE9E3D6)

/** WCAG contrast ratio between two colours (1 to 21). */
internal fun contrast(a: Color, b: Color): Float {
    val (light, dark) = a.luminance().let { la -> b.luminance().let { lb -> if (la > lb) la to lb else lb to la } }
    return (light + 0.05f) / (dark + 0.05f)
}

/** Darkens [color] only as much as needed to be readable (WCAG AA) on every one of [backgrounds]. */
internal fun readable(color: Color, backgrounds: List<Color>, min: Float = 4.5f): Color {
    var t = 0f
    while (t <= 1f) {
        val candidate = lerp(color, Color.Black, t)
        if (backgrounds.all { contrast(candidate, it) >= min }) return candidate
        t += 0.02f
    }
    return Color.Black
}

/** The Material colour scheme for a theme; internal so tests can check every one stays readable. */
internal fun colorSchemeFor(theme: GardenTheme, dark: Boolean): androidx.compose.material3.ColorScheme =
    Seeds.getValue(theme).let { if (dark) darkScheme(it) else lightScheme(it) }

private fun lightScheme(s: Seed) = lightColorScheme(
    // Seeds are picked for charm; text and buttons are nudged darker where needed to stay readable.
    primary = readable(s.primary, listOf(Color.White, s.background)),
    onPrimary = Color.White,
    primaryContainer = lerp(s.primary, Color.White, 0.78f),
    onPrimaryContainer = lerp(s.primary, Color.Black, 0.6f),
    secondary = readable(s.secondary, listOf(Color.White, s.background)),
    onSecondary = Color.White,
    secondaryContainer = lerp(s.secondary, Color.White, 0.8f),
    onSecondaryContainer = lerp(s.secondary, Color.Black, 0.6f),
    tertiary = readable(s.tertiary, listOf(Color.White, s.background)),
    onTertiary = Color.White,
    tertiaryContainer = lerp(s.tertiary, Color.White, 0.8f),
    onTertiaryContainer = lerp(s.tertiary, Color.Black, 0.6f),
    background = s.background,
    onBackground = lerp(Ink, s.primary, 0.08f),
    surface = s.background,
    onSurface = lerp(Ink, s.primary, 0.08f),
    surfaceVariant = lerp(s.background, Color(0xFFB0A28A), 0.16f),
    onSurfaceVariant = lerp(Color(0xFF6F675A), s.primary, 0.15f),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = lerp(s.background, Color.White, 0.6f),
    surfaceContainer = lerp(s.background, Color(0xFFE0D6C4), 0.25f),
    surfaceContainerHigh = lerp(s.background, Color(0xFFE0D6C4), 0.4f),
    surfaceContainerHighest = lerp(s.background, Color(0xFFE0D6C4), 0.55f),
    outline = lerp(Color(0xFFB8AE9C), s.primary, 0.15f),
    outlineVariant = lerp(Color(0xFFDDD3C1), s.primary, 0.1f),
    error = readable(Color(0xFFC0634A), listOf(Color.White, s.background)),
    onError = Color.White,
    errorContainer = Color(0xFFF7DCD3),
    onErrorContainer = Color(0xFF4D1A0E),
)

private fun darkScheme(s: Seed): androidx.compose.material3.ColorScheme {
    val night = lerp(Color(0xFF1A1D1B), s.primary, 0.08f)
    return darkColorScheme(
        primary = lerp(s.primary, Color.White, 0.45f),
        onPrimary = lerp(s.primary, Color.Black, 0.65f),
        primaryContainer = lerp(s.primary, Color.Black, 0.45f),
        onPrimaryContainer = lerp(s.primary, Color.White, 0.8f),
        secondary = lerp(s.secondary, Color.White, 0.45f),
        onSecondary = lerp(s.secondary, Color.Black, 0.65f),
        secondaryContainer = lerp(s.secondary, Color.Black, 0.45f),
        onSecondaryContainer = lerp(s.secondary, Color.White, 0.8f),
        tertiary = lerp(s.tertiary, Color.White, 0.45f),
        onTertiary = lerp(s.tertiary, Color.Black, 0.65f),
        tertiaryContainer = lerp(s.tertiary, Color.Black, 0.45f),
        onTertiaryContainer = lerp(s.tertiary, Color.White, 0.8f),
        background = night,
        onBackground = NightInk,
        surface = night,
        onSurface = NightInk,
        surfaceVariant = lerp(night, Color.White, 0.14f),
        onSurfaceVariant = Color(0xFFC3BBAC),
        surfaceContainerLowest = lerp(night, Color.Black, 0.3f),
        surfaceContainerLow = lerp(night, Color.White, 0.03f),
        surfaceContainer = lerp(night, Color.White, 0.05f),
        surfaceContainerHigh = lerp(night, Color.White, 0.08f),
        surfaceContainerHighest = lerp(night, Color.White, 0.11f),
        outline = Color(0xFF8B8578),
        outlineVariant = lerp(night, Color.White, 0.2f),
        error = Color(0xFFF0A08B),
        onError = Color(0xFF4D1A0E),
        errorContainer = Color(0xFF6E3324),
        onErrorContainer = Color(0xFFF7DCD3),
    )
}

private fun gardenColors(s: Seed, dark: Boolean): GardenColors {
    val p = if (dark) s.petals.map { lerp(it, Color.White, 0.08f) } else s.petals
    val background = if (dark) lerp(Color(0xFF1A1D1B), s.primary, 0.08f) else s.background
    return GardenColors(
        petalPink = p[0],
        petalLavender = p[1],
        petalHoney = p[2],
        petalPeach = p[3],
        petalSky = p[4],
        leaf = if (dark) lerp(s.leaf, Color.Black, 0.08f) else s.leaf,
        leafDark = if (dark) lerp(s.leaf, Color.White, 0.25f) else lerp(s.leaf, Color.Black, 0.3f),
        stem = lerp(s.leaf, Color.Black, 0.18f),
        soil = if (dark) Color(0xFF6B5B47) else Color(0xFFB9A285),
        sky = if (dark) lerp(background, Color.White, 0.04f) else lerp(background, Color(0xFFE6DCC8), 0.3f),
        wilted = if (dark) Color(0xFF7D7363) else Color(0xFFC2B49A),
        cardTint = if (dark) lerp(background, Color.White, 0.06f) else lerp(background, Color.White, 0.6f),
    )
}

/** Soft circle colours behind emoji avatars, keyed so they survive theme changes. */
enum class AvatarColor(val key: String, val light: Color, val dark: Color) {
    LEAF("leaf", Color(0xFFDCE8D2), Color(0xFF3B5636)),
    PETAL("petal", Color(0xFFF8DDE0), Color(0xFF6A3A40)),
    LAVENDER("lavender", Color(0xFFE7E2F5), Color(0xFF4B4170)),
    HONEY("honey", Color(0xFFFBEBC0), Color(0xFF6B5420)),
    PEACH("peach", Color(0xFFFBE0CF), Color(0xFF6E4330)),
    SKY("sky", Color(0xFFDCEBF2), Color(0xFF2F4F5E)),
    MINT("mint", Color(0xFFD5F0E3), Color(0xFF2E5546)),
    CREAM("cream", Color(0xFFF3EBDB), Color(0xFF4E473B));

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: LEAF
    }
}

/** True while the dark version of the theme is showing. */
val LocalDarkTheme = staticCompositionLocalOf { false }

private fun fraunces(weight: Int) = Font(
    R.font.fraunces,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // Fraunces' "soft" axis rounds every terminal, which suits the garden.
        FontVariation.Setting("SOFT", 100f),
        FontVariation.Setting("WONK", 1f),
    ),
)

private fun nunito(weight: Int) = Font(
    R.font.nunito,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Display = FontFamily(fraunces(400), fraunces(600), fraunces(700))
private val Body = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800))

private val MeadowTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

private val MeadowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val LocalGarden = staticCompositionLocalOf { gardenColors(Seeds.getValue(GardenTheme.MEADOW), dark = false) }

object Garden {
    val colors: GardenColors
        @Composable get() = LocalGarden.current
}

@Composable
fun MeadowTheme(
    theme: GardenTheme = GardenTheme.MEADOW,
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val seed = Seeds.getValue(theme)
    val scheme = remember(theme, dark) { colorSchemeFor(theme, dark) }
    val garden = remember(theme, dark) { gardenColors(seed, dark) }
    CompositionLocalProvider(LocalGarden provides garden, LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, typography = MeadowTypography, shapes = MeadowShapes, content = content)
    }
}

/** Applies the theme the person picked in Profile. */
@Composable
fun MeadowAppTheme(content: @Composable () -> Unit) {
    val theme by dev.pcoffline.meadow.AppGraph.store.theme.collectAsStateWithLifecycle()
    val darkMode by dev.pcoffline.meadow.AppGraph.store.darkMode.collectAsStateWithLifecycle()
    MeadowTheme(GardenTheme.fromKey(theme), DarkMode.fromKey(darkMode)) {
        // Status and navigation bar icons follow the app's day/night choice, not the phone's.
        val dark = LocalDarkTheme.current
        val view = androidx.compose.ui.platform.LocalView.current
        if (!view.isInEditMode) {
            androidx.compose.runtime.SideEffect {
                val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        content()
    }
}
