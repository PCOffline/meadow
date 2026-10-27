package dev.pcoffline.meadow.data

import android.app.Application
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

/** Whether the phone is online follows the network in use, whatever order Android reports changes in. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ConnectivityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val cm = app.getSystemService(ConnectivityManager::class.java)

    @After
    fun backOnline() = Connectivity.set(true)

    private fun reportLost() = shadowOf(cm).networkCallbacks.forEach { it.onLost(ShadowNetwork.newInstance(OLD_NETWORK)) }

    @Test
    fun `losing a network that's no longer the one in use doesn't count as offline`() {
        Connectivity.watch(app)
        assertTrue(Connectivity.online.value)
        // Wi-Fi to mobile data: the old network's loss can come after the new one is up.
        reportLost()
        assertTrue(Connectivity.online.value)
    }

    @Test
    fun `with no network in use, the phone is offline, and back online when one comes`() {
        Connectivity.watch(app)
        shadowOf(cm).setDefaultNetworkActive(false)
        reportLost()
        assertFalse(Connectivity.online.value)
        shadowOf(cm).setDefaultNetworkActive(true)
        shadowOf(cm).networkCallbacks.forEach { it.onAvailable(cm.activeNetwork!!) }
        assertTrue(Connectivity.online.value)
    }

    private companion object {
        const val OLD_NETWORK = 4242
    }
}
