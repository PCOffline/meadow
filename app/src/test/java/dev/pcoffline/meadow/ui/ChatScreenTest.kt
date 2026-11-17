package dev.pcoffline.meadow.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.snapshots
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.stringList
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatScreen
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A chat on screen against the emulators: what is sent arrives, and a Bumblebee can be removed and kept. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ChatScreenTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
    }

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun waitForText(value: String) =
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText(value, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `words sent in a chat arrive, and a Bumblebee can be removed, then kept`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        val chatId = ChatRepository.chatIdFor(robin.uid, bee.uid)
        var profile by mutableStateOf(DemoGarden.robin.copy(uid = robin.uid, displayName = "Robin", approverIds = listOf(bee.uid)))
        compose.setContent {
            MeadowTheme { CompositionLocalProvider(LocalSnackbar provides remember { SnackbarHostState() }) { ChatScreen(profile, chatId, onBack = {}) } }
        }

        // The chat loads with the other person's name.
        waitForText("Bee")
        compose.onNode(hasSetTextAction()).performTextInput("Hello Bee")
        compose.onNodeWithContentDescription(text(R.string.action_send)).performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText("Hello Bee")).fetchSemanticsNodes().size == 1 }

        compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        compose.onNodeWithText(text(R.string.chat_remove_bee)).performClick()
        compose.onNode(hasText(text(R.string.circle_remove_title, "Bee")) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText(text(R.string.action_remove)) and hasAnyAncestor(isDialog())).performClick()
        // The backend starts the day's wait before the Bumblebee leaves.
        onBackend {
            backend.account(robin.uid).snapshots().firstWhere { it.get("pendingRemovals.${bee.uid}") != null }
        }

        compose.runOnIdle { profile = profile.copy(pendingRemovals = mapOf(bee.uid to System.currentTimeMillis())) }
        compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        compose.onNodeWithText(text(R.string.chat_keep_bee)).performClick()
        onBackend {
            backend.account(robin.uid).snapshots().firstWhere { it.get("pendingRemovals.${bee.uid}") == null }
        }
        onBackend { assert(bee.uid in backend.account(robin.uid).snapshots().firstWhere { true }.stringList("approverIds")) }
    }
}
