package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.shared.MomentsVideoGravity
import com.moments.android.views.shared.MomentsVideoPlaybackTimeline
import com.moments.android.views.shared.MomentsVideoPlayer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Port de `Views/Messaging/Components/ChatMediaViews.swift`.
 * BlurView iOS → fill sólido / blur Coil (sin material UIKit).
 */
private val mediaCorner = RoundedCornerShape(16.dp)

/** ≡ iOS downsampling 208×272 en burbujas de chat. */
val ChatMediaBubbleDownsample = DpSize(208.dp, 272.dp)

/** Overlay centrado: flecha + tamaño (≡ iOS `ChatMediaDownloadOverlay`). */
@Composable
fun ChatMediaDownloadOverlay(sizeLabel: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
            Text(
                sizeLabel ?: stringResource(R.string.chat_media_download),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Placeholder genérico sin miniatura en disco (≡ iOS `ChatMediaManualDownloadPlaceholder`). */
@Composable
fun ChatMediaManualDownloadPlaceholder(
    sizeLabel: String?,
    showsVideoBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4A4A4C), Color(0xFF2C2C2E), Color(0xFF1C1C1E)),
                ),
            ),
    ) {
        // ≡ BlurView ultraThin ≈ velo oscuro sólido
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        ChatMediaDownloadOverlay(sizeLabel)
        if (showsVideoBadge) {
            ChatVideoPlayBadge(
                size = 14.dp,
                padding = 6.dp,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/** Overlay de progreso de descarga (≡ iOS `ChatMediaDownloadProgressOverlay`). */
@Composable
fun ChatMediaDownloadProgressOverlay(
    progress: Double,
    modifier: Modifier = Modifier,
    ringSize: Dp = 60.dp,
    lineWidth: Dp = 4.dp,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        MediaProgressRing(
            progress = progress.coerceIn(0.03, 1.0),
            size = ringSize,
            lineWidth = lineWidth,
        )
    }
}

@Composable
fun GlassmorphicImageMessage(
    imageUrl: String?,
    previewThumbnailUrl: String? = null,
    isSending: Boolean,
    isResolvingMedia: Boolean = false,
    isAwaitingManualDownload: Boolean = false,
    isDownloadingMedia: Boolean = false,
    downloadProgress: Double? = null,
    downloadSizeLabel: String? = null,
    progress: Double?,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    downsamplingSize: DpSize? = ChatMediaBubbleDownsample,
) {
    val a11yPhoto = stringResource(R.string.chat_a11y_photo)
    val a11yHint = stringResource(R.string.chat_a11y_open_media)
    val blurredPreview = previewThumbnailUrl?.takeIf { isAwaitingManualDownload && it.isNotBlank() }

    Box(
        modifier
            .clip(mediaCorner)
            .border(0.5.dp, Color.White.copy(alpha = 0.2f), mediaCorner)
            .shadow(10.dp, mediaCorner, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .semantics { contentDescription = "$a11yPhoto. $a11yHint" }
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier),
    ) {
        when {
            isDownloadingMedia -> {
                Box(Modifier.fillMaxSize()) {
                    when {
                        !blurredPreview.isNullOrBlank() -> ChatKFImage(
                            blurredPreview,
                            Modifier.fillMaxSize().blur(22.dp),
                            downsamplingSize,
                        )
                        !imageUrl.isNullOrBlank() -> ChatKFImage(imageUrl, Modifier.fillMaxSize(), downsamplingSize)
                        else -> Box(Modifier.fillMaxSize().background(Color.White.copy(0.1f)))
                    }
                    ChatMediaDownloadProgressOverlay(downloadProgress ?: 0.03)
                }
            }
            isAwaitingManualDownload -> {
                if (!blurredPreview.isNullOrBlank()) {
                    Box(Modifier.fillMaxSize()) {
                        ChatKFImage(
                            blurredPreview,
                            Modifier.fillMaxSize().blur(22.dp),
                            downsamplingSize,
                        )
                        ChatMediaDownloadOverlay(downloadSizeLabel)
                    }
                } else {
                    ChatMediaManualDownloadPlaceholder(downloadSizeLabel, modifier = Modifier.fillMaxSize())
                }
            }
            isResolvingMedia -> ChatMediaResolvingPlaceholder(Modifier.fillMaxSize())
            !imageUrl.isNullOrBlank() -> ChatKFImage(imageUrl, Modifier.fillMaxSize(), downsamplingSize)
            else -> Box(
                Modifier.fillMaxSize().background(Color.White.copy(0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Photo, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(28.dp))
            }
        }
        if (isSending) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(mediaCorner)
                    .background(Color.Black.copy(0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                MediaProgressRing(progress = max(progress ?: 0.03, 0.03), size = 60.dp, lineWidth = 4.dp)
            }
        }
    }
}

@Composable
fun GlassmorphicVideoMessage(
    videoUrl: String?,
    thumbnailUrl: String?,
    isSending: Boolean,
    isResolvingMedia: Boolean = false,
    isAwaitingManualDownload: Boolean = false,
    isDownloadingMedia: Boolean = false,
    downloadProgress: Double? = null,
    downloadSizeLabel: String? = null,
    progress: Double?,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    downsamplingSize: DpSize? = ChatMediaBubbleDownsample,
) {
    val a11yVideo = stringResource(R.string.chat_a11y_video)
    val a11yHint = stringResource(R.string.chat_a11y_open_media)
    val blurredPreview = thumbnailUrl?.takeIf { isAwaitingManualDownload && it.isNotBlank() }
    val showPlayBadge = (!isAwaitingManualDownload && !isDownloadingMedia) ||
        (isAwaitingManualDownload && !blurredPreview.isNullOrBlank())

    Box(
        modifier
            .clip(mediaCorner)
            .border(0.5.dp, Color.White.copy(alpha = 0.2f), mediaCorner)
            .shadow(10.dp, mediaCorner, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .semantics { contentDescription = "$a11yVideo. $a11yHint" }
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier),
    ) {
        when {
            isDownloadingMedia -> {
                Box(Modifier.fillMaxSize()) {
                    when {
                        !blurredPreview.isNullOrBlank() -> ChatKFImage(
                            blurredPreview,
                            Modifier.fillMaxSize().blur(22.dp),
                            downsamplingSize,
                        )
                        !thumbnailUrl.isNullOrBlank() -> ChatKFImage(thumbnailUrl, Modifier.fillMaxSize(), downsamplingSize)
                        else -> Box(Modifier.fillMaxSize().background(Color.White.copy(0.1f)))
                    }
                    ChatMediaDownloadProgressOverlay(downloadProgress ?: 0.03)
                }
            }
            isAwaitingManualDownload -> {
                if (!blurredPreview.isNullOrBlank()) {
                    Box(Modifier.fillMaxSize()) {
                        ChatKFImage(
                            blurredPreview,
                            Modifier.fillMaxSize().blur(22.dp),
                            downsamplingSize,
                        )
                        ChatMediaDownloadOverlay(downloadSizeLabel)
                    }
                } else {
                    ChatMediaManualDownloadPlaceholder(
                        sizeLabel = downloadSizeLabel,
                        showsVideoBadge = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            isResolvingMedia -> ChatMediaResolvingPlaceholder(Modifier.fillMaxSize())
            !thumbnailUrl.isNullOrBlank() -> ChatKFImage(thumbnailUrl, Modifier.fillMaxSize(), downsamplingSize)
            else -> Box(Modifier.fillMaxSize().background(Color.White.copy(0.1f)))
        }
        if (isSending) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(mediaCorner)
                    .background(Color.Black.copy(0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                MediaProgressRing(progress = max(progress ?: 0.03, 0.03), size = 60.dp, lineWidth = 4.dp)
            }
        }
        if (showPlayBadge) {
            ChatVideoPlayBadge(
                size = 22.dp,
                padding = 12.dp,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/** Icono play esquina, sin círculo (≡ iOS `ChatVideoPlayBadge`). */
@Composable
fun ChatVideoPlayBadge(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    padding: Dp = 10.dp,
) {
    Icon(
        Icons.Default.PlayArrow,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .padding(padding)
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(0.75f),
                spotColor = Color.Black.copy(0.55f),
            ),
    )
}

/** Visor de vídeo de chat (≡ iOS `NormalVideoPlayerView`). */
@Composable
fun NormalVideoPlayerView(
    videoUrl: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbnailUrl: String? = null,
) {
    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(0.0) }
    var duration by remember { mutableStateOf(0.0) }
    var externalSeekTime by remember { mutableStateOf<Double?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (dragOffsetPx > 100f) onClose() else dragOffsetPx = 0f
                    },
                    onDragCancel = { dragOffsetPx = 0f },
                    onDrag = { change, drag ->
                        change.consume()
                        if (drag.y > 0 || dragOffsetPx > 0f) {
                            dragOffsetPx = (dragOffsetPx + drag.y).coerceAtLeast(0f)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                )
            },
    ) {
        if (!videoUrl.isNullOrBlank()) {
            MomentsVideoPlayer(
                url = videoUrl,
                isLooping = true,
                isPaused = isPaused,
                isMuted = isMuted,
                prioritizeSmoothPlayback = true,
                videoGravity = MomentsVideoGravity.RESIZE_ASPECT_FILL,
                onDurationReceived = { duration = maxOf(it, 0.0) },
                onProgressUpdate = { if (!isPaused) currentTime = maxOf(it, 0.0) },
                onVideoFinished = {},
                externalSeekTime = externalSeekTime,
                onExternalSeekConsumed = { externalSeekTime = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE))))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.common_video).uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(34.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { isMuted = !isMuted },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            MomentsVideoPlaybackTimeline(
                currentTime = currentTime,
                duration = duration,
                horizontalPadding = 0.dp,
                onSeek = { target ->
                    currentTime = target
                    externalSeekTime = target
                },
                modifier = Modifier.padding(bottom = 26.dp),
            )
        }
    }
}

/** Fullscreen foto con dismiss por drag (≡ iOS `FullScreenImageView`). */
@Composable
fun FullScreenImageView(
    imageUrl: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (dragOffsetPx > 100f) onClose() else dragOffsetPx = 0f
                    },
                    onDragCancel = { dragOffsetPx = 0f },
                    onDrag = { change, drag ->
                        change.consume()
                        if (drag.y > 0 || dragOffsetPx > 0f) {
                            dragOffsetPx = (dragOffsetPx + drag.y).coerceAtLeast(0f)
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Photo, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.common_photo).uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
