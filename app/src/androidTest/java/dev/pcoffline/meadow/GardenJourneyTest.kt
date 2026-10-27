package dev.pcoffline.meadow

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import dev.pcoffline.meadow.a11y.assertAccessible
import dev.pcoffline.meadow.ui.legal.LegalDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, against the Firebase emulators: a new gardener starts without an email, meets the
 * permission disclosures, edits their profile, changes a privacy choice, reads the privacy
 * notice and deletes their account. Each step is checked in the UI and in the backend.
 */
@RunWith(AndroidJUnit4::class)
class GardenJourneyTest : EndToEndTest() {
    @Test
    fun aNewGardenerPlantsTendsAndLeaves() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Intro and first steps: no email, but the terms must be accepted.
            waitFor(R.string.intro_next)
            node(R.string.intro_next).performClick()
            node(R.string.intro_next).performClick()
            node(R.string.intro_begin).performClick()
            typeInto(hasSetTextAction() and hasText(text(R.string.onboarding_name_label)), "Robin")
            node(R.string.legal_agree).performScrollTo().performClick()
            node(R.string.onboarding_plant).performScrollTo().performClick()

            // Setup: sensitive permissions are explained before Android's settings open.
            waitFor(R.string.setup_later)
            val uid = FirebaseAuth.getInstance().currentUser!!.uid
            val account = backendDoc("users/$uid/private/account")!!
            assertEquals(LegalDoc.CURRENT_VERSION.toString(), account.getJSONObject("acceptedTerms").getJSONObject("mapValue").getJSONObject("fields").getJSONObject("version").getString("integerValue"))
            compose.onAllNodesWithText(text(R.string.setup_step_allow)).onFirst().performScrollTo().performClick()
            node(R.string.disclosure_usage).assertExists()
            node(R.string.disclosure_not_now).performClick()
            node(R.string.setup_later).performScrollTo().performClick()

            // Garden, then the profile.
            waitForText("Robin", substring = true)
            compose.assertAccessible(compose.density)
            compose.onNodeWithContentDescription(text(R.string.profile_open)).performClick()
            typeInto(hasSetTextAction() and hasText(text(R.string.profile_bio)), "Growing calmer days")
            node(R.string.action_save).performScrollTo().performClick()
            waitFor(R.string.profile_saved)
            waitForSnackbarToGo(R.string.profile_saved)
            assertEquals("Growing calmer days", backendDoc("users/$uid")!!.getJSONObject("bio").getString("stringValue"))
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Privacy: a look at what Bumblebees see, then sharing screen time stops and leaves the page.
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            waitFor(R.string.settings_privacy_title)
            compose.assertAccessible(compose.density)
            node(R.string.settings_privacy_preview).performScrollTo().performClick()
            waitFor(R.string.privacy_preview_title)
            val notShared = text(R.string.friend_no_usage_shared, "Robin")
            eventually("today's screen time on the page") { showing(text(R.string.friend_today)) && !showing(notShared) }
            node(R.string.friend_limits).performScrollTo().assertExists()
            compose.assertAccessible(compose.density)
            val shareUsage = compose.onNode(isToggleable() and hasText(text(R.string.settings_share_usage)))
            shareUsage.performScrollTo().assertIsOn().performClick()
            eventually("the page without today's screen time") { showing(notShared) }
            // Privacy choices are owner-only, so they live on the private account.
            waitForBackend("users/$uid/private/account") { doc ->
                doc.getJSONObject("privacy").getJSONObject("mapValue").getJSONObject("fields")
                    .getJSONObject("shareUsage").getBoolean("booleanValue").not()
            }
            waitFor(R.string.settings_privacy_saved)
            waitForSnackbarToGo(R.string.settings_privacy_saved)
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            waitFor(R.string.settings_privacy_title)

            // The privacy notice is a tap away.
            node(R.string.legal_privacy).performScrollTo().performClick()
            waitForText("Who runs Meadow")
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Deleting the account removes it from the backend and returns to the start.
            node(R.string.settings_delete).performScrollTo().performClick()
            node(R.string.settings_delete_button).assertIsNotEnabled() // Not until DELETE is typed.
            compose.onNode(hasSetTextAction() and hasText(text(R.string.settings_delete_confirm_hint))).performTextInput("DELETE")
            node(R.string.settings_delete_button).performClick()
            waitFor(R.string.intro_have_garden)
            assertNull(backendDoc("users/$uid"))
            assertNull(backendDoc("users/$uid/private/account"))
        }
    }
}
