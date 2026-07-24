package com.moments.android.views.creator.components

/** Port de `StoryTextVisualTreatment` de `StoryTextVisualRenderer.swift`. */
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

/** Equivalente de `TextEffect.visualTreatment`; Android persiste el raw string. */
fun storyTextVisualTreatmentForEffect(effect: String?): StoryTextVisualTreatment = when (effect?.lowercase()) {
    "sparkle" -> StoryTextVisualTreatment.SPARKLE_PULSE
    "neon" -> StoryTextVisualTreatment.NEON_GLOW
    "glow" -> StoryTextVisualTreatment.SOFT_GLOW
    "pulse" -> StoryTextVisualTreatment.PULSE_HALO
    "marker" -> StoryTextVisualTreatment.MARKER_HIGHLIGHT
    "chalk" -> StoryTextVisualTreatment.CHALK_DUST
    "pixel" -> StoryTextVisualTreatment.PIXEL_BITMAP
    "outline" -> StoryTextVisualTreatment.OUTLINE_POP
    "sticker" -> StoryTextVisualTreatment.STICKER_CUTOUT
    "gradient" -> StoryTextVisualTreatment.GRADIENT_FILL
    "glass" -> StoryTextVisualTreatment.GLASS_TEXT
    "holographic" -> StoryTextVisualTreatment.HOLOGRAPHIC_FILL
    "tape" -> StoryTextVisualTreatment.TAPE_LABEL
    "textshimmer", "shimmer" -> StoryTextVisualTreatment.TEXT_SHIMMER
    "echo" -> StoryTextVisualTreatment.ECHO_STACK
    "depth" -> StoryTextVisualTreatment.LONG_SHADOW
    "glitch" -> StoryTextVisualTreatment.GLITCH_SPLIT
    else -> StoryTextVisualTreatment.PLAIN
}

/** Equivalente de `TextStyle.styleAccentTreatment`. */
fun StoryTextStyle.styleAccentTreatment(): StoryTextVisualTreatment = when (this) {
    StoryTextStyle.TYPEWRITER, StoryTextStyle.BOLD -> StoryTextVisualTreatment.BOXED_CAPTION
    StoryTextStyle.MEME -> StoryTextVisualTreatment.MEME_STRONG
    else -> StoryTextVisualTreatment.PLAIN
}

/** Lista de la toolbar Swift en su orden visual. */
val storyTextVisualToolbarEffects = listOf(
    "none", "sticker", "outline", "gradient", "neon", "glitch", "echo", "depth",
    "glow", "glass", "sparkle", "pixel", "holographic", "tape", "pulse",
)
