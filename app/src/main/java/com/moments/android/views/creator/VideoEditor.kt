package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** iOS `setupVideoPlayer` cap: `min(duration, 60)`. */
private const val EDITOR_MAX_DURATION_SECONDS = 60.0
private const val MIN_CLIP_DURATION = 1.0

enum class PlaybackSpeed(val labelRes: Int, val multiplier: Float) {
    SLOW(R.string.video_editor_speed_slow, 0.3f),
    NORMAL(R.string.video_editor_speed_normal, 1f),
    FAST(R.string.video_editor_speed_fast, 2f),
    VERY_FAST(R.string.video_editor_speed_very_fast, 3f),
}

/**
 * Port de `SocialVideoEditorView.VideoFormat`.
 * [ratioLabelRes] ≡ rawValue; [displayNameRes] ≡ displayName; target ≡ targetSize.
 */
enum class VideoFormat(
    val ratioLabelRes: Int,
    val displayNameRes: Int,
    val aspectRatio: Float,
    val creatorAspectRatio: CreatorAspectRatio,
    val targetWidth: Int,
    val targetHeight: Int,
) {
    REELS(
        R.string.video_editor_format_reels,
        R.string.video_editor_format_reels_name,
        9f / 16f,
        CreatorAspectRatio.NINE_BY_SIXTEEN,
        1080,
        1920,
    ),
    SQUARE(
        R.string.video_editor_format_square,
        R.string.video_editor_format_square_name,
        1f,
        CreatorAspectRatio.SQUARE,
        1080,
        1080,
    ),
    LANDSCAPE(
        R.string.video_editor_format_landscape,
        R.string.video_editor_format_landscape_name,
        16f / 9f,
        CreatorAspectRatio.LANDSCAPE,
        1920,
        1080,
    );

    val icon: ImageVector
        get() = when (this) {
            REELS -> Icons.Filled.CropPortrait
            SQUARE -> Icons.Filled.CropSquare
            LANDSCAPE -> Icons.Filled.CropLandscape
        }

    /** ≡ `calculateThumbnailSize`. */
    fun thumbnailMaxSize(): Pair<Int, Int> = when (this) {
        REELS -> 720 to 1280
        SQUARE -> 1080 to 1080
        LANDSCAPE -> 1280 to 720
    }
}

/** Espejo de `ProcessedVideoData`. */
private data class ProcessedVideoData(
    val compressedVideoUri: Uri,
    val thumbnailUri: Uri,
    val duration: Double,
    val fileSize: Long,
    val resolutionWidth: Int,
    val resolutionHeight: Int,
)

