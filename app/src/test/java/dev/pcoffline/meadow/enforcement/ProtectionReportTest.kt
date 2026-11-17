package dev.pcoffline.meadow.enforcement

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.snapshots
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.UsageShareRepository
import dev.pcoffline.meadow.usage.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Against the emulators: a protection switched off while limits and Bumblebees are in place
 * tells them, once. Switching one on, or having no one to tell, says nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ProtectionReportTest : EmulatorTest() {
    private val on = ProtectionState(
        usageAccess = true, accessibility = true, notifications = true, overlay = true, deviceAdmin = true, batteryUnrestricted = true,
    )

    private fun monitor(backendReady: Boolean = true) =
        ProtectionMonitor(app, UsageTracker(app), store, UsageShareRepository(backend), CoroutineScope(SupervisorJob() + Dispatchers.IO), backendReady)

    /** When the backend last told Bumblebees about [uid], or null if never. */
    private fun lastAlert(uid: String) = onBackend { account(uid).getTimestamp("lastAlertAt") }

    @Test
    fun `a protection switched off with limits and Bumblebees tells them`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        store.setLimits(listOf(AppLimit("com.pebbles", "Pebbles", 30, 0)))
        store.setApproverCount(1)
        val monitor = monitor()

        // The first look only remembers what's on.
        monitor.check(on)
        monitor.check(on.copy(overlay = false))
        assertNull("only some protections are reported", lastAlert(robin.uid))

        monitor.check(on.copy(overlay = false, accessibility = false))
        onBackend { backend.account(robin.uid).snapshots().firstWhere { it.getTimestamp("lastAlertAt") != null } }
    }

    @Test
    fun `nothing is said without Bumblebees, without limits, or switching a protection on`() {
        val robin = person("Robin")
        val monitor = monitor()
        monitor.check(on.copy(accessibility = false))
        monitor.check(on)
        // No limits and no Bumblebees.
        monitor.check(on.copy(usageAccess = false))
        store.setLimits(listOf(AppLimit("com.pebbles", "Pebbles", 30, 0)))
        monitor.check(on)
        monitor.check(on.copy(usageAccess = false))
        onBackend { delay(1_500) }
        assertNull(lastAlert(robin.uid))
        assertEquals("usageAccess=0", store.lastReportedProtection?.split(',')?.first())
    }

    @Test
    fun `without a backend nothing is remembered or sent`() {
        person("Robin")
        monitor(backendReady = false).check(on)
        assertNull(store.lastReportedProtection)
    }
}
