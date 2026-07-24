package com.moments.android.views.creator.components
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.moments.android.models.Point
import com.moments.android.models.Story
import com.moments.android.models.StoryTextOverlayMetadata
import java.util.UUID

/** iOS `StoryEditingView.ActiveEditorMode` (mínimo chunk-2). */
enum class ActiveEditorMode {
    IDLE,
    TEXT,
    DRAWING,
    FILTERS,
}

/** Port de `StoryTextCanvasPlacement`; Android conserva el draft normalizado. */
object StoryTextCanvasPlacement {
    fun defaultPosition(canvasSize: Size): Offset =
        Offset(canvasSize.width / 2f, maxOf(canvasSize.height * .42f, 80f))

    fun needsSeed(position: Offset, canvasSize: Size): Boolean {
        if (canvasSize.width <= 1f || canvasSize.height <= 1f) return false
        return position == Offset.Zero ||
            position.x < 12f || position.y < 12f ||
            position.x > canvasSize.width - 12f || position.y > canvasSize.height - 12f
    }
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
    val gradientStopHexes: List<String> = emptyList(),
    val gradientAngle: Int = 0,
) {
    val isReady: Boolean get() = text.trim().isNotEmpty()
    val gradientColors: List<Color> get() = StoryTextGradientSettings.decodeStops(gradientStopHexes, parseStoryColorHex(colorHex))

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
        gradientStopHexes = gradientStopHexes.takeIf { visualEffectRaw == "gradient" && it.isNotEmpty() },
        gradientAngle = gradientAngle.takeIf { visualEffectRaw == "gradient" },
    )

    companion object {
        fun defaultPlacement(): StoryTextOverlayDraft = StoryTextOverlayDraft(
            normalizedX = 0.5,
            normalizedY = 0.42,
        )

        /** Port de `StoryTextOverlayDraft.from(metadata:canvasSize:)`. */
        fun from(metadata: StoryTextOverlayMetadata): StoryTextOverlayDraft {
            val color = parseStoryColorHex(metadata.colorHex)
            return StoryTextOverlayDraft(
                id = metadata.id,
                text = metadata.text,
                normalizedX = metadata.normalizedPosition.x.coerceIn(0.0, 1.0),
                normalizedY = metadata.normalizedPosition.y.coerceIn(0.0, 1.0),
                styleRaw = metadata.styleRaw.ifBlank { "modern" },
                visualEffectRaw = metadata.visualEffectRaw.sanitizeStoryTextEffectRaw(),
                colorHex = metadata.colorHex.ifBlank { "FFFFFF" },
                alignmentRaw = metadata.alignmentRaw.decodeStoryTextAlignmentRaw(),
                backgroundFillRaw = metadata.backgroundFillRaw.legacyStoryTextBackgroundRaw(),
                fontSize = metadata.fontSize,
                strokeRaw = metadata.strokeRaw.ifBlank { "none" },
                motionRaw = metadata.motionRaw.sanitizeStoryTextMotionRaw(),
                forcesAllCaps = metadata.forcesAllCaps,
                layerOrder = metadata.layerOrder,
                gradientStopHexes = metadata.gradientStopHexes
                    ?: StoryTextGradientSettings.encodeStops(StoryTextGradientSettings.defaultStops(color)),
                gradientAngle = metadata.gradientAngle ?: 0,
            )
        }
    }
}

/** Port de `StoryTextOverlayMetadata.build` para los consumidores Compose. */
fun buildStoryTextOverlayMetadata(
    id: String,
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
    return StoryTextOverlayMetadata(
        id = id,
        text = trimmed,
        normalizedPosition = Point(
            (editorPosition.x / contentSize.width.coerceAtLeast(1f)).toDouble().coerceIn(0.0, 1.0),
            (editorPosition.y / contentSize.height.coerceAtLeast(1f)).toDouble().coerceIn(0.0, 1.0),
        ),
        layerOrder = layerOrder,
        styleRaw = style.raw,
        colorHex = textColor.toStoryHex(),
        fontSize = fontSize.toDouble(),
        alignmentRaw = alignmentRaw.decodeStoryTextAlignmentRaw(),
        backgroundFillRaw = backgroundFillRaw.legacyStoryTextBackgroundRaw(),
        strokeRaw = strokeRaw,
        visualEffectRaw = visualEffectRaw.sanitizeStoryTextEffectRaw(),
        motionRaw = motionRaw.sanitizeStoryTextMotionRaw(),
        forcesAllCaps = forcesAllCaps,
        isLiveOverlay = true,
        gradientStopHexes = gradientStopHexes.takeIf { visualEffectRaw == "gradient" && it.isNotEmpty() },
        gradientAngle = gradientAngle.takeIf { visualEffectRaw == "gradient" },
    )
}

