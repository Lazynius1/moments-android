package com.moments.android.services.security

import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.moments.android.BuildConfig

/**
 * Port de `MomentsAppCheckProviderFactory.swift`.
 * iOS: App Attest en device / Debug en simulador.
 * Android: Play Integrity en release device / Debug en DEBUG o emulador.
 */
object MomentsAppCheckProviderFactory : AppCheckProviderFactory {
    override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
        val factory = if (shouldUseDebugProvider()) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        return factory.create(firebaseApp)
    }

    private fun shouldUseDebugProvider(): Boolean {
        if (BuildConfig.DEBUG) return true
        return isEmulator()
    }

    /** Equivalente práctico a `#if targetEnvironment(simulator)`. */
    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.PRODUCT == "google_sdk"
    }
}
