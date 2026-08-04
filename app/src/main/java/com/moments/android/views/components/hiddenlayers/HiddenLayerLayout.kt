package com.moments.android.views.components.hiddenlayers

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.views.creator.HiddenLayerDraft

/** Port 1:1 de `HiddenLayerLayout.swift`. */
object HiddenLayerLayout {
    const val imageAspectRatio = 1.26f
    const val textAspectRatio = 0.18f / 0.34f
    const val minimumPostAspectRatio = 0.8f
    const val maximumPostAspectRatio = 4f / 3f

    fun displayedPostAspectRatio(
        imageWidth: Float,
        imageHeight: Float,
        preferredAspectRatio: Float? = null,
    ): Float {
        val sourceRatio = preferredAspectRatio?.takeIf { it.isFinite() && it > 0f }
            ?: (imageWidth / maxOf(imageHeight, 1f))
        if (!sourceRatio.isFinite() || sourceRatio <= 0f) return 1f
        return sourceRatio.coerceIn(minimumPostAspectRatio, maximumPostAspectRatio)
    }

    /** Equiv. iOS `displayedPostAspectRatio(for imageSize: CGSize, …)`. */
    fun displayedPostAspectRatio(
        imageSize: Size,
        preferredAspectRatio: Float? = null,
    ): Float = displayedPostAspectRatio(imageSize.width, imageSize.height, preferredAspectRatio)

    fun fixedAspectRect(aspectRatio: Float, containerSize: Size): Rect {
        if (aspectRatio <= 0f || containerSize.width <= 0f || containerSize.height <= 0f) {
            return Rect(0f, 0f, containerSize.width, containerSize.height)
        }
        val containerAspectRatio = containerSize.width / containerSize.height
        val width: Float
        val height: Float
        if (aspectRatio > containerAspectRatio) {
            width = containerSize.width
            height = width / aspectRatio
        } else {
            height = containerSize.height
            width = height * aspectRatio
        }
        return Rect(
            left = (containerSize.width - width) / 2f,
            top = (containerSize.height - height) / 2f,
            right = (containerSize.width - width) / 2f + width,
            bottom = (containerSize.height - height) / 2f + height,
        )
    }

    fun frame(layer: MomentHiddenLayer, imageRect: Rect, minimumSizePx: Float): Rect {
        val width = maxOf(minimumSizePx, imageRect.width * layer.width.toFloat())
        val height = if (layer.type == MomentHiddenLayer.LayerType.IMAGE) {
            maxOf(minimumSizePx, width * imageAspectRatio)
        } else {
            maxOf(minimumSizePx, imageRect.height * layer.height.toFloat())
        }
        val centerX = imageRect.left + imageRect.width * layer.anchorX.toFloat()
        val centerY = imageRect.top + imageRect.height * layer.anchorY.toFloat()
        return Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
        )
    }

    /** Port de `frame(for draft: HiddenLayerDraft, in imageRect:)`. */
    fun frame(draft: HiddenLayerDraft, imageRect: Rect, minimumSizePx: Float): Rect {
        val width = maxOf(minimumSizePx, imageRect.width * draft.width.toFloat())
        val height = if (draft.type == MomentHiddenLayer.LayerType.IMAGE) {
            maxOf(minimumSizePx, width * imageAspectRatio)
        } else {
            maxOf(minimumSizePx, imageRect.height * draft.height.toFloat())
        }
        val centerX = imageRect.left + imageRect.width * draft.anchorX.toFloat()
        val centerY = imageRect.top + imageRect.height * draft.anchorY.toFloat()
        return Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
        )
    }

    /** Helper Android: frame con tamaño de contenedor (equiv. imageRect origen cero). */
    fun frame(layer: MomentHiddenLayer, containerSize: IntSize, minimumSizePx: Float): Rect =
        frame(
            layer,
            Rect(0f, 0f, containerSize.width.toFloat(), containerSize.height.toFloat()),
            minimumSizePx,
        )
}
