package dev.pcoffline.meadow.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.AppSync
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.RequestRepository
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.ui.limits.LimitEditorScreen
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The limit editor with its own view model, against the emulators: a new limit is planted, and
 * with a Bumblebee a looser one is asked for, then waits, and can be withdrawn.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LimitEditorScreenTest : EmulatorTest() {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val requests by lazy { RequestRepository(backend) }

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
        AppSync.startOnce()
    }

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

    /** Waits for [condition], turning the main thread, where Firebase and the screen answer. */
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

    /** Shows the editor once sync holds [person]'s garden. */
    private fun showEditor(person: Person, onDone: () -> Unit = {}) {
        onBackend { AppGraph.sync.profile.firstWhere { (it as? ProfileState.Ready)?.profile?.uid == person.uid } }
        compose.setContent {
            MeadowTheme {
                CompositionLocalProvider(LocalSnackbar provides remember { SnackbarHostState() }) { LimitEditorScreen(APP, onDone = onDone) }
            }
        }
    }

    @Test
    fun `a new limit is planted in the garden`() {
        val robin = person("Robin")
        var done = 0
        showEditor(robin) { done++ }
        waitOnMain { shows(text(R.string.limit_editor_plant)) }
        compose.onNodeWithText(text(R.string.limit_editor_new)).assertExists()
        compose.onNodeWithText(text(R.string.limit_editor_plant)).performScrollTo().performClick()

        waitOnMain { done == 1 }
        val saved = onBackend { backend.user(robin.uid).collection("limits").document(APP).get().await() }
        assertEquals(30L, saved.getLong("dailyMinutes"))
    }

    @Test
    fun `with a Bumblebee, more time is asked for, then waits, and can be withdrawn`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        onBackend { LimitsRepository(backend, store, requests).save(AppLimit(APP, "Pebbles", 30, 0)) }
        showEditor(robin)
        waitOnMain { AppGraph.store.limits.value.isNotEmpty() && AppGraph.store.approverCount.value == 1 }
        waitOnMain { shows(text(R.string.limit_editor_existing)) }

        // An hour a day is looser than half an hour: it needs a yes.
        compose.onAllNodes(hasText(app.resources.minutesText(60)))[0].performScrollTo().performClick()
        waitOnMain { shows(text(R.string.limit_editor_ask)) }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("A long train ride")
        compose.onNodeWithText(text(R.string.limit_editor_ask)).performScrollTo().performClick()

        val asked = onBackend {
            requests.outgoing(robin.uid, fresh = true).firstWhere { list -> list.any { it.type == RequestType.CHANGE_LIMIT } }
                .single { it.type == RequestType.CHANGE_LIMIT }
        }
        assertEquals("A long train ride", asked.message)
        assertEquals(60, asked.dailyMinutes)

        // The editor shows the question waiting, and takes it back.
        waitOnMain { shows(text(R.string.limit_editor_pending_title)) }
        compose.onNodeWithText(text(R.string.request_withdraw)).performScrollTo().performClick()
        onBackend {
            requests.outgoing(robin.uid, fresh = true).firstWhere { list -> list.single { it.id == asked.id }.status == RequestStatus.CANCELLED }
        }
    }

    private companion object {
        const val APP = "com.example.pebbles"
    }
}
