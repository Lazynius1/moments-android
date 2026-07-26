package com.moments.android.services.performance

import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Momento con vídeo para Reels.
 * iOS: `VideoMoment` en Reels.swift (un entry por moment con vídeo).
 * `mediaIndex` ayuda al caller Android a resolver el item de carrusel.
 */
data class VideoMoment(
    val moment: Moment,
    val mediaIndex: Int = 0,
) {
    val id: String get() = moment.id ?: "${moment.hashCode()}_$mediaIndex"
    val videoUrl: String
        get() = moment.previewVideoURLString
            ?: moment.mediaItems?.getOrNull(mediaIndex)?.url
            ?: moment.videoUrl
            ?: ""
}

/**
 * Port de `VideoMomentsIndex.swift`.
 * Índice ligero de momentos con vídeo para Reels sin pasar el feed completo.
 */
object VideoMomentsIndex {
    private val _videoMoments = MutableStateFlow<List<VideoMoment>>(emptyList())
    val videoMoments: StateFlow<List<VideoMoment>> = _videoMoments.asStateFlow()

    /** Port de `moments.videoMoments` (Reels.swift) → un VideoMoment por moment. */
    fun rebuild(from: List<Moment>) {
        _videoMoments.value = from.mapNotNull { moment ->
            val url = moment.previewVideoURLString ?: moment.videoUrl
            if (url.isNullOrBlank()) return@mapNotNull null
            val mediaIndex = moment.mediaItems
                ?.indexOfFirst { it.type == MediaItem.MediaType.VIDEO }
                ?.takeIf { it >= 0 }
                ?: 0
            VideoMoment(moment, mediaIndex)
        }
    }

    fun reelsStartIndex(momentId: String?): Int {
        if (momentId == null) return 0
        val idx = _videoMoments.value.indexOfFirst { it.moment.id == momentId }
        return if (idx >= 0) idx else 0
    }
}
