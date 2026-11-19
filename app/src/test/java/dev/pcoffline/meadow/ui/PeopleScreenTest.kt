package dev.pcoffline.meadow.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppSync
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.SocialRepository
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.data.stringList
import dev.pcoffline.meadow.ui.circle.PeopleScreen
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The People tab with its own view model, against the emulators: invites to answer or take back.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PeopleScreenTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val social by lazy { SocialRepository(backend) }

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
        AppSync.startOnce()
    }

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun waitOnMain(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Waited ${WAIT_MS / 1000} s" }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun shows(value: String) = compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()

    /** Shows the People tab once sync holds [person]'s garden, and returns it as sync has it. */
    private fun showPeople(person: Person): UserProfile {
        val profile = onBackend {
            (AppGraph.sync.profile.firstWhere { (it as? ProfileState.Ready)?.profile?.uid == person.uid } as ProfileState.Ready).profile
        }
        compose.setContent {
            MeadowTheme {
                CompositionLocalProvider(LocalSnackbar provides remember { SnackbarHostState() }) {
                    PeopleScreen(profile, onOpenChat = {}, onOpenBeeStats = {})
                }
            }
        }
        return profile
    }

    @Test
    fun `an invite can be accepted, and one sent can be taken back`() {
        val robin = person("Robin")
        val ivy = person("Ivy")
        val sam = person("Sam")
        // Sam asks Robin to look after Sam's garden, and Robin asks Ivy.
        onBackend { social.invite(robin.friendCode) }
        signInAs(robin)
        onBackend { social.invite(ivy.friendCode) }
        showPeople(robin)

        waitOnMain { shows(text(R.string.circle_invite_title, "Sam")) }
        compose.onNodeWithText(text(R.string.circle_invite_accept)).performScrollTo().performClick()
        onBackend { social.incomingInvites(robin.uid).firstWhere { it.isEmpty() } }

        // Robin's own invite to Ivy waits, and can be taken back.
        waitOnMain { shows("Ivy") }
        compose.onNode(hasText(text(R.string.action_cancel))).performScrollTo().performClick()
        onBackend { social.outgoingInvites(robin.uid).firstWhere { it.isEmpty() } }
        assertEquals(listOf(sam.uid), onBackend { account(robin.uid).stringList("approveeIds") })
    }

    @Test
    fun `an invite can be declined`() {
        val robin = person("Robin")
        person("Sam")
        onBackend { social.invite(robin.friendCode) }
        signInAs(robin)
        showPeople(robin)

        waitOnMain { shows(text(R.string.circle_invite_decline)) }
        compose.onNodeWithText(text(R.string.circle_invite_decline)).performScrollTo().performClick()
        onBackend { social.incomingInvites(robin.uid).firstWhere { it.isEmpty() } }
    }

}
