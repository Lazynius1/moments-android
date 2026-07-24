package com.moments.android.views.creator.creatoruikit

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorMedia
import java.io.File
import java.io.FileInputStream

/**
 * Android photo-picker counterpart of `StoryMediaPicker.swift`.
 * The system picker owns its own scoped photo access, so no broad gallery permission is needed.
 */
@Composable
fun StoryMediaPicker(
    isPresented: Boolean,
    onSelect: (Uri?) -> Unit,
) {
    val latestOnSelect = rememberUpdatedState(onSelect)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        latestOnSelect.value(uri)
    }

    LaunchedEffect(isPresented) {
        if (isPresented) {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        }
    }
}

/** Shared media decoding for the one-item Story picker and Story camera. */
fun storyMediaFromUri(context: Context, uri: Uri): CreatorMedia? {
    val type = context.contentResolver.getType(uri).orEmpty()
    val isVideo = type.startsWith("video") || uri.toString().endsWith(".mp4", ignoreCase = true)
    return if (isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000.0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toFloatOrNull()?.coerceAtLeast(1f)
            val ratio = if (width != null && height != null) {
                CreatorAspectRatio.fromRatio(width / height)
            } else {
                CreatorAspectRatio.NINE_BY_SIXTEEN
            }
            CreatorMedia(
                uri = uri,
                isVideo = true,
                durationSeconds = duration,
                aspectRatio = ratio,
                recommendedAspectRatio = ratio,
            )
        } catch (_: Exception) {
            CreatorMedia(uri = uri, isVideo = true, aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN)
        } finally {
            runCatching { retriever.release() }
        }
    } else {
        val ratio = runCatching {
            val stream = when (uri.scheme) {
                "file" -> uri.path?.let(::FileInputStream)
                else -> context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                val width = options.outWidth.toFloat()
                val height = options.outHeight.toFloat().coerceAtLeast(1f)
                if (width <= 0f) CreatorAspectRatio.NINE_BY_SIXTEEN else CreatorAspectRatio.fromRatio(width / height)
            } ?: CreatorAspectRatio.NINE_BY_SIXTEEN
        }.getOrDefault(CreatorAspectRatio.NINE_BY_SIXTEEN)
        CreatorMedia(
            uri = uri,
            isVideo = false,
            aspectRatio = ratio,
            recommendedAspectRatio = ratio,
        )
    }
}
