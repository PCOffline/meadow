package dev.pcoffline.meadow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.readable
import kotlin.math.cos
import kotlin.math.sin

/**
 * A flower whose bloom mirrors how much time is left: wide open with plenty left,
 * closing and drooping as the limit approaches, wilted once it is used up.
 */
@Composable
fun BloomFlower(
    bloom: Float,
    petalColor: Color,
    modifier: Modifier = Modifier,
    petals: Int = 6,
    sway: Boolean = true,
    growIn: Boolean = true,
) {
    val garden = Garden.colors
    val reduced = rememberReducedMotion()
    val animatedBloom by animateFloatAsState(bloom.coerceIn(0f, 1f), tween(900), label = "bloom")
    // Sprouts up from the soil the first time it appears.
    val grow = remember { Animatable(if (growIn && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow))
    }
    // Read only while drawing, so the sway redraws the flower without recomposing it.
    val swayAngle: State<Float>? = if (sway && !reduced) {
        val transition = rememberInfiniteTransition(label = "sway")
        transition.animateFloat(
            initialValue = -2.5f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
            label = "swayAngle",
        )
    } else null
    // Reused every frame instead of allocating new paths.
    val stem = remember { Path() }
    val leafPath = remember { Path() }

    Canvas(modifier) {
        val angle = swayAngle?.value ?: 0f
        val wilt = 1f - animatedBloom
        val w = size.width
        val h = size.height
        val base = Offset(w / 2f, h * 0.94f)
        val head = Offset(w / 2f + wilt * wilt * w * 0.22f, h * 0.34f + wilt * wilt * h * 0.16f)

        // Soil mound.
        drawOval(garden.soil.copy(alpha = 0.55f), topLeft = Offset(w * 0.22f, h * 0.89f), size = Size(w * 0.56f, h * 0.09f))

        scale(grow.value, grow.value, pivot = base) {
        rotate(angle * (0.4f + animatedBloom), pivot = base) {
            stem.reset()
            stem.moveTo(base.x, base.y)
            stem.quadraticTo(w / 2f - w * 0.06f, h * 0.62f, head.x, head.y)
            drawPath(stem, lerp(garden.stem, garden.wilted, wilt * 0.7f), style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))
            leaf(Offset(w / 2f - w * 0.02f, h * 0.70f), w * 0.22f, -35f, lerp(garden.leaf, garden.wilted, wilt * 0.6f), leafPath)
            leaf(Offset(w / 2f + w * 0.01f, h * 0.60f), w * 0.18f, 200f, lerp(garden.leaf, garden.wilted, wilt * 0.6f), leafPath)
            flowerHead(head, w * 0.13f + w * 0.11f * animatedBloom, petals, lerp(petalColor, garden.wilted, wilt * 0.85f), animatedBloom, garden.petalHoney)
        }
        }
        if (animatedBloom < 0.15f) {
            // A couple of fallen petals.
            val fallen = lerp(petalColor, garden.wilted, 0.6f)
            drawOval(fallen, topLeft = Offset(w * 0.62f, h * 0.88f), size = Size(w * 0.10f, h * 0.035f))
            drawOval(fallen, topLeft = Offset(w * 0.28f, h * 0.90f), size = Size(w * 0.08f, h * 0.03f))
        }
    }
}

private fun DrawScope.leaf(at: Offset, length: Float, degrees: Float, color: Color, path: Path = Path()) {
    rotate(degrees, pivot = at) {
        path.reset()
        path.moveTo(at.x, at.y)
        path.quadraticTo(at.x + length * 0.5f, at.y - length * 0.35f, at.x + length, at.y)
        path.quadraticTo(at.x + length * 0.5f, at.y + length * 0.35f, at.x, at.y)
        path.close()
        drawPath(path, color)
    }
}

