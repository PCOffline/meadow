package dev.pcoffline.meadow

import android.content.Intent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.enforcement.BlockActivity
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.navigation.DeepLinks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, from the side of the person who asks. Kai (on this phone) has Sam as a Bumblebee:
 * an app is resting, from its block screen Kai asks for 15 more minutes, Sam (played by the test)
 * says yes, and the block screen turns into a way back into the app, all without leaving it.
 * With two Bumblebees, Kai asks early from the garden, only the one picked is asked, and the
 * fewer minutes they give show on the app's flower.
 */
@RunWith(AndroidJUnit4::class)
class AskJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @After
    fun closePeople() {
        people.forEach { it.close() }
    }

    @Test
    fun askingFromTheBlockScreenAndHearingYesReopensTheApp() {
        val pkg = "dev.pcoffline.e2e.sketchbook"
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also(people::add)
        val token = kai.call("createInviteLink")!!["token"] as String
        sam.call("acceptInviteLink", mapOf("token" to token))
        // This phone signs in as Kai, who already has a garden and a Bumblebee.
        await(FirebaseAuth.getInstance().signInWithEmailAndPassword(kai.email, EmulatorFriend.PASSWORD))

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(R.string.setup_later)
            compose.onAllNodesWithText(text(R.string.setup_later)).onFirst().performScrollTo().performClick()
            waitForText("Kai", substring = true)
            // Allowed at no hour of any day, so it's resting right now.
            runBlocking { AppGraph.limits.save(AppLimit(pkg, "Sketchbook", 0, 0, Schedule((1..7).associateWith { emptyList() }))) }
        }
        compose.waitUntil(20_000) {
            AppGraph.engine.budget(pkg, forceRefresh = true)?.exhausted == true && AppGraph.store.approverCount.value == 1
        }

        val block = Intent(app, BlockActivity::class.java).putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
        ActivityScenario.launch<BlockActivity>(block).use {
            waitForText(text(R.string.rest_title, "Sketchbook"))
            node(R.string.rest_ask).performScrollTo().performClick()
            compose.onNode(hasText(app.resources.minutesText(15)) and hasAnyAncestor(isDialog())).performClick()
            typeIntoDialog(hasSetTextAction(), "Finishing a drawing for Mum")
            compose.onNode(hasText(text(R.string.action_send)) and hasAnyAncestor(isDialog())).performClick()

            // It reaches Sam, and the block screen says it's waiting.
            var requestId = ""
            compose.waitUntil(20_000) {
                requestId = await(sam.db.collection("users/${sam.uid}/inbox").get()).documents.firstOrNull()?.id.orEmpty()
                requestId.isNotEmpty()
            }
            waitForText(text(R.string.rest_pending, app.resources.minutesText(15)))
            val request = backendDoc("requests/$requestId")!!
            assertEquals("Finishing a drawing for Mum", request.string("message"))
            assertEquals(pkg, request.string("packageName"))

            // Sam says yes; the screen opens up without Kai doing anything.
            sam.call("decideRequest", mapOf("requestId" to requestId, "approve" to true))
            waitFor(R.string.rest_title_yes)
            compose.onNode(hasText(text(R.string.rest_open, "Sketchbook"))).assertExists()
            assertEquals(false, AppGraph.engine.budget(pkg, forceRefresh = true)!!.exhausted)
        }
    }

    private fun inbox(person: EmulatorFriend): List<String> =
        await(person.db.collection("users/${person.uid}/inbox").get()).documents.map { it.id }

    @Test
    fun askingEarlyGoesOnlyToTheBumblebeePickedAndCanGetLess() {
        val kai = EmulatorFriend.create(app, "Kai", "🌷").also(people::add)
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also(people::add)
        val lee = EmulatorFriend.create(app, "Lee", "🌼").also(people::add)
        for (bee in listOf(sam, lee)) bee.call("acceptInviteLink", mapOf("token" to kai.call("createInviteLink")!!["token"]))
        signInAs(kai)

        ActivityScenario.launch(MainActivity::class.java).use {
            intoTheGarden("Kai")
            runBlocking { AppGraph.limits.save(AppLimit(pebbles, "Pebbles", 30, 0)) }
            eventually("both Bumblebees counted") { AppGraph.store.approverCount.value == 2 }

            // From the app's flower, before it's resting: 30 minutes, asked of Lee only.
            waitFor(R.string.garden_tile_ask)
            node(R.string.garden_tile_ask).performScrollTo().performClick()
            waitFor(R.string.ask_title)
            compose.onNode(hasText(app.resources.minutesText(30)) and hasAnyAncestor(isDialog())).performClick()
            // The Bumblebees' names load after the dialog opens.
            eventually("Sam among the Bumblebees to ask") { runCatching { inDialog("Sam", substring = true).assertExists() }.isSuccess }
            inDialog("Sam", substring = true).performScrollTo().performClick()
            typeIntoDialog(hasSetTextAction(), "A long call with Mum")
            inDialog(text(R.string.action_send)).performClick()
            waitFor(R.string.ask_sent)
            var requestId = ""
            eventually("the request in Lee's inbox") {
                requestId = inbox(lee).firstOrNull().orEmpty()
                requestId.isNotEmpty()
            }
            assertEquals(listOf(lee.uid), backendDoc("requests/$requestId")!!.strings("approverIds"))
            assertEquals(emptyList<String>(), inbox(sam))

            // Lee gives 10 of the 30 minutes, and the flower shows them.
            lee.call("decideRequest", mapOf("requestId" to requestId, "approve" to true, "minutes" to 10))
            waitForText(text(R.string.garden_tile_extra, app.resources.minutesText(10)))

            // The Requests tab's button asks about the same limited apps.
            node(R.string.tab_requests).performClick()
            // A real tap where the button is on screen, as a finger would: Compose's injected
            // click on this floating button doesn't reach it on the emulator.
            val askButton = hasClickAction() and hasAnyDescendant(hasText(text(R.string.requests_ask_button)))
            val at = compose.onNode(askButton, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow.center
            shell("input tap ${at.x.toInt()} ${at.y.toInt()}")
            eventually("the ask dialog from the Requests button (tappable: ${tappables()})") { showing(text(R.string.ask_title)) }
            inDialog("Pebbles").assertExists()
            inDialog(text(R.string.action_cancel)).performClick()
        }

        // The "ask" on a running-low notification opens the same question for its app.
        val ask = Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, DeepLinks.ask(pebbles))
        ActivityScenario.launch<MainActivity>(ask).use {
            waitFor(R.string.ask_title)
            inDialog(text(R.string.ask_how_much)).assertExists()
            inDialog(text(R.string.action_cancel)).performClick()
        }
    }
}
