package com.moments.android.views.creator.creatoruikit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.StoryVideoMode
import com.moments.android.views.creator.StoryVideoTrimEditorView
import kotlin.math.ceil

private const val MAX_STORY_SEGMENT_SECONDS = 60.0
private const val MAX_AUTO_SPLIT_SECONDS = CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS

/**
 * Port of `StoryGalleryPicker.swift`: one media item, direct images/short videos,
 * and the automatic-split decision for videos over one Story segment.
 */
@Composable
fun StoryGalleryPicker(
    isPresented: Boolean,
    onSelect: (CreatorMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pickerPresented by remember { mutableStateOf(isPresented) }
    var pendingLongVideo by remember { mutableStateOf<CreatorMedia?>(null) }
    var tooLongDuration by remember { mutableStateOf<Double?>(null) }
    var isTrimming by remember { mutableStateOf(false) }

    StoryMediaPicker(isPresented = pickerPresented) { uri ->
        pickerPresented = false
        if (uri == null) {
            onDismiss()
            return@StoryMediaPicker
        }
        val media = storyMediaFromUri(context, uri) ?: run {
            onDismiss()
            return@StoryMediaPicker
        }
        val duration = media.durationSeconds ?: 0.0
        when {
            !media.isVideo || duration <= MAX_STORY_SEGMENT_SECONDS -> onSelect(media)
            duration > MAX_AUTO_SPLIT_SECONDS -> tooLongDuration = duration
            else -> pendingLongVideo = media
        }
    }

    if (tooLongDuration != null) {
        AlertDialog(
            onDismissRequest = {
                tooLongDuration = null
                pickerPresented = true
            },
            title = { Text("Video too long") },
            text = {
                Text(
                    "${formatStoryDuration(tooLongDuration ?: 0.0)} exceeds the ${formatStoryDuration(MAX_AUTO_SPLIT_SECONDS)} Story limit.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    tooLongDuration = null
                    pickerPresented = true
                }) { Text("Understood") }
            },
        )
    }

    pendingLongVideo?.let { media ->
        StoryLongVideoDecisionOverlay(
            duration = media.durationSeconds ?: 0.0,
            onCancel = {
                pendingLongVideo = null
                pickerPresented = true
            },
            onEdit = {
                isTrimming = true
            },
            onConfirmSplit = {
                onSelect(media.copy(storyVideoMode = StoryVideoMode.AUTO_SPLIT))
            },
        )
    }

    if (isTrimming) {
        val media = pendingLongVideo ?: return
        StoryVideoTrimEditorView(
            videoUri = media.uri,
            duration = media.durationSeconds ?: 0.0,
            onCancel = { isTrimming = false },
            onComplete = { trimmed -> onSelect(trimmed) },
        )
    }
}

@Composable
private fun StoryLongVideoDecisionOverlay(
    duration: Double,
    onConfirmSplit: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(32.dp)) },
        title = { Text("Long video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${formatStoryDuration(duration)} will be published in ${ceil(duration / MAX_STORY_SEGMENT_SECONDS).toInt()} parts.",
                )
                Text(
                    "You can split it automatically or edit a one-minute clip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Edit")
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirmSplit) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Split")
            }
        },
    )
}

private fun formatStoryDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
