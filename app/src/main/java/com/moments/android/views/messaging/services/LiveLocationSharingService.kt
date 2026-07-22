package com.moments.android.views.messaging.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

/**
 * Port mínimo de LiveLocationSharingService.swift para logout completo.
 * Tracking GPS / restoreIfNeeded requieren Messaging UI + permisos Always.
 */
object LiveLocationSharingService {

    private const val PREFS = "live_location_sharing"
    private const val KEY_ACTIVE_SESSION = "activeSession"

    @Volatile private var appContext: Context? = null
    @Volatile private var activeSession: ActiveSession? = null

    data class ActiveSession(
        val ownerUserId: String,
        val conversationId: String,
        val messageId: String,
        val sessionId: String,
        val durationRaw: String,
        val expiresAtMs: Long,
    )

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        activeSession = loadPersistedSession()
    }

    val hasActiveSession: Boolean get() = activeSession != null

    /** Marca sesión activa como detenida en servidor antes de signOut (paridad iOS). */
    suspend fun endActiveSessionForSignOut() {
        val session = activeSession ?: run {
            clearPersistedSession()
            return
        }
        runCatching {
            ChatService.stopLiveLocationMessage(session.conversationId, session.messageId)
        }
        stopLocal(markStopped = false)
    }

    fun handleUserSignedOut() {
        if (activeSession != null) {
            stopLocal(markStopped = false)
        } else {
            clearPersistedSession()
        }
    }

    private fun stopLocal(markStopped: Boolean) {
        activeSession = null
        clearPersistedSession()
    }

    private fun prefs(): android.content.SharedPreferences? {
        val ctx = appContext ?: return null
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun persist(session: ActiveSession) {
        val p = prefs() ?: return
        val json = JSONObject().apply {
            put("ownerUserId", session.ownerUserId)
            put("conversationId", session.conversationId)
            put("messageId", session.messageId)
            put("sessionId", session.sessionId)
            put("durationRaw", session.durationRaw)
            put("expiresAtMs", session.expiresAtMs)
        }
        p.edit().putString(KEY_ACTIVE_SESSION, json.toString()).apply()
    }

    private fun loadPersistedSession(): ActiveSession? {
        val raw = prefs()?.getString(KEY_ACTIVE_SESSION, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            ActiveSession(
                ownerUserId = json.getString("ownerUserId"),
                conversationId = json.getString("conversationId"),
                messageId = json.getString("messageId"),
                sessionId = json.getString("sessionId"),
                durationRaw = json.optString("durationRaw", ""),
                expiresAtMs = json.optLong("expiresAtMs", 0L),
            )
        }.getOrNull()?.takeIf {
            it.expiresAtMs > System.currentTimeMillis() &&
                it.ownerUserId == FirebaseAuth.getInstance().currentUser?.uid
        }
    }

    private fun clearPersistedSession() {
        prefs()?.edit()?.remove(KEY_ACTIVE_SESSION)?.apply()
    }
}
