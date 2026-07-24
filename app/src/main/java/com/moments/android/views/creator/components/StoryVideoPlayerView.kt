package com.moments.android.views.creator.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Equivalente Android de AVLayerVideoGravity para StoryVideoPlayerView. */
enum class StoryVideoGravity {
    RESIZE_ASPECT,
    RESIZE_ASPECT_FILL,
}

/**
 * Port de StoryVideoPlayerView.swift para el canvas del creador.
 *
 * Mantiene un ExoPlayer privado por preview, informa el playhead cada 50 ms y
 * repite desde trimStart tanto al acabar el vídeo como al cruzar trimEnd.
 */
@Composable
fun StoryVideoPlayerView(
    videoUri: Uri,
    videoGravity: StoryVideoGravity = StoryVideoGravity.RESIZE_ASPECT,
    isMuted: Boolean = false,
    volume: Float? = null,
    playbackSpeed: Float = 1f,
    isPlaying: Boolean? = null,
    trimStart: Double = 0.0,
    trimEnd: Double = 0.0,
    previewTime: Double? = null,
    onPlayProgress: ((Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
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
        player.playbackParameters = PlaybackParameters(playbackSpeed.coerceIn(0.1f, 4f))
    }

    LaunchedEffect(player, isPlaying) {
        when (isPlaying) {
            true -> player.play()
            false -> player.pause()
            null -> Unit
        }
    }

    LaunchedEffect(player, previewTime, trimStart) {
        if (previewTime != null) {
            isScrubbing = true
            player.pause()
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
