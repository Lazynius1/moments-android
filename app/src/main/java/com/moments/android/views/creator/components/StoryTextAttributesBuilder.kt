package com.moments.android.views.creator.components

import android.text.TextPaint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.moments.android.models.StoryTextOverlayMetadata
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Contrato Android de `StoryTextRenderConfiguration`. */
data class StoryTextRenderConfiguration(
    val text: String,
    val style: StoryTextStyle,
    val visualEffectRaw: String,
    val textColor: Color,
    val textAlignmentRaw: String,
    val textBackgroundFillRaw: String,
    val fontSize: Float,
    val textStrokeRaw: String,
    val forcesAllCaps: Boolean = false,
    val appliesDisplayTransform: Boolean = true,
    val gradientStops: List<Color> = emptyList(),
    val gradientAngle: Int = 0,
) {
    val visualTreatment: StoryTextVisualTreatment
        get() {
            val fromEffect = storyTextVisualTreatmentForEffect(visualEffectRaw)
            return if (fromEffect != StoryTextVisualTreatment.PLAIN) fromEffect else style.styleAccentTreatment()
        }

    val displayText: String
        get() = if (appliesDisplayTransform && (forcesAllCaps || style.usesAllCaps)) text.uppercase() else text

    val textAlign: TextAlign
        get() = when (textAlignmentRaw.lowercase()) {
            "leading", "left" -> TextAlign.Start
            "trailing", "right" -> TextAlign.End
            else -> TextAlign.Center
        }

    companion object {
        fun from(metadata: StoryTextOverlayMetadata): StoryTextRenderConfiguration =
            StoryTextRenderConfiguration(
                text = metadata.text,
                style = StoryTextStyle.fromRaw(metadata.styleRaw),
                visualEffectRaw = metadata.visualEffectRaw,
                textColor = parseStoryColorHex(metadata.colorHex),
                textAlignmentRaw = metadata.alignmentRaw,
                textBackgroundFillRaw = metadata.backgroundFillRaw,
                fontSize = metadata.fontSize.toFloat(),
                textStrokeRaw = metadata.strokeRaw,
                forcesAllCaps = metadata.forcesAllCaps,
                gradientStops = metadata.gradientStopHexes.orEmpty().map(::parseStoryColorHex),
                gradientAngle = metadata.gradientAngle ?: 0,
            )
    }
}

/** Equivalente Compose de los atributos de texto UIKit que necesita el renderer. */
data class StoryTextCoreAttributes(
    val foreground: Color,
    val background: Color?,
    val textAlign: TextAlign,
    val letterSpacing: Float,
    val strokeWidth: Float,
    val strokeColor: Color?,
)

object StoryTextAttributesBuilder {
    fun contrastColor(color: Color): Color {
        val luminance = .299f * color.red + .587f * color.green + .114f * color.blue
        return if (luminance > .68f) Color.Black else Color.White
    }

    fun backgroundColor(config: StoryTextRenderConfiguration): Color? = when (config.textBackgroundFillRaw.lowercase()) {
        "solid" -> config.textColor
        "semitransparent" -> config.textColor.copy(alpha = .70f)
        "inverted" -> if (contrastColor(config.textColor) == Color.Black) Color.White else Color.Black
        else -> when {
            config.style == StoryTextStyle.TYPEWRITER -> Color.Gray.copy(alpha = .55f)
            config.style == StoryTextStyle.BOLD -> Color.Black.copy(alpha = .60f)
            config.visualTreatment == StoryTextVisualTreatment.MARKER_HIGHLIGHT -> null
            else -> null
        }
    }

