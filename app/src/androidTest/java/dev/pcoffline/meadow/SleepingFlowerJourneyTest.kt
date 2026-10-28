package dev.pcoffline.meadow

import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.components.FlowerAsleep
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * End to end: an app outside the hours it's allowed sleeps in the garden, and says when it wakes;
 * allowed again, it's awake. (Once, it wilted as if its time were used up.)
 */
@RunWith(AndroidJUnit4::class)
class SleepingFlowerJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @After
    fun closePeople() {
        people.forEach { it.close() }
    }

    private fun flowers(asleep: Boolean) =
        compose.onAllNodes(SemanticsMatcher.expectValue(FlowerAsleep, asleep), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun anAppOutsideItsHoursSleepsAndWakesWhenAllowed() {
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        signInAs(kai)
        // An hour far from now, so the test can't cross into it.
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        val elsewhere = if (now < 12 * 60) TimeWindow(22 * 60, 23 * 60) else TimeWindow(60, 120)
        val resting = text(R.string.garden_tile_resting_until, "").trim()

        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            runBlocking { AppGraph.limits.save(AppLimit(pebbles, "Pebbles", 60, 0, schedule = Schedule.everyDay(elsewhere))) }
            waitForText("Pebbles")
            waitForText(resting, substring = true)
            eventually("Pebbles asleep") { flowers(asleep = true) == 1 }

            // With no Bumblebees, any hour can be allowed at once.
            runBlocking { AppGraph.limits.save(AppLimit(pebbles, "Pebbles", 60, 0)) }
            eventually("Pebbles awake") { flowers(asleep = true) == 0 && flowers(asleep = false) >= 1 }
            eventually("no longer resting") { !showing(resting, substring = true) }
        }
    }
}
