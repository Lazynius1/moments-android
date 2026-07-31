package com.moments.android.views.story.storyviewer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.utilities.MomentsAudioSession
import com.moments.android.views.creator.StoryMediaLayoutRules
import com.moments.android.views.creator.StoryMediaPresentationMode
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import com.moments.android.views.feed.video.VideoPosterOverlay
import kotlinx.coroutines.delay

/** Equivalente de `StoryAudioSession` en `StoryViewerMedia.swift`. */
private object StoryAudioSession {
    fun initialize(context: android.content.Context) = MomentsAudioSession.initialize(context)

    suspend fun activate() {
        MomentsAudioSession.activate()
    }

    fun deactivate() = MomentsAudioSession.deactivate()
}

/**
 * Port de `GlassmorphicStoryVideoPlayer` (StoryViewerMedia.swift / contentView).
 */
@Composable
fun GlassmorphicStoryVideoPlayer(
    url: String,
    isPlaying: Boolean,
    onReadyToPlayChanged: (Boolean) -> Unit,
    isMutedExternally: Boolean,
    shouldLoop: Boolean,
    onProgressUpdate: (Float) -> Unit,
    onVideoComplete: () -> Unit,
    /** ≡ iOS `isHorizontalVideo` / resizeMode según presentation. */
    contentScaleFit: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // ≡ VideoPreloader.shared.getPlayerItem(for:)
    val player = remember(url, shouldLoop) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(VideoPreloader.getPlayerItem(url))
            repeatMode = if (shouldLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player) {
        StoryAudioSession.initialize(context)
        // ≡ setupObservers: reset progreso
        onProgressUpdate(0f)
        onReadyToPlayChanged(false)
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
            // ≡ StoryAudioSession.deactivate() en deinit
            StoryAudioSession.deactivate()
        }
    }

    LaunchedEffect(player, isPlaying, isMutedExternally) {
        // ≡ isMutedExternally || !isPlaying; silenciar al pausar
        player.volume = if (isMutedExternally || !isPlaying) 0f else 1f
        if (isPlaying) {
            StoryAudioSession.activate()
            player.play()
        } else {
            player.pause()
        }
    }

    // ≡ periodicTimeObserver 0.1s
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
            // texture_view (story_player_view.xml): SurfaceView + Compose offset/clip = negro.
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.story_player_view, null, false) as PlayerView).apply {
                useController = false
                resizeMode = if (contentScaleFit) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.resizeMode = if (contentScaleFit) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            if (view.player !== player) view.player = player
        },
        modifier = modifier,
    )
}

/**
 * Port de `contentView(canvasRect:)` en `StoryViewerScreen.swift`
 * (layout rules + poster + unavailable).
 */
@Composable
fun StoryViewerMedia(
    story: Story,
    isPaused: Boolean = false,
    isMutedExternally: Boolean = false,
    onVideoProgress: (Float) -> Unit = {},
    onVideoComplete: () -> Unit = {},
    onReadyToPlayChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val media = story.mediaItem
    val corner = storyViewerCanvasCornerRadius

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val canvasW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val canvasH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val canvasAspect = canvasW / canvasH
        val mediaAspect = StoryViewerLayoutHelpers.parseAspectRatio(story.aspectRatio) ?: canvasAspect
        val presentation = StoryMediaLayoutRules.presentationMode(mediaAspect, canvasAspect)
        val imageScale = when (presentation) {
            StoryMediaPresentationMode.FILL -> ContentScale.Crop
            StoryMediaPresentationMode.FIT_WITH_BLUR -> ContentScale.Fit
        }
        val videoFit = presentation == StoryMediaPresentationMode.FIT_WITH_BLUR
        val hasUrl = media.url.isNotBlank()

        when {
            media.type == MediaItem.MediaType.VIDEO && hasUrl -> {
                var isReady by remember(story.id) { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    GlassmorphicStoryVideoPlayer(
                        url = media.url,
                        isPlaying = !isPaused,
                        onReadyToPlayChanged = {
                            isReady = it
                            onReadyToPlayChanged(it)
                        },
                        isMutedExternally = isMutedExternally,
                        shouldLoop = false,
                        onProgressUpdate = onVideoProgress,
                        onVideoComplete = onVideoComplete,
                        contentScaleFit = videoFit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    VideoPosterOverlay(
                        posterUrl = media.thumbnailUrl,
                        isReadyToPlay = isReady,
                        contentScale = imageScale,
                        cornerRadius = corner,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            media.type == MediaItem.MediaType.IMAGE && hasUrl -> {
                val url = media.url
                Box(Modifier.fillMaxSize().clip(RectangleShape)) {
                    // ≡ KFImage fill + blur(20) + scaleEffect(1.1) + clipped
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.1f)
                            .blur(20.dp),
                    )
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = imageScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            else -> {
                // stories.contentUnavailable
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.Black.copy(0.85f),
                                    Color.Black.copy(0.6f),
                                    Color.Black.copy(0.4f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White.copy(0.6f),
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            stringResource(R.string.stories_content_unavailable),
                            color = Color.White.copy(0.8f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}
