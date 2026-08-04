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

    /**
     * ≡ `stickerForDisplay`: escala guardada × (ancho del canvas / referencia).
     *
     * iOS usa **points** (SwiftUI). En Android el canvas suele medirse en **px**;
     * hay que pasar a **dp** (≈ points) antes del factor 375. Si se usa px,
     * historias iOS se ven enormes en Android y las de Android enanas en iOS.
     *
     * La referencia **375** es el contrato Firestore compartido
     * (`referenceContentWidth`), no el ancho de un dispositivo concreto.
     */
    fun stickerDisplayScale(scale: Double, containerWidthPx: Float, density: Float): Float {
        val widthDp = canvasWidthDp(containerWidthPx, density)
        return scale.toFloat() * widthDp.coerceAtLeast(1f) / STORY_STICKER_REFERENCE_WIDTH
    }

    /**
     * ≡ iOS `normalizedScale = editorScale * (375 / contentRect.width)` donde
     * `contentRect.width` está en points.
     */
    fun normalizeStickerScaleForFirestore(
        editorScale: Double,
        containerWidthPx: Float,
        density: Float,
    ): Double {
        val widthDp = canvasWidthDp(containerWidthPx, density).toDouble().coerceAtLeast(1.0)
        return editorScale * (STORY_STICKER_REFERENCE_WIDTH.toDouble() / widthDp)
    }

    /** Ancho del canvas en unidades de layout iOS (points ≈ dp). */
    fun canvasWidthDp(containerWidthPx: Float, density: Float): Float =
        containerWidthPx / density.coerceAtLeast(0.01f)

    /** Referencia de escala en Firestore (iOS `referenceContentWidth = 375`). */
    const val STORY_STICKER_REFERENCE_WIDTH = 375f

    /** @deprecated Usar [STORY_STICKER_REFERENCE_WIDTH]; el nombre “PX” era engañoso. */
    const val STORY_STICKER_REFERENCE_WIDTH_PX = STORY_STICKER_REFERENCE_WIDTH
}
