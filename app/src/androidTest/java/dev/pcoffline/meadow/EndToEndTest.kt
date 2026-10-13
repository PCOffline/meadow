package dev.pcoffline.meadow

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import java.net.HttpURLConnection
import java.net.URL

/**
 * What every end-to-end test needs: finding text on screen, waiting for it with a useful message
 * when it never comes, and reading the Firestore emulator directly to check the backend.
 *
 * Run with the emulators started (see README) and:
 * ./gradlew connectedDebugAndroidTest -Pmeadow.emulatorHost=10.0.2.2
 */
abstract class EndToEndTest {
    @get:Rule val compose = createEmptyComposeRule()
    protected val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun needsEmulators() {
        assumeTrue("Build with -Pmeadow.emulatorHost to run end-to-end tests", BuildConfig.EMULATOR_HOST.isNotEmpty())
    }

    protected fun text(@StringRes id: Int, vararg args: Any) = app.getString(id, *args)
    protected fun node(@StringRes id: Int): SemanticsNodeInteraction = compose.onNodeWithText(text(id))
    protected fun waitFor(@StringRes id: Int, substring: Boolean = false) = waitForText(text(id), substring)
    protected fun waitForText(value: String, substring: Boolean = false, timeout: Long = 60_000) {
        try {
            compose.waitUntil(timeout) { compose.onAllNodesWithText(value, substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            // One line, so CI logs show where it stopped and what was on screen instead.
            throw AssertionError("Never saw \"$value\". On screen: ${screenText()}", e)
        }
    }

    protected fun screenText(): String = runCatching {
        compose.onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { root ->
            fun walk(node: SemanticsNode): List<String> =
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    node.children.flatMap(::walk)
            walk(root)
        }.distinct().joinToString(" | ").replace('\n', ' ').take(1500)
    }.getOrElse { "(couldn't read the screen: $it)" }

    /** Snackbars sit over the bottom of the screen; taps there would land on them. */
    protected fun waitForSnackbarToGo(@StringRes id: Int, vararg args: Any) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text(id, *args)).fetchSemanticsNodes().isEmpty() }

    /** Reads a document straight from the Firestore emulator, bypassing security rules. */
    protected fun backendDoc(path: String): JSONObject? {
        val url = URL("http://${BuildConfig.EMULATOR_HOST}:8080/v1/projects/demo-meadow/databases/(default)/documents/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply { setRequestProperty("Authorization", "Bearer owner") }
        return try {
            if (connection.responseCode == 404) null else JSONObject(connection.inputStream.bufferedReader().readText()).getJSONObject("fields")
        } finally {
            connection.disconnect()
        }
    }

    protected fun waitForBackend(path: String, timeout: Long = 20_000, check: (JSONObject) -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!runCatching { check(backendDoc(path)!!) }.getOrDefault(false)) {
            if (System.currentTimeMillis() > end) {
                throw AssertionError("$path never changed. Now: ${runCatching { backendDoc(path) }.getOrNull()}. On screen: ${screenText()}")
            }
            Thread.sleep(250)
        }
    }

    /** The strings in a Firestore REST array field (empty when the array is). */
    protected fun JSONObject.strings(field: String): List<String> {
        val values = getJSONObject(field).getJSONObject("arrayValue").optJSONArray("values") ?: JSONArray()
        return (0 until values.length()).map { values.getJSONObject(it).getString("stringValue") }
    }

    /** From the first screen to the garden, as a guest called [name], skipping the permissions for now. */
    protected fun startAsGuest(name: String) {
        waitFor(R.string.intro_next)
        node(R.string.intro_next).performClick()
        node(R.string.intro_next).performClick()
        node(R.string.intro_begin).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.onboarding_name_label))).performTextInput(name)
        node(R.string.legal_agree).performScrollTo().performClick()
        node(R.string.onboarding_plant).performScrollTo().performClick()
        waitFor(R.string.setup_later)
        compose.onAllNodesWithText(text(R.string.setup_later)).onFirst().performScrollTo().performClick()
        waitForText(name, substring = true)
    }
}
