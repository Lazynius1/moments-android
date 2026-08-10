package com.moments.android.models.cache

import android.net.Uri
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.toMap
import com.moments.android.views.messaging.core.EncryptedChatMediaMetadata
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Arrays
import java.util.Date

/**
 * Port de `Models/Cache/CachedMessage.swift`.
 * Blobs JSON ≡ `Data` / JSONEncoder en iOS.
 */
data class CachedMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val typeString: String,
    val content: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaObjectPath: String? = null,
    val thumbnailObjectPath: String? = null,
    val mediaEncryptionData: ByteArray? = null,
    val thumbnailEncryptionData: ByteArray? = null,
    val mediaBatchId: String? = null,
    val duration: Double? = null,
    val audioWaveformData: ByteArray? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Ubicación (fija + en vivo). Paridad iOS: sin esto toEnhancedMessage pierde el live. */
    val locationName: String? = null,
    val locationAddress: String? = null,
    val isLiveLocation: Boolean? = null,
    val liveLocationExpiresAt: Date? = null,
    val liveLocationDuration: String? = null,
    val liveLocationStoppedAt: Date? = null,
    val liveLocationSessionId: String? = null,
    val locationUpdatedAt: Date? = null,
    val timestamp: Date,
    val statusString: String,
    val isRead: Boolean,
    val isDeleted: Boolean,
    val deletedAt: Date? = null,
    val editedAt: Date? = null,
    val reactionsData: ByteArray? = null,
    val replyTo: String? = null,
    val expirationDate: Date? = null,
    val isViewed: Boolean,
    val storyReplyDataEncoded: ByteArray? = null,
    val sharedMomentDataEncoded: ByteArray? = null,
    val sharedStoryDataEncoded: ByteArray? = null,
    val textOverlayLive: Boolean? = null,
    val textOverlaysData: ByteArray? = null,
    val stickersData: ByteArray? = null,
    val drawingData: ByteArray? = null,
    val viewedBy: List<String>? = null,
    val lastSyncedAt: Date = Date(),
    val isVanishModeMessage: Boolean = false,
    val vanishedFor: List<String> = emptyList(),
    val vanishExpiresAt: Date? = null,
) {
    /** ≡ iOS `toEnhancedMessage()`. */
    fun toEnhancedMessage(): EnhancedMessage {
        val type = MessageType.from(typeString)
        // iOS: MessageStatus(rawValue:) ?? .sent (no .pending)
        val status = MessageStatus.entries.firstOrNull { it.raw == statusString } ?: MessageStatus.SENT
        return EnhancedMessage(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            type = type,
            content = content,
            mediaUrl = sanitizedMediaURL(mediaUrl, type),
            thumbnailUrl = sanitizedMediaURL(thumbnailUrl, type),
            mediaObjectPath = mediaObjectPath,
            thumbnailObjectPath = thumbnailObjectPath,
            mediaEncryption = decodeMediaEncryption(mediaEncryptionData),
            thumbnailEncryption = decodeMediaEncryption(thumbnailEncryptionData),
            duration = duration,
            audioWaveform = decodeFloatList(audioWaveformData),
            fileName = fileName,
            fileSize = fileSize,
            mediaWidth = mediaWidth,
            mediaHeight = mediaHeight,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            locationAddress = locationAddress,
            isLiveLocation = isLiveLocation,
            liveLocationExpiresAt = liveLocationExpiresAt,
            liveLocationDuration = liveLocationDuration,
            liveLocationStoppedAt = liveLocationStoppedAt,
            liveLocationSessionId = liveLocationSessionId,
            locationUpdatedAt = locationUpdatedAt,
            timestamp = timestamp,
            status = status,
            isRead = isRead,
            isDeleted = isDeleted,
            deletedAt = deletedAt,
            editedAt = editedAt,
            reactions = decodeReactions(reactionsData),
            replyTo = replyTo,
            expirationDate = expirationDate,
            isViewed = isViewed,
            storyReplyData = decodeStringMap(storyReplyDataEncoded),
            sharedMomentData = decodeStringMap(sharedMomentDataEncoded),
            sharedStoryData = decodeStringMap(sharedStoryDataEncoded),
            mediaBatchId = mediaBatchId,
            textOverlayLive = textOverlayLive,
            textOverlays = decodeOverlays(textOverlaysData),
            stickers = decodeStickers(stickersData),
            drawingData = drawingData,
            viewedBy = viewedBy,
            isVanishModeMessage = isVanishModeMessage,
            vanishedFor = vanishedFor,
            vanishExpiresAt = vanishExpiresAt,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedMessage) return false
        return id == other.id &&
            conversationId == other.conversationId &&
            senderId == other.senderId &&
            typeString == other.typeString &&
            content == other.content &&
            mediaUrl == other.mediaUrl &&
            thumbnailUrl == other.thumbnailUrl &&
            mediaObjectPath == other.mediaObjectPath &&
            thumbnailObjectPath == other.thumbnailObjectPath &&
            Arrays.equals(mediaEncryptionData, other.mediaEncryptionData) &&
            Arrays.equals(thumbnailEncryptionData, other.thumbnailEncryptionData) &&
            mediaBatchId == other.mediaBatchId &&
            duration == other.duration &&
            Arrays.equals(audioWaveformData, other.audioWaveformData) &&
            fileName == other.fileName &&
            fileSize == other.fileSize &&
            mediaWidth == other.mediaWidth &&
            mediaHeight == other.mediaHeight &&
            latitude == other.latitude &&
            longitude == other.longitude &&
            locationName == other.locationName &&
            locationAddress == other.locationAddress &&
            isLiveLocation == other.isLiveLocation &&
            liveLocationExpiresAt == other.liveLocationExpiresAt &&
            liveLocationDuration == other.liveLocationDuration &&
            liveLocationStoppedAt == other.liveLocationStoppedAt &&
            liveLocationSessionId == other.liveLocationSessionId &&
            locationUpdatedAt == other.locationUpdatedAt &&
            timestamp == other.timestamp &&
            statusString == other.statusString &&
            isRead == other.isRead &&
            isDeleted == other.isDeleted &&
            deletedAt == other.deletedAt &&
            editedAt == other.editedAt &&
            Arrays.equals(reactionsData, other.reactionsData) &&
            replyTo == other.replyTo &&
            expirationDate == other.expirationDate &&
            isViewed == other.isViewed &&
            Arrays.equals(storyReplyDataEncoded, other.storyReplyDataEncoded) &&
            Arrays.equals(sharedMomentDataEncoded, other.sharedMomentDataEncoded) &&
            Arrays.equals(sharedStoryDataEncoded, other.sharedStoryDataEncoded) &&
            textOverlayLive == other.textOverlayLive &&
            Arrays.equals(textOverlaysData, other.textOverlaysData) &&
            Arrays.equals(stickersData, other.stickersData) &&
            Arrays.equals(drawingData, other.drawingData) &&
            viewedBy == other.viewedBy &&
            lastSyncedAt == other.lastSyncedAt &&
            isVanishModeMessage == other.isVanishModeMessage &&
            vanishedFor == other.vanishedFor &&
            vanishExpiresAt == other.vanishExpiresAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + typeString.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        result = 31 * result + (mediaUrl?.hashCode() ?: 0)
        result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
        result = 31 * result + (mediaObjectPath?.hashCode() ?: 0)
        result = 31 * result + (thumbnailObjectPath?.hashCode() ?: 0)
        result = 31 * result + (mediaEncryptionData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (thumbnailEncryptionData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (mediaBatchId?.hashCode() ?: 0)
        result = 31 * result + (duration?.hashCode() ?: 0)
        result = 31 * result + (audioWaveformData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (fileSize?.hashCode() ?: 0)
        result = 31 * result + (mediaWidth ?: 0)
        result = 31 * result + (mediaHeight ?: 0)
        result = 31 * result + (latitude?.hashCode() ?: 0)
        result = 31 * result + (longitude?.hashCode() ?: 0)
        result = 31 * result + (locationName?.hashCode() ?: 0)
        result = 31 * result + (locationAddress?.hashCode() ?: 0)
        result = 31 * result + (isLiveLocation?.hashCode() ?: 0)
        result = 31 * result + (liveLocationExpiresAt?.hashCode() ?: 0)
        result = 31 * result + (liveLocationDuration?.hashCode() ?: 0)
        result = 31 * result + (liveLocationStoppedAt?.hashCode() ?: 0)
        result = 31 * result + (liveLocationSessionId?.hashCode() ?: 0)
        result = 31 * result + (locationUpdatedAt?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + statusString.hashCode()
        result = 31 * result + isRead.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + (editedAt?.hashCode() ?: 0)
        result = 31 * result + (reactionsData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (replyTo?.hashCode() ?: 0)
        result = 31 * result + (expirationDate?.hashCode() ?: 0)
        result = 31 * result + isViewed.hashCode()
        result = 31 * result + (storyReplyDataEncoded?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (sharedMomentDataEncoded?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (sharedStoryDataEncoded?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (textOverlayLive?.hashCode() ?: 0)
        result = 31 * result + (textOverlaysData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (stickersData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (drawingData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (viewedBy?.hashCode() ?: 0)
        result = 31 * result + lastSyncedAt.hashCode()
        result = 31 * result + isVanishModeMessage.hashCode()
        result = 31 * result + vanishedFor.hashCode()
        result = 31 * result + (vanishExpiresAt?.hashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * ≡ iOS `sanitizedMediaURL`.
         * No persistir `file://` muertos ni cache local de GIF/sticker.
         */
        fun sanitizedMediaURL(url: String?, type: MessageType): String? {
            if (url.isNullOrEmpty()) return null
            val uri = runCatching { Uri.parse(url) }.getOrNull()
            val isFile = uri?.scheme.equals("file", ignoreCase = true)
            if ((type == MessageType.GIF || type == MessageType.STICKER) && isFile) return null
            if (isFile) {
                val path = uri?.path ?: return null
                if (!File(path).exists()) return null
            }
            return url
        }

        /** ≡ iOS `from(_ message:)`. */
        fun from(message: EnhancedMessage): CachedMessage = CachedMessage(
            id = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            typeString = message.type.raw,
            content = message.content,
            mediaUrl = sanitizedMediaURL(message.mediaUrl, message.type),
            thumbnailUrl = sanitizedMediaURL(message.thumbnailUrl, message.type),
            mediaObjectPath = message.mediaObjectPath,
            thumbnailObjectPath = message.thumbnailObjectPath,
            mediaEncryptionData = message.mediaEncryption?.toJson()?.toString()?.toByteArray(),
            thumbnailEncryptionData = message.thumbnailEncryption?.toJson()?.toString()?.toByteArray(),
            mediaBatchId = message.mediaBatchId,
            duration = message.duration,
            audioWaveformData = encodeFloatList(message.audioWaveform),
            fileName = message.fileName,
            fileSize = message.fileSize,
            mediaWidth = message.mediaWidth,
            mediaHeight = message.mediaHeight,
            latitude = message.latitude,
            longitude = message.longitude,
            locationName = message.locationName,
            locationAddress = message.locationAddress,
            isLiveLocation = message.isLiveLocation,
            liveLocationExpiresAt = message.liveLocationExpiresAt,
            liveLocationDuration = message.liveLocationDuration,
            liveLocationStoppedAt = message.liveLocationStoppedAt,
            liveLocationSessionId = message.liveLocationSessionId,
            locationUpdatedAt = message.locationUpdatedAt,
            timestamp = message.timestamp,
            statusString = message.status.raw,
            isRead = message.isRead,
            isDeleted = message.isDeleted,
            deletedAt = message.deletedAt,
            editedAt = message.editedAt,
            reactionsData = encodeReactions(message.reactions),
            replyTo = message.replyTo,
            expirationDate = message.expirationDate,
            isViewed = message.isViewed,
            storyReplyDataEncoded = encodeStringMap(message.storyReplyData),
            sharedMomentDataEncoded = encodeStringMap(message.sharedMomentData),
            sharedStoryDataEncoded = encodeStringMap(message.sharedStoryData),
            textOverlayLive = message.textOverlayLive,
            textOverlaysData = encodeOverlays(message.textOverlays),
            stickersData = encodeStickers(message.stickers),
            drawingData = message.drawingData,
            viewedBy = message.viewedBy,
            lastSyncedAt = Date(),
            isVanishModeMessage = message.isVanishModeMessage,
            vanishedFor = message.vanishedFor,
            vanishExpiresAt = message.vanishExpiresAt,
        )

        private fun encodeReactions(reactions: Map<String, List<String>>?): ByteArray? =
            reactions?.let {
                JSONObject(it.mapValues { (_, v) -> JSONArray(v) }).toString().toByteArray()
            }

        private fun decodeReactions(data: ByteArray?): Map<String, List<String>>? {
            if (data == null) return null
            return runCatching {
                val obj = JSONObject(String(data))
                obj.keys().asSequence().associateWith { key ->
                    obj.getJSONArray(key).toStringList()
                }
            }.getOrNull()
        }

        private fun encodeStringMap(map: Map<String, String>?): ByteArray? =
            map?.let { JSONObject(it).toString().toByteArray() }

        private fun decodeStringMap(data: ByteArray?): Map<String, String>? {
            if (data == null) return null
            return runCatching {
                val obj = JSONObject(String(data))
                obj.keys().asSequence().associateWith { key -> obj.getString(key) }
            }.getOrNull()
        }

        private fun encodeFloatList(values: List<Float>?): ByteArray? =
            values?.let { JSONArray(it).toString().toByteArray() }

        private fun decodeFloatList(data: ByteArray?): List<Float>? {
            if (data == null) return null
            return runCatching {
                val arr = JSONArray(String(data))
                (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            }.getOrNull()
        }

        private fun encodeOverlays(overlays: List<StoryTextOverlayMetadata>?): ByteArray? {
            if (overlays == null) return null
            return runCatching {
                JSONArray().apply {
                    overlays.forEach { put(JSONObject(jsonSafeMap(it.toMap()))) }
                }.toString().toByteArray()
            }.getOrNull()
        }

        private fun decodeOverlays(data: ByteArray?): List<StoryTextOverlayMetadata>? {
            if (data == null) return null
            return runCatching {
                val arr = JSONArray(String(data))
                (0 until arr.length()).map { i ->
                    StoryTextOverlayMetadata.from(arr.getJSONObject(i).toAnyMap())
                }
            }.getOrNull()
        }

        private fun encodeStickers(stickers: List<StickerData>?): ByteArray? {
            if (stickers == null) return null
            return runCatching {
                JSONArray().apply {
                    stickers.forEach { put(JSONObject(jsonSafeMap(it.toMap()))) }
                }.toString().toByteArray()
            }.getOrNull()
        }

        private fun decodeStickers(data: ByteArray?): List<StickerData>? {
            if (data == null) return null
            return runCatching {
                val arr = JSONArray(String(data))
                (0 until arr.length()).map { i ->
                    StickerData.from(arr.getJSONObject(i).toAnyMap())
                }
            }.getOrNull()
        }

        private fun decodeMediaEncryption(data: ByteArray?): EncryptedChatMediaMetadata? {
            if (data == null) return null
            return runCatching {
                EncryptedChatMediaMetadata.fromJson(JSONObject(String(data)))
            }.getOrNull()
        }

        private fun jsonSafeMap(map: Map<String, Any>): Map<String, Any?> =
            map.mapValues { (_, v) -> jsonSafe(v) }

        private fun jsonSafe(value: Any?): Any? = when (value) {
            null -> null
            is String, is Boolean, is Int, is Long, is Double, is Float -> value
            is Number -> value
            is Date -> value.time
            is Map<*, *> -> JSONObject().also { obj ->
                value.forEach { (k, v) ->
                    if (k is String) obj.put(k, jsonSafe(v) ?: JSONObject.NULL)
                }
            }
            is List<*> -> JSONArray().also { arr ->
                value.forEach { arr.put(jsonSafe(it) ?: JSONObject.NULL) }
            }
            else -> {
                val toDate = runCatching {
                    value.javaClass.getMethod("toDate").invoke(value) as? Date
                }.getOrNull()
                toDate?.time ?: value.toString()
            }
        }

        private fun JSONArray.toStringList(): List<String> =
            (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

        private fun JSONObject.toAnyMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
            when (val value = opt(key)) {
                null, JSONObject.NULL -> null
                is JSONObject -> value.toAnyMap()
                is JSONArray -> (0 until value.length()).map { i ->
                    when (val item = value.opt(i)) {
                        null, JSONObject.NULL -> null
                        is JSONObject -> item.toAnyMap()
                        else -> item
                    }
                }
                else -> value
            }
        }
    }
}
