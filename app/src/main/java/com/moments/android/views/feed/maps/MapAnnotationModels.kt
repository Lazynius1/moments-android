package com.moments.android.views.feed.maps

import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import java.util.UUID

/** Port de `MapAnnotationModels.swift`. */
data class MapsLocationAnnotation(
    val id: UUID = UUID.randomUUID(),
    val latitude: Double,
    val longitude: Double,
    val title: String,
)

/** ≡ iOS `Moment` helpers en `MapAnnotationModels.swift`. */
val Moment.mapAvailabilityKey: String
    get() {
        val trimmedId = id?.trim().orEmpty()
        if (trimmedId.isNotEmpty()) return trimmedId
        return "$authorId|${timestamp.time / 1000}|$content"
    }

val Moment.mapHasVideoMedia: Boolean
    get() {
        if (mediaItems?.any { it.type == MediaItem.MediaType.VIDEO } == true) return true
        val url = videoUrl?.trim().orEmpty()
        return url.isNotEmpty()
    }

val Moment.mapHasRenderableMedia: Boolean
    get() {
        if (mediaItems?.any { it.url.trim().isNotEmpty() } == true) return true
        if (imagePath?.trim().orEmpty().isNotEmpty()) return true
        if (videoUrl?.trim().orEmpty().isNotEmpty()) return true
        return false
    }

/** ≡ iOS `mapPreferredImageURL`. */
val Moment.mapPreferredImageUrl: String?
    get() {
        mediaItems?.let { items ->
            items.firstOrNull { it.type == MediaItem.MediaType.IMAGE }?.let { firstImage ->
                val imageURL = firstImage.url.trim()
                if (imageURL.isNotEmpty()) return imageURL
            }
            items.firstOrNull { it.type == MediaItem.MediaType.VIDEO }?.let { firstVideo ->
                val thumb = firstVideo.thumbnailUrl?.trim().orEmpty()
                if (thumb.isNotEmpty()) return thumb
                val fallback = firstVideo.url.trim()
                if (fallback.isNotEmpty()) return fallback
            }
        }
        thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        imagePath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

/** ≡ iOS `mapPreferredVideoThumbnailURL`. */
val Moment.mapPreferredVideoThumbnailUrl: String?
    get() {
        mediaItems?.firstOrNull { it.type == MediaItem.MediaType.VIDEO }?.let { firstVideo ->
            val thumb = firstVideo.thumbnailUrl?.trim().orEmpty()
            if (thumb.isNotEmpty()) return thumb
            val fallback = firstVideo.url.trim()
            if (fallback.isNotEmpty()) return fallback
        }
        thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        imagePath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

/** ≡ iOS `CombinedMapAnnotation`. */
data class CombinedMapAnnotation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val locationTitle: String?,
    val moment: Moment? = null,
    val moments: List<Moment> = emptyList(),
) {
    val primaryMoment: Moment?
        get() = moment ?: moments.firstOrNull()

    val count: Int
        get() = when {
            moments.isNotEmpty() -> moments.size
            moment == null -> 0
            else -> 1
        }
}
