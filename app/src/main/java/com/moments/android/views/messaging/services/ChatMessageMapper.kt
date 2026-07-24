package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.EncryptedChatMediaMetadata
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageSyncCursor
import com.moments.android.models.MessageType
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.toMap
import com.moments.android.models.cache.CachedAction
import com.moments.android.models.MessagePayload
import com.moments.android.views.messaging.models.ChatLocationPayload
import com.moments.android.services.firestore.shouldQueueFirestoreOutbox
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Date

import com.moments.android.services.messaging.EncryptionService

/** Mapeo Firestore ↔ EnhancedMessage (paridad ChatService+MessageHydration iOS). */
internal object ChatMessageMapper {

    suspend fun buildFromSnapshot(
        snapshot: DocumentSnapshot,
        conversationId: String,
    ): EnhancedMessage? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        @Suppress("UNCHECKED_CAST")
        val deletedFor = data["deletedFor"] as? List<String>
        if (currentUserId != null && deletedFor?.contains(currentUserId) == true) return null
        @Suppress("UNCHECKED_CAST")
        val vanishedFor = data["vanishedFor"] as? List<String>
        if (currentUserId != null && vanishedFor?.contains(currentUserId) == true) return null
        return buildFromMap(data, snapshot.id, conversationId)
    }

    suspend fun buildFromMap(
        data: Map<String, Any?>,
        docId: String,
        conversationId: String,
    ): EnhancedMessage {
        val id = data["id"] as? String ?: docId
        val senderId = data["senderId"] as? String ?: ""
        val type = MessageType.from(data["type"] as? String)
        val rawContent = data["content"] as? String
        val decryptedContent = when {
            type == MessageType.CHAT_NOTICE -> rawContent
            rawContent.isNullOrEmpty() -> null
            else -> ChatMessageContentContract.resolve(
                rawContent,
                EncryptionService.decryptChatMessage(rawContent, conversationId),
            )
        }
        var locationLatitude = (data["latitude"] as? Number)?.toDouble()
        var locationLongitude = (data["longitude"] as? Number)?.toDouble()
        var locationName = data["locationName"] as? String
        var locationAddress = data["locationAddress"] as? String
        val content = if (type == MessageType.LOCATION) {
            ChatLocationPayload.decode(decryptedContent.orEmpty())?.let { payload ->
                locationLatitude = payload.lat
                locationLongitude = payload.lng
                locationName = payload.name ?: locationName
                locationAddress = payload.address ?: locationAddress
            }
            null
        } else decryptedContent
        @Suppress("UNCHECKED_CAST")
        val mediaEncryption = (data["mediaEncryption"] as? Map<String, Any?>)?.let {
            EncryptedChatMediaMetadata.fromJson(JSONObject(it))
        }
        @Suppress("UNCHECKED_CAST")
        val thumbnailEncryption = (data["thumbnailEncryption"] as? Map<String, Any?>)?.let {
            EncryptedChatMediaMetadata.fromJson(JSONObject(it))
        }
        val mediaObjectPath = data["mediaObjectPath"] as? String
        val thumbnailObjectPath = data["thumbnailObjectPath"] as? String
        val isDeleted = data["isDeleted"] as? Boolean ?: false
        val resolvedMedia = when {
            isDeleted -> CachedResolvedMedia(null, null)
            !mediaObjectPath.isNullOrBlank() && mediaEncryption != null ->
                ChatEncryptedMediaResolver.resolveForDisplay(
                    messageId = id,
                    conversationId = conversationId,
                    mediaObjectPath = mediaObjectPath,
                    mediaEncryption = mediaEncryption,
                    thumbnailObjectPath = thumbnailObjectPath,
                    thumbnailEncryption = thumbnailEncryption,
                )
            else -> CachedResolvedMedia(data["mediaUrl"] as? String, data["thumbnailUrl"] as? String)
        }
        val timestamp = when (val ts = data["timestamp"]) {
            is Timestamp -> ts.toDate()
            is Date -> ts
            else -> Date()
        }
        @Suppress("UNCHECKED_CAST")
        val reactions = data["reactions"] as? Map<String, List<String>>
        @Suppress("UNCHECKED_CAST")
        val vanishedForList = data["vanishedFor"] as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val storyReplyData = (data["storyReplyData"] as? Map<String, Any?>)
            ?.mapValues { (_, value) -> value?.toString().orEmpty() }
        @Suppress("UNCHECKED_CAST")
        val sharedMomentData = (data["sharedMomentData"] as? Map<String, Any?>)
            ?.mapValues { (_, value) -> value?.toString().orEmpty() }
        @Suppress("UNCHECKED_CAST")
        val sharedStoryData = (data["sharedStoryData"] as? Map<String, Any?>)
            ?.mapValues { (_, value) -> value?.toString().orEmpty() }
        @Suppress("UNCHECKED_CAST")
        fun stringList(key: String): List<String>? = (data[key] as? List<*>)?.filterIsInstance<String>()
        return EnhancedMessage(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            type = type,
            content = content,
            mediaUrl = resolvedMedia.mediaUrl,
            thumbnailUrl = resolvedMedia.thumbnailUrl,
            mediaObjectPath = mediaObjectPath,
            thumbnailObjectPath = thumbnailObjectPath,
            mediaEncryption = mediaEncryption,
            thumbnailEncryption = thumbnailEncryption,
            duration = (data["duration"] as? Number)?.toDouble(),
            audioWaveform = (data["audioWaveform"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() },
            fileName = data["fileName"] as? String,
            fileSize = (data["fileSize"] as? Number)?.toLong(),
            mediaWidth = (data["mediaWidth"] as? Number)?.toInt(),
            mediaHeight = (data["mediaHeight"] as? Number)?.toInt(),
            latitude = locationLatitude,
            longitude = locationLongitude,
            locationName = locationName,
            locationAddress = locationAddress,
            isLiveLocation = data["isLiveLocation"] as? Boolean,
            liveLocationExpiresAt = (data["liveLocationExpiresAt"] as? Timestamp)?.toDate(),
            liveLocationDuration = data["liveLocationDuration"] as? String,
            liveLocationStoppedAt = (data["liveLocationStoppedAt"] as? Timestamp)?.toDate(),
            liveLocationSessionId = data["liveLocationSessionId"] as? String,
            locationUpdatedAt = (data["locationUpdatedAt"] as? Timestamp)?.toDate(),
            timestamp = timestamp,
            status = MessageStatus.from(data["status"] as? String),
            isRead = resolvedIncomingIsRead(data, senderId),
            isDeleted = isDeleted,
            deletedAt = (data["deletedAt"] as? Timestamp)?.toDate(),
            editedAt = (data["editedAt"] as? Timestamp)?.toDate(),
            reactions = reactions,
            replyTo = data["replyTo"] as? String,
            storyReplyData = storyReplyData,
            sharedMomentData = sharedMomentData,
            sharedStoryData = sharedStoryData,
            expirationDate = (data["expirationDate"] as? Timestamp)?.toDate(),
            isViewed = data["isViewed"] as? Boolean ?: false,
            mediaBatchId = data["mediaBatchId"] as? String,
            textOverlayLive = data["textOverlayLive"] as? Boolean,
            textOverlays = (data["textOverlays"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(StoryTextOverlayMetadata::from) },
            stickers = (data["stickers"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(StickerData::from) },
            drawingData = data["drawingData"] as? ByteArray,
            viewedBy = stringList("viewedBy"),
            allowReplay = data["allowReplay"] as? Boolean,
            replayedBy = stringList("replayedBy"),
            readBy = stringList("readBy"),
            starredBy = stringList("starredBy"),
            isForwarded = data["isForwarded"] as? Boolean,
            isVanishModeMessage = data["isVanishModeMessage"] as? Boolean ?: false,
            vanishedFor = vanishedForList,
            vanishExpiresAt = (data["vanishExpiresAt"] as? Timestamp)?.toDate(),
        )
    }

    fun toFirestoreData(message: EnhancedMessage, useServerTimestamp: Boolean): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "id" to message.id,
            "conversationId" to message.conversationId,
            "senderId" to message.senderId,
            "type" to message.type.raw,
            "status" to MessageStatus.SENT.raw,
            "isRead" to message.isRead,
            "isDeleted" to message.isDeleted,
            "isViewed" to message.isViewed,
        )
        message.content?.let { data["content"] = it }
        message.mediaObjectPath?.let { data["mediaObjectPath"] = it } ?: message.mediaUrl?.let { data["mediaUrl"] = it }
        message.thumbnailObjectPath?.let { data["thumbnailObjectPath"] = it }
            ?: message.thumbnailUrl?.let { data["thumbnailUrl"] = it }
        message.mediaEncryption?.toJson()?.let { json ->
            data["mediaEncryption"] = json.keys().asSequence().associateWith { json.get(it) }
        }
        message.thumbnailEncryption?.toJson()?.let { json ->
            data["thumbnailEncryption"] = json.keys().asSequence().associateWith { json.get(it) }
        }
        message.duration?.let { data["duration"] = it }
        message.audioWaveform?.takeIf { it.isNotEmpty() }?.let { data["audioWaveform"] = it.take(64).map(Float::toDouble) }
        message.audioWaveform?.takeIf { it.isNotEmpty() }?.let { data["audioWaveform"] = it.take(64).map { v -> v.toDouble() } }
        message.fileName?.let { data["fileName"] = it }
        message.fileSize?.let { data["fileSize"] = it }
        message.mediaWidth?.let { data["mediaWidth"] = it }
        message.mediaHeight?.let { data["mediaHeight"] = it }
        message.latitude?.let { data["latitude"] = it }
        message.longitude?.let { data["longitude"] = it }
        message.locationName?.let { data["locationName"] = it }
        message.locationAddress?.let { data["locationAddress"] = it }
        message.isLiveLocation?.let { data["isLiveLocation"] = it }
        message.liveLocationExpiresAt?.let { data["liveLocationExpiresAt"] = Timestamp(it) }
        message.liveLocationDuration?.let { data["liveLocationDuration"] = it }
        message.liveLocationStoppedAt?.let { data["liveLocationStoppedAt"] = Timestamp(it) }
        message.liveLocationSessionId?.let { data["liveLocationSessionId"] = it }
        message.locationUpdatedAt?.let { data["locationUpdatedAt"] = Timestamp(it) }
        message.replyTo?.let { data["replyTo"] = it }
        message.storyReplyData?.let { data["storyReplyData"] = it }
        message.sharedMomentData?.let { data["sharedMomentData"] = it }
        message.sharedStoryData?.let { data["sharedStoryData"] = it }
        message.expirationDate?.let { data["expirationDate"] = Timestamp(it) }
        message.mediaBatchId?.let { data["mediaBatchId"] = it }
        message.textOverlayLive?.let { data["textOverlayLive"] = it }
        message.textOverlays?.let { data["textOverlays"] = it.map(StoryTextOverlayMetadata::toMap) }
        message.stickers?.let { data["stickers"] = it.map(StickerData::toMap) }
        message.drawingData?.let { data["drawingData"] = it }
        message.viewedBy?.let { data["viewedBy"] = it }
        message.allowReplay?.let { data["allowReplay"] = it }
        message.replayedBy?.let { data["replayedBy"] = it }
        if (message.isViewOnce) data["isViewOnce"] = true
        message.readBy?.let { data["readBy"] = it }
        message.starredBy?.let { data["starredBy"] = it }
        message.isForwarded?.let { data["isForwarded"] = it }
        if (message.isVanishModeMessage) data["isVanishModeMessage"] = true
        message.vanishExpiresAt?.let { data["vanishExpiresAt"] = Timestamp(it) }
        data["timestamp"] = if (useServerTimestamp) FieldValue.serverTimestamp() else Timestamp(message.timestamp)
        return data
    }

    fun queueOfflineMessage(message: EnhancedMessage, useServerTimestamp: Boolean) {
        val pending = message.copy(status = MessageStatus.PENDING)
        val payload = MessagePayload(pending, useServerTimestamp)
        val payloadJson = JSONObject().apply {
            put("message", pending.toJson())
            put("useServerTimestamp", useServerTimestamp)
        }
        LocalPersistenceService.saveAction(
            CachedAction(
                id = message.id,
                type = CachedAction.ActionType.MESSAGE.raw,
                payloadData = payloadJson.toString().toByteArray(),
            ),
        )
    }
}

/** Matches ChatService.swift's `decryptChatMessage(...) ?? content` behavior. */
internal object ChatMessageContentContract {
    fun resolve(ciphertext: String, decrypted: String?): String = decrypted ?: ciphertext
}
