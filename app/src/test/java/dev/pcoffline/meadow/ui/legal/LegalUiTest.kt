package dev.pcoffline.meadow.ui.legal

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LegalUiTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private fun text(id: Int) = app.getString(id)

    @Test
    fun `the consent box toggles from its label and opens each document`() {
        val opened = mutableListOf<LegalDoc>()
        compose.setContent {
            MeadowTheme {
                var agreed by remember { mutableStateOf(false) }
                ConsentCheckbox(agreed, onChange = { agreed = it }, onOpen = { opened += it })
            }
        }
        val box = compose.onNode(isToggleable())
        box.assertIsOff()
        compose.onNodeWithText(text(R.string.legal_agree)).performClick()
        box.assertIsOn()
        compose.onNodeWithText(text(R.string.legal_terms)).performClick()
        compose.onNodeWithText(text(R.string.legal_privacy)).performClick()
        assertEquals(listOf(LegalDoc.TERMS, LegalDoc.PRIVACY), opened)
    }

    @Test
    fun `the shipped privacy notice loads and renders`() {
        AppGraph.init(app)
        compose.setContent { MeadowTheme { LegalScreen(LegalDoc.PRIVACY, onBack = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text(R.string.legal_privacy)) >= 2 }
        // The title bar and the document's own heading, then its sections.
        compose.onNodeWithText("Who runs Meadow").assertExists()
        compose.onNodeWithText("Who can see what").assertExists()
    }

    @Test
    fun `changed terms can only be closed by agreeing`() {
        var accepted = 0
        compose.setContent { MeadowTheme { TermsUpdateDialog(onAccept = { accepted++ }) } }
        compose.onNodeWithText(text(R.string.legal_update_title)).assertExists()
        compose.onNodeWithText(text(R.string.legal_update_accept)).performClick()
        assertEquals(1, accepted)
    }

    @Test
    fun `markdown headings, bullets and bold render as text`() {
        compose.setContent { MeadowTheme { MarkdownText("# Hello\n\nSome **bold** words.\n\n- A bullet") } }
        compose.onNodeWithText("Hello").assertExists()
        compose.onNodeWithText("Some bold words.").assertExists()
        compose.onNodeWithText("A bullet").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(value: String) =
        onAllNodes(androidx.compose.ui.test.hasText(value)).fetchSemanticsNodes().size
}
