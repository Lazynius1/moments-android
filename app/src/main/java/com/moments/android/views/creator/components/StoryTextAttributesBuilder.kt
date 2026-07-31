package com.moments.android.views.creator.components

import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
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
    /** ≡ `visualEffect` / `effect`. */
    val effect: String get() = visualEffectRaw

    val visualTreatment: StoryTextVisualTreatment
        get() {
            val fromEffect = storyTextVisualTreatmentForEffect(visualEffectRaw)
            return if (fromEffect != StoryTextVisualTreatment.PLAIN) fromEffect else style.styleAccentTreatment()
        }

    val displayText: String
        get() {
            if (!appliesDisplayTransform) return text
            return if (forcesAllCaps || style.usesAllCaps) text.uppercase() else text
        }

    /** ≡ `uiTextAlignment`. */
    val textAlign: TextAlign
        get() = when (textAlignmentRaw.lowercase()) {
            "leading", "left" -> TextAlign.Start
            "trailing", "right" -> TextAlign.End
            else -> TextAlign.Center
        }

    companion object {
        fun from(metadata: StoryTextOverlayMetadata): StoryTextRenderConfiguration =
            metadata.renderConfiguration()
    }
}

/** Sombra ≈ `NSShadow` / `TextEffect.nsShadow`. */
data class StoryTextShadowSpec(
    val color: Color,
    val blurRadius: Float,
    val offset: Offset,
)

/** Equivalente Compose de los atributos tipográficos UIKit. */
data class StoryTextCoreAttributes(
    val foreground: Color,
    val background: Color?,
    val textAlign: TextAlign,
    val letterSpacing: Float,
    /** Magnitud Compose; iOS NSAttributedString usa valores negativos (−2/−4). */
    val strokeWidth: Float,
    val strokeColor: Color?,
    val shadow: StoryTextShadowSpec? = null,
)

object StoryTextAttributesBuilder {
    /** ≡ `contrastUIColor(for:)`. */
    fun contrastColor(color: Color): Color {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return if (luminance > 0.68f) Color.Black else Color.White
    }

    /**
     * ≡ `backgroundUIColor(fill:selectedColor:effect:style:)`.
     * Presets: typewriter gray@0.55, bold black@0.6; marker treatment → nil.
     */
    fun backgroundColor(config: StoryTextRenderConfiguration): Color? =
        backgroundColor(
            fillRaw = config.textBackgroundFillRaw,
            selectedColor = config.textColor,
            effectRaw = config.visualEffectRaw,
            style = config.style,
            treatment = config.visualTreatment,
        )

    fun backgroundColor(
        fillRaw: String,
        selectedColor: Color,
        effectRaw: String,
        style: StoryTextStyle,
        treatment: StoryTextVisualTreatment = storyTextVisualTreatmentForEffect(effectRaw),
    ): Color? = when (fillRaw.lowercase()) {
        "solid" -> selectedColor
        "semitransparent" -> selectedColor.copy(alpha = 0.70f)
        "inverted" -> if (contrastColor(selectedColor) == Color.Black) Color.White else Color.Black
        else -> {
            // .none
            styleDefaultBackground(style)?.let { return it }
            if (treatment == StoryTextVisualTreatment.MARKER_HIGHLIGHT) return null
            effectBackgroundColor(effectRaw)
        }
    }

    fun coreAttributes(config: StoryTextRenderConfiguration): StoryTextCoreAttributes {
        val selected = config.textColor
        val fill = config.textBackgroundFillRaw.lowercase()
        val treatment = config.visualTreatment

        var textBackground: Color? = null
        var textForeground: Color = selected

        // Solo .plain usa fondo por atributo (como iOS).
        if (treatment == StoryTextVisualTreatment.PLAIN) {
            textBackground = backgroundColor(config)
        }

        when (fill) {
            "none" -> Unit
            "solid", "semitransparent" -> textForeground = contrastColor(selected)
            "inverted" -> textForeground = selected
        }

        if (treatment == StoryTextVisualTreatment.MARKER_HIGHLIGHT) {
            textBackground = null
        }

        val drawsOwnStroke = treatment in setOf(
            StoryTextVisualTreatment.MEME_STRONG,
            StoryTextVisualTreatment.OUTLINE_POP,
            StoryTextVisualTreatment.STICKER_CUTOUT,
        )
        val stroke = if (!drawsOwnStroke) storyTextStrokeWidth(config.textStrokeRaw) else 0f

        // Glow/neon/… se pintan fuera; NSShadow aquí solo chalk/pixel en el default.
        val shadow = when (treatment) {
            StoryTextVisualTreatment.SOFT_GLOW,
            StoryTextVisualTreatment.NEON_GLOW,
            StoryTextVisualTreatment.SPARKLE_PULSE,
            StoryTextVisualTreatment.PULSE_HALO,
            StoryTextVisualTreatment.GRADIENT_FILL,
            StoryTextVisualTreatment.HOLOGRAPHIC_FILL,
            StoryTextVisualTreatment.GLASS_TEXT,
            StoryTextVisualTreatment.TEXT_SHIMMER,
            StoryTextVisualTreatment.OUTLINE_POP,
            StoryTextVisualTreatment.STICKER_CUTOUT,
            StoryTextVisualTreatment.TAPE_LABEL,
            -> null
            else -> effectShadow(config.visualEffectRaw)
        }

        return StoryTextCoreAttributes(
            foreground = textForeground,
            background = textBackground,
            textAlign = config.textAlign,
            letterSpacing = storyTextLetterSpacing(config.style),
            strokeWidth = stroke,
            strokeColor = textForeground.takeIf { stroke != 0f },
            shadow = shadow,
        )
    }

