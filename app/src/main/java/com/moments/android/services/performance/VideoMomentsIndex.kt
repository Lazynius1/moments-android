package com.moments.android.services.performance

import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.services.video.videoPlaybackSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Port de `VideoMoment` (Reels.swift).
 * `mediaIndex` ayuda al caller Android a resolver el item de carrusel.
 */
data class VideoMoment(
    val moment: Moment,
    val mediaIndex: Int = 0,
) {
    val id: String get() = moment.id ?: "${moment.hashCode()}_$mediaIndex"

    /** ≡ iOS `videoUrl` resuelto en init. */
    val videoUrl: String
        get() = moment.previewVideoURLString
            ?: moment.mediaItems?.getOrNull(mediaIndex)?.url
            ?: moment.videoUrl
            ?: ""

    /** ≡ iOS `playbackURL`. */
    val playbackUrl: String?
        get() = moment.videoPlaybackSource()?.playbackUrl
            ?: videoUrl.takeIf { it.isNotBlank() }

    /** ≡ iOS `preloadURLStrings`. */
    val preloadUrlStrings: List<String>
        get() {
            val fromSource = moment.videoPlaybackSource()?.preheatUrlStrings.orEmpty()
            if (fromSource.isNotEmpty()) return fromSource
            return if (videoUrl.isEmpty()) emptyList() else listOf(videoUrl)
        }
}

/**
 * Port de `VideoMomentsIndex.swift` + `Array.videoMoments` (Reels.swift).
 */
object VideoMomentsIndex {
    private val _videoMoments = MutableStateFlow<List<VideoMoment>>(emptyList())
    val videoMoments: StateFlow<List<VideoMoment>> = _videoMoments.asStateFlow()

    /** Port de `moments.videoMoments` → un VideoMoment por moment con vídeo. */
    fun rebuild(from: List<Moment>) {
        _videoMoments.value = from.toVideoMoments()
    }

    fun reelsStartIndex(momentId: String?): Int {
        if (momentId == null) return 0
        val idx = _videoMoments.value.indexOfFirst { it.moment.id == momentId }
        return if (idx >= 0) idx else 0
    }
}

/** ≡ iOS `Array where Element == Moment { var videoMoments }`. */
fun List<Moment>.toVideoMoments(): List<VideoMoment> = mapNotNull { moment ->
    val url = moment.previewVideoURLString ?: moment.videoUrl
    if (url.isNullOrBlank()) return@mapNotNull null
    val mediaIndex = moment.mediaItems
        ?.indexOfFirst { it.type == MediaItem.MediaType.VIDEO }
        ?.takeIf { it >= 0 }
        ?: 0
    VideoMoment(moment, mediaIndex)
}