    fun coreAttributes(config: StoryTextRenderConfiguration): StoryTextCoreAttributes {
        val selected = config.textColor
        val fill = config.textBackgroundFillRaw.lowercase()
        val foreground = when (fill) {
            "solid", "semitransparent" -> contrastColor(selected)
            "inverted" -> selected
            else -> selected
        }
        val treatment = config.visualTreatment
        val drawsOwnStroke = treatment in setOf(
            StoryTextVisualTreatment.MEME_STRONG,
            StoryTextVisualTreatment.OUTLINE_POP,
            StoryTextVisualTreatment.STICKER_CUTOUT,
        )
        val stroke = if (!drawsOwnStroke) storyTextStrokeWidth(config.textStrokeRaw) else 0f
        return StoryTextCoreAttributes(
            foreground = foreground,
            background = if (treatment == StoryTextVisualTreatment.PLAIN) backgroundColor(config) else null,
            textAlign = config.textAlign,
            letterSpacing = storyTextLetterSpacing(config.style),
            strokeWidth = stroke,
            strokeColor = foreground.takeIf { stroke != 0f },
        )
    }

    fun typingAttributes(config: StoryTextRenderConfiguration): StoryTextCoreAttributes = coreAttributes(config)

    /** Medida de texto envuelto equivalente a `boundingRect` de UIKit. */
    fun measuredSize(config: StoryTextRenderConfiguration, maxWidth: Float): Size {
        val width = maxWidth.coerceAtLeast(1f)
        val paint = TextPaint().apply { textSize = config.fontSize.coerceAtLeast(1f) }
        val lines = config.displayText.split('\n').flatMap { line ->
            if (line.isEmpty()) listOf("") else wrapLine(line, paint, width)
        }
        val measuredWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val lineHeight = paint.fontMetrics.run { bottom - top }
        return Size(min(width, measuredWidth), lineHeight * max(1, lines.size))
    }

    fun overlayContentSize(config: StoryTextRenderConfiguration, maxWidth: Float): Size {
        val measured = measuredSize(config, maxWidth)
        val padding = when (config.visualTreatment) {
            StoryTextVisualTreatment.NEON_GLOW -> 14f
            StoryTextVisualTreatment.SOFT_GLOW, StoryTextVisualTreatment.SPARKLE_PULSE,
            StoryTextVisualTreatment.PULSE_HALO, StoryTextVisualTreatment.TEXT_SHIMMER -> 28f
            StoryTextVisualTreatment.GRADIENT_FILL, StoryTextVisualTreatment.HOLOGRAPHIC_FILL,
            StoryTextVisualTreatment.MARKER_HIGHLIGHT, StoryTextVisualTreatment.BOXED_CAPTION,
            StoryTextVisualTreatment.TAPE_LABEL -> 32f
            StoryTextVisualTreatment.GLASS_TEXT -> 36f
            StoryTextVisualTreatment.MEME_STRONG, StoryTextVisualTreatment.STICKER_CUTOUT,
            StoryTextVisualTreatment.OUTLINE_POP -> 20f
            StoryTextVisualTreatment.ECHO_STACK, StoryTextVisualTreatment.LONG_SHADOW -> 26f
            StoryTextVisualTreatment.GLITCH_SPLIT -> 16f
            else -> 12f
        }
        return Size(min(maxWidth, measured.width + padding), measured.height + padding)
    }

    private fun wrapLine(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += current
                current = word
            } else current = candidate
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}

private fun storyTextLetterSpacing(style: StoryTextStyle): Float = when (style) {
    StoryTextStyle.MODERN -> 1.2f
    StoryTextStyle.GROTESK -> .4f
    StoryTextStyle.OSWALD -> .6f
    StoryTextStyle.DECO -> 2f
    StoryTextStyle.ARCADE -> .5f
    StoryTextStyle.CYBER -> 1.5f
    StoryTextStyle.RETRO -> 1f
    StoryTextStyle.STENCIL -> .8f
    else -> 0f
}

private fun storyTextStrokeWidth(raw: String): Float = when (raw.lowercase()) {
    "thin" -> 1f
    "medium" -> 2f
    "thick" -> 3f
    else -> 0f
}
