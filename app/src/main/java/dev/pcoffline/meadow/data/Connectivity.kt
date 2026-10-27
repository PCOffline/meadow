package dev.pcoffline.meadow.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the phone has a network that reaches the internet. Offline, Firestore keeps writes for
 * later and applies them here at once, so nothing waits on them; what only the backend can do
 * (asking a Bumblebee) says so straight away instead of spinning.
 */
object Connectivity {
    // Unknown counts as online: a wrong "offline" would block what might well have worked.
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    fun watch(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        _online.value = cm.activeNetwork != null && cm.getNetworkCapabilities(cm.activeNetwork)?.hasInternet() != false
        runCatching {
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        _online.value = caps.hasInternet()
                    }

                    override fun onLost(network: Network) {
                        _online.value = false
                    }
                },
            )
        }
    }

    @VisibleForTesting
    fun set(online: Boolean) {
        _online.value = online
    }

    private fun NetworkCapabilities.hasInternet() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
