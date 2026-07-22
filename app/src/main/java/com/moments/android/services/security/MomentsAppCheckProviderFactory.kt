package com.moments.android.services.security

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Port de MomentsAppCheckProviderFactory.swift.
 * iOS: App Attest en device / Debug en simulador.
 * Android: Play Integrity en release / Debug provider en debug builds.
 */
object MomentsAppCheckProviderFactory : AppCheckProviderFactory {
    override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
        val factory = if (com.moments.android.BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        return factory.create(firebaseApp)
    }
}
