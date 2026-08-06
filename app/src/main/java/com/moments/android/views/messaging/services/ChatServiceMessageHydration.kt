package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.EncryptedChatMediaMetadata
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.toMap
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.views.messaging.models.ChatLocationPayload
import java.util.Date
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.json.JSONObject

/**
 * Port de `ChatService+MessageHydration.swift`
 * (+ `ViewOnceReplaySessionStore` del mismo archivo).
 */
object ViewOnceReplaySessionStore {
    data class PendingReplay(val conversationId: String, val messageId: String, val viewerId: String)

    private val lock = ReentrantLock()
    private val availableKeys = mutableSetOf<String>()
    private val consumedKeys = mutableSetOf<String>()

    fun markAvailable(message: EnhancedMessage, viewerId: String) = key(message, viewerId)?.let { key ->
        lock.withLock {
            availableKeys += key
            consumedKeys -= key
        }
    }

    fun markConsumed(message: EnhancedMessage, viewerId: String) = key(message, viewerId)?.let { key ->
        lock.withLock {
            availableKeys -= key
            consumedKeys += key
        }
    }

    fun state(message: EnhancedMessage, viewerId: String): Pair<Boolean, Boolean> =
        key(message, viewerId)?.let { key ->
            lock.withLock { availableKeys.contains(key) to consumedKeys.contains(key) }
        } ?: (false to false)

    /** ≡ `apply(to:viewerId:)` — flags de sesión sobre copia del mensaje. */
    fun apply(message: EnhancedMessage, viewerId: String?): EnhancedMessage {
        if (viewerId == null) return message
        val key = key(message, viewerId) ?: return message
        val (available, consumed) = lock.withLock {
            availableKeys.contains(key) to consumedKeys.contains(key)
        }
        if (!available && !consumed) return message
        return if (available && message.allowReplay == true && !message.hasBeenReplayedBy(viewerId)) {
            message.copy(
                replayAvailableInCurrentChatSession = true,
                replayConsumedInCurrentChatSession = false,
            )
        } else if (consumed) {
            message.copy(
                replayAvailableInCurrentChatSession = false,
                replayConsumedInCurrentChatSession = true,
            )
        } else {
            message
        }
    }

    fun clear(conversationId: String) {
        drainAvailable(conversationId)
    }

    fun drainAvailable(conversationId: String): List<PendingReplay> = lock.withLock {
        val prefix = "$conversationId|"
        val pending = availableKeys.filter { it.startsWith(prefix) }.mapNotNull(::pendingReplay)
        availableKeys.removeAll { it.startsWith(prefix) }
        consumedKeys.removeAll { it.startsWith(prefix) }
        pending
    }

    private fun key(message: EnhancedMessage, viewerId: String): String? =
        if (
            message.isViewOnce &&
            message.allowReplay == true &&
            message.senderId != viewerId &&
            viewerId.isNotEmpty()
        ) {
            "${message.conversationId}|${message.id}|$viewerId"
        } else {
            null
        }

    private fun pendingReplay(key: String): PendingReplay? {
        val parts = key.split("|")
        if (parts.size != 3) return null
        return PendingReplay(parts[0], parts[1], parts[2])
    }
}

/**
 * ≡ `createBasicMessageData(from:)` — subset (sin content); overlays vía appendOverlayPayload.
 * Usado por view-once send path.
 */
fun ChatService.createBasicMessageData(message: EnhancedMessage): MutableMap<String, Any> {
    val data = mutableMapOf<String, Any>(
        "id" to message.id,
        "conversationId" to message.conversationId,
        "senderId" to message.senderId,
        "type" to message.type.raw,
        "timestamp" to FieldValue.serverTimestamp(),
        "status" to MessageStatus.SENT.raw,
        "isRead" to message.isRead,
        "isDeleted" to message.isDeleted,
        "isViewed" to message.isViewed,
    )
    when {
        message.mediaObjectPath != null -> data["mediaObjectPath"] = message.mediaObjectPath!!
        message.mediaUrl != null -> data["mediaUrl"] = message.mediaUrl!!
    }
    when {
        message.thumbnailObjectPath != null -> data["thumbnailObjectPath"] = message.thumbnailObjectPath!!
        message.thumbnailUrl != null -> data["thumbnailUrl"] = message.thumbnailUrl!!
    }
    message.mediaEncryption?.let { data["mediaEncryption"] = it.toFirestoreMap() }
    message.thumbnailEncryption?.let { data["thumbnailEncryption"] = it.toFirestoreMap() }
    message.duration?.let { data["duration"] = it }
    message.audioWaveform?.takeIf { it.isNotEmpty() }?.let {
        data["audioWaveform"] = it.take(64).map(Float::toDouble)
    }
    message.fileSize?.let { data["fileSize"] = it }
    message.mediaWidth?.let { data["mediaWidth"] = it }
    message.mediaHeight?.let { data["mediaHeight"] = it }
    message.replyTo?.let { data["replyTo"] = it }
    if (message.isVanishModeMessage) data["isVanishModeMessage"] = true
    appendOverlayPayload(message, data)
    return data
}

