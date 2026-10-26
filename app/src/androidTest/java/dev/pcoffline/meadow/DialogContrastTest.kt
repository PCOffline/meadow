package dev.pcoffline.meadow

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.a11y.contrastProblems
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.legal.TermsUpdateDialog
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dialogs, on a phone: what answering a request asks, that the block screen's dialogs stay with
 * their app, and every dialog's text measured in every theme, by day and by night, from a
 * screenshot. Robolectric can't capture dialog windows (and
 * doesn't settle with these open), so this runs on the emulator; it needs no backend.
 */
@RunWith(AndroidJUnit4::class)
class DialogContrastTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val looks = GardenTheme.entries.flatMap { theme -> listOf(DarkMode.LIGHT, DarkMode.DARK).map { theme to it } }

    /** Shows [content] in each look; [open] opens the dialog when the content doesn't on its own. */
    private fun sweep(open: () -> Unit = {}, content: @Composable () -> Unit) {
        var look by mutableStateOf(looks.first())
        compose.setContent {
            // A fresh composition per look, so a dialog opened in one look doesn't carry over.
            key(look) {
                MeadowTheme(look.first, look.second) {
                    SnackbarScope { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
                }
            }
        }
        val problems = looks.flatMap { next ->
            look = next
            compose.waitForIdle()
            open()
            compose.waitForIdle()
            val where = "${next.first.name}/${if (next.second == DarkMode.DARK) "night" else "day"}"
            compose.contrastProblems().map { "$where: $it" }
        }
        assertTrue("Unreadable text:\n" + problems.joinToString("\n") { "- $it" }, problems.isEmpty())
    }

    private val request = LimitRequest(
        id = "r1", requesterId = "sam", requesterName = "Sam", type = RequestType.EXTRA_TIME,
        packageName = "com.instagram.android", appName = "Instagram", minutes = 30, dailyMinutes = 0, weeklyMinutes = 0,
        message = "Finishing a message to my sister", status = RequestStatus.PENDING, approverIds = listOf("bee"),
        decidedByName = null, decisionNote = null, dayKey = "2026-10-13",
        createdAtMillis = System.currentTimeMillis(), myDecision = null, usedMinutes = 29, allowedMinutes = 30,
    )

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)
    private fun inDialog(value: String) = compose.onNode(hasText(value) and hasAnyAncestor(isDialog()))

    @Test
    fun sayingYesAsksFirstAndOffersToGiveLess() {
        compose.setContent { MeadowTheme { SnackbarScope { RequestCard(request, myUid = "bee") } } }
        compose.onNodeWithText(text(R.string.request_say_yes)).performClick()
        inDialog(text(R.string.request_decide_yes_title, "Sam")).assertExists()
        inDialog(text(R.string.request_decide_less)).assertExists()
        inDialog(text(R.string.request_decide_note)).assertExists()
        inDialog(text(R.string.request_say_yes)).assertExists()
        // Back closes it without answering.
        inDialog(text(R.string.action_back)).performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.request_status_waiting)).assertExists()
    }

    @Test
    fun notTodayHasNoMinutesOnlyAKindWord() {
        compose.setContent { MeadowTheme { SnackbarScope { RequestCard(request, myUid = "bee") } } }
        compose.onNodeWithText(text(R.string.request_not_today)).performClick()
        inDialog(text(R.string.request_decide_no_title)).assertExists()
        inDialog(text(R.string.request_decide_note)).assertExists()
        compose.onAllNodesWithText(text(R.string.request_decide_less)).assertCountEquals(0)
    }

    @Test
    fun aDialogLeftOpenOnTheBlockScreenDoesNotCarryOverToTheNextApp() {
        // The block screen is reused when another app's time runs out (onNewIntent).
        var pkg by mutableStateOf("demo.tiktok")
        compose.setContent { MeadowTheme { RestContent(pkg, DemoGarden.rest) } }
        compose.onNodeWithText(text(R.string.rest_ask)).performClick()
        inDialog(text(R.string.ask_title)).assertExists()
        pkg = "demo.instagram"
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun updatedTerms()= sweep { TermsUpdateDialog(onAccept = {}) }

    @Test
    fun sayingYes() = sweep(open = { compose.onNodeWithText(app.getString(R.string.request_say_yes)).performClick() }) {
        RequestCard(request, myUid = "bee")
    }

    @Test
    fun sayingNotToday() = sweep(open = { compose.onNodeWithText(app.getString(R.string.request_not_today)).performClick() }) {
        RequestCard(request, myUid = "bee")
    }
}
