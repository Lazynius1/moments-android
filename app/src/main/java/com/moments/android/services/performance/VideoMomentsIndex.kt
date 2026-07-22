package com.moments.android.services.performance

import com.moments.android.models.Moment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Momento con vídeo para Reels (espejo de VideoMoment en Reels.swift). */
data class VideoMoment(
    val moment: Moment,
    val mediaIndex: Int = 0,
) {
    val id: String get() = moment.id ?: "${moment.hashCode()}_$mediaIndex"
}

/**
 * Port de VideoMomentsIndex.swift.
 * Índice ligero de momentos con vídeo para Reels.
 */
object VideoMomentsIndex {
    private val _videoMoments = MutableStateFlow<List<VideoMoment>>(emptyList())
    val videoMoments: StateFlow<List<VideoMoment>> = _videoMoments.asStateFlow()

    fun rebuild(from: List<Moment>) {
        _videoMoments.value = from.flatMap { moment ->
            val items = moment.mediaItems
            if (items.isNullOrEmpty()) {
                // Legacy: videoUrl en el moment
                if (!moment.videoUrl.isNullOrBlank()) listOf(VideoMoment(moment, 0)) else emptyList()
            } else {
                items.mapIndexedNotNull { index, item ->
                    if (item.type == com.moments.android.models.MediaItem.MediaType.VIDEO) {
                        VideoMoment(moment, index)
                    } else null
                }
            }
        }
    }

    fun reelsStartIndex(momentId: String?): Int {
        if (momentId == null) return 0
        return _videoMoments.value.indexOfFirst { it.moment.id == momentId }.coerceAtLeast(0)
    }
}
