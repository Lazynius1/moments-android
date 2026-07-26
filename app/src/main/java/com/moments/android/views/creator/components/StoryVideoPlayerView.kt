package com.moments.android.views.creator.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Equivalente Android de `AVLayerVideoGravity` para `StoryVideoPlayerView`. */
enum class StoryVideoGravity {
    RESIZE_ASPECT,
    RESIZE_ASPECT_FILL,
}

/**
 * Port de `StoryVideoPlayerView.swift` para el canvas del creador.
 *
 * Playhead cada 50 ms; loop a `trimStart` al acabar o al cruzar `trimEnd`.
 *
 * Params extra (`volume`, `playbackSpeed`, `isPlaying`) no están en este Swift —
 * los usa VideoEditor/Echo en Android donde iOS tiene AVPlayer propio.
 */
@Composable
fun StoryVideoPlayerView(
    videoUri: Uri,
    videoGravity: StoryVideoGravity = StoryVideoGravity.RESIZE_ASPECT,
    isMuted: Boolean = false,
    trimStart: Double = 0.0,
    trimEnd: Double = 0.0,
    previewTime: Double? = null,
    onPlayProgress: ((Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Android-only (callers fuera de este archivo Swift)
    volume: Float? = null,
    playbackSpeed: Float = 1f,
    isPlaying: Boolean? = null,
) {
    val context = LocalContext.current
    val latestProgress by rememberUpdatedState(onPlayProgress)
    val latestTrimStart by rememberUpdatedState(trimStart)
    val player = remember(videoUri) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    var isScrubbing by remember(videoUri) { mutableStateOf(false) }

    DisposableEffect(player) {
        val completionListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo((latestTrimStart.coerceAtLeast(0.0) * 1_000).toLong())
                    player.play()
                }
            }
        }
        player.addListener(completionListener)
        onDispose {
            player.removeListener(completionListener)
            player.pause()
            player.release()
        }
    }

    LaunchedEffect(player, isMuted, volume) {
        player.volume = (volume ?: if (isMuted) 0f else 1f).coerceIn(0f, 1f)
    }

    LaunchedEffect(player, playbackSpeed) {
        if (playbackSpeed != 1f) {
            player.playbackParameters = PlaybackParameters(playbackSpeed.coerceIn(0.1f, 4f))
        }
    }

    LaunchedEffect(player, isPlaying) {
        when (isPlaying) {
            true -> player.play()
            false -> player.pause()
            null -> Unit
        }
    }

    // ≡ iOS NotificationCenter `CleanupVideoPlayer`
    LaunchedEffect(player) {
        NavigationEventBus.events.collect { event ->
            if (event is CoordinatorNavigationEvent.CleanupVideoPlayer) {
                player.pause()
                player.clearMediaItems()
            }
        }
    }

    // ≡ scrubbing seek vs normal playback
    LaunchedEffect(player, previewTime, trimStart) {
        if (previewTime != null) {
            if (!isScrubbing) {
                isScrubbing = true
                player.pause()
            }
            player.seekTo((previewTime.coerceAtLeast(0.0) * 1_000).toLong())
        } else if (isScrubbing) {
            isScrubbing = false
            player.seekTo((trimStart.coerceAtLeast(0.0) * 1_000).toLong())
            player.play()
        }
    }

    LaunchedEffect(player, trimStart, trimEnd) {
        while (isActive) {
            val currentSeconds = player.currentPosition / 1_000.0
            latestProgress?.invoke(currentSeconds)
            if (
                !isScrubbing &&
                trimEnd > 0.0 &&
                (currentSeconds >= trimEnd || currentSeconds < trimStart - 0.2)
            ) {
                player.seekTo((trimStart.coerceAtLeast(0.0) * 1_000).toLong())
                player.play()
            }
            delay(50)
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                setKeepContentOnPlayerReset(true)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = when (videoGravity) {
                StoryVideoGravity.RESIZE_ASPECT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                StoryVideoGravity.RESIZE_ASPECT_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier,
    )
}

/** Port de `VideoControlsOverlay` — UI stub como en iOS (no cablea el player). */
@Composable
fun VideoControlsOverlay(modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3_000)
            showControls = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .clickable { showControls = !showControls }
            .padding(16.dp),
    ) {
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f)),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { /* iOS: action vacía */ }) {
                    Icon(
                        Icons.Filled.Replay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
