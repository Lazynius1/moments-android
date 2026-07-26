package com.moments.android.services.messaging

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Port de `LocalFirstMessagingSettings.swift`.
 * Clave `useLocalFirstMessaging`: si no existe → `true` (igual que iOS).
 * `MessagingEvents.messagesIngested` ≡ `Notification.Name.messagesIngested`.
 *
 * Reaction/buzz highlights viven en `ChatNavigationIntentStore` (como en iOS).
 */
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

    /** Get-only como iOS; por defecto `true` cuando la clave no existe. */
    val isEnabled: Boolean
        get() = prefs().let { if (!it.contains(KEY)) true else it.getBoolean(KEY, true) }
}

/** Payload de `Notification.Name.messagesIngested` (`userInfo` en iOS). */
data class MessagesIngestedEvent(
    val conversationId: String,
    val messageIds: List<String>,
    val source: String,
)

/** Bus de `messagesIngested` (misma extensión Notification.Name que en el .swift). */
object MessagingEvents {
    private val _messagesIngested = MutableSharedFlow<MessagesIngestedEvent>(extraBufferCapacity = 64)
    val messagesIngested: SharedFlow<MessagesIngestedEvent> = _messagesIngested.asSharedFlow()

    internal fun emitMessagesIngested(event: MessagesIngestedEvent) {
        _messagesIngested.tryEmit(event)
    }
}
