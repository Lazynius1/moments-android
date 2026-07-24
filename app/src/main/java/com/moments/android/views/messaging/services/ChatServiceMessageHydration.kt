package com.moments.android.views.messaging.services

import com.moments.android.models.EnhancedMessage
import com.moments.android.models.EncryptedChatMediaMetadata
import com.moments.android.views.messaging.models.ChatLocationPayload
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Port de `ChatService+MessageHydration.swift`. */
object ViewOnceReplaySessionStore {
    data class PendingReplay(val conversationId: String, val messageId: String, val viewerId: String)

    private val lock = ReentrantLock()
    private val availableKeys = mutableSetOf<String>()
    private val consumedKeys = mutableSetOf<String>()

    fun markAvailable(message: EnhancedMessage, viewerId: String) = key(message, viewerId)?.let { key ->
        lock.withLock { availableKeys += key; consumedKeys -= key }
    }

    fun markConsumed(message: EnhancedMessage, viewerId: String) = key(message, viewerId)?.let { key ->
        lock.withLock { availableKeys -= key; consumedKeys += key }
    }

    fun state(message: EnhancedMessage, viewerId: String): Pair<Boolean, Boolean> = key(message, viewerId)?.let { key ->
        lock.withLock { availableKeys.contains(key) to consumedKeys.contains(key) }
    } ?: (false to false)

    fun clear(conversationId: String) { drainAvailable(conversationId) }

    fun drainAvailable(conversationId: String): List<PendingReplay> = lock.withLock {
        val prefix = "$conversationId|"
        val pending = availableKeys.filter { it.startsWith(prefix) }.mapNotNull(::pendingReplay)
        availableKeys.removeAll { it.startsWith(prefix) }
        consumedKeys.removeAll { it.startsWith(prefix) }
        pending
    }

    private fun key(message: EnhancedMessage, viewerId: String): String? =
        if (message.isViewOnce && message.allowReplay == true && message.senderId != viewerId && viewerId.isNotBlank()) {
            "${message.conversationId}|${message.id}|$viewerId"
        } else null

    private fun pendingReplay(key: String): PendingReplay? = key.split('|', limit = 3).takeIf { it.size == 3 }
        ?.let { PendingReplay(it[0], it[1], it[2]) }
}

fun ChatService.createBasicMessageData(message: EnhancedMessage): MutableMap<String, Any?> =
    ChatMessageMapper.toFirestoreData(message, useServerTimestamp = true).toMutableMap()

suspend fun ChatService.resolveEncryptedMediaForMessage(message: EnhancedMessage, forceDownload: Boolean = false): CachedResolvedMedia? =
    encryptedMediaResolver.resolveForMessage(message, forceDownload)

suspend fun ChatService.resolveVideoThumbnail(message: EnhancedMessage, forceDownload: Boolean = false): String? =
    encryptedMediaResolver.resolveThumbnailURL(message, forceDownload)

fun ChatService.warmMessageURLsFromDiskCache(message: EnhancedMessage): CachedResolvedMedia =
    encryptedMediaResolver.warmMessageURLsFromDiskCache(message)

suspend fun ChatService.resolveEncryptedMediaForDisplay(
    messageId: String,
    conversationId: String,
    mediaObjectPath: String,
    mediaEncryption: EncryptedChatMediaMetadata,
    thumbnailObjectPath: String?,
    thumbnailEncryption: EncryptedChatMediaMetadata?,
): CachedResolvedMedia = encryptedMediaResolver.resolveForDisplay(
    messageId,
    conversationId,
    mediaObjectPath,
    mediaEncryption,
    thumbnailObjectPath,
    thumbnailEncryption,
)

internal fun resolveLocationPayload(content: String?): ChatLocationPayload? = content?.let(ChatLocationPayload::decode)
