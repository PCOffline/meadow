package dev.pcoffline.meadow.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.ui.theme.ButterflyLook
import dev.pcoffline.meadow.ui.theme.WingMark
import dev.pcoffline.meadow.ui.theme.WingShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** How far a wing turns up, in radians, when fully folded: never quite shut. */
private const val MAX_FOLD = 1.4f
private const val TAU = (2 * PI).toFloat()

/** Wingbeats a second while flapping. */
private const val WINGBEATS_PER_SECOND = 2.8f

/**
 * How far folded the wings are, 0 (spread flat) to 1 (up over the back), [beat] wingbeats in:
 * a slow lift and a quicker downstroke, the way a butterfly flies.
 */
internal fun wingbeat(beat: Float): Float {
    val f = beat - floor(beat)
    // Both ends of a stroke are turning points, so the change of pace doesn't jolt.
    val half = if (f < LIFT) f / LIFT * 0.5f else 0.5f + (f - LIFT) / (1f - LIFT) * 0.5f
    return 0.5f - 0.5f * cos(TAU * half)
}

/** The share of a wingbeat spent raising the wings. */
private const val LIFT = 0.6f

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * One butterfly crossing the sky [t] seconds in: bursts of wingbeats, then a glide on open
 * wings. [seed] sets it apart from the others; [lane] is its height as a fraction of the sky.
 */
internal fun DrawScope.flutter(look: ButterflyLook, t: Float, seed: Float, lane: Float, length: Float, speed: Float) {
    val path = t * speed + seed
    val glide = smoothstep(0.55f, 0.85f, 0.5f + 0.5f * sin(t * 0.45f + seed * 3f))
    val beat = WINGBEATS_PER_SECOND * t + seed
    val held = 0.12f + 0.06f * sin(t * 2f + seed)
    val fold = (1f - glide) * wingbeat(beat) + glide * held
    val hindFold = (1f - glide) * wingbeat(beat - 0.07f) + glide * held
    val x = size.width * (0.5f + 0.4f * sin(path))
    // Each downstroke lifts it a little; gliding, it sinks.
    val y = size.height * (lane + 0.1f * sin(path * 2.3f + seed)) - (1f - glide) * (1f - fold) * 2.5.dp.toPx() + glide * 2.dp.toPx()
    butterfly(look, Offset(x, y), length, fold, hindFold, tilt = 22f * cos(path), sway = 0.04f * sin(beat * TAU))
}

/** One side's wing outline, in forewing lengths with the body at the origin, and where its veins and edge dots go. */
private class Wing(val outline: Path, val veins: List<Path>, val dots: List<Offset>)

private class Wings(val fore: Wing, val hind: Wing)

private fun wing(outline: Path, root: Offset, veins: ClosedFloatingPointRange<Float>, veinCount: Int): Wing {
    val measure = PathMeasure().apply { setPath(outline, false) }
    fun along(range: ClosedFloatingPointRange<Float>, count: Int) = List(count) { i ->
        measure.getPosition(measure.length * (range.start + (range.endInclusive - range.start) * i / (count - 1)))
    }
    // Edge dots sit just inside the border, on the way from the edge to the body.
    val dots = along(veins, veinCount * 2 - 1).map { edge ->
        val toRoot = root - edge
        edge + toRoot / toRoot.getDistance() * 0.07f
    }
    // Veins bow gently on their way from the body to the edge.
    val veinPaths = along(veins, veinCount).map { end ->
        val bend = Offset((end.y - root.y) * 0.08f, -(end.x - root.x) * 0.08f)
        val middle = (root + end) / 2f + bend
        Path().apply {
            moveTo(root.x, root.y)
            quadraticTo(middle.x, middle.y, end.x, end.y)
        }
    }
    return Wing(outline, veinPaths, dots)
}

private fun path(build: Path.() -> Unit) = Path().apply { build(); close() }

