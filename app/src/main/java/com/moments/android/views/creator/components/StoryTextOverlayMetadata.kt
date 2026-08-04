package com.moments.android.views.creator.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.moments.android.models.Point
import com.moments.android.models.Story
import com.moments.android.models.StoryTextOverlayMetadata
import java.util.UUID

/**
 * Port de `StoryTextOverlayMetadata.swift`:
 * CanvasPlacement + Draft + helpers de metadata + extensión `Story`.
 * El data class Codable/Firestore vive en `models.StoryTextOverlayMetadata`.
 */

/** ≡ `StoryTextCanvasPlacement`. */
object StoryTextCanvasPlacement {
    fun defaultPosition(canvasSize: Size): Offset =
        Offset(canvasSize.width / 2f, maxOf(canvasSize.height * 0.42f, 80f))

    fun needsSeed(position: Offset, canvasSize: Size): Boolean {
        if (canvasSize.width <= 1f || canvasSize.height <= 1f) return false
        return position == Offset.Zero ||
            position.x < 12f || position.y < 12f ||
            position.x > canvasSize.width - 12f ||
            position.y > canvasSize.height - 12f
    }
}

/**
 * ≡ `StoryTextOverlayDraft`.
 * Android guarda posición normalizada (0..1); iOS usa puntos absolutos y normaliza en `metadata(in:)`.
 */
data class StoryTextOverlayDraft(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
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
    val gradientStopHexes: List<String> = emptyList(),
    val gradientAngle: Int = 0,
) {
    val isReady: Boolean get() = text.trim().isNotEmpty()

    val gradientColors: List<Color>
        get() = StoryTextGradientSettings.decodeStops(gradientStopHexes, parseStoryColorHex(colorHex))

    /** ≡ `metadata(in:)` — nil si el texto está vacío. */
    fun metadata(contentSize: Size): StoryTextOverlayMetadata? =
        buildStoryTextOverlayMetadata(
            id = id,
            text = text,
            editorPosition = Offset(
                (normalizedX * contentSize.width.coerceAtLeast(1f)).toFloat(),
                (normalizedY * contentSize.height.coerceAtLeast(1f)).toFloat(),
            ),
            contentSize = contentSize,
            layerOrder = layerOrder,
            style = StoryTextStyle.fromRaw(styleRaw),
            textColor = parseStoryColorHex(colorHex),
            fontSize = fontSize.toFloat(),
            alignmentRaw = alignmentRaw,
            backgroundFillRaw = backgroundFillRaw,
            strokeRaw = strokeRaw,
            visualEffectRaw = visualEffectRaw,
            motionRaw = motionRaw,
            forcesAllCaps = forcesAllCaps,
            gradientStopHexes = gradientStopHexes,
            gradientAngle = gradientAngle,
        )

    /** Atajo cuando la posición ya está normalizada (sin rect de contenido). */
    fun toMetadata(): StoryTextOverlayMetadata? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val effect = StoryTextEffect.fromStoredRaw(visualEffectRaw)
        return StoryTextOverlayMetadata(
            id = id,
            text = trimmed,
            normalizedPosition = Point(
                normalizedX.coerceIn(0.0, 1.0),
                normalizedY.coerceIn(0.0, 1.0),
            ),
            layerOrder = layerOrder,
            styleRaw = styleRaw.ifBlank { StoryTextStyle.MODERN.raw },
            colorHex = colorHex.ifBlank { "FFFFFF" },
            fontSize = fontSize,
            alignmentRaw = encodeStoryTextAlignment(alignmentRaw),
            backgroundFillRaw = StoryTextBackgroundFill.fromRaw(backgroundFillRaw).raw,
            strokeRaw = StoryTextStroke.fromRaw(strokeRaw).raw,
            visualEffectRaw = effect.raw,
            motionRaw = sanitizeStoryTextMotionRaw(motionRaw),
            forcesAllCaps = forcesAllCaps,
            isLiveOverlay = true,
            gradientStopHexes = gradientStopHexes.takeIf {
                effect == StoryTextEffect.GRADIENT && it.isNotEmpty()
            },
            gradientAngle = gradientAngle.takeIf { effect == StoryTextEffect.GRADIENT },
        )
    }

    companion object {
        fun defaultPlacement(): StoryTextOverlayDraft = StoryTextOverlayDraft(
            normalizedX = 0.5,
            normalizedY = 0.42,
        )

        /** ≡ `from(metadata:canvasSize:)` — posición vía `displayPosition`. */
        fun from(metadata: StoryTextOverlayMetadata, canvasSize: Size): StoryTextOverlayDraft {
            val color = parseStoryColorHex(metadata.colorHex)
            val position = metadata.displayPosition(canvasSize)
            val w = canvasSize.width.coerceAtLeast(1f).toDouble()
            val h = canvasSize.height.coerceAtLeast(1f).toDouble()
            return StoryTextOverlayDraft(
                id = metadata.id,
                text = metadata.text,
                normalizedX = (position.x / w).coerceIn(0.0, 1.0),
                normalizedY = (position.y / h).coerceIn(0.0, 1.0),
                styleRaw = metadata.styleRaw.ifBlank { StoryTextStyle.MODERN.raw },
                visualEffectRaw = StoryTextEffect.fromStoredRaw(metadata.visualEffectRaw).raw,
                colorHex = metadata.colorHex.ifBlank { "FFFFFF" },
                alignmentRaw = decodeStoryTextAlignment(metadata.alignmentRaw),
                backgroundFillRaw = legacyStoryTextBackgroundRaw(metadata.backgroundFillRaw),
                fontSize = metadata.fontSize,
                strokeRaw = StoryTextStroke.fromRaw(metadata.strokeRaw).raw,
                motionRaw = metadata.motion.raw,
                forcesAllCaps = metadata.forcesAllCaps,
                layerOrder = metadata.layerOrder,
                gradientStopHexes = metadata.gradientStopHexes
                    ?: StoryTextGradientSettings.encodeStops(StoryTextGradientSettings.defaultStops(color)),
                gradientAngle = metadata.gradientAngle ?: 0,
            )
        }

        /** Overload sin canvas (usa la posición ya normalizada del metadata). */
        fun from(metadata: StoryTextOverlayMetadata): StoryTextOverlayDraft =
            from(metadata, Size(1f, 1f)).copy(
                normalizedX = metadata.normalizedPosition.x.coerceIn(0.0, 1.0),
                normalizedY = metadata.normalizedPosition.y.coerceIn(0.0, 1.0),
            )
    }
}

