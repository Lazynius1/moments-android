package com.moments.android.services.messaging

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageSyncCursor
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.moments.android.views.messaging.services.ChatService

/** Port de MessageIngestService.swift. */
object MessageIngestService {
    private val mutex = Mutex()
    private val inFlightKeys = mutableSetOf<String>()
    private val recentlyIngestedKeys = mutableSetOf<String>()

    fun resetOnSignOut() {
        inFlightKeys.clear()
        recentlyIngestedKeys.clear()
        MessageIngestQueue.clear()
        MessageSyncCursorStore.clearAll()
        LocalPersistenceService.clearAllChatCache()
    }

    suspend fun drainPendingQueue() {
        if (!LocalFirstMessagingSettings.isEnabled) return
        if (FirebaseAuth.getInstance().currentUser == null) return

        val pending = MessageIngestQueue.drainAll()
        if (pending.isEmpty()) return

        val processed = mutableListOf<PendingMessageIngest>()
        for (item in pending) {
            if (ingest(item.conversationId, item.messageId, MessageIngestSource.NOTIFICATION_EXTENSION)) {
                processed.add(item)
            }
        }

        if (processed.size != pending.size) {
            val processedKeys = processed.map { "${it.conversationId}:${it.messageId}" }.toSet()
            pending.filter { "${it.conversationId}:${it.messageId}" !in processedKeys }.forEach {
                MessageIngestQueue.enqueue(it.conversationId, it.messageId)
            }
        }
    }

    suspend fun ingest(userInfo: Map<String, Any?>): Boolean {
        if (!LocalFirstMessagingSettings.isEnabled) return false
        val type = (userInfo["type"] as? String)?.trim()?.lowercase() ?: return false
        if (type != "message" && type != "new_message") return false
        val conversationId = userInfo["conversationId"] as? String ?: return false
        val messageId = userInfo["messageId"] as? String ?: return false
        return ingest(conversationId, messageId, MessageIngestSource.PUSH)
    }

    suspend fun ingestBatch(
        messages: List<EnhancedMessage>,
        conversationId: String,
        source: MessageIngestSource,
    ): Int {
        if (!LocalFirstMessagingSettings.isEnabled) return 0
        if (messages.isEmpty()) return 0

        val sorted = messages.sortedWith(compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id })
        LocalPersistenceService.saveMessagesInBackground(sorted, conversationId, sync = false)

        latestSyncCursor(sorted)?.let { latestCursor ->
            val stored = MessageSyncCursorStore.cursor(conversationId)
            val next = if (stored != null && !latestCursor.isAfter(stored)) stored else latestCursor
            MessageSyncCursorStore.updateCursor(conversationId, next)
            sorted.lastOrNull()?.let { LocalPersistenceService.upsertConversationPreview(it) }
        }

        sorted.forEach { rememberIngestedKey(dedupKey(conversationId, it.id)) }

        FirebaseAuth.getInstance().currentUser?.uid?.let { currentUserId ->
            ChatService.markMessagesAsDelivered(sorted, conversationId, currentUserId)
        }

        ChatMediaPrefetcher.prefetchIfNeeded(sorted)

        MessagingEvents.emitMessagesIngested(
            MessagesIngestedEvent(
                conversationId = conversationId,
                messageIds = sorted.map { it.id },
                source = source.raw,
            ),
        )

        return sorted.size
    }

    suspend fun ingest(
        conversationId: String,
        messageId: String,
        source: MessageIngestSource,
    ): Boolean {
        if (!LocalFirstMessagingSettings.isEnabled) return false
        if (FirebaseAuth.getInstance().currentUser == null) return false

        val conv = conversationId.trim()
        val msg = messageId.trim()
        if (conv.isEmpty() || msg.isEmpty()) return false

        val key = dedupKey(conv, msg)
        if (key in recentlyIngestedKeys) return true

        mutex.withLock {
            if (key in inFlightKeys) return false
            inFlightKeys.add(key)
        }

        try {
            if (LocalPersistenceService.messageExistsInBackground(conv, msg)) {
                rememberIngestedKey(key)
                return true
            }

            val message = ChatService.fetchMessage(conv, msg).getOrNull() ?: return false

            LocalPersistenceService.saveMessagesInBackground(listOf(message), conv, sync = false)
            LocalPersistenceService.upsertConversationPreview(message)
            ChatMediaPrefetcher.prefetchIfNeeded(listOf(message))

            FirebaseAuth.getInstance().currentUser?.uid?.let { currentUserId ->
                ChatService.markMessagesAsDelivered(listOf(message), conv, currentUserId)
            }

            MessageCatchUpService.sync(conv)

            rememberIngestedKey(key)

            ChatCommunicationNotificationService.donateFromPush(
                mapOf(
                    "type" to "new_message",
                    "conversationId" to conv,
                    "messageId" to msg,
                    "senderId" to message.senderId,
                ),
                message.content,
            )

            MessagingEvents.emitMessagesIngested(
                MessagesIngestedEvent(conv, listOf(msg), source.raw),
            )
            return true
        } finally {
            mutex.withLock { inFlightKeys.remove(key) }
        }
    }

    private fun dedupKey(conversationId: String, messageId: String) = "$conversationId:$messageId"

    private fun rememberIngestedKey(key: String) {
        if (recentlyIngestedKeys.size > 4000) recentlyIngestedKeys.clear()
        recentlyIngestedKeys.add(key)
    }

    private fun latestSyncCursor(messages: List<EnhancedMessage>): MessageSyncCursor? {
        var latest: MessageSyncCursor? = null
        for (message in messages) {
            val candidate = MessageSyncCursor(message.timestamp, message.id)
            latest = when {
                latest == null -> candidate
                candidate.isAfter(latest) -> candidate
                else -> latest
            }
        }
        return latest
    }
}
