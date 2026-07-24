package com.moments.android.views.creator

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.storage.VideoCompressionPreset
import com.moments.android.services.storage.VideoCompressionService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class PlaybackSpeed(val labelRes: Int, val multiplier: Float) {
    SLOW(R.string.video_editor_speed_slow, .3f), NORMAL(R.string.video_editor_speed_normal, 1f), FAST(R.string.video_editor_speed_fast, 2f), VERY_FAST(R.string.video_editor_speed_very_fast, 3f),
}

enum class VideoFormat(
    val labelRes: Int,
    val aspectRatio: Float,
    val creatorAspectRatio: CreatorAspectRatio,
) {
    REELS(R.string.video_editor_format_reels, 9f / 16f, CreatorAspectRatio.NINE_BY_SIXTEEN),
    SQUARE(R.string.video_editor_format_square, 1f, CreatorAspectRatio.SQUARE),
    LANDSCAPE(R.string.video_editor_format_landscape, 16f / 9f, CreatorAspectRatio.LANDSCAPE),
}

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
    val videos = selectedMediaItems.filter { it.isVideo }
    if (videos.isEmpty()) {
        onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION)
        return
    }
    var selectedClipIndex by remember { mutableIntStateOf(0) }
    val currentVideo = videos.getOrElse(selectedClipIndex.coerceIn(0, videos.lastIndex)) { videos.first() }
    var duration by remember(currentVideo.id) { mutableDoubleStateOf(currentVideo.durationSeconds ?: 60.0) }
    var trimStart by remember(currentVideo.id) { mutableDoubleStateOf(0.0) }
    var trimEnd by remember(currentVideo.id) { mutableDoubleStateOf(duration) }
    var speed by remember { mutableStateOf(PlaybackSpeed.NORMAL) }
    var format by remember { mutableStateOf(inferVideoFormat(currentVideo.aspectRatio)) }
    var volume by remember { mutableStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    var customCoverUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var processingMessage by remember { mutableStateOf(context.getString(R.string.video_editor_processing_start)) }
    var processError by remember { mutableStateOf<String?>(null) }
    var timelineFrames by remember(currentVideo.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var playbackProgress by remember(currentVideo.id) { mutableDoubleStateOf(0.0) }

    LaunchedEffect(currentVideo.uri) {
        runCatching { StoryVideoProcessingService.duration(currentVideo.uri) }.getOrNull()?.let {
            duration = min(it, CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS)
            trimEnd = duration
            format = inferVideoFormat(currentVideo.aspectRatio)
        }
    }
    LaunchedEffect(currentVideo.uri, duration) {
        timelineFrames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            extractVideoTimelineFrames(currentVideo.uri, duration, 20)
        }
    }

    LaunchedEffect(isProcessing) {
        if (!isProcessing) return@LaunchedEffect
        runCatching {
            val videoCount = selectedMediaItems.count { it.isVideo }.coerceAtLeast(1)
            var completed = 0
            val updated = selectedMediaItems.map { media ->
                if (!media.isVideo) media else {
                    processingMessage = context.getString(R.string.video_editor_processing_video, completed + 1, videoCount)
                    processingProgress = completed.toFloat() / videoCount
                    val output = VideoCompressionService.prepareVideoForUpload(media.uri, VideoCompressionPreset.MOMENT)
                    completed += 1
                    processingProgress = completed.toFloat() / videoCount
                    media.copy(
                        uri = output,
                        durationSeconds = media.durationSeconds ?: duration,
                        aspectRatio = format.creatorAspectRatio,
                        recommendedAspectRatio = format.creatorAspectRatio,
                        hasEdits = true,
                        thumbnailUri = customCoverUri ?: media.thumbnailUri ?: generateVideoThumbnailUri(media.uri),
                    )
                }
            }
            processingMessage = context.getString(R.string.video_editor_processing_finish)
            onSelectedMediaItemsChange(updated)
        }.onSuccess {
            isProcessing = false
            onCurrentFlowChange(CreatorFlow.CAPTION_AND_DETAILS)
        }.onFailure {
            isProcessing = false
            processError = it.message ?: context.getString(R.string.video_editor_error_processing)
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF0B1215))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorCircleButton(Icons.AutoMirrored.Filled.ArrowBack, enabled = !isProcessing) {
                    onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION)
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.video_editor_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                EditorCircleButton(Icons.Filled.Check, enabled = !isProcessing) { isProcessing = true }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(format.aspectRatio)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black),
                ) {
                    StoryVideoPlayerView(
                        videoUri = currentVideo.uri,
                        videoGravity = StoryVideoGravity.RESIZE_ASPECT_FILL,
                        isMuted = volume <= 0f,
                        volume = volume,
                        playbackSpeed = speed.multiplier,
                        trimStart = trimStart,
                        trimEnd = trimEnd,
                        onPlayProgress = { progress -> playbackProgress = progress },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (speed != PlaybackSpeed.NORMAL) {
                    Text(stringResource(speed.labelRes), color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
                }
                EditorCircleButton(
                    if (volume <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                ) { volume = if (volume > 0f) 0f else 1f }
            }

            if (videos.size > 1) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
                        AsyncImage(
                            model = video.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp, 80.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(.1f))
                                .clickable(enabled = !isProcessing) { selectedClipIndex = index },
                        )
                    }
                }
            }

            VideoEditorTrimTimeline(
                duration = duration,
                trimStart = trimStart,
                trimEnd = trimEnd,
                playbackProgress = playbackProgress,
                frames = timelineFrames,
                enabled = !isProcessing,
                onRangeChange = { start, end ->
                    trimStart = start
                    trimEnd = end
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                EditorControl(Icons.Filled.Speed, stringResource(R.string.video_editor_speed), stringResource(speed.labelRes)) { showSpeed = true }
                EditorControl(Icons.Filled.CropPortrait, stringResource(R.string.video_editor_format), stringResource(format.labelRes)) { showFormat = true }
                EditorControl(Icons.Filled.Photo, stringResource(R.string.video_editor_cover), stringResource(if (customCoverUri == null) R.string.video_editor_cover_auto else R.string.video_editor_cover_manual)) { showCoverPicker = true }
                EditorVolumeControl(volume = volume, onChange = { volume = it })
            }
        }
        if (isProcessing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(.72f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(progress = { processingProgress }, color = Color.White)
                    Text("${(processingProgress * 100).roundToInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(processingMessage, color = Color.White.copy(.8f))
                }
            }
        }
    }
    if (showSpeed) EditorChoiceDialog(stringResource(R.string.video_editor_speed), PlaybackSpeed.entries, speed, { context.getString(it.labelRes) }, {
        speed = it; showSpeed = false
    }, { showSpeed = false })
    if (showFormat) EditorChoiceDialog(stringResource(R.string.video_editor_format), VideoFormat.entries, format, { context.getString(it.labelRes) }, {
        format = it; showFormat = false
    }, { showFormat = false })
    if (showCoverPicker) VideoThumbnailPicker(
        videoUri = currentVideo.uri,
        initialDuration = duration,
        onDismiss = { showCoverPicker = false },
        onSelect = { bitmap ->
            customCoverUri = persistVideoThumbnail(bitmap, "video_cover")
            showCoverPicker = false
        },
    )
    processError?.let { message ->
        AlertDialog(onDismissRequest = { processError = null }, title = { Text(stringResource(R.string.video_editor_error_title)) }, text = { Text(message) }, confirmButton = {
            Text(stringResource(R.string.common_understood), modifier = Modifier.clickable { processError = null }.padding(16.dp))
        })
    }
}