/** ≡ `StoryTextOverlayMetadata.build`. */
fun buildStoryTextOverlayMetadata(
    id: String = UUID.randomUUID().toString(),
    text: String,
    editorPosition: Offset,
    contentSize: Size,
    layerOrder: Int,
    style: StoryTextStyle,
    textColor: Color,
    fontSize: Float,
    alignmentRaw: String,
    backgroundFillRaw: String,
    strokeRaw: String,
    visualEffectRaw: String,
    motionRaw: String,
    forcesAllCaps: Boolean,
    gradientStopHexes: List<String> = emptyList(),
    gradientAngle: Int = 0,
): StoryTextOverlayMetadata? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val effect = StoryTextEffect.fromStoredRaw(visualEffectRaw)
    val safeWidth = contentSize.width.coerceAtLeast(1f)
    val safeHeight = contentSize.height.coerceAtLeast(1f)
    return StoryTextOverlayMetadata(
        id = id,
        text = trimmed,
        normalizedPosition = Point(
            (editorPosition.x / safeWidth).toDouble().coerceIn(0.0, 1.0),
            (editorPosition.y / safeHeight).toDouble().coerceIn(0.0, 1.0),
        ),
        layerOrder = layerOrder,
        styleRaw = style.raw,
        colorHex = textColor.toStoryHex(),
        fontSize = fontSize.toDouble(),
        alignmentRaw = encodeStoryTextAlignment(alignmentRaw),
        backgroundFillRaw = StoryTextBackgroundFill.fromRaw(backgroundFillRaw).raw,
        strokeRaw = StoryTextStroke.fromRaw(strokeRaw).raw,
        visualEffectRaw = effect.raw,
        motionRaw = sanitizeStoryTextMotionRaw(motionRaw),
        forcesAllCaps = forcesAllCaps,
        isLiveOverlay = true,
        gradientStopHexes = gradientStopHexes.takeIf {
            effect == StoryTextEffect.GRADIENT && it.isNotEmpty()
        },
        gradientAngle = gradientAngle.takeIf { effect == StoryTextEffect.GRADIENT },
    )
}

/** ≡ `sanitizeMotionRaw`. */
fun sanitizeStoryTextMotionRaw(raw: String): String =
    when (raw.lowercase()) {
        "shimmer" -> StoryTextMotion.TYPEWRITER.raw
        "jump" -> StoryTextMotion.BOUNCE.raw
        else -> raw.ifBlank { StoryTextMotion.NONE.raw }
    }

/** ≡ `encodeAlignment`. */
fun encodeStoryTextAlignment(raw: String): String = when (raw.lowercase()) {
    "leading", "left" -> "leading"
    "trailing", "right" -> "trailing"
    else -> "center"
}

/** ≡ `decodeAlignment` (raw string; Compose no tiene TextAlignment de SwiftUI). */
fun decodeStoryTextAlignment(raw: String?): String = when (raw?.lowercase()) {
    "leading", "left" -> "leading"
    "trailing", "right" -> "trailing"
    else -> "center"
}

private fun legacyStoryTextBackgroundRaw(raw: String): String = when (raw.lowercase()) {
    "black", "white" -> StoryTextBackgroundFill.SOLID.raw
    "semitransparent" -> StoryTextBackgroundFill.SEMI_TRANSPARENT.raw
    else -> StoryTextBackgroundFill.fromRaw(raw.ifBlank { "none" }).raw
}

/** ≡ `motion` + sanitize. */
val StoryTextOverlayMetadata.motion: StoryTextMotion
    get() = StoryTextMotion.fromLegacyRaw(sanitizeStoryTextMotionRaw(motionRaw))

/**
 * ≡ `renderConfiguration()`.
 * Legacy `backgroundFillRaw` black/white → solid + color.
 */
