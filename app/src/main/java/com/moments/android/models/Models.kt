package com.moments.android.models

import android.content.Context
import com.google.firebase.Timestamp
import com.moments.android.R
import com.moments.android.services.privacy.ContentVisibilityType
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.UUID

// Port incremental de Models.swift de iOS. Se van añadiendo tipos poco a poco.

// MARK: - Point (equivalente a CGPoint de iOS en modelos)
data class Point(val x: Double, val y: Double) {
    companion object {
        /** Lee un punto desde un mapa {x,y}. */
        fun from(data: Any?): Point? {
            val m = data as? Map<*, *> ?: return null
            val x = (m["x"] as? Number)?.toDouble() ?: return null
            val y = (m["y"] as? Number)?.toDouble() ?: return null
            return Point(x, y)
        }
    }
}

// MARK: - FilterSettings
data class FilterSettings(val name: String, val intensity: Double)

// MARK: - Seguimiento y solicitudes
enum class FollowRequestStatus(val raw: String) {
    PENDING("pending"), ACCEPTED("accepted"), REJECTED("rejected"), CANCELLED("cancelled");

    /** ≡ iOS `displayName` (textos fijos del Swift). */
    val displayName: String
        get() = when (this) {
            PENDING -> "Pendiente"
            ACCEPTED -> "Aceptada"
            REJECTED -> "Rechazada"
            CANCELLED -> "Cancelada"
        }

    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: PENDING }
}

data class FollowRequest(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val recipientId: String,
    val status: FollowRequestStatus,
    val timestamp: Date,
    val expirationDate: Date?,
) {
    val isExpired: Boolean get() = expirationDate?.let { Date().after(it) } ?: false
    val isValid: Boolean get() = status == FollowRequestStatus.PENDING && !isExpired

    companion object {
        /** Nueva solicitud (id/timestamp/expiración a 30 días), equivalente al init de conveniencia de iOS. */
        fun create(senderId: String, senderUsername: String, recipientId: String): FollowRequest {
            val now = Date()
            val expiry = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, 30) }.time
            return FollowRequest(UUID.randomUUID().toString(), senderId, senderUsername, recipientId, FollowRequestStatus.PENDING, now, expiry)
        }

        fun from(data: Map<String, Any?>): FollowRequest = FollowRequest(
            id = data["id"] as? String ?: "",
            senderId = data["senderId"] as? String ?: "",
            senderUsername = data["senderUsername"] as? String ?: "",
            recipientId = data["recipientId"] as? String ?: "",
            status = FollowRequestStatus.from(data["status"] as? String),
            timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
            expirationDate = MediaItem.anyToDate(data["expirationDate"]),
        )
    }
}

// MARK: - Conexiones legacy (id == userId, como en iOS)
data class Connection(val id: String, val userId: String, val timestamp: Date) {
    companion object {
        fun from(data: Map<String, Any?>): Connection {
            val userId = data["userId"] as? String ?: ""
            return Connection(userId, userId, MediaItem.anyToDate(data["timestamp"]) ?: Date())
        }
    }
}

data class FollowerRecord(val id: String, val userId: String, val timestamp: Date) {
    companion object {
        fun from(data: Map<String, Any?>): FollowerRecord {
            val userId = data["userId"] as? String ?: ""
            return FollowerRecord(userId, userId, MediaItem.anyToDate(data["timestamp"]) ?: Date())
        }
    }
}

/** Ratio ancho/alto resuelto desde aspectRatio ("w:h") o la resolución de vídeo ("WxH"). */
fun resolveAspectRatioValue(aspectRatio: String?, videoResolution: String?): Float? {
    aspectRatio?.trim()?.takeIf { it.isNotEmpty() }?.let { normalized ->
        val parts = normalized.split(":")
        if (parts.size == 2) {
            val w = parts[0].toDoubleOrNull()
            val h = parts[1].toDoubleOrNull()
            if (w != null && h != null && h > 0) {
                val r = (w / h).toFloat()
                if (r.isFinite() && r > 0) return r
            }
        }
        // ≡ CreatorMedia.AspectRatio(from:).value (CreatorView.swift)
        val canonical = when (normalized) {
            "1:1" -> 1f
            "4:5" -> 0.8f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            else -> 1f // default .square
        }
        if (canonical.isFinite() && canonical > 0) return canonical
    }
    videoResolution?.let { res ->
        val idx = res.indexOf('x')
        if (idx > 0) {
            val w = res.substring(0, idx).toDoubleOrNull()
            val h = res.substring(idx + 1).toDoubleOrNull()
            if (w != null && h != null && h > 0) {
                val r = (w / h).toFloat()
                if (r.isFinite() && r > 0) return r
            }
        }
    }
    return null
}

// MARK: - PhotoTag (etiqueta espacial sobre una imagen)
data class PhotoTag(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val username: String,
    val x: Double, // 0.0..1.0 relativo al ancho
    val y: Double, // 0.0..1.0 relativo al alto
) {
    companion object {
        fun from(data: Map<String, Any?>): PhotoTag = PhotoTag(
            id = data["id"] as? String ?: UUID.randomUUID().toString(),
            userId = data["userId"] as? String ?: "",
            username = data["username"] as? String ?: "",
            x = (data["x"] as? Number)?.toDouble() ?: 0.0,
            y = (data["y"] as? Number)?.toDouble() ?: 0.0,
        )
    }
}

// MARK: - Video (variantes por calidad)
enum class VideoPlaybackTier { LOW, MEDIUM, HIGH }

data class VideoVariants(
    val low: String? = null,
    val medium: String? = null,
    val high: String? = null,
) {
    fun url(tier: VideoPlaybackTier): String? {
        val candidate = when (tier) {
            VideoPlaybackTier.LOW -> low
            VideoPlaybackTier.MEDIUM -> medium ?: low
            VideoPlaybackTier.HIGH -> high ?: medium ?: low
        }
        return candidate?.takeUnless { it.isBlank() }
    }

    val allUrls: List<String> get() = listOfNotNull(low, medium, high).filter { it.isNotBlank() }

    companion object {
        fun from(data: Map<String, Any?>?): VideoVariants? {
            if (data == null) return null
            return VideoVariants(data["low"] as? String, data["medium"] as? String, data["high"] as? String)
        }
    }
}

