package dev.pcoffline.meadow.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.snapshots
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.stringList
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.circle.FriendScreen
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A garden I look after, against the emulators: it shows, I can step down, and a garden that's gone says so. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class FriendScreenTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
    }

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun waitForText(value: String) =
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty() }

    private fun showFriend(me: Person, uid: String, onBack: () -> Unit = {}) = compose.setContent {
        MeadowTheme {
            CompositionLocalProvider(LocalSnackbar provides remember { SnackbarHostState() }) {
                FriendScreen(DemoGarden.robin.copy(uid = me.uid, approveeIds = listOf(uid)), uid, onBack = onBack, onOpenChat = {})
            }
        }
    }

    @Test
    fun `a Bumblebee sees the garden, and can step down`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        var backs = 0
        showFriend(bee, robin.uid, onBack = { backs++ })

        waitForText(text(R.string.friend_step_down, "Robin"))
        compose.onNodeWithText(text(R.string.friend_step_down, "Robin")).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.friend_step_down_title)) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText(text(R.string.friend_step_down_confirm)) and hasAnyAncestor(isDialog())).performClick()

        onBackend { backend.account(bee.uid).snapshots().firstWhere { robin.uid !in it.stringList("approveeIds") } }
        // Firebase answers on the main thread, which this turns while it waits.
        onBackend { kotlinx.coroutines.withTimeout(WAIT_MS) { while (backs == 0) kotlinx.coroutines.delay(20) } }
        compose.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun `a garden that isn't there says so`() {
        val bee = person("Bee")
        showFriend(bee, "no-such-garden")
        waitForText(text(R.string.friend_gone_title))
        assertEquals(0, compose.onAllNodes(hasText(text(R.string.friend_step_down_title))).fetchSemanticsNodes().size)
    }
}
