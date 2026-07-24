package com.moments.android.views.messaging.core

import com.moments.android.models.EnhancedMessage
import com.moments.android.models.PendingChatContext
import com.moments.android.models.PendingChatTimelineMessage
import com.moments.android.views.messaging.services.ChatBuzzEvent
import java.util.Date

/** Port de `MessageItem.swift`: unidad renderizable de mensaje o álbum. */
sealed interface MessageItem {
    val id: String

    data class Single(val message: EnhancedMessage) : MessageItem {
        override val id: String get() = message.id
    }

    data class MediaCluster(val messages: List<EnhancedMessage>) : MessageItem {
        // El último conserva la identidad visible cuando se amplía el álbum por arriba.
        override val id: String get() = "cluster-" + (messages.lastOrNull()?.id ?: "empty")
    }
}

/** Fila renderizable: contenido de conversación y filas sintéticas del timeline. */
sealed interface ChatRenderRow {
    val id: String

    data class ConversationIntro(val context: PendingChatContext?) : ChatRenderRow {
        override val id: String get() = "row:synthetic:conversation-intro:${context?.id ?: "normal"}"
    }

    data class RequestDisclaimer(val context: PendingChatContext?) : ChatRenderRow {
        override val id: String get() = "row:synthetic:request-disclaimer:${context?.id ?: "normal"}"
    }

    data class PendingRequestMessage(val message: PendingChatTimelineMessage) : ChatRenderRow {
        override val id: String get() = "row:pending-request:${message.id}"
    }

    data class Header(val date: Date) : ChatRenderRow {
        override val id: String get() = "row:header:${date.time}"
    }

    data class Message(val item: MessageItem) : ChatRenderRow {
        override val id: String get() = "row:message:${item.id}"
    }

    data class Buzz(val event: ChatBuzzEvent) : ChatRenderRow {
        override val id: String get() = "row:buzz:${event.id}"
    }

    data object Typing : ChatRenderRow {
        override val id: String = "row:synthetic:typing-indicator"
    }

    data object HistoryStart : ChatRenderRow {
        override val id: String = "row:synthetic:history-start"
    }
}

data class ChatTimelineSection(
    val date: Date,
    val rows: List<ChatRenderRow>,
) {
    val id: String get() = "section-${date.time}"
}
