package com.moments.android.services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Port de `NetworkMonitor.swift`.
 * `NWPathMonitor` → `ConnectivityManager.NetworkCallback`.
 * Se asume conectado por defecto; el callback corrige el estado real al arrancar.
 */
object NetworkMonitor {

    enum class ConnectionType {
        WIFI,
        CELLULAR,
        ETHERNET,
        UNKNOWN,
        ;

        /** Port de `ConnectionType.description` (mismas cadenas que iOS). */
        val description: String
            get() = when (this) {
                WIFI -> "WiFi"
                CELLULAR -> "Datos móviles"
                ETHERNET -> "Ethernet"
                UNKNOWN -> "Desconocido"
            }

        /**
         * Port de `ConnectionType.icon` (SF Symbol name).
         * Los callers Compose usan Material Icons; este valor es paridad de API.
         */
        val icon: String
            get() = when (this) {
                WIFI -> "wifi"
                CELLULAR -> "antenna.radiowaves.left.and.right"
                ETHERNET -> "network"
                UNKNOWN -> "questionmark.circle"
            }
    }

    private val _isConnected = MutableStateFlow(true)
    val isConnectedFlow: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionType = MutableStateFlow(ConnectionType.UNKNOWN)
    val connectionTypeFlow: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val _isExpensive = MutableStateFlow(false)
    val isExpensiveFlow: StateFlow<Boolean> = _isExpensive.asStateFlow()

    private val _isConstrained = MutableStateFlow(false)
    val isConstrainedFlow: StateFlow<Boolean> = _isConstrained.asStateFlow()

    /** Accesos síncronos (equivalente a leer `@Published` en iOS). */
    val isConnected: Boolean get() = _isConnected.value
    val connectionType: ConnectionType get() = _connectionType.value
    val isExpensive: Boolean get() = _isExpensive.value
    /** Equivalente a `NWPath.isConstrained` (Low Data Mode / datos limitados). */
    val isConstrained: Boolean get() = _isConstrained.value

    /** Helper iOS: celular + caro. */
    val isSlowConnection: Boolean
        get() = connectionType == ConnectionType.CELLULAR && isExpensive

    /** Helper iOS: sin red o conexión lenta → preferir cache / modo offline. */
    val shouldUseOfflineMode: Boolean
        get() = !isConnected || isSlowConnection

    private var connectivityManager: ConnectivityManager? = null
    private var monitoring = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            applyCapabilities(network)
        }

        override fun onLost(network: Network) {
            // Como un pathUpdate de NWPathMonitor: si queda otra red activa, usarla.
            val active = connectivityManager?.activeNetwork
            if (active != null && active != network) {
                applyCapabilities(active)
            } else {
                applyDisconnected()
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            applyFromCapabilities(caps)
        }
    }

    fun initialize(context: Context) {
        if (connectivityManager != null) return
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm
        startMonitoring()
    }

    /** Port de `startMonitoring()`. */
    fun startMonitoring() {
        val cm = connectivityManager ?: return
        if (monitoring) return

        cm.activeNetwork?.let { applyCapabilities(it) } ?: applyDisconnected()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        monitoring = true
    }

    /** Port de `stopMonitoring()` → `monitor.cancel()`. */
    fun stopMonitoring() {
        val cm = connectivityManager ?: return
        if (!monitoring) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        monitoring = false
    }

    private fun applyCapabilities(network: Network) {
        val caps = connectivityManager?.getNetworkCapabilities(network)
        if (caps == null) {
            applyDisconnected()
            return
        }
        applyFromCapabilities(caps)
    }

    private fun applyFromCapabilities(caps: NetworkCapabilities) {
        // `satisfied` ≈ INTERNET + VALIDATED (ruta usable de verdad).
        _isConnected.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        _isExpensive.value = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        // Low Data / congestión ≈ isConstrained de NWPath.
        _isConstrained.value = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
        _connectionType.value = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            else -> ConnectionType.UNKNOWN
        }
    }

    private fun applyDisconnected() {
        _isConnected.value = false
        _isExpensive.value = false
        _isConstrained.value = false
        _connectionType.value = ConnectionType.UNKNOWN
    }
}
