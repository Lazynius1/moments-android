package com.moments.android.models

import com.google.firebase.Timestamp
import com.moments.android.models.cache.CachedMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

// MARK: - Tipos de mensaje / estado / media (MessageModel.swift)

enum class MessageType(val raw: String) {
    TEXT("text"), IMAGE("image"), VIDEO("video"), AUDIO("audio"), GIF("gif"),
    STICKER("sticker"), LOCATION("location"), FILE("file"), EPHEMERAL("ephemeral"),
    SHARED_MOMENT("sharedMoment"), SHARED_STORY("sharedStory"),
    VIEW_ONCE_IMAGE("viewOnceImage"), VIEW_ONCE_VIDEO("viewOnceVideo"), CHAT_NOTICE("chatNotice");

    val isViewOnce: Boolean get() = this == VIEW_ONCE_IMAGE || this == VIEW_ONCE_VIDEO

    companion object {
        fun from(raw: String?): MessageType = entries.firstOrNull { it.raw == raw } ?: TEXT
    }
}

enum class MessageStatus(val raw: String) {
    PENDING("pending"), SENDING("sending"), SENT("sent"), DELIVERED("delivered"),
    READ("read"), FAILED("failed");

    companion object {
        fun from(raw: String?): MessageStatus = entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

enum class ChatMediaPurpose(val raw: String) {
    PRIMARY("primary"), THUMBNAIL("thumbnail");

    companion object {
        fun from(raw: String?): ChatMediaPurpose? = entries.firstOrNull { it.raw == raw }
    }
}

data class EncryptedChatMediaMetadata(
    val version: String = "1.0",
    val algorithm: String = "AES.GCM+HKDF-SHA256",
    val purpose: ChatMediaPurpose,
    val mediaId: String,
    val contentType: String,
    val fileExtension: String,
    val plaintextSize: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("algorithm", algorithm)
        put("purpose", purpose.raw)
        put("mediaId", mediaId)
        put("contentType", contentType)
        put("fileExtension", fileExtension)
        put("plaintextSize", plaintextSize)
    }

    companion object {
        fun fromJson(obj: JSONObject?): EncryptedChatMediaMetadata? {
            if (obj == null) return null
            val purpose = ChatMediaPurpose.from(obj.optString("purpose")) ?: return null
            return EncryptedChatMediaMetadata(
                version = obj.optString("version", "1.0"),
                algorithm = obj.optString("algorithm", "AES.GCM+HKDF-SHA256"),
                purpose = purpose,
                mediaId = obj.optString("mediaId"),
                contentType = obj.optString("contentType"),
                fileExtension = obj.optString("fileExtension"),
                plaintextSize = obj.optLong("plaintextSize"),
            )
        }
    }
}

data class EnhancedMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val type: MessageType = MessageType.TEXT,
    val content: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaObjectPath: String? = null,
    val thumbnailObjectPath: String? = null,
    val mediaEncryption: EncryptedChatMediaMetadata? = null,
    val thumbnailEncryption: EncryptedChatMediaMetadata? = null,
    val duration: Double? = null,
    val audioWaveform: List<Float>? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Date = Date(),
    var status: MessageStatus = MessageStatus.PENDING,
    var isRead: Boolean = false,
    var isDeleted: Boolean = false,
    var deletedAt: Date? = null,
    var editedAt: Date? = null,
    var reactions: Map<String, List<String>>? = null,
    var replyTo: String? = null,
    var expirationDate: Date? = null,
    var isViewed: Boolean = false,
    val mediaBatchId: String? = null,
    var isVanishModeMessage: Boolean = false,
    var vanishedFor: List<String> = emptyList(),
    var vanishExpiresAt: Date? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("conversationId", conversationId)
        put("senderId", senderId)
        put("type", type.raw)
        content?.let { put("content", it) }
        mediaUrl?.let { put("mediaUrl", it) }
        thumbnailUrl?.let { put("thumbnailUrl", it) }
        mediaObjectPath?.let { put("mediaObjectPath", it) }
        thumbnailObjectPath?.let { put("thumbnailObjectPath", it) }
        mediaEncryption?.toJson()?.let { put("mediaEncryption", it) }
        thumbnailEncryption?.toJson()?.let { put("thumbnailEncryption", it) }
        duration?.let { put("duration", it) }
        audioWaveform?.let { put("audioWaveform", JSONArray(it)) }
        fileName?.let { put("fileName", it) }
        fileSize?.let { put("fileSize", it) }
        mediaWidth?.let { put("mediaWidth", it) }
        mediaHeight?.let { put("mediaHeight", it) }
        latitude?.let { put("latitude", it) }
        longitude?.let { put("longitude", it) }
        put("timestamp", timestamp.time)
        put("status", status.raw)
        put("isRead", isRead)
        put("isDeleted", isDeleted)
        deletedAt?.let { put("deletedAt", it.time) }
        editedAt?.let { put("editedAt", it.time) }
        reactions?.let { map ->
            put("reactions", JSONObject().apply {
                map.forEach { (k, v) -> put(k, JSONArray(v)) }
            })
        }
        replyTo?.let { put("replyTo", it) }
        expirationDate?.let { put("expirationDate", it.time) }
        put("isViewed", isViewed)
        mediaBatchId?.let { put("mediaBatchId", it) }
        put("isVanishModeMessage", isVanishModeMessage)
        put("vanishedFor", JSONArray(vanishedFor))
        vanishExpiresAt?.let { put("vanishExpiresAt", it.time) }
    }

    fun toCachedMessage(): CachedMessage = CachedMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        typeString = type.raw,
        content = content,
        mediaUrl = mediaUrl,
        thumbnailUrl = thumbnailUrl,
        mediaObjectPath = mediaObjectPath,
        thumbnailObjectPath = thumbnailObjectPath,
        mediaEncryptionData = mediaEncryption?.toJson()?.toString()?.toByteArray(),
        thumbnailEncryptionData = thumbnailEncryption?.toJson()?.toString()?.toByteArray(),
        mediaBatchId = mediaBatchId,
        duration = duration,
        audioWaveformData = audioWaveform?.let { JSONArray(it).toString().toByteArray() },
        fileName = fileName,
        fileSize = fileSize,
        mediaWidth = mediaWidth,
        mediaHeight = mediaHeight,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        statusString = status.raw,
        isRead = isRead,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
        editedAt = editedAt,
        reactionsData = reactions?.let { JSONObject(it.mapValues { (_, v) -> JSONArray(v) }).toString().toByteArray() },
        replyTo = replyTo,
        expirationDate = expirationDate,
        isViewed = isViewed,
        isVanishModeMessage = isVanishModeMessage,
        vanishedFor = vanishedFor,
        vanishExpiresAt = vanishExpiresAt,
    )

    companion object {
        fun fromJson(obj: JSONObject): EnhancedMessage = EnhancedMessage(
            id = obj.getString("id"),
            conversationId = obj.getString("conversationId"),
            senderId = obj.getString("senderId"),
            type = MessageType.from(obj.optString("type")),
            content = obj.optString("content").takeIf { obj.has("content") && !obj.isNull("content") },
            mediaUrl = obj.optString("mediaUrl").takeIf { obj.has("mediaUrl") && !obj.isNull("mediaUrl") },
            thumbnailUrl = obj.optString("thumbnailUrl").takeIf { obj.has("thumbnailUrl") && !obj.isNull("thumbnailUrl") },
            mediaObjectPath = obj.optString("mediaObjectPath").takeIf { obj.has("mediaObjectPath") && !obj.isNull("mediaObjectPath") },
            thumbnailObjectPath = obj.optString("thumbnailObjectPath").takeIf { obj.has("thumbnailObjectPath") && !obj.isNull("thumbnailObjectPath") },
            mediaEncryption = EncryptedChatMediaMetadata.fromJson(obj.optJSONObject("mediaEncryption")),
            thumbnailEncryption = EncryptedChatMediaMetadata.fromJson(obj.optJSONObject("thumbnailEncryption")),
            duration = obj.optDouble("duration").takeIf { obj.has("duration") && !obj.isNull("duration") },
            audioWaveform = obj.optJSONArray("audioWaveform")?.let { arr ->
                (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            },
            fileName = obj.optString("fileName").takeIf { obj.has("fileName") && !obj.isNull("fileName") },
            fileSize = obj.optLong("fileSize").takeIf { obj.has("fileSize") },
            mediaWidth = obj.optInt("mediaWidth").takeIf { obj.has("mediaWidth") },
            mediaHeight = obj.optInt("mediaHeight").takeIf { obj.has("mediaHeight") },
            latitude = obj.optDouble("latitude").takeIf { obj.has("latitude") && !obj.isNull("latitude") },
            longitude = obj.optDouble("longitude").takeIf { obj.has("longitude") && !obj.isNull("longitude") },
            timestamp = Date(obj.optLong("timestamp", System.currentTimeMillis())),
            status = MessageStatus.from(obj.optString("status")),
            isRead = obj.optBoolean("isRead"),
            isDeleted = obj.optBoolean("isDeleted"),
            deletedAt = obj.optLong("deletedAt").takeIf { obj.has("deletedAt") }?.let { Date(it) },
            editedAt = obj.optLong("editedAt").takeIf { obj.has("editedAt") }?.let { Date(it) },
            replyTo = obj.optString("replyTo").takeIf { obj.has("replyTo") && !obj.isNull("replyTo") },
            expirationDate = obj.optLong("expirationDate").takeIf { obj.has("expirationDate") }?.let { Date(it) },
            isViewed = obj.optBoolean("isViewed"),
            mediaBatchId = obj.optString("mediaBatchId").takeIf { obj.has("mediaBatchId") && !obj.isNull("mediaBatchId") },
            isVanishModeMessage = obj.optBoolean("isVanishModeMessage"),
            vanishedFor = obj.optJSONArray("vanishedFor")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            vanishExpiresAt = obj.optLong("vanishExpiresAt").takeIf { obj.has("vanishExpiresAt") }?.let { Date(it) },
        )

        fun fromCached(cached: CachedMessage): EnhancedMessage = EnhancedMessage(
            id = cached.id,
            conversationId = cached.conversationId,
            senderId = cached.senderId,
            type = MessageType.from(cached.typeString),
            content = cached.content,
            mediaUrl = cached.mediaUrl,
            thumbnailUrl = cached.thumbnailUrl,
            mediaObjectPath = cached.mediaObjectPath,
            thumbnailObjectPath = cached.thumbnailObjectPath,
            mediaEncryption = cached.mediaEncryptionData?.let {
                runCatching { EncryptedChatMediaMetadata.fromJson(JSONObject(String(it))) }.getOrNull()
            },
            thumbnailEncryption = cached.thumbnailEncryptionData?.let {
                runCatching { EncryptedChatMediaMetadata.fromJson(JSONObject(String(it))) }.getOrNull()
            },
            duration = cached.duration,
            fileName = cached.fileName,
            fileSize = cached.fileSize,
            mediaWidth = cached.mediaWidth,
            mediaHeight = cached.mediaHeight,
            latitude = cached.latitude,
            longitude = cached.longitude,
            timestamp = cached.timestamp,
            status = MessageStatus.from(cached.statusString),
            isRead = cached.isRead,
            isDeleted = cached.isDeleted,
            deletedAt = cached.deletedAt,
            editedAt = cached.editedAt,
            replyTo = cached.replyTo,
            expirationDate = cached.expirationDate,
            isViewed = cached.isViewed,
            mediaBatchId = cached.mediaBatchId,
            isVanishModeMessage = cached.isVanishModeMessage,
            vanishedFor = cached.vanishedFor,
            vanishExpiresAt = cached.vanishExpiresAt,
        )
    }
}

