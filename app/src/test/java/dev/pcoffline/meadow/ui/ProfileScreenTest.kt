package dev.pcoffline.meadow.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The profile screen saving to the emulators: new words about me, and a photo removed. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ProfileScreenTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val snackbar = SnackbarHostState()

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
    }

    private fun text(id: Int) = app.getString(id)

    private fun waitOnMain(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Waited ${WAIT_MS / 1000} s" }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun show(profile: dev.pcoffline.meadow.data.UserProfile) = compose.setContent {
        MeadowTheme { CompositionLocalProvider(LocalSnackbar provides snackbar) { ProfileScreen(profile, onBack = {}) } }
    }

    @Test
    fun `new words about me are saved`() {
        val robin = person("Robin")
        show(DemoGarden.robin.copy(uid = robin.uid, displayName = "Robin", bio = ""))
        compose.onNode(hasSetTextAction() and hasText(text(R.string.profile_bio))).performScrollTo().performTextReplacement("Growing tomatoes")
        // The card above shows it as it's typed, as well as the field.
        assertEquals(2, compose.onAllNodes(hasText("Growing tomatoes")).fetchSemanticsNodes().size)
        compose.onNodeWithText(text(R.string.action_save)).performScrollTo().performClick()

        waitOnMain { snackbar.currentSnackbarData?.visuals?.message == text(R.string.profile_saved) }
        assertEquals("Growing tomatoes", onBackend { backend.user(robin.uid).get().await().getString("bio") })
    }

    @Test
    fun `a photo can be removed`() {
        val robin = person("Robin")
        // A photo where the rules expect one: the person's own, in the project's bucket.
        val photo = "https://firebasestorage.googleapis.com/v0/b/demo-meadow.appspot.com/o/avatars%2F${robin.uid}?alt=media&token=abc-123#v=1"
        onBackend { backend.user(robin.uid).update("photoUrl", photo).await() }
        show(DemoGarden.robin.copy(uid = robin.uid, displayName = "Robin", avatar = DemoGarden.robin.avatar.copy(photoUrl = photo)))
        compose.onNodeWithText(text(R.string.profile_photo_change)).assertExists()
        compose.onNodeWithText(text(R.string.profile_photo_remove)).performClick()

        // The profile forgets the photo first. (Deleting its file needs the Storage emulator, which these tests don't run.)
        onBackend { backend.user(robin.uid).snapshots().firstWhere { it.getString("photoUrl") == null } }
    }
}