@Composable
private fun EditorCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(42.dp).momentsChromeGlass(CircleShape, interactive = true)) {
        Icon(icon, null, tint = Color.White)
    }
}

@Composable
private fun EditorControl(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(title, color = Color.White, fontSize = 11.sp)
        Text(subtitle, color = Color.White.copy(.55f), fontSize = 9.sp)
    }
}

@Composable
private fun EditorVolumeControl(volume: Float, onChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
        Icon(if (volume <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(22.dp).clickable { onChange(if (volume > 0f) 0f else 1f) })
        Slider(value = volume, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.height(24.dp))
        Text("${(volume * 100).roundToInt()}%", color = Color.White.copy(.55f), fontSize = 9.sp)
    }
}

@Composable
private fun <T> EditorChoiceDialog(title: String, options: Iterable<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column { options.forEach { option -> Text(label(option), fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(14.dp)) } }
    }, confirmButton = {})
}

private fun inferVideoFormat(aspect: CreatorAspectRatio) = when (aspect) {
    CreatorAspectRatio.LANDSCAPE -> VideoFormat.LANDSCAPE
    CreatorAspectRatio.SQUARE, CreatorAspectRatio.PORTRAIT -> VideoFormat.SQUARE
    CreatorAspectRatio.NINE_BY_SIXTEEN -> VideoFormat.REELS
}

private fun formatEditorTime(seconds: Double): String = "%d:%02d".format(seconds.toInt().coerceAtLeast(0) / 60, seconds.toInt().coerceAtLeast(0) % 60)

/**
 * Timeline section of `SocialVideoEditorView`: thumbnail rail, trim window and playhead.
 * Material's range slider supplies the same two independent bounded handles as Swift's
 * custom drag recognizers while remaining accessible on Android.
 */
