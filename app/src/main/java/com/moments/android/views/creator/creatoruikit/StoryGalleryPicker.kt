package com.moments.android.views.creator.creatoruikit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.StoryVideoMode
import com.moments.android.views.creator.StoryVideoProcessingService
import com.moments.android.views.creator.StoryVideoTrimEditorView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Port de `StoryGalleryPicker.swift`.
 * Un ítem; imagen/vídeo corto → select; largo → decisión split/trim; exceso → alert.
 */
@Composable
fun StoryGalleryPicker(
    isPresented: Boolean,
    onSelect: (CreatorMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickerPresented by remember { mutableStateOf(false) }
    var pendingLongVideo by remember { mutableStateOf<CreatorMedia?>(null) }
    var pendingThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var tooLongDuration by remember { mutableStateOf<Double?>(null) }
    var showingLongVideoDecision by remember { mutableStateOf(false) }
    var isTrimming by remember { mutableStateOf(false) }

    LaunchedEffect(isPresented) {
        if (isPresented) {
            delay(100) // ≡ presentMediaPickerSoon
            pickerPresented = true
        } else {
            pickerPresented = false
            pendingLongVideo = null
            pendingThumbnail = null
            tooLongDuration = null
            showingLongVideoDecision = false
            isTrimming = false
        }
    }

    StoryMediaPicker(isPresented = pickerPresented) { uri ->
        pickerPresented = false
        if (uri == null) {
            onDismiss()
            return@StoryMediaPicker
        }
        scope.launch {
            val media = withContext(Dispatchers.IO) {
                storyMediaFromUri(context, uri)?.copy(
                    aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                    recommendedAspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                )
            }
            if (media == null) {
                onDismiss()
                return@launch
            }
            if (!media.isVideo) {
                onSelect(media)
                return@launch
            }
            val duration = media.durationSeconds
                ?: withContext(Dispatchers.IO) {
                    runCatching { StoryVideoProcessingService.duration(media.uri) }.getOrDefault(0.0)
                }
            val withDuration = media.copy(durationSeconds = duration)
            val thumb = withContext(Dispatchers.IO) {
                runCatching {
                    StoryVideoProcessingService.generateStoryThumbnail(withDuration.uri, time = 0.1)
                }.getOrNull()
            }
            when {
                duration > StoryVideoProcessingService.maxAutoSplitDuration -> {
                    tooLongDuration = duration
                }
                duration <= StoryVideoProcessingService.maxStorySegmentDuration -> {
                    onSelect(withDuration)
                }
                else -> {
                    pendingLongVideo = withDuration
                    pendingThumbnail = thumb
                    delay(350)
                    showingLongVideoDecision = true
                }
            }
        }
    }

    tooLongDuration?.let { duration ->
        AlertDialog(
            onDismissRequest = {
                tooLongDuration = null
                pickerPresented = true
            },
            title = { Text(stringResource(R.string.story_video_too_long_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.story_video_too_long_message,
                        formatStoryDuration(duration),
                        formatStoryDuration(StoryVideoProcessingService.maxAutoSplitDuration),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tooLongDuration = null
                    pickerPresented = true
                }) {
                    Text(stringResource(R.string.common_understood))
                }
            },
        )
    }

    if (showingLongVideoDecision) {
        pendingLongVideo?.let { media ->
            val duration = media.durationSeconds ?: 0.0
            val partCount = ceil(duration / StoryVideoProcessingService.maxStorySegmentDuration).toInt()
                .coerceAtLeast(2)
            val canAutoSplit = duration <= StoryVideoProcessingService.maxAutoSplitDuration
            StoryLongVideoDecisionOverlay(
                duration = duration,
                partCount = partCount,
                canAutoSplit = canAutoSplit,
                thumbnail = pendingThumbnail,
                onConfirmSplit = {
                    showingLongVideoDecision = false
                    onSelect(
                        media.copy(
                            storyVideoMode = StoryVideoMode.AUTO_SPLIT,
                            durationSeconds = duration,
                        ),
                    )
                },
                onEdit = {
                    showingLongVideoDecision = false
                    isTrimming = true
                },
                onCancel = {
                    showingLongVideoDecision = false
                    pendingLongVideo = null
                    pendingThumbnail = null
                    pickerPresented = true
                },
            )
        }
    }

    if (isTrimming) {
        val media = pendingLongVideo ?: return
        StoryVideoTrimEditorView(
            videoUri = media.uri,
            duration = media.durationSeconds ?: 0.0,
            onCancel = {
                // ≡ onCancel trim → volver a decisión
                isTrimming = false
                showingLongVideoDecision = true
            },
            onComplete = { trimmed ->
                isTrimming = false
                onSelect(trimmed)
            },
        )
    }
}

/** Port de `StoryLongVideoDecisionOverlay` (StoryGalleryPicker.swift). */
@Composable
private fun StoryLongVideoDecisionOverlay(
    duration: Double,
    partCount: Int,
    canAutoSplit: Boolean,
    thumbnail: Bitmap?,
    onConfirmSplit: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(alpha = 0.86f)
    val secondary = if (isDark) Color.White.copy(0.72f) else Color.Black.copy(0.58f)
    val tertiary = if (isDark) Color.White.copy(0.62f) else Color.Black.copy(0.5f)
    val message = if (canAutoSplit) {
        stringResource(R.string.story_video_long_message, formatStoryDuration(duration), partCount)
    } else {
        stringResource(
            R.string.story_video_long_too_long_for_split,
            formatStoryDuration(duration),
            formatStoryDuration(StoryVideoProcessingService.maxAutoSplitDuration),
            StoryVideoProcessingService.maxAutoSplitPartCount,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.34f else 0.42f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .momentsChromeGlass(RoundedCornerShape(30.dp), interactive = false)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(62.dp)
                            .height(82.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        Modifier
                            .width(62.dp)
                            .height(82.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Gray.copy(0.3f)),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.story_video_long_title),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(message, color = secondary, fontSize = 13.sp)
                }
            }

            if (canAutoSplit) {
                Text(
                    stringResource(R.string.story_video_long_reveal_hint),
                    color = tertiary,
                    fontSize = 12.sp,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.common_cancel),
                    color = secondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.clickable(onClick = onEdit).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            null,
                            tint = primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.story_video_long_edit),
                            color = primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    if (canAutoSplit) {
                        Row(
                            Modifier.clickable(onClick = onConfirmSplit).padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.ContentCut,
                                null,
                                tint = primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                stringResource(R.string.story_video_long_confirm),
                                color = primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatStoryDuration(seconds: Double): String {
    val total = max(0, seconds.roundToInt())
    return "%d:%02d".format(total / 60, total % 60)
}
