package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Emergency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * The rest screen reads the whole day's usage log afresh each time it starts showing. Each later
 * change (an emergency unlock, a profile update, the 15-second tick) uses the engine's last
 * reading, so an unlock does not start several full reads in a few seconds (#68).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class RestScreenReadsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `only the first budget forces a fresh read of the usage log`() {
        AppGraph.init(app)
        val forced = Collections.synchronizedList(mutableListOf<Boolean>())
        val model = RestViewModel("demo.tiktok") { _, force -> forced += force; null }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { model.state.collect { } }
        try {
            fun settle(atLeast: Int) {
                val until = System.currentTimeMillis() + 5_000
                while (forced.size < atLeast && System.currentTimeMillis() < until) {
                    shadowOf(Looper.getMainLooper()).idle()
                    Thread.sleep(20)
                }
            }
            settle(1)
            // An emergency unlock starts, and is then ended: each changes what the screen shows.
            AppGraph.store.setEmergency(Emergency(System.currentTimeMillis() + 15 * 60_000L, "demo.tiktok"))
            settle(2)
            AppGraph.store.setEmergency(Emergency(System.currentTimeMillis(), null))
            settle(3)
            assertTrue("read again after each change: $forced", forced.size >= 3)
            assertEquals("forced reads: $forced", 1, forced.count { it })
            assertTrue("the first read is forced", forced.first())
        } finally {
            scope.cancel()
            AppGraph.store.setEmergency(null)
        }
    }

    @Test
    fun `shown again for the same app, the screen reads afresh first`() {
        AppGraph.init(app)
        val forced = Collections.synchronizedList(mutableListOf<Boolean>())
        val model = RestViewModel("demo.tiktok") { _, force -> forced += force; null }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { model.state.collect { } }
        fun settle(atLeast: Int) {
            val until = System.currentTimeMillis() + 5_000
            while (forced.size < atLeast && System.currentTimeMillis() < until) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
        }
        try {
            settle(1)
            AppGraph.store.setEmergency(Emergency(System.currentTimeMillis() + 15 * 60_000L, "demo.tiktok"))
            settle(2)
            // The rest screen comes back for the same app, and something changes.
            model.shownAgain()
            AppGraph.store.setEmergency(Emergency(System.currentTimeMillis(), null))
            settle(3)
            assertEquals("forced reads: $forced", listOf(true, false, true), forced.take(3))
        } finally {
            scope.cancel()
            AppGraph.store.setEmergency(null)
        }
    }

}
