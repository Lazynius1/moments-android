package com.moments.android.services.video

import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.VideoPlaybackTier
import com.moments.android.models.VideoVariants
import com.moments.android.services.network.NetworkMonitor
import java.net.URI

/** Port de VideoPlaybackSelector.swift — HLS master si existe; si no, ABR manual MP4. */
data class VideoPlaybackSource(
    val playbackUrl: String,
    val tier: VideoPlaybackTier?,
    val preheatUrlStrings: List<String>,
    val isHls: Boolean = false,
    val fallbackMp4Url: String? = null,
)

object VideoPlaybackSelector {

    fun source(forItem: MediaItem, moment: Moment? = null): VideoPlaybackSource? {
        if (forItem.type != MediaItem.MediaType.VIDEO) return null
        val fallbackUrl = resolvedFallbackUrl(forItem, moment) ?: return null
        val tier = recommendedTier()
        val tierUrlString = forItem.videoVariants?.url(tier) ?: fallbackUrl
        val mp4Url = normalizedUrlString(tierUrlString) ?: tierUrlString

        val hlsRaw = forItem.hlsMasterUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (hlsRaw != null) {
            val hlsUrl = normalizedUrlString(hlsRaw) ?: hlsRaw
            return VideoPlaybackSource(
                playbackUrl = hlsUrl,
                tier = tier,
                // Prefetch del master: ExoPlayer solo baja los primeros segmentos.
                preheatUrlStrings = listOf(hlsUrl),
                isHls = true,
                fallbackMp4Url = mp4Url,
            )
        }

        return VideoPlaybackSource(
            playbackUrl = mp4Url,
            tier = tier,
            preheatUrlStrings = preheatStrings(forItem.videoVariants, tierUrlString, tier),
            isHls = false,
            fallbackMp4Url = null,
        )
    }

    fun source(forMoment: Moment): VideoPlaybackSource? {
        val item = forMoment.primaryVisibleMediaItem
        if (item != null && item.type == MediaItem.MediaType.VIDEO) {
            return source(forItem = item, moment = forMoment)
        }
        if (!forMoment.shouldUseLegacyMediaFallback) return null
        val videoUrl = forMoment.videoUrl ?: return null
        val url = normalizedUrlString(videoUrl) ?: videoUrl
        return VideoPlaybackSource(
            playbackUrl = url,
            tier = recommendedTier(),
            preheatUrlStrings = listOf(videoUrl),
        )
    }

    fun posterUrlString(forItem: MediaItem, moment: Moment?): String? {
        forItem.thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        moment?.thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        moment?.imagePath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    fun isHlsUrl(raw: String): Boolean = raw.lowercase().contains(".m3u8")

    /** MP4 / HLS / contenedores de vídeo: no van a Coil. */
    fun isLikelyVideoUrl(raw: String): Boolean {
        val s = raw.lowercase()
        return isHlsUrl(s) ||
            s.contains(".mp4") ||
            s.contains(".mov") ||
            s.contains(".webm") ||
            s.contains(".m4v")
    }

    /** Prefetch de feed: foto = URL de imagen; vídeo = póster. Nunca `.mp4` / `.m3u8`. */
    fun imagePrefetchUrlStrings(from: List<Moment>, maxMoments: Int = 8): List<String> {
        val collected = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (moment in from.take(maxMoments)) {
            val item = moment.primaryVisibleMediaItem
            val candidates: List<String?> = when {
                item == null -> listOf(moment.imagePath, moment.thumbnailUrl)
                item.type == MediaItem.MediaType.IMAGE -> listOf(item.url, moment.imagePath, moment.thumbnailUrl)
                else -> listOf(
                    posterUrlString(forItem = item, moment = moment),
                    moment.imagePath,
                    moment.thumbnailUrl,
                )
            }
            for (raw in candidates) {
                val normalized = normalizedUrlString(raw) ?: continue
                if (isLikelyVideoUrl(normalized)) continue
                if (!seen.add(normalized)) continue
                collected.add(normalized)
                break
            }
        }
        return collected
    }

    fun preloadUrlStrings(from: List<Moment>, maxMoments: Int = 6): List<String> {
        val collected = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (moment in from.take(maxMoments)) {
            val item = moment.primaryVisibleMediaItem ?: continue
            if (item.type != MediaItem.MediaType.VIDEO) continue
            val src = source(forItem = item, moment = moment) ?: continue
            for (url in src.preheatUrlStrings) {
                if (seen.add(url)) collected.add(url)
            }
        }
        return collected
    }

    fun recommendedTier(): VideoPlaybackTier {
        val monitor = NetworkMonitor
        if (!monitor.isConnected || monitor.shouldUseOfflineMode) return VideoPlaybackTier.LOW
        if (monitor.isConstrained || monitor.isExpensive) return VideoPlaybackTier.LOW
        return when (monitor.connectionType) {
            NetworkMonitor.ConnectionType.WIFI,
            NetworkMonitor.ConnectionType.ETHERNET,
            -> VideoPlaybackTier.HIGH
            NetworkMonitor.ConnectionType.CELLULAR,
            NetworkMonitor.ConnectionType.UNKNOWN,
            -> VideoPlaybackTier.MEDIUM
        }
    }

    fun normalizedUrlString(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (runCatching { URI(trimmed).toURL() }.isSuccess) return trimmed
        val encoded = trimmed.replace(" ", "%20")
        return if (runCatching { URI(encoded).toURL() }.isSuccess) encoded else null
    }

    private fun resolvedFallbackUrl(item: MediaItem, moment: Moment?): String? {
        val raw = item.url.trim()
        normalizedUrlString(raw)?.let { return it }
        if (moment != null && moment.shouldUseLegacyMediaFallback) {
            val legacy = moment.videoUrl ?: return null
            return normalizedUrlString(legacy) ?: legacy
        }
        return null
    }

    private fun preheatStrings(
        variants: VideoVariants?,
        primary: String,
        tier: VideoPlaybackTier,
    ): List<String> {
        val urls = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fun append(value: String?) {
            val n = normalizedUrlString(value) ?: return
            if (seen.add(n)) urls.add(n)
        }
        // Siempre el tier elegido; low solo como fallback (nunca high si el tier es medium/low).
        append(primary)
        if (variants != null && tier != VideoPlaybackTier.LOW) {
            append(variants.url(VideoPlaybackTier.LOW))
        }
        return urls
    }
}

fun Moment.videoPlaybackSource(): VideoPlaybackSource? = VideoPlaybackSelector.source(forMoment = this)
