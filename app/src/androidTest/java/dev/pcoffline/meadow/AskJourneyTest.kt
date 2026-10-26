package dev.pcoffline.meadow

import android.content.Intent
import androidx.compose.ui.test.hasAnyAncestor
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, from the side of the person who asks: Kai (on this phone) has Sam as a Bumblebee.
 * An app is resting; from its block screen Kai asks for 15 more minutes, Sam (played by the test)
 * says yes, and the block screen turns into a way back into the app, all without leaving it.
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
}
