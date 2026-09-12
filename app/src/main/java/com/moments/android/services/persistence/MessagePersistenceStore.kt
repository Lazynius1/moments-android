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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Port de MessagePersistenceStore.swift (@ModelActor).
 * Core: save/reconcile/recent/before/after/all/contains/lastCursor + merge/trim.
 * Extras (search, mutations, cleanup) = helpers que en iOS viven en LocalPersistenceService
 * y aquí se apoyan en el mismo almacén JSON por conversación.
 */
object MessagePersistenceStore {
    private const val MAX_MESSAGES_PER_CONVERSATION = 2_000
    private val lock = ReentrantReadWriteLock()

    fun initialize(context: Context) {
        MomentsRoomStore.initialize(context)
    }

    suspend fun save(
        encodedMessages: ByteArray,
        conversationId: String,
        sync: Boolean,
    ) = withContext(Dispatchers.IO) {
        lock.write {
            val messages = decodeMessages(encodedMessages)
            if (messages.isEmpty() && !sync) return@withContext

            val existing = if (sync) emptyList() else loadMessagesUnsafe(conversationId)
            val merged = mergeMessages(existing, messages, sync)
            writeMessagesUnsafe(conversationId, merged)
            trimMessagesUnsafe(conversationId, merged)
        }
    }

    suspend fun reconcile(encodedMessages: ByteArray, conversationId: String) = withContext(Dispatchers.IO) {
        val messages = decodeMessages(encodedMessages)
        if (messages.isEmpty()) return@withContext
        save(encodedMessages, conversationId, sync = false)

        val oldest = messages.minOf { it.timestamp }
        val remoteIds = messages.map { it.id }.toSet()
        lock.write {
            val kept = loadMessagesUnsafe(conversationId).filter { msg ->
                msg.timestamp < oldest || msg.id in remoteIds
            }
            writeMessagesUnsafe(conversationId, kept)
        }
    }