// MARK: - MediaItem (imagen o vídeo de un momento)
data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val type: MediaType,
    val url: String,
    val aspectRatio: String? = null,
    val thumbnailUrl: String? = null,
    val videoDuration: Double? = null,
    val videoFileSize: Long? = null,
    val videoResolution: String? = null,
    val videoProcessingStatus: VideoProcessingStatus? = null,
    val originalVideoUrl: String? = null,
    val videoVariants: VideoVariants? = null,
    val tags: List<PhotoTag>? = null,
    val moderationState: ModerationState? = null,
    val moderationReason: String? = null,
    val moderationCategory: String? = null,
    val moderationConfidence: String? = null,
    val moderatedAt: Date? = null,
) {
    enum class MediaType(val raw: String) {
        IMAGE("image"), VIDEO("video");
        companion object { fun from(raw: String?) = if (raw == "video") VIDEO else IMAGE }
    }

    enum class ModerationState(val raw: String) {
        VISIBLE("visible"), HIDDEN("hidden");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
    }

    enum class VideoProcessingStatus(val raw: String) {
        PENDING("pending"), PROCESSING("processing"), READY("ready"), FAILED("failed"), SKIPPED("skipped");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
    }

    val isHiddenByModeration: Boolean get() = moderationState == ModerationState.HIDDEN

    /** Ratio ancho/alto resuelto desde aspectRatio ("w:h") o la resolución de vídeo ("WxH"). */
    val resolvedAspectRatioValue: Float?
        get() = resolveAspectRatioValue(aspectRatio, videoResolution)

    companion object {
        fun from(data: Map<String, Any?>): MediaItem = MediaItem(
            id = data["id"] as? String ?: UUID.randomUUID().toString(),
            type = MediaType.from(data["type"] as? String),
            url = data["url"] as? String ?: "",
            aspectRatio = data["aspectRatio"] as? String,
            thumbnailUrl = data["thumbnailUrl"] as? String,
            videoDuration = (data["videoDuration"] as? Number)?.toDouble(),
            videoFileSize = (data["videoFileSize"] as? Number)?.toLong(),
            videoResolution = data["videoResolution"] as? String,
            videoProcessingStatus = VideoProcessingStatus.from(data["videoProcessingStatus"] as? String),
            originalVideoUrl = data["originalVideoUrl"] as? String,
            videoVariants = VideoVariants.from(data["videoVariants"] as? Map<String, Any?>),
            tags = (data["tags"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(PhotoTag::from) },
            moderationState = ModerationState.from(data["moderationState"] as? String),
            moderationReason = data["moderationReason"] as? String,
            moderationCategory = data["moderationCategory"] as? String,
            moderationConfidence = data["moderationConfidence"] as? String,
            moderatedAt = anyToDate(data["moderatedAt"]),
        )

        internal fun anyToDate(value: Any?): Date? = when (value) {
            is Timestamp -> value.toDate()
            is Date -> value
            is Number -> Date((value.toDouble()).let { if (it > 1e12) it.toLong() else (it * 1000).toLong() })
            else -> null
        }
    }
}

// MARK: - Moment (el modelo core del feed)
data class Moment(
    val id: String? = null,
    val authorId: String = "",
    val username: String = "",
    val content: String = "",
    val imagePath: String? = null, // clave Firestore "imageUrl"
    val videoUrl: String? = null,
    val timestamp: Date = Date(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val commentCount: Int = 0,
    val profileImagePath: String? = null,
    val taggedUsers: List<String>? = null,
    val mentionedUsers: List<String>? = null,
    val location: String? = null,
    val locationCoordinate: LocationCoordinate? = null,
    val audience: String? = null,
    val mediaItems: List<MediaItem>? = null,
    val aspectRatio: String? = null,
    val customListId: String? = null,
    val thumbnailUrl: String? = null,
    val videoDuration: Double? = null,
    val videoFileSize: Long? = null,
    val videoResolution: String? = null,
    val scheduledDate: Date? = null,
    val isArchived: Boolean? = null,
    val archivedAt: Date? = null,
    val isPinned: Boolean? = null,
    val pinnedAt: Date? = null,
    val gridPreviewScale: Double? = null,
    val gridPreviewOffsetX: Double? = null,
    val gridPreviewOffsetY: Double? = null,
    val gridPreviewFitMode: String? = null,
    val gridPreviewBackground: String? = null,
    val hasHiddenLayers: Boolean = false,
    val hiddenLayerCount: Int = 0,
    val isModerationHidden: Boolean? = null,
    val originalAudience: String? = null,
    val reviewRequired: Boolean? = null,
    val canRestore: Boolean? = null,
    val disableComments: Boolean = false,
    val hideLikeCounts: Boolean = false,
    val allowSharing: Boolean = true,
) {
    /** Ratio ancho/alto resuelto desde aspectRatio ("w:h") o la resolución de vídeo ("WxH"). */
    val resolvedAspectRatioValue: Float?
        get() = resolveAspectRatioValue(aspectRatio, videoResolution)

    data class LocationCoordinate(val latitude: Double, val longitude: Double) {
        companion object {
            fun from(data: Map<String, Any?>?): LocationCoordinate? {
                if (data == null) return null
                val lat = (data["latitude"] as? Number)?.toDouble() ?: return null
                val lon = (data["longitude"] as? Number)?.toDouble() ?: return null
                return LocationCoordinate(lat, lon)
            }
        }
    }

    // Igualdad por id (como Moment: Equatable de iOS).
    override fun equals(other: Any?): Boolean = other is Moment && other.id == id
    override fun hashCode(): Int = id.hashCode()

    val isScheduled: Boolean get() = scheduledDate?.let { it.after(Date()) } ?: false

    val visibleMediaItems: List<MediaItem>
        get() = (mediaItems ?: emptyList()).filter { !it.isHiddenByModeration && it.url.trim().isNotEmpty() }

    val shouldUseLegacyMediaFallback: Boolean get() = mediaItems == null

    val primaryVisibleMediaItem: MediaItem? get() = visibleMediaItems.firstOrNull()

    val visibleMediaCount: Int
        get() {
            if (visibleMediaItems.isNotEmpty()) return visibleMediaItems.size
            var count = 0
            if (!imagePath?.trim().isNullOrEmpty()) count++
            if (!videoUrl?.trim().isNullOrEmpty()) count++
            return count
        }

    val isCarouselMoment: Boolean get() = visibleMediaCount > 1

    val previewImageURLString: String?
        get() {
            primaryVisibleMediaItem?.let { item ->
                return when (item.type) {
                    MediaItem.MediaType.IMAGE -> item.url.trim().ifEmpty { null }
                    MediaItem.MediaType.VIDEO -> item.thumbnailUrl?.trim()?.ifEmpty { null } ?: item.url.trim().ifEmpty { null }
                }
            }
            if (!shouldUseLegacyMediaFallback) return null
            thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            imagePath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            return null
        }

    val previewVideoURLString: String?
        get() {
            primaryVisibleMediaItem?.let { item ->
                if (item.type == MediaItem.MediaType.VIDEO) return item.url.trim().ifEmpty { null }
            }
            if (!shouldUseLegacyMediaFallback) return null
            return videoUrl?.trim()?.takeIf { it.isNotEmpty() }
        }

    /**
     * Vídeo con ExoPlayer/SurfaceView. No envolver en `ScreenshotProtectionMode.ContentSurface`
     * (el blit a SurfaceView seguro provoca parpadeos).
     */
    val hasHardwareVideo: Boolean
        get() = visibleMediaItems.any { it.type == MediaItem.MediaType.VIDEO } ||
            !videoUrl?.trim().isNullOrEmpty()

    /** ≡ iOS `scheduledTimeFormatted()` (formato corto `Xh Ym` / `Ym`). */
    fun scheduledTimeFormatted(context: Context): String {
        val scheduled = scheduledDate ?: return ""
        val diffSec = ((scheduled.time - System.currentTimeMillis()) / 1000L).toInt()
        if (diffSec <= 0) return context.getString(R.string.moment_publishing)
        val hours = diffSec / 3600
        val minutes = (diffSec % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** ≡ iOS `Moment.scheduledRemainingText` (MARK Moment Helpers). */
    fun scheduledRemainingText(context: Context): String {
        val scheduled = scheduledDate ?: return ""
        val now = Date()
        if (!scheduled.after(now)) return ""
        val millis = scheduled.time - now.time
        val days = (millis / 86_400_000L).toInt()
        val hours = ((millis % 86_400_000L) / 3_600_000L).toInt()
        val minutes = ((millis % 3_600_000L) / 60_000L).toInt()
        return when {
            days > 0 -> context.getString(
                if (days == 1) R.string.moment_scheduled_day else R.string.moment_scheduled_days,
                days,
            )
            hours > 0 -> context.getString(
                if (hours == 1) R.string.moment_scheduled_hour else R.string.moment_scheduled_hours,
                hours,
            )
            minutes > 0 -> context.getString(
                if (minutes == 1) R.string.moment_scheduled_minute else R.string.moment_scheduled_minutes,
                minutes,
            )
            else -> context.getString(R.string.moment_scheduled_soon)
        }
    }

    companion object {
        fun from(id: String?, data: Map<String, Any?>): Moment = Moment(
            id = id ?: data["id"] as? String,
            authorId = data["authorId"] as? String ?: "",
            username = data["username"] as? String ?: "",
            content = data["content"] as? String ?: "",
            imagePath = data["imageUrl"] as? String,
            videoUrl = data["videoUrl"] as? String,
            timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
            reactions = (data["reactions"] as? Map<*, *>)?.entries?.mapNotNull { entry ->
                val k = entry.key as? String ?: return@mapNotNull null
                k to ((entry.value as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>())
            }?.toMap() ?: emptyMap(),
            commentCount = (data["commentCount"] as? Number)?.toInt() ?: 0,
            profileImagePath = data["profileImagePath"] as? String,
            taggedUsers = (data["taggedUsers"] as? List<*>)?.filterIsInstance<String>(),
            mentionedUsers = (data["mentionedUsers"] as? List<*>)?.filterIsInstance<String>(),
            location = data["location"] as? String,
            locationCoordinate = LocationCoordinate.from(data["locationCoordinate"] as? Map<String, Any?>),
            audience = data["audience"] as? String,
            mediaItems = (data["mediaItems"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(MediaItem::from) },
            aspectRatio = data["aspectRatio"] as? String,
            customListId = data["customListId"] as? String,
            thumbnailUrl = data["thumbnailUrl"] as? String,
            videoDuration = (data["videoDuration"] as? Number)?.toDouble(),
            videoFileSize = (data["videoFileSize"] as? Number)?.toLong(),
            videoResolution = data["videoResolution"] as? String,
            scheduledDate = MediaItem.anyToDate(data["scheduledDate"]),
            isArchived = data["isArchived"] as? Boolean,
            archivedAt = MediaItem.anyToDate(data["archivedAt"]),
            isPinned = data["isPinned"] as? Boolean,
            pinnedAt = MediaItem.anyToDate(data["pinnedAt"]),
            gridPreviewScale = (data["gridPreviewScale"] as? Number)?.toDouble(),
            gridPreviewOffsetX = (data["gridPreviewOffsetX"] as? Number)?.toDouble(),
            gridPreviewOffsetY = (data["gridPreviewOffsetY"] as? Number)?.toDouble(),
            gridPreviewFitMode = data["gridPreviewFitMode"] as? String,
            gridPreviewBackground = data["gridPreviewBackground"] as? String,
            hasHiddenLayers = data["hasHiddenLayers"] as? Boolean ?: false,
            hiddenLayerCount = (data["hiddenLayerCount"] as? Number)?.toInt() ?: 0,
            isModerationHidden = data["isModerationHidden"] as? Boolean,
            originalAudience = data["originalAudience"] as? String,
            reviewRequired = data["reviewRequired"] as? Boolean,
            canRestore = data["canRestore"] as? Boolean,
            disableComments = data["disableComments"] as? Boolean ?: false,
            hideLikeCounts = data["hideLikeCounts"] as? Boolean ?: false,
            allowSharing = data["allowSharing"] as? Boolean ?: true,
        )
    }
}

// MARK: - CommentMentionEntity (mención dentro de un comentario)
data class CommentMentionEntity(
    val userId: String,
    val username: String,
    val rangeStart: Int,
    val rangeLength: Int,
) {
    val id: String get() = userId

    companion object {
        fun from(data: Map<String, Any?>): CommentMentionEntity = CommentMentionEntity(
            userId = data["userId"] as? String ?: "",
            username = data["username"] as? String ?: "",
            rangeStart = (data["rangeStart"] as? Number)?.toInt() ?: 0,
            rangeLength = (data["rangeLength"] as? Number)?.toInt() ?: 0,
        )
    }
}

// MARK: - Comment (comentario de un momento)
data class Comment(
    val id: String? = null,
    val authorId: String,
    val username: String,
    val content: String,
    val timestamp: Date,
    val profileImagePath: String? = null,
    val updatedAt: Date? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val parentCommentId: String? = null,
    val isEdited: Boolean? = false,
    val editedTimestamp: Date? = null,
    val mentions: List<CommentMentionEntity> = emptyList(),
) {
    // Flag offline, fuera de la igualdad (como en iOS).
    var isPending: Boolean? = false

    val isEditedFlag: Boolean get() = isEdited ?: false
    val wasEdited: Boolean get() = editedTimestamp != null

    companion object {
        fun from(id: String?, data: Map<String, Any?>): Comment = Comment(
            id = id ?: data["id"] as? String,
            authorId = data["authorId"] as? String ?: "",
            username = data["username"] as? String ?: "",
            content = data["content"] as? String ?: "",
            timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
            profileImagePath = data["profileImagePath"] as? String,
            updatedAt = MediaItem.anyToDate(data["updatedAt"]),
            reactions = (data["reactions"] as? Map<*, *>)?.entries?.mapNotNull { entry ->
                val k = entry.key as? String ?: return@mapNotNull null
                k to ((entry.value as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>())
            }?.toMap() ?: emptyMap(),
            parentCommentId = data["parentCommentId"] as? String,
            isEdited = data["isEdited"] as? Boolean ?: false,
            editedTimestamp = MediaItem.anyToDate(data["editedTimestamp"]),
            mentions = (data["mentions"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(CommentMentionEntity::from) } ?: emptyList(),
        )
    }
}

// MARK: - Hidden Layers (capas ocultas en un momento)
enum class HiddenLayerTextStyle(val raw: String) {
    CLEAN("clean"), SERIF("serif"), HANDWRITTEN("handwritten"), MONO("mono"), BUBBLE("bubble"), EDITORIAL("editorial");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
}

enum class HiddenLayerPresentationStyle(val raw: String) {
    GLASS_CARD("glassCard"), CAPTION_PILL("captionPill"), PAPER_NOTE("paperNote"),
    MARKER_LABEL("markerLabel"), FLOATING_QUOTE("floatingQuote"), MINIMAL_TEXT("minimalText");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: GLASS_CARD }
}

enum class HiddenLayerImageFrameStyle(val raw: String) {
    CLASSIC("classic"), CLEAN("clean"), VINTAGE("vintage");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
}

data class MomentHiddenLayer(
    val id: String = UUID.randomUUID().toString(),
    val type: LayerType,
    val anchorX: Double,
    val anchorY: Double,
    val width: Double,
    val height: Double,
    val shape: LayerShape = LayerShape.ROUNDED_RECT,
    val zIndex: Int = 0,
    val text: String? = null,
    val mediaURL: String? = null,
    val thumbnailURL: String? = null,
    val duration: Double? = null,
    val caption: String? = null,
    val imageOffsetX: Double? = null,
    val imageOffsetY: Double? = null,
    val imageScale: Double? = null,
    val imageFrameStyle: HiddenLayerImageFrameStyle? = null,
    val textStyle: HiddenLayerTextStyle? = null,
    val presentationStyle: HiddenLayerPresentationStyle = HiddenLayerPresentationStyle.GLASS_CARD,
    val unlockMode: UnlockMode = UnlockMode.IMMEDIATE,
    val unlockAt: Date? = null,
    val authorTimezoneIdentifier: String? = null,
    val discoverCount: Int? = null,
    val uniqueDiscovererCount: Int? = null,
    val lastDiscoveredAt: Date? = null,
    val moderationState: ModerationState? = ModerationState.VISIBLE,
    val moderationReason: String? = null,
    val moderationCategory: String? = null,
    val moderatedAt: Date? = null,
    val createdAt: Date = Date(),
) {
    enum class LayerType(val raw: String) {
        TEXT("text"), AUDIO("audio"), IMAGE("image");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: TEXT }
    }

    enum class LayerShape(val raw: String) {
        CIRCLE("circle"), ROUNDED_RECT("roundedRect");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: ROUNDED_RECT }
    }

    enum class ModerationState(val raw: String) {
        VISIBLE("visible"), HIDDEN("hidden"), PENDING("pending");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
    }

    enum class UnlockMode(val raw: String) {
        IMMEDIATE("immediate"), SCHEDULED("scheduled");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: IMMEDIATE }
    }

    val isHiddenByModeration: Boolean get() = moderationState == ModerationState.HIDDEN

    val isVisibleInViewer: Boolean
        get() = when (moderationState ?: ModerationState.VISIBLE) {
            ModerationState.VISIBLE -> true
            ModerationState.HIDDEN, ModerationState.PENDING -> false
        }

    fun isUnlocked(at: Date = Date()): Boolean = when (unlockMode) {
        UnlockMode.IMMEDIATE -> true
        UnlockMode.SCHEDULED -> unlockAt?.let { !it.after(at) } ?: true
    }

    companion object {
        fun from(data: Map<String, Any?>): MomentHiddenLayer = MomentHiddenLayer(
            id = data["id"] as? String ?: UUID.randomUUID().toString(),
            type = LayerType.from(data["type"] as? String),
            anchorX = (data["anchorX"] as? Number)?.toDouble() ?: 0.0,
            anchorY = (data["anchorY"] as? Number)?.toDouble() ?: 0.0,
            width = (data["width"] as? Number)?.toDouble() ?: 0.0,
            height = (data["height"] as? Number)?.toDouble() ?: 0.0,
            shape = LayerShape.from(data["shape"] as? String),
            zIndex = (data["zIndex"] as? Number)?.toInt() ?: 0,
            text = data["text"] as? String,
            mediaURL = data["mediaURL"] as? String,
            thumbnailURL = data["thumbnailURL"] as? String,
            duration = (data["duration"] as? Number)?.toDouble(),
            caption = data["caption"] as? String,
            imageOffsetX = (data["imageOffsetX"] as? Number)?.toDouble(),
            imageOffsetY = (data["imageOffsetY"] as? Number)?.toDouble(),
            imageScale = (data["imageScale"] as? Number)?.toDouble(),
            imageFrameStyle = HiddenLayerImageFrameStyle.from(data["imageFrameStyle"] as? String),
            textStyle = HiddenLayerTextStyle.from(data["textStyle"] as? String),
            presentationStyle = HiddenLayerPresentationStyle.from(data["presentationStyle"] as? String),
            unlockMode = UnlockMode.from(data["unlockMode"] as? String),
            unlockAt = MediaItem.anyToDate(data["unlockAt"]),
            authorTimezoneIdentifier = data["authorTimezoneIdentifier"] as? String,
            discoverCount = (data["discoverCount"] as? Number)?.toInt(),
            uniqueDiscovererCount = (data["uniqueDiscovererCount"] as? Number)?.toInt(),
            lastDiscoveredAt = MediaItem.anyToDate(data["lastDiscoveredAt"]),
            moderationState = ModerationState.from(data["moderationState"] as? String) ?: ModerationState.VISIBLE,
            moderationReason = data["moderationReason"] as? String,
            moderationCategory = data["moderationCategory"] as? String,
            moderatedAt = MediaItem.anyToDate(data["moderatedAt"]),
            createdAt = MediaItem.anyToDate(data["createdAt"]) ?: Date(),
        )
    }
}

data class HiddenLayerDiscovery(
    val viewerId: String,
    val username: String? = null,
    val profileImagePath: String? = null,
    val discoveredAt: Date,
) {
    val id: String get() = viewerId

    companion object {
        fun from(data: Map<String, Any?>): HiddenLayerDiscovery = HiddenLayerDiscovery(
            viewerId = data["viewerId"] as? String ?: "",
            username = data["username"] as? String,
            profileImagePath = data["profileImagePath"] as? String,
            discoveredAt = MediaItem.anyToDate(data["discoveredAt"]) ?: Date(),
        )
    }
}

data class HiddenLayerMetricsSnapshot(
    val layers: List<MomentHiddenLayer>,
    val uniquePeopleCount: Int,
    val recentDiscoveriesByLayer: Map<String, List<HiddenLayerDiscovery>>,
) {
    val totalDiscoveries: Int get() = layers.sumOf { maxOf(0, it.discoverCount ?: 0) }
    val discoveredLayerCount: Int get() = layers.count { (it.discoverCount ?: 0) > 0 }
    val totalLayerCount: Int get() = layers.size
    val coverageRatio: Double get() = if (totalLayerCount > 0) discoveredLayerCount.toDouble() / totalLayerCount else 0.0
    val topLayer: MomentHiddenLayer?
        get() = layers.maxWithOrNull(compareBy<MomentHiddenLayer> { it.discoverCount ?: 0 }
            .thenBy { it.lastDiscoveredAt?.time ?: Long.MIN_VALUE })
}

// MARK: - StickerData (sticker interactivo de una historia/momento)
data class StickerData(
    val stickerId: String? = null,
    val type: String,
    val content: String,
    val position: Point,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int? = null,
    val username: String? = null,
    val userId: String? = null,
    val hashtag: String? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val styleVariant: Int? = null,
    val questionText: String? = null,
    val pollOptions: List<String>? = null,
    val weatherSymbol: String? = null,
    val linkURL: String? = null,
    val linkTitle: String? = null,
    val countdownTitle: String? = null,
    val countdownTargetAtMs: Double? = null,
    val sliderEmoji: String? = null,
    val sliderPrompt: String? = null,
    val caption: String? = null,
    val profileImagePath: String? = null,
    val momentId: String? = null,
    val mediaCount: Int? = null,
    val quizQuestion: String? = null,
    val quizOptions: List<String>? = null,
    val quizCorrectIndex: Int? = null,
    val revealType: String? = null,
    val revealPattern: String? = null,
    val revealPrimaryColor: String? = null,
    val revealSecondaryColor: String? = null,
    val revealEffectColor: String? = null,
    val frameStyle: String? = null,
    val contentScale: Double? = null,
    val contentOffsetX: Double? = null,
    val contentOffsetY: Double? = null,
    val moderationState: String? = null,
    val moderationReason: String? = null,
    val moderationCategory: String? = null,
    val audioURL: String? = null,
    val audioDuration: Double? = null,
    val isAnimated: Boolean = false,
    val gifURL: String? = null,
    val videoURL: String? = null,
) {
    companion object {
        /** ≡ iOS `init(from:)` / mapa Firestore (incl. compat GIF antiguo). */
        fun from(data: Map<String, Any?>): StickerData {
            val position = Point.from(data["position"]) ?: Point(
                (data["positionX"] as? Number)?.toDouble() ?: 0.0,
                (data["positionY"] as? Number)?.toDouble() ?: 0.0,
            )
            fun strList(key: String) = (data[key] as? List<*>)?.filterIsInstance<String>()
            val type = data["type"] as? String ?: ""
            val content = data["content"] as? String ?: ""
            val decodedIsAnimated = data["isAnimated"] as? Boolean ?: false
            val decodedGifURL = data["gifURL"] as? String
            val (isAnimated, gifURL, videoURL) = if (!decodedIsAnimated && decodedGifURL == null) {
                val isGifFromContent = content.startsWith("http") &&
                    (content.contains(".gif") || content.contains("giphy"))
                val isGifFromType = type == "sticker" && content.startsWith("http")
                Triple(
                    isGifFromContent || isGifFromType,
                    if (isGifFromContent) content else null,
                    null as String?,
                )
            } else {
                Triple(decodedIsAnimated, decodedGifURL, data["videoURL"] as? String)
            }
            return StickerData(
                stickerId = data["stickerId"] as? String ?: data["id"] as? String,
                type = type,
                content = content,
                position = position,
                scale = (data["scale"] as? Number)?.toDouble() ?: 1.0,
                rotation = (data["rotation"] as? Number)?.toDouble() ?: 0.0,
                zIndex = (data["zIndex"] as? Number)?.toInt(),
                username = data["username"] as? String,
                userId = data["userId"] as? String,
                hashtag = data["hashtag"] as? String,
                location = data["location"] as? String,
                latitude = (data["latitude"] as? Number)?.toDouble(),
                longitude = (data["longitude"] as? Number)?.toDouble(),
                styleVariant = (data["styleVariant"] as? Number)?.toInt(),
                questionText = data["questionText"] as? String,
                pollOptions = strList("pollOptions"),
                weatherSymbol = data["weatherSymbol"] as? String,
                linkURL = data["linkURL"] as? String,
                linkTitle = data["linkTitle"] as? String,
                countdownTitle = data["countdownTitle"] as? String,
                countdownTargetAtMs = (data["countdownTargetAtMs"] as? Number)?.toDouble(),
                sliderEmoji = data["sliderEmoji"] as? String,
                sliderPrompt = data["sliderPrompt"] as? String,
                caption = data["caption"] as? String,
                profileImagePath = data["profileImagePath"] as? String,
                momentId = data["momentId"] as? String,
                mediaCount = (data["mediaCount"] as? Number)?.toInt(),
                quizQuestion = data["quizQuestion"] as? String,
                quizOptions = strList("quizOptions"),
                quizCorrectIndex = (data["quizCorrectIndex"] as? Number)?.toInt(),
                revealType = data["revealType"] as? String,
                revealPattern = data["revealPattern"] as? String,
                revealPrimaryColor = data["revealPrimaryColor"] as? String,
                revealSecondaryColor = data["revealSecondaryColor"] as? String,
                revealEffectColor = data["revealEffectColor"] as? String,
                frameStyle = data["frameStyle"] as? String,
                contentScale = (data["contentScale"] as? Number)?.toDouble(),
                contentOffsetX = (data["contentOffsetX"] as? Number)?.toDouble(),
                contentOffsetY = (data["contentOffsetY"] as? Number)?.toDouble(),
                moderationState = data["moderationState"] as? String,
                moderationReason = data["moderationReason"] as? String,
                moderationCategory = data["moderationCategory"] as? String,
                audioURL = data["audioURL"] as? String,
                audioDuration = (data["audioDuration"] as? Number)?.toDouble(),
                isAnimated = isAnimated,
                gifURL = gifURL,
                videoURL = videoURL,
            )
        }

        /** ≡ iOS `StickerData.from(_ stickerItem:)`. */
        fun from(stickerItem: StickerItem): StickerData {
            val interaction = stickerItem.interactionData
            return StickerData(
                stickerId = stickerItem.id,
                type = stickerItem.type.raw,
                content = extractContent(stickerItem),
                position = stickerItem.position,
                scale = stickerItem.scale,
                rotation = stickerItem.rotationRadians,
                zIndex = stickerItem.zIndex,
                username = interaction?.username,
                userId = interaction?.userId,
                hashtag = interaction?.hashtag,
                location = interaction?.location,
                latitude = interaction?.locationCoordinate?.latitude,
                longitude = interaction?.locationCoordinate?.longitude,
                styleVariant = interaction?.styleVariant,
                questionText = interaction?.questionText,
                pollOptions = interaction?.pollData,
                weatherSymbol = interaction?.weatherSymbol,
                linkURL = interaction?.linkURL,
                linkTitle = interaction?.linkTitle,
                countdownTitle = interaction?.countdownTitle,
                countdownTargetAtMs = interaction?.countdownTargetAtMs,
                sliderEmoji = interaction?.sliderEmoji,
                sliderPrompt = interaction?.sliderPrompt,
                caption = interaction?.caption,
                profileImagePath = interaction?.profileImagePath,
                momentId = interaction?.momentId,
                mediaCount = interaction?.mediaCount,
                quizQuestion = interaction?.quizQuestion,
                quizOptions = interaction?.quizOptions,
                quizCorrectIndex = interaction?.quizCorrectIndex,
                revealType = interaction?.revealType,
                revealPattern = interaction?.revealPattern,
                revealPrimaryColor = interaction?.revealPrimaryColor,
                revealSecondaryColor = interaction?.revealSecondaryColor,
                revealEffectColor = interaction?.revealEffectColor,
                frameStyle = interaction?.frameStyle,
                contentScale = interaction?.contentScale,
                contentOffsetX = interaction?.contentOffsetX,
                contentOffsetY = interaction?.contentOffsetY,
                moderationState = null,
                moderationReason = null,
                moderationCategory = null,
                audioURL = interaction?.audioURL,
                audioDuration = interaction?.audioDuration,
                isAnimated = stickerItem.isAnimated,
                gifURL = stickerItem.gifURL,
                videoURL = stickerItem.videoURL,
            )
        }

        /** ≡ iOS `extractContent(from:)` (Bitmap → Base64 / metadatos). */
        private fun extractContent(sticker: StickerItem): String {
            if (sticker.type == StickerType.SELFIE) {
                bitmapToBase64(sticker.image, android.graphics.Bitmap.CompressFormat.PNG, 100)?.let { return it }
            }
            if (sticker.type == StickerType.FRAME) {
                val resized = resizeMaxDimension(sticker.image, 900)
                bitmapToBase64(resized, android.graphics.Bitmap.CompressFormat.JPEG, 42)?.let { return it }
            }
            if (sticker.type == StickerType.SHARE_MOMENT) {
                // Calidad alta: el borde glass + media se recrean en el editor/viewer
                bitmapToBase64(sticker.image, android.graphics.Bitmap.CompressFormat.JPEG, 88)?.let { return it }
            }
            val jpegTypes = setOf(
                StickerType.GENERIC, StickerType.STICKER, StickerType.EMOJI, StickerType.TIME,
                StickerType.SELFIE, StickerType.QUESTION_RESPONSE,
                StickerType.LINK, StickerType.COUNTDOWN, StickerType.EMOJI_SLIDER,
                StickerType.FRAME, StickerType.QUIZ,
            )
            if (sticker.type in jpegTypes) {
                bitmapToBase64(sticker.image, android.graphics.Bitmap.CompressFormat.JPEG, 60)?.let { return it }
            }
            sticker.interactionData?.let { data ->
                when (sticker.type) {
                    StickerType.MENTION -> return "@${data.username.orEmpty()}"
                    StickerType.HASHTAG -> return "#${data.hashtag.orEmpty()}"
                    StickerType.LOCATION -> return data.location.orEmpty()
                    StickerType.QUESTION -> return data.questionText.orEmpty()
                    StickerType.POLL -> return data.pollData?.joinToString("|").orEmpty()
                    StickerType.WEATHER -> return data.weatherSymbol ?: "🌤️"
                    StickerType.LINK -> return data.linkURL.orEmpty()
                    StickerType.COUNTDOWN -> return data.countdownTitle.orEmpty()
                    StickerType.EMOJI_SLIDER -> return data.sliderPrompt.orEmpty()
                    else -> Unit
                }
            }
            if (sticker.isAnimated) {
                sticker.gifURL?.let { return it }
            }
            return "sticker_${sticker.type.raw}"
        }

        private fun bitmapToBase64(
            bitmap: android.graphics.Bitmap,
            format: android.graphics.Bitmap.CompressFormat,
            quality: Int,
        ): String? = runCatching {
            val stream = java.io.ByteArrayOutputStream()
            if (!bitmap.compress(format, quality, stream)) return null
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        }.getOrNull()

        private fun resizeMaxDimension(bitmap: android.graphics.Bitmap, maxDim: Int): android.graphics.Bitmap {
            val w = bitmap.width
            val h = bitmap.height
            val longest = maxOf(w, h)
            if (longest <= maxDim) return bitmap
            val scale = maxDim.toFloat() / longest
            return android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
                true,
            )
        }
    }
}

// MARK: - StoryTextOverlayMetadata (overlay de texto vivo en una historia)
// El factory build(...) es del editor (StoryEditingView) → módulo Creator.
data class StoryTextOverlayMetadata(
    val id: String,
    val text: String,
    val normalizedPosition: Point,
    val layerOrder: Int,
    val styleRaw: String,
    val colorHex: String,
    val fontSize: Double,
    val alignmentRaw: String,
    val backgroundFillRaw: String,
    val strokeRaw: String,
    val visualEffectRaw: String,
    val motionRaw: String,
    val forcesAllCaps: Boolean,
    val isLiveOverlay: Boolean = true,
    val gradientStopHexes: List<String>? = null,
    val gradientAngle: Int? = null,
) {
    companion object {
        fun from(data: Map<String, Any?>): StoryTextOverlayMetadata = StoryTextOverlayMetadata(
            id = data["id"] as? String ?: UUID.randomUUID().toString(),
            text = data["text"] as? String ?: "",
            normalizedPosition = Point.from(data["normalizedPosition"]) ?: Point(0.0, 0.0),
            layerOrder = (data["layerOrder"] as? Number)?.toInt() ?: 0,
            styleRaw = data["styleRaw"] as? String ?: "",
            colorHex = data["colorHex"] as? String ?: "",
            fontSize = (data["fontSize"] as? Number)?.toDouble() ?: 0.0,
            alignmentRaw = data["alignmentRaw"] as? String ?: "",
            backgroundFillRaw = data["backgroundFillRaw"] as? String ?: "",
            strokeRaw = data["strokeRaw"] as? String ?: "",
            visualEffectRaw = data["visualEffectRaw"] as? String ?: "",
            motionRaw = data["motionRaw"] as? String ?: "",
            forcesAllCaps = data["forcesAllCaps"] as? Boolean ?: false,
            isLiveOverlay = data["isLiveOverlay"] as? Boolean ?: true,
            gradientStopHexes = (data["gradientStopHexes"] as? List<*>)?.filterIsInstance<String>(),
            gradientAngle = (data["gradientAngle"] as? Number)?.toInt(),
        )
    }
}

// MARK: - HighlightedStory (destacados)
data class HighlightedStory(
    val id: String? = null,
    val title: String,
    val coverImageUrl: String? = null,
    val storiesCount: Int,
    val createdAt: Date,
    val storyIds: List<String>,
    val authorId: String,
) {
    companion object {
        fun from(id: String?, data: Map<String, Any?>): HighlightedStory = HighlightedStory(
            id = id ?: data["id"] as? String,
            title = data["title"] as? String ?: "",
            coverImageUrl = data["coverImageUrl"] as? String,
            storiesCount = (data["storiesCount"] as? Number)?.toInt() ?: 0,
            createdAt = MediaItem.anyToDate(data["createdAt"]) ?: Date(),
            storyIds = (data["storyIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            authorId = data["authorId"] as? String ?: "",
        )
    }
}

// MARK: - Story (historia)
data class Story(
    val id: String? = null,
    val authorId: String,
    val duration: Double,
    val expirationHours: Int? = null,
    val expirationDate: Date,
    val mediaItem: MediaItem,
    val profileImagePath: String? = null,
    val timestamp: Date,
    val username: String,
    val audience: String? = null,
    val customListId: String? = null,
    val text: String? = null,
    val textPosition: Point? = null,
    val textStyle: String? = null,
    val textPositionNormX: Double? = null,
    val textPositionNormY: Double? = null,
    val textColorHex: String? = null,
    val textFontSize: Double? = null,
    val textAlignment: String? = null,
    val textBackgroundFill: String? = null,
    val textStroke: String? = null,
    val textVisualEffect: String? = null,
    val textMotion: String? = null,
    val forcesAllCaps: Boolean? = null,
    val textLayerOrder: Int? = null,
    val textOverlayLive: Boolean? = null,
    val textOverlays: List<StoryTextOverlayMetadata>? = null,
    val stickers: List<StickerData>? = null,
    val drawingData: ByteArray? = null,
    val aspectRatio: String? = null,
    val backgroundFrameURL: String? = null,
    val backgroundBlurredFrameURL: String? = null,
    val chainId: String? = null,
    val chainPosition: Int? = null,
    val chainTitle: String? = null,
) {
    /**
     * `convertStickersToStickerItems` (UIImage/UIKit) → módulo Creator / viewer.
     * Aquí solo persisten [StickerData]; no se inventa render Android en el modelo.
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Story) return false
        return id == other.id &&
            authorId == other.authorId &&
            duration == other.duration &&
            expirationHours == other.expirationHours &&
            expirationDate == other.expirationDate &&
            mediaItem == other.mediaItem &&
            profileImagePath == other.profileImagePath &&
            timestamp == other.timestamp &&
            username == other.username &&
            audience == other.audience &&
            customListId == other.customListId &&
            text == other.text &&
            textPosition == other.textPosition &&
            textStyle == other.textStyle &&
            textPositionNormX == other.textPositionNormX &&
            textPositionNormY == other.textPositionNormY &&
            textColorHex == other.textColorHex &&
            textFontSize == other.textFontSize &&
            textAlignment == other.textAlignment &&
            textBackgroundFill == other.textBackgroundFill &&
            textStroke == other.textStroke &&
            textVisualEffect == other.textVisualEffect &&
            textMotion == other.textMotion &&
            forcesAllCaps == other.forcesAllCaps &&
            textLayerOrder == other.textLayerOrder &&
            textOverlayLive == other.textOverlayLive &&
            textOverlays == other.textOverlays &&
            stickers == other.stickers &&
            Arrays.equals(drawingData, other.drawingData) &&
            aspectRatio == other.aspectRatio &&
            backgroundFrameURL == other.backgroundFrameURL &&
            backgroundBlurredFrameURL == other.backgroundBlurredFrameURL &&
            chainId == other.chainId &&
            chainPosition == other.chainPosition &&
            chainTitle == other.chainTitle
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + authorId.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + (expirationHours ?: 0)
        result = 31 * result + expirationDate.hashCode()
        result = 31 * result + mediaItem.hashCode()
        result = 31 * result + (profileImagePath?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + (audience?.hashCode() ?: 0)
        result = 31 * result + (customListId?.hashCode() ?: 0)
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (textPosition?.hashCode() ?: 0)
        result = 31 * result + (textStyle?.hashCode() ?: 0)
        result = 31 * result + (textPositionNormX?.hashCode() ?: 0)
        result = 31 * result + (textPositionNormY?.hashCode() ?: 0)
        result = 31 * result + (textColorHex?.hashCode() ?: 0)
        result = 31 * result + (textFontSize?.hashCode() ?: 0)
        result = 31 * result + (textAlignment?.hashCode() ?: 0)
        result = 31 * result + (textBackgroundFill?.hashCode() ?: 0)
        result = 31 * result + (textStroke?.hashCode() ?: 0)
        result = 31 * result + (textVisualEffect?.hashCode() ?: 0)
        result = 31 * result + (textMotion?.hashCode() ?: 0)
        result = 31 * result + (forcesAllCaps?.hashCode() ?: 0)
        result = 31 * result + (textLayerOrder ?: 0)
        result = 31 * result + (textOverlayLive?.hashCode() ?: 0)
        result = 31 * result + (textOverlays?.hashCode() ?: 0)
        result = 31 * result + (stickers?.hashCode() ?: 0)
        result = 31 * result + (drawingData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (aspectRatio?.hashCode() ?: 0)
        result = 31 * result + (backgroundFrameURL?.hashCode() ?: 0)
        result = 31 * result + (backgroundBlurredFrameURL?.hashCode() ?: 0)
        result = 31 * result + (chainId?.hashCode() ?: 0)
        result = 31 * result + (chainPosition ?: 0)
        result = 31 * result + (chainTitle?.hashCode() ?: 0)
        return result
    }

    companion object {
        /** Devuelve null si no hay media válido (imagen/vídeo), como el throw de iOS. */
        fun from(id: String?, data: Map<String, Any?>): Story? {
            val chainId = data["chainId"] as? String
            val mediaItem = (data["mediaItem"] as? Map<String, Any?>)?.let(MediaItem::from)
                ?: (data["imagePath"] as? String)?.takeIf { it.isNotEmpty() }?.let { MediaItem(type = MediaItem.MediaType.IMAGE, url = it) }
                ?: (data["videoUrl"] as? String)?.takeIf { it.isNotEmpty() }?.let { MediaItem(type = MediaItem.MediaType.VIDEO, url = it) }
                ?: return null

            val textPosition = Point.from(data["textPosition"]) ?: run {
                val x = (data["textPositionX"] as? Number)?.toDouble()
                val y = (data["textPositionY"] as? Number)?.toDouble()
                if (x != null && y != null) Point(x, y) else null
            }

            return Story(
                id = id ?: data["id"] as? String,
                authorId = data["authorId"] as? String ?: "",
                duration = (data["duration"] as? Number)?.toDouble() ?: 0.0,
                expirationHours = (data["expirationHours"] as? Number)?.toInt() ?: (if (chainId != null) 48 else 24),
                expirationDate = MediaItem.anyToDate(data["expirationDate"]) ?: Date(),
                mediaItem = mediaItem,
                profileImagePath = data["profileImagePath"] as? String,
                timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
                username = data["username"] as? String ?: "",
                audience = data["audience"] as? String,
                customListId = data["customListId"] as? String,
                text = data["text"] as? String,
                textPosition = textPosition,
                textStyle = data["textStyle"] as? String,
                textPositionNormX = (data["textPositionNormX"] as? Number)?.toDouble(),
                textPositionNormY = (data["textPositionNormY"] as? Number)?.toDouble(),
                textColorHex = data["textColorHex"] as? String,
                textFontSize = (data["textFontSize"] as? Number)?.toDouble(),
                textAlignment = data["textAlignment"] as? String,
                textBackgroundFill = data["textBackgroundFill"] as? String,
                textStroke = data["textStroke"] as? String,
                textVisualEffect = data["textVisualEffect"] as? String,
                textMotion = data["textMotion"] as? String,
                forcesAllCaps = data["forcesAllCaps"] as? Boolean,
                textLayerOrder = (data["textLayerOrder"] as? Number)?.toInt(),
                textOverlayLive = data["textOverlayLive"] as? Boolean,
                textOverlays = (data["textOverlays"] as? List<*>)?.mapNotNull {
                    it.asStringKeyedMap()?.let(StoryTextOverlayMetadata::from)
                },
                stickers = (data["stickers"] as? List<*>)?.mapNotNull {
                    it.asStringKeyedMap()?.let(StickerData::from)
                },
                drawingData = (data["drawingData"] as? com.google.firebase.firestore.Blob)?.toBytes() ?: (data["drawingData"] as? ByteArray),
                aspectRatio = data["aspectRatio"] as? String,
                backgroundFrameURL = data["backgroundFrameURL"] as? String,
                backgroundBlurredFrameURL = data["backgroundBlurredFrameURL"] as? String,
                chainId = chainId,
                chainPosition = (data["chainPosition"] as? Number)?.toInt(),
                chainTitle = data["chainTitle"] as? String,
            )
        }
    }
}

/** Firestore/JSON maps llegan como `Map<*, *>` — cast tipado fiable. */
@Suppress("UNCHECKED_CAST")
private fun Any?.asStringKeyedMap(): Map<String, Any?>? {
    val map = this as? Map<*, *> ?: return null
    return map.entries.associate { (key, value) -> key.toString() to value }
}

// MARK: - Visibilidad de contenido (ContentProtocol en Models.swift)
// Enum canónico + interface viven en services/privacy (ContentVisibilityservice.kt).
// Aquí solo las extensiones Moment/Story ≡ iOS (customViewers/hiddenFrom stub nil).

val Moment.visibilityType: ContentVisibilityType
    get() = when (audience) {
        "everyone" -> ContentVisibilityType.EVERYONE
        "mutuals" -> ContentVisibilityType.MUTUALS
        "bestFriends" -> ContentVisibilityType.BEST_FRIENDS
        "custom" -> ContentVisibilityType.CUSTOM
        else -> ContentVisibilityType.EVERYONE
    }

/** ≡ iOS stub (siempre nil hasta cablear lista custom). */
val Moment.customViewers: List<String>? get() = null

/** ≡ iOS stub. */
val Moment.hiddenFrom: List<String>? get() = null

val Story.visibilityType: ContentVisibilityType
    get() = when (audience) {
        "everyone" -> ContentVisibilityType.EVERYONE
        "mutuals" -> ContentVisibilityType.MUTUALS
        "bestFriends" -> ContentVisibilityType.BEST_FRIENDS
        "custom" -> ContentVisibilityType.CUSTOM
        else -> ContentVisibilityType.EVERYONE
    }

val Story.customViewers: List<String>? get() = null
val Story.hiddenFrom: List<String>? get() = null

// MARK: - NotificationType
enum class NotificationType(val raw: String) {
    LIKE("like"),
    REACTION("reaction"),
    COMMENT("comment"),
    MENTION("mention"),
    NEW_FOLLOWER("newFollower"),
    FOLLOW_REQUEST("followRequest"),
    REQUEST_ACCEPTED("requestAccepted"),
    MUTUAL_CONNECTION("mutualConnection"),
    STORY_REACTION("storyReaction"),
    MESSAGE("message"),
    MESSAGE_REACTION("messageReaction"),
    CHAT_BUZZ("chatBuzz"),
    GENTLE_REMINDER("gentleReminder"),
    PHOTO_TAG("photoTag"),
    ECHO_SUGGESTION("echoSuggestion"),
    DATA_EXPORT_READY("data_export_ready"),
    STORY_CHAIN_CONTINUED("storyChainContinued"),
    MEDIA_MODERATION("mediaModeration");

    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
    // displayName / systemIconName (localizados + SF Symbols) → capa de UI.
}

// MARK: - Notification
data class MomentsNotification(
    val id: String? = null,
    val type: NotificationType,
    val senderId: String,
    val senderUsername: String,
    val timestamp: Date = Date(),
    val isPending: Boolean = true,
    val title: String? = null,
    val message: String? = null,
    val downloadURL: String? = null,
    val momentId: String? = null,
    val visitCount: Int? = null,
    val storyId: String? = null,
    val storyAuthorId: String? = null,
    val storyPreviewUrl: String? = null,
    val mentionContext: String? = null,
    val targetAuthorId: String? = null,
    val targetAuthorUsername: String? = null,
    val reaction: String? = null,
    val reactionCount: Int? = null,
    val commentId: String? = null,
    val conversationId: String? = null,
    val echoId: String? = null,
    val moderationScope: String? = null,
    val chainId: String? = null,
    val chainTitle: String? = null,
    val chainPosition: Int? = null,
    val totalParts: Int? = null,
    val chainRole: String? = null,
    val messageId: String? = null,
    val messageType: String? = null,
    val buzzEventId: String? = null,
    val reminderVariant: String? = null,
    val isReactionPlural: Boolean? = null,
) {
    companion object {
        /**
         * null solo si falta `type` (iOS throw en decode).
         * Tipo desconocido → [NotificationType.NEW_FOLLOWER] (≡ `?? .newFollower`).
         */
        fun from(id: String?, data: Map<String, Any?>): MomentsNotification? {
            val typeRaw = data["type"] as? String ?: return null
            val type = NotificationType.from(typeRaw) ?: NotificationType.NEW_FOLLOWER
            val isPending = data["isPending"] as? Boolean
                ?: (data["isRead"] as? Boolean)?.let { !it }
                ?: true
            // reaction: reaction → reactionType → commentText → moderationType (push/docs de moderación).
            val reaction = data["reaction"] as? String
                ?: data["reactionType"] as? String
                ?: data["commentText"] as? String
                ?: data["moderationType"] as? String
            val isReactionPlural = data["isReactionPlural"] as? Boolean
                ?: (data["isReactionPlural"] as? String)?.let { it == "1" || it.lowercase() == "true" }
            val reactionCount = (data["reactionCount"] as? Number)?.toInt()
                ?: (data["moderatedMediaCount"] as? Number)?.toInt()
                ?: (data["reactionCount"] as? String)?.toIntOrNull()
                ?: (data["moderatedMediaCount"] as? String)?.toIntOrNull()
            return MomentsNotification(
                id = id ?: data["id"] as? String,
                type = type,
                senderId = data["senderId"] as? String ?: "",
                senderUsername = data["senderUsername"] as? String ?: "",
                timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
                isPending = isPending,
                title = data["title"] as? String,
                message = data["message"] as? String,
                downloadURL = data["downloadURL"] as? String,
                momentId = data["momentId"] as? String,
                visitCount = (data["visitCount"] as? Number)?.toInt(),
                storyId = data["storyId"] as? String,
                storyAuthorId = data["storyAuthorId"] as? String,
                storyPreviewUrl = data["storyPreviewUrl"] as? String,
                mentionContext = data["mentionContext"] as? String,
                targetAuthorId = data["targetAuthorId"] as? String,
                targetAuthorUsername = data["targetAuthorUsername"] as? String,
                reaction = reaction,
                reactionCount = reactionCount,
                commentId = data["commentId"] as? String,
                conversationId = data["conversationId"] as? String,
                echoId = data["echoId"] as? String,
                moderationScope = data["moderationScope"] as? String,
                chainId = data["chainId"] as? String,
                chainTitle = data["chainTitle"] as? String,
                chainPosition = (data["chainPosition"] as? Number)?.toInt(),
                totalParts = (data["totalParts"] as? Number)?.toInt(),
                chainRole = data["chainRole"] as? String,
                messageId = data["messageId"] as? String,
                messageType = data["messageType"] as? String,
                buzzEventId = data["buzzEventId"] as? String,
                reminderVariant = data["reminderVariant"] as? String,
                isReactionPlural = isReactionPlural,
            )
        }
    }
}

// MARK: - Sticker Questions
data class QuestionResponse(
    val id: String,
    val userId: String,
    val response: String,
    val timestamp: Date,
    val isAnonymous: Boolean = true,
) {
    companion object {
        fun create(userId: String, response: String) = QuestionResponse(UUID.randomUUID().toString(), userId, response, Date(), true)

        fun from(data: Map<String, Any?>): QuestionResponse = QuestionResponse(
            id = data["id"] as? String ?: UUID.randomUUID().toString(),
            userId = data["userId"] as? String ?: "",
            response = data["response"] as? String ?: "",
            timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
            isAnonymous = data["isAnonymous"] as? Boolean ?: true,
        )
    }
}

data class QuestionData(
    val questionText: String,
    val responses: List<QuestionResponse> = emptyList(),
    val responseCount: Int = 0,
    val createdAt: Date = Date(),
) {
    companion object {
        /** ≡ iOS `init(questionText:)` — responses vacías, count 0, createdAt ahora. */
        fun create(questionText: String) = QuestionData(questionText)

        fun from(data: Map<String, Any?>): QuestionData = QuestionData(
            questionText = data["questionText"] as? String ?: "",
            responses = (data["responses"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(QuestionResponse::from) } ?: emptyList(),
            responseCount = (data["responseCount"] as? Number)?.toInt() ?: 0,
            createdAt = MediaItem.anyToDate(data["createdAt"]) ?: Date(),
        )
    }
}

// ==========================================================================
// MARK: - Serialización a Firestore (equivalente a encode(to:) de iOS)
// Omite nulls (como encodeIfPresent), fechas → Timestamp, enums → raw.
// ==========================================================================

fun FilterSettings.toMap(): Map<String, Any> = mapOf("name" to name, "intensity" to intensity)

fun Point.toMap(): Map<String, Any> = mapOf("x" to x, "y" to y)

fun PhotoTag.toMap(): Map<String, Any> = mapOf("id" to id, "userId" to userId, "username" to username, "x" to x, "y" to y)

fun VideoVariants.toMap(): Map<String, Any> = buildMap {
    low?.let { put("low", it) }
    medium?.let { put("medium", it) }
    high?.let { put("high", it) }
}

fun MediaItem.toMap(): Map<String, Any> = buildMap {
    put("id", id); put("type", type.raw); put("url", url)
    aspectRatio?.let { put("aspectRatio", it) }
    thumbnailUrl?.let { put("thumbnailUrl", it) }
    videoDuration?.let { put("videoDuration", it) }
    videoFileSize?.let { put("videoFileSize", it) }
    videoResolution?.let { put("videoResolution", it) }
    videoProcessingStatus?.let { put("videoProcessingStatus", it.raw) }
    originalVideoUrl?.let { put("originalVideoUrl", it) }
    videoVariants?.let { put("videoVariants", it.toMap()) }
    tags?.let { put("tags", it.map { t -> t.toMap() }) }
    moderationState?.let { put("moderationState", it.raw) }
    moderationReason?.let { put("moderationReason", it) }
    moderationCategory?.let { put("moderationCategory", it) }
    moderationConfidence?.let { put("moderationConfidence", it) }
    moderatedAt?.let { put("moderatedAt", Timestamp(it)) }
}

fun CommentMentionEntity.toMap(): Map<String, Any> =
    mapOf("userId" to userId, "username" to username, "rangeStart" to rangeStart, "rangeLength" to rangeLength)

fun Comment.toMap(): Map<String, Any> = buildMap {
    id?.let { put("id", it) }
    put("authorId", authorId); put("username", username); put("content", content)
    put("timestamp", Timestamp(timestamp))
    profileImagePath?.let { put("profileImagePath", it) }
    updatedAt?.let { put("updatedAt", Timestamp(it)) }
    put("reactions", reactions)
    parentCommentId?.let { put("parentCommentId", it) }
    isEdited?.let { put("isEdited", it) }
    editedTimestamp?.let { put("editedTimestamp", Timestamp(it)) }
    put("mentions", mentions.map { it.toMap() })
    // isPending no se serializa (como iOS).
}

fun FollowRequest.toMap(): Map<String, Any> = buildMap {
    put("id", id); put("senderId", senderId); put("senderUsername", senderUsername)
    put("recipientId", recipientId); put("status", status.raw); put("timestamp", Timestamp(timestamp))
    expirationDate?.let { put("expirationDate", Timestamp(it)) }
}

fun Connection.toMap(): Map<String, Any> = mapOf("userId" to userId, "timestamp" to Timestamp(timestamp))
fun FollowerRecord.toMap(): Map<String, Any> = mapOf("userId" to userId, "timestamp" to Timestamp(timestamp))

fun Moment.LocationCoordinate.toMap(): Map<String, Any> = mapOf("latitude" to latitude, "longitude" to longitude)

fun MomentHiddenLayer.toMap(): Map<String, Any> = buildMap {
    put("id", id); put("type", type.raw); put("anchorX", anchorX); put("anchorY", anchorY)
    put("width", width); put("height", height); put("shape", shape.raw); put("zIndex", zIndex)
    text?.let { put("text", it) }
    mediaURL?.let { put("mediaURL", it) }
    thumbnailURL?.let { put("thumbnailURL", it) }
    duration?.let { put("duration", it) }
    caption?.let { put("caption", it) }
    imageOffsetX?.let { put("imageOffsetX", it) }
    imageOffsetY?.let { put("imageOffsetY", it) }
    imageScale?.let { put("imageScale", it) }
    imageFrameStyle?.let { put("imageFrameStyle", it.raw) }
    textStyle?.let { put("textStyle", it.raw) }
    put("presentationStyle", presentationStyle.raw)
    put("unlockMode", unlockMode.raw)
    unlockAt?.let { put("unlockAt", Timestamp(it)) }
    authorTimezoneIdentifier?.let { put("authorTimezoneIdentifier", it) }
    discoverCount?.let { put("discoverCount", it) }
    uniqueDiscovererCount?.let { put("uniqueDiscovererCount", it) }
    lastDiscoveredAt?.let { put("lastDiscoveredAt", Timestamp(it)) }
    moderationState?.let { put("moderationState", it.raw) }
    moderationReason?.let { put("moderationReason", it) }
    moderationCategory?.let { put("moderationCategory", it) }
    moderatedAt?.let { put("moderatedAt", Timestamp(it)) }
    put("createdAt", Timestamp(createdAt))
}

fun HiddenLayerDiscovery.toMap(): Map<String, Any> = buildMap {
    put("viewerId", viewerId)
    username?.let { put("username", it) }
    profileImagePath?.let { put("profileImagePath", it) }
    put("discoveredAt", Timestamp(discoveredAt))
}

fun Moment.toMap(): Map<String, Any> = buildMap {
    id?.let { put("id", it) }
    put("authorId", authorId); put("username", username); put("content", content)
    imagePath?.let { put("imageUrl", it) } // clave Firestore "imageUrl"
    videoUrl?.let { put("videoUrl", it) }
    put("timestamp", Timestamp(timestamp))
    put("reactions", reactions); put("commentCount", commentCount)
    profileImagePath?.let { put("profileImagePath", it) }
    taggedUsers?.let { put("taggedUsers", it) }
    mentionedUsers?.let { put("mentionedUsers", it) }
    location?.let { put("location", it) }
    locationCoordinate?.let { put("locationCoordinate", it.toMap()) }
    audience?.let { put("audience", it) }
    mediaItems?.let { put("mediaItems", it.map { m -> m.toMap() }) }
    aspectRatio?.let { put("aspectRatio", it) }
    customListId?.let { put("customListId", it) }
    thumbnailUrl?.let { put("thumbnailUrl", it) }
    videoDuration?.let { put("videoDuration", it) }
    videoFileSize?.let { put("videoFileSize", it) }
    videoResolution?.let { put("videoResolution", it) }
    scheduledDate?.let { put("scheduledDate", Timestamp(it)) }
    archivedAt?.let { put("archivedAt", Timestamp(it)) }
    isArchived?.let { put("isArchived", it) }
    pinnedAt?.let { put("pinnedAt", Timestamp(it)) }
    isPinned?.let { put("isPinned", it) }
    gridPreviewScale?.let { put("gridPreviewScale", it) }
    gridPreviewOffsetX?.let { put("gridPreviewOffsetX", it) }
    gridPreviewOffsetY?.let { put("gridPreviewOffsetY", it) }
    gridPreviewFitMode?.let { put("gridPreviewFitMode", it) }
    gridPreviewBackground?.let { put("gridPreviewBackground", it) }
    put("disableComments", disableComments); put("hideLikeCounts", hideLikeCounts); put("allowSharing", allowSharing)
    put("hasHiddenLayers", hasHiddenLayers); put("hiddenLayerCount", hiddenLayerCount)
    isModerationHidden?.let { put("isModerationHidden", it) }
    originalAudience?.let { put("originalAudience", it) }
    reviewRequired?.let { put("reviewRequired", it) }
    canRestore?.let { put("canRestore", it) }
}

fun HighlightedStory.toMap(): Map<String, Any> = buildMap {
    id?.let { put("id", it) }
    put("title", title)
    coverImageUrl?.let { put("coverImageUrl", it) }
    put("storiesCount", storiesCount); put("createdAt", Timestamp(createdAt))
    put("storyIds", storyIds); put("authorId", authorId)
}

fun StoryTextOverlayMetadata.toMap(): Map<String, Any> = buildMap {
    put("id", id); put("text", text); put("normalizedPosition", normalizedPosition.toMap())
    put("layerOrder", layerOrder); put("styleRaw", styleRaw); put("colorHex", colorHex)
    put("fontSize", fontSize); put("alignmentRaw", alignmentRaw); put("backgroundFillRaw", backgroundFillRaw)
    put("strokeRaw", strokeRaw); put("visualEffectRaw", visualEffectRaw); put("motionRaw", motionRaw)
    put("forcesAllCaps", forcesAllCaps); put("isLiveOverlay", isLiveOverlay)
    gradientStopHexes?.let { put("gradientStopHexes", it) }
    gradientAngle?.let { put("gradientAngle", it) }
}

fun StickerData.toMap(): Map<String, Any> = buildMap {
    stickerId?.let { put("stickerId", it) }
    put("type", type)
    put("content", content)
    // ≡ iOS FirestoreStoriesRepository.serializedStorySticker — positionX/Y, no mapa `position`.
    put("positionX", position.x)
    put("positionY", position.y)
    put("scale", scale)
    put("rotation", rotation)
    zIndex?.let { put("zIndex", it) }
    username?.let { put("username", it) }; userId?.let { put("userId", it) }
    hashtag?.let { put("hashtag", it) }; location?.let { put("location", it) }
    latitude?.let { put("latitude", it) }; longitude?.let { put("longitude", it) }
    styleVariant?.let { put("styleVariant", it) }; questionText?.let { put("questionText", it) }
    pollOptions?.let { put("pollOptions", it) }; weatherSymbol?.let { put("weatherSymbol", it) }
    linkURL?.let { put("linkURL", it) }; linkTitle?.let { put("linkTitle", it) }
    countdownTitle?.let { put("countdownTitle", it) }; countdownTargetAtMs?.let { put("countdownTargetAtMs", it) }
    sliderEmoji?.let { put("sliderEmoji", it) }; sliderPrompt?.let { put("sliderPrompt", it) }
    caption?.let { put("caption", it) }; profileImagePath?.let { put("profileImagePath", it) }
    momentId?.let { put("momentId", it) }; mediaCount?.let { put("mediaCount", it) }
    quizQuestion?.let { put("quizQuestion", it) }; quizOptions?.let { put("quizOptions", it) }
    quizCorrectIndex?.let { put("quizCorrectIndex", it) }
    revealType?.let { put("revealType", it) }; revealPattern?.let { put("revealPattern", it) }
    revealPrimaryColor?.let { put("revealPrimaryColor", it) }; revealSecondaryColor?.let { put("revealSecondaryColor", it) }
    revealEffectColor?.let { put("revealEffectColor", it) }; frameStyle?.let { put("frameStyle", it) }
    contentScale?.let { put("contentScale", it) }; contentOffsetX?.let { put("contentOffsetX", it) }
    contentOffsetY?.let { put("contentOffsetY", it) }
    moderationState?.let { put("moderationState", it) }; moderationReason?.let { put("moderationReason", it) }
    moderationCategory?.let { put("moderationCategory", it) }
    audioURL?.let { put("audioURL", it) }; audioDuration?.let { put("audioDuration", it) }
    put("isAnimated", isAnimated)
    gifURL?.let { put("gifURL", it) }; videoURL?.let { put("videoURL", it) }
}

fun Story.toMap(): Map<String, Any> = buildMap {
    id?.let { put("id", it) }
    put("authorId", authorId); put("username", username); put("mediaItem", mediaItem.toMap())
    put("duration", duration)
    expirationHours?.let { put("expirationHours", it) }
    put("timestamp", Timestamp(timestamp)); put("expirationDate", Timestamp(expirationDate))
    profileImagePath?.let { put("profileImagePath", it) }
    audience?.let { put("audience", it) }
    customListId?.let { put("customListId", it) }
    text?.let { put("text", it) }
    // Guardamos posición como x/y (compat con el lado de lectura).
    textPosition?.let { put("textPositionX", it.x); put("textPositionY", it.y) }
    textStyle?.let { put("textStyle", it) }
    textPositionNormX?.let { put("textPositionNormX", it) }
    textPositionNormY?.let { put("textPositionNormY", it) }
    textColorHex?.let { put("textColorHex", it) }
    textFontSize?.let { put("textFontSize", it) }
    textAlignment?.let { put("textAlignment", it) }
    textBackgroundFill?.let { put("textBackgroundFill", it) }
    textStroke?.let { put("textStroke", it) }
    textVisualEffect?.let { put("textVisualEffect", it) }
    textMotion?.let { put("textMotion", it) }
    forcesAllCaps?.let { put("forcesAllCaps", it) }
    textLayerOrder?.let { put("textLayerOrder", it) }
    textOverlayLive?.let { put("textOverlayLive", it) }
    textOverlays?.let { put("textOverlays", it.map { o -> o.toMap() }) }
    stickers?.let { put("stickers", it.map { s -> s.toMap() }) }
    drawingData?.let { put("drawingData", com.google.firebase.firestore.Blob.fromBytes(it)) }
    aspectRatio?.let { put("aspectRatio", it) }
    backgroundFrameURL?.let { put("backgroundFrameURL", it) }
    backgroundBlurredFrameURL?.let { put("backgroundBlurredFrameURL", it) }
    chainId?.let { put("chainId", it) }
    chainPosition?.let { put("chainPosition", it) }
    chainTitle?.let { put("chainTitle", it) }
    // Compat: imagePath/videoUrl según el tipo del media.
    if (mediaItem.type == MediaItem.MediaType.IMAGE) put("imagePath", mediaItem.url) else put("videoUrl", mediaItem.url)
}

fun MomentsNotification.toMap(): Map<String, Any> = buildMap {
    id?.let { put("id", it) }
    put("type", type.raw); put("senderId", senderId); put("senderUsername", senderUsername)
    put("timestamp", Timestamp(timestamp)); put("isPending", isPending)
    title?.let { put("title", it) }; message?.let { put("message", it) }
    downloadURL?.let { put("downloadURL", it) }; momentId?.let { put("momentId", it) }
    visitCount?.let { put("visitCount", it) }; storyId?.let { put("storyId", it) }
    storyAuthorId?.let { put("storyAuthorId", it) }; storyPreviewUrl?.let { put("storyPreviewUrl", it) }
    mentionContext?.let { put("mentionContext", it) }; targetAuthorId?.let { put("targetAuthorId", it) }
    targetAuthorUsername?.let { put("targetAuthorUsername", it) }
    reaction?.let { put("reaction", it) }; reactionCount?.let { put("reactionCount", it) }
    commentId?.let { put("commentId", it) }; conversationId?.let { put("conversationId", it) }
    echoId?.let { put("echoId", it) }; moderationScope?.let { put("moderationScope", it) }
    chainId?.let { put("chainId", it) }; chainTitle?.let { put("chainTitle", it) }
    chainPosition?.let { put("chainPosition", it) }; totalParts?.let { put("totalParts", it) }
    chainRole?.let { put("chainRole", it) }; messageId?.let { put("messageId", it) }
    messageType?.let { put("messageType", it) }; buzzEventId?.let { put("buzzEventId", it) }
    reminderVariant?.let { put("reminderVariant", it) }; isReactionPlural?.let { put("isReactionPlural", it) }
}

fun QuestionResponse.toMap(): Map<String, Any> =
    mapOf("id" to id, "userId" to userId, "response" to response, "timestamp" to Timestamp(timestamp), "isAnonymous" to isAnonymous)

fun QuestionData.toMap(): Map<String, Any> = mapOf(
    "questionText" to questionText,
    "responses" to responses.map { it.toMap() },
    "responseCount" to responseCount,
    "createdAt" to Timestamp(createdAt),
)

// Compatibilidad para los consumidores que aún importan el modelo histórico.
typealias CustomAudienceList = com.moments.android.views.creator.audienceselector.CustomAudienceList

// MARK: - MomentGridPreviewSettings (MomentGridPreview.swift)
data class MomentGridPreviewSettings(
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val fitMode: FitMode = FitMode.FILL,
    val background: Background = Background.BLACK,
) {
    enum class FitMode(val raw: String) {
        FILL("fill"), FIT("fit");
    }

    enum class Background(val raw: String) {
        BLACK("black"), WHITE("white");
    }

    /** Paridad iOS `isDefault` — no incluye `background`. */
    val isDefault: Boolean
        get() = kotlin.math.abs(scale - 1.0) < 0.001 &&
            kotlin.math.abs(offsetX) < 0.001 &&
            kotlin.math.abs(offsetY) < 0.001 &&
            fitMode == FitMode.FILL

    companion object {
        /** Paridad iOS `MomentGridPreviewSettings.default`. */
        val DEFAULT = MomentGridPreviewSettings()
    }
}
