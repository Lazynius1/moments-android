package com.moments.android.views.messaging.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Port de `Views/Messaging/Services/ChatDraftStore.swift`. */
sealed interface ChatDraftEvent {
    data class Changed(val conversationId: String) : ChatDraftEvent
    data class VanishModeChanged(val conversationId: String) : ChatDraftEvent
    data class MarkedReadLocally(val conversationId: String) : ChatDraftEvent
}

object ChatDraftEvents {
    private val _events = MutableSharedFlow<ChatDraftEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ChatDraftEvent> = _events.asSharedFlow()
    fun emit(event: ChatDraftEvent) { _events.tryEmit(event) }
}

object ChatDraftStore {
    private const val preferences = "chat_drafts"
    private const val keyPrefix = "chatDraft"

    fun draft(context: Context, conversationId: String, userId: String? = FirebaseAuth.getInstance().currentUser?.uid): String {
        val key = storageKey(conversationId, userId) ?: return ""
        return context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE).getString(key, "").orEmpty()
    }

    fun setDraft(context: Context, text: String, conversationId: String, userId: String? = FirebaseAuth.getInstance().currentUser?.uid) {
        val key = storageKey(conversationId, userId) ?: return
        val prefs = context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        val previous = prefs.getString(key, "").orEmpty()
        if (text.trim().isEmpty()) prefs.edit().remove(key).apply() else prefs.edit().putString(key, text).apply()
        if (previous != prefs.getString(key, "").orEmpty()) ChatDraftEvents.emit(ChatDraftEvent.Changed(conversationId))
    }

    fun clearDraft(context: Context, conversationId: String, userId: String? = FirebaseAuth.getInstance().currentUser?.uid) = setDraft(context, "", conversationId, userId)

    private fun storageKey(conversationId: String, userId: String?): String? {
        val clean = conversationId.trim()
        if (clean.isBlank() || userId.isNullOrBlank()) return null
        return "$keyPrefix.$userId.$clean"
    }
}
