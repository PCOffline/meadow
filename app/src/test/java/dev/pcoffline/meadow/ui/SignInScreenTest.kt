package dev.pcoffline.meadow.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Signing in again on the welcome screen, against the Auth emulator: with a password (a wrong
 * one says so), or by an emailed link, and a forgotten password's reset.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SignInScreenTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
    }

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun waitOnMain(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Waited ${WAIT_MS / 1000} s" }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun shows(value: String) = compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()

    private val snackbar = SnackbarHostState()

    /** The welcome screen's sign-in step, signed out, with [email] typed in. */
    private fun signInWith(email: String) {
        onBackend { backend.auth.signOut() }
        compose.setContent {
            MeadowTheme { CompositionLocalProvider(LocalSnackbar provides snackbar) { AuthScreen() } }
        }
        compose.onNodeWithText(text(R.string.intro_have_garden)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.field_email))).performTextInput(email)
    }

    private fun password() = compose.onNode(hasSetTextAction() and hasText(text(R.string.field_password)))

    @Test
    fun `a wrong password says so, and the right one signs in`() {
        val robin = person("Robin")
        signInWith(robin.email)
        compose.onNodeWithText(text(R.string.auth_password_instead)).performClick()
        password().performTextInput("not the password")
        compose.onNodeWithText(text(R.string.sign_in_button)).performClick()
        waitOnMain { shows(text(R.string.error_auth_wrong_credentials)) }

        password().performTextReplacement(PASSWORD)
        // Done on the keyboard signs in too.
        password().performImeAction()
        waitOnMain { backend.auth.currentUser?.uid == robin.uid }
    }

    @Test
    fun `an emailed link is sent to the address, from the keyboard too`() {
        val robin = person("Robin")
        signInWith(robin.email)
        val before = emailedLinks(robin.email).size
        compose.onNode(hasSetTextAction() and hasText(robin.email)).performImeAction()
        waitOnMain { shows(text(R.string.auth_email_link_sent, robin.email)) }
        assertEquals(robin.email, AppGraph.auth.linkEmail)
        waitOnMain { emailedLinks(robin.email).size == before + 1 }
    }

    @Test
    fun `a forgotten password's reset is sent`() {
        val robin = person("Robin")
        signInWith(robin.email)
        compose.onNodeWithText(text(R.string.auth_password_instead)).performClick()
        compose.onNodeWithText(text(R.string.sign_in_forgot)).performClick()
        waitOnMain { snackbar.currentSnackbarData?.visuals?.message == text(R.string.sign_in_reset_sent) }
    }
}
