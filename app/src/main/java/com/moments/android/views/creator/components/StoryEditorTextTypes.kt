package com.moments.android.views.creator.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Port de `StoryEditorTextTypes.swift` (`extension StoryEditingView`).
 * Tipografías, fills, stroke, effects, motion y modo de editor.
 */

/** ≡ `StoryTextStylePreset`. */
data class StoryTextStylePreset(
    val usesAllCaps: Boolean = false,
    val letterSpacing: Float = 0f,
    val defaultColorHex: String = "FFFFFF",
    val defaultBackgroundFill: StoryTextBackgroundFill = StoryTextBackgroundFill.NONE,
    val defaultEffect: StoryTextEffect = StoryTextEffect.NONE,
    val defaultStroke: StoryTextStroke = StoryTextStroke.NONE,
    /** ≡ `defaultBackgroundUIColor` (typewriter gray@0.55, bold black@0.6). */
    val defaultBackgroundColor: Color? = null,
    val fontSizeOffset: Float = 0f,
)

/**
 * ≡ `StoryEditingView.TextStyle`.
 * Fuera del carrusel (legacy decode): spartan≈grotesk, squeeze≈oswald,
 * casual≈signature, editorial≈classic.
 */
enum class StoryTextStyle(val raw: String, val displayName: String, val fontFile: String?) {
    MODERN("modern", "Modern", "BebasNeue-Regular.ttf"),
    CLASSIC("classic", "Classic", "Lora-Regular.ttf"),
    CLEAN("clean", "Clean", null),
    GROTESK("grotesk", "Grotesk", "Montserrat-Black.ttf"),
    OSWALD("oswald", "Condensed", "Oswald-Bold.ttf"),
    SPARTAN("spartan", "Geo", "LeagueSpartan-Bold.ttf"),
    POSTER("poster", "Poster", "PlayfairDisplay-Bold.ttf"),
    EDITORIAL("editorial", "Editor", "IBMPlexSerif-Regular.ttf"),
    SLAB("slab", "Slab", "RobotoSlab-Bold.ttf"),
    ROUNDED("rounded", "Bubble", "VarelaRound-Regular.ttf"),
    SIGNATURE("signature", "Signature", "DancingScript-Bold.ttf"),
    CASUAL("casual", "Script", "Satisfy-Regular.ttf"),
    FANCY("fancy", "Fancy", "GreatVibes-Regular.ttf"),
    MARKER("marker", "Marker", "PermanentMarker-Regular.ttf"),
    TYPEWRITER("typewriter", "Mono", null),
    HANDWRITTEN("handwritten", "Journal", "Caveat-Bold.ttf"),
    INDIE("indie", "Indie", "IndieFlower-Regular.ttf"),
    BOLD("bold", "Strong", "Anton-Regular.ttf"),
    NEON("neon", "Neon", "Pacifico-Regular.ttf"),
    CHALK("chalk", "Chalk", null),
    SQUEEZE("squeeze", "Squeeze", "BarlowCondensed-Bold.ttf"),
    ELEGANT("elegant", "Elegant", "CormorantGaramond-Italic.ttf"),
    DECO("deco", "Deco", "PoiretOne-Regular.ttf"),
    MEME("meme", "Meme", "Bangers-Regular.ttf"),
    ARCADE("arcade", "Arcade", "Silkscreen-Regular.ttf"),
    CYBER("cyber", "Cyber", "Audiowide-Regular.ttf"),
    RETRO("retro", "Retro", "Monoton-Regular.ttf"),
    GROOVY("groovy", "Groovy", "Shrikhand-Regular.ttf"),
    STENCIL("stencil", "Stencil", "BlackOpsOne-Regular.ttf"),
    GLAM("glam", "Glam", "AbrilFatface-Regular.ttf"),
    ;

