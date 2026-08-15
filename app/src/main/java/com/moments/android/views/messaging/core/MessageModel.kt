package com.moments.android.views.messaging.core

import android.content.Context
import android.text.format.Formatter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.models.MessageRequestPolicy
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.OnlineStatus
import com.moments.android.models.WrappedConversationKey
import com.moments.android.models.toMap
import com.moments.android.models.cache.CachedMessage
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.content.BackendFeedService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.utilities.MomentsFormat
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Base64
import java.util.UUID

// MARK: - Tipos de mensaje / estado / media (MessageModel.swift)

enum class MessageType(val raw: String) {
    TEXT("text"), IMAGE("image"), VIDEO("video"), AUDIO("audio"), GIF("gif"),
    STICKER("sticker"), LOCATION("location"), FILE("file"), EPHEMERAL("ephemeral"),
    SHARED_MOMENT("sharedMoment"), SHARED_STORY("sharedStory"),
    VIEW_ONCE_IMAGE("viewOnceImage"), VIEW_ONCE_VIDEO("viewOnceVideo"), CHAT_NOTICE("chatNotice");

    val isViewOnce: Boolean get() = this == VIEW_ONCE_IMAGE || this == VIEW_ONCE_VIDEO
    val isChatNotice: Boolean get() = this == CHAT_NOTICE

    /** ≡ `MessageType.iconName` (SF Symbol names; UI mapea a Material). */
    val iconName: String
        get() = when (this) {
            TEXT -> "text.bubble"
            IMAGE -> "photo"
            VIDEO -> "video"
            AUDIO -> "mic"
            GIF -> "photo.on.rectangle.angled"
            STICKER -> "face.smiling"
            LOCATION -> "location"
            FILE -> "doc"
            EPHEMERAL -> "timer"
            SHARED_MOMENT -> "square.and.arrow.up"
            SHARED_STORY -> "paperplane.fill"
            VIEW_ONCE_IMAGE -> "camera.circle"
            VIEW_ONCE_VIDEO -> "video.circle"
            CHAT_NOTICE -> "timer"
        }

    fun displayName(context: Context): String = when (this) {
        TEXT -> context.getString(R.string.message_type_text)
        IMAGE -> context.getString(R.string.message_type_photo)
        VIDEO -> context.getString(R.string.message_type_video)
        AUDIO -> context.getString(R.string.message_type_audio)
        GIF -> "GIF"
        STICKER -> "Sticker"
        LOCATION -> context.getString(R.string.message_type_location)
        FILE -> context.getString(R.string.message_type_file)
        EPHEMERAL -> context.getString(R.string.chat_view_once_label)
        SHARED_MOMENT -> context.getString(R.string.chat_preview_shared_moment)
        SHARED_STORY -> context.getString(R.string.chat_preview_shared_story)
        VIEW_ONCE_IMAGE -> context.getString(R.string.chat_view_once_photo) +
            " (" + context.getString(R.string.chat_view_once_label) + ")"
        VIEW_ONCE_VIDEO -> context.getString(R.string.chat_view_once_video) +
            " (" + context.getString(R.string.chat_view_once_label) + ")"
        CHAT_NOTICE -> ""
    }

    companion object {
        fun from(raw: String?): MessageType = entries.firstOrNull { it.raw == raw } ?: TEXT
    }
}

/** Port de `MessageType.conversationPreview` (MessageModel.swift). */
fun MessageType.conversationPreview(context: android.content.Context): String = when (this) {
    MessageType.TEXT -> context.getString(com.moments.android.R.string.chat_preview_text)
    MessageType.IMAGE, MessageType.VIEW_ONCE_IMAGE -> context.getString(com.moments.android.R.string.chat_preview_photo)
    MessageType.VIDEO, MessageType.VIEW_ONCE_VIDEO -> context.getString(com.moments.android.R.string.chat_preview_video)
    MessageType.AUDIO -> context.getString(com.moments.android.R.string.chat_preview_audio)
    MessageType.GIF -> context.getString(com.moments.android.R.string.chat_preview_gif)
    MessageType.STICKER -> context.getString(com.moments.android.R.string.chat_preview_sticker)
    MessageType.LOCATION -> context.getString(com.moments.android.R.string.chat_preview_location)
    MessageType.FILE -> context.getString(com.moments.android.R.string.chat_preview_file)
    MessageType.EPHEMERAL -> context.getString(com.moments.android.R.string.chat_preview_ephemeral)
    MessageType.SHARED_MOMENT -> context.getString(com.moments.android.R.string.chat_preview_shared_moment)
    MessageType.SHARED_STORY -> context.getString(com.moments.android.R.string.chat_preview_shared_story)
    MessageType.CHAT_NOTICE -> ""
}

private val neutralConversationPreviewPrefixes = listOf("💬", "📷", "🎥", "🎵", "🎞", "😊", "📍", "📎", "📸", "⏱")

/** ≡ `sanitizedConversationPreview(_:encryptionVersion:)` — usa preview neutro de texto. */
fun sanitizedConversationPreview(
    context: Context,
    rawPreview: String?,
    encryptionVersion: String?,
): String = sanitizedConversationPreview(
    rawPreview,
    encryptionVersion,
    neutralTextPreview = context.getString(R.string.chat_preview_text),
)

fun sanitizedConversationPreview(rawPreview: String?, encryptionVersion: String?, neutralTextPreview: String): String {
    val preview = rawPreview?.trim().orEmpty()
    if (encryptionVersion?.startsWith("3") != true) return preview
    if (preview.isEmpty()) return neutralTextPreview
    return if (neutralConversationPreviewPrefixes.any(preview::startsWith)) preview else neutralTextPreview
}

enum class MessageStatus(val raw: String) {
    PENDING("pending"), SENDING("sending"), SENT("sent"), DELIVERED("delivered"),
    READ("read"), FAILED("failed");

    /** ≡ `MessageStatus.displayName`. */
    fun displayName(context: Context): String = context.getString(
        when (this) {
            PENDING -> R.string.chat_status_pending
            SENDING -> R.string.chat_status_sending
            SENT -> R.string.chat_status_sent
            DELIVERED -> R.string.chat_status_delivered
            READ -> R.string.chat_status_read
            FAILED -> R.string.chat_status_failed
        },
    )

