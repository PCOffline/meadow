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
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.readable
import kotlin.math.cos
import kotlin.math.sin

/** Whether a [BloomFlower] is drawn asleep; read by tests, never announced. */
val FlowerAsleep = SemanticsPropertyKey<Boolean>("FlowerAsleep")
private var SemanticsPropertyReceiver.flowerAsleep by FlowerAsleep

/**
 * A flower whose bloom mirrors how much time is left: wide open with plenty left,
 * closing and drooping as the limit approaches, wilted once it is used up. [asleep] folds it into
 * a closed bud nodding off, keeping its colour: resting outside its hours, not used up. In the
 * mushroom forest it's a mushroom instead, its cap [petalColor]: spread wide with plenty left,
 * closing and drooping the same way.
 */
@Composable
fun BloomFlower(
    bloom: Float,
    petalColor: Color,
    modifier: Modifier = Modifier,
    petals: Int = 6,
    sway: Boolean = true,
    growIn: Boolean = true,
    asleep: Boolean = false,
) {
    val garden = Garden.colors
    val mushroom = Garden.theme == GardenTheme.MUSHROOM
    val reduced = rememberReducedMotion()
    val animatedBloom by animateFloatAsState(bloom.coerceIn(0f, 1f), tween(900), label = "bloom")
    val sleep by animateFloatAsState(if (asleep) 1f else 0f, tween(900), label = "sleep")
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

    Canvas(modifier.semantics { flowerAsleep = asleep }) {
        // Asleep, it barely sways and never looks wilted.
        val angle = (swayAngle?.value ?: 0f) * (1f - 0.7f * sleep)
        val wilt = (1f - animatedBloom) * (1f - sleep)
        val shown = animatedBloom + (0.12f - animatedBloom) * sleep
        val w = size.width
        val h = size.height
        val base = Offset(w / 2f, h * 0.94f)
        // Nods off to one side, gently, rather than drooping.
        val head = Offset(
            w / 2f + wilt * wilt * w * 0.22f + sleep * w * 0.12f,
            h * 0.34f + wilt * wilt * h * 0.16f + sleep * h * 0.07f,
        )

        // Soil mound.
        drawOval(garden.soil.copy(alpha = 0.55f), topLeft = Offset(w * 0.22f, h * 0.89f), size = Size(w * 0.56f, h * 0.09f))

        scale(grow.value, grow.value, pivot = base) {
        rotate(angle * (0.4f + shown), pivot = base) {
            if (mushroom) {
                // A shorter stalk than a flower's stem, so the cap sits low like a mushroom's.
                val top = Offset(head.x, head.y + h * 0.16f)
                mushroom(base, top, w, lerp(petalColor, garden.wilted, wilt * 0.85f), lerp(MushroomStalk, garden.wilted, wilt * 0.6f),
                    lerp(garden.leaf, garden.wilted, wilt * 0.6f), open = shown, droop = wilt * (1f - sleep), path = stem, leafPath = leafPath)
                return@rotate
            }
            stem.reset()
            stem.moveTo(base.x, base.y)
            stem.quadraticTo(w / 2f - w * 0.06f, h * 0.62f, head.x, head.y)
            drawPath(stem, lerp(garden.stem, garden.wilted, wilt * 0.7f), style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))
            leaf(Offset(w / 2f - w * 0.02f, h * 0.70f), w * 0.22f, -35f, lerp(garden.leaf, garden.wilted, wilt * 0.6f), leafPath)
            leaf(Offset(w / 2f + w * 0.01f, h * 0.60f), w * 0.18f, 200f, lerp(garden.leaf, garden.wilted, wilt * 0.6f), leafPath)
            flowerHead(head, w * 0.13f + w * 0.11f * shown, petals, lerp(petalColor, garden.wilted, wilt * 0.85f), shown, garden.petalHoney, droops = 1f - sleep)
        }
        }
        if (!mushroom && animatedBloom < 0.15f && sleep < 0.5f) {
            // A couple of fallen petals.
            val fallen = lerp(petalColor, garden.wilted, 0.6f)
            drawOval(fallen, topLeft = Offset(w * 0.62f, h * 0.88f), size = Size(w * 0.10f, h * 0.035f))
            drawOval(fallen, topLeft = Offset(w * 0.28f, h * 0.90f), size = Size(w * 0.08f, h * 0.03f))
        }
    }
}

private val MushroomStalk = Color(0xFFF1E6D2)

