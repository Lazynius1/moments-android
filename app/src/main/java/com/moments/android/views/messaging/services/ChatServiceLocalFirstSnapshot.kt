package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Date

/** Port de `ChatService+LocalFirstSnapshot.swift`. */
fun resolvedIncomingIsRead(data: Map<String, Any?>, senderId: String): Boolean {
    val readBy = (data["readBy"] as? List<*>)?.filterIsInstance<String>().orEmpty()
    val documentIsRead = data["isRead"] as? Boolean ?: false
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    return if (currentUserId != null && senderId != currentUserId) documentIsRead || currentUserId in readBy else documentIsRead
}

suspend fun ChatService.buildMessagesFromSnapshotUsingLocalCache(
    documents: List<QueryDocumentSnapshot>,
    conversationId: String,
    cutoffDate: Date?,
): List<EnhancedMessage> {
    val cachedById = LocalPersistenceService.loadMessagesFast(conversationId).associateBy(EnhancedMessage::id)
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val indexedMessages = mutableMapOf<Int, EnhancedMessage>()
    val needingHydration = mutableListOf<Triple<Int, QueryDocumentSnapshot, Map<String, Any?>>>()
    val orderedIndices = mutableListOf<Int>()

    documents.forEachIndexed { index, document ->
        val data = document.data
        val deletedFor = (data["deletedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val vanishedFor = (data["vanishedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val timestamp = (data["timestamp"] as? Timestamp)?.toDate()
        if (currentUserId != null && (currentUserId in deletedFor || currentUserId in vanishedFor)) return@forEachIndexed
        if (cutoffDate != null && timestamp != null && timestamp <= cutoffDate) return@forEachIndexed

        orderedIndices += index
        val messageId = data["id"] as? String ?: document.id
        val cached = cachedById[messageId]
        if (cached != null && !snapshotNeedsFullHydrate(data, cached)) {
            indexedMessages[index] = applySnapshotMetadata(cached, data)
        } else {
            needingHydration += Triple(index, document, data)
        }
    }

    coroutineScope {
        needingHydration.map { (index, document, data) ->
            async { index to ChatMessageMapper.buildFromMap(data, document.id, conversationId) }
        }.awaitAll().forEach { (index, message) -> indexedMessages[index] = message }
    }
    return orderedIndices.mapNotNull(indexedMessages::get)
}

private fun snapshotNeedsFullHydrate(data: Map<String, Any?>, cached: EnhancedMessage): Boolean {
    val type = data["type"] as? String ?: MessageType.TEXT.raw
    if (type != cached.type.raw) return true
    if ((data["editedAt"] as? Timestamp)?.toDate() != cached.editedAt) return true
    if ((data["isDeleted"] as? Boolean ?: false) != cached.isDeleted) return true
    if (type == MessageType.CHAT_NOTICE.raw && (data["content"] as? String) != cached.content) return true
    if ((data["mediaObjectPath"] as? String) != cached.mediaObjectPath) return true
    if ((data["thumbnailObjectPath"] as? String) != cached.thumbnailObjectPath) return true

    @Suppress("UNCHECKED_CAST")
    val remoteEncryption = data["mediaEncryption"] as? Map<String, Any?>
    val cachedEncryption = cached.mediaEncryption
    if (remoteEncryption != null && cachedEncryption != null) {
        if ((remoteEncryption["mediaId"] as? String) != cachedEncryption.mediaId) return true
    } else if ((remoteEncryption != null) != (cachedEncryption != null)) {
        return true
    }
    return data["content"] != null && data["editedAt"] != null
}

private fun applySnapshotMetadata(message: EnhancedMessage, data: Map<String, Any?>): EnhancedMessage {
    fun stringList(key: String): List<String>? = (data[key] as? List<*>)?.filterIsInstance<String>()
    val isDeleted = data["isDeleted"] as? Boolean ?: message.isDeleted
    val updated = message.copy(
        isRead = resolvedIncomingIsRead(data, message.senderId),
        status = (data["status"] as? String)?.let(MessageStatus::from) ?: message.status,
        isDeleted = isDeleted,
        deletedAt = (data["deletedAt"] as? Timestamp)?.toDate() ?: message.deletedAt,
        isViewed = data["isViewed"] as? Boolean ?: message.isViewed,
        viewedBy = stringList("viewedBy") ?: message.viewedBy,
        allowReplay = data["allowReplay"] as? Boolean ?: message.allowReplay,
        replayedBy = stringList("replayedBy") ?: message.replayedBy,
        readBy = stringList("readBy") ?: message.readBy,
        starredBy = stringList("starredBy") ?: message.starredBy,
        isForwarded = data["isForwarded"] as? Boolean ?: message.isForwarded,
        vanishedFor = stringList("vanishedFor") ?: message.vanishedFor,
        vanishExpiresAt = (data["vanishExpiresAt"] as? Timestamp)?.toDate() ?: message.vanishExpiresAt,
        textOverlayLive = data["textOverlayLive"] as? Boolean,
        textOverlays = decodeTextOverlays(data["textOverlays"]),
        stickers = decodeStickers(data["stickers"]),
        drawingData = data["drawingData"] as? ByteArray,
    )
    return if (updated.isDeleted) updated.copy(
        mediaUrl = null,
        thumbnailUrl = null,
        textOverlayLive = null,
        textOverlays = null,
        stickers = null,
        drawingData = null,
    ) else updated
}

private fun decodeTextOverlays(value: Any?): List<StoryTextOverlayMetadata>? =
    (value as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(StoryTextOverlayMetadata::from) }

private fun decodeStickers(value: Any?): List<StickerData>? =
    (value as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(StickerData::from) }
