package com.moments.android.views.shared

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.services.video.buildAdaptiveExoPlayer
import com.moments.android.services.video.configure
import com.moments.android.utilities.MomentsAudioSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/** ≡ `AVLayerVideoGravity` en iOS `MomentsVideoPlayer`. */
enum class MomentsVideoGravity {
    RESIZE_ASPECT,
    RESIZE_ASPECT_FILL,
    RESIZE,
}

/**
 * Port de `MomentsVideoPlayer.swift` — player de chat / view-once / fullscreen media.
 * AVQueuePlayer + AVPlayerLooper → ExoPlayer + REPEAT_MODE; stall recovery como iOS.
 */
@Composable
fun MomentsVideoPlayer(
    url: String,
    isLooping: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    prioritizeSmoothPlayback: Boolean = false,
    showsPlaybackControls: Boolean = false,
    respectsExternalPauseState: Boolean = true,
    shouldAutoplay: Boolean = true,
    videoGravity: MomentsVideoGravity = MomentsVideoGravity.RESIZE_ASPECT,
    onDurationReceived: ((Double) -> Unit)? = null,
    onProgressUpdate: ((Double) -> Unit)? = null,
    onProgressFractionUpdate: ((Double) -> Unit)? = null,
    onVideoFinished: (() -> Unit)? = null,
    /** Segundos a seek; el caller lo pone a null tras consumir (≡ `externalSeekTime` Binding). */
    externalSeekTime: Double? = null,
    onExternalSeekConsumed: () -> Unit = {},
    onSharedPlayerChanged: ((ExoPlayer?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val latestPaused by rememberUpdatedState(isPaused)
    val latestOnDuration by rememberUpdatedState(onDurationReceived)
    val latestOnProgress by rememberUpdatedState(onProgressUpdate)
    val latestOnFraction by rememberUpdatedState(onProgressFractionUpdate)
    val latestOnFinished by rememberUpdatedState(onVideoFinished)

    var stallRetryCount by remember(url) { mutableIntStateOf(0) }
    var pendingRecovery by remember(url) { mutableStateOf(false) }

    val player = remember(url, prioritizeSmoothPlayback) {
        val player = if (prioritizeSmoothPlayback) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(8_000, 30_000, 1_500, 8_000)
                .build()
            buildAdaptiveExoPlayer(context, loadControl)
        } else {
            buildAdaptiveExoPlayer(context)
        }
        player.apply {
            repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            val mediaItem = VideoPreloader.getPlayerItem(url)
            setMediaItem(mediaItem)
            VideoPlaybackSelector.configure(
                this,
                tier = VideoPlaybackSelector.recommendedTier(),
            )
            prepare()
            volume = if (isMuted) 0f else 1f
            val autoplay = shouldAutoplay && (!respectsExternalPauseState || !isPaused)
            playWhenReady = autoplay
        }
    }

    DisposableEffect(player) {
        GlobalVideoManager.pauseAllVideos()
        onSharedPlayerChanged?.invoke(player)
        onDispose {
            onSharedPlayerChanged?.invoke(null)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        MomentsAudioSession.activate(
            usage = android.media.AudioAttributes.USAGE_MEDIA,
            contentType = android.media.AudioAttributes.CONTENT_TYPE_MOVIE,
        )
    }

    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isLooping) {
        player.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(isPaused, shouldAutoplay, respectsExternalPauseState) {
        if (respectsExternalPauseState) {
            if (isPaused) {
                if (player.isPlaying) player.pause()
            } else if (!player.isPlaying &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            ) {
                player.play()
            }
        } else if (shouldAutoplay) {
            if (!player.isPlaying &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            ) {
                player.play()
            }
        } else if (player.isPlaying) {
            player.pause()
        }
    }

    LaunchedEffect(externalSeekTime) {
        val seconds = externalSeekTime ?: return@LaunchedEffect
        player.seekTo((seconds * 1000.0).toLong().coerceAtLeast(0L))
        onExternalSeekConsumed()
    }

    DisposableEffect(player, isLooping) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val durMs = player.duration
                    if (durMs > 0) {
                        latestOnDuration?.invoke(durMs / 1000.0)
                    }
                    if (!latestPaused) {
                        player.play()
                    }
                    stallRetryCount = 0
                    pendingRecovery = false
                }
                if (playbackState == Player.STATE_ENDED && !isLooping) {
                    latestOnFinished?.invoke()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    stallRetryCount = 0
                    pendingRecovery = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Paridad iOS: log implícito; no inventar UI de error aquí.
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Progress a 10 Hz (≡ CMTime 0.1s)
    LaunchedEffect(player) {
        while (isActive) {
            val posMs = player.currentPosition.coerceAtLeast(0L)
            val durMs = player.duration
            val seconds = posMs / 1000.0
            if (seconds.isFinite()) {
                latestOnProgress?.invoke(seconds)
                if (durMs > 0) {
                    val fraction = min(max(seconds / (durMs / 1000.0), 0.0), 1.0)
                    latestOnFraction?.invoke(fraction)
                }
            }
            // Stall: buffer vacío mientras debería reproducir
            if (!latestPaused &&
                player.playbackState == Player.STATE_BUFFERING &&
                !player.isPlaying &&
                player.playerError == null
            ) {
                if (!pendingRecovery && stallRetryCount < 5) {
                    pendingRecovery = true
                    stallRetryCount += 1
                    player.pause()
                    val delayMs = min(1_250.0, 250.0 + stallRetryCount * 200.0).toLong()
                    delay(delayMs)
                    pendingRecovery = false
                    if (!latestPaused) player.play()
                } else {
                    delay(100)
                }
            } else {
                delay(100)
            }
        }
    }

    val resizeMode = when (videoGravity) {
        MomentsVideoGravity.RESIZE_ASPECT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        MomentsVideoGravity.RESIZE_ASPECT_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        MomentsVideoGravity.RESIZE -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = showsPlaybackControls
                    this.player = player
                    this.resizeMode = resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = {
                it.player = player
                it.useController = showsPlaybackControls
                it.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
