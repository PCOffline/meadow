package dev.pcoffline.meadow.ui.components.flora

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A colour as lightness (0 to 1), chroma and hue in degrees (OKLCH), with the tones that each
 * plant part is drawn in. In this space a change of lightness or chroma keeps the hue, so a shaded
 * or dried petal is still that flower's colour.
 */
data class Tone(val l: Float, val c: Float, val h: Float) {
    val color: Color get() = oklch(l, c, h)

    /** Darker and a little richer, turned slightly towards blue-violet, as a shadow is. */
    fun shade(k: Float = 1f) = Tone(l - 0.10f * k, c + 0.012f * k, turn(h, 285f, 0.09f * k))

    /** Lighter and a little paler, turned slightly towards yellow, as a lit part is. */
    fun light(k: Float = 1f) = Tone(min(0.97f, l + 0.07f * k), c * (1f - 0.25f * k), turn(h, 95f, 0.10f * k))

    /**
     * As a dried petal: a little darker, with less chroma. It keeps its hue. Only a red, orange
     * or yellow petal turns slightly towards straw.
     */
    fun dried(k: Float = 1f) = Tone(l - 0.06f * k, c * (1f - 0.42f * k), if (h < 130f) turn(h, 75f, 0.14f * k) else h)

    /** By night: a little darker and paler, turned slightly towards blue. */
    fun night() = Tone(l - 0.04f, c * 0.9f, turn(h, 265f, 0.06f))

    /** This tone, [k] of the way to [other], the short way round the hues. */
    fun mix(other: Tone, k: Float) = Tone(l + (other.l - l) * k, c + (other.c - c) * k, turn(h, other.h, k))
}

/** The hue [h] turned [share] of the way towards [toward], the short way round. */
internal fun turn(h: Float, toward: Float, share: Float): Float {
    val d = (toward - h + 540f).mod(360f) - 180f
    return (h + d * share).mod(360f)
}

/** The sRGB colour of an OKLCH colour. A colour outside sRGB is brought to its nearest edge. */
internal fun oklch(l: Float, c: Float, h: Float): Color {
    val hr = Math.toRadians(h.toDouble())
    val a = c * cos(hr)
    val b = c * sin(hr)
    val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s3 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    fun encode(v: Double): Float {
        val x = v.coerceIn(0.0, 1.0)
        return (if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055).toFloat()
    }
    return Color(
        encode(4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3),
        encode(-1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3),
        encode(-0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3),
    )
}
