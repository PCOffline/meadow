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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.ui.components.flora.Form
import dev.pcoffline.meadow.ui.components.flora.Look
import dev.pcoffline.meadow.ui.components.flora.Piece
import dev.pcoffline.meadow.ui.components.flora.Plant
import dev.pcoffline.meadow.ui.components.flora.Sketch
import dev.pcoffline.meadow.ui.components.flora.sketch
import dev.pcoffline.meadow.ui.components.flora.vigour
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.LocalDarkTheme
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.readable
import kotlin.math.cos
import kotlin.math.sin

/** Whether a [BloomFlower] is drawn asleep; read by tests, never announced. */
val FlowerAsleep = SemanticsPropertyKey<Boolean>("FlowerAsleep")
private var SemanticsPropertyReceiver.flowerAsleep by FlowerAsleep

/**
 * A plant that shows how much time is left ([bloom], from 1 to 0). It is in full bloom while half
 * or more is left. With less, it nods, closes a little and drops a petal or two. With none left,
 * it is dried, in its own colour and in the form its kind takes: a seed head, a bare cup or a
 * skirt of hanging petals. [asleep] closes it into a bud in full colour: it rests outside its
 * hours, and its time isn't used up. In the mushroom forest, [plant] is a mushroom, which ages in
 * place of wilting.
 */
@Composable
fun BloomFlower(
    bloom: Float,
    plant: Plant,
    modifier: Modifier = Modifier,
    sway: Boolean = true,
    growIn: Boolean = true,
    asleep: Boolean = false,
) {
    val theme = Garden.theme
    val dark = LocalDarkTheme.current
    val reduced = rememberReducedMotion()
    val left = bloom.coerceIn(0f, 1f)
    val lively by animateFloatAsState(vigour(left), tween(900), label = "vigour")
    // Used up and asleep are forms of their own, so the plant fades from one form to the next.
    val spent by animateFloatAsState(if (left <= 0f) 1f else 0f, tween(900), label = "spent")
    val sleep by animateFloatAsState(if (asleep) 1f else 0f, tween(900), label = "sleep")
    // Sprouts up from the soil the first time it appears.
    val grow = remember { Animatable(if (growIn && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow))
    }
    val swayAngle: State<Float>? = if (sway && !reduced) {
        val transition = rememberInfiniteTransition(label = "sway")
        transition.animateFloat(
            initialValue = -2.5f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
            label = "swayAngle",
        )
    } else null
    // A mushroom is stiffer than a flower.
    val bend = if (plant.form == Form.MUSHROOM) 0.4f else 1f

    Box(modifier.semantics { flowerAsleep = asleep }) {
        // What lies on the ground stays still.
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val look = looks(lively, spent, sleep).maxBy { it.second }.first
                val ground = painted(sketch(theme, plant, look, dark).ground)
                onDrawBehind { paint(ground) }
            },
        )
        // The plant is drawn once for each look, and then only turned: the sway and the sprouting
        // are read here, so they move the drawing without drawing it again.
        Spacer(
            Modifier.matchParentSize()
                .graphicsLayer {
                    val foot = size.height - (Sketch.HEIGHT - Sketch.BASE.y) * fit(size)
                    transformOrigin = TransformOrigin(0.5f, foot / size.height)
                    // Asleep, it barely sways.
                    rotationZ = (swayAngle?.value ?: 0f) * bend * (0.6f + 0.6f * lively) * (1f - 0.7f * sleep)
                    scaleX = grow.value
                    scaleY = grow.value
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    val layers = looks(lively, spent, sleep).map { (look, share) -> painted(sketch(theme, plant, look, dark).body) to share }
                    onDrawBehind {
                        layers.forEach { (shapes, share) ->
                            if (share > 0.99f) {
                                paint(shapes)
                            } else {
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = share })
                                    paint(shapes)
                                    canvas.restore()
                                }
                            }
                        }
                    }
                },
        )
    }
}

/**
 * The looks to draw, each with its share from 0 to 1. At rest there is one. While a plant changes
 * to or from used up ([spent]) or asleep ([sleep]), there are two, and one fades into the other.
 */
