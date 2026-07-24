package com.moments.android.views.messaging.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/** Port de `Views/Messaging/Services/ChatBuzzProcessedStore.swift`. */
object ChatBuzzProcessedStore {
    const val replayWindowMillis = 5 * 60 * 1000L
    private const val keyPrefix = "chatBuzzProcessed"
    private const val preferences = "chat_buzz_processed"
    private const val maximumStoredIds = 40

    fun isProcessed(context: Context, eventId: String, conversationId: String, userId: String? = FirebaseAuth.getInstance().currentUser?.uid): Boolean {
        val key = storageKey(conversationId, userId) ?: return false
        return context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE).getStringSet(key, emptySet()).orEmpty().contains(eventId)
    }

    fun markProcessed(context: Context, eventId: String, conversationId: String, userId: String? = FirebaseAuth.getInstance().currentUser?.uid) {
        val key = storageKey(conversationId, userId) ?: return
        val prefs = context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(key, emptySet()).orEmpty().toMutableList()
        if (eventId in ids) return
        ids += eventId
        prefs.edit().putStringSet(key, ids.takeLast(maximumStoredIds).toSet()).apply()
    }

    fun clear(context: Context, conversationId: String, userId: String? = FirebaseAuth.getInstance().currentUser?.uid) {
        val key = storageKey(conversationId, userId) ?: return
        context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().remove(key).apply()
    }

    private fun storageKey(conversationId: String, userId: String?): String? {
        val cleanConversationId = conversationId.trim()
        if (userId.isNullOrBlank() || cleanConversationId.isBlank()) return null
        return "$keyPrefix.$userId.$cleanConversationId"
    }
}
