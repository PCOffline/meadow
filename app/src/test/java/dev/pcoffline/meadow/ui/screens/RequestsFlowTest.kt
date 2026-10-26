package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.LocalSnackbar
import dev.pcoffline.meadow.ui.requests.RequestsContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Requests tab's own button: asking for extra time from there. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RequestsFlowTest : ScreenTest() {
    private fun requests() = show { RequestsContent(DemoGarden.robin, incoming = emptyList(), outgoing = emptyList()) }

    @Test
    fun `the ask button has a name`() {
        requests()
        assertAccessible()
    }

    @Test
    fun `a snackbar doesn't cover the ask button`() {
        lateinit var snackbar: SnackbarHostState
        lateinit var scope: CoroutineScope
        show {
            snackbar = LocalSnackbar.current
            scope = rememberCoroutineScope()
            RequestsContent(DemoGarden.robin, incoming = emptyList(), outgoing = emptyList())
        }
        val button = compose.onNodeWithContentDescription(text(R.string.requests_ask_button))
        val resting = button.getBoundsInRoot().bottom
        // Nothing here draws the snackbar, so it stays up until the end.
        scope.launch { snackbar.showSnackbar("Asked") }
        compose.waitForIdle()
        assertTrue(button.getBoundsInRoot().bottom <= resting - 71.dp)
    }

    // Opening it is checked on a phone (AskJourneyTest): Robolectric never settles with a dialog open.
}
