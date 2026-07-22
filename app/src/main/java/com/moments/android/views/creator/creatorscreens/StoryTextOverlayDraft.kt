package com.moments.android.views.creator.creatorscreens

import androidx.compose.ui.geometry.Offset
import com.moments.android.models.Point
import com.moments.android.models.StoryTextOverlayMetadata
import java.util.UUID

/** iOS `StoryEditingView.ActiveEditorMode` (mínimo chunk-2). */
enum class ActiveEditorMode {
    IDLE,
    TEXT,
    DRAWING,
    FILTERS,
}

/**
 * Draft local de overlay de texto — espejo mínimo de iOS `StoryTextOverlayDraft`.
 * Chunk 3: styleRaw / colorHex / forcesAllCaps vivos.
 * Motion / gradients: defaults fijos hasta chunks siguientes.
 */
data class StoryTextOverlayDraft(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    /** Posición normalizada 0..1 relativa al canvas. */
    val normalizedX: Double = 0.5,
    val normalizedY: Double = 0.42,
    val fontSize: Double = 30.0,
    val colorHex: String = "FFFFFF",
    val alignmentRaw: String = "center",
    val backgroundFillRaw: String = "none",
    val styleRaw: String = "modern",
    val strokeRaw: String = "none",
    val visualEffectRaw: String = "none",
    val motionRaw: String = "none",
    val forcesAllCaps: Boolean = false,
    val layerOrder: Int = 0,
) {
    val isReady: Boolean get() = text.trim().isNotEmpty()

    fun toMetadata(): StoryTextOverlayMetadata = StoryTextOverlayMetadata(
        id = id,
        text = text.trim(),
        normalizedPosition = Point(
            normalizedX.coerceIn(0.0, 1.0),
            normalizedY.coerceIn(0.0, 1.0),
        ),
        layerOrder = layerOrder,
        styleRaw = styleRaw,
        colorHex = colorHex,
        fontSize = fontSize,
        alignmentRaw = alignmentRaw,
        backgroundFillRaw = backgroundFillRaw,
        strokeRaw = strokeRaw,
        visualEffectRaw = visualEffectRaw,
        motionRaw = motionRaw,
        forcesAllCaps = forcesAllCaps,
        isLiveOverlay = true,
        gradientStopHexes = null,
        gradientAngle = null,
    )

    companion object {
        fun defaultPlacement(): StoryTextOverlayDraft = StoryTextOverlayDraft(
            normalizedX = 0.5,
            normalizedY = 0.42,
        )
    }
}

fun Offset.toNormalized(width: Float, height: Float): Pair<Double, Double> {
    val w = width.coerceAtLeast(1f)
    val h = height.coerceAtLeast(1f)
    return (x / w).toDouble().coerceIn(0.05, 0.95) to (y / h).toDouble().coerceIn(0.05, 0.95)
}
