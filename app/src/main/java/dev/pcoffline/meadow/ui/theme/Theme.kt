@file:OptIn(ExperimentalTextApi::class)

package dev.pcoffline.meadow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
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
    /** Bars and progress: [leaf], deepened (by night, lightened) to stand out from any surface (3:1). */
    val growth: Color,
    /** Highlighted bars and nearly-full progress: [petalPink], made to stand out the same way. */
    val growthHigh: Color,
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
        Color(0xFFB5577A), Color(0xFF7F8F5A), Color(0xFF8E6FB0), Color(0xFFFCEBEE),
        listOf(Color(0xFFF4B6C8), Color(0xFFF9D3DF), Color(0xFFE8A0A6), Color(0xFFD7B6E0), Color(0xFFFFE1A8)),
        Color(0xFF9DB57E),
    ),
    GardenTheme.LAVENDER to Seed(
        Color(0xFF7461A8), Color(0xFFB26B8A), Color(0xFF5E8C7A), Color(0xFFF2EEFA),
        listOf(Color(0xFFB4A7D6), Color(0xFFCFC2EC), Color(0xFF9C8AC9), Color(0xFFE8A0A6), Color(0xFFF2D58C)),
        Color(0xFF95AE8C),
    ),
    GardenTheme.SUNFLOWER to Seed(
        Color(0xFFA8741A), Color(0xFF6E8A3A), Color(0xFFB0614A), Color(0xFFFDF3D8),
        listOf(Color(0xFFF2C14E), Color(0xFFF6D77F), Color(0xFFE9A23B), Color(0xFFF2B48C), Color(0xFFE8A0A6)),
        Color(0xFF94B061),
    ),
    GardenTheme.POND to Seed(
        Color(0xFF3F7F86), Color(0xFFC07A86), Color(0xFF6C7FB0), Color(0xFFE7F3F1),
        listOf(Color(0xFFF2B8C6), Color(0xFF9CC3D5), Color(0xFFB7DCD3), Color(0xFFE9D27A), Color(0xFFC7B6E6)),
        Color(0xFF7FB39E),
    ),
    GardenTheme.MUSHROOM to Seed(
        Color(0xFFA4553F), Color(0xFF7A6A4F), Color(0xFF6F8B5E), Color(0xFFF6EBE0),
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

/**
 * Moves [color] towards [toward] (black by default) only as much as needed to reach [min]:1
 * against every one of [backgrounds]: 4.5 for text (WCAG AA), 3 for control edges and icons.
 */
internal fun readable(color: Color, backgrounds: List<Color>, min: Float = 4.5f, toward: Color = Color.Black): Color {
    var t = 0f
    while (t <= 1f) {
        val candidate = lerp(color, toward, t)
        if (backgrounds.all { contrast(candidate, it) >= min }) return candidate
        t += 0.02f
    }
    return toward
}

/** Black or white, whichever reads better on [background]; one of them always reaches 4.5:1. */
fun contentOn(background: Color): Color =
    if (contrast(Color.Black, background) >= contrast(Color.White, background)) Color.Black else Color.White

/** The Material colour scheme for a theme; internal so tests can check every one stays readable. */
internal fun colorSchemeFor(theme: GardenTheme, dark: Boolean): androidx.compose.material3.ColorScheme =
    Seeds.getValue(theme).let { if (dark) darkScheme(it) else lightScheme(it) }

/** The garden palette for a theme; internal so tests can check the colours used behind text. */
internal fun gardenColorsFor(theme: GardenTheme, dark: Boolean): GardenColors = gardenColors(Seeds.getValue(theme), dark)

private val LightErrorContainer = Color(0xFFF7DCD3)
private val DarkErrorContainer = Color(0xFF6E3324)

private fun nightOf(s: Seed) = lerp(Color(0xFF1A1D1B), s.primary, 0.14f)

private fun cardTintOf(s: Seed, dark: Boolean) =
    if (dark) lerp(lerp(nightOf(s), s.primary, 0.08f), Color.White, 0.06f) else lerp(s.background, Color.White, 0.5f)

private fun lightScheme(s: Seed): androidx.compose.material3.ColorScheme {
    // Every surface is the theme's background, shaded towards its primary colour.
    val surfaces = listOf(0.04f, 0.07f, 0.10f, 0.14f).map { lerp(s.background, s.primary, it) }
    val lowest = lerp(s.background, Color.White, 0.7f)
    val variant = lerp(s.background, s.primary, 0.12f)
    val primaryContainer = lerp(s.primary, Color.White, 0.78f)
    val secondaryContainer = lerp(s.secondary, Color.White, 0.8f)
    val tertiaryContainer = lerp(s.tertiary, Color.White, 0.8f)
    val backdrops = listOf(Color.White, s.background, lowest, variant, cardTintOf(s, dark = false), primaryContainer,
        secondaryContainer, tertiaryContainer, LightErrorContainer) + surfaces
    // Seeds are picked for charm; text, icons and edges are nudged darker where needed to stay readable.
    fun text(color: Color) = readable(color, backdrops)
    val ink = text(lerp(Ink, s.primary, 0.08f))
    val inverse = lerp(Ink, s.primary, 0.1f)
    return lightColorScheme(
        primary = text(s.primary),
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = lerp(s.primary, Color.Black, 0.6f),
        secondary = text(s.secondary),
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = lerp(s.secondary, Color.Black, 0.6f),
        tertiary = text(s.tertiary),
        onTertiary = Color.White,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = lerp(s.tertiary, Color.Black, 0.6f),
        background = s.background,
        onBackground = ink,
        surface = s.background,
        onSurface = ink,
        surfaceVariant = variant,
        onSurfaceVariant = text(lerp(Color(0xFF6F675A), s.primary, 0.15f)),
        surfaceContainerLowest = lowest,
        surfaceContainerLow = surfaces[0],
        surfaceContainer = surfaces[1],
        surfaceContainerHigh = surfaces[2],
        surfaceContainerHighest = surfaces[3],
        // Text fields, switches and segmented buttons are recognised by their edge (WCAG 1.4.11: 3:1).
        outline = readable(lerp(Color(0xFFB8AE9C), s.primary, 0.35f), backdrops, min = 3f),
        outlineVariant = lerp(s.background, s.primary, 0.22f),
        error = text(Color(0xFFC0634A)),
        onError = Color.White,
        errorContainer = LightErrorContainer,
        onErrorContainer = Color(0xFF4D1A0E),
        // Snackbars.
        inverseSurface = inverse,
        inverseOnSurface = lerp(s.background, Color.White, 0.5f),
        inversePrimary = readable(lerp(s.primary, Color.White, 0.45f), listOf(inverse), toward = Color.White),
        surfaceTint = s.primary,
    )
}

private fun darkScheme(s: Seed): androidx.compose.material3.ColorScheme {
    val night = nightOf(s)
    val surfaces = listOf(0.06f to 0.03f, 0.08f to 0.05f, 0.10f to 0.08f, 0.12f to 0.11f)
        .map { (tint, light) -> lerp(lerp(night, s.primary, tint), Color.White, light) }
    val lowest = lerp(night, Color.Black, 0.3f)
    val variant = lerp(lerp(night, s.primary, 0.2f), Color.White, 0.1f)
    val primaryContainer = lerp(s.primary, Color.Black, 0.45f)
    val secondaryContainer = lerp(s.secondary, Color.Black, 0.45f)
    val tertiaryContainer = lerp(s.tertiary, Color.Black, 0.45f)
    val backdrops = listOf(night, lowest, variant, cardTintOf(s, dark = true), primaryContainer, secondaryContainer,
        tertiaryContainer, DarkErrorContainer) + surfaces
    // At night, text is nudged lighter instead.
    fun text(color: Color) = readable(color, backdrops, toward = Color.White)
    val ink = text(NightInk)
    return darkColorScheme(
        primary = text(lerp(s.primary, Color.White, 0.45f)),
        onPrimary = lerp(s.primary, Color.Black, 0.65f),
        primaryContainer = primaryContainer,
        onPrimaryContainer = lerp(s.primary, Color.White, 0.8f),
        secondary = text(lerp(s.secondary, Color.White, 0.45f)),
        onSecondary = lerp(s.secondary, Color.Black, 0.65f),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = lerp(s.secondary, Color.White, 0.8f),
        tertiary = text(lerp(s.tertiary, Color.White, 0.45f)),
        onTertiary = lerp(s.tertiary, Color.Black, 0.65f),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = lerp(s.tertiary, Color.White, 0.8f),
        background = night,
        onBackground = ink,
        surface = night,
        onSurface = ink,
        surfaceVariant = variant,
        onSurfaceVariant = text(Color(0xFFC3BBAC)),
        surfaceContainerLowest = lowest,
        surfaceContainerLow = surfaces[0],
        surfaceContainer = surfaces[1],
        surfaceContainerHigh = surfaces[2],
        surfaceContainerHighest = surfaces[3],
        outline = readable(lerp(Color(0xFF8B8578), s.primary, 0.3f), backdrops, min = 3f, toward = Color.White),
        outlineVariant = lerp(night, lerp(s.primary, Color.White, 0.4f), 0.22f),
        error = text(Color(0xFFF0A08B)),
        onError = Color(0xFF4D1A0E),
        errorContainer = DarkErrorContainer,
        onErrorContainer = Color(0xFFF7DCD3),
        inverseSurface = NightInk,
        inverseOnSurface = night,
        inversePrimary = readable(s.primary, listOf(NightInk)),
        surfaceTint = s.primary,
    )
}

private fun gardenColors(s: Seed, dark: Boolean): GardenColors {
    val p = if (dark) s.petals.map { lerp(it, Color.White, 0.08f) } else s.petals
    val background = if (dark) nightOf(s) else s.background
    val leaf = if (dark) lerp(s.leaf, Color.Black, 0.08f) else s.leaf
    val cardTint = cardTintOf(s, dark)
    // Charts and progress carry meaning, so they need 3:1 against whatever they're drawn on (WCAG 1.4.11).
    val scheme = if (dark) darkScheme(s) else lightScheme(s)
    val surfaces = with(scheme) {
        listOf(
            background, surfaceVariant, surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh,
            surfaceContainerHighest, primaryContainer, secondaryContainer, tertiaryContainer, errorContainer, cardTint,
        )
    }
    fun visible(color: Color) = readable(color, surfaces, min = 3f, toward = if (dark) Color.White else Color.Black)
    return GardenColors(
        petalPink = p[0],
        petalLavender = p[1],
        petalHoney = p[2],
        petalPeach = p[3],
        petalSky = p[4],
        leaf = leaf,
        leafDark = if (dark) lerp(s.leaf, Color.White, 0.25f) else lerp(s.leaf, Color.Black, 0.3f),
        stem = lerp(s.leaf, Color.Black, 0.18f),
        soil = if (dark) Color(0xFF6B5B47) else Color(0xFFB9A285),
        sky = if (dark) lerp(background, Color.White, 0.04f) else lerp(background, Color(0xFFE6DCC8), 0.3f),
        wilted = if (dark) Color(0xFF7D7363) else Color(0xFFC2B49A),
        cardTint = cardTint,
        growth = visible(leaf),
        growthHigh = visible(p[0]),
    )
}

/** Soft circle colours behind emoji avatars, keyed so they survive theme changes. */
enum class AvatarColor(val key: String, @StringRes val label: Int, val light: Color, val dark: Color) {
    LEAF("leaf", R.string.avatar_color_leaf, Color(0xFFDCE8D2), Color(0xFF3B5636)),
    PETAL("petal", R.string.avatar_color_petal, Color(0xFFF8DDE0), Color(0xFF6A3A40)),
    LAVENDER("lavender", R.string.avatar_color_lavender, Color(0xFFE7E2F5), Color(0xFF4B4170)),
    HONEY("honey", R.string.avatar_color_honey, Color(0xFFFBEBC0), Color(0xFF6B5420)),
    PEACH("peach", R.string.avatar_color_peach, Color(0xFFFBE0CF), Color(0xFF6E4330)),
    SKY("sky", R.string.avatar_color_sky, Color(0xFFDCEBF2), Color(0xFF2F4F5E)),
    MINT("mint", R.string.avatar_color_mint, Color(0xFFD5F0E3), Color(0xFF2E5546)),
    CREAM("cream", R.string.avatar_color_cream, Color(0xFFF3EBDB), Color(0xFF4E473B));

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
        MaterialTheme(colorScheme = scheme, typography = MeadowTypography, shapes = MeadowShapes) {
            // Screens drawn straight on the background (onboarding, the rest screen) have no Surface
            // to set a text colour, and Compose's default is black, unreadable at night.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
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
