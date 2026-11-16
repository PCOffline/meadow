package dev.pcoffline.meadow.ui.components.flora

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** One flat shape of a drawing: a closed outline ([points] as x, y, x, y) in one [color], cut to [clip] when it has one. */
internal class Piece(val points: FloatArray, val color: Color, val clip: FloatArray? = null)

/**
 * A plant as flat shapes, in a box [WIDTH] wide and [HEIGHT] tall with the ground at [BASE].
 * [ground] holds what lies on the ground and stays still. [body] holds the plant, which sways.
 * Each list is in painting order.
 */
internal class Sketch {
    val ground = ArrayList<Piece>()
    val body = ArrayList<Piece>()
    private var target = body
    private var matrix = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    private var clip: FloatArray? = null

    /** Draws [block] on the ground. */
    fun onGround(block: () -> Unit) {
        val was = target
        target = ground
        block()
        target = was
    }

    fun poly(points: List<Offset>, tone: Tone) {
        target += Piece(flat(points), tone.color, clip)
    }

    fun ellipse(c: Offset, rx: Float, ry: Float, tone: Tone, degrees: Float = 0f) {
        val n = if (rx < 2.5f) 12 else 28
        val r = Math.toRadians(degrees.toDouble())
        val (cr, sr) = cos(r).toFloat() to sin(r).toFloat()
        poly(List(n) { i ->
            val a = 2 * PI * i / n
            val x = rx * cos(a).toFloat()
            val y = ry * sin(a).toFloat()
            Offset(c.x + x * cr - y * sr, c.y + x * sr + y * cr)
        }, tone)
    }

    fun circle(c: Offset, r: Float, tone: Tone) = ellipse(c, r, r, tone)

    /** A tapered stem along [line]: [w0] wide at its start and [w1] at its end, with a round tip. */
    fun ribbon(line: List<Offset>, w0: Float, w1: Float, tone: Tone) {
        val n = line.size - 1
        val left = ArrayList<Offset>(line.size)
        val right = ArrayList<Offset>(line.size)
        line.forEachIndexed { i, p ->
            val a = line[maxOf(i - 1, 0)]
            val b = line[minOf(i + 1, n)]
            val d = hypot(b.x - a.x, b.y - a.y).takeIf { it > 0f } ?: 1f
            val normal = Offset(-(b.y - a.y) / d, (b.x - a.x) / d)
            val w = (w0 + (w1 - w0) * i / n) / 2f
            left += p + normal * w
            right += p - normal * w
        }
        poly(left + right.reversed(), tone)
        circle(line.last(), w1 / 2f, tone)
    }

    /** Draws [block] turned [degrees] clockwise about [pivot]. */
    fun rotated(degrees: Float, pivot: Offset, block: () -> Unit) {
        val r = Math.toRadians(degrees.toDouble())
        val (c, s) = cos(r).toFloat() to sin(r).toFloat()
        transformed(floatArrayOf(c, s, -s, c, pivot.x - c * pivot.x + s * pivot.y, pivot.y - s * pivot.x - c * pivot.y), block)
    }

    /** Draws [block] at [scale] of its size, moved by [move]. */
    fun scaled(scale: Float, move: Offset, block: () -> Unit) = transformed(floatArrayOf(scale, 0f, 0f, scale, move.x, move.y), block)

    /** Draws [block] cut to [outline]. */
    fun clipped(outline: List<Offset>, block: () -> Unit) {
        val was = clip
        clip = flat(outline)
        block()
        clip = was
    }

    private fun transformed(local: FloatArray, block: () -> Unit) {
        val was = matrix
        val (a, b, c, d, e) = was
        val f = was[5]
        matrix = floatArrayOf(
            a * local[0] + c * local[1], b * local[0] + d * local[1],
            a * local[2] + c * local[3], b * local[2] + d * local[3],
            a * local[4] + c * local[5] + e, b * local[4] + d * local[5] + f,
        )
        block()
        matrix = was
    }

    private fun flat(points: List<Offset>): FloatArray {
        val m = matrix
        val out = FloatArray(points.size * 2)
        points.forEachIndexed { i, p ->
            out[2 * i] = m[0] * p.x + m[2] * p.y + m[4]
            out[2 * i + 1] = m[1] * p.x + m[3] * p.y + m[5]
        }
        return out
    }

    companion object {
        const val WIDTH = 90f
        const val HEIGHT = 120f
        val BASE = Offset(45f, 106f)
    }
}

/** Points along the curve from [p0] to [p3] that bends towards [p1] and then [p2]. */
internal fun bezier(p0: Offset, p1: Offset, p2: Offset, p3: Offset, n: Int = 24): List<Offset> = List(n + 1) { i ->
    val t = i / n.toFloat()
    val u = 1f - t
    p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
}
