package dev.pcoffline.meadow.ui

import android.os.Looper
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.ui.auth.EmailLinkDialog
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opening an emailed sign-in link, against the Auth emulator, with the address the phone kept. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EmailLinkDialogTest : EmulatorTest() {
    @get:Rule val compose = createComposeRule()

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
        waitOnMain { AppGraph.auth.linkEmail == null }
    }
}