private fun wingsFor(shape: WingShape): Wings {
    val foreRoot = Offset(0.05f, -0.12f)
    val hindRoot = Offset(0.05f, 0.06f)
    return when (shape) {
        WingShape.ROUND -> Wings(
            wing(path {
                moveTo(0.04f, -0.28f)
                cubicTo(0.3f, -1.15f, 1.25f, -1.15f, 1.12f, -0.5f)
                cubicTo(1.05f, -0.12f, 0.5f, 0f, 0.04f, 0f)
            }, foreRoot, 0.14f..0.68f, 6),
            wing(path {
                moveTo(0.04f, -0.02f)
                cubicTo(0.75f, -0.04f, 1f, 0.55f, 0.72f, 0.85f)
                cubicTo(0.45f, 1.1f, 0.1f, 0.78f, 0.04f, 0.35f)
            }, hindRoot, 0.12f..0.72f, 5),
        )
        WingShape.POINTED -> Wings(
            wing(path {
                moveTo(0.04f, -0.3f)
                cubicTo(0.35f, -1f, 0.95f, -1.2f, 1.2f, -0.98f)
                cubicTo(1.15f, -0.6f, 1f, -0.2f, 0.55f, -0.05f)
                quadraticTo(0.3f, 0.02f, 0.04f, 0f)
            }, foreRoot, 0.16f..0.7f, 6),
            wing(path {
                moveTo(0.04f, -0.02f)
                cubicTo(0.7f, -0.06f, 0.95f, 0.45f, 0.68f, 0.78f)
                cubicTo(0.45f, 1f, 0.12f, 0.72f, 0.04f, 0.32f)
            }, hindRoot, 0.12f..0.72f, 5),
        )
        WingShape.SCALLOPED -> Wings(
            wing(path {
                moveTo(0.04f, -0.3f)
                cubicTo(0.3f, -1.1f, 0.95f, -1.15f, 1.12f, -0.92f)
                quadraticTo(1f, -0.8f, 1.08f, -0.66f)
                quadraticTo(0.94f, -0.56f, 1.02f, -0.42f)
                quadraticTo(0.9f, -0.32f, 0.9f, -0.16f)
                cubicTo(0.6f, -0.02f, 0.3f, 0f, 0.04f, 0f)
            }, foreRoot, 0.14f..0.7f, 6),
            wing(path {
                moveTo(0.04f, -0.02f)
                cubicTo(0.7f, -0.05f, 0.95f, 0.3f, 0.88f, 0.5f)
                quadraticTo(0.76f, 0.56f, 0.8f, 0.7f)
                quadraticTo(0.66f, 0.72f, 0.62f, 0.88f)
                quadraticTo(0.5f, 0.84f, 0.42f, 0.98f)
                cubicTo(0.2f, 0.9f, 0.08f, 0.6f, 0.04f, 0.35f)
            }, hindRoot, 0.12f..0.72f, 5),
        )
        WingShape.SWALLOWTAIL -> Wings(
            wing(path {
                moveTo(0.04f, -0.3f)
                cubicTo(0.3f, -1f, 1f, -1.25f, 1.3f, -1.05f)
                cubicTo(1.15f, -0.7f, 0.85f, -0.25f, 0.6f, -0.06f)
                quadraticTo(0.3f, 0.02f, 0.04f, 0f)
            }, foreRoot, 0.16f..0.7f, 6),
            wing(path {
                moveTo(0.04f, -0.02f)
                cubicTo(0.7f, -0.05f, 0.92f, 0.3f, 0.78f, 0.6f)
                quadraticTo(0.64f, 0.72f, 0.56f, 0.8f)
                lineTo(0.56f, 1.32f)
                quadraticTo(0.52f, 1.42f, 0.46f, 1.34f)
                lineTo(0.42f, 0.9f)
                cubicTo(0.25f, 0.86f, 0.08f, 0.6f, 0.04f, 0.35f)
            }, hindRoot, 0.1f..0.5f, 5),
        )
    }
}

/** Built on first use, on the main thread, where everything is drawn. */
private val allWings by lazy(LazyThreadSafetyMode.NONE) { WingShape.entries.associateWith(::wingsFor) }

/** Wings colour from the body out. */
private fun ButterflyLook.shading(center: Offset, radius: Float) =
    Brush.radialGradient(0f to inner, 0.5f to wing, 1f to outer, center = center, radius = radius)

private class Paints(val fore: Brush, val hind: Brush)

private val paints = HashMap<ButterflyLook, Paints>()

private fun paintsFor(look: ButterflyLook) = paints.getOrPut(look) {
    Paints(look.shading(Offset(0.02f, -0.15f), 1.25f), look.shading(Offset(0.02f, 0.05f), if (look.shape == WingShape.SWALLOWTAIL) 1.3f else 1f))
}

/**
 * A butterfly seen from above, head up, drawn as shapes so it's sharp at any size. [length] is a
 * forewing's length in px. [fold] 0..1 is how far the forewings are raised over its back (0 is
 * spread flat); [hindFold] the same for the hindwings, which trail a little behind. [tilt] banks it,
 * in degrees, toward where it's heading; [sway] swings its antennae.
 */
fun DrawScope.butterfly(look: ButterflyLook, at: Offset, length: Float, fold: Float, hindFold: Float = fold, tilt: Float = 0f, sway: Float = 0f) {
    val wings = allWings.getValue(look.shape)
    val paint = paintsFor(look)
    translate(at.x, at.y) {
        rotate(tilt, pivot = Offset.Zero) {
            scale(length, length, pivot = Offset.Zero) {
                for (side in floatArrayOf(-1f, 1f)) {
                    wing(look, wings.hind, paint.hind, hindFold, side, fore = false)
                    wing(look, wings.fore, paint.fore, fold, side, fore = true)
                }
                body(look, sway)
            }
        }
    }
}

