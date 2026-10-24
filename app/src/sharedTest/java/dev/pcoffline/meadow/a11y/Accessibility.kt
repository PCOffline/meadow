package dev.pcoffline.meadow.a11y

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The checks screen reader and switch users rely on (WCAG 2.2 AA, Android accessibility
 * guidelines), applied to a whole screen at once:
 * - everything you can tap, toggle or type into says what it is;
 * - touch targets are at least 48dp;
 * - a screen has at least one heading to navigate by;
 * - images that carry meaning are described (decorative ones have no semantics at all);
 * - text is readable where it's drawn ([TextContrast]).
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

/** Fails with every accessibility problem on screen (dialogs included), not just the first. */
fun SemanticsNodeInteractionsProvider.assertAccessible(density: Density, requireHeading: Boolean = true) {
    val roots = onAllNodes(isRoot()).fetchSemanticsNodes()
    val problems = Accessibility.problems(roots, density, requireHeading) + contrastProblems()
    if (problems.isNotEmpty()) throw AssertionError("Accessibility problems:\n" + problems.joinToString("\n") { "- $it" })
}

/**
 * Text drawn too faintly anywhere on screen, measured from a capture of each window.
 *
 * A dialog's root captures the activity's window behind it, not the dialog, so on a phone dialogs
 * are measured from a screenshot of the whole screen instead. Robolectric can't take one, so
 * there dialogs are left to the instrumented tests (DialogContrastTest); their colours are still
 * checked in every theme by ThemeContrastTest.
 */
fun SemanticsNodeInteractionsProvider.contrastProblems(): List<String> {
    val roots = onAllNodes(isRoot(), useUnmergedTree = true)
    return roots.fetchSemanticsNodes().indices.flatMap { i ->
        val root = roots[i]
        val node = root.fetchSemanticsNode()
        when {
            // A window with nothing in it (say, the screen behind a dialog-only test) can't be captured.
            node.size.width == 0 || node.size.height == 0 -> emptyList()
            isDialog(node) -> if (ROBOLECTRIC) emptyList() else dialogProblems(node)
            else -> TextContrast.problems(node, root.captureToImage())
        }
    }
}

private val ROBOLECTRIC = Build.FINGERPRINT == "robolectric"

private fun dialogProblems(root: SemanticsNode): List<String> {
    // The window's own fade-in isn't something Compose waits for.
    SystemClock.sleep(WINDOW_SETTLE_MS)
    val shot = screenshotWithoutErrorDialogs()
    val screen = shot.copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()
    val problems = TextContrast.problems(root, screen) { Rect(it.positionOnScreen, it.size.toSize()) }
    return if (problems.isEmpty()) problems else listOf(whatWasMeasured(root, shot)) + problems
}

/**
 * What a failed dialog measurement looked at: the screenshot (kept on the device, under
 * [SHOT_DIR], for CI to collect), where the dialog was and every window on screen.
 */
private fun whatWasMeasured(dialog: SemanticsNode, shot: Bitmap): String {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    val windows = automation.windows.joinToString("; ") { window ->
        val bounds = android.graphics.Rect().also(window::getBoundsInScreen)
        "${window.title} (${window.root?.packageName}, type ${window.type}, layer ${window.layer}) at $bounds"
    }
    val file = "$SHOT_DIR/dialog-${SystemClock.uptimeMillis()}.png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val png = java.io.ByteArrayOutputStream().also { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        shell("mkdir -p $SHOT_DIR")
        val (output, input) = automation.executeShellCommandRw("dd of=$file")
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write(png) }
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }
    return "Measured a ${shot.width}×${shot.height} screenshot ($file), dialog at ${dialog.positionOnScreen} " +
        "sized ${dialog.size}; windows: $windows"
}

/**
 * A screenshot of the whole screen with no system error dialog in it. A slow emulator's "Pixel
 * Launcher isn't responding" (or another app's crash dialog) sits over the app and would be
 * measured in place of the dialog, and can come back at any moment, so it is closed and the
 * screenshot retaken until one is taken with none on screen. One that won't go fails the test by
 * name instead of blaming the app's colours.
 */
private fun screenshotWithoutErrorDialogs(): Bitmap {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    fun errorDialogs() = automation.windows.filter {
        it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.root?.packageName?.toString() == "android"
    }
    val deadline = SystemClock.uptimeMillis() + ERROR_DIALOG_WAIT_MS
    while (true) {
        val before = errorDialogs()
        if (before.isEmpty()) {
            val shot = automation.takeScreenshot() ?: throw AssertionError("Couldn't take a screenshot to measure a dialog")
            // Still none once it's taken, so none can have been in it.
            if (errorDialogs().isEmpty()) return shot
            continue
        }
        if (SystemClock.uptimeMillis() > deadline) {
            throw AssertionError("Can't measure the dialog: \"${before.joinToString { "${it.title}" }}\" is on top of it")
        }
        for (dialog in before) {
            val button = dialog.root?.let { root -> ERROR_DIALOG_BUTTONS.firstNotNullOfOrNull { root.findAccessibilityNodeInfosByViewId(it).firstOrNull() } }
            // Without a button to press, ask the system to close its dialogs instead.
            if (button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) {
                shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            }
        }
        SystemClock.sleep(WINDOW_SETTLE_MS)
    }
}

private fun shell(command: String) {
    val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
}

private const val WINDOW_SETTLE_MS = 400L
private const val SHOT_DIR = "/data/local/tmp/meadow-contrast"
private const val ERROR_DIALOG_WAIT_MS = 10_000L
// Close, not Wait: an app stuck enough to be reported stays stuck, and the report comes back.
private val ERROR_DIALOG_BUTTONS = listOf("android:id/aerr_close")

private fun isDialog(node: SemanticsNode): Boolean = SemanticsProperties.IsDialog in node.config || node.children.any(::isDialog)

/** Fails with every piece of text drawn too faintly. */
fun SemanticsNodeInteractionsProvider.assertReadable() {
    val problems = contrastProblems()
    if (problems.isNotEmpty()) throw AssertionError("Contrast problems:\n" + problems.joinToString("\n") { "- $it" })
}