    val preset: StoryTextStylePreset
        get() = when (this) {
            MODERN -> StoryTextStylePreset(usesAllCaps = true, letterSpacing = 1.2f)
            CLASSIC -> StoryTextStylePreset()
            CLEAN -> StoryTextStylePreset()
            GROTESK -> StoryTextStylePreset(letterSpacing = 0.4f, fontSizeOffset = 1f)
            OSWALD -> StoryTextStylePreset(usesAllCaps = true, letterSpacing = 0.6f, fontSizeOffset = 2f)
            SPARTAN -> StoryTextStylePreset(letterSpacing = 0.5f, fontSizeOffset = 1f)
            POSTER -> StoryTextStylePreset(fontSizeOffset = 4f)
            EDITORIAL -> StoryTextStylePreset(letterSpacing = 0.8f, fontSizeOffset = 1f)
            SLAB -> StoryTextStylePreset(letterSpacing = 0.3f, fontSizeOffset = 1f)
            ROUNDED -> StoryTextStylePreset()
            SIGNATURE -> StoryTextStylePreset(fontSizeOffset = 4f)
            CASUAL -> StoryTextStylePreset(fontSizeOffset = 3f)
            FANCY -> StoryTextStylePreset(fontSizeOffset = 6f)
            MARKER -> StoryTextStylePreset(
                defaultColorHex = "000000",
                defaultEffect = StoryTextEffect.MARKER,
            )
            TYPEWRITER -> StoryTextStylePreset(
                defaultBackgroundFill = StoryTextBackgroundFill.SOLID,
                defaultBackgroundColor = Color.Gray.copy(alpha = 0.55f),
            )
            HANDWRITTEN -> StoryTextStylePreset(fontSizeOffset = 2f)
            INDIE -> StoryTextStylePreset(fontSizeOffset = 2f)
            BOLD -> StoryTextStylePreset(
                defaultBackgroundFill = StoryTextBackgroundFill.SOLID,
                defaultBackgroundColor = Color.Black.copy(alpha = 0.6f),
            )
            NEON -> StoryTextStylePreset(
                defaultColorHex = "FF2D55",
                defaultEffect = StoryTextEffect.NEON,
                fontSizeOffset = 2f,
            )
            CHALK -> StoryTextStylePreset(defaultEffect = StoryTextEffect.CHALK)
            SQUEEZE -> StoryTextStylePreset(fontSizeOffset = 2f)
            ELEGANT -> StoryTextStylePreset(fontSizeOffset = 2f)
            DECO -> StoryTextStylePreset(letterSpacing = 2.0f)
            MEME -> StoryTextStylePreset(
                defaultStroke = StoryTextStroke.THICK,
                fontSizeOffset = 3f,
            )
            ARCADE -> StoryTextStylePreset(letterSpacing = 0.5f, fontSizeOffset = 2f)
            CYBER -> StoryTextStylePreset(usesAllCaps = true, letterSpacing = 1.5f, fontSizeOffset = 1f)
            RETRO -> StoryTextStylePreset(usesAllCaps = true, letterSpacing = 1.0f, fontSizeOffset = 4f)
            GROOVY -> StoryTextStylePreset(fontSizeOffset = 2f)
            STENCIL -> StoryTextStylePreset(usesAllCaps = true, letterSpacing = 0.8f, fontSizeOffset = 1f)
            GLAM -> StoryTextStylePreset(fontSizeOffset = 3f)
        }

    val usesAllCaps: Boolean get() = preset.usesAllCaps
    val defaultColorHex: String get() = preset.defaultColorHex
    val letterSpacing: Float get() = preset.letterSpacing
    val fontSizeOffset: Float get() = preset.fontSizeOffset

    fun displayText(raw: String): String = if (usesAllCaps) raw.uppercase() else raw

    /** ≡ `applyPreset(textColor:textBackgroundFill:selectedEffect:textStroke:)`. */
    fun applyPreset(): AppliedStoryTextPreset {
        val p = preset
        return AppliedStoryTextPreset(
            colorHex = p.defaultColorHex,
            backgroundFill = p.defaultBackgroundFill,
            effect = p.defaultEffect,
            stroke = p.defaultStroke,
            forcesAllCaps = p.usesAllCaps,
        )
    }

