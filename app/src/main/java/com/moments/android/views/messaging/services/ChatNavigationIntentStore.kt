package com.moments.android.views.messaging.services

/**
 * Port mínimo de ChatNavigationIntentStore.swift — intenciones al abrir chat desde push.
 */
object ChatNavigationIntentStore {
    data class OpenIntent(
        var playBuzzOnOpen: Boolean = false,
        var buzzEventId: String? = null,
        val highlightMessageIds: MutableSet<String> = mutableSetOf(),
    )

    private val lock = Any()
    private val pending = mutableMapOf<String, OpenIntent>()

    private fun updateIntent(conversationId: String, update: (OpenIntent) -> Unit) {
        if (conversationId.isBlank()) return
        synchronized(lock) {
            val intent = pending.getOrPut(conversationId) { OpenIntent() }
            update(intent)
            pending[conversationId] = intent
        }
    }

    fun enqueueHighlight(conversationId: String, messageId: String) {
        if (messageId.isBlank()) return
        updateIntent(conversationId) { it.highlightMessageIds.add(messageId) }
    }

    fun enqueueBuzz(conversationId: String, buzzEventId: String? = null) {
        updateIntent(conversationId) {
            it.playBuzzOnOpen = true
            if (!buzzEventId.isNullOrBlank()) it.buzzEventId = buzzEventId
        }
    }

    fun clearBuzz(conversationId: String) {
        updateIntent(conversationId) {
            it.playBuzzOnOpen = false
            it.buzzEventId = null
        }
    }

    fun clearHighlights(conversationId: String) {
        updateIntent(conversationId) { it.highlightMessageIds.clear() }
    }

    fun peek(conversationId: String): OpenIntent? {
        if (conversationId.isBlank()) return null
        synchronized(lock) { return pending[conversationId]?.copy(highlightMessageIds = pending[conversationId]!!.highlightMessageIds.toMutableSet()) }
    }

    fun clear(conversationId: String) {
        if (conversationId.isBlank()) return
        synchronized(lock) { pending.remove(conversationId) }
    }
}
