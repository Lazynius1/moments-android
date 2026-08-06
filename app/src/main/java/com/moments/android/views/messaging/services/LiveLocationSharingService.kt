package com.moments.android.views.messaging.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.messaging.models.LiveLocationDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Date

/**
 * Port de `LiveLocationSharingService.swift`.
 * Sesión de ubicación en vivo: throttle 10s / 10m, persistencia, restore con validación servidor.
 */
object LiveLocationSharingService {

    data class ActiveSession(
        val ownerUserId: String,
        val conversationId: String,
        val messageId: String,
        val sessionId: String,
        val duration: LiveLocationDuration,
        val expiresAt: Date,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** ≡ UserDefaults key `liveLocationSharing.activeSession`. */
    private const val PREFS = "live_location_sharing"
    private const val PERSISTENCE_KEY = "liveLocationSharing.activeSession"
    private const val MIN_UPDATE_INTERVAL_MS = 10_000L
    private const val MIN_DISTANCE_METERS = 10f

    @Volatile private var appContext: Context? = null
    @Volatile private var activeSession: ActiveSession? = null
    @Volatile private var isRestoring = false

    private var locationManager: LocationManager? = null
    private var lastUpdateSentAtMs: Long? = null
    private var lastSentLat: Double? = null
    private var lastSentLng: Double? = null
    private var expirationJob: Job? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }

