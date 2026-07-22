package com.moments.android.views.story.storyviewer

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.moments.android.models.MediaItem
import com.moments.android.models.Story

/**
 * Port MVP de `StoryViewerScreen.swift` — media + progress + header + gestos.
 * Stickers / reply / viewers / ads = stubs (no-op).
 */
@Composable
fun StoryViewerScreen(
    story: Story,
    segmentCount: Int,
    segmentIndex: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onHoldChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val durationMs = ((if (story.duration > 0) story.duration else 5.0) * 1000).toLong().coerceIn(2000L, 30000L)
    val progress = remember(story.id) { Animatable(0f) }
    var held by remember { mutableStateOf(false) }
    val onNextState = rememberUpdatedState(onNext)
    val onPreviousState = rememberUpdatedState(onPrevious)

    LaunchedEffect(story.id, held) {
        if (held) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMs.toInt(), easing = LinearEasing),
        )
        onNextState.value()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        StoryViewerMedia(story = story, modifier = Modifier.fillMaxSize())

        // Gradient top for readability
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.55f), Color.Transparent),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            StoryProgressRow(
                count = segmentCount.coerceAtLeast(1),
                currentIndex = segmentIndex.coerceIn(0, (segmentCount - 1).coerceAtLeast(0)),
                progress = progress.value,
            )
            Spacer(Modifier.height(10.dp))
            StoryViewerHeader(
                username = story.username,
                profileImagePath = story.profileImagePath,
                onClose = onDismiss,
            )
        }

        // Caption text (simple)
        story.text?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                caption,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 48.dp),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Tap zones + hold pause
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(story.id) {
                        detectTapGestures(
                            onPress = {
                                held = true
                                onHoldChanged(true)
                                tryAwaitRelease()
                                held = false
                                onHoldChanged(false)
                            },
                            onTap = { onPreviousState.value() },
                        )
                    },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(story.id) {
                        detectTapGestures(
                            onPress = {
                                held = true
                                onHoldChanged(true)
                                tryAwaitRelease()
                                held = false
                                onHoldChanged(false)
                            },
                            onTap = { onNextState.value() },
                        )
                    },
            )
        }
    }
}

@Composable
private fun StoryProgressRow(
    count: Int,
    currentIndex: Int,
    progress: Float,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(count) { i ->
            val fill = when {
                i < currentIndex -> 1f
                i == currentIndex -> progress
                else -> 0f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(0.28f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun StoryViewerHeader(
    username: String,
    profileImagePath: String?,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.2f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            username,
            Modifier.weight(1f),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun StoryViewerMedia(
    story: Story,
    modifier: Modifier = Modifier,
) {
    val media = story.mediaItem
    when (media.type) {
        MediaItem.MediaType.VIDEO -> StoryVideoPlayer(url = media.url, modifier = modifier)
        else -> {
            val url = media.url.ifBlank { media.thumbnailUrl.orEmpty() }
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StoryVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.parse(url)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