/**
 * A mushroom from [base] up to its cap at [top]: a pale stalk with a tuft of moss, and a [cap]
 * that's a tall closed dome at [open] 0 and a wide, flatter cap at 1, tipping over as it [droop]s.
 */
private fun DrawScope.mushroom(
    base: Offset, top: Offset, w: Float, cap: Color, stalk: Color, moss: Color, open: Float, droop: Float, path: Path, leafPath: Path,
) {
    path.reset()
    path.moveTo(base.x, base.y)
    path.quadraticTo(base.x - w * 0.03f, (base.y + top.y) / 2f, top.x, top.y)
    drawPath(path, stalk, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round))
    leaf(Offset(base.x - w * 0.04f, base.y - w * 0.02f), w * 0.15f, -150f, moss, leafPath)
    leaf(Offset(base.x + w * 0.04f, base.y - w * 0.02f), w * 0.13f, -28f, moss, leafPath)
    val width = w * (0.32f + 0.3f * open)
    val height = width * (0.9f - 0.42f * open)
    rotate(droop * droop * 32f, pivot = top) {
        // The gills under the cap, then the cap over them, then its pale spots.
        drawOval(lerp(cap, stalk, 0.55f), topLeft = Offset(top.x - width * 0.45f, top.y - height * 0.1f), size = Size(width * 0.9f, height * 0.22f))
        drawArc(cap, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(top.x - width / 2f, top.y - height), size = Size(width, height * 2f))
        for ((dx, dy, r) in MUSHROOM_SPOTS) {
            drawCircle(Color.White.copy(alpha = 0.82f), radius = width * r, center = Offset(top.x + dx * width, top.y - dy * height))
        }
    }
}

/** Where a cap's spots go, across its width and up its height, and their sizes. */
private val MUSHROOM_SPOTS = listOf(Triple(-0.24f, 0.45f, 0.06f), Triple(0.16f, 0.62f, 0.07f), Triple(0.02f, 0.25f, 0.045f), Triple(0.3f, 0.28f, 0.04f))

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

private fun DrawScope.flowerHead(center: Offset, radius: Float, petals: Int, color: Color, bloom: Float, heart: Color, droops: Float = 1f) {
    // Petals fold inwards and droop as the bloom fades; a sleeping bud folds without drooping.
    val spread = 0.45f + 0.55f * bloom
    val droop = (1f - bloom) * 50f * droops
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
 * Rolling hills with a scatter of tiny flowers (toadstools in the mushroom forest), drawn behind
 * screen headers. Given the [sky] behind them and the [text] drawn over them, the hills are shaded
 * just enough to keep that text readable (4.5:1).
 */
@Composable
fun MeadowHills(modifier: Modifier = Modifier, sky: Color? = null, text: Color? = null) {
    val garden = Garden.colors
    val mushrooms = Garden.theme == GardenTheme.MUSHROOM
    val (backColor, frontColor) = remember(garden, sky, text) {
        val back = garden.grass.copy(alpha = 0.35f)
        val front = garden.grass.copy(alpha = 0.55f)
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
        val unit = 1.dp.toPx()
        dots.forEachIndexed { i, (x, y) ->
            val color = garden.petals[i % garden.petals.size]
            translate(w * x, h * y) {
                if (mushrooms) {
                    tinyToadstool(color, unit)
                } else {
                    for (k in 0 until 5) {
                        val a = Math.toRadians(72.0 * k).toFloat()
                        drawCircle(color, radius = 1.5f * unit, center = Offset(cos(a) * 1.7f * unit, sin(a) * 1.7f * unit))
                    }
                    drawCircle(garden.petalHoney, radius = 1.05f * unit, center = Offset.Zero)
                }
            }
        }
    }
}

/** A toadstool a few dp tall, its cap [cap] with pale spots, standing on the origin's ground. */
private fun DrawScope.tinyToadstool(cap: Color, unit: Float) {
    val stalk = Color(0xFFF3E9D8)
    drawRoundRect(stalk, topLeft = Offset(-1f * unit, -3.2f * unit), size = Size(2f * unit, 4f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit))
    drawArc(cap, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(-3.6f * unit, -5.6f * unit), size = Size(7.2f * unit, 5.4f * unit))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 0.6f * unit, center = Offset(-1.3f * unit, -4.2f * unit))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 0.5f * unit, center = Offset(1.2f * unit, -3.7f * unit))
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
