package com.ghealth.tools.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkStatus(
    val isAvailable: Boolean,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false
)

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("MissingPermission")
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @SuppressLint("MissingPermission")
    val networkStatus: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                
                trySend(NetworkStatus(
                    isAvailable = true,
                    isWifi = isWifi,
                    isCellular = isCellular
                ))
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus(isAvailable = false))
            }

            override fun onUnavailable() {
                trySend(NetworkStatus(isAvailable = false))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        val currentNetwork = connectivityManager.activeNetwork
        val currentCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
        val isCurrentlyAvailable = currentNetwork != null && 
            currentCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        if (isCurrentlyAvailable) {
            val isWifi = currentCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = currentCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            trySend(NetworkStatus(
                isAvailable = true,
                isWifi = isWifi,
                isCellular = isCellular
            ))
        } else {
            trySend(NetworkStatus(isAvailable = false))
        }

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    @SuppressLint("MissingPermission")
    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
