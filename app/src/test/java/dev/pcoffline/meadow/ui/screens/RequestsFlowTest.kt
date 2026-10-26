package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.LocalSnackbarClearance
import dev.pcoffline.meadow.ui.SnackbarClearance
import dev.pcoffline.meadow.ui.requests.RequestsContent
import org.junit.Assert.assertEquals
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
    fun `snackbars sit above the ask button while it's there`() {
        val clearance = SnackbarClearance()
        var here by mutableStateOf(true)
        show {
            CompositionLocalProvider(LocalSnackbarClearance provides clearance) {
                if (here) RequestsContent(DemoGarden.robin, incoming = emptyList(), outgoing = emptyList())
            }
        }
        val button = compose.onNodeWithContentDescription(text(R.string.requests_ask_button)).getBoundsInRoot()
        // The button's height and the gap under it: the snackbar's own margin then keeps them apart.
        assertEquals((button.height + 20.dp).value, clearance.height.value, 0.5f)

        here = false
        compose.waitForIdle()
        assertEquals(0f, clearance.height.value)
    }

    // Opening it is checked on a phone (AskJourneyTest): Robolectric never settles with a dialog open.
}
