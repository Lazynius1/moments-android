package com.moments.android.views.creator

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MIN_CLIP_DURATION = 1.0

/**
 * Port de `StoryVideoTrimEditorView.swift`.
 * Estética “Nitidez” (light/dark), canvas 9:16, timeline + handles + playhead.
 */
@Composable
fun StoryVideoTrimEditorView(
    videoUri: Uri,
    duration: Double,
    onCancel: () -> Unit,
    onComplete: (CreatorMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val maxClipDuration = min(StoryVideoProcessingService.maxStorySegmentDuration, duration)
    val corner = storyViewerCanvasCornerRadius

    // Adaptive aesthetics ≡ Swift workspaceBg / selectionColor / …
    val workspaceBg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val selectionColor = if (isDark) Color.White else Color.Black
    val gripColor = if (isDark) Color.Black else Color.White
    val dimmingColor = if (isDark) Color.Black else Color.White
    val chromeIcon = if (isDark) Color.White else Color.Black.copy(0.82f)
    val chromeStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    val shadowColor = if (isDark) Color.Black else Color.Gray.copy(0.3f)
    val placeholderFill = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f)

    var trimStart by remember(videoUri) { mutableDoubleStateOf(0.0) }
    var trimDuration by remember(videoUri) { mutableDoubleStateOf(maxClipDuration) }
    var thumbnails by remember(videoUri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(true) }
    var playbackProgress by remember { mutableDoubleStateOf(0.0) }
    var previewTime by remember { mutableStateOf<Double?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var isScrubbingPlayhead by remember { mutableStateOf(false) }
    var lastHapticTick by remember { mutableDoubleStateOf(0.0) }
    val trimEnd = min(trimStart + trimDuration, duration)

    LaunchedEffect(videoUri, duration) {
        thumbnails = withContext(Dispatchers.IO) { timelineFrames(context, videoUri, duration) }
    }

    fun tick(time: Double) {
        val rounded = time.roundToInt().toDouble()
        if (abs(rounded - lastHapticTick) >= 1.0) {
            lastHapticTick = rounded
            HapticManager.shared.lightImpact()
        }
    }

    fun export() {
        isProcessing = true
    }

    LaunchedEffect(isProcessing) {
        if (!isProcessing) return@LaunchedEffect
        runCatching {
            StoryVideoProcessingService.exportStoryClip(videoUri, trimStart, trimEnd)
        }.onSuccess { media ->
            isProcessing = false
            onComplete(media)
        }.onFailure { error ->
            isProcessing = false
            errorMessage = error.message
                ?: context.getString(R.string.story_video_error_export_failed)
        }
    }

    Box(modifier.fillMaxSize().background(workspaceBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(6.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(corner),
                        ambientColor = shadowColor.copy(if (isDark) 0.4f else 0.15f),
                        spotColor = shadowColor.copy(if (isDark) 0.4f else 0.15f),
                    )
                    .clip(RoundedCornerShape(corner))
                    .background(Color.Black)
                    .border(1.dp, chromeStroke, RoundedCornerShape(corner)),
            ) {
                StoryVideoPlayerView(
                    videoUri = videoUri,
                    videoGravity = StoryVideoGravity.RESIZE_ASPECT_FILL,
                    isMuted = isMuted,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    previewTime = previewTime,
                    onPlayProgress = { progress ->
                        if (!isDragging && !isScrubbingPlayhead) {
                            playbackProgress = progress
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TrimChromeButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        tint = chromeIcon,
                        stroke = chromeStroke,
                        onClick = onCancel,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    TrimChromeButton(
                        Icons.Filled.Check,
                        tint = chromeIcon,
                        stroke = chromeStroke,
                        enabled = !isProcessing,
                        onClick = ::export,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }

                TrimChromeButton(
                    if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    tint = chromeIcon,
                    stroke = chromeStroke,
                    onClick = { isMuted = !isMuted },
                    size = 38.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )

                // iOS hardcodes "%.1fs selected" (no Localizable key).
                Text(
                    String.format("%.1fs selected", trimDuration),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                        .border(1.dp, chromeStroke, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            StoryTrimTimeline(
                duration = duration,
                trimStart = trimStart,
                trimDuration = trimDuration,
                playbackProgress = playbackProgress,
                thumbnails = thumbnails,
                selectionColor = selectionColor,
                gripColor = gripColor,
                dimmingColor = dimmingColor,
                placeholderFill = placeholderFill,
                outlineStroke = chromeStroke,
                showPlayhead = !isDragging || isScrubbingPlayhead,
                onSelectionDelta = { deltaSeconds ->
                    if (!isDragging) {
                        isDragging = true
                        HapticManager.shared.lightImpact()
                    }
                    val clampedStart = min(
                        max(trimStart + deltaSeconds, 0.0),
                        max(duration - trimDuration, 0.0),
                    )
                    trimStart = clampedStart
                    previewTime = clampedStart
                    tick(clampedStart)
                },
                onLeadingDelta = { deltaSeconds ->
                    if (!isDragging) {
                        isDragging = true
                        HapticManager.shared.lightImpact()
                    }
                    val originalEnd = trimStart + trimDuration
                    val newStart = min(
                        max(0.0, trimStart + deltaSeconds),
                        originalEnd - MIN_CLIP_DURATION,
                    )
                    trimStart = newStart
                    trimDuration = min(maxClipDuration, max(MIN_CLIP_DURATION, originalEnd - newStart))
                    previewTime = newStart
                    tick(newStart)
                },
                onTrailingDelta = { deltaSeconds ->
                    if (!isDragging) {
                        isDragging = true
                        HapticManager.shared.lightImpact()
                    }
                    val newDuration = min(
                        maxClipDuration,
                        max(MIN_CLIP_DURATION, trimDuration + deltaSeconds),
                    )
                    val finalDuration = min(newDuration, duration - trimStart)
                    trimDuration = finalDuration
                    val currentEnd = trimStart + finalDuration
                    previewTime = currentEnd
                    tick(currentEnd)
                },
                onPlayheadDelta = { deltaSeconds ->
                    if (!isScrubbingPlayhead) {
                        isScrubbingPlayhead = true
                        HapticManager.shared.lightImpact()
                    }
                    val target = (playbackProgress + deltaSeconds).coerceIn(trimStart, trimEnd)
                    playbackProgress = target
                    previewTime = target
                    tick(target)
                },
                onDragFinished = {
                    isDragging = false
                    isScrubbingPlayhead = false
                    previewTime = null
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
            )
        }

        if (isProcessing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        stringResource(R.string.story_video_trim_processing),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.video_editor_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(stringResource(R.string.video_editor_ok))
                }
            },
        )
    }
}

@Composable
private fun StoryTrimTimeline(
    duration: Double,
    trimStart: Double,
    trimDuration: Double,
    playbackProgress: Double,
    thumbnails: List<Bitmap>,
    selectionColor: Color,
    gripColor: Color,
    dimmingColor: Color,
    placeholderFill: Color,
    outlineStroke: Color,
    showPlayhead: Boolean,
    onSelectionDelta: (Double) -> Unit,
    onLeadingDelta: (Double) -> Unit,
    onTrailingDelta: (Double) -> Unit,
    onPlayheadDelta: (Double) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(52.dp)) {
        val density = LocalDensity.current
        val totalWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val safeDuration = duration.coerceAtLeast(0.1)
        val handleHalfWidth = with(density) { 7.dp.toPx() }
        val startPx = (totalWidth * (trimStart / safeDuration).toFloat()).coerceAtLeast(0f)
        val windowPx = max(
            with(density) { 44.dp.toPx() },
            totalWidth * (trimDuration / safeDuration).toFloat(),
        )
        val clampedStart = min(startPx, max(0f, totalWidth - windowPx))
        val endPx = min(totalWidth, clampedStart + windowPx)
        val needleX = (totalWidth * (playbackProgress / safeDuration).toFloat())
            .coerceIn(clampedStart, endPx - with(density) { 3.dp.toPx() })

        fun secondsDelta(dx: Float): Double = (dx / totalWidth) * duration

        // Thumbnail strip (44pt, centered in 52pt)
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            if (thumbnails.isEmpty()) {
                repeat(8) {
                    Box(Modifier.weight(1f).fillMaxSize().background(placeholderFill))
                }
            } else {
                thumbnails.forEach { frame ->
                    Image(
                        frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }

        // Inactive dimming (clipped to 44pt strip)
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            Box(
                Modifier
                    .width(with(density) { clampedStart.toDp() })
                    .fillMaxSize()
                    .background(dimmingColor.copy(0.65f)),
            )
            Box(
                Modifier
                    .offset { IntOffset(endPx.roundToInt(), 0) }
                    .width(with(density) { (totalWidth - endPx).coerceAtLeast(0f).toDp() })
                    .fillMaxSize()
                    .background(dimmingColor.copy(0.65f)),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .align(Alignment.Center)
                .border(1.dp, outlineStroke, RoundedCornerShape(10.dp)),
        )

        // Selection window (48pt stroke, offset y: 2)
        Box(
            Modifier
                .offset { IntOffset(clampedStart.roundToInt(), with(density) { 2.dp.roundToPx() }) }
                .width(with(density) { windowPx.toDp() })
                .height(48.dp)
                .border(4.dp, selectionColor, RoundedCornerShape(11.dp))
                .pointerInput(totalWidth, duration) {
                    detectDragGestures(
                        onDragEnd = onDragFinished,
                        onDragCancel = onDragFinished,
                    ) { change, drag ->
                        change.consume()
                        onSelectionDelta(secondsDelta(drag.x))
                    }
                },
        )

        // Playhead needle + 32dp hitbox
        if (showPlayhead) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (needleX - with(density) { 14.5.dp.toPx() }).roundToInt(),
                            0,
                        )
                    }
                    .size(width = 32.dp, height = 52.dp)
                    .pointerInput(totalWidth, duration) {
                        detectDragGestures(
                            onDragEnd = onDragFinished,
                            onDragCancel = onDragFinished,
                        ) { change, drag ->
                            change.consume()
                            onPlayheadDelta(secondsDelta(drag.x))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(46.dp)
                        .shadow(2.dp, ambientColor = Color.Black.copy(0.35f))
                        .background(selectionColor),
                )
            }
        }

        TrimHandle(
            gripColor = gripColor,
            selectionColor = selectionColor,
            modifier = Modifier.offset {
                IntOffset(max(0, (clampedStart - handleHalfWidth).roundToInt()), 0)
            },
            onMove = { dx -> onLeadingDelta(secondsDelta(dx)) },
            onFinished = onDragFinished,
        )
        TrimHandle(
            gripColor = gripColor,
            selectionColor = selectionColor,
            modifier = Modifier.offset {
                IntOffset(
                    min(
                        (totalWidth - with(density) { 14.dp.toPx() }).roundToInt(),
                        (endPx - handleHalfWidth).roundToInt(),
                    ),
                    0,
                )
            },
            onMove = { dx -> onTrailingDelta(secondsDelta(dx)) },
            onFinished = onDragFinished,
        )
    }
}

@Composable
private fun TrimHandle(
    gripColor: Color,
    selectionColor: Color,
    onMove: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(width = 14.dp, height = 52.dp)
            .shadow(3.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(0.2f))
            .clip(RoundedCornerShape(7.dp))
            .background(selectionColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onFinished,
                    onDragCancel = onFinished,
                ) { change, drag ->
                    change.consume()
                    onMove(drag.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(3.dp)
                        .background(gripColor.copy(0.65f), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TrimChromeButton(
    icon: ImageVector,
    tint: Color,
    stroke: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 42.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

/** ≡ `generateTimeline()` — 10 frames, maxSize 160×284. */
private fun timelineFrames(
    context: android.content.Context,
    uri: Uri,
    duration: Double,
): List<Bitmap> {
    val retriever = MediaMetadataRetriever()
    val count = 10
    return try {
        retriever.setDataSource(context, uri)
        (0 until count).mapNotNull { index ->
            val seconds = duration * (index.toDouble() / max(count - 1, 1).toDouble())
            val timeUs = (seconds * 1_000_000L).toLong()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    160,
                    284,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }
    } catch (_: Exception) {
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}
