package com.moments.android.views.misc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.ad.AdMobConfiguration
import com.moments.android.ad.FeedNativeAdPool
import com.moments.android.views.permission.shared.PermissionPrimerGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ≡ `WhatsNewPresentationCoordinator.swift` — no mostrar en splash/login;
 * presentar desde el feed cuando ads + notifs hayan pasado.
 */
object WhatsNewPresentationCoordinator {
    private const val KEY_LAST_VERSION = "lastVersionPrompted"
    /** Primer de notifs a +20s; margen extra vs iOS 22s para no solaparlo. */
    private const val NOTIFS_SETTLE_MS = 28_000L

    private val _readyToPresent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val readyToPresent: SharedFlow<Unit> = _readyToPresent.asSharedFlow()

    @Volatile private var isPending = false
    @Volatile private var didPresentThisLaunch = false
    private var evaluationJob: Job? = null
    private var feedAppearAtMs: Long = 0L

    fun handleSplashFinished(context: Context) {
        val prefs = prefs(context)
        val current = currentVersion(context)
        // Primera install (sin key) o upgrade → mismas notas; no hay onboarding aparte.
        if (!prefs.contains(KEY_LAST_VERSION)) {
            isPending = true
            return
        }
        val last = prefs.getString(KEY_LAST_VERSION, current) ?: current
        isPending = last != current
    }

    fun feedBecameActive(context: Context, scope: CoroutineScope) {
        if (!isPending || didPresentThisLaunch) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        feedAppearAtMs = System.currentTimeMillis()
        evaluationJob?.cancel()
        evaluationJob = scope.launch {
            // Más vueltas: cubre primer (~20s) + margen (~28s) + ads.
            repeat(90) {
                if (isReadyToPresent(context)) {
                    delay(1_000)
                    if (isReadyToPresent(context)) {
                        presentIfNeeded(context)
                        return@launch
                    }
                }
                delay(500)
            }
            presentIfNeeded(context)
        }
    }

    fun feedBecameInactive() {
        evaluationJob?.cancel()
        evaluationJob = null
    }

    private fun isReadyToPresent(context: Context): Boolean {
        if (!isPending || didPresentThisLaunch) return false
        if (FirebaseAuth.getInstance().currentUser == null) return false
        val adsReady = !AdMobConfiguration.shouldShowConsentFlow ||
            FeedNativeAdPool.didOfferConsentThisSession
        if (!adsReady) return false
        return notificationsSettled(context)
    }

    private fun notificationsSettled(context: Context): Boolean {
        // Mientras el fullscreen del primer está abierto, no solapar WhatsNew.
        if (PermissionPrimerGate.notificationsPrimerPresenting) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true
        // Aún no concedido: esperar ventana del primer (+20s) + margen.
        val elapsed = System.currentTimeMillis() - feedAppearAtMs
        return elapsed >= NOTIFS_SETTLE_MS
    }

    private fun presentIfNeeded(context: Context) {
        if (!isPending || didPresentThisLaunch) return
        didPresentThisLaunch = true
        isPending = false
        prefs(context).edit().putString(KEY_LAST_VERSION, currentVersion(context)).apply()
        _readyToPresent.tryEmit(Unit)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("moments_app", Context.MODE_PRIVATE)

    private fun currentVersion(context: Context): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    }.getOrNull() ?: "1.1.0"
}