fun StoryTextOverlayMetadata.renderConfiguration(): StoryTextRenderConfiguration {
    val legacyFill = backgroundFillRaw.lowercase()
    val resolvedColor: Color
    val resolvedFill: String
    when (legacyFill) {
        "black" -> {
            resolvedFill = StoryTextBackgroundFill.SOLID.raw
            resolvedColor = Color.Black
        }
        "white" -> {
            resolvedFill = StoryTextBackgroundFill.SOLID.raw
            resolvedColor = Color.White
        }
        else -> {
            resolvedFill = legacyStoryTextBackgroundRaw(backgroundFillRaw)
            resolvedColor = parseStoryColorHex(colorHex)
        }
    }
    return StoryTextRenderConfiguration(
        text = text,
        style = StoryTextStyle.fromRaw(styleRaw),
        visualEffectRaw = StoryTextEffect.fromStoredRaw(visualEffectRaw).raw,
        textColor = resolvedColor,
        textAlignmentRaw = decodeStoryTextAlignment(alignmentRaw),
        textBackgroundFillRaw = resolvedFill,
        fontSize = fontSize.toFloat(),
        textStrokeRaw = StoryTextStroke.fromRaw(strokeRaw).raw,
        forcesAllCaps = forcesAllCaps,
        gradientStops = StoryTextGradientSettings.decodeStops(gradientStopHexes, resolvedColor),
        gradientAngle = gradientAngle ?: 0,
    )
}

fun StoryTextOverlayMetadata.scaledFontSize(containerWidthDp: Float): Float {
    // ≡ iOS: containerWidth en points; en Android hay que pasar dp, no px.
    val scaleFactor = containerWidthDp.coerceAtLeast(1f) / 375f
    return (fontSize * scaleFactor).toFloat()
}

fun StoryTextOverlayMetadata.scaledRenderConfiguration(containerWidthDp: Float): StoryTextRenderConfiguration =
    renderConfiguration().copy(fontSize = scaledFontSize(containerWidthDp))

fun StoryTextOverlayMetadata.displayPosition(containerSize: Size): Offset =
    Offset(
        (normalizedPosition.x * containerSize.width.coerceAtLeast(1f)).toFloat(),
        (normalizedPosition.y * containerSize.height.coerceAtLeast(1f)).toFloat(),
    )

/** ≡ `Story.usesLiveTextOverlay`. */
val Story.usesLiveTextOverlay: Boolean
    get() = when {
        !textOverlays.isNullOrEmpty() -> true
        text.isNullOrBlank() -> false
        textOverlayLive == true -> true
        else -> textColorHex != null || textMotion != null || textVisualEffect != null
    }

/**
 * ≡ `Story.resolvedTextOverlays`.
 * Fallback canvas legacy 393×852 (= iOS cuando no hay ventana).
 */
val Story.resolvedTextOverlays: List<StoryTextOverlayMetadata>
    get() {
        textOverlays?.takeIf { it.isNotEmpty() }?.let { overlays ->
            return overlays
                .filter { it.text.isNotBlank() }
                .sortedWith(
                    compareBy<StoryTextOverlayMetadata> { it.layerOrder }
                        .thenBy { it.id },
                )
        }
        val legacyText = text?.takeIf { it.isNotBlank() } ?: return emptyList()
        val normalizedX = textPositionNormX
            ?: textPosition?.x?.div(393.0)
            ?: return emptyList()
        val normalizedY = textPositionNormY
            ?: textPosition?.y?.div(852.0)
            ?: return emptyList()
        return listOf(
            StoryTextOverlayMetadata(
                id = "legacy-text-overlay",
                text = legacyText,
                normalizedPosition = Point(
                    normalizedX.coerceIn(0.0, 1.0),
                    normalizedY.coerceIn(0.0, 1.0),
                ),
                layerOrder = textLayerOrder ?: 0,
                styleRaw = textStyle ?: StoryTextStyle.MODERN.raw,
                colorHex = textColorHex ?: "FFFFFF",
                fontSize = textFontSize ?: 30.0,
                alignmentRaw = textAlignment ?: "center",
                backgroundFillRaw = textBackgroundFill ?: StoryTextBackgroundFill.NONE.raw,
                strokeRaw = textStroke ?: StoryTextStroke.NONE.raw,
                visualEffectRaw = textVisualEffect ?: StoryTextEffect.NONE.raw,
                motionRaw = textMotion ?: StoryTextMotion.NONE.raw,
                forcesAllCaps = forcesAllCaps ?: false,
                isLiveOverlay = true,
            ),
        )
    }

val Story.resolvedTextOverlayMetadata: StoryTextOverlayMetadata?
    get() = resolvedTextOverlays.firstOrNull()

/** Normaliza offset a 0..1 (≡ `clamped01`). */
fun Offset.toNormalized(width: Float, height: Float): Pair<Double, Double> {
    val w = width.coerceAtLeast(1f)
    val h = height.coerceAtLeast(1f)
    return (x / w).toDouble().coerceIn(0.0, 1.0) to (y / h).toDouble().coerceIn(0.0, 1.0)
}
