package com.moments.android.services.messaging

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageSyncCursor
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/** Port de `MessageCatchUpService.swift`. */
object MessageCatchUpService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastFullSyncAt: Date? = null
    private val inFlightConversationIds = ConcurrentHashMap.newKeySet<String>()
    private const val FULL_SYNC_INTERVAL_MS = 30_000L
    private const val MAX_CONVERSATIONS_PER_SYNC = 20
    private const val CATCH_UP_PAGE_SIZE = 50
    /** Máximo de mensajes ingeridos por conversación y pasada de sync. */
    private const val MAX_CATCH_UP_MESSAGES_PER_SYNC = 500

    fun syncRecent(conversations: List<Conversation>) {
        if (!LocalFirstMessagingSettings.isEnabled) return
        if (FirebaseAuth.getInstance().currentUser == null) return

        val now = Date()
        lastFullSyncAt?.let { last ->
            if (now.time - last.time < FULL_SYNC_INTERVAL_MS) return
        }
        lastFullSyncAt = now

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val batch = conversations
            .sortedWith(
                compareByDescending<Conversation> { !(it.readStatus[userId] ?: true) }
                    .thenByDescending { it.timestamp },
            )
            .take(MAX_CONVERSATIONS_PER_SYNC)

        scope.launch {
            preloadKeys(batch.mapNotNull { it.id })
            coroutineScope {
                batch.mapNotNull { it.id }.map { conversationId ->
                    async { sync(conversationId) }
                }.awaitAll()
            }
        }
    }

    suspend fun sync(conversationId: String) {
        if (!LocalFirstMessagingSettings.isEnabled) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        if (conversationId.isEmpty()) return
        if (!inFlightConversationIds.add(conversationId)) return

        try {
            var ingestedCount = 0
            val maxPages = MAX_CATCH_UP_MESSAGES_PER_SYNC / CATCH_UP_PAGE_SIZE

            for (_page in 0 until maxPages) {
                if (ingestedCount >= MAX_CATCH_UP_MESSAGES_PER_SYNC) break

                val cursor = resolveCatchUpCursor(conversationId)
                val pageLimit = minOf(CATCH_UP_PAGE_SIZE, MAX_CATCH_UP_MESSAGES_PER_SYNC - ingestedCount)
                val messages = fetchCatchUpPage(conversationId, cursor, pageLimit)
                if (messages.isEmpty()) break

                MessageIngestService.ingestBatch(messages, conversationId, MessageIngestSource.CATCH_UP)
                ingestedCount += messages.size

                if (messages.size < pageLimit) break
            }
        } finally {
            inFlightConversationIds.remove(conversationId)
        }
    }

    fun resetOnSignOut() {
        lastFullSyncAt = null
        inFlightConversationIds.clear()
    }

    private suspend fun resolveCatchUpCursor(conversationId: String): MessageSyncCursor? {
        MessageSyncCursorStore.cursor(conversationId)?.takeIf { it.messageId.isNotEmpty() }?.let { return it }
        LocalPersistenceService.lastMessageSyncCursorInBackground(conversationId)?.let { return it }
        return MessageSyncCursorStore.cursor(conversationId)
    }

    private suspend fun fetchCatchUpPage(
        conversationId: String,
        cursor: MessageSyncCursor?,
        limit: Int,
    ): List<EnhancedMessage> {
        if (cursor != null) {
            return fetchMessagesAfter(conversationId, cursor, limit)
        }
        return fetchRecentMessages(conversationId, limit)
    }

    private suspend fun fetchRecentMessages(conversationId: String, limit: Int): List<EnhancedMessage> =
        ChatService.fetchRecentMessages(conversationId, limit).getOrElse { emptyList() }

    private suspend fun fetchMessagesAfter(
        conversationId: String,
        after: MessageSyncCursor,
        limit: Int,
    ): List<EnhancedMessage> =
        ChatService.fetchMessagesAfter(conversationId, after, limit).getOrElse { emptyList() }

    private suspend fun preloadKeys(conversationIds: List<String>) {
        if (conversationIds.isEmpty()) return
        EncryptionService.preloadConversationKeys(conversationIds)
    }
}
