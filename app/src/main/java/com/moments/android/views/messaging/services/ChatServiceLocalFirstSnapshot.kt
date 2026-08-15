package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Date

/**
 * Port de `ChatService+LocalFirstSnapshot.swift`.
 * Local-first: reutiliza Room/cache y solo hidrata docs nuevos o con cambio material.
 */

/** ≡ `ChatService.resolvedIncomingIsRead(from:senderId:)` (static iOS). */
fun resolvedIncomingIsRead(data: Map<String, Any?>, senderId: String): Boolean {
    val readBy = (data["readBy"] as? List<*>)?.filterIsInstance<String>().orEmpty()
    val docIsRead = data["isRead"] as? Boolean ?: false
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    return if (currentUid != null && senderId != currentUid) {
        docIsRead || currentUid in readBy
    } else {
        docIsRead
    }
}

suspend fun ChatService.buildMessagesFromSnapshotUsingLocalCache(
    documents: List<QueryDocumentSnapshot>,
    conversationId: String,
    cutoffDate: Date?,
): List<EnhancedMessage> {
    val cached = LocalPersistenceService.loadMessagesFast(conversationId)
    val cachedById = cached.associateBy(EnhancedMessage::id)
    val hasLocalCache = cached.isNotEmpty()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    val indexedMessages = mutableMapOf<Int, EnhancedMessage>()
    val indicesNeedingHydration =
        mutableListOf<Triple<Int, QueryDocumentSnapshot, Map<String, Any?>>>()
    val orderedIndices = mutableListOf<Int>()

    documents.forEachIndexed { index, doc ->
        val data = doc.data

        val deletedFor = (data["deletedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (currentUserId != null && currentUserId in deletedFor) return@forEachIndexed

        val vanishedFor = (data["vanishedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (currentUserId != null && currentUserId in vanishedFor) return@forEachIndexed

        val msgTimestamp = (data["timestamp"] as? Timestamp)?.toDate()
        if (cutoffDate != null && msgTimestamp != null && !msgTimestamp.after(cutoffDate)) {
            return@forEachIndexed
        }

        orderedIndices += index
        val messageId = data["id"] as? String ?: doc.id

        if (hasLocalCache) {
            val existing = cachedById[messageId]
            if (existing != null && !snapshotNeedsFullHydrate(data, existing)) {
                indexedMessages[index] = applySnapshotMetadata(existing, data)
                return@forEachIndexed
            }
        }
        indicesNeedingHydration += Triple(index, doc, data)
    }

    if (indicesNeedingHydration.isNotEmpty()) {
        coroutineScope {
            // ≡ withTaskGroup hydrate paralelo
            indicesNeedingHydration.map { (index, doc, data) ->
                async {
                    index to buildEnhancedMessage(data, doc.id, conversationId)
                }
            }.awaitAll().forEach { (index, message) ->
                indexedMessages[index] = message
            }
        }
    }

    return orderedIndices.mapNotNull { indexedMessages[it] }
}

private fun snapshotNeedsFullHydrate(data: Map<String, Any?>, cached: EnhancedMessage): Boolean {
    val typeString = data["type"] as? String ?: MessageType.TEXT.raw
    if (typeString != cached.type.raw) return true

    val remoteEditedAt = (data["editedAt"] as? Timestamp)?.toDate()
    if (remoteEditedAt != cached.editedAt) return true

    val remoteDeleted = data["isDeleted"] as? Boolean ?: false
    if (remoteDeleted != cached.isDeleted) return true

    if (typeString == MessageType.CHAT_NOTICE.raw) {
        if ((data["content"] as? String) != cached.content) return true
    }

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

    if (data["content"] != null && remoteEditedAt != null) return true

    // Ubicación en vivo: cache viejo / reuse sin estos campos deja la bubble como fija.
    val remoteIsLive = data["isLiveLocation"] as? Boolean
    if (remoteIsLive != cached.isLiveLocation) return true

    val remoteLiveExpiresAt = (data["liveLocationExpiresAt"] as? Timestamp)?.toDate()
    if (remoteLiveExpiresAt != cached.liveLocationExpiresAt) return true

    val remoteLiveStoppedAt = (data["liveLocationStoppedAt"] as? Timestamp)?.toDate()
    if (remoteLiveStoppedAt != cached.liveLocationStoppedAt) return true

    val remoteLiveDuration = data["liveLocationDuration"] as? String
    if (remoteLiveDuration != cached.liveLocationDuration) return true

    val remoteLiveSessionId = data["liveLocationSessionId"] as? String
    if (remoteLiveSessionId != cached.liveLocationSessionId) return true

    val remoteLocationUpdatedAt = (data["locationUpdatedAt"] as? Timestamp)?.toDate()
    if (remoteLocationUpdatedAt != cached.locationUpdatedAt) return true

    val remoteLocationName = data["locationName"] as? String
    if (remoteLocationName != cached.locationName) return true

    val remoteLocationAddress = data["locationAddress"] as? String
    if (remoteLocationAddress != cached.locationAddress) return true

    return false
}

private fun applySnapshotMetadata(message: EnhancedMessage, data: Map<String, Any?>): EnhancedMessage {
    fun stringList(key: String): List<String>? =
        (data[key] as? List<*>)?.filterIsInstance<String>()

    var updated = message.copy(
        isRead = resolvedIncomingIsRead(data, message.senderId),
        status = (data["status"] as? String)?.let(MessageStatus::from) ?: message.status,
        isDeleted = data["isDeleted"] as? Boolean ?: message.isDeleted,
        deletedAt = (data["deletedAt"] as? Timestamp)?.toDate() ?: message.deletedAt,
        isViewed = data["isViewed"] as? Boolean ?: message.isViewed,
        viewedBy = stringList("viewedBy") ?: message.viewedBy,
        allowReplay = data["allowReplay"] as? Boolean ?: message.allowReplay,
        replayedBy = stringList("replayedBy") ?: message.replayedBy,
        readBy = stringList("readBy") ?: message.readBy,
        readAtBy = (data["readAtBy"] as? Map<*, *>)
            ?.mapNotNull { (userId, value) ->
                val id = userId as? String ?: return@mapNotNull null
                val date = (value as? Timestamp)?.toDate() ?: return@mapNotNull null
                id to date
            }
            ?.toMap()
            ?: message.readAtBy,
        starredBy = stringList("starredBy") ?: message.starredBy,
        isForwarded = data["isForwarded"] as? Boolean ?: message.isForwarded,
        vanishedFor = stringList("vanishedFor") ?: message.vanishedFor,
        vanishExpiresAt = (data["vanishExpiresAt"] as? Timestamp)?.toDate() ?: message.vanishExpiresAt,
        textOverlayLive = data["textOverlayLive"] as? Boolean,
        textOverlays = decodeTextOverlays(data["textOverlays"]),
        stickers = decodeStickers(data["stickers"]),
        drawingData = decodeDrawingData(data["drawingData"]),
    )

    updated = ViewOnceReplaySessionStore.apply(
        updated,
        FirebaseAuth.getInstance().currentUser?.uid,
    )

    // CF consumeViewOnceMessage borra campos de media sin poner isDeleted.
    // Si el remoto ya no tiene media view-once, no conservar URLs/paths del cache local.
    val remoteViewOnceMediaGone = updated.isViewOnce &&
        (data["mediaObjectPath"] as? String).isNullOrBlank() &&
        (data["mediaUrl"] as? String).isNullOrBlank() &&
        (data["thumbnailObjectPath"] as? String).isNullOrBlank() &&
        (data["thumbnailUrl"] as? String).isNullOrBlank()

    return if (updated.isDeleted || remoteViewOnceMediaGone) {
        updated.copy(
            mediaUrl = null,
            thumbnailUrl = null,
            mediaObjectPath = null,
            thumbnailObjectPath = null,
            mediaEncryption = null,
            thumbnailEncryption = null,
            textOverlayLive = null,
            textOverlays = null,
            stickers = null,
            drawingData = null,
        )
    } else {
        updated
    }
}

private fun decodeTextOverlays(value: Any?): List<StoryTextOverlayMetadata>? =
    (value as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { map ->
        @Suppress("UNCHECKED_CAST")
        StoryTextOverlayMetadata.from(map as Map<String, Any?>)
    } }

private fun decodeStickers(value: Any?): List<StickerData>? =
    (value as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { map ->
        @Suppress("UNCHECKED_CAST")
        StickerData.from(map as Map<String, Any?>)
    } }

private fun decodeDrawingData(value: Any?): ByteArray? = when (value) {
    is ByteArray -> value
    is Blob -> value.toBytes()
    else -> null
}
