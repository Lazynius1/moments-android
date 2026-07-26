package com.moments.android.views.messaging.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Port de `ChatDraftStore.swift` + `Notification.Name` del mismo archivo.
 * SharedFlow ≡ NotificationCenter (chatDraft / vanish / markedRead).
 */
sealed interface ChatDraftEvent {
    data class Changed(val conversationId: String) : ChatDraftEvent
    /** ≡ `conversationVanishModeDidChange` — userInfo: conversationId + vanishModeActive. */
    data class VanishModeChanged(val conversationId: String, val vanishModeActive: Boolean) : ChatDraftEvent
    /** ≡ `conversationMarkedReadLocally`. */
    data class MarkedReadLocally(val conversationId: String) : ChatDraftEvent
}

object ChatDraftEvents {
    private val _events = MutableSharedFlow<ChatDraftEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ChatDraftEvent> = _events.asSharedFlow()
    fun emit(event: ChatDraftEvent) {
        _events.tryEmit(event)
    }
}

object ChatDraftStore {
    private const val PREFS = "chat_drafts"
    private const val KEY_PREFIX = "chatDraft"

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() =
        (appContext ?: error("ChatDraftStore.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun draft(
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ): String {
        val key = storageKey(conversationId, userId) ?: return ""
        return prefs().getString(key, null).orEmpty()
    }

    fun draft(
        context: Context,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ): String {
        initialize(context)
        return draft(conversationId, userId)
    }

    fun setDraft(
        text: String,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        val key = storageKey(conversationId, userId) ?: return
        val p = prefs()
        val previous = p.getString(key, null).orEmpty()
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            p.edit().remove(key).apply()
        } else {
            // iOS guarda `text` original (no trimmed) si normalized no está vacío.
            p.edit().putString(key, text).apply()
        }
        val next = p.getString(key, null).orEmpty()
        if (previous != next) {
            ChatDraftEvents.emit(ChatDraftEvent.Changed(conversationId))
        }
    }

    fun setDraft(
        context: Context,
        text: String,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        initialize(context)
        setDraft(text, conversationId, userId)
    }

    fun clearDraft(
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        setDraft("", conversationId, userId)
    }

    fun clearDraft(
        context: Context,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        initialize(context)
        clearDraft(conversationId, userId)
    }

    private fun storageKey(conversationId: String, userId: String?): String? {
        val cleanConversationId = conversationId.trim()
        if (userId.isNullOrBlank() || cleanConversationId.isEmpty()) return null
        return "$KEY_PREFIX.$userId.$cleanConversationId"
    }
}
