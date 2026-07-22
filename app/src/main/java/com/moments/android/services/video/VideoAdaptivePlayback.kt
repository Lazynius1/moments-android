package com.moments.android.services.video

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.moments.android.models.MediaItem as MomentMediaItem
import com.moments.android.models.Moment
import com.moments.android.models.VideoPlaybackTier
import com.moments.android.services.cache.VideoPreloader

/** Port de VideoAdaptivePlayback.swift — bitrate / stall downgrade. */
object VideoAdaptivePlaybackConfig {
    /** Paridad iOS `preferredForwardBufferDuration = 2.5` (segundos). */
    const val PREFERRED_FORWARD_BUFFER_MS = 2_500

    fun peakBitRate(tier: VideoPlaybackTier): Double = when (tier) {
        VideoPlaybackTier.LOW -> 800_000.0
        VideoPlaybackTier.MEDIUM -> 2_500_000.0
        VideoPlaybackTier.HIGH -> 5_000_000.0
    }
}

fun VideoPlaybackSelector.tierBelow(tier: VideoPlaybackTier): VideoPlaybackTier? = when (tier) {
    VideoPlaybackTier.HIGH -> VideoPlaybackTier.MEDIUM
    VideoPlaybackTier.MEDIUM -> VideoPlaybackTier.LOW
    VideoPlaybackTier.LOW -> null
}

fun VideoPlaybackSelector.playbackUrl(
    item: MomentMediaItem,
    moment: Moment?,
    tier: VideoPlaybackTier,
): String? {
    if (item.type != MomentMediaItem.MediaType.VIDEO) return null
    val fallback = source(forItem = item, moment = moment)?.playbackUrl
    val tierString = item.videoVariants?.url(tier)
        ?: fallback
        ?: item.url
    return normalizedUrlString(tierString) ?: tierString
}

/**
 * Ajustes equivalentes a `configure(playerItem:tier:)` de iOS.
 * En Media3 se aplica peak bitrate vía TrackSelector cuando está disponible.
 */
fun VideoPlaybackSelector.configure(
    player: ExoPlayer,
    tier: VideoPlaybackTier,
    @Suppress("UNUSED_PARAMETER") isActivelyPlaying: Boolean = true,
) {
    val maxBitrate = VideoAdaptivePlaybackConfig.peakBitRate(tier).toInt()
    val selector = player.trackSelector as? DefaultTrackSelector ?: return
    selector.setParameters(
        selector.buildUponParameters()
            .setMaxVideoBitrate(maxBitrate)
            .build(),
    )
}

/** LoadControl con buffer forward ~2.5s (usar al construir ExoPlayer). */
fun VideoPlaybackSelector.createAdaptiveLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            VideoAdaptivePlaybackConfig.PREFERRED_FORWARD_BUFFER_MS,
            15_000,
            1_000,
            VideoAdaptivePlaybackConfig.PREFERRED_FORWARD_BUFFER_MS,
        )
        .build()

fun VideoPlaybackSelector.makeConfiguredPlayerItem(
    item: MomentMediaItem,
    moment: Moment?,
    tier: VideoPlaybackTier? = null,
): MediaItem? {
    val resolvedTier = tier ?: recommendedTier()
    val url = playbackUrl(item, moment, resolvedTier) ?: return null
    return VideoPreloader.getPlayerItem(url)
}

/** Si el buffer se vacía varias veces, baja de tier (high → medium → low). */
class VideoAdaptiveTierController(
    private val mediaItem: MomentMediaItem?,
    private val moment: Moment?,
    initialTier: VideoPlaybackTier? = null,
) {
    private val selector = VideoPlaybackSelector
    var currentTier: VideoPlaybackTier =
        initialTier ?: selector.recommendedTier()
        private set

    private var consecutiveStalls = 0
    private val stallsBeforeDowngrade = 2

    val hasVariants: Boolean
        get() {
            val variants = mediaItem?.videoVariants ?: return false
            return variants.low != null || variants.medium != null || variants.high != null
        }

    fun notePlaybackHealthy() {
        consecutiveStalls = 0
    }

    /** Registra un stall. Devuelve un nuevo MediaItem si toca bajar de calidad. */
    fun handleStall(): MediaItem? {
        val media = mediaItem ?: return null
        if (!hasVariants) return null
        consecutiveStalls += 1
        if (consecutiveStalls < stallsBeforeDowngrade) return null
        val nextTier = selector.tierBelow(currentTier) ?: return null
        val url = selector.playbackUrl(media, moment, nextTier) ?: return null
        consecutiveStalls = 0
        currentTier = nextTier
        return VideoPreloader.getPlayerItem(url)
    }
}

object VideoPlaybackRecovery {
    fun recoverFromStall(
        player: ExoPlayer,
        isPlaying: Boolean,
        adaptive: VideoAdaptiveTierController?,
        onReplaceItem: (MediaItem) -> Unit,
    ) {
        if (!isPlaying) return
        if (player.playbackState == Player.STATE_IDLE && player.playerError != null) return

        val newItem = adaptive?.handleStall()
        if (newItem != null) {
            val resumeMs = player.currentPosition
            onReplaceItem(newItem)
            player.setMediaItem(newItem)
            player.prepare()
            player.seekTo(resumeMs)
            player.play()
            return
        }
        player.play()
    }
}
