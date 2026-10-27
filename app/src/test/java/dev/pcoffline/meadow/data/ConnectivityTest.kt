package dev.pcoffline.meadow.data

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** Whether the phone is online follows the network in use, whatever order Android reports changes in. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ConnectivityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val cm = app.getSystemService(ConnectivityManager::class.java)

    @Before
    fun reachesTheInternet() {
        // As on a phone: the network in use says it reaches the internet (Robolectric's doesn't by default).
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
    }

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
