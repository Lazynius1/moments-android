package com.moments.android.views.messaging.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * Port de `ChatScrollTarget` + `ChatScrollStateStore.swift`.
 * Ya no se persiste posición entre sesiones; solo limpieza de claves legacy.
 * Codable iOS no se porta: el target es solo en memoria.
 */
sealed interface ChatScrollTarget {
    val pinsToBottom: Boolean

    /** ≡ `.bottom(messageId:)` — messageId obligatorio. */
    data class Bottom(val messageId: String) : ChatScrollTarget {
        override val pinsToBottom = true
    }

    data class FirstUnread(val messageId: String) : ChatScrollTarget {
        override val pinsToBottom = false
    }

    data class HighlightedMessage(val messageId: String) : ChatScrollTarget {
        override val pinsToBottom = false
    }
}

object ChatScrollStateStore {
    private const val PREFS = "chat_scroll_state"
    private const val KEY_PREFIX = "chatScrollState"

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() =
        (appContext ?: error("ChatScrollStateStore.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clear(
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        val key = storageKey(conversationId, userId) ?: return
        prefs().edit().remove(key).apply()
    }

    fun clear(
        context: Context,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        initialize(context)
        clear(conversationId, userId)
    }

    fun clearAll(userId: String? = FirebaseAuth.getInstance().currentUser?.uid) {
        if (userId.isNullOrEmpty()) return
        val prefix = "$KEY_PREFIX.$userId."
        val p = prefs()
        val editor = p.edit()
        p.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    fun clearAll(
        context: Context,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        initialize(context)
        clearAll(userId)
    }

    private fun storageKey(conversationId: String, userId: String?): String? {
        val cleanConversationId = conversationId.trim()
        if (userId.isNullOrEmpty() || cleanConversationId.isEmpty()) return null
        return "$KEY_PREFIX.$userId.$cleanConversationId"
    }
}
