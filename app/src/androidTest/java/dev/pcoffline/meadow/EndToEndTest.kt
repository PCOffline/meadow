package dev.pcoffline.meadow

import android.app.Activity
import android.app.Application
import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.firebase.auth.FirebaseAuth
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
    protected fun inDialog(value: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onNode(hasText(value, substring) and hasAnyAncestor(isDialog()))

    /** Whether [value] is on screen now. */
    protected fun showing(value: String, substring: Boolean = false) =
        compose.onAllNodesWithText(value, substring).fetchSemanticsNodes().isNotEmpty()

    /** Waits for [check], saying [what] never happened (and what was on screen) if it doesn't. */
    protected fun eventually(what: String, timeout: Long = 20_000, check: () -> Boolean) {
        try {
            compose.waitUntil(timeout) { runCatching(check).getOrDefault(false) }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Never saw $what. On screen: ${screenText()}", e)
        }
    }
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
                    listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text?.takeIf { it.isNotEmpty() }?.let { "[typed: $it]" }) +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    node.children.flatMap(::walk)
            walk(root)
        }.distinct().joinToString(" | ").replace('\n', ' ').take(1500)
    }.getOrElse { "(couldn't read the screen: $it)" }

    /** Types into [field], then closes the keyboard, which could otherwise cover what's tapped next. */
    protected fun typeInto(field: SemanticsMatcher, value: String) {
        compose.onNode(field).performTextInput(value)
        hideKeyboard()
    }

    /**
     * Types into a field in a dialog. The keyboard stays up: it belongs to the dialog's window,
     * which [hideKeyboard] doesn't watch, and test taps reach the dialog's buttons regardless.
     */
    protected fun typeIntoDialog(field: SemanticsMatcher, value: String) {
        compose.onNode(field and hasAnyAncestor(isDialog())).performTextInput(value)
        compose.waitForIdle()
    }

    private fun hideKeyboard() {
        val ime = WindowInsetsCompat.Type.ime()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun onScreen(block: (Activity) -> Unit) = instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()?.let(block)
        }
        onScreen { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(ime) }
        compose.waitUntil(5_000) {
            var open = false
            onScreen { open = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(ime) == true }
            !open
        }
        compose.waitForIdle()
    }

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

    /** The fields of each document in a collection, read straight from the Firestore emulator. */
    protected fun backendDocs(collection: String): List<JSONObject> {
        val documents = JSONObject(emulatorGet(8080, "v1/projects/demo-meadow/databases/(default)/documents/$collection?pageSize=300"))
            .optJSONArray("documents") ?: JSONArray()
        return (0 until documents.length()).map { documents.getJSONObject(it).getJSONObject("fields") }
    }

    protected fun waitForBackend(path: String, timeout: Long = 20_000, check: (JSONObject) -> Boolean) =
        waitUntilBackend(path, timeout, { backendDoc(path) }) { check(it!!) }

    protected fun waitForBackendDocs(collection: String, timeout: Long = 20_000, check: (List<JSONObject>) -> Boolean) =
        waitUntilBackend(collection, timeout, { backendDocs(collection) }, check)

    private fun <T> waitUntilBackend(path: String, timeout: Long, read: () -> T, check: (T) -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!runCatching { check(read()) }.getOrDefault(false)) {
            if (System.currentTimeMillis() > end) {
                throw AssertionError("$path never changed. Now: ${runCatching(read).getOrNull()}. On screen: ${screenText()}")
            }
            Thread.sleep(250)
        }
    }

    /** A GET to one of the emulators (port [port]) with owner rights, returning the body. */
    protected fun emulatorGet(port: Int, path: String): String {
        val connection = (URL("http://${BuildConfig.EMULATOR_HOST}:$port/$path").openConnection() as HttpURLConnection)
            .apply { setRequestProperty("Authorization", "Bearer owner") }
        return try {
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    protected fun JSONObject.string(field: String): String? = optJSONObject(field)?.optString("stringValue")

    /** A Firestore REST integer field, or null when it's missing or not a number. */
    protected fun JSONObject.int(field: String): Int? = optJSONObject(field)?.optString("integerValue")?.toIntOrNull()

    protected fun JSONObject.bool(field: String): Boolean? = optJSONObject(field)?.takeIf { it.has("booleanValue") }?.getBoolean("booleanValue")

    /** The package of Pebbles, a stand-in app in the test APK for the tests to limit: Meadow can't limit itself. */
    protected val pebbles: String get() = InstrumentationRegistry.getInstrumentation().context.packageName

    /** Opens Pebbles, as if from the launcher. */
    protected fun openPebbles() = app.startActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(pebbles, PebblesActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /**
     * Runs [command] as the shell, the way adb would, and returns what it printed. Accessibility
     * services stay on: by default a test's UiAutomation silences them.
     */
    protected fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().decodeToString() }
    }

    /** Signs this phone in as [friend], who already has an email and a profile, before Meadow opens. */
    protected fun signInAs(friend: EmulatorFriend) {
        EmulatorFriend.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(friend.email, EmulatorFriend.PASSWORD))
    }

    /** From Meadow opening, signed in as [name], past the setup offer to the garden. */
    protected fun intoTheGarden(name: String) {
        waitFor(R.string.setup_later)
        compose.onAllNodesWithText(text(R.string.setup_later)).onFirst().performScrollTo().performClick()
        waitForText(name, substring = true)
    }

    /** The newest sign-in link the Auth emulator "emailed" to [email]. */
    protected fun emailedLink(email: String): String {
        val codes = JSONObject(emulatorGet(9099, "emulator/v1/projects/demo-meadow/oobCodes")).getJSONArray("oobCodes")
        return (codes.length() - 1 downTo 0).map(codes::getJSONObject)
            .first { it.optString("email") == email && it.optString("requestType") == "EMAIL_SIGNIN" }
            .getString("oobLink")
    }

    /** Opens an emailed sign-in link the way tapping it in a mail app does. */
    protected fun openEmailedLink(email: String) {
        val link = Uri.parse("https://${BuildConfig.AUTH_LINK_HOST}/__/auth/links?link=${Uri.encode(emailedLink(email))}")
        app.startActivity(
            Intent(Intent.ACTION_VIEW, link).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
        typeInto(hasSetTextAction() and hasText(text(R.string.onboarding_name_label)), name)
        node(R.string.legal_agree).performScrollTo().performClick()
        node(R.string.onboarding_plant).performScrollTo().performClick()
        intoTheGarden(name)
    }

    /**
     * Becomes [friend]'s Bumblebee by pasting their invite link on the circle screen (below the
     * fold, so scrolled to first) and saying yes. Returns the uid of the person on this phone.
     */
    protected fun acceptInviteFrom(friend: EmulatorFriend, friendName: String): String {
        val token = friend.call("createInviteLink")!!["token"] as String
        val me = FirebaseAuth.getInstance().currentUser!!.uid
        node(R.string.tab_circle).performClick()
        waitFor(R.string.circle_title)
        val joinField = hasSetTextAction() and hasText(text(R.string.join_link_placeholder))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(joinField)
        typeInto(joinField, "Be my Bumblebee? https://${BuildConfig.LINK_HOST}/i/$token")
        node(R.string.join_link_button).performClick()
        waitForText(text(R.string.accept_invite_title, friendName))
        compose.onNode(hasText(text(R.string.accept_invite_yes)) and hasAnyAncestor(isDialog())).performClick()
        waitForBackend("users/${friend.uid}/private/account") { it.strings("approverIds") == listOf(me) }
        // The welcome snackbar covers the bottom of whatever screen comes next, until it goes.
        waitForText(text(R.string.accept_invite_done, friendName))
        waitForSnackbarToGo(R.string.accept_invite_done, friendName)
        return me
    }

    /** From the circle screen, opens the garden of the person called [name]. */
    protected fun openGardenOf(name: String) {
        val garden = hasText(name, substring = true) and hasClickAction()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(garden)
        compose.onNode(garden).performClick()
    }
}