        @Deprecated("Deprecated in API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            locationManager = context.applicationContext
                .getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        }
    }

    val hasActiveSession: Boolean get() = activeSession != null

    fun isSharing(conversationId: String): Boolean =
        activeSession?.conversationId == conversationId

    /** ≡ `startSession` — inicia tracking para un mensaje ya creado. */
    fun startSession(
        conversationId: String,
        messageId: String,
        sessionId: String,
        duration: LiveLocationDuration,
        expiresAt: Date,
    ) {
        activeSession?.let { existing ->
            scope.launch {
                runCatching {
                    ChatService.stopLiveLocationMessage(existing.conversationId, existing.messageId)
                }
            }
        }

        val session = ActiveSession(
            ownerUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
            conversationId = conversationId,
            messageId = messageId,
            sessionId = sessionId,
            duration = duration,
            expiresAt = expiresAt,
        )
        activeSession = session
        persist(session)
        lastUpdateSentAtMs = null
        lastSentLat = null
        lastSentLng = null
        beginTracking(expiresAt)
    }

    /**
     * ≡ `restoreIfNeeded` — reanuda tras reopen si el servidor confirma que sigue activa.
     * Error de red: conserva persistencia y reintenta después.
     */
    fun restoreIfNeeded() {
        if (activeSession != null || isRestoring) return
        val session = loadPersistedSession() ?: return

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        if (session.ownerUserId != currentUserId) {
            clearPersistedSession()
            return
        }

        if (!session.expiresAt.after(Date())) {
            clearPersistedSession()
            scope.launch {
                runCatching {
                    ChatService.stopLiveLocationMessage(session.conversationId, session.messageId)
                }
            }
            return
        }

        isRestoring = true
        scope.launch {
            try {
                val status = ChatService.fetchLiveLocationStatus(
                    session.conversationId,
                    session.messageId,
                ) ?: return@launch // red: conservar persistencia

                if (!status.exists ||
                    status.senderId != currentUserId ||
                    status.isStopped
                ) {
                    clearPersistedSession()
                    return@launch
                }

                val serverExpiry = status.expiresAt
                if (serverExpiry != null && !serverExpiry.after(Date())) {
                    clearPersistedSession()
                    return@launch
                }

                if (activeSession != null) return@launch

                activeSession = session
                lastUpdateSentAtMs = null
                lastSentLat = null
                lastSentLng = null
                beginTracking(session.expiresAt)
            } finally {
                isRestoring = false
            }
        }
    }

    /** ≡ `stop(markStopped:)` */
    fun stop(markStopped: Boolean = true) {
        val session = activeSession ?: return
        stopLocationUpdates()
        expirationJob?.cancel()
        expirationJob = null

        if (markStopped) {
            scope.launch {
                runCatching {
                    ChatService.stopLiveLocationMessage(session.conversationId, session.messageId)
                }
            }
        }
        activeSession = null
        clearPersistedSession()
        lastUpdateSentAtMs = null
        lastSentLat = null
        lastSentLng = null
    }

    /** ≡ `stopSharing` — match por messageId **o** conversationId (como iOS). */
    fun stopSharing(messageId: String, conversationId: String) {
        val session = activeSession
        if (session != null &&
            (session.messageId == messageId || session.conversationId == conversationId)
        ) {
            stop(markStopped = true)
            return
        }
        clearPersistedSession()
        scope.launch {
            runCatching { ChatService.stopLiveLocationMessage(conversationId, messageId) }
        }
    }

    /** ≡ `stop(messageId:)` */
    fun stop(messageId: String) {
        if (activeSession?.messageId != messageId) return
        stop(markStopped = true)
    }

    fun handleUserSignedOut() {
        if (activeSession != null) {
            stop(markStopped = false)
        } else {
            clearPersistedSession()
        }
        isRestoring = false
    }

    suspend fun endActiveSessionForSignOut() {
        val session = activeSession ?: run {
            clearPersistedSession()
            return
        }
        runCatching {
            ChatService.stopLiveLocationMessage(session.conversationId, session.messageId)
        }
        stop(markStopped = false)
    }

    // MARK: - Tracking

    private fun beginTracking(expiresAt: Date) {
        val ctx = appContext ?: return
        val lm = locationManager ?: return
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            scheduleExpiration(expiresAt)
            return
        }

        // ≡ iOS: Always → background updates; While Using → solo foreground (LocationManager).
        stopLocationUpdates()
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { lm.isProviderEnabled(it) }
        for (provider in providers) {
            runCatching {
                lm.requestLocationUpdates(
                    provider,
                    MIN_UPDATE_INTERVAL_MS,
                    MIN_DISTANCE_METERS,
                    locationListener,
                    Looper.getMainLooper(),
                )
            }
        }
        // Empujar primera coordenada conocida sin esperar al throttle.
        runCatching {
            providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let(::handleNewLocation)
        }
        scheduleExpiration(expiresAt)
    }

    private fun stopLocationUpdates() {
        val lm = locationManager ?: return
        runCatching { lm.removeUpdates(locationListener) }
    }

    private fun scheduleExpiration(at: Date) {
        expirationJob?.cancel()
        val delayMs = (at.time - System.currentTimeMillis()).coerceAtLeast(1_000L)
        expirationJob = scope.launch {
            delay(delayMs)
            stop(markStopped = true)
        }
    }

    private fun handleNewLocation(location: Location) {
        val session = activeSession ?: return
        if (!session.expiresAt.after(Date())) {
            stop(markStopped = true)
            return
        }

        val now = System.currentTimeMillis()
        // Primera fix: enviar siempre (aunque no haya movimiento).
        val isFirstFix = lastUpdateSentAtMs == null
        if (!isFirstFix) {
            lastUpdateSentAtMs?.let { last ->
                if (now - last < MIN_UPDATE_INTERVAL_MS) return
            }

            val lastLat = lastSentLat
            val lastLng = lastSentLng
            if (lastLat != null && lastLng != null) {
                val results = FloatArray(1)
                Location.distanceBetween(lastLat, lastLng, location.latitude, location.longitude, results)
                if (results[0] < MIN_DISTANCE_METERS) return
            }
        }

        lastUpdateSentAtMs = now
        lastSentLat = location.latitude
        lastSentLng = location.longitude
        scope.launch {
            runCatching {
                ChatService.updateLiveLocationMessage(
                    conversationId = session.conversationId,
                    messageId = session.messageId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                )
            }
        }
    }

    // MARK: - Persistencia

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist(session: ActiveSession) {
        val p = prefs() ?: return
        val json = JSONObject().apply {
            put("ownerUserId", session.ownerUserId)
            put("conversationId", session.conversationId)
            put("messageId", session.messageId)
            put("sessionId", session.sessionId)
            put("duration", session.duration.firestoreValue)
            put("expiresAt", session.expiresAt.time / 1000.0) // ≡ Date Codable ~epoch seconds
        }
        p.edit().putString(PERSISTENCE_KEY, json.toString()).apply()
    }

    private fun loadPersistedSession(): ActiveSession? {
        val raw = prefs()?.getString(PERSISTENCE_KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val duration = LiveLocationDuration.from(json.optString("duration", null))
                ?: LiveLocationDuration.from(json.optString("durationRaw", null))
                ?: return@runCatching null
            val expiresAtMs = when {
                json.has("expiresAt") -> (json.getDouble("expiresAt") * 1000.0).toLong()
                json.has("expiresAtMs") -> json.getLong("expiresAtMs")
                else -> return@runCatching null
            }
            ActiveSession(
                ownerUserId = json.getString("ownerUserId"),
                conversationId = json.getString("conversationId"),
                messageId = json.getString("messageId"),
                sessionId = json.getString("sessionId"),
                duration = duration,
                expiresAt = Date(expiresAtMs),
            )
        }.getOrNull()
    }

    private fun clearPersistedSession() {
        prefs()?.edit()?.remove(PERSISTENCE_KEY)?.apply()
    }
}