private fun DrawScope.wing(look: ButterflyLook, wing: Wing, paint: Brush, fold: Float, side: Float, fore: Boolean) {
    val marks = look.marks
    val outline = wing.outline
    // Seen from above, a raised wing is foreshortened; its underside, turned from the light, darker.
    scale(scaleX = side * cos(fold * MAX_FOLD), scaleY = 1f, pivot = Offset.Zero) {
        clipPath(outline) {
            drawPath(outline, paint)
            if (WingMark.SHEEN in marks) {
                val shine = 0.15f + 0.55f * (1f - fold) * (1f - fold)
                val center = if (fore) Offset(0.55f, -0.5f) else Offset(0.4f, 0.4f)
                val radius = if (fore) 0.65f else 0.5f
                drawCircle(Brush.radialGradient(listOf(look.accent.copy(alpha = shine), Color.Transparent), center, radius), radius, center)
            }
            val strongVeins = WingMark.VEINS in marks
            val vein = Stroke(if (strongVeins) 0.05f else 0.018f, cap = StrokeCap.Round)
            val veinColor = if (strongVeins) look.edge else look.edge.copy(alpha = 0.3f)
            wing.veins.forEach { drawPath(it, veinColor, style = vein) }
            drawMarks(look, fore)
            // The border: a stroke along the outline, half of it inside the clip.
            val border = if (WingMark.FRINGE in marks || strongVeins) 0.26f else 0.12f
            drawPath(outline, look.edge, style = Stroke(border))
            if (WingMark.FRINGE in marks) {
                wing.dots.forEach { drawCircle(Color.White.copy(alpha = 0.92f), radius = 0.028f, center = it) }
            }
            if (fold > 0f) drawPath(outline, Color.Black.copy(alpha = 0.32f * fold))
        }
        drawPath(outline, lerp(look.edge, Color.Black, 0.3f), style = Stroke(0.022f))
    }
}

private fun DrawScope.drawMarks(look: ButterflyLook, fore: Boolean) {
    val marks = look.marks
    if (WingMark.BAND in marks) {
        val (from, to) = if (fore) Offset(0.35f, -0.98f) to Offset(1.08f, -0.3f) else Offset(0.08f, 0.2f) to Offset(0.8f, 0.6f)
        drawLine(look.accent.copy(alpha = 0.9f), from, to, strokeWidth = if (fore) 0.17f else 0.14f, cap = StrokeCap.Round)
    }
    if (WingMark.DOTS in marks) {
        val dots = if (fore) listOf(Offset(0.5f, -0.55f) to 0.075f, Offset(0.78f, -0.72f) to 0.06f, Offset(0.32f, -0.3f) to 0.055f)
        else listOf(Offset(0.38f, 0.32f) to 0.06f, Offset(0.6f, 0.55f) to 0.05f)
        dots.forEach { (at, r) -> drawCircle(look.accent, r, at) }
    }
    if (WingMark.TIPS in marks && fore) {
        drawCircle(look.edge, 0.42f, Offset(1.2f, -0.95f))
        listOf(Offset(1f, -0.9f) to 0.055f, Offset(1.08f, -0.78f) to 0.045f, Offset(0.92f, -0.78f) to 0.04f)
            .forEach { (at, r) -> drawCircle(Color.White, r, at) }
    }
    if (WingMark.EYESPOTS in marks) {
        val (at, r) = if (fore) Offset(0.66f, -0.62f) to 0.2f else Offset(0.5f, 0.5f) to 0.15f
        drawCircle(Color(0xFFF3D58A), r, at)
        drawCircle(look.accent, r * 0.74f, at)
        drawCircle(look.edge, r * 0.44f, at)
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.14f, at + Offset(-r * 0.18f, -r * 0.18f))
    }
}

private fun DrawScope.body(look: ButterflyLook, sway: Float) {
    val light = lerp(look.body, Color.White, 0.28f)
    for (side in floatArrayOf(-1f, 1f)) {
        val tip = Offset(side * (0.3f + sway), -1.1f)
        val antenna = Path().apply {
            moveTo(side * 0.03f, -0.6f)
            quadraticTo(side * 0.08f, -0.95f, tip.x, tip.y)
        }
        drawPath(antenna, look.body, style = Stroke(0.022f, cap = StrokeCap.Round))
        drawCircle(look.body, 0.042f, tip)
    }
    drawOval(Brush.horizontalGradient(listOf(look.body, light, look.body), startX = -0.065f, endX = 0.065f), Offset(-0.065f, -0.18f), Size(0.13f, 0.98f))
    for (i in 1..5) {
        val y = -0.05f + i * 0.13f
        drawLine(light.copy(alpha = 0.5f), Offset(-0.05f, y), Offset(0.05f, y), strokeWidth = 0.012f)
    }
    drawOval(Brush.radialGradient(listOf(light, look.body), Offset(-0.02f, -0.3f), 0.2f), Offset(-0.1f, -0.44f), Size(0.2f, 0.36f))
    drawCircle(look.body, 0.085f, Offset(0f, -0.53f))
    for (side in floatArrayOf(-1f, 1f)) {
        val eye = Offset(side * 0.055f, -0.56f)
        drawCircle(lerp(look.body, Color.Black, 0.45f), 0.035f, eye)
        drawCircle(Color.White.copy(alpha = 0.7f), 0.012f, eye + Offset(-0.01f, -0.01f))
    }
}
