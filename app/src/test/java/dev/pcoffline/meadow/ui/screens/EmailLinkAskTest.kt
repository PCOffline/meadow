package dev.pcoffline.meadow.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.auth.EmailLinkDialog
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** An emailed sign-in link opened on a phone that doesn't know the address: it asks for it, and a link that fails says so. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class EmailLinkAskTest : ScreenTest() {
    @Before
    fun forgetTheAddress() {
        AppGraph.store.pendingLinkEmail = null
    }

    @Test
    fun `it asks for the address, and Continue waits for one`() {
        show { EmailLinkDialog("https://example.com/not-a-link") }
        node(R.string.link_title).assertExists()
        node(R.string.link_ask_email).assertExists()
        node(R.string.link_continue).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("robin@example.com")
        node(R.string.link_continue).assertIsEnabled()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `a link that doesn't sign in says so, and can be tried again`() {
        show { EmailLinkDialog("https://example.com/not-a-link") }
        compose.onNode(hasSetTextAction()).performTextInput("robin@example.com")
        node(R.string.link_continue).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        node(R.string.link_continue).assertIsEnabled()
    }
}
