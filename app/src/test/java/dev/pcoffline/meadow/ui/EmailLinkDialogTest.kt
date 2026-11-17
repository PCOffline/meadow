package dev.pcoffline.meadow.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.ui.auth.EmailLinkDialog
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opening an emailed sign-in link, against the Auth emulator: with the address the phone kept, or one typed in. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EmailLinkDialogTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
    }

    private fun text(id: Int) = app.getString(id)

    /** A new person's address, signed out, with the link Meadow just emailed to it. */
    private fun emailedLink(): Pair<String, String> {
        val person = person("Robin")
        onBackend { backend.auth.signOut() }
        val before = emailedLinks(person.email).toSet()
        onBackend { AppGraph.auth.sendSignInLink(person.email) }
        return person.email to emailedLinks(person.email).single { it !in before }
    }

    private fun showDialog(link: String) = compose.setContent {
        MeadowTheme { CompositionLocalProvider(LocalSnackbar provides remember { SnackbarHostState() }) { EmailLinkDialog(link) } }
    }

    /** Waits for [condition], turning the main thread, where Firebase and the screen answer. */
    private fun waitOnMain(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Waited ${WAIT_MS / 1000} s" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun signedInAs(email: String) = waitOnMain { backend.auth.currentUser?.email == email }

    @Test
    fun `with the address the phone kept, the link signs in at once`() {
        val (email, link) = emailedLink()
        showDialog(link)
        signedInAs(email)
        // The address is used once.
        assertEquals(null, AppGraph.auth.linkEmail)
    }

    @Test
    fun `without it, the link asks for the address`() {
        val (email, link) = emailedLink()
        AppGraph.store.pendingLinkEmail = null
        showDialog(link)
        compose.onNodeWithText(text(R.string.link_ask_email)).assertExists()
        compose.onNode(hasSetTextAction()).performTextInput(email)
        compose.onNodeWithText(text(R.string.link_continue)).performClick()
        signedInAs(email)
    }

    @Test
    fun `the wrong address says so and can be tried again`() {
        val (_, link) = emailedLink()
        AppGraph.store.pendingLinkEmail = null
        showDialog(link)
        compose.onNode(hasSetTextAction()).performTextInput("someone-else@example.com")
        compose.onNodeWithText(text(R.string.link_continue)).performClick()
        waitOnMain { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.link_continue)).assertExists()
    }
}
