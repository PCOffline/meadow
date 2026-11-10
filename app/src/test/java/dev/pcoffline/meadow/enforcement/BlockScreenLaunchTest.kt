package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
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
import java.time.ZonedDateTime

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

    @Test
    fun `asked again and again while a cover is under way, the block screen opens once`() {
        // Windows change several times a second while a video plays, and each change can ask.
        repeat(30) { service.block(APP) }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(APP), blockScreensOpened())
    }

    @Test
    fun `a resting app on screen is covered once, however often the windows change`() {
        AppGraph.store.setGrants(emptyList())
        AppGraph.store.setEmergency(null)
        AppGraph.store.setLimits(listOf(AppLimit(APP, "Reels", 30, 0, schedule = hoursAwayFromNow())))
        // Each change is looked at, to test the watcher by itself.
        service.windowScanMs = 0
        try {
            service.onForeground(APP)
            // Half a second of window changes, as a video feed with a floating player makes them.
            val main = shadowOf(Looper.getMainLooper())
            repeat(25) {
                service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
                main.idle()
                Thread.sleep(20)
            }
            main.idle()

            assertEquals(listOf(APP), blockScreensOpened())
        } finally {
            AppGraph.store.setLimits(emptyList())
        }
    }

    @Test
    fun `however often the windows change, they are listed about once a second`() {
        var listed = 0
        service.listWindows = { listed++; emptyList() }
        val main = shadowOf(Looper.getMainLooper())

        // Half a second of window changes, as a video feed with a floating player makes them.
        repeat(25) {
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
            main.idle()
            Thread.sleep(20)
        }
        assertEquals(1, listed)

        // The changes since are looked at once the second is up: the last one is never missed.
        waitUntil { listed == 2 }
        Thread.sleep(300)
        main.idle()
        assertEquals(2, listed)
    }

    @Test
    fun `an app opened again just after its block screen showed is covered again at once`() {
        service.block(APP)
        // The screen showed, and the person left it and opened the app again within a second,
        // before the first cover finished its work.
        BlockActivity.markShown(APP)
        service.block(APP)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(APP, APP), blockScreensOpened())
    }

    @Test
    fun `a cover that has ended lets the app be covered again`() {
        service.block(APP)
        BlockActivity.markShown(APP)
        waitUntil { service.scope.coroutineContext.job.children.none() }
        blockScreensOpened()
        opened.clear()

        // The app is opened again later, still resting.
        service.block(APP)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(APP), blockScreensOpened())
    }

    /** Hours far from now, so an app limited to them is resting. */
    private fun hoursAwayFromNow(): Schedule {
        val from = if (ZonedDateTime.now().hour < 12) 20 * 60 else 2 * 60
        return Schedule((1..7).associateWith { listOf(TimeWindow(from, from + 60)) })
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