private fun DrawScope.flowerHead(center: Offset, radius: Float, petals: Int, color: Color, bloom: Float, heart: Color) {
    // Petals fold inwards and droop as the bloom fades.
    val spread = 0.45f + 0.55f * bloom
    val droop = (1f - bloom) * 50f
    for (i in 0 until petals) {
        val baseAngle = 360f / petals * i - 90f
        val a = baseAngle * spread + droop * (if (baseAngle > -90f) 1f else -1f) * 0.3f + (1f - spread) * 90f
        rotate(a, pivot = center) {
            drawOval(
                color = color,
                topLeft = Offset(center.x - radius * 0.32f, center.y - radius * 1.05f),
                size = Size(radius * 0.64f, radius * 1.05f),
            )
        }
    }
    drawCircle(heart, radius = radius * (0.26f + 0.08f * bloom), center = center)
    drawCircle(Color.White.copy(alpha = 0.35f), radius = radius * 0.08f, center = center + Offset(-radius * 0.08f, -radius * 0.08f))
}

/** A small flat daisy for decorating headers and empty states. */
@Composable
fun Daisy(modifier: Modifier = Modifier, petal: Color = Color.White, heart: Color = Garden.colors.petalHoney) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        for (i in 0 until 8) {
            val a = Math.toRadians((45.0 * i)).toFloat()
            val p = Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.55f)
            drawCircle(petal, radius = r * 0.36f, center = p)
        }
        drawCircle(heart, radius = r * 0.34f, center = c)
    }
}

/**
 * Rolling hills with a scatter of tiny flowers, drawn behind screen headers. Given the [sky]
 * behind them and the [text] drawn over them, the hills are shaded just enough to keep that text
 * readable (4.5:1).
 */
@Composable
fun MeadowHills(modifier: Modifier = Modifier, sky: Color? = null, text: Color? = null) {
    val garden = Garden.colors
    val (backColor, frontColor) = remember(garden, sky, text) {
        val back = garden.leaf.copy(alpha = 0.35f)
        val front = garden.leaf.copy(alpha = 0.55f)
        if (sky == null || text == null) {
            back to front
        } else {
            // Dark text lightens the hills, light text darkens them.
            val toward = if (text.luminance() > 0.5f) Color.Black else Color.White
            val solidBack = readable(back.compositeOver(sky), listOf(text), toward = toward)
            solidBack to readable(front.compositeOver(solidBack), listOf(text), toward = toward)
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val back = Path().apply {
            moveTo(0f, h * 0.55f)
            cubicTo(w * 0.25f, h * 0.30f, w * 0.55f, h * 0.70f, w, h * 0.38f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(back, backColor)
        val front = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.3f, h * 0.55f, w * 0.7f, h * 0.95f, w, h * 0.68f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(front, frontColor)
        val dots = listOf(0.08f to 0.82f, 0.21f to 0.72f, 0.37f to 0.80f, 0.63f to 0.86f, 0.78f to 0.74f, 0.91f to 0.80f)
        dots.forEachIndexed { i, (x, y) ->
            val color = garden.petals[i % garden.petals.size]
            translate(w * x, h * y) {
                for (k in 0 until 5) {
                    val a = Math.toRadians(72.0 * k).toFloat()
                    drawCircle(color, radius = 3.2f, center = Offset(cos(a) * 3.6f, sin(a) * 3.6f))
                }
                drawCircle(garden.petalHoney, radius = 2.2f, center = Offset.Zero)
            }
        }
    }
}

/** A thin vine with leaves, used as a section divider. */
@Composable
fun VineDivider(modifier: Modifier = Modifier) {
    val garden = Garden.colors
    Canvas(modifier) {
        val y = size.height / 2f
        val path = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = true
            while (x < size.width) {
                val next = x + 40f
                quadraticTo(x + 20f, if (up) y - 6f else y + 6f, next, y)
                x = next
                up = !up
            }
        }
        drawPath(path, garden.leaf.copy(alpha = 0.6f), style = Stroke(width = 2f))
        var x = 30f
        var flip = false
        while (x < size.width) {
            leaf(Offset(x, y), 14f, if (flip) 30f else -30f, garden.leaf.copy(alpha = 0.7f))
            x += 80f
            flip = !flip
        }
    }
}