    /** ≡ `MessageStatus.iconName` (SF Symbol; UI mapea a Material). */
    val iconName: String
        get() = when (this) {
            PENDING, SENDING -> "clock"
            SENT -> "checkmark"
            DELIVERED -> "checkmark.circle"
            READ -> "checkmark.circle.fill"
            FAILED -> "exclamationmark.triangle"
        }

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
    override val senderId: String,
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
    val locationName: String? = null,
    val locationAddress: String? = null,
    val isLiveLocation: Boolean? = null,
    val liveLocationExpiresAt: Date? = null,
    val liveLocationDuration: String? = null,
    var liveLocationStoppedAt: Date? = null,
    val liveLocationSessionId: String? = null,
    val locationUpdatedAt: Date? = null,
    override val timestamp: Date = Date(),
    var status: MessageStatus = MessageStatus.PENDING,
    override var isRead: Boolean = false,
    var isDeleted: Boolean = false,
    var deletedAt: Date? = null,
    var editedAt: Date? = null,
    var reactions: Map<String, List<String>>? = null,
    var replyTo: String? = null,
    /** Payload de `StoryReplyData` para previews de respuestas a stories. */
    val storyReplyData: Map<String, String>? = null,
    val sharedMomentData: Map<String, String>? = null,
    val sharedStoryData: Map<String, String>? = null,
    var expirationDate: Date? = null,
    var isViewed: Boolean = false,
    val mediaBatchId: String? = null,
    var textOverlayLive: Boolean? = null,
    var textOverlays: List<StoryTextOverlayMetadata>? = null,
    var stickers: List<StickerData>? = null,
    var drawingData: ByteArray? = null,
    var viewedBy: List<String>? = null,
    var allowReplay: Boolean? = null,
    var replayedBy: List<String>? = null,
    var readBy: List<String>? = null,
    /** Hora real de lectura por usuario; ausente cuando los acuses están desactivados. */
    var readAtBy: Map<String, Date>? = null,
    var starredBy: List<String>? = null,
    var isForwarded: Boolean? = null,
    var isVanishModeMessage: Boolean = false,
    var vanishedFor: List<String> = emptyList(),
    var vanishExpiresAt: Date? = null,
    /** Session-only (no Firestore) — ≡ iOS `@Published` en EnhancedMessage. */
    var replayAvailableInCurrentChatSession: Boolean = false,
    var replayConsumedInCurrentChatSession: Boolean = false,
) : MessageProtocol {
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
        locationName?.let { put("locationName", it) }
        locationAddress?.let { put("locationAddress", it) }
        isLiveLocation?.let { put("isLiveLocation", it) }
        liveLocationExpiresAt?.let { put("liveLocationExpiresAt", it.time) }
        liveLocationDuration?.let { put("liveLocationDuration", it) }
        liveLocationStoppedAt?.let { put("liveLocationStoppedAt", it.time) }
        liveLocationSessionId?.let { put("liveLocationSessionId", it) }
        locationUpdatedAt?.let { put("locationUpdatedAt", it.time) }
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
        storyReplyData?.let { put("storyReplyData", JSONObject(it)) }
        sharedMomentData?.let { put("sharedMomentData", JSONObject(it)) }
        sharedStoryData?.let { put("sharedStoryData", JSONObject(it)) }
        expirationDate?.let { put("expirationDate", it.time) }
        put("isViewed", isViewed)
        mediaBatchId?.let { put("mediaBatchId", it) }
        textOverlayLive?.let { put("textOverlayLive", it) }
        textOverlays?.let { overlays -> put("textOverlays", JSONArray(overlays.map { JSONObject(it.toMap()) })) }
        stickers?.let { values -> put("stickers", JSONArray(values.map { JSONObject(it.toMap()) })) }
        drawingData?.let { put("drawingData", Base64.getEncoder().encodeToString(it)) }
        viewedBy?.let { put("viewedBy", JSONArray(it)) }
        allowReplay?.let { put("allowReplay", it) }
        replayedBy?.let { put("replayedBy", JSONArray(it)) }
        readBy?.let { put("readBy", JSONArray(it)) }
        readAtBy?.let { values ->
            put("readAtBy", JSONObject().apply {
                values.forEach { (userId, date) -> put(userId, date.time) }
            })
        }
        starredBy?.let { put("starredBy", JSONArray(it)) }
        isForwarded?.let { put("isForwarded", it) }
        put("isVanishModeMessage", isVanishModeMessage)
        put("vanishedFor", JSONArray(vanishedFor))
        vanishExpiresAt?.let { put("vanishExpiresAt", it.time) }
    }

    fun toCachedMessage(): CachedMessage = CachedMessage.from(this)

    fun isVanished(userId: String): Boolean = userId in vanishedFor
    fun isStarred(userId: String): Boolean = userId in starredBy.orEmpty()
    fun replacingContent(newContent: String?): EnhancedMessage = copy(content = newContent)
    val isExpired: Boolean get() = expirationDate?.before(Date()) == true
    val isLiveLocationMessage: Boolean get() = type == MessageType.LOCATION && isLiveLocation == true
    val isLiveLocationActive: Boolean
        get() {
            if (!isLiveLocationMessage) return false
            if (liveLocationStoppedAt != null) return false
            val expiresAt = liveLocationExpiresAt
            if (expiresAt != null && !Date().before(expiresAt)) return false
            return true
        }
    val isViewOnce: Boolean get() = type.isViewOnce
    fun hasBeenViewedBy(userId: String): Boolean = isViewOnce && userId in viewedBy.orEmpty()
    fun hasBeenReplayedBy(userId: String): Boolean = isViewOnce && userId in replayedBy.orEmpty()
    fun canReplayViewOnce(userId: String): Boolean =
        isViewOnce && allowReplay == true && senderId != userId && hasBeenViewedBy(userId) && !hasBeenReplayedBy(userId)

    /** ≡ `viewOnceStatus(for:)`. */
    fun viewOnceStatus(context: Context, currentUserId: String): String {
        if (!isViewOnce) return ""
        return if (senderId == currentUserId) {
            if (isViewed) context.getString(R.string.chat_view_once_viewed)
            else context.getString(R.string.chat_view_once_sent)
        } else {
            if (hasBeenViewedBy(currentUserId)) context.getString(R.string.chat_view_once_viewed)
            else context.getString(R.string.chat_view_once_tap_to_view)
        }
    }

    fun shouldShowViewOnceContent(userId: String): Boolean =
        !isViewOnce || senderId == userId || !hasBeenViewedBy(userId)

    val typeIcon: String get() = type.iconName
    val canBeAutoDeleted: Boolean get() = isViewOnce && isViewed && !isDeleted

    /** ≡ `EnhancedMessage.preview`. */
    fun preview(context: Context): String {
        if (isVanishModeMessage && type != MessageType.CHAT_NOTICE) {
            return type.conversationPreview(context)
        }
        return when (type) {
            MessageType.TEXT -> content.orEmpty()
            MessageType.IMAGE -> context.getString(R.string.chat_preview_image)
            MessageType.VIDEO -> context.getString(R.string.chat_preview_video)
            MessageType.AUDIO -> context.getString(R.string.chat_preview_audio)
            MessageType.GIF -> context.getString(R.string.chat_preview_gif)
            MessageType.STICKER -> context.getString(R.string.chat_preview_sticker)
            MessageType.LOCATION -> context.getString(R.string.chat_preview_location)
            MessageType.FILE -> "📎 ${fileName ?: context.getString(R.string.message_type_file)}"
            MessageType.EPHEMERAL -> context.getString(R.string.chat_preview_ephemeral_long)
            MessageType.SHARED_MOMENT -> context.getString(R.string.chat_preview_shared_moment)
            MessageType.SHARED_STORY -> context.getString(R.string.chat_preview_shared_story)
            MessageType.VIEW_ONCE_IMAGE -> context.getString(R.string.chat_preview_photo)
            MessageType.VIEW_ONCE_VIDEO -> context.getString(R.string.chat_preview_video)
            MessageType.CHAT_NOTICE -> chatNoticePreviewText(context, content.orEmpty())
        }
    }

    /** Preview truncado para lista de conversaciones. */
    fun conversationPreview(context: Context): String {
        if (type == MessageType.TEXT) {
            val text = content ?: return type.conversationPreview(context)
            return if (text.length > 50) text.take(47) + "..." else text
        }
        return type.conversationPreview(context)
    }

    val analyticsData: Map<String, Any> get() = buildMap {
        put("messageType", type.raw); put("hasMedia", mediaUrl != null); put("isViewOnce", isViewOnce); put("messageLength", content?.length ?: 0)
        if (isViewOnce) { put("viewOnceType", type.raw); put("hasBeenViewed", isViewed); put("viewerCount", viewedBy.orEmpty().size) }
        duration?.let { put("mediaDuration", it) }; fileSize?.let { put("fileSize", it) }
    }
    val analyticsEvent: String get() = if (isViewOnce) if (isViewed) "view_once_message_viewed" else "view_once_message_sent" else "message_sent"

    val relativeTimeString: String get() = MomentsFormat.relativeTime(from = timestamp)
    val absoluteTimeString: String get() = MomentsFormat.smartDate(from = timestamp, context = MomentsFormat.DateContext.MESSAGE_ABSOLUTE)
    val formattedFileSize: String?
        get() = fileSize?.let { Formatter.formatFileSize(MomentsFormat.requireContext(), it) }
    val formattedDuration: String?
        get() {
            val d = duration ?: return null
            val minutes = d.toInt() / 60
            val seconds = d.toInt() % 60
            return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "0:%02d".format(seconds)
        }

    private fun missingLocalFile(url: String?): Boolean = runCatching {
        if (url == null) return false
        val uri = android.net.Uri.parse(url)
        uri.scheme == "file" && (uri.path == null || !java.io.File(uri.path!!).isFile)
    }.getOrDefault(false)

    val hasMissingLocalMedia: Boolean get() = missingLocalFile(mediaUrl)
    val hasMissingLocalThumbnail: Boolean get() = missingLocalFile(thumbnailUrl)

    fun localMediaFileIsReachable(url: String): Boolean = runCatching {
        val uri = android.net.Uri.parse(url)
        uri.scheme != "file" || (uri.path != null && java.io.File(uri.path!!).isFile)
    }.getOrDefault(false)

    val needsVideoThumbnailForDisplay: Boolean
        get() {
            if (type != MessageType.VIDEO) return false
            val url = thumbnailUrl ?: return true
            return !localMediaFileIsReachable(url)
        }

    /** ≡ iOS `isMediaPendingResolution` (cache disco + file:// ausente). */
    val isMediaPendingResolution: Boolean
        get() {
            if (isDeleted || status == MessageStatus.SENDING) return false
            val canResolve = mediaObjectPath != null && mediaEncryption != null
            fun diskMediaReachable(): Boolean {
                if (mediaUrl != null && !missingLocalFile(mediaUrl)) return true
                val disk = ChatCacheStore.localURLsIfPresent(this)
                val candidate = disk.first ?: mediaUrl
                return candidate?.let(::localMediaFileIsReachable) == true
            }
            fun diskThumbReachable(): Boolean {
                if (thumbnailUrl != null && !missingLocalFile(thumbnailUrl)) return true
                val disk = ChatCacheStore.localURLsIfPresent(this)
                val candidate = disk.second ?: thumbnailUrl
                return candidate?.let(::localMediaFileIsReachable) == true
            }
            return when (type) {
                MessageType.IMAGE, MessageType.EPHEMERAL -> {
                    if (diskMediaReachable()) false
                    else if (mediaUrl == null) canResolve
                    else missingLocalFile(mediaUrl) && canResolve
                }
                MessageType.VIDEO -> {
                    if (diskThumbReachable() || diskMediaReachable()) false
                    else {
                        val canResolveThumb = thumbnailObjectPath != null && thumbnailEncryption != null
                        canResolve || canResolveThumb
                    }
                }
                MessageType.GIF, MessageType.STICKER -> when {
                    mediaUrl == null -> canResolve
                    missingLocalFile(mediaUrl) -> canResolve
                    else -> false
                }
                else -> false
            }
        }

    val hasLocalVideoFileReady: Boolean
        get() = type == MessageType.VIDEO && mediaUrl?.let(::localMediaFileIsReachable) == true

    val hasLocalMediaReadyForViewer: Boolean
        get() = when (type) {
            MessageType.IMAGE, MessageType.EPHEMERAL -> mediaUrl?.let(::localMediaFileIsReachable) == true
            MessageType.VIDEO -> hasLocalVideoFileReady
            else -> false
        }

    val needsDownloadForPlayback: Boolean
        get() {
            if (isDeleted || status == MessageStatus.SENDING) return false
            return when (type) {
                MessageType.IMAGE, MessageType.EPHEMERAL, MessageType.VIDEO -> {
                    if (hasLocalMediaReadyForViewer) return false
                    if (mediaUrl == null || missingLocalFile(mediaUrl)) {
                        val disk = ChatCacheStore.localURLsIfPresent(this).first
                        if (disk != null && localMediaFileIsReachable(disk)) return false
                    }
                    mediaObjectPath != null && mediaEncryption != null
                }
                MessageType.GIF, MessageType.STICKER -> {
                    val url = mediaUrl
                    if (url != null) {
                        val uri = android.net.Uri.parse(url)
                        if (uri.scheme != "file") return false
                    }
                    isMediaPendingResolution
                }
                else -> false
            }
        }

    val isMediaAwaitingManualDownload: Boolean
        get() {
            if (ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return false
            return if (type == MessageType.VIDEO) needsDownloadForPlayback else isMediaPendingResolution
        }

    val estimatedDownloadByteCount: Long?
        get() {
            fileSize?.takeIf { it > 0 }?.let { return it }
            val main = mediaEncryption?.plaintextSize?.takeIf { it > 0 } ?: return null
            return if (type == MessageType.VIDEO) {
                val thumb = thumbnailEncryption?.plaintextSize?.takeIf { it > 0 } ?: 0L
                main + thumb
            } else main
        }

    val formattedDownloadSize: String?
        get() = estimatedDownloadByteCount?.let { Formatter.formatFileSize(MomentsFormat.requireContext(), it) }

    val previewThumbnailURLForDisplay: String?
        get() {
            val url = thumbnailUrl ?: return null
            return url.takeIf { localMediaFileIsReachable(it) }
        }

    companion object {
        /** ≡ `EnhancedMessage.chatNoticePreviewText(for:)`. */
        fun chatNoticePreviewText(context: Context, token: String): String {
            if (VanishMessageTimer.parseEnabledNotice(token) != null || token == "chat.vanish.enabled") {
                return context.getString(R.string.chat_vanish_notice_preview_enabled)
            }
            if (token == VanishMessageTimer.DISABLED_NOTICE_TOKEN || token == "chat.vanish.disabled") {
                return context.getString(R.string.chat_vanish_notice_preview_disabled)
            }
            if (token == VanishMessageTimer.SCREENSHOT_NOTICE_TOKEN) {
                return context.getString(R.string.chat_vanish_screenshot)
            }
            if (token == VanishMessageTimer.SCREEN_RECORDING_NOTICE_TOKEN) {
                return context.getString(R.string.chat_vanish_screen_recording)
            }
            if (token.isEmpty()) return ""
            val resId = context.resources.getIdentifier(token.replace('.', '_'), "string", context.packageName)
            if (resId != 0) {
                val localized = context.getString(resId)
                if (localized != token) return localized
            }
            return ""
        }

        fun createViewOnceImage(conversationId: String, senderId: String, mediaUrl: String, fileSize: Long? = null): EnhancedMessage =
            EnhancedMessage(UUID.randomUUID().toString(), conversationId, senderId, MessageType.VIEW_ONCE_IMAGE, mediaUrl = mediaUrl, fileSize = fileSize, viewedBy = emptyList())
        fun createViewOnceVideo(conversationId: String, senderId: String, mediaUrl: String, thumbnailUrl: String? = null, duration: Double? = null, fileSize: Long? = null): EnhancedMessage =
            EnhancedMessage(UUID.randomUUID().toString(), conversationId, senderId, MessageType.VIEW_ONCE_VIDEO, mediaUrl = mediaUrl, thumbnailUrl = thumbnailUrl, duration = duration, fileSize = fileSize, viewedBy = emptyList())
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
            locationName = obj.optString("locationName").takeIf { obj.has("locationName") && !obj.isNull("locationName") },
            locationAddress = obj.optString("locationAddress").takeIf { obj.has("locationAddress") && !obj.isNull("locationAddress") },
            isLiveLocation = obj.optBoolean("isLiveLocation").takeIf { obj.has("isLiveLocation") },
            liveLocationExpiresAt = obj.optLong("liveLocationExpiresAt").takeIf { obj.has("liveLocationExpiresAt") }?.let(::Date),
            liveLocationDuration = obj.optString("liveLocationDuration").takeIf { obj.has("liveLocationDuration") && !obj.isNull("liveLocationDuration") },
            liveLocationStoppedAt = obj.optLong("liveLocationStoppedAt").takeIf { obj.has("liveLocationStoppedAt") }?.let(::Date),
            liveLocationSessionId = obj.optString("liveLocationSessionId").takeIf { obj.has("liveLocationSessionId") && !obj.isNull("liveLocationSessionId") },
            locationUpdatedAt = obj.optLong("locationUpdatedAt").takeIf { obj.has("locationUpdatedAt") }?.let(::Date),
            timestamp = Date(obj.optLong("timestamp", System.currentTimeMillis())),
            status = MessageStatus.from(obj.optString("status")),
            isRead = obj.optBoolean("isRead"),
            isDeleted = obj.optBoolean("isDeleted"),
            deletedAt = obj.optLong("deletedAt").takeIf { obj.has("deletedAt") }?.let { Date(it) },
            editedAt = obj.optLong("editedAt").takeIf { obj.has("editedAt") }?.let { Date(it) },
            reactions = obj.optJSONObject("reactions")?.let { payload ->
                payload.keys().asSequence().associateWith { key ->
                    val arr = payload.optJSONArray(key) ?: return@associateWith emptyList()
                    (0 until arr.length()).map(arr::getString)
                }
            },
            replyTo = obj.optString("replyTo").takeIf { obj.has("replyTo") && !obj.isNull("replyTo") },
            storyReplyData = obj.optJSONObject("storyReplyData")?.let { payload ->
                payload.keys().asSequence().associateWith { key -> payload.optString(key) }
            },
            sharedMomentData = obj.optJSONObject("sharedMomentData")?.let { payload -> payload.keys().asSequence().associateWith { key -> payload.optString(key) } },
            sharedStoryData = obj.optJSONObject("sharedStoryData")?.let { payload -> payload.keys().asSequence().associateWith { key -> payload.optString(key) } },
            expirationDate = obj.optLong("expirationDate").takeIf { obj.has("expirationDate") }?.let { Date(it) },
            isViewed = obj.optBoolean("isViewed"),
            mediaBatchId = obj.optString("mediaBatchId").takeIf { obj.has("mediaBatchId") && !obj.isNull("mediaBatchId") },
            textOverlayLive = obj.optBoolean("textOverlayLive").takeIf { obj.has("textOverlayLive") },
            textOverlays = obj.optJSONArray("textOverlays")?.let { array -> (0 until array.length()).map { StoryTextOverlayMetadata.from(array.getJSONObject(it).asMap()) } },
            stickers = obj.optJSONArray("stickers")?.let { array -> (0 until array.length()).map { StickerData.from(array.getJSONObject(it).asMap()) } },
            drawingData = obj.optString("drawingData").takeIf { obj.has("drawingData") && !obj.isNull("drawingData") }?.let { encoded -> runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() },
            viewedBy = obj.optJSONArray("viewedBy")?.let { array -> (0 until array.length()).map(array::getString) },
            allowReplay = obj.optBoolean("allowReplay").takeIf { obj.has("allowReplay") },
            replayedBy = obj.optJSONArray("replayedBy")?.let { array -> (0 until array.length()).map(array::getString) },
            readBy = obj.optJSONArray("readBy")?.let { array -> (0 until array.length()).map(array::getString) },
            readAtBy = obj.optJSONObject("readAtBy")?.let { payload ->
                payload.keys().asSequence().associateWith { userId -> Date(payload.getLong(userId)) }
            },
            starredBy = obj.optJSONArray("starredBy")?.let { array -> (0 until array.length()).map(array::getString) },
            isForwarded = obj.optBoolean("isForwarded").takeIf { obj.has("isForwarded") },
            isVanishModeMessage = obj.optBoolean("isVanishModeMessage"),
            vanishedFor = obj.optJSONArray("vanishedFor")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            vanishExpiresAt = obj.optLong("vanishExpiresAt").takeIf { obj.has("vanishExpiresAt") }?.let { Date(it) },
        )

        fun fromCached(cached: CachedMessage): EnhancedMessage = cached.toEnhancedMessage()
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

data class ConversationLastMessageReaction(
    val messageId: String,
    val emoji: String,
    val byUserId: String,
)

data class Conversation(
    var id: String? = null,
    val participants: List<String> = emptyList(),
    val lastMessage: String? = null,
    val timestamp: Date = Date(),
    var readStatus: Map<String, Boolean> = emptyMap(),
    val otherParticipantId: String = "",
    var otherParticipantUsername: String? = null,
    var otherParticipantProfileImagePath: String? = null,
    val isPinned: Boolean? = false,
    val pinnedByUserIds: List<String>? = null,
    val pinnedBy: String? = null,
    val isMuted: Boolean? = false,
    val mutedByUserIds: List<String>? = null,
    val mutedBy: String? = null,
    val archivedByUserIds: List<String>? = null,
    val encryptionVersion: String? = null,
    val conversationKeyVersion: Int? = null,
    val wrappedKeys: Map<String, WrappedConversationKey>? = null,
    var readReceiptPreferences: Map<String, Boolean>? = emptyMap(),
    var buzzPreferences: Map<String, Boolean>? = emptyMap(),
    var forwardingPreferences: Map<String, Boolean>? = emptyMap(),
    var lastDeletedAt: Map<String, Date>? = null,
    var lastReadAt: Map<String, Date>? = null,
    var vanishModeActive: Boolean? = false,
    var vanishModeEnabledBy: String? = null,
    var vanishModeEnabledAt: Date? = null,
    var vanishMessageTimer: String? = null,
    var vanishSettingsNoticeMessageId: String? = null,
    var vanishDisabledNoticeMessageId: String? = null,
    var lastMessageSenderId: String? = null,
    var lastMessageSeenAt: Map<String, Date>? = null,
    var lastMessageReaction: ConversationLastMessageReaction? = null,
    var lastMessageType: MessageType? = null,
    var lastMessageViewOncePending: Boolean = false,
) {
    fun allowsForwarding(senderId: String): Boolean = forwardingPreferences?.get(senderId) ?: true
    fun isMuted(userId: String?): Boolean = when {
        userId.isNullOrBlank() -> isMuted == true
        userId in mutedByUserIds.orEmpty() -> true
        isMuted == true && mutedBy == userId -> true
        else -> false
    }
    fun isPinned(userId: String?): Boolean = when {
        userId.isNullOrBlank() -> isPinned == true
        userId in pinnedByUserIds.orEmpty() -> true
        isPinned == true && pinnedBy == userId -> true
        else -> false
    }
    fun isArchived(userId: String?): Boolean = !userId.isNullOrBlank() && userId in archivedByUserIds.orEmpty()
    fun deletedAtCutoff(userId: String): Date? = lastDeletedAt?.get(userId)
    fun unreadCount(currentUserId: String): Int {
        if (readStatus[currentUserId] != false) return 0
        val conversationId = id ?: return 1
        val count = com.moments.android.services.persistence.LocalPersistenceService.unreadMessageCount(conversationId, currentUserId, lastReadAt?.get(currentUserId))
        return count.takeIf { it > 0 } ?: 1
    }
    /** Stub iOS: siempre true (bloqueos no filtrados aquí). */
    val isActive: Boolean get() = true
    fun isOwnLastMessage(currentUserId: String): Boolean =
        lastMessageSenderId == currentUserId || lastMessageSeenAt?.get(otherParticipantId) != null || lastMessageReaction?.byUserId == otherParticipantId
    fun showsViewOnceInboxPlayButton(currentUserId: String): Boolean =
        lastMessageViewOncePending && lastMessageType?.isViewOnce == true && !isOwnLastMessage(currentUserId)

    /** ≡ `Conversation.messagePreview`. */
    fun messagePreview(context: Context): String {
        val last = lastMessage
        if (last != null) {
            if (last.startsWith("📎")) return last
            if (last.length > 50) return last.take(47) + "..."
            return last
        }
        return context.getString(R.string.chat_preview_new_conversation)
    }

    /** ≡ `inboxMessagePreview(for:)` — view-once entrante → Foto/Video genérico. */
    fun inboxMessagePreview(context: Context, currentUserId: String): String {
        val type = lastMessageType
        if (type != null && type.isViewOnce && !isOwnLastMessage(currentUserId)) {
            return when (type) {
                MessageType.VIEW_ONCE_VIDEO -> context.getString(R.string.chat_preview_video)
                else -> context.getString(R.string.chat_preview_photo)
            }
        }
        return messagePreview(context)
    }
}

/** Compatibilidad con el modelo `Message` previo a `EnhancedMessage`. */
data class LegacyMessage(
    val id: String? = null,
    val conversationId: String,
    override val senderId: String,
    val content: String,
    override val timestamp: Date,
    override val isRead: Boolean,
    val reaction: String? = null,
    val expirationDate: Date? = null,
    val isViewed: Boolean = false,
) : MessageProtocol

data class TypingIndicator(
    val userId: String,
    val conversationId: String,
    val timestamp: Date = Date(),
)

data class MessageNotification(
    val conversationId: String,
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val messagePreview: String,
    val timestamp: Date = Date(),
    val isViewOnce: Boolean = false,
) {
    val title: String get() = senderName
    fun body(context: Context): String =
        if (isViewOnce) context.getString(R.string.chat_notification_view_once_prompt) else messagePreview
}

data class ViewOnceMetadata(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val createdAt: Date = Date(),
    var viewedBy: List<String> = emptyList(),
    var isExpired: Boolean = false,
) {
    fun markViewed(userId: String) { if (userId !in viewedBy) viewedBy = viewedBy + userId }
    val canBeDeleted: Boolean get() = viewedBy.isNotEmpty() && !isExpired
}

object ViewOnceStateManager {
    fun shouldDelete(message: EnhancedMessage, userId: String): Boolean =
        message.isViewOnce && message.senderId != userId && message.hasBeenViewedBy(userId)

    fun getViewOnceStatusText(context: Context, message: EnhancedMessage, userId: String): String {
        if (!message.isViewOnce) return ""
        if (message.isDeleted) return context.getString(R.string.messaging_message_deleted)
        return if (message.senderId == userId) {
            if (message.isViewed) context.getString(R.string.chat_view_once_viewed)
            else context.getString(R.string.chat_view_once_sent)
        } else {
            if (message.hasBeenViewedBy(userId)) context.getString(R.string.chat_view_once_viewed)
            else context.getString(R.string.chat_view_once_tap_to_view)
        }
    }

    /** Tokens de color lógicos ≡ iOS (`primary`/`secondary`/`success`/`warning`). */
    fun getViewOnceStatusColor(message: EnhancedMessage, userId: String): String {
        if (!message.isViewOnce) return "primary"
        if (message.isDeleted) return "secondary"
        return if (message.senderId == userId) {
            if (message.isViewed) "success" else "warning"
        } else {
            if (message.hasBeenViewedBy(userId)) "secondary" else "primary"
        }
    }
}

enum class ViewOnceMediaType {
    IMAGE, VIDEO;
    val messageType: MessageType get() = if (this == IMAGE) MessageType.VIEW_ONCE_IMAGE else MessageType.VIEW_ONCE_VIDEO
    val iconName: String get() = if (this == IMAGE) "camera.circle" else "video.circle"
    fun preview(context: Context): String = when (this) {
        IMAGE -> context.getString(R.string.chat_preview_view_once_photo)
        VIDEO -> context.getString(R.string.chat_preview_view_once_video)
    }
    companion object { fun fromMessageType(type: MessageType): ViewOnceMediaType? = when (type) { MessageType.VIEW_ONCE_IMAGE -> IMAGE; MessageType.VIEW_ONCE_VIDEO -> VIDEO; else -> null } }
}

interface MessageProtocol {
    val senderId: String
    val timestamp: Date
    val isRead: Boolean
}

object ViewOnceConstants {
    const val AUTO_DELETE_DELAY_MILLIS = 500L
    const val MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L
    val supportedImageTypes = setOf("image/jpeg", "image/png", "image/heic")
    val supportedVideoTypes = setOf("video/mp4", "video/mov", "video/quicktime")

    object Analytics {
        const val VIEW_ONCE_CREATED = "view_once_created"
        const val VIEW_ONCE_OPENED = "view_once_opened"
        const val VIEW_ONCE_CLOSED = "view_once_closed"
        const val VIEW_ONCE_DELETED = "view_once_deleted"
        const val VIEW_ONCE_EXPIRED = "view_once_expired"
    }

    object Notifications {
        const val VIEW_ONCE_VIEWED = "ViewOnceMessageViewed"
        const val VIEW_ONCE_DELETED = "ViewOnceMessageDeleted"
        const val VIEW_ONCE_RECEIVED = "ViewOnceMessageReceived"
        const val VIEW_ONCE_CLOSED = "ViewOnceMessageClosed"
    }

    object MomentsStyle {
        const val DELETE_ON_VIEW_CLOSE = true
        const val ALLOW_SCREENSHOTS = false
        const val SHOW_CLOSE_WARNING = true
        const val ENABLE_HAPTIC_FEEDBACK = true
    }
}

enum class ViewOnceError {
    MESSAGE_NOT_FOUND, ALREADY_VIEWED, NOT_VIEW_ONCE_MESSAGE, DELETION_FAILED,
    UPLOAD_FAILED, INVALID_MEDIA_TYPE, FILE_TOO_LARGE, NETWORK_ERROR;

    /** ≡ `ViewOnceError.errorDescription`. */
    fun errorDescription(context: Context): String = context.getString(
        when (this) {
            MESSAGE_NOT_FOUND -> R.string.messaging_message_not_found
            ALREADY_VIEWED -> R.string.messaging_message_already_viewed
            NOT_VIEW_ONCE_MESSAGE -> R.string.messaging_message_not_view_once
            DELETION_FAILED -> R.string.messaging_message_delete_failed
            UPLOAD_FAILED -> R.string.messaging_message_upload_failed
            INVALID_MEDIA_TYPE -> R.string.messaging_message_unsupported_file
            FILE_TOO_LARGE -> R.string.messaging_message_file_too_large
            NETWORK_ERROR -> R.string.messaging_message_connection_error
        },
    )
}

/** Una sola reacción activa por usuario: repetir emoji la elimina. */
object MessageReactionMutation {
    fun apply(reactions: Map<String, List<String>>?, emoji: String, userId: String): Map<String, List<String>>? {
        val updated = reactions.orEmpty().mapValues { (_, users) -> users.toMutableList() }.toMutableMap()
        if (userId in updated[emoji].orEmpty()) {
            updated[emoji] = updated[emoji].orEmpty().filterNot { it == userId }.toMutableList()
            if (updated[emoji].isNullOrEmpty()) updated.remove(emoji)
        } else {
            updated.keys.toList().forEach { key ->
                updated[key] = updated[key].orEmpty().filterNot { it == userId }.toMutableList()
                if (updated[key].isNullOrEmpty()) updated.remove(key)
            }
            updated[emoji] = (updated[emoji].orEmpty() + userId).distinct().toMutableList()
        }
        return updated.takeIf { it.isNotEmpty() }
    }
}

object ChatMessagePolicy {
    const val EDIT_WINDOW_MILLIS = 10L * 60L * 1000L
    fun isVanishRestricted(message: EnhancedMessage): Boolean = message.isVanishModeMessage
    fun canEdit(message: EnhancedMessage, userId: String, now: Date = Date()): Boolean =
        !isVanishRestricted(message) && message.senderId == userId && message.type == MessageType.TEXT && !message.isDeleted && now.time - message.timestamp.time < EDIT_WINDOW_MILLIS
    fun canForward(message: EnhancedMessage, currentUserId: String, forwardingPreferences: Map<String, Boolean>? = null): Boolean =
        !isVanishRestricted(message) && message.type == MessageType.TEXT && !message.isDeleted && !message.content.isNullOrBlank() &&
            (message.senderId == currentUserId || forwardingPreferences?.get(message.senderId) != false)
    fun canCopy(message: EnhancedMessage, currentUserId: String, forwardingPreferences: Map<String, Boolean>? = null): Boolean =
        canForward(message, currentUserId, forwardingPreferences)
    fun canSendBuzz(participants: List<String>, currentUserId: String, buzzPreferences: Map<String, Boolean>? = null): Boolean =
        participants.filter { it != currentUserId }.all { buzzPreferences?.get(it) != false }
}

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

        /** ≡ `RequestStatus.displayName`. */
        fun displayName(context: Context): String = context.getString(
            when (this) {
                PENDING -> R.string.messaging_request_status_pending
                ACCEPTED -> R.string.messaging_request_status_accepted
                REJECTED -> R.string.messaging_request_status_rejected
                BLOCKED -> R.string.messaging_request_status_blocked
            },
        )

        /** ≡ `RequestStatus.color` (hex sin `#`). */
        val color: String
            get() = when (this) {
                PENDING -> "FF9500"
                ACCEPTED -> "34C759"
                REJECTED -> "FF3B30"
                BLOCKED -> "8E8E93"
            }

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

    val isPending: Boolean get() = status == RequestStatus.PENDING
    val canSendMoreRequests: Boolean get() = status != RequestStatus.BLOCKED

    /** ≡ `MessageRequest.messagePreview` (MessageModel.swift). */
    fun messagePreview(context: Context): String = when (messageType) {
        MessageType.TEXT ->
            if (message.length > 50) message.take(47) + "..." else message
        MessageType.IMAGE -> context.getString(R.string.chat_preview_photo)
        MessageType.VIDEO -> context.getString(R.string.chat_preview_video)
        MessageType.AUDIO -> context.getString(R.string.chat_preview_audio)
        MessageType.GIF -> context.getString(R.string.chat_preview_gif)
        MessageType.FILE -> context.getString(R.string.chat_preview_file)
        MessageType.LOCATION -> context.getString(R.string.chat_preview_location)
        MessageType.STICKER -> context.getString(R.string.chat_preview_sticker)
        MessageType.EPHEMERAL -> context.getString(R.string.chat_preview_ephemeral)
        MessageType.SHARED_MOMENT -> context.getString(R.string.chat_preview_shared_moment)
        MessageType.SHARED_STORY -> context.getString(R.string.chat_preview_shared_story)
        MessageType.VIEW_ONCE_IMAGE -> context.getString(R.string.chat_preview_view_once_photo)
        MessageType.VIEW_ONCE_VIDEO -> context.getString(R.string.chat_preview_view_once_video)
        MessageType.CHAT_NOTICE -> {
            val localized = message
            if (localized.length > 50) localized.take(47) + "..." else localized
        }
    }

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

/** Tipos de `Views/Messaging/Core/MessageModel.swift` consumidos por el chrome del chat. */
data class PendingChatContext(
    val otherUserId: String,
    val otherUsername: String,
    val otherProfileImagePath: String?,
    val otherFollowersCount: Int? = null,
    val otherMomentsCount: Int? = null,
    val otherIsVerified: Boolean = false,
    val viewerFollowsOther: Boolean? = null,
    val otherFollowsViewer: Boolean? = null,
    val viewerFollowedAt: Date? = null,
    val otherFollowedViewerAt: Date? = null,
    val request: MessageRequest? = null,
    val direction: Direction,
    var status: Status,
    var initialText: String? = null,
) {
    enum class Direction { OUTGOING, INCOMING }
    enum class Status {
        NORMAL_CONVERSATION,
        OUTGOING_REQUEST_DRAFT,
        OUTGOING_REQUEST_SENT,
        OUTGOING_REQUEST_BLOCKED,
        INCOMING_REQUEST_PENDING,
    }

    val id: String
        get() = request?.id?.let { "request:$it" }
            ?: "pending:${direction.name.lowercase()}:$otherUserId"

    fun resetToDraft(): PendingChatContext = copy(
        request = null,
        direction = Direction.OUTGOING,
        status = Status.OUTGOING_REQUEST_DRAFT,
        initialText = null,
    )

    fun syntheticConversation(currentUserId: String): Conversation = Conversation(
        participants = listOf(currentUserId, otherUserId).filter { it.isNotEmpty() }.sorted(),
        lastMessage = initialText,
        timestamp = request?.timestamp ?: Date(),
        readStatus = mapOf(currentUserId to true, otherUserId to false),
        otherParticipantId = otherUserId,
        otherParticipantUsername = otherUsername,
        otherParticipantProfileImagePath = otherProfileImagePath,
    )

    companion object {
        fun outgoing(
            user: AppUser,
            status: Status = Status.OUTGOING_REQUEST_DRAFT,
            initialText: String? = null,
            request: MessageRequest? = null,
        ): PendingChatContext = PendingChatContext(
            otherUserId = user.id,
            otherUsername = user.username,
            otherProfileImagePath = user.profileImagePath,
            otherFollowersCount = user.followersCount,
            otherMomentsCount = user.momentsCount,
            otherIsVerified = user.isVerified,
            request = request,
            direction = Direction.OUTGOING,
            status = status,
            initialText = initialText,
        )

        fun incoming(request: MessageRequest, fallbackUsername: String): PendingChatContext = PendingChatContext(
            otherUserId = request.senderId,
            otherUsername = request.senderUsername ?: fallbackUsername,
            otherProfileImagePath = request.senderProfileImagePath,
            request = request,
            direction = Direction.INCOMING,
            status = Status.INCOMING_REQUEST_PENDING,
            initialText = request.message,
        )
    }
}

/** Port de `PendingChatContextFactory`: compone contexto de perfil, relación y solicitud. */
object PendingChatContextFactory {
    private data class CachedIntro(val context: PendingChatContext, val storedAt: Date)
    private val introCache = mutableMapOf<String, CachedIntro>()
    private const val INTRO_TTL_MILLIS = 5L * 60L * 1000L
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun conversationIntro(conversation: Conversation, currentUserId: String): PendingChatContext? {
        val otherId = conversation.otherParticipantId
        if (currentUserId.isBlank() || otherId.isBlank()) return null
        val key = "$currentUserId::$otherId"
        synchronized(introCache) {
            introCache[key]?.takeIf { Date().time - it.storedAt.time < INTRO_TTL_MILLIS }?.let { return it.context }
            introCache.remove(key)
        }
        val user = cachedOrRemoteUser(otherId)
        val viewerFollowedAt = followTimestamp(currentUserId, otherId)
        val otherFollowedViewerAt = followerTimestamp(currentUserId, otherId)
        val stats = profileStats(otherId)
        val followers = aggregateFollowersCount(otherId)
        val moments = visibleMomentsCount(otherId)
        val context = PendingChatContext(
            otherUserId = otherId,
            otherUsername = user?.username ?: conversation.otherParticipantUsername.orEmpty(),
            otherProfileImagePath = user?.profileImagePath ?: conversation.otherParticipantProfileImagePath,
            otherFollowersCount = resolvedCount(user?.followersCount, stats?.followersCount, followers),
            otherMomentsCount = moments ?: resolvedCount(user?.momentsCount, stats?.momentsCount),
            otherIsVerified = user?.isVerified ?: false,
            viewerFollowsOther = viewerFollowedAt != null,
            otherFollowsViewer = otherFollowedViewerAt != null,
            viewerFollowedAt = viewerFollowedAt,
            otherFollowedViewerAt = otherFollowedViewerAt,
            direction = PendingChatContext.Direction.OUTGOING,
            status = PendingChatContext.Status.NORMAL_CONVERSATION,
        )
        synchronized(introCache) { introCache[key] = CachedIntro(context, Date()) }
        return context
    }

    suspend fun outgoing(
        user: AppUser,
        currentUserId: String,
        followersCountOverride: Int? = null,
        momentsCountOverride: Int? = null,
    ): PendingChatContext {
        val viewerFollowedAt = followTimestamp(currentUserId, user.id)
        val otherFollowedViewerAt = followerTimestamp(currentUserId, user.id)
        val stats = profileStats(user.id)
        val request = pendingOutgoingRequest(currentUserId, user.id)
        val followers = aggregateFollowersCount(user.id)
        val moments = visibleMomentsCount(user.id)
        val receiverFollowsViewer = otherFollowedViewerAt != null
        val policy = stats?.requestPolicy ?: user.messageRequestPolicy
        val closed = policy == MessageRequestPolicy.NOBODY || (policy == MessageRequestPolicy.FOLLOWING && !receiverFollowsViewer)
        return PendingChatContext(
            otherUserId = user.id,
            otherUsername = user.username,
            otherProfileImagePath = user.profileImagePath,
            otherFollowersCount = resolvedCount(user.followersCount, followersCountOverride, stats?.followersCount, followers),
            otherMomentsCount = momentsCountOverride ?: moments ?: resolvedCount(user.momentsCount, stats?.momentsCount),
            otherIsVerified = user.isVerified,
            viewerFollowsOther = viewerFollowedAt != null,
            otherFollowsViewer = receiverFollowsViewer,
            viewerFollowedAt = viewerFollowedAt,
            otherFollowedViewerAt = otherFollowedViewerAt,
            request = request,
            direction = PendingChatContext.Direction.OUTGOING,
            status = if (request != null) PendingChatContext.Status.OUTGOING_REQUEST_SENT else if (closed) PendingChatContext.Status.OUTGOING_REQUEST_BLOCKED else PendingChatContext.Status.OUTGOING_REQUEST_DRAFT,
            initialText = request?.message,
        )
    }

    suspend fun incoming(request: MessageRequest, viewerId: String): PendingChatContext {
        val sender = fetchUser(request.senderId)
        val viewerFollowedAt = followTimestamp(viewerId, request.senderId)
        val otherFollowedViewerAt = followerTimestamp(viewerId, request.senderId)
        val stats = profileStats(request.senderId)
        val followers = aggregateFollowersCount(request.senderId)
        val moments = visibleMomentsCount(request.senderId)
        return PendingChatContext(
            otherUserId = request.senderId,
            otherUsername = sender?.username ?: request.senderUsername.orEmpty(),
            otherProfileImagePath = sender?.profileImagePath ?: request.senderProfileImagePath,
            otherFollowersCount = resolvedCount(sender?.followersCount, stats?.followersCount, followers),
            otherMomentsCount = moments ?: resolvedCount(sender?.momentsCount, stats?.momentsCount),
            otherIsVerified = sender?.isVerified ?: false,
            viewerFollowsOther = viewerFollowedAt != null,
            otherFollowsViewer = otherFollowedViewerAt != null,
            viewerFollowedAt = viewerFollowedAt,
            otherFollowedViewerAt = otherFollowedViewerAt,
            request = request,
            direction = PendingChatContext.Direction.INCOMING,
            status = PendingChatContext.Status.INCOMING_REQUEST_PENDING,
            initialText = request.message,
        )
    }

    suspend fun pendingOutgoingRequest(senderId: String, receiverId: String): MessageRequest? = runCatching {
        if (senderId.isBlank() || receiverId.isBlank()) return@runCatching null
        val document = db.collection("messageRequests")
            .whereEqualTo("senderId", senderId)
            .whereEqualTo("receiverId", receiverId)
            .whereEqualTo("status", MessageRequest.RequestStatus.PENDING.raw)
            .get().await().documents.firstOrNull() ?: return@runCatching null
        MessageRequest.fromFirestoreData(document.data.orEmpty(), document.id)
    }.getOrNull()

    private suspend fun cachedOrRemoteUser(userId: String): AppUser? = UserCacheService.getCachedUser(userId) ?: fetchUser(userId)
    private suspend fun fetchUser(userId: String): AppUser? = if (userId.isBlank()) null else runCatching { FirestoreService().fetchUsersAsync(listOf(userId)).firstOrNull() }.getOrNull()
    private suspend fun followTimestamp(from: String, to: String): Date? = timestampAt("following", from, to)
    private suspend fun followerTimestamp(viewerId: String, otherId: String): Date? = timestampAt("followers", viewerId, otherId)
    private suspend fun timestampAt(collection: String, userId: String, otherId: String): Date? = runCatching {
        if (userId.isBlank() || otherId.isBlank()) return@runCatching null
        (db.collection("users").document(userId).collection(collection).document(otherId).get().await().get("timestamp") as? Timestamp)?.toDate()
    }.getOrNull()
    private suspend fun aggregateFollowersCount(userId: String): Int? = countCollection("followers", userId)
    private suspend fun visibleMomentsCount(userId: String): Int? {
        if (userId.isBlank()) return null
        BackendFeedService.fetchProfileMoments(targetUserId = userId, limit = 1, includeTotalCount = true)?.totalVisibleCount?.let { return it }
        return runCatching { db.collection("users").document(userId).collection("moments").whereEqualTo("audience", "everyone").get().await().size() }.getOrNull()
    }
    private suspend fun countCollection(collection: String, userId: String): Int? = runCatching {
        if (userId.isBlank()) return@runCatching null
        db.collection("users").document(userId).collection(collection).get().await().size()
    }.getOrNull()
    private data class ProfileStats(val followersCount: Int?, val momentsCount: Int?, val requestPolicy: MessageRequestPolicy?)
    private suspend fun profileStats(userId: String): ProfileStats? = runCatching {
        if (userId.isBlank()) return@runCatching null
        val data = db.collection("users").document(userId).get().await().data ?: return@runCatching null
        ProfileStats(firstInt(data, "followersCount", "followers_count"), firstInt(data, "momentsCount", "moments_count", "postsCount", "posts_count"), (data["messageRequestPolicy"] as? String)?.let(MessageRequestPolicy::from))
    }.getOrNull()
    private fun firstInt(data: Map<String, Any?>, vararg keys: String): Int? = keys.firstNotNullOfOrNull { (data[it] as? Number)?.toInt() }
    private fun resolvedCount(vararg values: Int?): Int? = values.filterNotNull().maxOrNull()?.takeIf { it > 0 }
}

data class PendingChatTimelineMessage(
    val id: String,
    val text: String,
    val messageType: MessageType,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val timestamp: Date,
    val isOutgoing: Boolean,
) {
    companion object {
        fun from(request: MessageRequest, currentUserId: String): PendingChatTimelineMessage = PendingChatTimelineMessage(
            id = request.id?.let { "pending-request:$it" }
                ?: "pending-request:${request.senderId}:${request.timestamp.time}",
            text = request.message,
            messageType = request.messageType,
            mediaUrl = request.mediaUrl,
            thumbnailUrl = request.thumbnailUrl,
            timestamp = request.timestamp,
            isOutgoing = request.senderId == currentUserId,
        )

        fun outgoingText(text: String, receiverId: String): PendingChatTimelineMessage = PendingChatTimelineMessage(
            id = "pending-outgoing:$receiverId",
            text = text,
            messageType = MessageType.TEXT,
            mediaUrl = null,
            thumbnailUrl = null,
            timestamp = Date(),
            isOutgoing = true,
        )
    }
}

data class PresenceDisplay(
    val status: OnlineStatus,
    val statusText: String,
    val supplementalText: String?,
)

private fun JSONObject.asMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = opt(key)) {
        JSONObject.NULL -> null
        is JSONObject -> value.asMap()
        is JSONArray -> (0 until value.length()).map { index -> value.opt(index) }
        else -> value
    }
}

fun encodeMessages(messages: List<EnhancedMessage>): ByteArray =
    JSONArray().apply { messages.forEach { put(it.toJson()) } }.toString().toByteArray()

fun decodeMessages(data: ByteArray): List<EnhancedMessage> {
    val arr = JSONArray(String(data))
    return (0 until arr.length()).map { EnhancedMessage.fromJson(arr.getJSONObject(it)) }
}
