package com.moments.android.services.messaging

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Equivalente a LocalFirstMessagingSettings.swift + Notification.Name.messagesIngested. */
object LocalFirstMessagingSettings {
    private const val PREFS = "moments_messaging_settings"
    private const val KEY = "useLocalFirstMessaging"

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() =
        (appContext ?: error("LocalFirstMessagingSettings.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Por defecto `true`, igual que iOS cuando la clave no existe. */
    var isEnabled: Boolean
        get() = prefs().let { if (!it.contains(KEY)) true else it.getBoolean(KEY, true) }
        set(value) { prefs().edit().putBoolean(KEY, value).apply() }
}

data class MessagesIngestedEvent(
    val conversationId: String,
    val messageIds: List<String>,
    val source: String,
)

data class ChatReactionHighlightEvent(val conversationId: String, val messageId: String)
data class ChatBuzzHighlightEvent(val conversationId: String, val buzzEventId: String?)

object MessagingEvents {
    private val _messagesIngested = MutableSharedFlow<MessagesIngestedEvent>(extraBufferCapacity = 64)
    val messagesIngested: SharedFlow<MessagesIngestedEvent> = _messagesIngested.asSharedFlow()

    private val _messageReactionHighlight = MutableSharedFlow<ChatReactionHighlightEvent>(extraBufferCapacity = 16)
    val messageReactionHighlight: SharedFlow<ChatReactionHighlightEvent> = _messageReactionHighlight.asSharedFlow()

    private val _chatBuzzHighlight = MutableSharedFlow<ChatBuzzHighlightEvent>(extraBufferCapacity = 16)
    val chatBuzzHighlight: SharedFlow<ChatBuzzHighlightEvent> = _chatBuzzHighlight.asSharedFlow()

    internal fun emitMessagesIngested(event: MessagesIngestedEvent) {
        _messagesIngested.tryEmit(event)
    }

    fun emitMessageReactionHighlight(conversationId: String, messageId: String) {
        _messageReactionHighlight.tryEmit(ChatReactionHighlightEvent(conversationId, messageId))
    }

    fun emitChatBuzzHighlight(conversationId: String, buzzEventId: String?) {
        _chatBuzzHighlight.tryEmit(ChatBuzzHighlightEvent(conversationId, buzzEventId))
    }
}
