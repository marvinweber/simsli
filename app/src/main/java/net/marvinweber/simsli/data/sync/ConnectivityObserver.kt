package net.marvinweber.simsli.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the device currently has a usable internet connection, via the
 * default network callback plus a synchronous initial check (the callback only
 * reports *changes*).
 *
 * This detects "no network" — it cannot see a dead server behind a healthy
 * connection. [SyncManager] complements it with the last-sync-failed flag; the
 * warning icon is driven by the combination of the two.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline())

    /** True while the device has a validated internet connection. */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                // Re-query instead of blindly setting false: with Wi-Fi + cellular
                // (or a VPN) both alive, losing one path must not flip the flag.
                _isOnline.value = checkCurrentlyOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                _isOnline.value = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun checkCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
