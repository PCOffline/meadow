package dev.pcoffline.meadow

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.enforcement.BlockActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * End to end, against the Firebase emulators: emergency unlocks from both places they're offered.
 * From Settings, for every app, then ended early; from the block screen, for the one resting app.
 * The unlock button stays off (and looks it) until the reason is long enough and the countdown
 * is over, and says what's missing. Each unlock is checked on screen and in the backend.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyJourneyTest : EndToEndTest() {
    private val reasonField = hasSetTextAction() and hasAnyAncestor(isDialog())

    private fun JSONObject.millis(field: String): Long? =
        optJSONObject(field)?.optString("timestampValue")?.takeIf { it.isNotEmpty() }?.let { Instant.parse(it).toEpochMilli() }

    /** Fills in the reason in two goes, checking the button stays off until it's long enough. */
    private fun giveAReason() {
        typeIntoDialog(reasonField, "Lost")
        inDialog(app.resources.getQuantityString(R.plurals.emergency_dialog_reason_needed, 6, 6)).assertExists()
        // The countdown is over by now (typing takes longer), so only the reason holds it back.
        waitForText(text(R.string.emergency_dialog_unlock))
        inDialog(text(R.string.emergency_dialog_unlock)).assertIsNotEnabled()
        compose.onNode(reasonField).performTextReplacement("Lost my way home")
        inDialog(text(R.string.emergency_dialog_unlock)).assertIsEnabled()
    }

    @Test
    fun anEmergencyUnlockLiftsEveryLimitAndEndsEarly() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Ari")
            val uid = FirebaseAuth.getInstance().currentUser!!.uid
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            node(R.string.settings_emergency_button).performScrollTo().performClick()
            inDialog(text(R.string.emergency_dialog_title)).assertExists()
            // Settings has no app in mind, so there's no choice between one app and all.
            assertEquals(0, compose.onAllNodes(hasText(text(R.string.emergency_dialog_all_apps))).fetchSemanticsNodes().size)
            giveAReason()
            inDialog(text(R.string.emergency_dialog_unlock)).performClick()

            val now = System.currentTimeMillis()
            waitForBackend("users/$uid/private/account") { (it.millis("emergencyUntil") ?: 0L) > now }
            assertEquals("", backendDoc("users/$uid/private/account")!!.string("emergencyPackage").orEmpty())
            waitForBackendDocs("users/$uid/emergencies") { docs -> docs.any { it.string("reason") == "Lost my way home" } }
            waitForText(text(R.string.emergency_banner_all, ""), substring = true)
            assertEquals(0, compose.onAllNodes(isDialog()).fetchSemanticsNodes().size)

            // Ending it early brings the limits back, here and in the backend. The unlock's message
            // sits over the bottom of the screen, where the banner is, so it goes first.
            val unlocked = app.getString(R.string.emergency_unlocked).substringBefore("%1")
            compose.waitUntil(15_000) { compose.onAllNodesWithText(unlocked, substring = true).fetchSemanticsNodes().isEmpty() }
            node(R.string.emergency_banner_end).performScrollTo().performClick()
            waitFor(R.string.emergency_ended)
            waitForBackend("users/$uid/private/account") { (it.millis("emergencyUntil") ?: Long.MAX_VALUE) <= System.currentTimeMillis() + CLOCK_SLACK_MS }
        }
    }

    @Test
    fun theBlockScreenUnlocksJustTheRestingApp() {
        val pkg = "dev.pcoffline.e2e.pebbles"
        val uid = ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Kai")
            // Allowed at no hour of any day, so it's resting right now.
            runBlocking { AppGraph.limits.save(AppLimit(pkg, "Pebbles", 0, 0, Schedule((1..7).associateWith { emptyList() }))) }
            FirebaseAuth.getInstance().currentUser!!.uid
        }
        compose.waitUntil(20_000) { AppGraph.engine.budget(pkg, forceRefresh = true)?.exhausted == true }

        val block = Intent(app, BlockActivity::class.java).putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
        ActivityScenario.launch<BlockActivity>(block).use {
            waitForText(text(R.string.rest_title, "Pebbles"))
            node(R.string.rest_emergency).performScrollTo().performClick()
            // Opened from a resting app, it's for that app unless all apps are chosen.
            inDialog(text(R.string.emergency_dialog_just_app, "Pebbles")).assertExists()
            inDialog(text(R.string.emergency_dialog_all_apps)).assertExists()
            giveAReason()
            inDialog(text(R.string.emergency_dialog_unlock)).performClick()

            val now = System.currentTimeMillis()
            waitForBackend("users/$uid/private/account") { (it.millis("emergencyUntil") ?: 0L) > now && it.string("emergencyPackage") == pkg }
            waitForBackendDocs("users/$uid/emergencies") { docs ->
                docs.any { it.string("packageName") == pkg && it.string("appName") == "Pebbles" }
            }
            // The block screen turns into a way back into the app.
            waitFor(R.string.rest_title_emergency)
            compose.onNode(hasText(text(R.string.rest_open, "Pebbles"))).assertExists()
            assertEquals(false, AppGraph.engine.budget(pkg, forceRefresh = true)!!.exhausted)
        }
    }

    private companion object {
        /** The phone's clock and the emulator host's can differ a little. */
        const val CLOCK_SLACK_MS = 5_000L
    }
}