data class MessageSyncCursor(
    val timestamp: Date,
    val messageId: String,
) : Comparable<MessageSyncCursor> {
    fun isAfter(other: MessageSyncCursor): Boolean = this > other

    override fun compareTo(other: MessageSyncCursor): Int {
        val ts = timestamp.compareTo(other.timestamp)
        if (ts != 0) return ts
        return messageId.compareTo(other.messageId)
    }
}

data class Conversation(
    val id: String? = null,
    val participants: List<String> = emptyList(),
    val lastMessage: String? = null,
    val timestamp: Date = Date(),
    val readStatus: Map<String, Boolean> = emptyMap(),
    val otherParticipantId: String = "",
    val otherParticipantUsername: String? = null,
    val otherParticipantProfileImagePath: String? = null,
)

data class MessageRequest(
    val id: String? = null,
    val senderId: String,
    val senderUsername: String? = null,
    val senderProfileImagePath: String? = null,
    val receiverId: String,
    val message: String,
    val timestamp: Date = Date(),
    val status: RequestStatus = RequestStatus.PENDING,
    val messageType: MessageType = MessageType.TEXT,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
) {
    enum class RequestStatus(val raw: String) {
        PENDING("pending"), ACCEPTED("accepted"), REJECTED("rejected"), BLOCKED("blocked");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: PENDING }
    }

    fun encode(): Map<String, Any?> = mapOf(
        "senderId" to senderId,
        "createdBy" to senderId,
        "senderUsername" to senderUsername,
        "senderProfileImagePath" to senderProfileImagePath,
        "receiverId" to receiverId,
        "message" to message,
        "timestamp" to Timestamp(timestamp),
        "status" to status.raw,
        "messageType" to messageType.raw,
        "mediaUrl" to mediaUrl,
        "thumbnailUrl" to thumbnailUrl,
    )

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>, id: String): MessageRequest? {
            val senderId = data["senderId"] as? String ?: return null
            val receiverId = data["receiverId"] as? String ?: return null
            val message = data["message"] as? String ?: return null
            val statusRaw = data["status"] as? String ?: return null
            val messageTypeRaw = data["messageType"] as? String ?: return null
            val timestamp = when (val ts = data["timestamp"]) {
                is Timestamp -> ts.toDate()
                is Date -> ts
                else -> return null
            }
            return MessageRequest(
                id = id,
                senderId = senderId,
                senderUsername = data["senderUsername"] as? String,
                senderProfileImagePath = data["senderProfileImagePath"] as? String,
                receiverId = receiverId,
                message = message,
                timestamp = timestamp,
                status = RequestStatus.from(statusRaw),
                messageType = MessageType.from(messageTypeRaw),
                mediaUrl = data["mediaUrl"] as? String,
                thumbnailUrl = data["thumbnailUrl"] as? String,
            )
        }
    }
}

data class AcceptMessageRequestResult(
    val conversationId: String,
    val messageId: String,
)

data class PresenceDisplay(
    val status: OnlineStatus,
    val statusText: String,
    val supplementalText: String?,
)

fun encodeMessages(messages: List<EnhancedMessage>): ByteArray =
    JSONArray().apply { messages.forEach { put(it.toJson()) } }.toString().toByteArray()

fun decodeMessages(data: ByteArray): List<EnhancedMessage> {
    val arr = JSONArray(String(data))
    return (0 until arr.length()).map { EnhancedMessage.fromJson(arr.getJSONObject(it)) }
}
