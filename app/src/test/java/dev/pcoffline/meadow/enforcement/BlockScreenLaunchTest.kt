package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityService

/**
 * When an app's time is up the block screen opens straight over it. Going Home first made
 * Android hold the screen back, leaving people on the home screen with nothing shown.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BlockScreenLaunchTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<GuardianAccessibilityService>
    private lateinit var service: GuardianAccessibilityService

    private val shadow: ShadowAccessibilityService
        get() = Shadow.extract(service)

    /** Block screens started so far, by the app they're for. */
    private val opened = ArrayList<String>()

    @Before
    fun setUp() {
        AppGraph.init(app)
        controller = Robolectric.buildService(GuardianAccessibilityService::class.java).create()
        service = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(app).clearNextStartedActivities()
        // What an earlier test showed must not count here.
        BlockActivity.markShown("")
        BlockActivity.showing = null
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    @Test
    fun `the block screen opens over the app, without going Home`() {
        service.block(APP)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(APP), blockScreensOpened())
        assertEquals(emptyList<Int>(), shadow.globalActionsPerformed)
    }

    @Test
    fun `the block screen is opened again while Android holds it back`() {
        service.block(APP)

        waitUntil { blockScreensOpened().size >= 2 }

        assertEquals(emptyList<Int>(), shadow.globalActionsPerformed)
    }

    @Test
    fun `once the block screen has shown it isn't opened again`() {
        service.block(APP)
        BlockActivity.markShown(APP)

        waitUntil { service.scope.coroutineContext.job.children.none() }

        assertEquals(listOf(APP), blockScreensOpened())
    }

    @Test
    fun `a screen shown for another app doesn't count`() {
        service.block(APP)
        BlockActivity.markShown("com.example.other")

        waitUntil { blockScreensOpened().size >= 2 }
    }

    @Test
    fun `an app already behind the block screen isn't blocked again, so the screen doesn't flicker or go Home`() {
        BlockActivity.showing = APP
        service.block(APP)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), blockScreensOpened())
        assertEquals(emptyList<Int>(), shadow.globalActionsPerformed)
    }

    @Test
    fun `the block screen showing for another app doesn't stop this one being blocked`() {
        BlockActivity.showing = "com.example.other"
        service.block(APP)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(APP), blockScreensOpened())
    }

    /** Collects the block screens started since the last call. */
    private fun blockScreensOpened(): List<String> {
        while (true) {
            val intent = shadowOf(app).nextStartedActivity ?: break
            if (intent.component?.className == BlockActivity::class.java.name) {
                opened += intent.getStringExtra(BlockActivity.EXTRA_PACKAGE).orEmpty()
            }
        }
        return opened
    }

    /** Runs the main thread until [done], failing after a while. */
    private fun waitUntil(done: () -> Boolean) {
        val main = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            main.idle()
            Thread.sleep(10)
        }
        main.idle()
    }

    private companion object {
        const val APP = "com.example.reels"
        const val TIMEOUT_MS = 15_000L
    }
}
