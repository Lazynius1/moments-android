package com.moments.android.services.persistence

import android.content.Context
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageSyncCursor
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.core.decodeMessages
import com.moments.android.views.messaging.core.encodeMessages
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.persistence.room.MessageEntity
import com.moments.android.services.persistence.room.MomentsRoomStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * Port de MessagePersistenceStore.swift (@ModelActor).
 * Core: save/reconcile/recent/before/after/all/contains/lastCursor + merge/trim.
 * Extras (search, mutations, cleanup) = helpers que en iOS viven en LocalPersistenceService
 * y aquí se apoyan en el mismo almacén JSON por conversación.
 */
object MessagePersistenceStore {
    private const val MAX_MESSAGES_PER_CONVERSATION = 2_000
    private const val SQL_IN_CHUNK = 500
    private val lock = Mutex()

    fun initialize(context: Context) {
        MomentsRoomStore.initialize(context)
    }

    suspend fun save(
        encodedMessages: ByteArray,
        conversationId: String,
        sync: Boolean,
    ) = withContext(Dispatchers.IO) {
        lockedIo {
            val messages = decodeMessages(encodedMessages)
            if (messages.isEmpty() && !sync) return@lockedIo

            if (sync) {
                writeMessagesUnsafe(conversationId, mergeMessages(emptyList(), messages, sync = true))
            } else {
                // Upsert por fila: solo se leen y escriben las filas del lote (antes se reescribía
                // la conversación completa, hasta 2000 filas, en cada snapshot).
                val existing = loadMessagesByIdsUnsafe(conversationId, messages.map { it.id })
                upsertMessagesUnsafe(conversationId, mergeMessages(existing, messages, sync = false))
            }
            trimMessagesUnsafe(conversationId)
        }
    }

    suspend fun reconcile(encodedMessages: ByteArray, conversationId: String) = withContext(Dispatchers.IO) {
        val messages = decodeMessages(encodedMessages)
        if (messages.isEmpty()) return@withContext
        save(encodedMessages, conversationId, sync = false)

        val oldest = messages.minOf { it.timestamp }
        val remoteIds = messages.map { it.id }.toSet()
        lockedIo {
            // Dentro de la ventana del snapshot, lo que no viene se borró en remoto. Excepción:
            // salientes aún no confirmados (pending/failed/sending) que el servidor todavía no tiene.
            val removedIds = room { messagesFrom(conversationId, oldest.time) }
                .asSequence()
                .filter { it.id !in remoteIds }
                .mapNotNull(::decodeMessageEntity)
                .filterNot(::isUnconfirmedOutgoing)
                .map { it.id }
                .toList()
            deleteMessagesByIdsUnsafe(conversationId, removedIds)
        }
    }

    private fun isUnconfirmedOutgoing(message: EnhancedMessage): Boolean =
        message.status == com.moments.android.views.messaging.core.MessageStatus.PENDING ||
            message.status == com.moments.android.views.messaging.core.MessageStatus.FAILED ||
            message.status == com.moments.android.views.messaging.core.MessageStatus.SENDING

    suspend fun recentMessages(
        conversationId: String,
        limit: Int,
        cutoffDate: Date?,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext ByteArray(0)
        lockedIo {
            val cached = room {
                recentMessagesIn(conversationId, cutoffDate?.time, limit).mapNotNull(::decodeMessageEntity)
            }
            encodeMessages(cached.reversed())
        }
    }

    suspend fun messagesBefore(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date?,
        limit: Int,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext ByteArray(0)
        lockedIo {
            val filtered = room {
                messagesBefore(
                    conversationId,
                    cursor.timestamp.time,
                    cursor.messageId,
                    cutoffDate?.time,
                    limit,
                ).mapNotNull(::decodeMessageEntity)
            }
            encodeMessages(filtered.reversed())
        }
    }

    suspend fun messagesAfter(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date?,
        limit: Int,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext ByteArray(0)
        lockedIo {
            val filtered = room {
                messagesAfter(
                    conversationId,
                    cursor.timestamp.time,
                    cursor.messageId,
                    cutoffDate?.time,
                    limit,
                ).mapNotNull(::decodeMessageEntity)
            }
            encodeMessages(filtered)
        }
    }

