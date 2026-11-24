package dev.pcoffline.meadow.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.AppSync
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.SocialRepository
import dev.pcoffline.meadow.data.stringList
import dev.pcoffline.meadow.ui.legal.LegalDoc
import dev.pcoffline.meadow.ui.navigation.DeepLinks
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
        AppSync.startOnce()
    }

    @After
    fun restoreBackend() {
        AppGraph.backendReady = true
    }

    private fun showRoot() = compose.setContent { MeadowTheme { MeadowRoot(pending) } }

    /**
     * Shows the root once sync holds [person]'s garden. Firebase hands sign-ins and sign-outs to
     * the main thread, so one from making the people before could still be on its way; arriving
     * while the screen shows, it would replace the garden on screen as a phone never does.
     */
    private fun showRootFor(person: Person) {
        onBackend { AppGraph.sync.profile.firstWhere { (it as? ProfileState.Ready)?.profile?.uid == person.uid } }
        showRoot()
    }

    private fun text(@StringRes id: Int) = app.getString(id)

    private fun waitForText(@StringRes id: Int) = waitForText(text(id))

    private fun hasAnyText() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

    private fun waitForText(value: String, unmerged: Boolean = false) =
        waitFor("\"$value\"") { compose.onAllNodes(hasText(value), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for [condition]; if it never comes, the failure shows what was on screen. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MS, condition)
        } catch (e: ComposeTimeoutException) {
            // Every text on screen, dialogs and snackbars included, short enough to read in a CI log.
            val texts = compose.onAllNodes(hasAnyText(), useUnmergedTree = true).fetchSemanticsNodes()
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            throw AssertionError("Waited ${WAIT_MS / 1000} s for $what. On screen: ${texts.joinToString(" | ")}", e)
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
        onBackend { signUp(newEmail()) }
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
        showRootFor(robin)

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
        val robin = person("Robin")
        pending.value = DeepLinks.settings()
        showRootFor(robin)
        waitForText(R.string.action_settings)
        waitFor("the link to be used") { pending.value == null }
        assertNull(pending.value)
    }

    @Test
    fun `newer terms are offered once the account is read, and accepting them closes the offer`() {
        // Robin's garden was planted under the older terms, so no copy of the account ever shows the
        // current ones. (Sync only raises the phone's version: a late read of a newer version would
        // take the offer away.)
        val robin = person("Robin", termsVersion = LegalDoc.CURRENT_VERSION - 1)
        AppGraph.store.acceptedTermsVersion = LegalDoc.CURRENT_VERSION - 1
        showRootFor(robin)
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
        showRootFor(bee)

        val question = app.getString(R.string.accept_invite_title, "Robin")
        waitForText(question)
        compose.onNodeWithText(text(R.string.accept_invite_yes)).performClick()

        // As the phone journeys check it: the backend makes Bee Robin's Bumblebee, the welcome
        // shows, and the question closes.
        onBackend {
            waitUntilTrue("Bee to be Robin's Bumblebee") { account(bee.uid).stringList("approveeIds") == listOf(robin.uid) }
        }
        waitForText(app.getString(R.string.accept_invite_done, "Robin"))
        waitFor("the question to close") { compose.onAllNodes(hasText(question)).fetchSemanticsNodes().isEmpty() }
    }

    private suspend fun waitUntilTrue(what: String, condition: suspend () -> Boolean) {
        val until = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Waited ${WAIT_MS / 1000} s for $what" }
            delay(200)
        }
    }

    @Test
    fun `your own invite link says so, and OK closes it`() {
        val robin = person("Robin")
        val link = onBackend { SocialRepository(backend).createInviteLink() }
        AppGraph.store.setPendingInvite(link.token)
        showRootFor(robin)

        waitForText(R.string.accept_invite_own)
        compose.onNodeWithText(text(R.string.action_ok)).performClick()
        waitFor("the invite to close") { AppGraph.store.pendingInvite.value == null }
    }


    @Test
    fun `a link to troubleshooting opens it, and a test notification says the phone can't get them`() {
        val robin = person("Robin")
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        pending.value = DeepLinks.troubleshoot()
        showRootFor(robin)
        waitForText(R.string.trouble_title)
        compose.onNodeWithText(text(R.string.trouble_notify_test)).performScrollTo().performClick()
        // Robolectric's phone has no Firebase Cloud Messaging.
        waitForText(R.string.trouble_notify_no_phone)
    }

    @Test
    fun `with notifications off, troubleshooting opens their settings`() {
        val robin = person("Robin")
        shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(false)
        pending.value = DeepLinks.troubleshoot()
        showRootFor(robin)
        waitForText(R.string.trouble_title)
        compose.onNodeWithText(text(R.string.trouble_notify_settings)).performScrollTo().performClick()
        waitFor("the notification settings") {
            shadowOf(app).nextStartedActivity?.action == android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
        }
    }

    @Test
    fun `a link to ask for more time asks about that app`() {
        val robin = person("Robin")
        pending.value = DeepLinks.ask("com.example.pebbles")
        showRootFor(robin)
        waitForText(R.string.ask_title)
        waitFor("the link to be used") { pending.value == null }
    }

    @Test
    fun `links to a chat and to a garden I look after open them`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        pending.value = DeepLinks.friend(robin.uid)
        showRootFor(bee)
        waitForText(app.getString(R.string.friend_step_down, "Robin"))

        pending.value = DeepLinks.chat(ChatRepository.chatIdFor(robin.uid, bee.uid))
        waitFor("the chat") { compose.onAllNodes(hasText(text(R.string.chat_placeholder))).fetchSemanticsNodes().isNotEmpty() }
    }
}
