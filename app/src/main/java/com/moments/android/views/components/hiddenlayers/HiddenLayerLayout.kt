package com.moments.android.views.components.hiddenlayers

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.moments.android.models.MomentHiddenLayer

/** Port de `HiddenLayerLayout.swift`. */
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
        val source = preferredAspectRatio?.takeIf { it.isFinite() && it > 0f }
            ?: (imageWidth / maxOf(imageHeight, 1f))
        if (!source.isFinite() || source <= 0f) return 1f
        return source.coerceIn(minimumPostAspectRatio, maximumPostAspectRatio)
    }

    fun fixedAspectRect(aspectRatio: Float, container: Size): Rect {
        if (aspectRatio <= 0f || container.width <= 0f || container.height <= 0f) {
            return Rect(0f, 0f, container.width, container.height)
        }
        val containerAspect = container.width / container.height
        val width: Float
        val height: Float
        if (aspectRatio > containerAspect) {
            width = container.width
            height = width / aspectRatio
        } else {
            height = container.height
            width = height * aspectRatio
        }
        return Rect(
            left = (container.width - width) / 2f,
            top = (container.height - height) / 2f,
            right = (container.width - width) / 2f + width,
            bottom = (container.height - height) / 2f + height,
        )
    }

    fun frame(layer: MomentHiddenLayer, imageRect: Rect): Rect {
        val width = maxOf(44f, imageRect.width * layer.width.toFloat())
        val height = if (layer.type == MomentHiddenLayer.LayerType.IMAGE) {
            maxOf(44f, width * imageAspectRatio)
        } else {
            maxOf(44f, imageRect.height * layer.height.toFloat())
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

    fun frame(layer: MomentHiddenLayer, containerSize: IntSize): Rect =
        frame(layer, Rect(0f, 0f, containerSize.width.toFloat(), containerSize.height.toFloat()))
}
