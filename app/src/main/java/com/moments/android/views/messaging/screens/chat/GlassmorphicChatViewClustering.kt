package com.moments.android.views.messaging.screens.chat

import com.moments.android.models.EnhancedMessage
import com.moments.android.views.messaging.core.MessageItem
import com.moments.android.views.messaging.components.ClusterMessageGrouper
import com.moments.android.views.messaging.components.ClusterMessageItem
import com.moments.android.views.messaging.services.ChatBuzzEvent
import com.moments.android.views.messaging.services.ChatBuzzProcessedStore
import java.util.Date

/** Lógica pura de `GlassmorphicChatView+Clustering.swift`; el host Compose inyecta scroll/highlight. */
class GlassmorphicChatViewClustering(
    private val messageRowId: (String) -> String?,
    private val scrollToRow: (id: String, animated: Boolean) -> Unit,
    private val highlight: (Set<String>) -> Unit,
    private val currentUserId: () -> String,
    private val buzzEvents: () -> List<ChatBuzzEvent>,
    private val pendingReplayBuzz: () -> ChatBuzzEvent?,
    private val reduceMotion: () -> Boolean,
) {
    fun clusterMessages(input: List<EnhancedMessage>): List<MessageItem> = ClusterMessageGrouper.group(input).map { item ->
        when (item) {
            is ClusterMessageItem.Single -> MessageItem.Single(item.message)
            is ClusterMessageItem.MediaCluster -> MessageItem.MediaCluster(item.messages)
        }
    }

    fun jumpToMessage(messageId: String) {
        scrollToRow(messageRowId(messageId) ?: messageId, !reduceMotion())
        highlight(setOf(messageId))
    }

    fun highlightMessages(messageIds: Set<String>, scroll: Boolean = false) {
        if (messageIds.isEmpty()) return
        if (scroll) messageIds.firstOrNull()?.let { scrollToRow(messageRowId(it) ?: it, !reduceMotion()) }
        highlight(messageIds)
    }

    fun resolvePendingBuzzEventForReplay(buzzEventId: String?, playBuzzOnOpen: Boolean): ChatBuzzEvent? {
        if (playBuzzOnOpen) {
            if (buzzEventId != null) return buzzEvents().firstOrNull { it.id == buzzEventId }
            val cutoff = Date(System.currentTimeMillis() - ChatBuzzProcessedStore.replayWindowMillis)
            return buzzEvents().filter { it.senderId != currentUserId() && it.createdAt >= cutoff }.maxByOrNull { it.createdAt }
        }
        return pendingReplayBuzz()
    }
}