    /** Tamaño con `fontSizeOffset` (+ typewriter −2 como iOS). */
    fun resolvedFontSize(base: Float): Float {
        val withOffset = base + preset.fontSizeOffset
        return if (this == TYPEWRITER) maxOf(12f, withOffset - 2f) else withOffset
    }

    /**
     * ≡ `TextStyle.backgroundColor` (preview/legacy).
     * Preferir [preset.defaultBackgroundColor] cuando el fill es solid.
     */
    val backgroundColor: Color
        get() {
            preset.defaultBackgroundColor?.let { return it }
            return when (this) {
                MODERN -> Color.Black.copy(alpha = 0.6f)
                CLEAN -> Color.Black.copy(alpha = 0.45f)
                ROUNDED -> Color.Black.copy(alpha = 0.22f)
                MARKER -> Color.Yellow.copy(alpha = 0.18f)
                NEON -> Color.Magenta.copy(alpha = 0.8f)
                TYPEWRITER -> Color.Gray.copy(alpha = 0.55f)
                CHALK -> Color.Black.copy(alpha = 0.18f)
                GROTESK, BOLD -> Color.Black.copy(alpha = 0.5f)
                else -> Color.Transparent
            }
        }

    companion object {
        /** ≡ `TextStyle.fontPickerStyles`. */
        val fontPickerStyles: List<StoryTextStyle> = listOf(
            MODERN, CLASSIC, CLEAN, GROTESK, BOLD, OSWALD, STENCIL, CYBER,
            ROUNDED, POSTER, GLAM, SLAB, ELEGANT, FANCY, DECO, GROOVY,
            RETRO, SIGNATURE, INDIE, HANDWRITTEN, MARKER,
            TYPEWRITER, ARCADE, MEME, NEON, CHALK,
        )

        fun fromRaw(raw: String?): StoryTextStyle =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: MODERN
    }
}

/** Resultado de `TextStyle.applyPreset`. */
data class AppliedStoryTextPreset(
    val colorHex: String,
    val backgroundFill: StoryTextBackgroundFill,
    val effect: StoryTextEffect,
    val stroke: StoryTextStroke,
    val forcesAllCaps: Boolean,
)

/** ≡ `TextBackgroundFill`. */
enum class StoryTextBackgroundFill(val raw: String) {
    NONE("none"),
    SOLID("solid"),
    INVERTED("inverted"),
    SEMI_TRANSPARENT("semiTransparent"),
    ;

    fun cycled(): StoryTextBackgroundFill = when (this) {
        NONE -> SOLID
        SOLID -> INVERTED
        INVERTED -> SEMI_TRANSPARENT
        SEMI_TRANSPARENT -> NONE
    }

    companion object {
        fun fromRaw(raw: String?): StoryTextBackgroundFill = when (raw?.lowercase()) {
            "solid" -> SOLID
            "inverted" -> INVERTED
            "semitransparent" -> SEMI_TRANSPARENT
            else -> NONE
        }
    }
}

/** ≡ `TextStroke`. */
enum class StoryTextStroke(val raw: String) {
    NONE("none"),
    THIN("thin"),
    THICK("thick"),
    ;

    /** iOS NSAttributedString: thin = −2, thick = −4. */
    val strokeWidth: Float
        get() = when (this) {
            NONE -> 0f
            THIN -> -2f
            THICK -> -4f
        }

    /** Magnitud positiva para Compose drawStroke. */
    val composeStrokeWidth: Float get() = kotlin.math.abs(strokeWidth)

    fun cycled(): StoryTextStroke = when (this) {
        NONE -> THIN
        THIN -> THICK
        THICK -> NONE
    }

    companion object {
        fun fromRaw(raw: String?): StoryTextStroke =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NONE
    }
}

