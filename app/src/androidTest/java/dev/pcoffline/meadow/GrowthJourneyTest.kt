package dev.pcoffline.meadow

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end: the Growth tab asks for usage access until it has it, then shows where the time
 * went (here, a few seconds in Pebbles), by day and by week, can be shared, and leads from an app
 * to planting its limit. Protections and troubleshooting are a tap away from Settings.
 */
@RunWith(AndroidJUnit4::class)
class GrowthJourneyTest : EndToEndTest() {
    @After
    fun takeBackUsageAccess() {
        // Phone-wide settings outlast the app's data, so they're undone for the tests after.
        shell("appops set ${app.packageName} GET_USAGE_STATS default")
    }

    @Test
    fun growthNeedsUsageAccessThenShowsWhereTheTimeWent() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Robin")

            // Without usage access there's nothing to show; the way to turn it on is offered.
            tab(R.string.tab_growth).performClick()
            waitFor(R.string.stats_no_access_title)
            node(R.string.stats_no_access_action).performClick()
            waitFor(R.string.setup_title)
            compose.onAllNodesWithText(text(R.string.setup_later)).onFirst().performScrollTo().performClick()
            waitFor(R.string.stats_no_access_title)

            // With it, and a few seconds spent in Pebbles, Pebbles shows up.
            shell("appops set ${app.packageName} GET_USAGE_STATS allow")
            tab(R.string.tab_garden).performClick()
            openPebbles()
            Thread.sleep(3_000)
            shell("am start -W -n ${app.packageName}/${MainActivity::class.java.name}")
            // Out of sight for long enough that Growth reads everything afresh when it's back.
            Thread.sleep(6_000)
            tab(R.string.tab_growth).performClick()
            waitForText("Pebbles")
            node(R.string.stats_week).performClick()
            waitFor(R.string.stats_this_week)
            waitForText("Pebbles")

            // Screen time is for the person, not for posting.
            compose.onNodeWithContentDescription(text(R.string.share_title)).assertDoesNotExist()

            // An app leads to planting its limit.
            compose.onNode(hasText("Pebbles") and hasClickAction()).performScrollTo().performClick()
            waitFor(R.string.limit_editor_new)
        }
    }

    @Test
    fun protectionsAndTroubleshootingAreATapAwayFromSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Robin")
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            node(R.string.settings_protections_button).performScrollTo().performClick()
            waitFor(R.string.setup_title)
            node(R.string.setup_usage_title).assertExists()
            compose.onAllNodesWithText(text(R.string.setup_later)).onFirst().performScrollTo().performClick()
            node(R.string.trouble_button).performScrollTo().performClick()
            waitFor(R.string.trouble_title)
            waitFor(R.string.trouble_essentials)
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            waitFor(R.string.settings_protections_title)
        }
    }
}
