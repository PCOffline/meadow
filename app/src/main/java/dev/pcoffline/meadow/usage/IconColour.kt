package dev.pcoffline.meadow.usage

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/**
 * The colour that an app's icon shows most, as lightness (0 to 1), chroma and hue in degrees
 * (OKLCH, as the plants' colours). A chroma of 0 is a grey icon: black, white or grey only.
 */
@Serializable
data class IconColour(val l: Float, val c: Float, val h: Float) {
    val grey: Boolean get() = c <= 0f

    companion object {
        val Grey = IconColour(0f, 0f, 0f)

        /**
         * The colour that [pixels] (ARGB) show most. Transparent pixels and greys count for
         * nothing, so a white or black background doesn't hide a coloured logo. Each other pixel
         * counts by its chroma, in bands of hue. The colour is the mean of the heaviest band and
         * the bands next to it. When less than [MIN_COLOURED] of the icon has colour, it is [Grey].
         */
        fun of(pixels: IntArray): IconColour {
            val weight = DoubleArray(BANDS)
            val sumL = DoubleArray(BANDS)
            val sumA = DoubleArray(BANDS)
            val sumB = DoubleArray(BANDS)
            var opaque = 0
            var coloured = 0
            for (p in pixels) {
                if ((p ushr 24) < MIN_ALPHA) continue
                opaque++
                val (l, a, b) = oklab(p)
                val c = hypot(a, b)
                if (c < MIN_CHROMA) continue
                coloured++
                val band = ((Math.toDegrees(atan2(b, a)) + 360.0) % 360.0 / (360.0 / BANDS)).toInt().coerceIn(0, BANDS - 1)
                weight[band] += c
                sumL[band] += l * c
                sumA[band] += a * c
                sumB[band] += b * c
            }
            if (opaque == 0 || coloured < opaque * MIN_COLOURED) return Grey
            fun around(band: Int) = (-1..1).map { (band + it + BANDS) % BANDS }
            val best = (0 until BANDS).maxBy { band -> around(band).sumOf { weight[it] } }
            val bands = around(best)
            val w = bands.sumOf { weight[it] }
            val a = bands.sumOf { sumA[it] } / w
            val b = bands.sumOf { sumB[it] } / w
            val hue = (Math.toDegrees(atan2(b, a)) + 360.0) % 360.0
            return IconColour((bands.sumOf { sumL[it] } / w).toFloat(), hypot(a, b).toFloat(), hue.toFloat())
        }

        /** An sRGB pixel as OKLab: lightness, a and b. */
        private fun oklab(argb: Int): Triple<Double, Double, Double> {
            fun linear(channel: Int): Double {
                val v = channel / 255.0
                return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            val r = linear((argb shr 16) and 0xFF)
            val g = linear((argb shr 8) and 0xFF)
            val bl = linear(argb and 0xFF)
            val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * bl)
            val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * bl)
            val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * bl)
            return Triple(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
            )
        }

        /** Bands of hue, 15 degrees each. */
        private const val BANDS = 24

        /** Pixels this see-through or more count for nothing. */
        private const val MIN_ALPHA = 128

        /** Less chroma than this is grey. */
        private const val MIN_CHROMA = 0.06

        /** The share of an icon that must have colour for the icon to have a colour. */
        private const val MIN_COLOURED = 0.05
    }
}

