package com.moments.android.services.persistence

import android.content.Context
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageSyncCursor
import com.moments.android.models.MessageType
import com.moments.android.models.decodeMessages
import com.moments.android.models.encodeMessages
import com.moments.android.services.messaging.ChatCacheStore
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
 * Port de MessagePersistenceStore.swift (SwiftData @ModelActor).
 * Persistencia JSON en filesDir; mutaciones expuestas aquí y delegadas desde LocalPersistenceService
 * (misma separación actor/capa que iOS). API parity: save/reconcile/recent/before/after/search/mutations.
 */
object MessagePersistenceStore {
    private const val DIR = "message_cache"
    private const val MAX_MESSAGES_PER_CONVERSATION = 2_000
    private val lock = ReentrantReadWriteLock()

    @Volatile private var cacheDir: File? = null

    fun initialize(context: Context) {
        if (cacheDir != null) return
        cacheDir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
    }

    private fun conversationFile(conversationId: String): File {
        val safe = conversationId.replace("/", "_")
        return File(cacheDir ?: error("MessagePersistenceStore.initialize required"), "$safe.json")
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
            var cached = loadMessagesUnsafe(conversationId)
                .sortedWith(compareByDescending<EnhancedMessage> { it.timestamp }.thenByDescending { it.id })
                .take(limit)
            if (cutoffDate != null) {
                cached = cached.filter { it.timestamp > cutoffDate }
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
            val filtered = loadMessagesUnsafe(conversationId).filter { msg ->
                (cutoffDate == null || msg.timestamp > cutoffDate) &&
                    (msg.timestamp < cursor.timestamp ||
                        (msg.timestamp == cursor.timestamp && msg.id < cursor.messageId))
            }.sortedWith(compareByDescending<EnhancedMessage> { it.timestamp }.thenByDescending { it.id })
                .take(limit)
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
            val filtered = loadMessagesUnsafe(conversationId).filter { msg ->
                (cutoffDate == null || msg.timestamp > cutoffDate) &&
                    (msg.timestamp > cursor.timestamp ||
                        (msg.timestamp == cursor.timestamp && msg.id >= cursor.messageId))
            }.sortedWith(compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id })
                .take(limit)
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
                loadMessagesUnsafe(conversationId).any { it.id == messageId }
            }
        }

    suspend fun lastCursor(conversationId: String): MessageSyncCursor? = withContext(Dispatchers.IO) {
        lock.read {
            loadMessagesUnsafe(conversationId)
                .maxWithOrNull(compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id })
                ?.let { MessageSyncCursor(it.timestamp, it.id) }
        }
    }

    fun cachedMessageCount(): Int = lock.read {
        cacheDir?.listFiles()?.sumOf { file ->
            runCatching { loadMessagesFromFile(file).size }.getOrDefault(0)
        } ?: 0
    }

    fun cachedMessageKeys(since: Date): Set<String> = lock.read {
        val result = mutableSetOf<String>()
        cacheDir?.listFiles()?.forEach { file ->
            val convId = file.nameWithoutExtension.replace("_", "/") // best-effort
            loadMessagesFromFile(file).forEach { msg ->
                if (msg.timestamp >= since) result.add("${msg.conversationId}:${msg.id}")
            }
        }
        result
    }

    fun clearAll() = lock.write {
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    fun updateMessageStatus(conversationId: String, messageId: String, status: String) = lock.write {
        val messages = loadMessagesUnsafe(conversationId).map { msg ->
            if (msg.id == messageId) msg.copy(status = com.moments.android.models.MessageStatus.from(status)) else msg
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
        conversationFile(conversationId).takeIf { it.exists() }?.delete()
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
        if (kept.isEmpty()) {
            conversationFile(conversationId).takeIf { it.exists() }?.delete()
        } else {
            writeMessagesUnsafe(conversationId, kept)
        }
    }

    fun unreadMessageCount(
        conversationId: String,
        currentUserId: String,
        lastReadAt: Date? = null,
    ): Int = lock.read {
        loadMessagesUnsafe(conversationId).count { message ->
            message.senderId != currentUserId &&
                !message.isRead &&
                (lastReadAt == null || message.timestamp.after(lastReadAt))
        }
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
        val dir = cacheDir ?: return@write false
        for (file in dir.listFiles().orEmpty()) {
            val messages = loadMessagesFromFile(file)
            val index = messages.indexOfFirst { it.id == messageId }
            if (index < 0) continue
            val message = messages[index]
            val updatedReactions = applyMessageReactionMutation(message.reactions, emoji, userId)
            val updated = messages.toMutableList()
            updated[index] = message.copy(reactions = updatedReactions)
            val conversationId = message.conversationId
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
        val dir = cacheDir ?: return@read emptyList()
        for (file in dir.listFiles().orEmpty()) {
            for (message in loadMessagesFromFile(file)) {
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
        cacheDir?.listFiles()?.map { file ->
            val messages = loadMessagesFromFile(file)
            messages.firstOrNull()?.conversationId ?: file.nameWithoutExtension.replace("_", "/")
        }?.toSet().orEmpty()
    }

    fun cleanupOldChats(
        cutoffDate: Date,
        staleThresholdDate: Date,
        recentWindow: Int,
        staleWindow: Int,
    ) = lock.write {
        val dir = cacheDir ?: return@write
        for (conversationId in allConversationIdsUnsafe()) {
            val messages = loadMessagesUnsafe(conversationId)
            if (messages.isEmpty()) continue
            val latestTimestamp = messages.maxOfOrNull { it.timestamp }
            val isStale = latestTimestamp?.before(staleThresholdDate) ?: true
            val keepCount = if (isStale) staleWindow else recentWindow
            val protectedIds = messages
                .sortedWith(compareByDescending<EnhancedMessage> { it.timestamp }.thenByDescending { it.id })
                .take(keepCount)
                .map { it.id }
                .toSet()
            val kept = messages.filter { msg ->
                msg.timestamp.after(cutoffDate) || msg.id in protectedIds
            }
            val keptIds = kept.map { it.id }.toSet()
            val removed = messages.filter { it.id !in keptIds }
            writeMessagesUnsafe(conversationId, kept)
            removed.forEach { ChatCacheStore.deleteMessageFiles(conversationId, it.id) }
        }
        dir.listFiles()?.filter { it.length() == 0L }?.forEach { it.delete() }
    }

    private fun allConversationIdsUnsafe(): Set<String> {
        val dir = cacheDir ?: return emptySet()
        return dir.listFiles()?.mapNotNull { file ->
            loadMessagesFromFile(file).firstOrNull()?.conversationId
        }?.toSet().orEmpty()
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
        if (new.isDeleted) {
            return new.copy(
                isRead = existing.isRead || new.isRead,
                vanishedFor = (existing.vanishedFor + new.vanishedFor).distinct(),
                vanishExpiresAt = new.vanishExpiresAt ?: existing.vanishExpiresAt,
            )
        }
        return new.copy(
            mediaUrl = existing.mediaUrl?.takeIf { isLocalFilePresent(it) } ?: new.mediaUrl,
            thumbnailUrl = existing.thumbnailUrl?.takeIf { isLocalFilePresent(it) } ?: new.thumbnailUrl,
            isRead = existing.isRead || new.isRead,
            vanishedFor = (existing.vanishedFor + new.vanishedFor).distinct(),
            vanishExpiresAt = new.vanishExpiresAt ?: existing.vanishExpiresAt,
        )
    }

    private fun isLocalFilePresent(urlString: String?): Boolean {
        if (urlString.isNullOrEmpty()) return false
        return urlString.startsWith("file://") && File(android.net.Uri.parse(urlString).path ?: "").exists()
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
        loadMessagesFromFile(conversationFile(conversationId))

    private fun loadMessagesFromFile(file: File): List<EnhancedMessage> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { index ->
                val obj = arr.getJSONObject(index)
                EnhancedMessage.fromJson(obj).copy(reactions = parseReactionsFromJson(obj))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeMessagesUnsafe(conversationId: String, messages: List<EnhancedMessage>) {
        val arr = JSONArray().apply { messages.forEach { put(it.toJson()) } }
        conversationFile(conversationId).writeText(arr.toString())
    }
}
