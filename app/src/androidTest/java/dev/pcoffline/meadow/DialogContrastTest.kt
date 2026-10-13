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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.a11y.contrastProblems
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
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
 * Dialogs in every theme, by day and by night, with their text measured from the pixels a phone
 * draws. Robolectric can't draw text in dialog windows, so this runs on the emulator; it needs no
 * backend.
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
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
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

    @Test
    fun updatedTerms() = sweep { TermsUpdateDialog(onAccept = {}) }

    @Test
    fun sayingYes() = sweep(open = { compose.onNodeWithText(app.getString(R.string.request_say_yes)).performClick() }) {
        RequestCard(request, myUid = "bee")
    }

    @Test
    fun sayingNotToday() = sweep(open = { compose.onNodeWithText(app.getString(R.string.request_not_today)).performClick() }) {
        RequestCard(request, myUid = "bee")
    }
}