fun StoryTextOverlayMetadata.renderConfiguration(): StoryTextRenderConfiguration =
    StoryTextRenderConfiguration.from(
        copy(
            backgroundFillRaw = backgroundFillRaw.legacyStoryTextBackgroundRaw(),
            visualEffectRaw = visualEffectRaw.sanitizeStoryTextEffectRaw(),
            motionRaw = motionRaw.sanitizeStoryTextMotionRaw(),
        ),
    )

fun StoryTextOverlayMetadata.scaledRenderConfiguration(containerWidth: Float): StoryTextRenderConfiguration =
    renderConfiguration().copy(fontSize = (fontSize * (containerWidth.coerceAtLeast(1f) / 375f)).toFloat())

fun StoryTextOverlayMetadata.displayPosition(containerSize: Size): Offset =
    Offset(
        (normalizedPosition.x * containerSize.width).toFloat(),
        (normalizedPosition.y * containerSize.height).toFloat(),
    )

private fun String.sanitizeStoryTextMotionRaw(): String = when (lowercase()) {
    "jump" -> "bounce"
    "shimmer" -> "typewriter"
    else -> ifBlank { "none" }
}

private fun String.sanitizeStoryTextEffectRaw(): String =
    ifBlank { "none" }

private fun String.decodeStoryTextAlignmentRaw(): String = when (lowercase()) {
    "left" -> "leading"
    "right" -> "trailing"
    "leading", "trailing" -> lowercase()
    else -> "center"
}

private fun String.legacyStoryTextBackgroundRaw(): String = when (lowercase()) {
    "black", "white" -> "solid"
    "semitransparent" -> "semiTransparent"
    else -> ifBlank { "none" }
}

/** Port de `Story.usesLiveTextOverlay`. */
val Story.usesLiveTextOverlay: Boolean
    get() = when {
        !textOverlays.isNullOrEmpty() -> true
        text.isNullOrBlank() -> false
        textOverlayLive == true -> true
        else -> textColorHex != null || textMotion != null || textVisualEffect != null
    }

/** Port de `Story.resolvedTextOverlays`, incluyendo stories previas al array. */
val Story.resolvedTextOverlays: List<StoryTextOverlayMetadata>
    get() {
        textOverlays?.takeIf { it.isNotEmpty() }?.let { overlays ->
            return overlays
                .filter { it.text.isNotBlank() }
                .sortedWith(compareBy<StoryTextOverlayMetadata> { it.layerOrder }.thenBy { it.id })
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
                normalizedPosition = Point(normalizedX.coerceIn(0.0, 1.0), normalizedY.coerceIn(0.0, 1.0)),
                layerOrder = textLayerOrder ?: 0,
                styleRaw = textStyle ?: "modern",
                colorHex = textColorHex ?: "FFFFFF",
                fontSize = textFontSize ?: 30.0,
                alignmentRaw = textAlignment ?: "center",
                backgroundFillRaw = textBackgroundFill ?: "none",
                strokeRaw = textStroke ?: "none",
                visualEffectRaw = textVisualEffect ?: "none",
                motionRaw = textMotion ?: "none",
                forcesAllCaps = forcesAllCaps ?: false,
                isLiveOverlay = true,
            ),
        )
    }

val Story.resolvedTextOverlayMetadata: StoryTextOverlayMetadata?
    get() = resolvedTextOverlays.firstOrNull()

fun Offset.toNormalized(width: Float, height: Float): Pair<Double, Double> {
    val w = width.coerceAtLeast(1f)
    val h = height.coerceAtLeast(1f)
    return (x / w).toDouble().coerceIn(0.05, 0.95) to (y / h).toDouble().coerceIn(0.05, 0.95)
}
