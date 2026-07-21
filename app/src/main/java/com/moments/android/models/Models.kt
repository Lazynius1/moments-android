package com.moments.android.models

import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID

// Port incremental de Models.swift de iOS. Se van añadiendo tipos poco a poco.

// MARK: - FilterSettings
data class FilterSettings(val name: String, val intensity: Double)

// MARK: - Seguimiento y solicitudes
enum class FollowRequestStatus(val raw: String) {
    PENDING("pending"), ACCEPTED("accepted"), REJECTED("rejected"), CANCELLED("cancelled");
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
        get() {
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
                // NOTA: el fallback canónico CreatorMedia.AspectRatio se añadirá con el módulo Creator.
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
