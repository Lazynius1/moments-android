package com.moments.android.views.messaging.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Port de `ChatBuzzProcessedStore.swift`.
 * Buzz ya reproducidos (shake/haptic) por conversación — por event id, no un booleano global.
 * Persistencia ordenada (≡ `UserDefaults.stringArray`); el tope 40 es por sufijo = más recientes.
 */
object ChatBuzzProcessedStore {
    /** ≡ `replayWindow` iOS (300 s). */
    val replayWindowMillis: Long = TimeUnit.SECONDS.toMillis(300)

    private const val KEY_PREFIX = "chatBuzzProcessed"
    private const val PREFS = "chat_buzz_processed"
    private const val MAX_STORED_IDS = 40

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() =
        (appContext ?: error("ChatBuzzProcessedStore.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isProcessed(
        eventId: String,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ): Boolean {
        val key = storageKey(conversationId, userId) ?: return false
        return readIds(key).contains(eventId)
    }

    /** Overload con Context por call sites Compose (inicializa si hace falta). */
    fun isProcessed(
        context: Context,
        eventId: String,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ): Boolean {
        initialize(context)
        return isProcessed(eventId, conversationId, userId)
    }

    fun markProcessed(
        eventId: String,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        val key = storageKey(conversationId, userId) ?: return
        val ids = readIds(key)
        if (eventId in ids) return
        ids.add(eventId)
        val trimmed = if (ids.size > MAX_STORED_IDS) ids.takeLast(MAX_STORED_IDS) else ids
        writeIds(key, trimmed)
    }

    fun markProcessed(
        context: Context,
        eventId: String,
        conversationId: String,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    ) {
        initialize(context)
        markProcessed(eventId, conversationId, userId)
    }

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

    private fun storageKey(conversationId: String, userId: String?): String? {
        val cleanConversationId = conversationId.trim()
        if (userId.isNullOrBlank() || cleanConversationId.isEmpty()) return null
        return "$KEY_PREFIX.$userId.$cleanConversationId"
    }

    private fun readIds(key: String): MutableList<String> {
        val p = prefs()
        p.getString(key, null)?.let { raw ->
            return runCatching {
                val arr = JSONArray(raw)
                MutableList(arr.length()) { i -> arr.getString(i) }
            }.getOrDefault(mutableListOf())
        }
        // Migración one-shot desde StringSet legacy (sin orden fiable).
        val legacy = p.getStringSet(key, null)
        if (!legacy.isNullOrEmpty()) {
            val list = legacy.toMutableList()
            writeIds(key, list)
            return list
        }
        return mutableListOf()
    }

    private fun writeIds(key: String, ids: List<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs().edit().putString(key, arr.toString()).apply()
    }
}
