package com.moments.android.views.creator.components

import android.content.Context
import androidx.annotation.StringRes
import com.moments.android.R

/**
 * Port de `StoryTextVisualRenderer.swift`.
 * Android persiste efectos como raw string (≡ `StoryEditingView.TextEffect.rawValue`).
 */
enum class StoryTextVisualTreatment(val raw: String) {
    PLAIN("plain"),
    SPARKLE_PULSE("sparklePulse"),
    NEON_GLOW("neonGlow"),
    SOFT_GLOW("softGlow"),
    PULSE_HALO("pulseHalo"),
    MARKER_HIGHLIGHT("markerHighlight"),
    CHALK_DUST("chalkDust"),
    PIXEL_BITMAP("pixelBitmap"),
    BOXED_CAPTION("boxedCaption"),
    MEME_STRONG("memeStrong"),
    OUTLINE_POP("outlinePop"),
    STICKER_CUTOUT("stickerCutout"),
    GRADIENT_FILL("gradientFill"),
    GLASS_TEXT("glassText"),
    HOLOGRAPHIC_FILL("holographicFill"),
    TAPE_LABEL("tapeLabel"),
    TEXT_SHIMMER("textShimmer"),
    ECHO_STACK("echoStack"),
    LONG_SHADOW("longShadow"),
    GLITCH_SPLIT("glitchSplit"),
    ;

    companion object {
        fun fromRaw(raw: String?): StoryTextVisualTreatment =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: PLAIN
    }
}

/** ≡ `TextEffect.momentsVisualToolbar` — orden visual de la toolbar. */
val storyTextVisualToolbarEffects: List<String> =
    StoryTextEffect.momentsVisualToolbar.map { it.raw }

/** Equivalente de `TextEffect.visualTreatment`. */
fun storyTextVisualTreatmentForEffect(effect: String?): StoryTextVisualTreatment =
    when (StoryTextEffect.fromStoredRaw(effect)) {
        StoryTextEffect.SPARKLE -> StoryTextVisualTreatment.SPARKLE_PULSE
        StoryTextEffect.NEON -> StoryTextVisualTreatment.NEON_GLOW
        StoryTextEffect.GLOW -> StoryTextVisualTreatment.SOFT_GLOW
        StoryTextEffect.PULSE -> StoryTextVisualTreatment.PULSE_HALO
        StoryTextEffect.MARKER -> StoryTextVisualTreatment.MARKER_HIGHLIGHT
        StoryTextEffect.CHALK -> StoryTextVisualTreatment.CHALK_DUST
        StoryTextEffect.PIXEL -> StoryTextVisualTreatment.PIXEL_BITMAP
        StoryTextEffect.OUTLINE -> StoryTextVisualTreatment.OUTLINE_POP
        StoryTextEffect.STICKER -> StoryTextVisualTreatment.STICKER_CUTOUT
        StoryTextEffect.GRADIENT -> StoryTextVisualTreatment.GRADIENT_FILL
        StoryTextEffect.GLASS -> StoryTextVisualTreatment.GLASS_TEXT
        StoryTextEffect.HOLOGRAPHIC -> StoryTextVisualTreatment.HOLOGRAPHIC_FILL
        StoryTextEffect.TAPE -> StoryTextVisualTreatment.TAPE_LABEL
        StoryTextEffect.TEXT_SHIMMER, StoryTextEffect.SHIMMER -> StoryTextVisualTreatment.TEXT_SHIMMER
        StoryTextEffect.ECHO -> StoryTextVisualTreatment.ECHO_STACK
        StoryTextEffect.DEPTH -> StoryTextVisualTreatment.LONG_SHADOW
        StoryTextEffect.GLITCH -> StoryTextVisualTreatment.GLITCH_SPLIT
        StoryTextEffect.NONE -> StoryTextVisualTreatment.PLAIN
    }

/**
 * ≡ `TextEffect.momentsToolbarLabel` → `storyTextEffect.*` Localizable.
 */
@StringRes
fun storyTextEffectMomentsToolbarLabelRes(effect: String?): Int = when (effect?.lowercase()) {
    "none", null -> R.string.story_text_effect_none
    "sticker" -> R.string.story_text_effect_sticker
    "outline" -> R.string.story_text_effect_outline
    "gradient" -> R.string.story_text_effect_gradient
    "neon" -> R.string.story_text_effect_neon
    "glow" -> R.string.story_text_effect_glow
    "glass" -> R.string.story_text_effect_glass
    "sparkle" -> R.string.story_text_effect_sparkle
    "pixel" -> R.string.story_text_effect_pixel
    "holographic" -> R.string.story_text_effect_holographic
    "tape" -> R.string.story_text_effect_tape
    "pulse" -> R.string.story_text_effect_pulse
    "marker" -> R.string.story_text_effect_marker
    "chalk" -> R.string.story_text_effect_chalk
    "textshimmer", "shimmer" -> R.string.story_text_effect_text_shimmer
    "echo" -> R.string.story_text_effect_echo
    "depth" -> R.string.story_text_effect_depth
    "glitch" -> R.string.story_text_effect_glitch
    else -> R.string.story_text_effect_none
}

/** ≡ `TextEffect.momentsToolbarLabel` con Context. */
fun storyTextEffectMomentsToolbarLabel(effect: String?, context: Context): String =
    context.getString(storyTextEffectMomentsToolbarLabelRes(effect))

/** ≡ `TextEffect.usesGradientEditor`. */
fun storyTextEffectUsesGradientEditor(effect: String?): Boolean =
    StoryTextEffect.fromRaw(effect).usesGradientEditor

/** ≡ `TextEffect.opensColorContextOnSelect`. */
fun storyTextEffectOpensColorContextOnSelect(effect: String?): Boolean =
    StoryTextEffect.fromRaw(effect).opensColorContextOnSelect

/** Equivalente de `TextStyle.styleAccentTreatment`. */
fun StoryTextStyle.styleAccentTreatment(): StoryTextVisualTreatment = when (this) {
    StoryTextStyle.TYPEWRITER, StoryTextStyle.BOLD -> StoryTextVisualTreatment.BOXED_CAPTION
    StoryTextStyle.MEME -> StoryTextVisualTreatment.MEME_STRONG
    else -> StoryTextVisualTreatment.PLAIN
}
