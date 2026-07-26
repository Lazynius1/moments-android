package com.moments.android.models.cache

import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.toMap
import org.json.JSONArray
import org.json.JSONObject
import java.util.Arrays
import java.util.Date
import java.util.UUID

/**
 * Port de `Models/Cache/CachedMoment.swift`.
 * Espejo de [Moment] para persistencia offline (local-first).
 * Blobs JSON ≡ `Data` / JSONEncoder en iOS.
 */
data class CachedMoment(
    val momentId: String,
    val authorId: String,
    val username: String,
    val content: String,
    val imagePath: String? = null,
    val videoUrl: String? = null,
    val timestamp: Date,
    val commentCount: Int? = 0,
    val profileImagePath: String? = null,
    val location: String? = null,
    val audience: String? = null,
    val aspectRatio: String? = null,
    val thumbnailUrl: String? = null,
    val videoDuration: Double? = null,
    val videoFileSize: Long? = null,
    val videoResolution: String? = null,
    val customListId: String? = null,
    val disableComments: Boolean? = false,
    val hideLikeCounts: Boolean? = false,
    val allowSharing: Boolean? = true,
    val scheduledDate: Date? = null,
    val isPinned: Boolean? = null,
    val pinnedAt: Date? = null,
    val gridPreviewScale: Double? = null,
    val gridPreviewOffsetX: Double? = null,
    val gridPreviewOffsetY: Double? = null,
    val gridPreviewFitMode: String? = null,
    val gridPreviewBackground: String? = null,
    val hasHiddenLayers: Boolean? = false,
    val hiddenLayerCount: Int? = 0,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val reactionsData: ByteArray? = null,
    val mediaItemsData: ByteArray? = null,
    val taggedUsersData: ByteArray? = null,
    val mentionedUsersData: ByteArray? = null,
    val lastSyncedAt: Date = Date(),
    /** `"feed"` | `"explore"` | `"profile"` (+ variantes profile_*) */
    val feedSection: String = "feed",
) {
    /** ≡ iOS `toMoment()`. */
    fun toMoment(): Moment? {
        val reactions = decodeReactions(reactionsData)
        val mediaItems = decodeMediaItems(mediaItemsData)
        val taggedUsers = decodeStringList(taggedUsersData)
        val mentionedUsers = decodeStringList(mentionedUsersData)
        val locationCoordinate =
            if (locationLatitude != null && locationLongitude != null) {
                Moment.LocationCoordinate(locationLatitude, locationLongitude)
            } else {
                null
            }
        return Moment(
            id = momentId,
            authorId = authorId,
            username = username,
            content = content,
            imagePath = imagePath,
            videoUrl = videoUrl,
            timestamp = timestamp,
            reactions = reactions,
            commentCount = commentCount ?: 0,
            profileImagePath = profileImagePath,
            taggedUsers = taggedUsers,
            mentionedUsers = mentionedUsers,
            location = location,
            locationCoordinate = locationCoordinate,
            audience = audience,
            mediaItems = mediaItems,
            aspectRatio = aspectRatio,
            customListId = customListId,
            thumbnailUrl = thumbnailUrl,
            videoDuration = videoDuration,
            videoFileSize = videoFileSize,
            videoResolution = videoResolution,
            disableComments = disableComments ?: false,
            hideLikeCounts = hideLikeCounts ?: false,
            allowSharing = allowSharing ?: true,
            scheduledDate = scheduledDate,
            isPinned = isPinned,
            pinnedAt = pinnedAt,
            gridPreviewScale = gridPreviewScale,
            gridPreviewOffsetX = gridPreviewOffsetX,
            gridPreviewOffsetY = gridPreviewOffsetY,
            gridPreviewFitMode = gridPreviewFitMode,
            gridPreviewBackground = gridPreviewBackground,
            hasHiddenLayers = hasHiddenLayers ?: false,
            hiddenLayerCount = hiddenLayerCount ?: 0,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedMoment) return false
        return momentId == other.momentId &&
            authorId == other.authorId &&
            username == other.username &&
            content == other.content &&
            imagePath == other.imagePath &&
            videoUrl == other.videoUrl &&
            timestamp == other.timestamp &&
            commentCount == other.commentCount &&
            profileImagePath == other.profileImagePath &&
            location == other.location &&
            audience == other.audience &&
            aspectRatio == other.aspectRatio &&
            thumbnailUrl == other.thumbnailUrl &&
            videoDuration == other.videoDuration &&
            videoFileSize == other.videoFileSize &&
            videoResolution == other.videoResolution &&
            customListId == other.customListId &&
            disableComments == other.disableComments &&
            hideLikeCounts == other.hideLikeCounts &&
            allowSharing == other.allowSharing &&
            scheduledDate == other.scheduledDate &&
            isPinned == other.isPinned &&
            pinnedAt == other.pinnedAt &&
            gridPreviewScale == other.gridPreviewScale &&
            gridPreviewOffsetX == other.gridPreviewOffsetX &&
            gridPreviewOffsetY == other.gridPreviewOffsetY &&
            gridPreviewFitMode == other.gridPreviewFitMode &&
            gridPreviewBackground == other.gridPreviewBackground &&
            hasHiddenLayers == other.hasHiddenLayers &&
            hiddenLayerCount == other.hiddenLayerCount &&
            locationLatitude == other.locationLatitude &&
            locationLongitude == other.locationLongitude &&
            Arrays.equals(reactionsData, other.reactionsData) &&
            Arrays.equals(mediaItemsData, other.mediaItemsData) &&
            Arrays.equals(taggedUsersData, other.taggedUsersData) &&
            Arrays.equals(mentionedUsersData, other.mentionedUsersData) &&
            lastSyncedAt == other.lastSyncedAt &&
            feedSection == other.feedSection
    }

    override fun hashCode(): Int {
        var result = momentId.hashCode()
        result = 31 * result + authorId.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (imagePath?.hashCode() ?: 0)
        result = 31 * result + (videoUrl?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (commentCount ?: 0)
        result = 31 * result + (profileImagePath?.hashCode() ?: 0)
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + (audience?.hashCode() ?: 0)
        result = 31 * result + (aspectRatio?.hashCode() ?: 0)
        result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
        result = 31 * result + (videoDuration?.hashCode() ?: 0)
        result = 31 * result + (videoFileSize?.hashCode() ?: 0)
        result = 31 * result + (videoResolution?.hashCode() ?: 0)
        result = 31 * result + (customListId?.hashCode() ?: 0)
        result = 31 * result + (disableComments?.hashCode() ?: 0)
        result = 31 * result + (hideLikeCounts?.hashCode() ?: 0)
        result = 31 * result + (allowSharing?.hashCode() ?: 0)
        result = 31 * result + (scheduledDate?.hashCode() ?: 0)
        result = 31 * result + (isPinned?.hashCode() ?: 0)
        result = 31 * result + (pinnedAt?.hashCode() ?: 0)
        result = 31 * result + (gridPreviewScale?.hashCode() ?: 0)
        result = 31 * result + (gridPreviewOffsetX?.hashCode() ?: 0)
        result = 31 * result + (gridPreviewOffsetY?.hashCode() ?: 0)
        result = 31 * result + (gridPreviewFitMode?.hashCode() ?: 0)
        result = 31 * result + (gridPreviewBackground?.hashCode() ?: 0)
        result = 31 * result + (hasHiddenLayers?.hashCode() ?: 0)
        result = 31 * result + (hiddenLayerCount ?: 0)
        result = 31 * result + (locationLatitude?.hashCode() ?: 0)
        result = 31 * result + (locationLongitude?.hashCode() ?: 0)
        result = 31 * result + (reactionsData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (mediaItemsData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (taggedUsersData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (mentionedUsersData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + lastSyncedAt.hashCode()
        result = 31 * result + feedSection.hashCode()
        return result
    }

    companion object {
        /** ≡ iOS `from(_:section:)`. */
        fun from(moment: Moment, section: String = "feed"): CachedMoment = CachedMoment(
            momentId = moment.id ?: UUID.randomUUID().toString(),
            authorId = moment.authorId,
            username = moment.username,
            content = moment.content,
            imagePath = moment.imagePath,
            videoUrl = moment.videoUrl,
            timestamp = moment.timestamp,
            commentCount = moment.commentCount,
            profileImagePath = moment.profileImagePath,
            location = moment.location,
            audience = moment.audience,
            aspectRatio = moment.aspectRatio,
            thumbnailUrl = moment.thumbnailUrl,
            videoDuration = moment.videoDuration,
            videoFileSize = moment.videoFileSize,
            videoResolution = moment.videoResolution,
            customListId = moment.customListId,
            disableComments = moment.disableComments,
            hideLikeCounts = moment.hideLikeCounts,
            allowSharing = moment.allowSharing,
            scheduledDate = moment.scheduledDate,
            isPinned = moment.isPinned,
            pinnedAt = moment.pinnedAt,
            gridPreviewScale = moment.gridPreviewScale,
            gridPreviewOffsetX = moment.gridPreviewOffsetX,
            gridPreviewOffsetY = moment.gridPreviewOffsetY,
            gridPreviewFitMode = moment.gridPreviewFitMode,
            gridPreviewBackground = moment.gridPreviewBackground,
            hasHiddenLayers = moment.hasHiddenLayers,
            hiddenLayerCount = moment.hiddenLayerCount,
            locationLatitude = moment.locationCoordinate?.latitude,
            locationLongitude = moment.locationCoordinate?.longitude,
            reactionsData = encodeReactions(moment.reactions),
            mediaItemsData = encodeMediaItems(moment.mediaItems),
            taggedUsersData = encodeStringList(moment.taggedUsers),
            mentionedUsersData = encodeStringList(moment.mentionedUsers),
            lastSyncedAt = Date(),
            feedSection = section,
        )

        /** Usado por LPS al mutar reacciones en caché. */
        fun encodeReactions(reactions: Map<String, List<String>>): ByteArray =
            JSONObject(reactions.mapValues { (_, v) -> JSONArray(v) }).toString().toByteArray()

        fun decodeReactions(data: ByteArray?): Map<String, List<String>> {
            if (data == null) return emptyMap()
            return runCatching {
                val obj = JSONObject(String(data))
                obj.keys().asSequence().associateWith { key ->
                    obj.getJSONArray(key).toStringList()
                }
            }.getOrDefault(emptyMap())
        }

        private fun encodeMediaItems(items: List<MediaItem>?): ByteArray? {
            if (items == null) return null
            return runCatching {
                JSONArray().apply {
                    items.forEach { item ->
                        put(JSONObject(item.toMap().mapValues { (_, v) -> jsonSafe(v) }))
                    }
                }.toString().toByteArray()
            }.getOrNull()
        }

        private fun decodeMediaItems(data: ByteArray?): List<MediaItem>? {
            if (data == null) return null
            return runCatching {
                val arr = JSONArray(String(data))
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.toAnyMap()?.let(MediaItem::from)
                }
            }.getOrNull()
        }

        private fun encodeStringList(list: List<String>?): ByteArray? =
            list?.let { JSONArray(it).toString().toByteArray() }

        private fun decodeStringList(data: ByteArray?): List<String>? {
            if (data == null) return null
            return runCatching { JSONArray(String(data)).toStringList() }.getOrNull()
        }

        /** Timestamp/Date → millis; Map/List anidados → JSON. */
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
                // Firebase Timestamp u otros: toDate()/seconds si existen
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
