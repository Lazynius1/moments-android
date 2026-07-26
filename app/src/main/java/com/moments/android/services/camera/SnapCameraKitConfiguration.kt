package com.moments.android.services.camera

import com.moments.android.BuildConfig

/**
 * Port de `SnapCameraKitConfiguration.swift`.
 * BuildConfig ≡ Info.plist (`SCCameraKitAPIToken` / `SCCameraKitClientID`) + `SnapCameraKit.plist`.
 * Snap SDK no cableado aún; [isFeatureEnabled] = false como iOS.
 */
object SnapCameraKitConfiguration {

    /** Flag maestro: desactiva filtros AR mientras solo haya lentes demo. */
    const val isFeatureEnabled: Boolean = false

    private data class Loaded(
        val apiToken: String?,
        val clientID: String?,
        val lensGroupID: String?,
    )

    @Volatile
    private var loaded: Loaded? = null

    private fun values(): Loaded {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            val next = Loaded(
                apiToken = normalized(BuildConfig.SC_CAMERA_KIT_API_TOKEN),
                clientID = normalized(BuildConfig.SC_CAMERA_KIT_CLIENT_ID),
                lensGroupID = normalized(BuildConfig.SC_CAMERA_KIT_LENS_GROUP_ID),
            )
            loaded = next
            return next
        }
    }

    val apiToken: String? get() = values().apiToken
    val clientID: String? get() = values().clientID
    val defaultLensGroupID: String? get() = values().lensGroupID

    val isConfigured: Boolean
        get() = apiToken != null && clientID != null && defaultLensGroupID != null

    /** Override (tests / spike); iOS solo lee Bundle. */
    fun configure(apiToken: String?, clientID: String?, lensGroupID: String?) {
        loaded = Loaded(
            apiToken = normalized(apiToken),
            clientID = normalized(clientID),
            lensGroupID = normalized(lensGroupID),
        )
    }

    private fun normalized(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.startsWith("\$(")) return null
        return trimmed
    }
}
