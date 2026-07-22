package com.moments.android.services.messaging

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageSyncCursor
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.concurrent.TimeUnit

import com.moments.android.views.messaging.services.ChatService

/** Port de MessageCatchUpService.swift. */
object MessageCatchUpService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastFullSyncAt: Date? = null
    private val inFlightConversationIds = mutableSetOf<String>()
    private val mutex = Mutex()
    private val fullSyncIntervalMs = TimeUnit.SECONDS.toMillis(30)
    private const val MAX_CONVERSATIONS_PER_SYNC = 20
    private const val CATCH_UP_PAGE_SIZE = 50
    private const val MAX_CATCH_UP_MESSAGES_PER_SYNC = 500

    fun syncRecent(conversations: List<com.moments.android.models.Conversation>) {
        if (!LocalFirstMessagingSettings.isEnabled) return
        if (FirebaseAuth.getInstance().currentUser == null) return

        val now = Date()
        lastFullSyncAt?.let { last ->
            if (now.time - last.time < fullSyncIntervalMs) return
        }
        lastFullSyncAt = now

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val batch = conversations
            .sortedWith(
                compareByDescending<com.moments.android.models.Conversation> { !(it.readStatus[userId] ?: true) }
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

        val acquired = mutex.withLock {
            if (conversationId in inFlightConversationIds) false
            else {
                inFlightConversationIds.add(conversationId)
                true
            }
        }
        if (!acquired) return

        try {
            var ingestedCount = 0
            val maxPages = MAX_CATCH_UP_MESSAGES_PER_SYNC / CATCH_UP_PAGE_SIZE

            repeat(maxPages) {
                if (ingestedCount >= MAX_CATCH_UP_MESSAGES_PER_SYNC) return@repeat
                val cursor = resolveCatchUpCursor(conversationId)
                val pageLimit = minOf(CATCH_UP_PAGE_SIZE, MAX_CATCH_UP_MESSAGES_PER_SYNC - ingestedCount)
                val messages = fetchCatchUpPage(conversationId, cursor, pageLimit)
                if (messages.isEmpty()) return@repeat

                MessageIngestService.ingestBatch(messages, conversationId, MessageIngestSource.CATCH_UP)
                ingestedCount += messages.size
                if (messages.size < pageLimit) return@repeat
            }
        } finally {
            mutex.withLock { inFlightConversationIds.remove(conversationId) }
        }
    }

    fun resetOnSignOut() {
        lastFullSyncAt = null
        kotlinx.coroutines.runBlocking {
            mutex.withLock { inFlightConversationIds.clear() }
        }
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
    ): List<EnhancedMessage> = if (cursor != null) {
        ChatService.fetchMessagesAfter(conversationId, cursor, limit).getOrElse { emptyList() }
    } else {
        ChatService.fetchRecentMessages(conversationId, limit).getOrElse { emptyList() }
    }

    private suspend fun preloadKeys(conversationIds: List<String>) {
        if (conversationIds.isEmpty()) return
        EncryptionService.preloadConversationKeys(conversationIds)
    }
}

enum class MessageIngestSource(val raw: String) {
    PUSH("push"),
    NOTIFICATION_EXTENSION("notificationExtension"),
    CATCH_UP("catchUp"),
    MANUAL("manual"),
}