/** ≡ `TextEffect`. */
enum class StoryTextEffect(val raw: String) {
    NONE("none"),
    STICKER("sticker"),
    OUTLINE("outline"),
    GRADIENT("gradient"),
    SPARKLE("sparkle"),
    NEON("neon"),
    GLOW("glow"),
    GLASS("glass"),
    HOLOGRAPHIC("holographic"),
    TAPE("tape"),
    PULSE("pulse"),
    TEXT_SHIMMER("textShimmer"),
    MARKER("marker"),
    CHALK("chalk"),
    PIXEL("pixel"),
    SHIMMER("shimmer"),
    ECHO("echo"),
    DEPTH("depth"),
    GLITCH("glitch"),
    ;

    fun cycled(): StoryTextEffect {
        val all = toolbarEffects
        val idx = all.indexOf(this)
        if (idx < 0) return NONE
        return all[(idx + 1) % all.size]
    }

    /** ≡ `TextEffect.backgroundColor` / `uiBackgroundColor`. */
    val backgroundColor: Color?
        get() = when (this) {
            MARKER -> Color(0xFFFFCC00).copy(alpha = 0.28f)
            else -> null
        }

    /** ≡ `TextEffect.shadow` / `nsShadow` (chalk/pixel). */
    fun shadow(): StoryTextShadowSpec? = when (this) {
        CHALK, PIXEL -> StoryTextShadowSpec(
            color = Color.Black.copy(alpha = 0.62f),
            blurRadius = 1f,
            offset = Offset(1f, 1f),
        )
        else -> null
    }

    val usesGradientEditor: Boolean get() = this == GRADIENT
    val opensColorContextOnSelect: Boolean get() = usesGradientEditor

    companion object {
        /**
         * ≡ `momentsVisualToolbar` (definido en VisualRenderer.swift;
         * `toolbarEffects` lo reutiliza).
         */
        val momentsVisualToolbar: List<StoryTextEffect> = listOf(
            NONE, STICKER, OUTLINE, GRADIENT, NEON, GLITCH, ECHO, DEPTH,
            GLOW, GLASS, SPARKLE, PIXEL, HOLOGRAPHIC, TAPE, PULSE,
        )

        val toolbarEffects: List<StoryTextEffect> get() = momentsVisualToolbar

        fun fromRaw(raw: String?): StoryTextEffect =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NONE

        /** ≡ `init?(storedRawValue:)` — shimmer → textShimmer. */
        fun fromStoredRaw(raw: String?): StoryTextEffect {
            val normalized = when (raw?.lowercase()) {
                "shimmer" -> TEXT_SHIMMER.raw
                else -> raw
            }
            return fromRaw(normalized)
        }
    }
}

/** ≡ `TextMotion` (+ typealias `TextAnimation`). */
enum class StoryTextMotion(val raw: String, val displayName: String) {
    NONE("none", "None"),
    TYPEWRITER("typewriter", "Type"),
    POP("pop", "Pop"),
    BOUNCE("bounce", "Jump"),
    WAVE("wave", "Wave"),
    REVEAL("reveal", "Reveal"),
    ;

    fun cycled(): StoryTextMotion {
        val all = toolbarMotions
        val idx = all.indexOf(this)
        if (idx < 0) return NONE
        return all[(idx + 1) % all.size]
    }

    companion object {
        val toolbarMotions: List<StoryTextMotion> =
            listOf(NONE, TYPEWRITER, POP, BOUNCE, WAVE, REVEAL)

        val momentsToolbarMotions: List<StoryTextMotion> =
            listOf(NONE, TYPEWRITER, POP, BOUNCE)

        fun fromRaw(raw: String?): StoryTextMotion =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NONE

        /** ≡ `init?(legacyRawValue:)` — jump→bounce, shimmer→typewriter. */
        fun fromLegacyRaw(raw: String?): StoryTextMotion {
            val mapped = when (raw?.lowercase()) {
                "jump" -> BOUNCE.raw
                "shimmer" -> TYPEWRITER.raw
                else -> raw
            }
            return fromRaw(mapped)
        }
    }
}

typealias StoryTextAnimation = StoryTextMotion

/** ≡ `ActiveEditorMode`. */
enum class ActiveEditorMode {
    IDLE,
    TEXT,
    DRAWING,
    FILTERS,
}
