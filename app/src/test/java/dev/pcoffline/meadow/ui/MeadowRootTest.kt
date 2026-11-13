package dev.pcoffline.meadow.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.SocialRepository
import dev.pcoffline.meadow.ui.legal.LegalDoc
import dev.pcoffline.meadow.ui.navigation.DeepLinks
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app's root against the emulators: what it shows signed out, without a garden and with one,
 * the tabs and their badge, links from notifications, and the terms update.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class MeadowRootTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val pending = MutableStateFlow<String?>(null)

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
        startSyncOnce()
    }

    @After
    fun restoreBackend() {
        AppGraph.backendReady = true
    }

    private fun showRoot() = compose.setContent { MeadowTheme { MeadowRoot(pending) } }

    private fun text(@StringRes id: Int) = app.getString(id)

    private fun waitForText(@StringRes id: Int) = waitForText(text(id))

    private fun waitForText(value: String, unmerged: Boolean = false) =
        waitFor("\"$value\"") { compose.onAllNodes(hasText(value), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for [condition]; if it never comes, the failure shows what was on screen. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MS, condition)
        } catch (e: ComposeTimeoutException) {
            val roots = compose.onAllNodes(isRoot())
            val screen = roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() }
            throw AssertionError("Waited ${WAIT_MS / 1000} s for $what. On screen:\n$screen", e)
        }
    }

    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `without a backend, it says Meadow can't start`() {
        AppGraph.backendReady = false
        showRoot()
        compose.onNodeWithText(text(R.string.backend_missing_title)).assertExists()
    }

    @Test
    fun `signed out, it opens on the introduction`() {
        showRoot()
        waitForText(R.string.intro_plant_title)
    }

    @Test
    fun `an account without a garden is asked to plant one`() {
        onBackend { AppGraph.auth.signUp(newEmail(), PASSWORD) }
        showRoot()
        waitForText(R.string.profile_setup_title)
    }

    @Test
    fun `with a garden, setup comes first, then the tabs, with a badge for an invite`() {
        val robin = person("Robin")
        person("Bee")
        // Bee asks Robin to be their Bumblebee: one invite for Robin to answer.
        onBackend { SocialRepository(backend).invite(robin.friendCode) }
        signInAs(robin)
        showRoot()

        // The phone isn't set up for Meadow, so setup opens over the garden, without the tabs.
        waitForText(R.string.setup_title)
        back()
        waitForText(R.string.tab_people)

        compose.onNodeWithText(text(R.string.tab_people)).performClick()
        waitForText(R.string.people_title)
        // The People tab counts the invite. The tab's label names it, so the count is only in the unmerged tree.
        waitForText("1", unmerged = true)
    }

    @Test
    fun `a link from a notification opens its screen once`() {
        person("Robin")
        pending.value = DeepLinks.settings()
        showRoot()
        waitForText(R.string.action_settings)
        waitFor("the link to be used") { pending.value == null }
        assertNull(pending.value)
    }

    @Test
    fun `newer terms are offered once the account is read, and accepting them closes the offer`() {
        val robin = person("Robin")
        onBackend {
            backend.account(robin.uid).update(
                "acceptedTerms",
                mapOf("version" to LegalDoc.CURRENT_VERSION - 1, "at" to FieldValue.serverTimestamp()),
            ).await()
        }
        // This phone accepted the older terms too. (Sync only ever raises the phone's version, and it
        // already saw the current one when the garden was planted.)
        AppGraph.store.acceptedTermsVersion = LegalDoc.CURRENT_VERSION - 1
        showRoot()
        waitForText(R.string.legal_update_title)
        compose.onNodeWithText(text(R.string.legal_update_accept)).performClick()
        waitFor("the offer to close") { compose.onAllNodes(hasText(text(R.string.legal_update_title))).fetchSemanticsNodes().isEmpty() }
        waitFor("the current terms to be stored") { AppGraph.store.acceptedTermsVersion == LegalDoc.CURRENT_VERSION }
    }

    @Test
    fun `an invite link asks whose Bumblebee to be, and yes makes one`() {
        val robin = person("Robin")
        val link = onBackend { SocialRepository(backend).createInviteLink() }
        val bee = person("Bee")
        AppGraph.store.setPendingInvite(link.token)
        showRoot()

        waitForText(app.getString(R.string.accept_invite_title, "Robin"))
        compose.onNodeWithText(text(R.string.accept_invite_yes)).performClick()

        waitFor("the invite to be accepted") { AppGraph.store.pendingInvite.value == null }
        onBackend {
            @Suppress("UNCHECKED_CAST")
            val approvees = account(bee.uid).get("approveeIds") as List<String>
            assertEquals(listOf(robin.uid), approvees)
        }
    }

    @Test
    fun `your own invite link says so, and OK closes it`() {
        person("Robin")
        val link = onBackend { SocialRepository(backend).createInviteLink() }
        AppGraph.store.setPendingInvite(link.token)
        showRoot()

        waitForText(R.string.accept_invite_own)
        compose.onNodeWithText(text(R.string.action_ok)).performClick()
        waitFor("the invite to close") { AppGraph.store.pendingInvite.value == null }
    }

    private companion object {
        private var started = false

        /** The app starts sync once per process; so do these tests, which share one app graph. */
        @Synchronized
        fun startSyncOnce() {
            if (started) return
            AppGraph.sync.start()
            started = true
        }
    }
}