    fun typingAttributes(config: StoryTextRenderConfiguration): StoryTextCoreAttributes =
        coreAttributes(config)

    /** ≡ `measuredSize` / `boundingRect` + `.integral`. */
    fun measuredSize(config: StoryTextRenderConfiguration, maxWidth: Float): Size {
        val width = maxWidth.coerceAtLeast(1f)
        val paint = TextPaint().apply { textSize = config.fontSize.coerceAtLeast(1f) }
        val lines = config.displayText.split('\n').flatMap { line ->
            if (line.isEmpty()) listOf("") else wrapLine(line, paint, width)
        }
        val measuredWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val lineHeight = paint.fontMetrics.run { bottom - top }
        return Size(
            width = ceil(min(width, measuredWidth).toDouble()).toFloat(),
            height = ceil((lineHeight * max(1, lines.size)).toDouble()).toFloat(),
        )
    }

    /** ≡ `overlayContentSize` — padding por treatment. */
    fun overlayContentSize(config: StoryTextRenderConfiguration, maxWidth: Float): Size {
        val measured = measuredSize(config, maxWidth)
        val padding = when (config.visualTreatment) {
            StoryTextVisualTreatment.NEON_GLOW -> 14f
            StoryTextVisualTreatment.SOFT_GLOW,
            StoryTextVisualTreatment.SPARKLE_PULSE,
            StoryTextVisualTreatment.PULSE_HALO,
            StoryTextVisualTreatment.TEXT_SHIMMER,
            -> 28f
            StoryTextVisualTreatment.GRADIENT_FILL,
            StoryTextVisualTreatment.HOLOGRAPHIC_FILL,
            StoryTextVisualTreatment.MARKER_HIGHLIGHT,
            StoryTextVisualTreatment.BOXED_CAPTION,
            StoryTextVisualTreatment.TAPE_LABEL,
            -> 32f
            StoryTextVisualTreatment.GLASS_TEXT -> 36f
            StoryTextVisualTreatment.MEME_STRONG,
            StoryTextVisualTreatment.STICKER_CUTOUT,
            StoryTextVisualTreatment.OUTLINE_POP,
            -> 20f
            StoryTextVisualTreatment.ECHO_STACK,
            StoryTextVisualTreatment.LONG_SHADOW,
            -> 26f
            StoryTextVisualTreatment.GLITCH_SPLIT -> 16f
            else -> 12f
        }
        return Size(
            width = min(maxWidth, measured.width + padding),
            height = measured.height + padding,
        )
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
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}

/** ≡ `TextStyle.preset.defaultBackgroundUIColor`. */
private fun styleDefaultBackground(style: StoryTextStyle): Color? =
    style.preset.defaultBackgroundColor

/** ≡ `TextEffect.uiBackgroundColor` (marker yellow@0.28). */
private fun effectBackgroundColor(effectRaw: String): Color? =
    StoryTextEffect.fromStoredRaw(effectRaw).backgroundColor

/** ≡ `TextEffect.nsShadow` para chalk/pixel. */
private fun effectShadow(effectRaw: String): StoryTextShadowSpec? =
    StoryTextEffect.fromStoredRaw(effectRaw).shadow()

/** ≡ `TextStyle.preset.letterSpacing` — puntos iOS (`NSKern`); en Compose aplicar con `.sp`. */
private fun storyTextLetterSpacing(style: StoryTextStyle): Float = style.letterSpacing

/**
 * Magnitud Compose del stroke.
 * iOS: thin = −2, thick = −4 (NSAttributedString fill+stroke).
 */
private fun storyTextStrokeWidth(raw: String): Float =
    StoryTextStroke.fromRaw(raw).composeStrokeWidth
