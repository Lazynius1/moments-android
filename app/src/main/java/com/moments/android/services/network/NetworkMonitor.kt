package com.moments.android.services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Equivalente Android de NetworkMonitor.swift (NWPathMonitor -> ConnectivityManager).
// Se asume conectado por defecto; el callback corrige el estado real al arrancar.
object NetworkMonitor {

    enum class ConnectionType { WIFI, CELLULAR, ETHERNET, UNKNOWN }

    private val _isConnected = MutableStateFlow(true)
    val isConnectedFlow: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionType = MutableStateFlow(ConnectionType.UNKNOWN)
    val connectionTypeFlow: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val _isExpensive = MutableStateFlow(false)
    val isExpensiveFlow: StateFlow<Boolean> = _isExpensive.asStateFlow()

    private val _isConstrained = MutableStateFlow(false)
    val isConstrainedFlow: StateFlow<Boolean> = _isConstrained.asStateFlow()

    // Accesos síncronos (equivalente a leer la @Published directamente en iOS).
    val isConnected: Boolean get() = _isConnected.value
    val connectionType: ConnectionType get() = _connectionType.value
    val isExpensive: Boolean get() = _isExpensive.value
    /** Equivalente a NWPath.isConstrained (datos limitados / Low Data Mode). */
    val isConstrained: Boolean get() = _isConstrained.value

    val isSlowConnection: Boolean
        get() = connectionType == ConnectionType.CELLULAR && isExpensive

    val shouldUseOfflineMode: Boolean
        get() = !isConnected || isSlowConnection

    private var connectivityManager: ConnectivityManager? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
            refreshCapabilities(network)
        }

        override fun onLost(network: Network) {
            _isConnected.value = false
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _isConnected.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isExpensive.value = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            _isConstrained.value = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
            _connectionType.value = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
        }
    }

    fun initialize(context: Context) {
        if (connectivityManager != null) return
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        // Estado inicial a partir de la red activa.
        cm.activeNetwork?.let { refreshCapabilities(it) }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    private fun refreshCapabilities(network: Network) {
        val caps = connectivityManager?.getNetworkCapabilities(network) ?: return
        callback.onCapabilitiesChanged(network, caps)
    }
}
