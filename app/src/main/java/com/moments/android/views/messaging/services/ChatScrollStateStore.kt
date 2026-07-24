package com.moments.android.views.messaging.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

sealed interface ChatScrollTarget {
    val pinsToBottom: Boolean

    data class Bottom(val messageId: String? = null) : ChatScrollTarget {
        override val pinsToBottom = true
    }

    data class FirstUnread(val messageId: String) : ChatScrollTarget {
        override val pinsToBottom = false
    }

    data class HighlightedMessage(val messageId: String) : ChatScrollTarget {
        override val pinsToBottom = false
    }
}

/** Limpia las claves heredadas de posición de scroll; no persiste estado nuevo. */
object ChatScrollStateStore {
    private const val preferences = "chat_scroll_state"
    private const val keyPrefix = "chatScrollState"

    fun clear(
        context: Context,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        val key = storageKey(conversationId, userId) ?: return
        context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    fun clearAll(
        context: Context,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        if (userId.isNullOrBlank()) return
        val prefix = "$keyPrefix.$userId."
        val prefs = context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun storageKey(conversationId: String, userId: String?): String? {
        val cleanConversationId = conversationId.trim()
        if (cleanConversationId.isBlank() || userId.isNullOrBlank()) return null
        return "$keyPrefix.$userId.$cleanConversationId"
    }
}
