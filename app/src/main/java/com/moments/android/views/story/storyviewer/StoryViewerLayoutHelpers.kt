package com.moments.android.views.story.storyviewer

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.geometry.Rect
import com.moments.android.models.Point

/** Port de los helpers estáticos de `StoryViewerLayoutHelpers.swift`. */
object StoryViewerLayoutHelpers {
    fun detectVideoAspectRatio(path: String): String? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) "$height:$width" else "$width:$height"
        }
    }.getOrNull()

    fun detectImageAspectRatio(path: String): String? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0) "${options.outWidth}:${options.outHeight}" else null
    }.getOrNull()

    fun isHorizontalAspectRatio(aspectRatio: String?): Boolean = parseAspectRatio(aspectRatio)?.let { it > 1f } ?: false

    fun parseAspectRatio(aspectRatio: String?): Float? {
        val parts = aspectRatio?.split(':') ?: return null
        val width = parts.getOrNull(0)?.toFloatOrNull() ?: return null
        val height = parts.getOrNull(1)?.toFloatOrNull() ?: return null
        return (width / height).takeIf { width > 0f && height > 0f }
    }

    fun contentRect(containerWidth: Float, containerHeight: Float, mediaAspectRatio: Float, fit: Boolean): Rect {
        val cw = containerWidth.coerceAtLeast(1f); val ch = containerHeight.coerceAtLeast(1f)
        val wider = mediaAspectRatio > cw / ch
        val width: Float; val height: Float
        if (fit == wider) { width = cw; height = cw / mediaAspectRatio.coerceAtLeast(.0001f) }
        else { height = ch; width = ch * mediaAspectRatio }
        return Rect((cw - width) / 2f, (ch - height) / 2f, (cw + width) / 2f, (ch + height) / 2f)
    }

    fun stickerDisplayPosition(position: Point, containerWidth: Float, containerHeight: Float): Pair<Float, Float> =
        position.x.toFloat() * containerWidth.coerceAtLeast(1f) to position.y.toFloat() * containerHeight.coerceAtLeast(1f)

    fun stickerDisplayScale(scale: Double, containerWidth: Float): Float = scale.toFloat() * containerWidth.coerceAtLeast(1f) / 375f
}