internal fun looks(vigour: Float, spent: Float, sleep: Float): List<Pair<Look, Float>> = listOf(
    Look.Open(vigour) to (1f - spent) * (1f - sleep),
    Look.Spent to spent * (1f - sleep),
    Look.Asleep to sleep,
).filter { it.second > 0.01f }

/** How many pixels one unit of a [Sketch] is, so that the whole sketch fits in [size]. */
private fun fit(size: Size): Float = minOf(size.width / Sketch.WIDTH, size.height / Sketch.HEIGHT)

private class Painted(val path: Path, val color: Color, val clip: Path?)

/** [pieces] as paths in this size: centred, and standing on the bottom edge. */
private fun CacheDrawScope.painted(pieces: List<Piece>): List<Painted> {
    val scale = fit(size)
    val left = (size.width - Sketch.WIDTH * scale) / 2f
    val top = size.height - Sketch.HEIGHT * scale
    fun path(points: FloatArray) = Path().apply {
        moveTo(left + points[0] * scale, top + points[1] * scale)
        for (i in 2 until points.size step 2) lineTo(left + points[i] * scale, top + points[i + 1] * scale)
        close()
    }
    // Pieces that are cut to one outline share its path.
    val clips = HashMap<FloatArray, Path>()
    return pieces.map { Painted(path(it.points), it.color, it.clip?.let { clip -> clips.getOrPut(clip) { path(clip) } }) }
}

private fun DrawScope.paint(shapes: List<Painted>) = shapes.forEach {
    if (it.clip == null) drawPath(it.path, it.color) else clipPath(it.clip) { drawPath(it.path, it.color) }
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
 * just enough to keep that text readable (4.5:1). [rise] below 1 keeps them lower, that share of
 * their full height above the bottom.
 */
@Composable
fun MeadowHills(modifier: Modifier = Modifier, sky: Color? = null, text: Color? = null, rise: Float = 1f) {
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
        // From a share of the way down to a height, squeezed towards the bottom by [rise].
        fun y(share: Float) = h * (1f - (1f - share) * rise)
        val back = Path().apply {
            moveTo(0f, y(0.55f))
            cubicTo(w * 0.25f, y(0.30f), w * 0.55f, y(0.70f), w, y(0.38f))
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(back, backColor)
        val front = Path().apply {
            moveTo(0f, y(0.78f))
            cubicTo(w * 0.3f, y(0.55f), w * 0.7f, y(0.95f), w, y(0.68f))
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(front, frontColor)
        val unit = 1.dp.toPx()
        HILL_FLOWERS.forEachIndexed { i, f ->
            val color = garden.petals[i % garden.petals.size]
            translate(w * f.x, y(f.down)) {
                // A toadstool is wider than a flower, so it grows a little less, staying under a butterfly.
                scale(if (mushrooms) f.scale * TOADSTOOL_SHARE else f.scale, pivot = Offset.Zero) {
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
}

/** A tiny flower on the hills: across ([x]) and down ([down]) as shares of the size, drawn [scale] times its base size. */
internal data class HillFlower(val x: Float, val down: Float, val scale: Float)

/**
 * The hills' flowers, in turn on the back hill and the front one, so they fill the meadow from
 * near to far. Those further back are a little smaller.
 */
internal val HILL_FLOWERS = listOf(
    HillFlower(0.08f, 0.66f, 1.15f),
    HillFlower(0.21f, 0.90f, 1.4f),
    HillFlower(0.37f, 0.60f, 1.15f),
    HillFlower(0.63f, 0.93f, 1.4f),
    HillFlower(0.78f, 0.64f, 1.15f),
    HillFlower(0.91f, 0.88f, 1.4f),
)

/** A flower's width before scaling: five petals 1.5 dp round, 1.7 dp from its middle. */
private const val HILL_FLOWER_BASE_DP = 2 * (1.7f + 1.5f)

private const val TOADSTOOL_SHARE = 0.85f

/** How wide [flower] is drawn, in dp. */
internal fun hillFlowerWidthDp(flower: HillFlower): Float = HILL_FLOWER_BASE_DP * flower.scale

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
