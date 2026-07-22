package com.moments.android.views.feed.video

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moments.android.R
import com.moments.android.services.video.SharedVideoPlayerPool
import com.moments.android.views.feed.FeedTeal

/**
 * Port del player usado por `CroppedVideoPlayer` (FeedMomentComponents.swift) con
 * `chromeStyle: .socialReels` (VideoPlaybackChromeStyle.swift):
 * - Sin barra de progreso
 * - Tap = pausa solo si `allowsPauseInteraction` (reels pasa `false` + overlay propio)
 * - Mute persistente lo añade `CroppedVideoPlayer` en bottomLeading (aquí, si `showMute`)
 *
 * `allowsPlayback=false` → póster (`CroppedVideoPlayer` cuando `!allowsVideoPlayback`).
 */
@Composable
fun FeedVideoPage(
    url: String,
    thumbnailUrl: String?,
    consumerId: String,
    modifier: Modifier = Modifier,
    allowsPlayback: Boolean = true,
    /** iOS ModernVideoPlayer.allowsPauseInteraction — false en reels del feed. */
    allowsPauseInteraction: Boolean = true,
    /** iOS CroppedVideoPlayer muteToggleButton (siempre si allowsVideoPlayback). */
    showMute: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var isPlaying by remember(consumerId) { mutableStateOf(false) }
    var isMuted by remember(consumerId) { mutableStateOf(true) }
    var isLoading by remember(consumerId) { mutableStateOf(true) }
    var hasError by remember(consumerId) { mutableStateOf(false) }
    var isReadyToPlay by remember(consumerId) { mutableStateOf(false) }

    // iOS CroppedVideoPlayer: if !allowsVideoPlayback { videoPosterFallback }
    if (!allowsPlayback) {
        Box(modifier.fillMaxSize()) {
            VideoPosterOverlay(
                posterUrl = thumbnailUrl,
                isReadyToPlay = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    DisposableEffect(consumerId, url) {
        SharedVideoPlayerPool.initialize(context)
        val player = SharedVideoPlayerPool.player(consumerId)
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = if (isMuted) 0f else 1f
        player.prepare()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    hasError = false
                    isReadyToPlay = true
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            SharedVideoPlayerPool.release(consumerId)
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    player = SharedVideoPlayerPool.player(consumerId)
                }
            },
            modifier = Modifier.fillMaxSize().clickable {
                // iOS: si allowsPauseInteraction → handleTap; si no, el overlay de CroppedVideoPlayer usa onTap
                when {
                    onTap != null && !allowsPauseInteraction -> onTap()
                    allowsPauseInteraction -> {
                        val player = SharedVideoPlayerPool.player(consumerId)
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    onTap != null -> onTap()
                }
            },
        )
        VideoPosterOverlay(
            posterUrl = thumbnailUrl,
            isReadyToPlay = isReadyToPlay && isPlaying,
            modifier = Modifier.fillMaxSize(),
        )
        // socialReels: mute+play pequeños solo cuando está pausado Y allowsPauseInteraction
        // + mute persistente de CroppedVideoPlayer (showMute)
        VideoPlaybackChrome(
            isPlaying = isPlaying,
            isMuted = isMuted,
            isLoading = isLoading,
            hasError = hasError,
            showPausedControls = allowsPauseInteraction && !isPlaying && !isLoading && !hasError,
            showMute = showMute,
            onToggleMute = {
                isMuted = !isMuted
                SharedVideoPlayerPool.player(consumerId).volume = if (isMuted) 0f else 1f
            },
            onRetry = {
                hasError = false
                isReadyToPlay = false
                SharedVideoPlayerPool.player(consumerId).prepare()
                SharedVideoPlayerPool.player(consumerId).play()
            },
            onTogglePlay = {
                val player = SharedVideoPlayerPool.player(consumerId)
                if (player.isPlaying) player.pause() else player.play()
            },
        )
        // socialReels: sin VideoFeedProgressBar (solo .classic)
    }
}

@Composable
fun VideoPlaybackChrome(
    isPlaying: Boolean,
    isMuted: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    onToggleMute: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showMute: Boolean = true,
    showPausedControls: Boolean = true,
    onTogglePlay: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(color = FeedTeal, modifier = Modifier.align(Alignment.Center))
        }
        if (hasError) {
            Text(
                stringResource(R.string.feed_video_load_error),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable(onClick = onRetry),
            )
        }
        // iOS SocialVideoPausedControls — solo si socialReels + paused + allowsPauseInteraction
        if (showPausedControls) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(VideoPlaybackChromeStyle.playButtonSize)
                    .clip(CircleShape)
                    .background(VideoPlaybackChromeStyle.playButtonBackground)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.feed_video_play),
                    tint = Color.White,
                )
            }
        }
        // iOS CroppedVideoPlayer.muteToggleButton — .overlay(alignment: .bottomLeading)
        if (showMute) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp)
                    .size(VideoPlaybackChromeStyle.muteButtonSize)
                    .clip(CircleShape)
                    .background(VideoPlaybackChromeStyle.muteButtonBackground)
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = stringResource(if (isMuted) R.string.feed_video_mute else R.string.feed_video_unmute),
                    tint = Color.White,
                    modifier = Modifier.size(VideoPlaybackChromeStyle.muteIconSize),
                )
            }
        }
    }
}
