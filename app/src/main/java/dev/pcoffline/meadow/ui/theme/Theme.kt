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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val LightGarden = GardenColors(
    petalPink = Color(0xFFE8A0A6),
    petalLavender = Color(0xFFB4A7D6),
    petalHoney = Color(0xFFE9B949),
    petalPeach = Color(0xFFF2B48C),
    petalSky = Color(0xFF9CC3D5),
    leaf = Color(0xFF8FB283),
    leafDark = Color(0xFF5B7F55),
    stem = Color(0xFF6E9466),
    soil = Color(0xFFB9A285),
    sky = Color(0xFFF3EBDB),
    wilted = Color(0xFFC2B49A),
    cardTint = Color(0xFFFFFDF7),
)

private val DarkGarden = GardenColors(
    petalPink = Color(0xFFE7A9AE),
    petalLavender = Color(0xFFBDB2DD),
    petalHoney = Color(0xFFE6C36A),
    petalPeach = Color(0xFFEDB995),
    petalSky = Color(0xFFA6C9D9),
    leaf = Color(0xFF86A87A),
    leafDark = Color(0xFFA8C69F),
    stem = Color(0xFF7FA376),
    soil = Color(0xFF6B5B47),
    sky = Color(0xFF232B26),
    wilted = Color(0xFF7D7363),
    cardTint = Color(0xFF29312C),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B7F55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8D2),
    onPrimaryContainer = Color(0xFF1E3319),
    secondary = Color(0xFFB4646C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8DDE0),
    onSecondaryContainer = Color(0xFF4A1E23),
    tertiary = Color(0xFF7A6CA8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7E2F5),
    onTertiaryContainer = Color(0xFF2D2450),
    background = Color(0xFFFBF6EC),
    onBackground = Color(0xFF3E3A32),
    surface = Color(0xFFFBF6EC),
    onSurface = Color(0xFF3E3A32),
    surfaceVariant = Color(0xFFEFE7D7),
    onSurfaceVariant = Color(0xFF6F675A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF7),
    surfaceContainer = Color(0xFFF7F0E2),
    surfaceContainerHigh = Color(0xFFF2EADA),
    surfaceContainerHighest = Color(0xFFECE3D1),
    outline = Color(0xFFB8AE9C),
    outlineVariant = Color(0xFFDDD3C1),
    error = Color(0xFFC0634A),
    onError = Color.White,
    errorContainer = Color(0xFFF7DCD3),
    onErrorContainer = Color(0xFF4D1A0E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA8C69F),
    onPrimary = Color(0xFF173012),
    primaryContainer = Color(0xFF3B5636),
    onPrimaryContainer = Color(0xFFDCE8D2),
    secondary = Color(0xFFE7A9AE),
    onSecondary = Color(0xFF4A1E23),
    secondaryContainer = Color(0xFF6A3A40),
    onSecondaryContainer = Color(0xFFF8DDE0),
    tertiary = Color(0xFFC6BBE8),
    onTertiary = Color(0xFF2D2450),
    tertiaryContainer = Color(0xFF4B4170),
    onTertiaryContainer = Color(0xFFE7E2F5),
    background = Color(0xFF1C221E),
    onBackground = Color(0xFFE9E3D6),
    surface = Color(0xFF1C221E),
    onSurface = Color(0xFFE9E3D6),
    surfaceVariant = Color(0xFF39413B),
    onSurfaceVariant = Color(0xFFC3BBAC),
    surfaceContainerLowest = Color(0xFF151A17),
    surfaceContainerLow = Color(0xFF222924),
    surfaceContainer = Color(0xFF262D28),
    surfaceContainerHigh = Color(0xFF2D3530),
    surfaceContainerHighest = Color(0xFF353D37),
    outline = Color(0xFF8B8578),
    outlineVariant = Color(0xFF4A524C),
    error = Color(0xFFF0A08B),
    onError = Color(0xFF4D1A0E),
    errorContainer = Color(0xFF6E3324),
    onErrorContainer = Color(0xFFF7DCD3),
)

private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif

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

val LocalGarden = staticCompositionLocalOf { LightGarden }

object Garden {
    val colors: GardenColors
        @Composable get() = LocalGarden.current
}

@Composable
fun MeadowTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGarden provides if (dark) DarkGarden else LightGarden) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = MeadowTypography,
            shapes = MeadowShapes,
            content = content,
        )
    }
}