@Composable
private fun VideoEditorTrimTimeline(
    duration: Double,
    trimStart: Double,
    trimEnd: Double,
    playbackProgress: Double,
    frames: List<Bitmap>,
    enabled: Boolean,
    onRangeChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeDuration = duration.coerceAtLeast(1.0)
    val minClip = 1f
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatEditorTime(max(trimStart, playbackProgress)), color = Color.White, fontSize = 12.sp)
            Text(stringResource(R.string.video_editor_duration_of, formatEditorTime(trimEnd - trimStart), formatEditorTime(duration)), color = Color.White.copy(.6f), fontSize = 11.sp)
            Text(formatEditorTime(trimEnd), color = Color.White, fontSize = 12.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp).padding(top = 6.dp)) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val playheadX = (widthPx * (playbackProgress / safeDuration).toFloat()).coerceIn(
                widthPx * (trimStart / safeDuration).toFloat(),
                widthPx * (trimEnd / safeDuration).toFloat(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(.08f)),
            ) {
                if (frames.isEmpty()) repeat(10) { Box(Modifier.weight(1f).fillMaxSize().background(Color.White.copy(.03f))) }
                else frames.forEach { frame ->
                    Image(frame.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize())
                }
            }
            Box(
                Modifier
                    .offset { IntOffset((playheadX - with(density) { 1.dp.toPx() }).roundToInt(), 0) }
                    .width(2.dp)
                    .height(48.dp)
                    .background(Color.White),
            )
            RangeSlider(
                value = trimStart.toFloat()..trimEnd.toFloat(),
                onValueChange = { range ->
                    val start = range.start.coerceIn(0f, safeDuration.toFloat() - minClip)
                    val end = range.endInclusive.coerceIn(start + minClip, safeDuration.toFloat())
                    onRangeChange(start.toDouble(), end.toDouble())
                },
                valueRange = 0f..safeDuration.toFloat(),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp).offset(y = (-4).dp),
            )
        }
    }
}

private fun extractVideoTimelineFrames(uri: Uri, duration: Double, count: Int): List<Bitmap> {
    val context = com.moments.android.MomentsApplication.instance ?: return emptyList()
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        (0 until count).mapNotNull { index ->
            retriever.getFrameAtTime((duration.coerceAtLeast(.1) * index / (count - 1).coerceAtLeast(1) * 1_000_000L).toLong())
        }
    } catch (_: Exception) {
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}

/** Android equivalent of `ThumbnailPickerView` in `VideoEditor.swift`. */
@Composable
private fun VideoThumbnailPicker(
    videoUri: Uri,
    initialDuration: Double,
    onDismiss: () -> Unit,
    onSelect: (Bitmap) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var duration by remember { mutableDoubleStateOf(initialDuration) }
    var selectedTime by remember { mutableDoubleStateOf(0.0) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var timelineFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(videoUri) {
        duration = runCatching { StoryVideoProcessingService.duration(videoUri) }.getOrDefault(initialDuration)
    }
    LaunchedEffect(videoUri, selectedTime) {
        frame = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            extractVideoFrame(context, videoUri, selectedTime)
        }
    }
    LaunchedEffect(videoUri, duration) {
        timelineFrames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            extractVideoTimelineFrames(videoUri, duration, 10)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_editor_choose_cover)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(260.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    frame?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    if (frame == null) CircularProgressIndicator()
                }
                Box(Modifier.fillMaxWidth().height(60.dp).padding(top = 8.dp)) {
                    Row(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.1f))) {
                        if (timelineFrames.isEmpty()) repeat(10) { Box(Modifier.weight(1f).fillMaxSize().background(Color.White.copy(.04f))) }
                        else timelineFrames.forEach { thumbnail -> Image(thumbnail.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize()) }
                    }
                    Slider(
                        value = selectedTime.toFloat(),
                        onValueChange = { selectedTime = it.toDouble() },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(.1f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Text(formatEditorTime(selectedTime))
            }
        },
        dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable(onClick = onDismiss).padding(16.dp)) },
        confirmButton = { Text(stringResource(R.string.common_done), modifier = Modifier.clickable { frame?.let(onSelect) }.padding(16.dp)) },
    )
}

private fun extractVideoFrame(context: android.content.Context, uri: Uri, time: Double): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime((time.coerceAtLeast(0.0) * 1_000_000L).toLong())
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun persistVideoThumbnail(bitmap: Bitmap, prefix: String): Uri? = runCatching {
    val context = com.moments.android.MomentsApplication.instance ?: return@runCatching null
    val output = File(context.cacheDir, "${prefix}_${UUID.randomUUID()}.jpg")
    FileOutputStream(output).use { bitmap.compress(CompressFormat.JPEG, 85, it) }
    Uri.fromFile(output)
}.getOrNull()

private fun generateVideoThumbnailUri(uri: Uri): Uri? = runCatching {
    persistVideoThumbnail(StoryVideoProcessingService.generateStoryThumbnail(uri), "video_thumbnail")
}.getOrNull()
