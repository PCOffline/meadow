package dev.pcoffline.meadow.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.Density

/**
 * The checks screen reader and switch users rely on (WCAG 2.2 AA, Android accessibility
 * guidelines), applied to a whole screen at once:
 * - everything you can tap, toggle or type into says what it is;
 * - touch targets are at least 48dp;
 * - a screen has at least one heading to navigate by;
 * - images that carry meaning are described (decorative ones have no semantics at all).
 */
object Accessibility {
    private const val MIN_TOUCH_DP = 48f

    fun problems(roots: List<SemanticsNode>, density: Density, requireHeading: Boolean = true): List<String> {
        val nodes = roots.flatMap { walk(it) }
        val problems = mutableListOf<String>()
        for (node in nodes) {
            val config = node.config
            val actionable = SemanticsActions.OnClick in config || SemanticsActions.SetText in config ||
                SemanticsProperties.ToggleableState in config
            if (!actionable) continue
            val label = describe(node)
            if (label.isBlank()) problems += "An unlabelled control (${roleOf(node)}) at ${node.boundsInRoot}"
            val disabled = SemanticsProperties.Disabled in config
            if (!disabled) {
                val minPx = with(density) { (MIN_TOUCH_DP - 0.5f) * this.density }
                // Material components draw at 40dp but reserve 48dp around themselves for touch
                // (minimumInteractiveComponentSize); that reserved space is the layout's size.
                val width = maxOf(node.size.width.toFloat(), node.touchBoundsInRoot.width, node.layoutInfo.width.toFloat())
                val height = maxOf(node.size.height.toFloat(), node.touchBoundsInRoot.height, node.layoutInfo.height.toFloat())
                if (width < minPx || height < minPx) {
                    val dp = { px: Float -> px / density.density }
                    problems += "\"$label\" is only %.0f×%.0fdp; touch targets need 48×48dp".format(dp(width), dp(height))
                }
            }
        }
        if (requireHeading && nodes.none { SemanticsProperties.Heading in it.config }) {
            problems += "No heading: screen reader users navigate a screen by its headings"
        }
        return problems
    }

    private fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)

    private fun roleOf(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "no role"

    private fun describe(node: SemanticsNode): String {
        val config = node.config
        return listOfNotNull(
            config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
            config.getOrNull(SemanticsProperties.EditableText)?.text?.takeIf { it.isNotBlank() },
            config.getOrNull(SemanticsActions.OnClick)?.label,
        ).joinToString(" ").trim()
    }
}

/** Fails with every accessibility problem on screen, not just the first. */
fun SemanticsNodeInteractionsProvider.assertAccessible(density: Density, requireHeading: Boolean = true) {
    val roots = onAllNodes(isRoot()).fetchSemanticsNodes()
    val problems = Accessibility.problems(roots, density, requireHeading)
    if (problems.isNotEmpty()) throw AssertionError("Accessibility problems:\n" + problems.joinToString("\n") { "- $it" })
}
