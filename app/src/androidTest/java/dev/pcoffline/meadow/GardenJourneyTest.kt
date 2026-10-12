package dev.pcoffline.meadow

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * End to end, against the Firebase emulators: a new gardener starts without an email, meets the
 * permission disclosures, edits their profile, changes a privacy choice, reads the privacy
 * notice and deletes their account. Each step is checked in the UI and in the backend.
 *
 * Run with the emulators started (see README) and:
 * ./gradlew connectedDebugAndroidTest -Pmeadow.emulatorHost=10.0.2.2
 */
@RunWith(AndroidJUnit4::class)
class GardenJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun needsEmulators() {
        assumeTrue("Build with -Pmeadow.emulatorHost to run end-to-end tests", BuildConfig.EMULATOR_HOST.isNotEmpty())
    }

    private fun text(@StringRes id: Int) = app.getString(id)
    private fun node(@StringRes id: Int): SemanticsNodeInteraction = compose.onNodeWithText(text(id))
    private fun waitFor(@StringRes id: Int, substring: Boolean = false) = waitForText(text(id), substring)
    private fun waitForText(value: String, substring: Boolean = false, timeout: Long = 60_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithText(value, substring).fetchSemanticsNodes().isNotEmpty() }

    /** Reads a document straight from the Firestore emulator, bypassing security rules. */
    private fun backendDoc(path: String): JSONObject? {
        val url = URL("http://${BuildConfig.EMULATOR_HOST}:8080/v1/projects/demo-meadow/databases/(default)/documents/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply { setRequestProperty("Authorization", "Bearer owner") }
        return try {
            if (connection.responseCode == 404) null else JSONObject(connection.inputStream.bufferedReader().readText()).getJSONObject("fields")
        } finally {
            connection.disconnect()
        }
    }

    private fun waitForBackend(timeout: Long = 20_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!runCatching(check).getOrDefault(false)) {
            if (System.currentTimeMillis() > end) throw AssertionError("Backend never reached the expected state")
            Thread.sleep(250)
        }
    }

    @Test
    fun aNewGardenerPlantsTendsAndLeaves() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Intro and first steps: no email, but the terms must be accepted.
            waitFor(R.string.intro_next)
            node(R.string.intro_next).performClick()
            node(R.string.intro_next).performClick()
            node(R.string.intro_begin).performClick()
            compose.onNode(hasSetTextAction() and hasText(text(R.string.onboarding_name_label))).performTextInput("Robin")
            node(R.string.legal_agree).performScrollTo().performClick()
            node(R.string.onboarding_plant).performScrollTo().performClick()

            // Setup: sensitive permissions are explained before Android's settings open.
            waitFor(R.string.setup_later)
            val uid = FirebaseAuth.getInstance().currentUser!!.uid
            val account = backendDoc("users/$uid/private/account")!!
            assertEquals("1", account.getJSONObject("acceptedTerms").getJSONObject("mapValue").getJSONObject("fields").getJSONObject("version").getString("integerValue"))
            compose.onAllNodesWithText(text(R.string.setup_step_allow)).onFirst().performScrollTo().performClick()
            node(R.string.disclosure_usage).assertExists()
            node(R.string.disclosure_not_now).performClick()
            node(R.string.setup_later).performScrollTo().performClick()

            // Garden, then the profile.
            waitForText("Robin", substring = true)
            compose.onNodeWithContentDescription(text(R.string.profile_open)).performClick()
            compose.onNode(hasSetTextAction() and hasText(text(R.string.profile_bio))).performTextInput("Growing calmer days")
            node(R.string.action_save).performScrollTo().performClick()
            waitFor(R.string.profile_saved)
            assertEquals("Growing calmer days", backendDoc("users/$uid")!!.getJSONObject("bio").getString("stringValue"))
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Privacy: stop sharing screen time.
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            val shareUsage = compose.onNode(isToggleable() and hasText(text(R.string.settings_share_usage)))
            shareUsage.performScrollTo().assertIsOn().performClick()
            waitForBackend {
                backendDoc("users/$uid")!!.getJSONObject("privacy").getJSONObject("mapValue").getJSONObject("fields")
                    .getJSONObject("shareUsage").getBoolean("booleanValue").not()
            }

            // The privacy notice is a tap away.
            node(R.string.legal_privacy).performScrollTo().performClick()
            waitForText("Who runs Meadow")
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Deleting the account removes it from the backend and returns to the start.
            node(R.string.settings_delete).performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText(text(R.string.settings_delete_confirm_hint))).performTextInput("DELETE")
            node(R.string.settings_delete_button).performClick()
            waitFor(R.string.intro_have_garden)
            assertNull(backendDoc("users/$uid"))
            assertNull(backendDoc("users/$uid/private/account"))
        }
    }
}
