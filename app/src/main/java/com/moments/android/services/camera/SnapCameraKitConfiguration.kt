package com.moments.android.services.camera

/**
 * Port de SnapCameraKitConfiguration.swift.
 * Snap Camera Kit (lentes AR) no está cableado en Android todavía;
 * mismos defaults: feature off hasta tener lentes reales + credenciales.
 */
object SnapCameraKitConfiguration {
    /** Flag maestro: desactiva filtros AR mientras no haya lentes reales. */
    const val isFeatureEnabled: Boolean = false

    /** Desde BuildConfig / meta-data cuando se integre Camera Kit. */
    var apiToken: String? = null
        private set
    var clientID: String? = null
        private set
    var defaultLensGroupID: String? = null
        private set

    val isConfigured: Boolean
        get() = apiToken != null && clientID != null && defaultLensGroupID != null

    fun configure(apiToken: String?, clientID: String?, lensGroupID: String?) {
        this.apiToken = normalized(apiToken)
        this.clientID = normalized(clientID)
        this.defaultLensGroupID = normalized(lensGroupID)
    }

    private fun normalized(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.startsWith("\$(")) return null
        return trimmed
    }
}