private fun EncryptedChatMediaMetadata.toFirestoreMap(): Map<String, Any> {
    val json = toJson()
    return json.keys().asSequence().associateWith { key -> json.get(key) }
}

/** ≡ `appendOverlayPayload(from:to:)`. */
private fun appendOverlayPayload(message: EnhancedMessage, data: MutableMap<String, Any>) {
    message.textOverlayLive?.let { data["textOverlayLive"] = it }
    message.textOverlays
        ?.map(StoryTextOverlayMetadata::toMap)
        ?.takeIf { it.isNotEmpty() }
        ?.let { data["textOverlays"] = it }
    message.stickers
        ?.map(StickerData::toMap)
        ?.takeIf { it.isNotEmpty() }
        ?.let { data["stickers"] = it }
    message.drawingData?.let { data["drawingData"] = it }
}

/**
 * Helper de fetch/listen (filtros deletedFor/vanishedFor como en LocalFirst iOS)
 * + [buildEnhancedMessage].
 */
suspend fun ChatService.buildEnhancedMessageFromSnapshot(
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
    return buildEnhancedMessage(data, snapshot.id, conversationId)
}

/** ≡ `buildEnhancedMessage(from:docId:conversationId:decryptedContentOverride:)`. */
suspend fun ChatService.buildEnhancedMessage(
    data: Map<String, Any?>,
    docId: String,
    conversationId: String,
    decryptedContentOverride: String? = null,
): EnhancedMessage {
    val id = data["id"] as? String ?: docId
    val senderId = data["senderId"] as? String ?: ""
    val type = MessageType.from(data["type"] as? String)
    val rawContent = data["content"] as? String
    // ≡ decryptMessageContent → decrypt ?? content
    val decryptedContent = when {
        type == MessageType.CHAT_NOTICE -> rawContent
        decryptedContentOverride != null -> decryptedContentOverride
        rawContent.isNullOrEmpty() -> null
        else -> EncryptionService.decryptChatMessage(rawContent, conversationId) ?: rawContent
    }
    var locationLatitude = (data["latitude"] as? Number)?.toDouble()
    var locationLongitude = (data["longitude"] as? Number)?.toDouble()
    var locationName = data["locationName"] as? String
    var locationAddress = data["locationAddress"] as? String
    val content = when (type) {
        MessageType.CHAT_NOTICE -> rawContent
        MessageType.LOCATION -> {
            ChatLocationPayload.decode(decryptedContent.orEmpty())?.let { payload ->
                locationLatitude = payload.lat
                locationLongitude = payload.lng
                locationName = payload.name ?: locationName
                locationAddress = payload.address ?: locationAddress
            }
            null
        }
        else -> decryptedContent
    }
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
            resolveEncryptedMediaForDisplay(
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
    fun stringList(key: String): List<String>? = (data[key] as? List<*>)?.filterIsInstance<String>()
    val drawingData = when (val value = data["drawingData"]) {
        is ByteArray -> value
        is Blob -> value.toBytes()
        else -> null
    }
    // iOS: MessageStatus(rawValue:) ?? .sent
    val status = (data["status"] as? String)?.let(MessageStatus::from) ?: MessageStatus.SENT
    val parsed = EnhancedMessage(
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
        audioWaveform = (data["audioWaveform"] as? List<*>)
            ?.take(64)
            ?.mapNotNull { (it as? Number)?.toFloat()?.coerceIn(0f, 1f) }
            ?.takeIf { it.isNotEmpty() },
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
        liveLocationStoppedAt = parseFirestoreDate(data["liveLocationStoppedAt"]),
        liveLocationSessionId = data["liveLocationSessionId"] as? String,
        locationUpdatedAt = parseFirestoreDate(data["locationUpdatedAt"]),
        timestamp = timestamp,
        status = status,
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
        textOverlays = (data["textOverlays"] as? List<*>)?.mapNotNull {
            (it as? Map<String, Any?>)?.let(StoryTextOverlayMetadata::from)
        },
        stickers = (data["stickers"] as? List<*>)?.mapNotNull {
            (it as? Map<String, Any?>)?.let(StickerData::from)
        },
        drawingData = drawingData,
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
    return ViewOnceReplaySessionStore.apply(
        parsed,
        FirebaseAuth.getInstance().currentUser?.uid,
    )
}

/** Acepta Timestamp / Date / epoch ms — serverTimestamp pendiente a veces no llega como Timestamp. */
private fun parseFirestoreDate(value: Any?): Date? = when (value) {
    is Timestamp -> value.toDate()
    is Date -> value
    is Number -> Date(value.toLong())
    else -> null
}

suspend fun ChatService.resolveEncryptedMediaForMessage(
    message: EnhancedMessage,
    forceDownload: Boolean = false,
): CachedResolvedMedia? =
    encryptedMediaResolver.resolveForMessage(message, forceDownload)

suspend fun ChatService.resolveVideoThumbnail(
    message: EnhancedMessage,
    forceDownload: Boolean = false,
): String? =
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
