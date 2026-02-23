package com.openwearables.healthsdk.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat.getSystemService

class NetworkConnectionManager private constructor() {
    private lateinit var _shared: NetworkConnectionManager

    var isConnected: Boolean = false
        private set

    companion object {
        val shared: NetworkConnectionManager by lazy { NetworkConnectionManager() }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // network is available for use
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            isConnected = true
        }

        // Network capabilities have changed for the network
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
        }

        // lost network connection
        override fun onLost(network: Network) {
            super.onLost(network)
            isConnected = false
        }
    }

    fun init(context: Context) {
        if (::_shared.isInitialized) return
        _shared = this

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        val connectivityManager = getSystemService(context, ConnectivityManager::class.java)
        connectivityManager?.requestNetwork(networkRequest, networkCallback)
    }
}