    suspend fun allMessages(conversationId: String): ByteArray = withContext(Dispatchers.IO) {
        lockedIo {
            encodeMessages(
                loadMessagesUnsafe(conversationId)
                    .sortedWith(compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id }),
            )
        }
    }

    suspend fun containsMessage(conversationId: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            lockedIo {
                room { containsMessage(conversationId, messageId) }
            }
        }

    suspend fun lastCursor(conversationId: String): MessageSyncCursor? = withContext(Dispatchers.IO) {
        lockedIo {
            room { latestMessageIn(conversationId) }
                ?.let { MessageSyncCursor(Date(it.timestamp), it.id) }
        }
    }

    suspend fun cachedMessageCount(): Int = lockedIo {
        room { allMessages().size }
    }

    suspend fun cachedMessageKeys(since: Date): Set<String> = lockedIo {
        room {
            allMessages()
                .filter { it.timestamp >= since.time }
                .mapTo(mutableSetOf()) { "${it.conversationId}:${it.id}" }
        }
    }

    suspend fun clearAll() = lockedIo {
        room { deleteAllMessages() }
        UnreadMessageCountStore.invalidateAll()
    }

    suspend fun updateMessageStatus(conversationId: String, messageId: String, status: String) = lockedIo {
        mutateRowsUnsafe(conversationId, listOf(messageId)) { msg ->
            msg.copy(status = com.moments.android.views.messaging.core.MessageStatus.from(status))
        }
    }

    suspend fun markMessagesAsRead(conversationId: String, messageIds: Set<String>) = lockedIo {
        if (messageIds.isEmpty()) return@lockedIo
        mutateRowsUnsafe(conversationId, messageIds.toList()) { msg ->
            if (!msg.isRead) msg.copy(isRead = true) else msg
        }
    }

    suspend fun markAllIncomingAsRead(conversationId: String, currentUserId: String) = lockedIo {
        val changed = loadMessagesUnsafe(conversationId)
            .filter { msg -> msg.senderId != currentUserId && !msg.isRead }
            .map { it.copy(isRead = true) }
        upsertMessagesUnsafe(conversationId, changed)
    }

    suspend fun deleteConversation(conversationId: String) = lockedIo {
        val messageIds = loadMessagesUnsafe(conversationId).map { it.id }
        room { deleteMessagesIn(conversationId) }
        messageIds.forEach { ChatCacheStore.deleteMessageFiles(conversationId, it) }
        UnreadMessageCountStore.invalidate(conversationId)
    }

    suspend fun markMessageDeletedForEveryone(conversationId: String, messageId: String) = lockedIo {
        mutateRowsUnsafe(conversationId, listOf(messageId)) { msg ->
            msg.copy(
                isDeleted = true,
                deletedAt = Date(),
                content = null,
                mediaUrl = null,
                thumbnailUrl = null,
                mediaObjectPath = null,
                thumbnailObjectPath = null,
                mediaEncryption = null,
                thumbnailEncryption = null,
                audioWaveform = null,
            )
        }
        ChatCacheStore.deleteMessageFiles(conversationId, messageId)
    }

    suspend fun removeCachedMessage(conversationId: String, messageId: String) = lockedIo {
        ChatCacheStore.deleteMessageFiles(conversationId, messageId)
        deleteMessagesByIdsUnsafe(conversationId, listOf(messageId))
    }

    suspend fun unreadMessageCount(
        conversationId: String,
        currentUserId: String,
        lastReadAt: Date? = null,
    ): Int = lockedIo {
        room { unreadCount(conversationId, currentUserId, lastReadAt?.time) }
    }

    suspend fun updateMessageVanishExpiresAt(conversationId: String, messageId: String, expiresAt: Date) = lockedIo {
        mutateRowsUnsafe(conversationId, listOf(messageId)) { it.copy(vanishExpiresAt = expiresAt) }
    }

    suspend fun updateMessageNoticeContent(conversationId: String, messageId: String, content: String) = lockedIo {
        mutateRowsUnsafe(conversationId, listOf(messageId)) { it.copy(content = content) }
    }

    suspend fun toggleMessageReactionLocally(messageId: String, emoji: String, userId: String): Boolean = lockedIo {
        for (conversationId in allConversationIdsUnsafe()) {
            val messages = loadMessagesUnsafe(conversationId)
            val index = messages.indexOfFirst { it.id == messageId }
            if (index < 0) continue
            val message = messages[index]
            val updatedReactions = applyMessageReactionMutation(message.reactions, emoji, userId)
            val updated = messages.toMutableList()
            updated[index] = message.copy(reactions = updatedReactions)
            writeMessagesUnsafe(conversationId, updated)
            return@lockedIo true
        }
        false
    }

    suspend fun markVanishMessagesDismissed(conversationId: String, messageIds: Set<String>, userId: String) = lockedIo {
        if (messageIds.isEmpty()) return@lockedIo
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id !in messageIds || !msg.isVanishModeMessage || userId in msg.vanishedFor) msg
            else msg.copy(vanishedFor = msg.vanishedFor + userId)
        }
        writeMessagesUnsafe(conversationId, messages)
    }

    suspend fun searchMessageIds(conversationId: String, query: String, limit: Int = 100): List<String> = lockedIo {
        if (limit <= 0) return@lockedIo emptyList()
        val normalizedQuery = SearchNormalization.normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return@lockedIo emptyList()
        val matches = mutableListOf<String>()
        for (message in loadMessagesUnsafe(conversationId).sortedBy { it.timestamp }) {
            if (message.type != MessageType.TEXT || message.isUndecryptable) continue
            if (!SearchNormalization.containsNormalized(message.content.orEmpty(), normalizedQuery)) continue
            matches += message.id
            if (matches.size >= limit) break
        }
        matches
    }

    suspend fun searchMessagesGlobally(query: String, limit: Int = 50): List<EnhancedMessage> = lockedIo {
        if (limit <= 0) return@lockedIo emptyList()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@lockedIo emptyList()
        val normalizedQuery = SearchNormalization.normalizeForSearch(trimmedQuery)
        if (normalizedQuery.isEmpty()) return@lockedIo emptyList()
        val matches = mutableListOf<EnhancedMessage>()
        for (conversationId in allConversationIdsUnsafe()) {
            for (message in loadMessagesUnsafe(conversationId)) {
                if (message.type != MessageType.TEXT || message.isDeleted || message.isVanishModeMessage || message.isUndecryptable) continue
                val content = message.content ?: continue
                if (!SearchNormalization.containsNormalized(content, normalizedQuery)) continue
                matches += message
            }
        }
        matches.sortedWith(compareByDescending<EnhancedMessage> { it.timestamp }.thenByDescending { it.id })
            .take(limit)
    }

    suspend fun allConversationIds(): Set<String> = lockedIo {
        room { conversationIdsWithMessages().toSet() }
    }

    suspend fun cleanupOldChats(
        cutoffDate: Date,
        staleThresholdDate: Date,
        recentWindow: Int,
        staleWindow: Int,
    ) = lockedIo {
        for (conversationId in allConversationIdsUnsafe()) {
            val latestTimestamp = room { latestMessageTimestampIn(conversationId) }
            val isStale = latestTimestamp?.let { it < staleThresholdDate.time } ?: true
            val keepCount = if (isStale) staleWindow else recentWindow
            val removedIds = room {
                staleMessageIdsOutsideRecentWindow(
                    conversationId = conversationId,
                    cutoff = cutoffDate.time,
                    keepCount = keepCount,
                )
            }
            if (removedIds.isEmpty()) continue
            room {
                deleteStaleMessagesOutsideRecentWindow(
                    conversationId = conversationId,
                    cutoff = cutoffDate.time,
                    keepCount = keepCount,
                )
            }
            removedIds.forEach { ChatCacheStore.deleteMessageFiles(conversationId, it) }
            UnreadMessageCountStore.invalidate(conversationId)
        }
    }

    private suspend fun allConversationIdsUnsafe(): Set<String> {
        return room { conversationIdsWithMessages().toSet() }
    }

    private fun applyMessageReactionMutation(
        reactions: Map<String, List<String>>?,
        emoji: String,
        userId: String,
    ): Map<String, List<String>>? {
        val map = reactions?.mapValues { (_, v) -> v.toMutableList() }?.toMutableMap() ?: mutableMapOf()
        val alreadyHasEmoji = map[emoji]?.contains(userId) == true
        if (alreadyHasEmoji) {
            val users = map[emoji]?.toMutableList() ?: mutableListOf()
            users.remove(userId)
            if (users.isEmpty()) map.remove(emoji) else map[emoji] = users
        } else {
            for (key in map.keys.toList()) {
                val users = map[key]?.toMutableList() ?: continue
                users.remove(userId)
                if (users.isEmpty()) map.remove(key) else map[key] = users
            }
            map[emoji] = (map[emoji]?.toMutableList() ?: mutableListOf()).apply { add(userId) }
        }
        return map.takeIf { it.isNotEmpty() }?.mapValues { (_, v) -> v.toList() }
    }

    private fun parseReactionsFromJson(obj: JSONObject): Map<String, List<String>>? {
        val reactionsObj = obj.optJSONObject("reactions") ?: return null
        val map = mutableMapOf<String, List<String>>()
        for (key in reactionsObj.keys()) {
            val arr = reactionsObj.optJSONArray(key) ?: continue
            map[key] = (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        }
        return map.takeIf { it.isNotEmpty() }
    }

    private fun mergeMessages(
        existing: List<EnhancedMessage>,
        incoming: List<EnhancedMessage>,
        sync: Boolean,
    ): List<EnhancedMessage> {
        if (sync) return incoming
        val byId = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { new ->
            val current = byId[new.id]
            byId[new.id] = if (current != null) mergeCached(new, current) else new
        }
        return byId.values.toList()
    }

    private fun mergeCached(new: EnhancedMessage, existing: EnhancedMessage): EnhancedMessage {
        // ≡ iOS MessagePersistenceStore.merge(_:into:)
        if (new.isDeleted) {
            return new.copy(
                content = null,
                mediaUrl = null,
                thumbnailUrl = null,
                mediaObjectPath = null,
                thumbnailObjectPath = null,
                mediaEncryption = null,
                thumbnailEncryption = null,
                audioWaveform = null,
                isRead = existing.isRead || new.isRead,
                readBy = new.readBy ?: existing.readBy,
                readAtBy = (existing.readAtBy.orEmpty() + new.readAtBy.orEmpty()).takeIf { it.isNotEmpty() },
                vanishedFor = (existing.vanishedFor + new.vanishedFor).distinct(),
                vanishExpiresAt = new.vanishExpiresAt ?: existing.vanishExpiresAt,
            )
        }
        return new.copy(
            mediaUrl = existing.mediaUrl?.takeIf { isLocalFilePresent(it) } ?: new.mediaUrl,
            thumbnailUrl = existing.thumbnailUrl?.takeIf { isLocalFilePresent(it) } ?: new.thumbnailUrl,
            isRead = existing.isRead || new.isRead,
            readBy = new.readBy ?: existing.readBy,
            readAtBy = (existing.readAtBy.orEmpty() + new.readAtBy.orEmpty()).takeIf { it.isNotEmpty() },
            vanishedFor = (existing.vanishedFor + new.vanishedFor).distinct(),
            vanishExpiresAt = new.vanishExpiresAt ?: existing.vanishExpiresAt,
            isLiveLocation = new.isLiveLocation ?: existing.isLiveLocation,
            liveLocationExpiresAt = new.liveLocationExpiresAt ?: existing.liveLocationExpiresAt,
            liveLocationDuration = new.liveLocationDuration ?: existing.liveLocationDuration,
            liveLocationStoppedAt = new.liveLocationStoppedAt ?: existing.liveLocationStoppedAt,
            liveLocationSessionId = new.liveLocationSessionId ?: existing.liveLocationSessionId,
            locationUpdatedAt = new.locationUpdatedAt ?: existing.locationUpdatedAt,
            locationName = new.locationName ?: existing.locationName,
            locationAddress = new.locationAddress ?: existing.locationAddress,
        )
    }

    /** ≡ iOS `shouldPreserveLocalMediaURL` — solo file:// que exista en disco. */
    private fun isLocalFilePresent(urlString: String?): Boolean {
        if (urlString.isNullOrEmpty()) return false
        if (!urlString.startsWith("file://")) return false
        val path = android.net.Uri.parse(urlString).path ?: return false
        return File(path).exists()
    }

    /** Recorta a [MAX_MESSAGES_PER_CONVERSATION] en SQL (mismo orden: timestamp/id desc). */
    private suspend fun trimMessagesUnsafe(conversationId: String) {
        if (room { messageCountIn(conversationId) } <= MAX_MESSAGES_PER_CONVERSATION) return
        val overflowIds = room {
            staleMessageIdsOutsideRecentWindow(conversationId, Long.MAX_VALUE, MAX_MESSAGES_PER_CONVERSATION)
        }
        deleteMessagesByIdsUnsafe(conversationId, overflowIds)
        overflowIds.forEach { ChatCacheStore.deleteMessageFiles(conversationId, it) }
    }

    private suspend fun loadMessagesUnsafe(conversationId: String): List<EnhancedMessage> =
        room {
            messagesIn(conversationId).mapNotNull(::decodeMessageEntity)
        }

    private suspend fun loadMessagesByIdsUnsafe(conversationId: String, ids: List<String>): List<EnhancedMessage> {
        val unique = ids.distinct()
        if (unique.isEmpty()) return emptyList()
        // SQLite limita las variables por sentencia: trocear.
        return unique.chunked(SQL_IN_CHUNK).flatMap { chunk ->
            room { messagesByIds(conversationId, chunk) }.mapNotNull(::decodeMessageEntity)
        }
    }

    private suspend fun deleteMessagesByIdsUnsafe(conversationId: String, ids: List<String>) {
        if (ids.isEmpty()) return
        ids.distinct().chunked(SQL_IN_CHUNK).forEach { chunk -> room { deleteMessagesByIds(conversationId, chunk) } }
        UnreadMessageCountStore.invalidate(conversationId)
    }

    /** Lee solo [ids], aplica [transform] y hace upsert de esas filas. */
    private suspend fun mutateRowsUnsafe(
        conversationId: String,
        ids: List<String>,
        transform: (EnhancedMessage) -> EnhancedMessage,
    ) {
        val rows = loadMessagesByIdsUnsafe(conversationId, ids)
        if (rows.isEmpty()) return
        upsertMessagesUnsafe(conversationId, rows.map(transform))
    }

    private suspend fun upsertMessagesUnsafe(conversationId: String, messages: List<EnhancedMessage>) {
        if (messages.isEmpty()) return
        room { upsertMessages(messages.map { it.toEntity(conversationId) }) }
        UnreadMessageCountStore.invalidate(conversationId)
    }

    /** Reemplazo completo de la conversación (sync=true y mutaciones globales). */
    private suspend fun writeMessagesUnsafe(conversationId: String, messages: List<EnhancedMessage>) {
        room { replaceMessagesIn(conversationId, messages.map { it.toEntity(conversationId) }) }
        UnreadMessageCountStore.invalidate(conversationId)
    }

    private fun EnhancedMessage.toEntity(conversationId: String) = MessageEntity(
        conversationId = conversationId,
        id = id,
        timestamp = timestamp.time,
        senderId = senderId,
        type = type.raw,
        // No indexar el aviso de "no descifrable" como contenido buscable.
        content = if (isUndecryptable) null else content,
        isRead = isRead,
        isDeleted = isDeleted,
        isVanishModeMessage = isVanishModeMessage,
        payload = toJson().toString(),
    )

    private fun decodeMessageEntity(entity: MessageEntity): EnhancedMessage? = runCatching {
        val obj = JSONObject(entity.payload)
        EnhancedMessage.fromJson(obj).copy(reactions = parseReactionsFromJson(obj))
    }.getOrNull()

    private suspend fun <T> lockedIo(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { block() }
    }

    private suspend fun <T> room(
        block: suspend com.moments.android.services.persistence.room.MomentsDao.() -> T,
    ): T = MomentsRoomStore.dao().block()
}
