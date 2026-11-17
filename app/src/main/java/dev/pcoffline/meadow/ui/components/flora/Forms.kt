package dev.pcoffline.meadow.ui.components.flora

import androidx.compose.ui.geometry.Offset
import dev.pcoffline.meadow.ui.theme.GardenTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** What a plant shows. */
internal sealed interface Look {
    /** In bloom. [vigour] is 1 with plenty of time and 0 with little left. */
    data class Open(val vigour: Float) : Look

    /** The time is used up: dried, in the form its kind takes. */
    data object Spent : Look

    /** Outside its hours: a closed bud in full colour, or a mushroom not yet open. */
    data object Asleep : Look
}

/**
 * How lively a flower with [bloom] of its time left looks, from 0 to 1. It is in full bloom while
 * half or more of the time is left, and at 0 from an eighth down, so that a flower with time left
 * doesn't look as one with none.
 */
internal fun vigour(bloom: Float): Float {
    val t = ((bloom - LITTLE_LEFT) / (PLENTY_LEFT - LITTLE_LEFT)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal const val PLENTY_LEFT = 0.5f
internal const val LITTLE_LEFT = 0.125f

/** [plant] in [theme] as flat shapes, by day or with [dark] by night. */
internal fun sketch(theme: GardenTheme, plant: Plant, look: Look, dark: Boolean = false): Sketch {
    val soil = Soils.getValue(theme).let { if (dark) it.night() else it }
    val p = if (dark) plant.night() else plant
    return Sketch().apply {
        onGround {
            ground(theme, soil)
            if (theme == GardenTheme.POND && p.form == Form.LOTUS) lilyPad(soil)
            fallenPetals(p, look)
        }
        when (p.form) {
            Form.RAY, Form.ROSETTE, Form.CUP, Form.POMPOM -> flower(p, soil, look)
            Form.LOTUS -> lotus(p, soil, look)
            Form.SPIKE -> spike(p, soil, look)
            Form.GLOBE -> globe(p, soil, look)
            Form.RACEME -> raceme(p, soil, look)
            Form.MUSHROOM -> mushroom(p, look)
        }
    }
}

/** A value that is [full] in full bloom, [low] with little time left, [spent] when used up and [asleep] when asleep. */
private fun Look.of(full: Float, low: Float, spent: Float, asleep: Float): Float = when (this) {
    is Look.Open -> low + (full - low) * vigour
    Look.Spent -> spent
    Look.Asleep -> asleep
}

private fun mix(a: Pose, b: Pose, t: Float): Pose {
    fun f(x: Float, y: Float) = x + (y - x) * t
    return Pose(f(a.tilt, b.tilt), f(a.face, b.face), f(a.cup, b.cup), f(a.plen, b.plen), f(a.dry, b.dry), f(a.y, b.y), f(a.x, b.x), f(a.lean, b.lean))
}

private fun radians(degrees: Float) = Math.toRadians(degrees.toDouble()).toFloat()
private fun degrees(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

/** The direction [degrees] clockwise from straight up. */
private fun up(degrees: Float) = radians(degrees).let { Offset(sin(it), -cos(it)) }

// ---------- the ground ----------

private fun Sketch.ground(theme: GardenTheme, soil: Soil) {
    when (theme) {
        GardenTheme.POND -> {
            // Water, with a ripple.
            ellipse(Offset(45f, 107f), 34f, 6.5f, soil.ground)
            ellipse(Offset(45f, 107f), 24f, 4f, soil.ground.light(0.8f))
            ellipse(Offset(45f, 107f), 13f, 2f, soil.ground)
        }
        GardenTheme.MUSHROOM -> {
            ellipse(Offset(45f, 107.5f), 25f, 4.6f, soil.ground)
            // Moss.
            ellipse(Offset(33f, 105.5f), 8f, 3.4f, soil.leaf)
            ellipse(Offset(57f, 106f), 9f, 3.6f, soil.leaf.shade(0.5f))
            ellipse(Offset(45f, 106.5f), 10f, 3.6f, soil.leaf.light(0.6f))
        }
        else -> ellipse(Offset(45f, 107.5f), 25f, 4.6f, soil.ground)
    }
}

private fun Sketch.lilyPad(soil: Soil) {
    ellipse(Offset(30f, 104.5f), 14f, 3.6f, soil.leaf)
    ellipse(Offset(30f, 104.2f), 12.5f, 2.9f, soil.leaf.light(0.8f))
    poly(listOf(Offset(30f, 104.5f), Offset(22f, 101.6f), Offset(24f, 100.9f)), soil.ground.light(0.8f))
}

private val FALLEN = listOf(Offset(60f, 107f) to 12f, Offset(29f, 108f) to -14f, Offset(67f, 105.5f) to 28f, Offset(38f, 109.5f) to 6f, Offset(53f, 109.5f) to -22f)

/** How lively a flower is when it drops its first petals. */
private const val SHEDS_BELOW = 0.35f

private fun Sketch.fallen(points: List<Pair<Offset, Float>>, tone: Tone, r: Float = 3f) = points.forEach { (p, turn) -> ellipse(p, r, r * 0.42f, tone, turn) }

/** The petals that a flower has dropped: two with little time left, and more when the time is used up. */
private fun Sketch.fallenPetals(p: Plant, look: Look) {
    if (p.shed == 0) return
    when (look) {
        is Look.Open -> if (look.vigour < SHEDS_BELOW) fallen(FALLEN.take(2), p.petal)
        Look.Spent -> fallen(FALLEN.take(p.shed), p.petal.mix(p.petal.dried(), 0.25f))
        Look.Asleep -> Unit
    }
}

// ---------- parts ----------

private fun stemLine(head: Offset, tilt: Float, lean: Float, reach: Float = 18f): List<Offset> {
    val base = Sketch.BASE
    return bezier(base, Offset(base.x + lean, base.y - 34f), head - up(tilt) * reach, head)
}

/** A leaf from [at], pointing [angle] degrees clockwise from straight up: two tones, split along its midrib. */
private fun Sketch.leaf(at: Offset, angle: Float, length: Float, width: Float, tone: Tone, kind: LeafShape = LeafShape.LANCE) {
    val d = up(angle)
    val n = Offset(d.y, -d.x)
    val broad = kind == LeafShape.HEART || kind == LeafShape.OVAL
    fun edge(t: Float, side: Float): Offset {
        val w = width * max(0f, sin(PI.toFloat() * t.pow(if (kind == LeafShape.HEART) 0.75f else 1.1f))).pow(if (broad) 0.7f else 1f)
        val bend = sin(t * PI.toFloat()) * length * 0.10f
        return at + d * (t * length) + n * (w * side + bend)
    }
    fun shape(low: Float, tone: Tone) = poly(List(13) { edge(it / 12f, 1f) } + List(13) { edge((12 - it) / 12f, low) }, tone)
    shape(-1f, tone)
    shape(0f, tone.light(0.9f))
}

/** Blades of grass, or a plant's narrow leaves, from [base]. */
private fun Sketch.blades(base: Offset, tone: Tone, spread: List<Float>, length: Float) = spread.forEachIndexed { i, a ->
    val long = length * (if (i % 2 == 1) 1f else 0.75f)
    val r = radians(a)
    val tip = Offset(base.x + sin(r) * long, base.y - cos(r) * long)
    val mid = Offset(base.x + sin(r) * length * 0.3f, base.y - length * 0.55f)
    ribbon(bezier(base, mid, mid, tip, 8), 2.4f, 0.5f, if (i % 2 == 1) tone else tone.shade(0.6f))
}

private fun Sketch.leaves(p: Plant, soil: Soil, line: List<Offset>, droop: Float, wilt: Float) {
    val tone = soil.leaf.mix(soil.leaf.dried(), wilt * 0.7f)
    fun on(t: Float) = line[(t * (line.size - 1)).toInt()]
    when (p.leaf) {
        LeafShape.NONE -> Unit
        LeafShape.FEATHER -> listOf(0.25f to -1f, 0.42f to 1f, 0.56f to -1f).forEach { (t, side) ->
            val at = on(t)
            for (da in listOf(-16f, 8f, 30f)) {
                val tip = at + up(side * (52f + da + droop)) * 12f
                ribbon(listOf(at, Offset((at.x + tip.x) / 2f, (at.y + tip.y) / 2f - 1f), tip), 1.5f, 0.6f, tone)
            }
        }
        LeafShape.HEART -> {
            leaf(on(0.28f), -62f - droop, 21f, 8.5f, tone, LeafShape.HEART)
            leaf(on(0.5f), 66f + droop, 18f, 7.5f, tone, LeafShape.HEART)
        }
        else -> {
            val w = if (p.leaf == LeafShape.LANCE) 4.6f else 6.2f
            leaf(on(0.24f), -58f - droop, 19f, w, tone, p.leaf)
            leaf(on(0.46f), 62f + droop, 16f, w * 0.9f, tone, p.leaf)
        }
    }
}

/** A petal that lies along +x from the origin, as its outline. */
private fun petalOutline(kind: PetalShape, length: Float, half: Float): List<Offset> {
    val (p, q) = when (kind) {
        PetalShape.ROUND, PetalShape.NOTCH -> 1.7f to 0.62f
        PetalShape.POINT -> 1.25f to 1.15f
        PetalShape.NARROW -> 1.3f to 0.9f
        PetalShape.TOOTH -> 1.9f to 0.55f
        PetalShape.WIDE -> 1.9f to 0.5f
        PetalShape.FAN -> 3.2f to 0.8f
    }
    val end = when (kind) {
        PetalShape.NOTCH -> 0.975f
        PetalShape.TOOTH -> 0.9f
        PetalShape.FAN -> 0.84f
        else -> 1f
    }
    val steps = 10
    val top = List(steps + 1) { i ->
        val t = i / steps.toFloat() * end
        Offset(t * length, half * max(0f, sin(PI.toFloat() * t.pow(p))).pow(q))
    }
    val w = top.last().y
    val tip = when (kind) {
        // A round petal with one clean cleft.
        PetalShape.NOTCH -> listOf(Offset(length * 0.995f, half * 0.2f), Offset(length * 0.86f, 0f), Offset(length * 0.995f, -half * 0.2f))
        PetalShape.TOOTH -> listOf(
            Offset(length * 0.97f, w * 0.8f), Offset(length * 0.955f, w * 0.42f), Offset(length, 0f),
            Offset(length * 0.955f, -w * 0.42f), Offset(length * 0.97f, -w * 0.8f),
        )
        // Widest near its tip, with deep teeth.
        PetalShape.FAN -> listOf(
            Offset(length, w * 0.95f), Offset(length * 0.86f, w * 0.5f), Offset(length * 1.04f, 0f),
            Offset(length * 0.86f, -w * 0.5f), Offset(length, -w * 0.95f),
        )
        else -> emptyList()
    }
    return top + tip + top.reversed().map { Offset(it.x, -it.y) }
}

/**
 * A flower's head: petals round an axis, seen by one camera, so that the head and its stem share a
 * point of view. [tilt], [face] and [cup] are as in [Pose].
 */
private class Head(val at: Offset, tilt: Float, val face: Float, val cup: Float = 0f) {
    /** Where the axis points on the page. */
    val v = up(tilt)

    /** Across the axis on the page. */
    val u = radians(tilt).let { Offset(cos(it), sin(it)) }
    val across = degrees(atan2(u.y, u.x))
    private val side = sqrt(max(0f, 1f - face * face))

    /** A point of the head on the page: [x] across it, [y] away from the viewer and [z] up its axis. */
    fun project(x: Float, y: Float, z: Float = 0f): Offset = at + u * x + v * (face * y + side * z)

    fun petal(angle: Float, kind: PetalShape, length: Float, half: Float, start: Float = 0f): List<Offset> {
        val a = radians(angle)
        return petalOutline(kind, length, half).map { (px, py) ->
            val lift = cup * (start + px) * (0.55f + 0.45f * px / max(length, 1e-6f))
            val r = start + px * (1f - 0.45f * cup)
            project(r * cos(a) - py * sin(a), r * sin(a) + py * cos(a), lift)
        }
    }
}

private fun Sketch.petal(head: Head, angle: Float, kind: PetalShape, length: Float, half: Float, tone: Tone, start: Float = 0f) =
    poly(head.petal(angle, kind, length, half, start), tone)

/** A ring of petals in painting order: the far ones first, shaded when the head turns away. */
private fun Sketch.ring(
    head: Head, count: Int, kind: PetalShape, length: Float, half: Float, main: Tone, shade: Tone,
    start: Float = 0f, offset: Float = 0f, keep: Set<Int>? = null, farShade: Boolean = true, alt: Float = 0f,
) {
    val k = if (farShade) min(1f, (1f - head.face) * 1.5f + head.cup * 0.5f) else 0f
    List(count) { it }.filter { keep == null || it in keep }
        .map { i -> Triple(sin(radians(offset + 360f / count * i)), offset + 360f / count * i, i) }
        .sortedWith(compareBy({ if (alt > 0f) -(it.third % 2) else 0 }, { -it.first }))
        .forEach { (depth, angle, i) ->
            val base = if (alt > 0f && i % 2 == 1) main.mix(shade, alt) else main
            val tone = if (depth > 0.05f) base.mix(shade, k * depth.pow(0.6f)) else base
            petal(head, angle, kind, length, half, tone, start)
        }
}

private fun Sketch.disc(head: Head, radius: Float, tone: Tone, dome: Float) =
    ellipse(head.project(0f, 0f, radius * dome), radius, radius * max(head.face, 0.45f), tone, head.across)

private fun Sketch.dots(head: Head, count: Int, radius: Float, r: Float, tone: Tone, dome: Float) = repeat(count) { i ->
    val a = radians(360f / count * i)
    circle(head.project(radius * cos(a), radius * sin(a), radius * dome), r, tone)
}

/** Seeds in the spiral that a sunflower's are in. */
private fun spiral(count: Int, radius: Float): List<Offset> = List(count) { i ->
    val r = radius * sqrt((i + 0.5f) / count)
    val a = i * GOLDEN_ANGLE
    Offset(r * cos(a), r * sin(a))
}

private const val GOLDEN_ANGLE = 2.39996f

// ---------- flowers with a head of petals ----------

private fun Sketch.flower(p: Plant, soil: Soil, look: Look) {
    val pose = when (look) {
        is Look.Open -> mix(p.low ?: LowPose, FullPose, look.vigour)
        Look.Spent -> p.spent ?: SpentPose
        Look.Asleep -> AsleepPose
    }
    val wilt = pose.dry
    val petal = p.petal.mix(p.petal.dried(), wilt)
    val shade = petal.shade()
    val centre = p.centre.mix(p.centre.dried(0.6f).shade(0.5f), wilt)
    val stemTone = soil.stem.mix(soil.stem.dried(), wilt * 0.6f)
    val cupTone = (p.calyx ?: soil.leaf).let { it.mix(it.dried(), wilt * 0.6f) }
    val size = p.size * 1.1f
    val at = Offset(pose.x, pose.y)
    val line = stemLine(at, pose.tilt, pose.lean)
    val droop = look.of(full = 0f, low = 14f, spent = 34f, asleep = 6f)
    val head = Head(at, pose.tilt, pose.face, pose.cup)
    val length = p.len * size * pose.plen
    val half = p.half * size
    val disc = p.disc * size
    fun stem() = ribbon(line, 4.4f, 2.6f, stemTone)

    if (look == Look.Asleep) {
        stem()
        leaves(p, soil, line, droop, wilt)
        bud(at, pose.tilt, (length + disc) * 1.12f, petal, cupTone)
        return
    }
    if (look == Look.Spent && p.pod != null) {
        stem()
        leaves(p, soil, line, droop, wilt)
        pod(p.pod, soil, head, petal)
        return
    }
    val keep = if (look == Look.Spent) p.keep else null
    stem()
    leaves(p, soil, line, droop, wilt)
    // The green cup shows behind a head that has turned away, or has closed.
    if (pose.face < 0.66f || look == Look.Spent) ellipse(at - head.v * ((disc * 1.1f + 1.5f) * 0.55f), (disc * 1.1f + 1.5f) * 0.8f, (disc * 1.1f + 1.5f) * 0.62f, cupTone, head.across)
    when (p.form) {
        Form.RAY -> {
            if (p.layers > 1) ring(head, p.n, p.kind, length * 0.97f, half, shade, shade.shade(0.6f), start = disc * 0.5f, offset = 180f / p.n, keep = keep)
            if (p.tip != null) {
                ring(head, p.n, p.kind, length, half, p.tip, p.tip.shade(), start = disc * 0.5f, keep = keep)
                ring(head, p.n, p.kind, length * 0.68f, half, petal, shade, start = disc * 0.5f, keep = keep)
            } else {
                ring(head, p.n, p.kind, length, half, petal, shade, start = disc * 0.5f, keep = keep, alt = p.alt)
            }
            disc(head, disc, centre, p.dome)
            if (p.seeds > 0) spiral(p.seeds, disc * 0.78f).forEach { circle(head.project(it.x, it.y, disc * 0.78f * p.dome), disc * 0.085f, centre.light(1.6f)) }
        }
        Form.ROSETTE -> {
            val offset = 90f - 180f / p.n
            // Behind the petals are the points of the cup. They show where petals fell.
            if (look == Look.Spent) ring(head, 5, PetalShape.POINT, disc * 2.3f, disc * 0.6f, cupTone, cupTone, start = disc * 0.5f, offset = offset + 36f, farShade = false)
            ring(head, p.n, p.kind, length, half, petal, shade, start = disc * 0.2f, offset = offset, keep = keep, alt = p.alt)
            disc(head, disc, centre, 0.2f)
            if (p.stamens > 0 && look != Look.Spent) dots(head, p.stamens, disc * 1.9f, 0.95f * size, centre, 0.5f)
        }
        Form.CUP -> {
            // A few broad petals in two layers: the back ones darker.
            ring(head, p.n, p.kind, length, half, shade, shade.shade(0.7f), start = disc * 0.1f, offset = 90f, keep = keep, farShade = false)
            ring(head, p.n, p.kind, length * 0.93f, half, petal, shade, start = disc * 0.1f, offset = 90f - 180f / p.n, keep = keep, alt = p.alt)
            disc(head, disc, centre, 0.2f)
            if (p.stamens > 0 && look != Look.Spent) dots(head, p.stamens, disc * 1.5f, 0.9f * size, centre, 0.3f)
        }
        else -> listOf(1f to shade, 0.76f to petal, 0.52f to petal.light(0.8f), 0.28f to petal.light(1.5f)).forEachIndexed { i, (k, tone) ->
            ring(head, p.n - i * 2, p.kind, length * k, half * (0.8f + 0.25f * k), tone, tone.shade(0.7f), offset = i * 17f + 5f)
        }
    }
}

/** A closed bud: the petals wrap into one teardrop, held by the green cup. */
private fun Sketch.bud(at: Offset, tilt: Float, long: Float, petal: Tone, cup: Tone) {
    val head = Head(at, tilt, 1f)
    petal(head, 90f, PetalShape.POINT, long, long * 0.36f, petal, start = -1f)
    petal(head, 82f, PetalShape.POINT, long * 0.93f, long * 0.2f, petal.shade(), start = -1f)
    petal(head, 98f, PetalShape.POINT, long * 0.9f, long * 0.17f, petal.light(0.5f), start = -1f)
    petal(head, 58f, PetalShape.POINT, long * 0.5f, long * 0.15f, cup, start = -2f)
    petal(head, 122f, PetalShape.POINT, long * 0.5f, long * 0.15f, cup, start = -2f)
    petal(head, 90f, PetalShape.POINT, long * 0.4f, long * 0.19f, cup.light(0.6f), start = -2.5f)
}

/** What stays when the petals are gone: a poppy's capsule, or a lotus's seed head. */
private fun Sketch.pod(pod: Pod, soil: Soil, head: Head, petal: Tone) {
    val (v, u) = head.v to head.u
    val at = head.at
    val tone = soil.leaf.dried(0.75f)
    if (pod == Pod.POPPY) {
        ellipse(at + v * 3.5f, 6.2f, 7f, tone, head.across)
        ellipse(at + v * 9.5f, 6.4f, 1.9f, tone.shade(), head.across)
        ellipse(at - v * 3.5f, 3f, 1.5f, tone.shade(0.6f), head.across)
        // One petal still holds on.
        petal(Head(at - v, 0f, 1f), -62f, PetalShape.WIDE, 15f, 8f, petal)
    } else {
        val top = at + v * 9f
        poly(listOf(top - u * 10.5f, top + u * 10.5f, at + u * 3.2f, at - u * 3.2f), tone)
        ellipse(top, 10.5f, 4f, tone.light(0.9f), head.across)
        listOf(-6f to 0f, -2f to -1.3f, 2.5f to -1.1f, 6f to 0.2f, -3f to 1.4f, 1.5f to 1.6f).forEach { (dx, dy) ->
            ellipse(top + u * dx + v * dy, 1.25f, 0.85f, tone.shade(1.4f), head.across)
        }
    }
}

// ---------- a lotus or a water lily ----------

/** From the side: a fan of pointed petals that opens and closes. Used up, its seed head stays. */
private fun Sketch.lotus(p: Plant, soil: Soil, look: Look) {
    val open = look.of(full = 1f, low = 0.72f, spent = 0f, asleep = 0.1f)
    val at = Offset(look.of(45f, 49f, 54f, 46f), look.of(50f, 52f, 50f, 52f))
    val tilt = look.of(0f, 10f, 26f, 3f)
    val wilt = look.of(0f, 0.1f, 1f, 0f)
    val petal = p.petal.mix(p.petal.dried(), wilt)
    val stemTone = soil.stem.mix(soil.stem.dried(), wilt * 0.6f)
    ribbon(stemLine(at, tilt, -4f, reach = 22f), 4.2f, 2.8f, stemTone)
    if (look == Look.Spent) {
        pod(Pod.LOTUS, soil, Head(at, tilt, 0.6f), petal)
        return
    }
    val length = p.len * 1.16f
    val half = length * p.slim
    val flat = Head(at, 0f, 1f)
    fun row(angles: List<Float>, long: Float, wide: Float, tone: Tone, droop: Float = 0f) = angles.forEach { angle ->
        val a = angle * (0.12f + 0.88f * open) + tilt + if (abs(angle) > 70f) droop * (if (angle > 0f) 1f else -1f) else 0f
        petal(flat, 90f - a, PetalShape.POINT, long, wide, tone, start = -1f)
    }
    // With little time left, the outer petals fall away.
    val droop = look.of(0f, 30f, 0f, 0f)
    row(listOf(-88f, 88f), length * 0.84f, half * 0.9f, petal.shade(1.5f), droop)
    row(listOf(-64f, 64f), length * 0.96f, half, petal.shade(0.95f))
    row(listOf(-34f, 34f), length * 1.02f, half, petal.shade(0.45f))
    row(listOf(0f), length * 1.06f, half, petal.shade(0.2f))
    row(listOf(-76f, 76f), length * 0.66f, half * 0.85f, petal.shade(0.5f), droop * 0.6f)
    row(listOf(-50f, 50f), length * 0.76f, half * 0.95f, petal)
    row(listOf(-20f, 20f), length * 0.74f, half * 0.9f, petal.light(0.5f))
    if (look != Look.Asleep) row(listOf(0f), length * 0.42f, half * 0.8f, p.centre)
    ellipse(Offset(at.x, at.y + 0.6f), half * 0.62f, 2.2f, stemTone, tilt)
}

// ---------- lavender and its kind ----------

/** A small bush of stems, with whorls of florets up each one. Dried, it still stands. */
private fun Sketch.spike(p: Plant, soil: Soil, look: Look) {
    val wilt = look.of(0f, 0.1f, 1f, 0f)
    val petal = p.petal.mix(p.petal.dried(), wilt)
    val stemTone = (p.green ?: soil.stem).mix(Straw, wilt * 0.8f)
    val spread = look.of(15f, 24f, 26f, 9f)
    val height = look.of(82f, 77f, 70f, 74f) * p.size
    val base = Sketch.BASE
    blades(base, soil.leaf.mix(soil.leaf.dried(), wilt * 0.6f), listOf(-52f, -30f, -10f, 12f, 30f, 54f), 20f)
    val middle = (p.stems - 1) / 2f
    // The outer stems first, so that the middle one is in front.
    List(p.stems) { it }.sortedBy { -abs(it - middle) }.forEach { i ->
        val k = if (p.stems > 1) (i - middle) / max(1f, middle) else 0f
        val a = radians(k * spread)
        val h = height * (1f - 0.12f * abs(k))
        val droop = look.of(0f, 0.22f, 0.42f, 0f) * k
        val tip = Offset(base.x + sin(a) * h + droop * 16f, base.y - cos(a) * h + abs(droop) * 20f)
        val line = bezier(
            Offset(base.x + k * 3f, base.y), Offset(base.x + k * 4f, base.y - h * 0.45f),
            Offset(base.x + sin(a) * h * 0.75f, base.y - cos(a) * h * 0.85f), tip, 30,
        )
        ribbon(line, 2.2f, 1f, stemTone)
        for (w in 0 until p.whorls) {
            // A dried spike has lost some of its florets.
            if (look == Look.Spent && (w + i) % 4 == 0) continue
            val t = 1f - (w + 0.2f) / p.whorls * p.cover
            val at = min(line.size - 2, (t * (line.size - 1)).toInt())
            val (from, to) = line[at] to line[at + 1]
            val along = to - from
            val long = hypot(along.x, along.y).takeIf { it > 0f } ?: 1f
            val normal = Offset(-along.y / long, along.x / long)
            val turn = degrees(atan2(along.y, along.x)) + 90f
            val r = p.floret * (0.55f + 0.45f * (w + 1) / p.whorls) * look.of(1f, 1f, 0.9f, 0.62f)
            val tone = if ((w + i) % 2 == 1) petal else petal.shade(0.8f)
            ellipse(from + normal * (r * 0.75f), r, r * 0.72f, tone, turn + 28f)
            ellipse(from - normal * (r * 0.75f), r, r * 0.72f, tone, turn - 28f)
            ellipse(from, r * 0.7f, r * 0.8f, if (look == Look.Asleep) petal.shade(0.4f) else petal.light(0.7f), turn)
        }
    }
    if (look == Look.Spent) onGround { fallen(listOf(Offset(27f, 107f) to 10f, Offset(62f, 108f) to -14f, Offset(53f, 106.5f) to 30f), petal, 2f) }
}

private val Straw = Tone(0.78f, 0.07f, 88f)

// ---------- an allium ----------

/** A ball of small florets on a straight stem. Used up, it is a seed head: the spokes stay. */
private fun Sketch.globe(p: Plant, soil: Soil, look: Look) {
    val wilt = look.of(0f, 0.1f, 1f, 0f)
    val petal = p.petal.mix(p.petal.dried(), wilt)
    val stemTone = soil.stem.mix(soil.stem.dried(), wilt * 0.7f)
    val at = Offset(look.of(46f, 52f, 57f, 47f), look.of(38f, 42f, 50f, 44f))
    blades(Sketch.BASE, soil.leaf.mix(soil.leaf.dried(), wilt * 0.6f), listOf(-60f, -34f, 30f, 58f), 26f)
    ribbon(stemLine(at, look.of(0f, 20f, 40f, 5f), -4f), 3.6f, 2.4f, stemTone)
    when (look) {
        Look.Asleep -> {
            // A bud in its papery sheath.
            val head = Head(at, 5f, 1f)
            petal(head, 90f, PetalShape.POINT, 24f, 8.5f, petal.shade(0.6f), start = -3f)
            petal(head, 90f, PetalShape.POINT, 21f, 5f, petal, start = -2f)
            petal(head, 90f, PetalShape.POINT, 11f, 6.5f, stemTone.light(0.9f), start = -4f)
        }
        Look.Spent -> {
            val seeds = spiral(22, 15f).map { at + it }
            seeds.forEach { ribbon(listOf(at, it), 0.7f, 0.7f, stemTone.light(0.6f)) }
            seeds.forEach { circle(it, 2f, petal) }
        }
        is Look.Open -> {
            val r = 17f
            circle(at, r, petal.shade())
            spiral(46, r * 0.93f).forEachIndexed { i, d ->
                circle(at + d, 2.5f - 0.9f * (hypot(d.x, d.y) / r), if (i % 3 == 0) petal.light() else petal)
            }
        }
    }
}

// ---------- wisteria ----------

/** A woody twig arches over, and a tapering cluster hangs from it. */
private fun Sketch.raceme(p: Plant, soil: Soil, look: Look) {
    val wilt = look.of(0f, 0.1f, 1f, 0f)
    val petal = p.petal.mix(p.petal.dried(), wilt)
    val top = Offset(58f, 30f)
    val line = bezier(Sketch.BASE, Offset(38f, 70f), Offset(34f, 28f), top, 26)
    ribbon(line, 4.4f, 2.2f, soil.stem)
    leaf(line[12], -70f, 17f, 4.4f, soil.leaf)
    leaf(line[19], -30f, 15f, 4f, soil.leaf)
    leaf(top, 70f, 14f, 3.8f, soil.leaf)
    val few = look is Look.Open && look.vigour < SHEDS_BELOW
    val rows = when (look) {
        is Look.Open -> if (few) 7 else 8
        Look.Spent -> 5
        Look.Asleep -> 6
    }
    for (r in rows - 1 downTo 0) {
        val y = top.y + 5f + r * 6.4f
        val width = (1f - r / (rows + 0.5f)) * (if (look == Look.Asleep) 6f else 11f)
        val sway = sin(r * 0.7f) * 1.5f
        val size = if (look == Look.Asleep) 3f else 4.6f - r * 0.28f
        listOf(-1f, 0f, 1f).forEachIndexed { k, dx ->
            if (look == Look.Spent && (r + k) % 3 == 0) return@forEachIndexed
            val tone = listOf(petal.shade(0.9f), petal, petal.light(0.9f))[k]
            ellipse(Offset(top.x + dx * width * 0.62f + sway, y + abs(dx) * 1.5f), size, size * 0.8f, tone, dx * 30f)
        }
    }
    val dropped = listOf(Offset(62f, 106f) to 12f, Offset(28f, 107.5f) to -10f, Offset(70f, 108f) to 24f, Offset(52f, 108.5f) to -30f)
    if (few) onGround { fallen(dropped.take(2), p.petal, 2.6f) }
    if (look == Look.Spent) onGround { fallen(dropped, p.petal.mix(p.petal.dried(), 0.4f), 2.6f) }
}

// ---------- mushrooms ----------

/**
 * A mushroom, which ages as a real one does: not yet open when asleep, a plump cap with plenty of
 * time, a flatter one with little left, and a flat, faded cap on a leaning stalk when used up.
 */
private fun Sketch.mushroom(p: Plant, look: Look) {
    val wilt = look.of(0f, 0.12f, 1f, 0f)
    val cap = p.petal.mix(p.petal.dried(0.8f), wilt)
    val stalk = (p.stalk ?: PaleStalk).mix(OldStalk, wilt)
    if (p.cap == Cap.TROOP) {
        // Three small ones together, the largest in front.
        listOf(Triple(-14f, 0.62f, -8f), Triple(13f, 0.74f, 9f), Triple(0f, 1f, 2f)).forEach { (dx, s, lean) ->
            scaled(s, Offset(45f + dx - 45f * s, 106f - 106f * s)) { oneMushroom(p, Cap.BELL, cap, stalk, look, lean) }
        }
    } else {
        oneMushroom(p, p.cap, cap, stalk, look, 0f)
    }
}

private val PaleStalk = Tone(0.93f, 0.03f, 85f)
private val OldStalk = Tone(0.8f, 0.04f, 75f)

/** A cap's width and height, its stalk's height, and the stalk's width at its top and at its base. */
private class Build(val wide: Float, val high: Float, val tall: Float, val top: Float, val base: Float)

private fun Sketch.oneMushroom(p: Plant, kind: Cap, cap: Tone, stalk: Tone, look: Look, lean0: Float) {
    val build = when (kind) {
        Cap.DOME -> Build(46f, 25f, 60f, 9f, 13f)
        Cap.BUN -> Build(44f, 21f, 44f, 17f, 22f)
        Cap.PARASOL -> Build(56f, 15f, 76f, 5f, 9f)
        Cap.FUNNEL -> Build(44f, 0f, 52f, 8f, 12f)
        else -> Build(27f, 18f, 60f, 4.5f, 6f)
    }
    val wide = build.wide * look.of(1f, 1.14f, if (kind == Cap.PARASOL) 1.04f else 1.16f, 0.58f)
    val high = if (look == Look.Asleep) max(build.high, 18f) * 0.95f else build.high * look.of(1f, 0.66f, 0.36f, 1f)
    val tall = build.tall * look.of(1f, 1f, 0.84f, 0.5f)
    val wt = build.top * look.of(1f, 1f, 1f, 1.15f)
    val wb = build.base * look.of(1f, 1f, 1f, 1.2f)
    val lean = lean0 + look.of(0f, 5f, if (kind == Cap.PARASOL) 10f else 15f, 0f)
    val base = Sketch.BASE
    val top = base + up(lean) * tall
    val gill = stalk.shade(0.9f)
    if (kind == Cap.FUNNEL) {
        chanterelle(cap, look, top, tall, wb)
        return
    }
    val line = bezier(base, Offset(base.x - 2f, base.y - tall * 0.5f), Offset(top.x - 1f, top.y + tall * 0.3f), top, 18)
    ribbon(line, wb, wt, stalk)
    ribbon(line.map { Offset(it.x + wb * 0.22f, it.y) }, wb * 0.3f, wt * 0.3f, stalk.shade(0.55f))
    if ((kind == Cap.DOME || kind == Cap.PARASOL) && look != Look.Asleep) {
        // The ring on the stalk.
        val at = line[(line.size * 0.66f).toInt()]
        ellipse(at, wt * 0.95f, 2.2f, stalk.shade(0.9f), lean)
        ellipse(Offset(at.x, at.y - 1f), wt * 0.85f, 1.6f, stalk, lean)
    }
    if (kind == Cap.DOME) ellipse(Offset(base.x, base.y - 1.5f), wb * 0.62f, 4.2f, stalk)
    if (look == Look.Spent) {
        oldCap(p, cap, gill, top, wide, lean * 0.45f)
        return
    }
    val rim = 4.2f
    rotated(lean * 1.3f, top) {
        val outline = bezier(
            Offset(top.x - wide / 2f, top.y), Offset(top.x - wide / 2f, top.y - high * 1.36f),
            Offset(top.x + wide / 2f, top.y - high * 1.36f), Offset(top.x + wide / 2f, top.y), 22,
        ) + List(7) { i ->
            // Back along the rim, which dips a little in the middle.
            val t = (i + 1) / 8f
            val u = 1f - t
            Offset(top.x + wide / 2f, top.y) * (u * u) + Offset(top.x, top.y + rim * 0.9f) * (2 * u * t) + Offset(top.x - wide / 2f, top.y) * (t * t)
        }
        // The gills show under the rim.
        ellipse(Offset(top.x, top.y + 0.6f), wide / 2f - 1.2f, rim, gill)
        poly(outline, cap)
        clipped(outline) {
            // One darker side, as flat shading.
            ellipse(Offset(top.x + wide * 0.2f, top.y - high * 0.05f), wide * 0.5f, high * 0.5f, cap.shade(0.55f), -12f)
            poly(outline.map { Offset(it.x - wide * 0.07f, it.y - high * 0.1f) }, cap)
            p.warts?.let { warts ->
                WARTS.forEach { (dx, dy, r) -> ellipse(Offset(top.x + dx * wide, top.y - dy * high), r * wide, r * wide * (0.62f + 0.3f * dy), warts, dx * 40f) }
            }
            if (p.umbo) {
                ellipse(Offset(top.x, top.y - high), wide * 0.17f, high * 0.42f, cap.shade(1.6f))
                SCALES.forEach { (dx, dy) -> ellipse(Offset(top.x + dx * wide, top.y - dy * high), wide * 0.035f, high * 0.09f, cap.shade(1.6f), dx * 50f) }
            }
        }
    }
}

/** Where a cap's warts go, across its width and up its height, and their sizes. */
private val WARTS = listOf(
    Triple(-0.30f, 0.42f, 0.085f), Triple(0.02f, 0.70f, 0.10f), Triple(0.27f, 0.40f, 0.075f), Triple(-0.10f, 0.20f, 0.06f),
    Triple(0.14f, 0.16f, 0.05f), Triple(-0.42f, 0.12f, 0.045f), Triple(0.40f, 0.10f, 0.04f),
)
private val SCALES = listOf(-0.3f to 0.45f, 0.3f to 0.5f, -0.14f to 0.2f, 0.16f to 0.22f, -0.38f to 0.14f, 0.4f to 0.16f, 0f to 0.5f)

/** An old cap: flat and faded, seen a little from above, with a wavy rim. It has lost most of its warts. */
private fun Sketch.oldCap(p: Plant, cap: Tone, gill: Tone, top: Offset, wide: Float, tilt: Float) = rotated(tilt, top) {
    fun wavy(dy: Float, rx: Float, ry: Float, wobble: Float) = List(48) { i ->
        val a = 2 * PI.toFloat() * i / 48
        val k = 1f + wobble * sin(a * 6f + 0.6f) + wobble * 0.6f * sin(a * 3f)
        Offset(top.x + rx * k * cos(a), top.y + dy + ry * k * sin(a))
    }
    poly(wavy(3.2f, wide / 2f - 1f, 6f, 0.03f), gill.shade(0.6f))
    poly(wavy(1.6f, wide / 2f, 6.4f, 0.04f), cap.shade())
    poly(wavy(0f, wide / 2f, 6.4f, 0.04f), cap)
    ellipse(Offset(top.x, top.y + 0.4f), wide * 0.2f, 2.4f, cap.shade(0.9f))
    p.warts?.let { warts ->
        listOf(Triple(-0.3f, -0.2f, 0.05f), Triple(0.26f, 0.25f, 0.055f), Triple(0.12f, -0.5f, 0.04f)).forEach { (dx, dy, r) ->
            ellipse(Offset(top.x + dx * wide, top.y + dy * 6f), r * wide, r * wide * 0.5f, warts)
        }
    }
    if (p.umbo) ellipse(Offset(top.x, top.y + 0.2f), wide * 0.14f, 2f, cap.shade(1.8f))
}

/** A chanterelle: the cap and the stalk are one trumpet, with ridges that run down it. */
private fun Sketch.chanterelle(cap: Tone, look: Look, top: Offset, tall: Float, wb: Float) {
    val base = Sketch.BASE
    val rimWide = look.of(23f, 25f, 24f, 9f)
    val rimHigh = look.of(5.5f, 4f, 3f, 3.5f)
    val body = bezier(
        Offset(top.x - rimWide, top.y), Offset(top.x - rimWide * 0.5f, top.y + 12f),
        Offset(base.x - wb * 0.7f, base.y - tall * 0.5f), Offset(base.x - wb / 2f, base.y), 14,
    ) + bezier(
        Offset(base.x + wb / 2f, base.y), Offset(base.x + wb * 0.7f, base.y - tall * 0.5f),
        Offset(top.x + rimWide * 0.5f, top.y + 12f), Offset(top.x + rimWide, top.y), 14,
    )
    poly(body, cap.shade(0.7f))
    for (k in listOf(-0.6f, -0.2f, 0.2f, 0.6f)) {
        ribbon(
            bezier(
                Offset(top.x + rimWide * k, top.y + 3f), Offset(top.x + rimWide * k * 0.6f, top.y + 12f),
                Offset(base.x + wb * k * 0.6f, base.y - tall * 0.55f), Offset(base.x + wb * k * 0.5f, base.y - tall * 0.3f), 8,
            ),
            1.3f, 0.5f, cap.shade(1.5f),
        )
    }
    val frill = if (look == Look.Asleep) 0f else 0.09f
    poly(List(40) { i ->
        val a = 2 * PI.toFloat() * i / 40
        val k = 1f + frill * sin(a * 5f) + if (look == Look.Spent) 0.12f * sin(a * 3f + 1f) else 0f
        Offset(top.x + rimWide * k * cos(a), top.y + rimHigh * k * sin(a))
    }, cap)
    ellipse(Offset(top.x, top.y + 0.8f), rimWide * 0.42f, rimHigh * 0.42f, cap.shade(0.9f))
}
