package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.auth.ProfileSetupScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Signed in, with no garden yet: a name and consent plant it. On the default screen: with a tall
 * phone's qualifiers, Robolectric never settles with the terms open.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ProfileSetupTest : ScreenTest() {
    private fun failed() = compose.waitUntil(5_000) { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a garden needs a name and consent, and the terms can be read first`() {
        show { ProfileSetupScreen() }
        assertAccessible()
        node(R.string.profile_setup_continue).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Robin")
        node(R.string.profile_setup_continue).assertIsNotEnabled()

        node(R.string.legal_terms).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.legal_terms)).fetchSemanticsNodes().size >= 3 }
        compose.onNodeWithContentDescription(text(R.string.action_back)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.legal_terms)).fetchSemanticsNodes().size == 1 }

        node(R.string.legal_agree).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        node(R.string.profile_setup_continue).assertIsEnabled()
        // These tests have no backend, so planting fails, and the screen says so.
        node(R.string.profile_setup_continue).performSemanticsAction(SemanticsActions.OnClick)
        failed()
    }

    @Test
    fun `another account can be used instead`() {
        show { ProfileSetupScreen() }
        node(R.string.profile_setup_other_account).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}
