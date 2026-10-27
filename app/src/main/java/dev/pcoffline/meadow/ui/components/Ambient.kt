package dev.pcoffline.meadow.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDateTime
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.key
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import dev.pcoffline.meadow.R
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.xml.image.ImageUtil
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.butterfliesFor
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** True when the person has turned animations off in system settings. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Seconds since first composition, advancing every frame. Read it only inside draw lambdas so
 * it redraws without recomposing. Frozen at 0 when animations are off.
 */
@Composable
fun rememberClock(): State<Float> {
    val reduced = rememberReducedMotion()
    return produceState(0f, reduced) {
        if (reduced) return@produceState
        // The "infinite" variant lets tests and accessibility tools pause ambient motion.
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
}

/** Presses shrink a touch and spring back. Pass the same source to the clickable. */
fun Modifier.pressBounce(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressBounce",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

@Immutable
/** The sky behind a header. [onSky] is readable on all of it, hills included. */
data class SkyMood(val top: Color, val bottom: Color, val night: Boolean, val onSky: Color, @StringRes val greeting: Int)

/**
 * The current time, refreshed whenever the screen comes back into view, so a garden left open
 * in the morning doesn't still say "good morning" in the evening.
 */
@Composable
fun rememberResumedNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LifecycleResumeEffect(Unit) {
        now = LocalDateTime.now()
        onPauseOrDispose { }
    }
    return now
}

/** Pins the sky to an hour of the day (screenshots); null follows the clock. */
val LocalSkyHour = androidx.compose.runtime.staticCompositionLocalOf<Int?> { null }

/** The sky follows the clock: peach mornings, pale-blue days, lavender evenings, starry nights. */
@Composable
fun rememberSkyMood(): SkyMood {
    val dark = dev.pcoffline.meadow.ui.theme.LocalDarkTheme.current
    val pinned = LocalSkyHour.current
    val hour = pinned ?: rememberResumedNow().hour
    val greeting = when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..21 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
    val ink = Color(0xFF3E3A32)
    val base = when {
        dark || hour !in 5..20 -> SkyMood(Color(0xFF26304A), Color(0xFF3A4658), true, Color(0xFFF3EEE2), greeting)
        hour <= 10 -> SkyMood(Color(0xFFFCE1CC), Color(0xFFFBF3E6), false, ink, greeting)
        hour <= 16 -> SkyMood(Color(0xFFD9ECF2), Color(0xFFF8F4E8), false, ink, greeting)
        else -> SkyMood(Color(0xFFE6D3EC), Color(0xFFF9E4D6), false, ink, greeting)
    }
    // The sky takes on the theme's colour and fades into the page below it.
    val scheme = MaterialTheme.colorScheme
    return base.copy(
        top = androidx.compose.ui.graphics.lerp(base.top, scheme.primary, if (base.night) 0.10f else 0.14f),
        // A night sky over a light page keeps its own colours, so its light text stays readable.
        bottom = if (base.night && !dark) base.bottom else androidx.compose.ui.graphics.lerp(base.bottom, scheme.background, 0.6f),
    )
}

/**
 * The living backdrop for headers: sky gradient, sun or moon, drifting clouds, rolling hills with
 * tiny flowers, the theme's two butterflies by day and fireflies and stars by night.
 */
@Composable
fun SkyScene(mood: SkyMood, modifier: Modifier = Modifier) {
    val garden = Garden.colors
    val theme = Garden.theme
    val butterflies = remember(theme) { butterfliesFor(theme) }
    val clock = rememberClock()
    val stars = remember { List(28) { Offset(Random(it * 31).nextFloat(), Random(it * 17 + 3).nextFloat() * 0.55f) } }
    val sky = remember(mood.top, mood.bottom) { Brush.verticalGradient(listOf(mood.top, mood.bottom)) }
    Box(modifier) {
        // Each animated layer redraws on its own, without redrawing the still hills between them.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val t = clock.value
            drawRect(sky)
            if (mood.night) {
                stars.forEachIndexed { i, p ->
                    val twinkle = 0.35f + 0.65f * abs(sin(t * 0.9f + i * 1.7f))
                    drawCircle(Color.White.copy(alpha = twinkle * 0.85f), radius = if (i % 5 == 0) 2.4f else 1.5f, center = Offset(p.x * size.width, p.y * size.height))
                }
                moon(Offset(size.width * 0.82f, size.height * 0.24f), 22.dp.toPx(), mood.top)
            } else {
                sun(Offset(size.width * 0.84f, size.height * 0.22f), 20.dp.toPx(), t, garden.petalHoney)
            }
            val cloudAlpha = if (mood.night) 0.10f else 0.85f
            cloud(Offset(((t * 9f) % (size.width + 240f)) - 120f, size.height * 0.18f), 1f, cloudAlpha)
            cloud(Offset(((t * 5.5f + size.width * 0.55f) % (size.width + 240f)) - 120f, size.height * 0.32f), 0.7f, cloudAlpha)
        }
        MeadowHills(Modifier.fillMaxSize(), sky = mood.bottom, text = mood.onSky)
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val t = clock.value
            if (mood.night) {
                repeat(6) { i ->
                    val x = size.width * (0.1f + 0.15f * i) + sin(t * 0.6f + i) * 18f
                    val y = size.height * (0.62f + 0.05f * (i % 3)) + cos(t * 0.8f + i * 2f) * 10f
                    val glow = 0.4f + 0.6f * abs(sin(t * 1.3f + i))
                    drawCircle(garden.petalHoney.copy(alpha = 0.25f * glow), radius = 9f, center = Offset(x, y))
                    drawCircle(garden.petalHoney.copy(alpha = glow), radius = 2.6f, center = Offset(x, y))
                }
            } else {
                // The theme's two butterflies, the smaller one further off and higher up.
                flutter(butterflies[1], t, seed = 2.1f, lane = 0.38f, length = 9.5.dp.toPx(), speed = 0.19f)
                flutter(butterflies[0], t, seed = 0f, lane = 0.52f, length = 13.dp.toPx(), speed = 0.25f)
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, t: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.18f), radius = radius * 2.1f, center = center)
    drawCircle(color.copy(alpha = 0.28f), radius = radius * 1.5f, center = center)
    rotate(t * 8f, pivot = center) {
        repeat(10) { i ->
            val a = (2 * PI * i / 10).toFloat()
            drawLine(
                color.copy(alpha = 0.7f),
                start = center + Offset(cos(a), sin(a)) * (radius * 1.3f),
                end = center + Offset(cos(a), sin(a)) * (radius * 1.75f),
                strokeWidth = radius * 0.16f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(color, radius = radius, center = center)
}

private fun DrawScope.moon(center: Offset, radius: Float, sky: Color) {
    drawCircle(Color(0xFFFFF6DC).copy(alpha = 0.15f), radius = radius * 1.8f, center = center)
    drawCircle(Color(0xFFFFF6DC), radius = radius, center = center)
    drawCircle(sky, radius = radius * 0.86f, center = center + Offset(radius * 0.45f, -radius * 0.25f))
}

private fun DrawScope.cloud(at: Offset, scale: Float, alpha: Float) {
    val c = Color.White.copy(alpha = alpha)
    val r = 18f * scale * density / 2.5f
    drawCircle(c, r * 1.4f, at)
    drawCircle(c, r * 1.1f, at + Offset(-r * 1.5f, r * 0.35f))
    drawCircle(c, r * 1.2f, at + Offset(r * 1.5f, r * 0.3f))
    drawRoundRect(
        c,
        topLeft = at + Offset(-r * 2.4f, r * 0.2f),
        size = Size(r * 4.8f, r * 1.25f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.62f),
    )
}

private data class Petal(val x: Float, val speed: Float, val sway: Float, val phase: Float, val size: Float, val colorIndex: Int)

/** Petals drifting down slowly, forever. Purely decorative. */
@Composable
fun FallingPetals(modifier: Modifier = Modifier, count: Int = 14) {
    val garden = Garden.colors
    val clock = rememberClock()
    val petals = remember(count) {
        val r = Random(42)
        List(count) { Petal(r.nextFloat(), 0.04f + r.nextFloat() * 0.05f, 10f + r.nextFloat() * 24f, r.nextFloat() * 6f, 5f + r.nextFloat() * 5f, r.nextInt(5)) }
    }
    Canvas(modifier) {
        val t = clock.value
        petals.forEach { p ->
            val progress = ((t * p.speed + p.phase) % 1f)
            val x = p.x * size.width + sin(t * 1.1f + p.phase * 3f) * p.sway
            val y = progress * (size.height + 40f) - 20f
            val s = p.size * density / 2f
            rotate(t * 40f + p.phase * 60f, pivot = Offset(x, y)) {
                drawOval(garden.petals[p.colorIndex].copy(alpha = 0.75f), topLeft = Offset(x - s, y - s * 0.6f), size = Size(s * 2f, s * 1.2f))
            }
        }
    }
}

/**
 * A celebratory shower of petals, drawn by Konfetti. Plays once each time [trigger] changes
 * to a new positive value; draws nothing while idle or when animations are off.
 */
@Composable
fun PetalBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger <= 0 || rememberReducedMotion()) return
    val colors = Garden.colors.petals.map { it.toArgb() }
    val context = LocalContext.current
    val flower = remember { ContextCompat.getDrawable(context, R.drawable.ic_flower) }
    val shapes = listOfNotNull(Shape.Circle, Shape.Rectangle(0.55f), flower?.let { ImageUtil.loadDrawable(it) })
    // Re-keying restarts the particle system for every new celebration.
    key(trigger) {
        KonfettiView(
            modifier = modifier,
            parties = listOf(
                Party(
                    angle = Angle.TOP,
                    spread = 100,
                    speed = 18f,
                    maxSpeed = 42f,
                    damping = 0.9f,
                    colors = colors,
                    shapes = shapes,
                    timeToLive = 2600,
                    position = Position.Relative(0.5, 0.35),
                    emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(70),
                ),
            ),
        )
    }
}

/** A seed that sprouts, unfurls two leaves, and starts again. Used instead of a spinner. */
@Composable
fun SproutLoader(modifier: Modifier = Modifier, label: String? = stringResource(R.string.loading_garden), size: Dp = 64.dp) {
    val garden = Garden.colors
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "sprout")
    val growing = transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "grow",
    )
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(size)) {
                // Without animations, a still sprout with its leaves out.
                val grow = if (reduced) 0.8f else growing.value
                val w = this.size.width
                val h = this.size.height
                val stem = (grow * 1.4f).coerceAtMost(1f)
                val leaves = ((grow - 0.45f) / 0.4f).coerceIn(0f, 1f)
                val fade = if (grow > 0.9f) (1f - grow) * 10f else 1f
                drawOval(garden.soil.copy(alpha = 0.6f * fade), topLeft = Offset(w * 0.25f, h * 0.84f), size = Size(w * 0.5f, h * 0.12f))
                val top = Offset(w / 2f, h * 0.86f - h * 0.5f * stem)
                drawLine(garden.stem.copy(alpha = fade), Offset(w / 2f, h * 0.86f), top, strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                if (leaves > 0f) {
                    val l = w * 0.22f * leaves
                    rotate(-35f, pivot = top) { drawOval(garden.leaf.copy(alpha = fade), topLeft = top - Offset(l, l * 0.3f), size = Size(l, l * 0.6f)) }
                    rotate(35f, pivot = top) { drawOval(garden.leaf.copy(alpha = fade), topLeft = top - Offset(0f, l * 0.3f), size = Size(l, l * 0.6f)) }
                }
            }
            if (label != null) {
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Little "z"s drifting up from a sleeping flower. */
@Composable
fun SleepyZs(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "zzz")
    val t = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "z")
    // Decoration: screen readers skip it.
    Box(modifier.size(36.dp, 40.dp).clearAndSetSemantics { }) {
        repeat(3) { i ->
            Text(
                "z",
                fontSize = (10 + 4 * i).sp,
                color = MaterialTheme.colorScheme.tertiary,
                // Animated in the layer only, so the text itself is never recomposed.
                modifier = Modifier.graphicsLayer {
                    val p = if (reduced) 0.2f + i * 0.25f else (t.value + i / 3f) % 1f
                    alpha = (1f - p) * 0.9f
                    translationX = (6 + i * 8).dp.toPx() + sin(p * 6f) * 3f
                    translationY = (1f - p) * 28.dp.toPx()
                },
            )
        }
    }
}

