package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which protections are on, read from Android's own settings, and what each set of them allows. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ProtectionStateTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private val monitor by lazy { AppGraph.init(app).let { AppGraph.protection } }

    private fun services(value: String?) = Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)

    @Test
    fun `the guardian is on only when Android lists Meadow's own service`() {
        val ours = ComponentName(app, GuardianAccessibilityService::class.java).flattenToString()
        services(null)
        assertFalse(monitor.isAccessibilityEnabled())
        services("com.other/.Reader:not a component")
        assertFalse(monitor.isAccessibilityEnabled())
        services("com.other/.Reader:$ours")
        assertTrue(monitor.isAccessibilityEnabled())
    }

    @Test
    fun `limits need usage access, the guardian and the notification keeper, and strict mode needs no battery limits`() {
        val all = ProtectionState(usageAccess = true, accessibility = true, notifications = true, overlay = true, deviceAdmin = true, batteryUnrestricted = true)
        assertTrue(all.essentialsReady)
        assertTrue(all.strictReady)
        assertFalse(all.copy(usageAccess = false).essentialsReady)
        assertFalse(all.copy(accessibility = false).essentialsReady)
        assertFalse(all.copy(notificationListener = false).essentialsReady)
        assertFalse(all.copy(notificationListener = false).strictReady)
        assertFalse(all.copy(batteryUnrestricted = false).strictReady)
        assertEquals(7, all.asMap().size)
    }
}
