package dev.pcoffline.meadow

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.minutesText
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * End to end, with no Bumblebees yet: a limit is planted for an app found in the picker, with a
 * daily and a weekly allowance and allowed hours, then tended, shared and removed. Without
 * Bumblebees every change applies at once; each is checked on screen and in the backend.
 */
@RunWith(AndroidJUnit4::class)
class LimitJourneyTest : EndToEndTest() {
    @Before
    fun catchSharing() {
        Intents.init()
        // The share sheet is Android's, outside the app: it's checked as asked for, not opened.
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun releaseSharing() = Intents.release()

    private fun toggle(label: Int) = compose.onNode(isToggleable() and hasText(text(label))).performScrollTo().performClick()

    private fun chip(value: String) = compose.onNode(hasText(value) and hasClickAction()).performScrollTo().performClick()

    @Test
    fun aLimitIsPlantedTendedSharedAndRemoved() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Robin")
            val uid = FirebaseAuth.getInstance().currentUser!!.uid
            val limit = "users/$uid/limits/$pebbles"

            // From the empty garden to the picker; a search narrows the apps down.
            node(R.string.garden_empty_title).performScrollTo().performClick()
            waitFor(R.string.picker_title)
            // Pebbles may sit below the fold, so it's found by searching.
            typeInto(hasSetTextAction(), "pebb")
            waitForText("Pebbles")
            compose.onNode(hasSetTextAction()).performTextReplacement("zzz")
            eventually("Pebbles filtered out") { !showing("Pebbles") }
            compose.onNode(hasSetTextAction()).performTextReplacement("pebb")
            waitForText("Pebbles")
            compose.onNode(hasText("Pebbles") and hasClickAction()).performClick()

            // With every kind of limit off there's nothing to plant.
            waitFor(R.string.limit_editor_new)
            toggle(R.string.limit_editor_daily)
            eventually("planting turned off") { runCatching { node(R.string.limit_editor_plant).assertIsNotEnabled() }.isSuccess }
            toggle(R.string.limit_editor_daily)

            // 45 minutes a day, 5 hours a week, and allowed hours that include now.
            chip(app.resources.minutesText(45))
            toggle(R.string.limit_editor_weekly)
            chip(app.resources.minutesText(300))
            toggle(R.string.limit_editor_hours)
            chip(hoursPresetForNow())
            node(R.string.limit_editor_plant).performScrollTo().performClick()
            waitFor(R.string.limit_editor_planted)
            waitForBackend(limit) { it.int("dailyMinutes") == 45 && it.int("weeklyMinutes") == 300 && it.has("schedule") }
            // A new commitment can be shared straight from the note that says it's planted. With
            // nobody in the circle yet, the sheet offers only other apps.
            node(R.string.share_title).performClick()
            waitFor(R.string.share_commitment_title)
            node(R.string.share_commitment_in_meadow).assertDoesNotExist()
            node(R.string.share_commitment_other_apps).performClick()
            val weekly = app.resources.minutesText(300)
            intended(allOf(hasAction(Intent.ACTION_CHOOSER), hasExtra(Intent.EXTRA_INTENT, hasExtra(Intent.EXTRA_TEXT, containsString(weekly)))))
            waitForSnackbarToGo(R.string.limit_editor_planted)

            // Back in the garden, it's a flower to tap, and tapping it tends the limit.
            compose.onNode(hasText("Pebbles") and hasClickAction()).performScrollTo().performClick()
            waitFor(R.string.limit_editor_existing)
            toggle(R.string.limit_editor_weekly)
            node(R.string.action_save).performScrollTo().performClick()
            waitFor(R.string.limit_editor_updated)
            waitForBackend(limit) { it.int("weeklyMinutes") == 0 && it.int("dailyMinutes") == 45 }

            // The editor closes, and the note that says it's saved shares the commitment: its
            // words go to the share sheet.
            node(R.string.share_title).performClick()
            waitFor(R.string.share_commitment_other_apps)
            node(R.string.share_commitment_other_apps).performClick()
            // The limit as it is now: the weekly part shared earlier is gone.
            val now = allOf(containsString("on Pebbles with Meadow"), not(containsString(weekly)))
            intended(allOf(hasAction(Intent.ACTION_CHOOSER), hasExtra(Intent.EXTRA_INTENT, hasExtra(Intent.EXTRA_TEXT, now))))
            waitForSnackbarToGo(R.string.limit_editor_updated)
            compose.onNode(hasText("Pebbles") and hasClickAction()).performScrollTo().performClick()
            waitFor(R.string.limit_editor_existing)

            // With nobody to ask, removing is immediate.
            node(R.string.limit_editor_remove).performScrollTo().performClick()
            inDialog(text(R.string.limit_remove_title, "Pebbles")).assertExists()
            inDialog(text(R.string.action_remove)).performClick()
            waitFor(R.string.limit_editor_removed)
            waitUntilGone(limit)
            waitFor(R.string.garden_empty_title)
        }
    }

    /**
     * An allowed-hours preset that includes now, with half an hour to spare. Pebbles is the test
     * app itself: outside its allowed hours, Meadow would cover the test while it runs.
     */
    private fun hoursPresetForNow(): String {
        val minute = LocalTime.now().let { it.hour * 60 + it.minute }
        return when {
            minute in 10 * 60 until 16 * 60 - 30 -> text(R.string.schedule_window, clockText(10 * 60), clockText(16 * 60))
            minute < 9 * 60 -> text(R.string.schedule_preset_until, clockText(22 * 60))
            else -> text(R.string.schedule_preset_from, clockText(9 * 60))
        }
    }

    private fun waitUntilGone(path: String) {
        val end = System.currentTimeMillis() + 20_000
        while (backendDoc(path) != null) {
            if (System.currentTimeMillis() > end) throw AssertionError("$path was never removed. On screen: ${screenText()}")
            Thread.sleep(250)
        }
    }
}
