package com.moments.android.views.story.storyviewer

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.moments.android.models.Point

/** Port de los helpers estáticos de `StoryViewerLayoutHelpers.swift`. */
object StoryViewerLayoutHelpers {

    /** ≡ `resolvedVideoPresentationSize(naturalSize:preferredTransform:)` vía rotación metadata. */
    fun resolvedVideoPresentationSize(naturalWidth: Float, naturalHeight: Float, rotationDegrees: Int): Size {
        return if (rotationDegrees == 90 || rotationDegrees == 270) {
            Size(naturalHeight, naturalWidth)
        } else {
            Size(naturalWidth, naturalHeight)
        }
    }

    fun detectVideoAspectRatio(path: String): String? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val resolved = resolvedVideoPresentationSize(width.toFloat(), height.toFloat(), rotation)
            "${resolved.width.toInt()}:${resolved.height.toInt()}"
        }
    }.getOrNull()

    fun detectImageAspectRatio(path: String): String? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            "${options.outWidth}:${options.outHeight}"
        } else {
            null
        }
    }.getOrNull()

    fun isHorizontalAspectRatio(aspectRatio: String?): Boolean {
        val parts = aspectRatio?.split(':') ?: return false
        if (parts.size != 2) return false
        val width = parts[0].toIntOrNull() ?: return false
        val height = parts[1].toIntOrNull() ?: return false
        return width > height
    }

    fun parseAspectRatio(aspectRatio: String?): Float? {
        val parts = aspectRatio?.split(':') ?: return null
        if (parts.size != 2) return null
        val width = parts[0].toFloatOrNull() ?: return null
        val height = parts[1].toFloatOrNull() ?: return null
        if (width <= 0f || height <= 0f) return null
        return width / height
    }

    /**
     * ≡ `contentRect(containerSize:mediaAspectRatio:contentMode:)`.
     * [fit] = `ContentMode.fit`; `false` = fill.
     */
    fun contentRect(
        containerWidth: Float,
        containerHeight: Float,
        mediaAspectRatio: Float,
        fit: Boolean,
    ): Rect {
        val cw = containerWidth.coerceAtLeast(1f)
        val ch = containerHeight.coerceAtLeast(1f)
        val containerAspect = cw / ch
        val mediaIsWider = mediaAspectRatio > containerAspect
        val width: Float
        val height: Float
        if (fit) {
            if (mediaIsWider) {
                width = cw
                height = cw / mediaAspectRatio.coerceAtLeast(0.0001f)
            } else {
                height = ch
                width = ch * mediaAspectRatio
            }
        } else {
            if (mediaIsWider) {
                height = ch
                width = ch * mediaAspectRatio
            } else {
                width = cw
                height = cw / mediaAspectRatio.coerceAtLeast(0.0001f)
            }
        }
        return Rect(
            left = (cw - width) / 2f,
            top = (ch - height) / 2f,
            right = (cw + width) / 2f,
            bottom = (ch + height) / 2f,
        )
    }

    fun stickerDisplayPosition(
        position: Point,
        containerWidth: Float,
        containerHeight: Float,
    ): Pair<Float, Float> =
        position.x.toFloat() * containerWidth.coerceAtLeast(1f) to
            position.y.toFloat() * containerHeight.coerceAtLeast(1f)

    /** ≡ `stickerForDisplay` (solo escala × width/375). */
    fun stickerDisplayScale(scale: Double, containerWidth: Float): Float =
        scale.toFloat() * containerWidth.coerceAtLeast(1f) / 375f
}
