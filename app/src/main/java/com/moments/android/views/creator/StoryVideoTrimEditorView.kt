package com.moments.android.views.creator

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Port of `StoryVideoTrimEditorView.swift`. */
@Composable
fun StoryVideoTrimEditorView(
    videoUri: Uri,
    duration: Double,
    onCancel: () -> Unit,
    onComplete: (CreatorMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val maxClipDuration = min(StoryVideoProcessingService.maxStorySegmentDuration, duration)
    var trimStart by remember(videoUri) { mutableDoubleStateOf(0.0) }
    var trimDuration by remember(videoUri) { mutableDoubleStateOf(maxClipDuration) }
    var thumbnails by remember(videoUri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(true) }
    var playbackProgress by remember { mutableDoubleStateOf(0.0) }
    var previewTime by remember { mutableStateOf<Double?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var lastHapticTick by remember { mutableDoubleStateOf(-1.0) }
    val trimEnd = min(trimStart + trimDuration, duration)

    LaunchedEffect(videoUri, duration) {
        thumbnails = withContext(Dispatchers.IO) { timelineFrames(context, videoUri, duration) }
    }

    fun tick(time: Double) {
        val rounded = time.roundToInt().toDouble()
        if (rounded != lastHapticTick) {
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
            errorMessage = error.message ?: "Unable to trim this video."
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF0B1215))) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black),
            ) {
                StoryVideoPlayerView(
                    videoUri = videoUri,
                    videoGravity = StoryVideoGravity.RESIZE_ASPECT_FILL,
                    isMuted = isMuted,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    previewTime = previewTime,
                    onPlayProgress = { progress -> if (!isDragging && previewTime == null) playbackProgress = progress },
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TrimChromeButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onCancel)
                    TrimChromeButton(Icons.Filled.Check, enabled = !isProcessing, onClick = ::export)
                }
                TrimChromeButton(
                    if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(38.dp),
                )
                Text(
                    "${"%.1f".format(trimDuration)}s selected",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
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
                onStartChange = { start ->
                    isDragging = true
                    trimStart = start.coerceIn(0.0, max(0.0, duration - trimDuration))
                    previewTime = trimStart
                    tick(trimStart)
                },
                onDurationChange = { newDuration ->
                    isDragging = true
                    trimDuration = newDuration.coerceIn(1.0, min(maxClipDuration, duration - trimStart))
                    previewTime = trimStart + trimDuration
                    tick(trimStart + trimDuration)
                },
                onPlayheadChange = { time ->
                    previewTime = time.coerceIn(trimStart, trimEnd)
                    playbackProgress = previewTime ?: trimStart
                    tick(previewTime ?: trimStart)
                },
                onDragFinished = {
                    isDragging = false
                    previewTime = null
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
        if (isProcessing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Processing video…", color = Color.White)
                }
            }
        }
    }
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Video editor") },
            text = { Text(message) },
            confirmButton = { IconButton(onClick = { errorMessage = null }) { Icon(Icons.Filled.Check, null) } },
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
    onStartChange: (Double) -> Unit,
    onDurationChange: (Double) -> Unit,
    onPlayheadChange: (Double) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(52.dp)) {
        val density = LocalDensity.current
        val totalWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val safeDuration = duration.coerceAtLeast(.1).toFloat()
        val handleHalfWidth = with(density) { 7.dp.toPx() }
        val frameTop = with(density) { 4.dp.roundToPx() }
        val playheadHalfWidth = with(density) { 2.dp.toPx() }
        val playheadTop = with(density) { 3.dp.roundToPx() }
        val startPx = (totalWidth * trimStart.toFloat() / safeDuration).coerceAtLeast(0f)
        val windowPx = max(with(density) { 44.dp.toPx() }, totalWidth * trimDuration.toFloat() / safeDuration)
        val clampedStart = min(startPx, max(0f, totalWidth - windowPx))
        val endPx = min(totalWidth, clampedStart + windowPx)
        val playheadPx = (totalWidth * playbackProgress.toFloat() / safeDuration).coerceIn(clampedStart, endPx)

        Row(Modifier.fillMaxWidth().height(44.dp).align(Alignment.Center).clip(RoundedCornerShape(10.dp))) {
            if (thumbnails.isEmpty()) repeat(8) {
                Box(Modifier.weight(1f).fillMaxSize().background(Color.White.copy(.08f)))
            } else thumbnails.forEach { frame ->
                Image(
                    frame.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
        Box(Modifier.offset { IntOffset(0, frameTop) }.width(with(density) { clampedStart.toDp() }).height(44.dp).background(Color.Black.copy(.65f)))
        Box(
            Modifier
                .offset { IntOffset(endPx.roundToInt(), frameTop) }
                .width(with(density) { (totalWidth - endPx).toDp() })
                .height(44.dp)
                .background(Color.Black.copy(.65f)),
        )
        Box(
            Modifier
                .offset { IntOffset(clampedStart.roundToInt(), 0) }
                .width(with(density) { windowPx.toDp() })
                .height(52.dp)
                .border(4.dp, Color.White, RoundedCornerShape(11.dp))
                .pointerInput(totalWidth, trimStart, trimDuration) {
                    detectDragGestures(
                        onDragStart = { HapticManager.shared.lightImpact() },
                        onDragEnd = onDragFinished,
                        onDragCancel = onDragFinished,
                    ) { change, drag ->
                        change.consume()
                        onStartChange(trimStart + (drag.x / totalWidth * duration).toDouble())
                    }
                },
        )
        TrimHandle(
            Modifier.offset { IntOffset((clampedStart - handleHalfWidth).roundToInt(), 0) },
            onMove = { delta -> onStartChange(trimStart + (delta / totalWidth * duration).toDouble()) },
            onFinished = onDragFinished,
        )
        TrimHandle(
            Modifier.offset { IntOffset((endPx - handleHalfWidth).roundToInt(), 0) },
            onMove = { delta -> onDurationChange(trimDuration + (delta / totalWidth * duration).toDouble()) },
            onFinished = onDragFinished,
        )
        Box(
            Modifier
                .offset { IntOffset((playheadPx - playheadHalfWidth).roundToInt(), playheadTop) }
                .width(3.dp)
                .height(46.dp)
                .background(Color.White)
                .pointerInput(totalWidth, trimStart, trimDuration) {
                    detectDragGestures(onDragEnd = onDragFinished, onDragCancel = onDragFinished) { change, drag ->
                        change.consume()
                        onPlayheadChange(playbackProgress + (drag.x / totalWidth * duration).toDouble())
                    }
                },
        )
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, onMove: (Float) -> Unit, onFinished: () -> Unit) {
    Box(
        modifier
            .size(width = 14.dp, height = 52.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { HapticManager.shared.lightImpact() },
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
            repeat(3) { Box(Modifier.size(3.dp).background(Color.Black, CircleShape)) }
        }
    }
}

@Composable
private fun TrimChromeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(42.dp).momentsChromeGlass(CircleShape, interactive = true),
    ) { Icon(icon, null, tint = Color.White) }
}

private fun timelineFrames(context: android.content.Context, uri: Uri, duration: Double): List<Bitmap> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        (0 until 10).mapNotNull { index ->
            retriever.getFrameAtTime((duration * index / 9.0 * 1_000_000L).toLong())
        }
    } catch (_: Exception) {
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}
