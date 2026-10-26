package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.requests.AskForTimeDialog
import dev.pcoffline.meadow.ui.requests.RequestsContent
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
    fun `asking about any app opens`() {
        show { AskForTimeDialog(packageName = null, onDismiss = {}) }
        node(R.string.ask_title).assertExists()
    }

    @Test
    fun `asking about one app opens`() {
        show { AskForTimeDialog(packageName = "com.instagram.android", onDismiss = {}) }
        node(R.string.ask_title).assertExists()
    }

    @Test
    fun `the ask button opens the question`() {
        requests()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text(R.string.requests_ask_button))), useUnmergedTree = true).performClick()
        node(R.string.ask_title).assertExists()
    }
}
