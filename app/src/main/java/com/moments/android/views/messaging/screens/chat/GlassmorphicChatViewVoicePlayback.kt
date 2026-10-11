package com.moments.android.views.messaging.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.moments.android.views.messaging.components.ChatVoiceConversation
import com.moments.android.views.messaging.components.ChatVoicePlaybackController
import com.moments.android.views.messaging.components.ChatVoiceQueueItem
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType

/**
 * ≡ iOS `configureVoicePlayback` / `tearDownVoicePlayback`: conecta el reproductor de notas
 * de voz con este chat. Al salir la nota sigue sonando y su barra pasa a la lista de chats;
 * la cola sigue con las notas ya cargadas. Al entrar en otro chat se para.
 */
@Composable
internal fun BindChatVoicePlayback(
    session: EnhancedChatViewModel,
    conversation: ChatVoiceConversation,
    senderName: (String?) -> String,
) {
    DisposableEffect(session, conversation) {
        val playback = ChatVoicePlaybackController
        playback.attach(conversation)
        playback.senderNameProvider = { senderName(it) }
        playback.nextVoiceNoteProvider = { messageId ->
            nextVoiceNoteMessage(session.messages.value, messageId)?.let { next ->
                next.mediaUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    ChatVoiceQueueItem(
                        messageId = next.id,
                        audioUrl = url,
                        duration = next.duration ?: 0.0,
                        senderId = next.senderId,
                    )
                }
            }
        }
        // Mientras suena una nota, descifra/descarga la siguiente para que empiece sin espera
        // aunque su burbuja no esté en pantalla.
        playback.onActiveMessageChanged = { messageId ->
            nextVoiceNoteMessage(session.messages.value, messageId)?.let(session::hydrateMediaIfNeeded)
        }
        onDispose {
            playback.detach(conversation.id)
        }
    }
}

/** Siguiente mensaje del hilo si es una nota de voz ya enviada (sin nada entre medias). */
internal fun nextVoiceNoteMessage(messages: List<EnhancedMessage>, messageId: String): EnhancedMessage? {
    val ordered = messages
        .filter { !it.isDeleted }
        .sortedWith(compareBy<EnhancedMessage>({ it.timestamp }, { it.id }))
    val index = ordered.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    val next = ordered.getOrNull(index + 1) ?: return null
    if (next.type != MessageType.AUDIO) return null
    if (next.status == MessageStatus.SENDING || next.status == MessageStatus.FAILED) return null
    return next
}
