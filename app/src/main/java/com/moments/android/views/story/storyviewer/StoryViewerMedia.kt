package com.moments.android.views.story.storyviewer

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.utilities.MomentsAudioSession
import kotlinx.coroutines.delay

/** Equivalente de `StoryAudioSession` en `StoryViewerMedia.swift`. */
private object StoryAudioSession {
    fun initialize(context: android.content.Context) = MomentsAudioSession.initialize(context)

    suspend fun activate() {
        MomentsAudioSession.activate()
    }

    fun deactivate() = MomentsAudioSession.deactivate()
}

/** Port de `GlassmorphicStoryVideoPlayer`: reproduce, informa de progreso y libera recursos al salir. */
@Composable
fun GlassmorphicStoryVideoPlayer(
    url: String,
    isPlaying: Boolean,
    onReadyToPlayChanged: (Boolean) -> Unit,
    isMutedExternally: Boolean,
    shouldLoop: Boolean,
    onProgressUpdate: (Float) -> Unit,
    onVideoComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = androidx.compose.runtime.remember(url, shouldLoop) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.parse(url)))
            repeatMode = if (shouldLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player) {
        StoryAudioSession.initialize(context)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                onReadyToPlayChanged(playbackState == Player.STATE_READY)
                if (playbackState == Player.STATE_ENDED && !shouldLoop) {
                    onProgressUpdate(0f)
                    onVideoComplete()
                }
            }
        }
        player.addListener(listener)
        onReadyToPlayChanged(player.playbackState == Player.STATE_READY)
        onDispose {
            player.removeListener(listener)
            player.pause()
            player.volume = 0f
            player.release()
            StoryAudioSession.deactivate()
        }
    }

    LaunchedEffect(player, isPlaying, isMutedExternally) {
        player.volume = if (isMutedExternally || !isPlaying) 0f else 1f
        if (isPlaying) {
            StoryAudioSession.activate()
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            if (duration > 0) {
                onProgressUpdate((player.currentPosition.toFloat() / duration).coerceIn(0f, 1f))
            }
            delay(100)
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun StoryViewerMedia(
    story: Story,
    isPaused: Boolean = false,
    onVideoProgress: (Float) -> Unit = {},
    onVideoComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val media = story.mediaItem
    when (media.type) {
        MediaItem.MediaType.VIDEO -> GlassmorphicStoryVideoPlayer(
            url = media.url,
            isPlaying = !isPaused,
            onReadyToPlayChanged = {},
            isMutedExternally = false,
            shouldLoop = false,
            onProgressUpdate = onVideoProgress,
            onVideoComplete = onVideoComplete,
            modifier = modifier,
        )

        else -> {
            val url = media.url.ifBlank { media.thumbnailUrl.orEmpty() }
            Box(modifier.background(Color.Black)) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(20.dp),
                )
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
