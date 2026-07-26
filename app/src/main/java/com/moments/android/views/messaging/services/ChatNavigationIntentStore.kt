package com.moments.android.views.messaging.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** ≡ Notification.Name.chatMessageReactionHighlight */
data class ChatReactionHighlightEvent(val conversationId: String, val messageId: String)

/** ≡ Notification.Name.chatBuzzHighlight */
data class ChatBuzzHighlightEvent(val conversationId: String, val buzzEventId: String?)

/**
 * Port de `ChatNavigationIntentStore.swift`.
 * Intenciones al abrir un chat desde push (scroll, zumbido, resaltar mensaje).
 * Peek al entrar; clear solo al procesar — nunca consumir en el init de la vista.
 * SharedFlows ≡ NotificationCenter de los dos `Notification.Name` del mismo archivo Swift.
 */
object ChatNavigationIntentStore {
    data class OpenIntent(
        val playBuzzOnOpen: Boolean = false,
        val buzzEventId: String? = null,
        val highlightMessageIds: Set<String> = emptySet(),
    )

    private val lock = Any()
    private val pending = mutableMapOf<String, OpenIntent>()

    private val _messageReactionHighlight =
        MutableSharedFlow<ChatReactionHighlightEvent>(extraBufferCapacity = 16)
    val messageReactionHighlight: SharedFlow<ChatReactionHighlightEvent> =
        _messageReactionHighlight.asSharedFlow()

    private val _chatBuzzHighlight =
        MutableSharedFlow<ChatBuzzHighlightEvent>(extraBufferCapacity = 16)
    val chatBuzzHighlight: SharedFlow<ChatBuzzHighlightEvent> =
        _chatBuzzHighlight.asSharedFlow()

    fun emitMessageReactionHighlight(conversationId: String, messageId: String) {
        _messageReactionHighlight.tryEmit(ChatReactionHighlightEvent(conversationId, messageId))
    }

    fun emitChatBuzzHighlight(conversationId: String, buzzEventId: String?) {
        _chatBuzzHighlight.tryEmit(ChatBuzzHighlightEvent(conversationId, buzzEventId))
    }

    private fun updateIntent(conversationId: String, update: (OpenIntent) -> OpenIntent) {
        if (conversationId.isEmpty()) return
        synchronized(lock) {
            pending[conversationId] = update(pending[conversationId] ?: OpenIntent())
        }
    }

    fun enqueueHighlight(conversationId: String, messageId: String) {
        if (messageId.isEmpty()) return
        updateIntent(conversationId) {
            it.copy(highlightMessageIds = it.highlightMessageIds + messageId)
        }
    }

    fun enqueueBuzz(conversationId: String, buzzEventId: String? = null) {
        updateIntent(conversationId) {
            it.copy(
                playBuzzOnOpen = true,
                buzzEventId = if (!buzzEventId.isNullOrEmpty()) buzzEventId else it.buzzEventId,
            )
        }
    }

    fun clearBuzz(conversationId: String) {
        updateIntent(conversationId) {
            it.copy(playBuzzOnOpen = false, buzzEventId = null)
        }
    }

    fun clearHighlights(conversationId: String) {
        updateIntent(conversationId) {
            it.copy(highlightMessageIds = emptySet())
        }
    }

    fun peek(conversationId: String): OpenIntent? {
        if (conversationId.isEmpty()) return null
        synchronized(lock) {
            return pending[conversationId]
        }
    }

    fun clear(conversationId: String) {
        if (conversationId.isEmpty()) return
        synchronized(lock) {
            pending.remove(conversationId)
        }
    }
}