    suspend fun recentMessages(
        conversationId: String,
        limit: Int,
        cutoffDate: Date?,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext ByteArray(0)
        lock.read {
            val cached = blockingRoom {
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
        lock.read {
            val filtered = blockingRoom {
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
        lock.read {
            val filtered = blockingRoom {
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
        lock.read {
            encodeMessages(
                loadMessagesUnsafe(conversationId)
                    .sortedWith(compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id }),
            )
        }
    }

    suspend fun containsMessage(conversationId: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            lock.read {
                blockingRoom { containsMessage(conversationId, messageId) }
            }
        }

    suspend fun lastCursor(conversationId: String): MessageSyncCursor? = withContext(Dispatchers.IO) {
        lock.read {
            blockingRoom { latestMessageIn(conversationId) }
                ?.let { MessageSyncCursor(Date(it.timestamp), it.id) }
        }
    }

    fun cachedMessageCount(): Int = lock.read {
        blockingRoom { allMessages().size }
    }

    fun cachedMessageKeys(since: Date): Set<String> = lock.read {
        blockingRoom {
            allMessages()
                .filter { it.timestamp >= since.time }
                .mapTo(mutableSetOf()) { "${it.conversationId}:${it.id}" }
        }
    }

    fun clearAll() = lock.write {
        blockingRoom { deleteAllMessages() }
    }

    fun updateMessageStatus(conversationId: String, messageId: String, status: String) = lock.write {
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id == messageId) msg.copy(status = com.moments.android.views.messaging.core.MessageStatus.from(status)) else msg
        }
        writeMessagesUnsafe(conversationId, messages)
    }

    fun markMessagesAsRead(conversationId: String, messageIds: Set<String>) = lock.write {
        if (messageIds.isEmpty()) return@write
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id in messageIds && !msg.isRead) msg.copy(isRead = true) else msg
        }
        writeMessagesUnsafe(conversationId, messages)
    }

    fun markAllIncomingAsRead(conversationId: String, currentUserId: String) = lock.write {
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.senderId != currentUserId && !msg.isRead) msg.copy(isRead = true) else msg
        }
        writeMessagesUnsafe(conversationId, messages)
    }

    fun deleteConversation(conversationId: String) = lock.write {
        val messageIds = loadMessagesUnsafe(conversationId).map { it.id }
        blockingRoom { deleteMessagesIn(conversationId) }
        messageIds.forEach { ChatCacheStore.deleteMessageFiles(conversationId, it) }
    }

    fun markMessageDeletedForEveryone(conversationId: String, messageId: String) = lock.write {
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id != messageId) msg
            else msg.copy(
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
        writeMessagesUnsafe(conversationId, messages)
        ChatCacheStore.deleteMessageFiles(conversationId, messageId)
    }

    fun removeCachedMessage(conversationId: String, messageId: String) = lock.write {
        ChatCacheStore.deleteMessageFiles(conversationId, messageId)
        val kept = loadMessagesUnsafe(conversationId).filter { it.id != messageId }
        writeMessagesUnsafe(conversationId, kept)
    }

    fun unreadMessageCount(
        conversationId: String,
        currentUserId: String,
        lastReadAt: Date? = null,
    ): Int = lock.read {
        blockingRoom { unreadCount(conversationId, currentUserId, lastReadAt?.time) }
    }

    fun updateMessageVanishExpiresAt(conversationId: String, messageId: String, expiresAt: Date) = lock.write {
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id == messageId) msg.copy(vanishExpiresAt = expiresAt) else msg
        }
        writeMessagesUnsafe(conversationId, messages)
    }

    fun updateMessageNoticeContent(conversationId: String, messageId: String, content: String) = lock.write {
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id == messageId) msg.copy(content = content) else msg
        }
        writeMessagesUnsafe(conversationId, messages)
    }

    fun toggleMessageReactionLocally(messageId: String, emoji: String, userId: String): Boolean = lock.write {
        for (conversationId in allConversationIdsUnsafe()) {
            val messages = loadMessagesUnsafe(conversationId)
            val index = messages.indexOfFirst { it.id == messageId }
            if (index < 0) continue
            val message = messages[index]
            val updatedReactions = applyMessageReactionMutation(message.reactions, emoji, userId)
            val updated = messages.toMutableList()
            updated[index] = message.copy(reactions = updatedReactions)
            writeMessagesUnsafe(conversationId, updated)
            return@write true
        }
        false
    }

    fun markVanishMessagesDismissed(conversationId: String, messageIds: Set<String>, userId: String) = lock.write {
        if (messageIds.isEmpty()) return@write
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id !in messageIds || !msg.isVanishModeMessage || userId in msg.vanishedFor) msg
            else msg.copy(vanishedFor = msg.vanishedFor + userId)
        }
        writeMessagesUnsafe(conversationId, messages)
    }

    fun searchMessageIds(conversationId: String, query: String, limit: Int = 100): List<String> = lock.read {
        if (limit <= 0) return@read emptyList()
        val normalizedQuery = SearchNormalization.normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return@read emptyList()
        val matches = mutableListOf<String>()
        for (message in loadMessagesUnsafe(conversationId).sortedBy { it.timestamp }) {
            if (message.type != MessageType.TEXT) continue
            if (!SearchNormalization.containsNormalized(message.content.orEmpty(), normalizedQuery)) continue
            matches += message.id
            if (matches.size >= limit) break
        }
        matches
    }

    fun searchMessagesGlobally(query: String, limit: Int = 50): List<EnhancedMessage> = lock.read {
        if (limit <= 0) return@read emptyList()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@read emptyList()
        val normalizedQuery = SearchNormalization.normalizeForSearch(trimmedQuery)
        if (normalizedQuery.isEmpty()) return@read emptyList()
        val matches = mutableListOf<EnhancedMessage>()
        for (conversationId in allConversationIdsUnsafe()) {
            for (message in loadMessagesUnsafe(conversationId)) {
                if (message.type != MessageType.TEXT || message.isDeleted || message.isVanishModeMessage) continue
                val content = message.content ?: continue
                if (!SearchNormalization.containsNormalized(content, normalizedQuery)) continue
                matches += message
            }
        }
        matches.sortedWith(compareByDescending<EnhancedMessage> { it.timestamp }.thenByDescending { it.id })
            .take(limit)
    }

    fun allConversationIds(): Set<String> = lock.read {
        blockingRoom { conversationIdsWithMessages().toSet() }
    }

    fun cleanupOldChats(
        cutoffDate: Date,
        staleThresholdDate: Date,
        recentWindow: Int,
        staleWindow: Int,
    ) = lock.write {
        for (conversationId in allConversationIdsUnsafe()) {
            val latestTimestamp = blockingRoom { latestMessageTimestampIn(conversationId) }
            val isStale = latestTimestamp?.let { it < staleThresholdDate.time } ?: true
            val keepCount = if (isStale) staleWindow else recentWindow
            val removedIds = blockingRoom {
                staleMessageIdsOutsideRecentWindow(
                    conversationId = conversationId,
                    cutoff = cutoffDate.time,
                    keepCount = keepCount,
                )
            }
            if (removedIds.isEmpty()) continue
            blockingRoom {
                deleteStaleMessagesOutsideRecentWindow(
                    conversationId = conversationId,
                    cutoff = cutoffDate.time,
                    keepCount = keepCount,
                )
            }
            removedIds.forEach { ChatCacheStore.deleteMessageFiles(conversationId, it) }
        }
    }

    private fun allConversationIdsUnsafe(): Set<String> {
        return blockingRoom { conversationIdsWithMessages().toSet() }
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

    private fun trimMessagesUnsafe(conversationId: String, messages: List<EnhancedMessage>) {
        val sorted = messages.sortedWith(compareByDescending<EnhancedMessage> { it.timestamp }.thenByDescending { it.id })
        if (sorted.size <= MAX_MESSAGES_PER_CONVERSATION) return
        val overflow = sorted.drop(MAX_MESSAGES_PER_CONVERSATION)
        val kept = sorted.take(MAX_MESSAGES_PER_CONVERSATION)
        writeMessagesUnsafe(conversationId, kept)
        overflow.forEach { ChatCacheStore.deleteMessageFiles(conversationId, it.id) }
    }

    private fun loadMessagesUnsafe(conversationId: String): List<EnhancedMessage> =
        blockingRoom {
            messagesIn(conversationId).mapNotNull(::decodeMessageEntity)
        }

    private fun writeMessagesUnsafe(conversationId: String, messages: List<EnhancedMessage>) {
        blockingRoom {
            replaceMessagesIn(
                conversationId,
                messages.map {
                    MessageEntity(
                        conversationId = conversationId,
                        id = it.id,
                        timestamp = it.timestamp.time,
                        senderId = it.senderId,
                        type = it.type.raw,
                        content = it.content,
                        isRead = it.isRead,
                        isDeleted = it.isDeleted,
                        isVanishModeMessage = it.isVanishModeMessage,
                        payload = it.toJson().toString(),
                    )
                },
            )
        }
    }

    private fun decodeMessageEntity(entity: MessageEntity): EnhancedMessage? = runCatching {
        val obj = JSONObject(entity.payload)
        EnhancedMessage.fromJson(obj).copy(reactions = parseReactionsFromJson(obj))
    }.getOrNull()

    private fun <T> blockingRoom(block: suspend com.moments.android.services.persistence.room.MomentsDao.() -> T): T =
        kotlinx.coroutines.runBlocking(Dispatchers.IO) { MomentsRoomStore.dao().block() }
}
