package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Messages from screens outside the tabs (onboarding, sign-in links) must reach the person. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SnackbarTest : ScreenTest() {

    @Test
    fun `a message from a screen outside the tabs shows`() {
        compose.setContent {
            MeadowTheme {
                SnackbarScope {
                    val action = rememberAction()
                    Button(onClick = { action.run(successMessage = "Reset link sent") {} }) { Text("Forgot password") }
                }
            }
        }
        compose.onNodeWithText("Forgot password").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reset link sent").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a screen with nowhere to show messages fails at once`() {
        val failure = runCatching {
            compose.setContent { MeadowTheme { rememberAction() } }
            compose.waitForIdle()
        }.exceptionOrNull()
        val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue("Expected a missing snackbar host to be reported, got $failure", messages.any { "No snackbar host" in it })
    }
}
