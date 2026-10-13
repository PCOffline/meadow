package dev.pcoffline.meadow.a11y

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max
import kotlin.math.min

/**
 * The contrast of text as it is actually drawn (WCAG 2.2 AA, 1.4.3), measured from pixels the way
 * Android's Accessibility Scanner does. It catches what checking colour pairs can't: text faded
 * with alpha, drawn over gradients or pictures, or given a colour that doesn't suit its surface.
 *
 * For each piece of text, the most common colour in its bounds is the background, and the
 * foreground is the contrast its solid strokes reach (the top tenth of its inked pixels; edges
 * are blended with the background by anti-aliasing, so they always read lower).
 */
object TextContrast {
    /** Normal text needs 4.5:1. */
    const val NORMAL = 4.5f

    /** Large text (18sp, or 14sp bold) needs 3:1. */
    const val LARGE = 3f

    /** Below this, a pixel is background (or noise in it), not ink. */
    private const val INK = 1.25f

    /**
     * Problems with the text under [root] (an unmerged semantics tree), drawn in [image], which
     * must be a capture of exactly that root.
     */
    fun problems(root: SemanticsNode, image: ImageBitmap): List<String> {
        val bitmap = image.asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val luminance = HashMap<Int, Float>()
        fun lum(argb: Int) = luminance.getOrPut(argb) { Color(argb).luminance() }

        val problems = mutableListOf<String>()
        for (node in walk(root)) {
            val text = textOf(node) ?: continue
            // Emoji and symbols are pictures, not text to read.
            if (text.none { it.isLetterOrDigit() }) continue
            if (disabled(node)) continue // Inactive controls are exempt (1.4.3).
            val bounds = node.boundsInRoot
            val left = max(0, bounds.left.toInt())
            val top = max(0, bounds.top.toInt())
            val right = min(bitmap.width, bounds.right.toInt())
            val bottom = min(bitmap.height, bounds.bottom.toInt())
            if (right - left < 4 || bottom - top < 4) continue // Off screen or scrolled away.
            // Partly scrolled out of view: only what's fully on screen can be measured fairly.
            if (bottom - top < node.size.height - 2 || right - left < node.size.width - 2) continue

            val counts = HashMap<Int, Int>()
            for (y in top until bottom) for (x in left until right) {
                val argb = pixels[y * bitmap.width + x]
                counts[argb] = (counts[argb] ?: 0) + 1
            }
            val background = counts.maxBy { it.value }.key
            val bgLum = lum(background)
            fun ratio(argb: Int): Float {
                val l = lum(argb)
                return (max(l, bgLum) + 0.05f) / (min(l, bgLum) + 0.05f)
            }
            // Every pixel's contrast with the background, strongest first.
            val ink = counts.entries.map { ratio(it.key) to it.value }.filter { it.first >= INK }.sortedByDescending { it.first }
            val inked = ink.sumOf { it.second }
            val measured: Float
            val foreground: Int
            if (inked == 0) {
                measured = counts.keys.maxOf { ratio(it) }
                foreground = counts.keys.maxBy { ratio(it) }
            } else {
                // The contrast the top tenth of the ink reaches: solid strokes, not their soft edges.
                var seen = 0
                val target = max(1, inked / 10)
                val strongest = ink.first { seen += it.second; seen >= target }
                measured = strongest.first
                foreground = counts.keys.first { ratio(it) == strongest.first }
            }
            val needed = if (isLarge(node)) LARGE else NORMAL
            if (measured < needed) {
                problems += "\"%s\" is drawn at %.2f:1 (#%06X on #%06X); it needs %.1f:1".format(
                    text.take(48), measured, foreground and 0xFFFFFF, background and 0xFFFFFF, needed,
                )
            }
        }
        return problems
    }

    private fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)

    private fun textOf(node: SemanticsNode): String? {
        val config = node.config
        // Text fields draw what's typed in onSurface and their labels as their own text nodes.
        if (SemanticsActions.SetText in config) return null
        return config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.takeIf { it.isNotBlank() }
    }

    private fun disabled(node: SemanticsNode): Boolean =
        generateSequence(node) { it.parent }.any { SemanticsProperties.Disabled in it.config }

    private fun isLarge(node: SemanticsNode): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val style = layouts.firstOrNull()?.layoutInput?.style ?: return false
        val size = if (style.fontSize.isSp) style.fontSize.value else 14f
        val bold = (style.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold
        return size >= 18f || (bold && size >= 14f)
    }
}