/** Port of `SocialVideoEditorView` from `VideoEditor.swift`. */
@Composable
fun SocialVideoEditorView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val workspaceBg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val selectionColor = if (isDark) Color.White else Color.Black
    val gripColor = if (isDark) Color.Black else Color.White
    val dimmingColor = if (isDark) Color.Black else Color.White
    val textPrimary = if (isDark) Color.White else Color.Black
    val chromeStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    val placeholderFill = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f)

    val videos = selectedMediaItems.filter { it.isVideo }
    if (videos.isEmpty()) {
        onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION)
        return
    }

    var selectedClipIndex by remember { mutableIntStateOf(0) }
    val currentVideo = videos.getOrElse(selectedClipIndex.coerceIn(0, videos.lastIndex)) { videos.first() }
    var duration by remember(currentVideo.id) {
        mutableDoubleStateOf(min(currentVideo.durationSeconds ?: 60.0, EDITOR_MAX_DURATION_SECONDS))
    }
    var trimStart by remember(currentVideo.id) { mutableDoubleStateOf(0.0) }
    var trimEnd by remember(currentVideo.id) { mutableDoubleStateOf(duration) }
    var speed by remember { mutableStateOf(PlaybackSpeed.NORMAL) }
    var format by remember { mutableStateOf(inferVideoFormat(currentVideo.aspectRatio)) }
    var volume by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    var customCoverUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingMessage by remember {
        mutableStateOf(context.getString(R.string.video_editor_processing_start))
    }
    var processError by remember { mutableStateOf<String?>(null) }
    var timelineFrames by remember(currentVideo.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var playbackProgress by remember(currentVideo.id) { mutableDoubleStateOf(0.0) }
    var previewTime by remember { mutableStateOf<Double?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var isScrubbingPlayhead by remember { mutableStateOf(false) }
    var lastHapticTick by remember { mutableDoubleStateOf(0.0) }

    fun tick(time: Double) {
        val rounded = time.roundToInt().toDouble()
        if (abs(rounded - lastHapticTick) >= 1.0) {
            lastHapticTick = rounded
            HapticManager.shared.lightImpact()
        }
    }

    LaunchedEffect(currentVideo.uri) {
        runCatching { StoryVideoProcessingService.duration(currentVideo.uri) }.getOrNull()?.let {
            duration = min(it, EDITOR_MAX_DURATION_SECONDS)
            trimStart = 0.0
            trimEnd = duration
            format = inferVideoFormat(currentVideo.aspectRatio)
        }
    }
    LaunchedEffect(currentVideo.uri, duration) {
        timelineFrames = withContext(Dispatchers.IO) {
            extractVideoTimelineFrames(currentVideo.uri, duration, 20)
        }
    }

    // ≡ processAndContinue / processAllVideos / processVideoWithThumbnail
    LaunchedEffect(isProcessing) {
        if (!isProcessing) return@LaunchedEffect
        processingProgress = 0f
        processingMessage = context.getString(R.string.video_editor_processing_start)
        runCatching {
            val videoItems = selectedMediaItems.filter { it.isVideo }
            if (videoItems.isEmpty()) {
                onSelectedMediaItemsChange(selectedMediaItems)
                return@runCatching
            }
            var updated = selectedMediaItems
            var completed = 0
            for ((index, media) in videoItems.withIndex()) {
                processingMessage = context.getString(
                    R.string.video_editor_processing_video,
                    index + 1,
                    videoItems.size,
                )
                processingProgress = index.toFloat() / videoItems.size
                val processed = withContext(Dispatchers.IO) {
                    processVideoWithThumbnail(
                        context = context,
                        videoUri = media.uri,
                        format = format,
                        customCoverUri = customCoverUri,
                    )
                }
                updated = updated.map { item ->
                    if (item.id != media.id) item else item.copy(
                        uri = processed.compressedVideoUri,
                        durationSeconds = processed.duration,
                        thumbnailUri = processed.thumbnailUri,
                        videoFileSize = processed.fileSize,
                        videoResolution = "${processed.resolutionWidth}x${processed.resolutionHeight}",
                        aspectRatio = format.creatorAspectRatio,
                        recommendedAspectRatio = format.creatorAspectRatio,
                        hasEdits = true,
                    )
                }
                completed += 1
                processingProgress = completed.toFloat() / videoItems.size
            }
            processingMessage = context.getString(R.string.video_editor_processing_finish)
            processingProgress = 1f
            onSelectedMediaItemsChange(updated)
        }.onSuccess {
            isProcessing = false
            onCurrentFlowChange(CreatorFlow.CAPTION_AND_DETAILS)
        }.onFailure {
            isProcessing = false
            processError = it.message ?: context.getString(R.string.video_editor_error_processing)
        }
    }

    Box(modifier.fillMaxSize().background(workspaceBg)) {
        Column(Modifier.fillMaxSize()) {
            // Header ≡ headerView
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorCircleButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    tint = textPrimary,
                    stroke = chromeStroke,
                    enabled = !isProcessing,
                ) { onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.video_editor_edit),
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.weight(1f))
                if (!isProcessing) {
                    GlowSharePill(
                        titleRes = R.string.creator_next,
                        onClick = { isProcessing = true },
                        icon = GlowSharePillNextIcon,
                        isSmall = true,
                    )
                } else {
                    CircularProgressIndicator(
                        color = textPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(40.dp).padding(8.dp),
                    )
                }
            }

            // Preview canvas ≡ resolvedVideoPreviewHeight + aspectRatio(.fit) dentro del espacio restante
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ratio = format.aspectRatio.coerceAtLeast(0.01f)
                val fittedWidth = minOf(maxWidth, maxHeight * ratio)
                val fittedHeight = fittedWidth / ratio
                Box(
                    Modifier
                        .width(fittedWidth)
                        .height(fittedHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .border(1.dp, chromeStroke, RoundedCornerShape(20.dp)),
                ) {
                    StoryVideoPlayerView(
                        videoUri = currentVideo.uri,
                        videoGravity = StoryVideoGravity.RESIZE_ASPECT_FILL,
                        isMuted = volume <= 0f,
                        volume = volume,
                        playbackSpeed = speed.multiplier,
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
                    if (speed != PlaybackSpeed.NORMAL) {
                        Text(
                            stringResource(speed.labelRes),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                                .border(1.dp, chromeStroke, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    EditorCircleButton(
                        if (volume <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        tint = Color.White,
                        stroke = chromeStroke,
                        size = 38.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp),
                    ) { volume = if (volume > 0f) 0f else 1f }
                }
            }

            Column(Modifier.background(workspaceBg)) {
                if (videos.size > 1) {
                    LazyRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
                            val selected = index == selectedClipIndex
                            Box {
                                AsyncImage(
                                    model = video.thumbnailUri ?: video.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp, 80.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(placeholderFill)
                                        .border(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) selectionColor else chromeStroke,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable(enabled = !isProcessing) {
                                            selectedClipIndex = index
                                        },
                                )
                                Text(
                                    "${index + 1}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .background(Color.Black.copy(0.55f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    EditorControl(
                        Icons.Filled.Speed,
                        stringResource(R.string.video_editor_speed),
                        stringResource(speed.labelRes),
                        textPrimary,
                    ) { if (!isProcessing) showSpeed = true }
                    EditorControl(
                        format.icon,
                        stringResource(R.string.video_editor_format),
                        stringResource(format.displayNameRes),
                        textPrimary,
                    ) { if (!isProcessing) showFormat = true }
                    EditorControl(
                        Icons.Filled.Photo,
                        stringResource(R.string.video_editor_cover),
                        stringResource(
                            if (customCoverUri == null) R.string.video_editor_cover_auto
                            else R.string.video_editor_cover_manual,
                        ),
                        textPrimary,
                    ) { if (!isProcessing) showCoverPicker = true }
                    EditorVolumeControl(volume, textPrimary) { volume = it }
                }

                VideoEditorTrimTimeline(
                    duration = duration,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    playbackProgress = playbackProgress,
                    frames = timelineFrames,
                    selectionColor = selectionColor,
                    gripColor = gripColor,
                    dimmingColor = dimmingColor,
                    textPrimary = textPrimary,
                    placeholderFill = placeholderFill,
                    outlineStroke = chromeStroke,
                    enabled = !isProcessing,
                    showPlayhead = !isDragging || isScrubbingPlayhead,
                    onSelectionDelta = { delta ->
                        if (!isDragging) {
                            isDragging = true
                            HapticManager.shared.lightImpact()
                        }
                        val window = trimEnd - trimStart
                        val newStart = min(max(trimStart + delta, 0.0), max(duration - window, 0.0))
                        trimStart = newStart
                        trimEnd = newStart + window
                        previewTime = newStart
                        tick(newStart)
                    },
                    onLeadingDelta = { delta ->
                        if (!isDragging) {
                            isDragging = true
                            HapticManager.shared.lightImpact()
                        }
                        val newStart = min(max(0.0, trimStart + delta), trimEnd - MIN_CLIP_DURATION)
                        trimStart = newStart
                        previewTime = newStart
                        tick(newStart)
                    },
                    onTrailingDelta = { delta ->
                        if (!isDragging) {
                            isDragging = true
                            HapticManager.shared.lightImpact()
                        }
                        val newEnd = min(max(trimStart + MIN_CLIP_DURATION, trimEnd + delta), duration)
                        trimEnd = newEnd
                        previewTime = newEnd
                        tick(newEnd)
                    },
                    onPlayheadDelta = { delta ->
                        if (!isScrubbingPlayhead) {
                            isScrubbingPlayhead = true
                            HapticManager.shared.lightImpact()
                        }
                        val target = (playbackProgress + delta).coerceIn(trimStart, trimEnd)
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
                        .padding(bottom = 24.dp),
                )
            }
        }

        // Speed / format glass overlays
        AnimatedVisibility(
            visible = showSpeed || showFormat,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(if (isDark) 0.6f else 0.3f))
                    .clickable {
                        showSpeed = false
                        showFormat = false
                    },
            )
        }
        AnimatedVisibility(
            visible = showSpeed,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            EditorGlassPickerSheet(
                title = stringResource(R.string.video_editor_speed),
                workspaceBg = workspaceBg,
                textPrimary = textPrimary,
                chromeStroke = chromeStroke,
                selectionColor = selectionColor,
                onCancel = { showSpeed = false },
                onDone = { showSpeed = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaybackSpeed.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { option ->
                                val selected = option == speed
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) selectionColor.copy(0.18f)
                                            else placeholderFill,
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) selectionColor else chromeStroke,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { speed = option }
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(option.labelRes),
                                        color = textPrimary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showFormat,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            EditorGlassPickerSheet(
                title = stringResource(R.string.video_editor_format),
                workspaceBg = workspaceBg,
                textPrimary = textPrimary,
                chromeStroke = chromeStroke,
                selectionColor = selectionColor,
                onCancel = { showFormat = false },
                onDone = { showFormat = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFormat.entries.forEach { option ->
                        val selected = option == format
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) selectionColor.copy(0.18f) else placeholderFill,
                                )
                                .border(
                                    1.dp,
                                    if (selected) selectionColor else chromeStroke,
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { format = option }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(option.icon, null, tint = textPrimary, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(option.displayNameRes),
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${stringResource(option.ratioLabelRes)} • ${option.targetWidth}x${option.targetHeight}",
                                    color = textPrimary.copy(0.55f),
                                    fontSize = 12.sp,
                                )
                            }
                            if (selected) {
                                Icon(Icons.Filled.Check, null, tint = selectionColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        if (isProcessing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 40.dp)
                        .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { processingProgress },
                            color = Color.White,
                            trackColor = Color.White.copy(0.2f),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(80.dp),
                        )
                        Text(
                            "${(processingProgress * 100).roundToInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(processingMessage, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            stringResource(R.string.video_editor_optimizing),
                            color = Color.White.copy(0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCoverPicker) {
        VideoThumbnailPicker(
            videoUri = currentVideo.uri,
            initialDuration = duration,
            workspaceBg = workspaceBg,
            selectionColor = selectionColor,
            textPrimary = textPrimary,
            chromeStroke = chromeStroke,
            onDismiss = { showCoverPicker = false },
            onSelect = { bitmap ->
                customCoverUri = persistVideoThumbnail(bitmap, "video_cover")
                showCoverPicker = false
            },
        )
    }

    processError?.let { message ->
        AlertDialog(
            onDismissRequest = { processError = null },
            title = { Text(stringResource(R.string.video_editor_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { processError = null }) {
                    Text(stringResource(R.string.video_editor_ok))
                }
            },
        )
    }
}

@Composable
private fun EditorGlassPickerSheet(
    title: String,
    workspaceBg: Color,
    textPrimary: Color,
    chromeStroke: Color,
    selectionColor: Color,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 24.dp)
            .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
            .border(1.dp, chromeStroke, RoundedCornerShape(24.dp))
            .background(workspaceBg.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
            .padding(16.dp)
            .clickable(enabled = false) {},
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.video_editor_cancel), color = textPrimary.copy(0.65f))
            }
            Text(title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.video_editor_done), color = selectionColor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun EditorCircleButton(
    icon: ImageVector,
    tint: Color,
    stroke: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    onClick: () -> Unit,
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

@Composable
private fun EditorControl(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textPrimary: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Icon(icon, null, tint = textPrimary, modifier = Modifier.size(22.dp))
        Text(title, color = textPrimary, fontSize = 11.sp)
        Text(subtitle, color = textPrimary.copy(0.55f), fontSize = 9.sp)
    }
}

@Composable
private fun EditorVolumeControl(
    volume: Float,
    textPrimary: Color,
    onChange: (Float) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
        Icon(
            if (volume <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            null,
            tint = textPrimary,
            modifier = Modifier
                .size(22.dp)
                .clickable { onChange(if (volume > 0f) 0f else 1f) },
        )
        Slider(value = volume, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.height(24.dp))
        Text("${(volume * 100).roundToInt()}%", color = textPrimary.copy(0.55f), fontSize = 9.sp)
    }
}

@Composable
private fun VideoEditorTrimTimeline(
    duration: Double,
    trimStart: Double,
    trimEnd: Double,
    playbackProgress: Double,
    frames: List<Bitmap>,
    selectionColor: Color,
    gripColor: Color,
    dimmingColor: Color,
    textPrimary: Color,
    placeholderFill: Color,
    outlineStroke: Color,
    enabled: Boolean,
    showPlayhead: Boolean,
    onSelectionDelta: (Double) -> Unit,
    onLeadingDelta: (Double) -> Unit,
    onTrailingDelta: (Double) -> Unit,
    onPlayheadDelta: (Double) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimDuration = (trimEnd - trimStart).coerceAtLeast(MIN_CLIP_DURATION)
    val safeDuration = duration.coerceAtLeast(0.1)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatEditorTime(max(trimStart, playbackProgress)),
                color = textPrimary,
                fontSize = 12.sp,
            )
            Text(
                stringResource(
                    R.string.video_editor_duration_of,
                    formatEditorTime(trimDuration),
                    formatEditorTime(duration),
                ),
                color = textPrimary.copy(0.6f),
                fontSize = 11.sp,
            )
            Text(formatEditorTime(trimEnd), color = textPrimary, fontSize = 12.sp)
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 6.dp),
        ) {
            val density = LocalDensity.current
            val totalWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            fun secondsDelta(dx: Float): Double = (dx / totalWidth) * duration
            val startPx = (totalWidth * (trimStart / safeDuration).toFloat()).coerceAtLeast(0f)
            val windowPx = max(
                with(density) { 44.dp.toPx() },
                totalWidth * (trimDuration / safeDuration).toFloat(),
            )
            val clampedStart = min(startPx, max(0f, totalWidth - windowPx))
            val endPx = min(totalWidth, clampedStart + windowPx)
            val needleX = (totalWidth * (playbackProgress / safeDuration).toFloat())
                .coerceIn(clampedStart, endPx - with(density) { 3.dp.toPx() })
            val handleHalf = with(density) { 7.dp.toPx() }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                if (frames.isEmpty()) {
                    repeat(10) {
                        Box(Modifier.weight(1f).fillMaxSize().background(placeholderFill))
                    }
                } else {
                    frames.forEach { frame ->
                        Image(
                            frame.asImageBitmap(),
                            null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }

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

            Box(
                Modifier
                    .offset { IntOffset(clampedStart.roundToInt(), with(density) { 2.dp.roundToPx() }) }
                    .width(with(density) { windowPx.toDp() })
                    .height(48.dp)
                    .border(4.dp, selectionColor, RoundedCornerShape(11.dp))
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(totalWidth, duration) {
                                detectDragGestures(
                                    onDragEnd = onDragFinished,
                                    onDragCancel = onDragFinished,
                                ) { change, drag ->
                                    change.consume()
                                    onSelectionDelta(secondsDelta(drag.x))
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )

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
                        .then(
                            if (enabled) {
                                Modifier.pointerInput(totalWidth, duration) {
                                    detectDragGestures(
                                        onDragEnd = onDragFinished,
                                        onDragCancel = onDragFinished,
                                    ) { change, drag ->
                                        change.consume()
                                        onPlayheadDelta(secondsDelta(drag.x))
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
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

            EditorTrimHandle(
                gripColor = gripColor,
                selectionColor = selectionColor,
                enabled = enabled,
                modifier = Modifier.offset {
                    IntOffset(max(0, (clampedStart - handleHalf).roundToInt()), 0)
                },
                onMove = { dx -> onLeadingDelta(secondsDelta(dx)) },
                onFinished = onDragFinished,
            )
            EditorTrimHandle(
                gripColor = gripColor,
                selectionColor = selectionColor,
                enabled = enabled,
                modifier = Modifier.offset {
                    IntOffset(
                        min(
                            (totalWidth - with(density) { 14.dp.toPx() }).roundToInt(),
                            (endPx - handleHalf).roundToInt(),
                        ),
                        0,
                    )
                },
                onMove = { dx -> onTrailingDelta(secondsDelta(dx)) },
                onFinished = onDragFinished,
            )
        }
    }
}

@Composable
private fun EditorTrimHandle(
    gripColor: Color,
    selectionColor: Color,
    enabled: Boolean,
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
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = onFinished,
                            onDragCancel = onFinished,
                        ) { change, drag ->
                            change.consume()
                            onMove(drag.x)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(Modifier.size(3.dp).background(gripColor.copy(0.65f), CircleShape))
            }
        }
    }
}

/** Port de `ThumbnailPickerView` (full-screen cover). */
@Composable
private fun VideoThumbnailPicker(
    videoUri: Uri,
    initialDuration: Double,
    workspaceBg: Color,
    selectionColor: Color,
    textPrimary: Color,
    chromeStroke: Color,
    onDismiss: () -> Unit,
    onSelect: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var duration by remember { mutableDoubleStateOf(initialDuration) }
    var selectedTime by remember { mutableDoubleStateOf(0.0) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var timelineFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(videoUri) {
        duration = runCatching { StoryVideoProcessingService.duration(videoUri) }.getOrDefault(initialDuration)
    }
    LaunchedEffect(videoUri, selectedTime) {
        frame = withContext(Dispatchers.IO) { extractVideoFrame(context, videoUri, selectedTime) }
    }
    LaunchedEffect(videoUri, duration) {
        timelineFrames = withContext(Dispatchers.IO) {
            extractVideoTimelineFrames(videoUri, duration, 10)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(workspaceBg)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel), color = textPrimary.copy(0.55f))
                }
                Text(
                    stringResource(R.string.video_editor_choose_cover),
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { frame?.let(onSelect) }) {
                    Text(stringResource(R.string.common_done), color = selectionColor, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(0.1f))
                        .border(1.dp, chromeStroke, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    frame?.let {
                        Image(
                            it.asImageBitmap(),
                            null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                        )
                    } ?: CircularProgressIndicator(color = selectionColor)
                }
            }

            Text(
                stringResource(R.string.video_editor_cover_instructions),
                color = textPrimary.copy(0.55f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Gray.copy(0.1f)),
                ) {
                    if (timelineFrames.isEmpty()) {
                        Box(Modifier.fillMaxSize().background(Color.Gray.copy(0.1f)))
                    } else {
                        timelineFrames.forEach { thumb ->
                            Image(
                                thumb.asImageBitmap(),
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
                Slider(
                    value = selectedTime.toFloat(),
                    onValueChange = { selectedTime = it.toDouble() },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(0.1f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Text(
                formatEditorTime(selectedTime),
                color = selectionColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 40.dp),
            )
        }
    }
}

private fun inferVideoFormat(aspect: CreatorAspectRatio) = when (aspect) {
    CreatorAspectRatio.LANDSCAPE -> VideoFormat.LANDSCAPE
    CreatorAspectRatio.SQUARE, CreatorAspectRatio.PORTRAIT -> VideoFormat.SQUARE
    CreatorAspectRatio.NINE_BY_SIXTEEN -> VideoFormat.REELS
}

private fun formatEditorTime(seconds: Double): String {
    val total = max(0, seconds.toInt())
    return "%d:%02d".format(total / 60, total % 60)
}

// region Processing (≡ processVideoWithThumbnail + helpers)

private suspend fun processVideoWithThumbnail(
    context: Context,
    videoUri: Uri,
    format: VideoFormat,
    customCoverUri: Uri?,
): ProcessedVideoData {
    val currentSize = videoDisplaySize(context, videoUri)
        ?: throw IllegalStateException("no video track")
    val fullDuration = StoryVideoProcessingService.duration(videoUri)
    val targetSize = calculateOptimalSize(currentSize, format)
    val needsCompression = shouldCompress(currentSize, targetSize)

    val thumbnailUri = if (customCoverUri != null) {
        customCoverUri
    } else {
        val thumbTime = min(1.0, fullDuration / 2.0)
        val (maxW, maxH) = format.thumbnailMaxSize()
        val bitmap = extractScaledFrame(context, videoUri, thumbTime, maxW, maxH)
            ?: throw IllegalStateException("thumbnail failed")
        persistVideoThumbnail(bitmap, "thumbnail")
            ?: throw IllegalStateException("thumbnail persist failed")
    }

    val finalUri = if (needsCompression) {
        compressVideo(context, videoUri, targetSize.first.roundToInt(), targetSize.second.roundToInt())
    } else {
        videoUri
    }

    val fileSize = fileSizeBytes(context, finalUri) ?: 0L
    return ProcessedVideoData(
        compressedVideoUri = finalUri,
        thumbnailUri = thumbnailUri,
        duration = fullDuration,
        fileSize = fileSize,
        resolutionWidth = targetSize.first.roundToInt(),
        resolutionHeight = targetSize.second.roundToInt(),
    )
}

/** ≡ `calculateOptimalSize`. */
private fun calculateOptimalSize(
    currentSize: Pair<Float, Float>,
    targetFormat: VideoFormat,
): Pair<Float, Float> {
    val targetSize = targetFormat.targetWidth.toFloat() to targetFormat.targetHeight.toFloat()
    val currentAspect = currentSize.first / currentSize.second
    val targetAspect = targetSize.first / targetSize.second
    val tolerance = 0.1f

    if (abs(currentAspect - targetAspect) < tolerance) {
        val maxDimension = if (targetFormat == VideoFormat.LANDSCAPE) 1920f else 1080f
        return if (max(currentSize.first, currentSize.second) > maxDimension * 1.2f) {
            calculateOptimalSizePreservingRatio(currentSize, maxDimension)
        } else {
            currentSize
        }
    }

    if (abs(currentAspect - targetAspect) > 0.3f) {
        return calculateOptimalSizePreservingRatio(currentSize, 1080f)
    }
    return targetSize
}

private fun calculateOptimalSizePreservingRatio(
    currentSize: Pair<Float, Float>,
    maxDimension: Float,
): Pair<Float, Float> {
    val ratio = currentSize.first / currentSize.second
    return if (currentSize.first > currentSize.second) {
        val width = min(currentSize.first, maxDimension)
        width to (width / ratio)
    } else {
        val height = min(currentSize.second, maxDimension)
        (height * ratio) to height
    }
}

/** ≡ `shouldCompress` (pixelRatio > 1.2). */
private fun shouldCompress(
    currentSize: Pair<Float, Float>,
    targetSize: Pair<Float, Float>,
): Boolean {
    if (currentSize == targetSize) return false
    val currentPixels = currentSize.first * currentSize.second
    val targetPixels = targetSize.first * targetSize.second
    return (currentPixels / targetPixels) > 1.2f
}

/** ≡ `compressVideo` letterbox via Media3 `Presentation.createForWidthAndHeight` + SCALE_TO_FIT. */
private suspend fun compressVideo(
    context: Context,
    input: Uri,
    targetWidth: Int,
    targetHeight: Int,
): Uri = suspendCancellableCoroutine { cont ->
    val output = File(context.cacheDir, "compressed_${UUID.randomUUID()}.mp4")
    val edited = EditedMediaItem.Builder(MediaItem.fromUri(input))
        .setEffects(
            Effects(
                emptyList(),
                listOf(
                    Presentation.createForWidthAndHeight(
                        targetWidth,
                        targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT,
                    ),
                ),
            ),
        )
        .build()
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (cont.isActive) cont.resume(Uri.fromFile(output))
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                output.delete()
                if (cont.isActive) cont.resumeWithException(exportException)
            }
        })
        .build()
    transformer.start(edited, output.absolutePath)
    cont.invokeOnCancellation {
        runCatching { transformer.cancel() }
        output.delete()
    }
}

private fun videoDisplaySize(context: Context, uri: Uri): Pair<Float, Float>? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toFloatOrNull() ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toFloatOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) height to width else width to height
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun fileSizeBytes(context: Context, uri: Uri): Long? = runCatching {
    when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.length()
        else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }
}.getOrNull()

private fun extractVideoTimelineFrames(uri: Uri, duration: Double, count: Int): List<Bitmap> {
    val context = com.moments.android.MomentsApplication.instance ?: return emptyList()
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        (0 until count).mapNotNull { index ->
            val seconds = duration.coerceAtLeast(0.1) * index / (count - 1).coerceAtLeast(1)
            extractScaledFrame(retriever, seconds, 160, 284)
        }
    } catch (_: Exception) {
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}

private fun extractVideoFrame(context: Context, uri: Uri, time: Double): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        extractScaledFrame(retriever, time, 540, 960)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun extractScaledFrame(
    context: Context,
    uri: Uri,
    time: Double,
    maxW: Int,
    maxH: Int,
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        extractScaledFrame(retriever, time, maxW, maxH)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun extractScaledFrame(
    retriever: MediaMetadataRetriever,
    time: Double,
    maxW: Int,
    maxH: Int,
): Bitmap? {
    val timeUs = (time.coerceAtLeast(0.0) * 1_000_000L).toLong()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        retriever.getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            maxW,
            maxH,
        )
    } else {
        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }
}

private fun persistVideoThumbnail(bitmap: Bitmap, prefix: String): Uri? = runCatching {
    val context = com.moments.android.MomentsApplication.instance ?: return@runCatching null
    val output = File(context.cacheDir, "${prefix}_${UUID.randomUUID()}.jpg")
    FileOutputStream(output).use { bitmap.compress(CompressFormat.JPEG, 80, it) }
    Uri.fromFile(output)
}.getOrNull()

// endregion
