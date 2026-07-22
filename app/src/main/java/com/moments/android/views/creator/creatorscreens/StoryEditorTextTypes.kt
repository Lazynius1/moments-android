package com.moments.android.views.creator.creatorscreens

/**
 * Port de `StoryEditorTextTypes.swift` — estilos del carrusel Aa (chunk 3).
 * Motion / effects visuales: chunks siguientes.
 */
enum class StoryTextStyle(val raw: String, val displayName: String, val fontFile: String?) {
    MODERN("modern", "Modern", "BebasNeue-Regular.ttf"),
    CLASSIC("classic", "Classic", "Lora-Regular.ttf"),
    CLEAN("clean", "Clean", null),
    GROTESK("grotesk", "Grotesk", "Montserrat-Black.ttf"),
    BOLD("bold", "Strong", "Anton-Regular.ttf"),
    OSWALD("oswald", "Condensed", "Oswald-Bold.ttf"),
    STENCIL("stencil", "Stencil", "BlackOpsOne-Regular.ttf"),
    CYBER("cyber", "Cyber", "Audiowide-Regular.ttf"),
    ROUNDED("rounded", "Bubble", "VarelaRound-Regular.ttf"),
    POSTER("poster", "Poster", "PlayfairDisplay-Bold.ttf"),
    GLAM("glam", "Glam", "AbrilFatface-Regular.ttf"),
    SLAB("slab", "Slab", "RobotoSlab-Bold.ttf"),
    ELEGANT("elegant", "Elegant", "CormorantGaramond-Italic.ttf"),
    FANCY("fancy", "Fancy", "GreatVibes-Regular.ttf"),
    DECO("deco", "Deco", "PoiretOne-Regular.ttf"),
    GROOVY("groovy", "Groovy", "Shrikhand-Regular.ttf"),
    RETRO("retro", "Retro", "Monoton-Regular.ttf"),
    SIGNATURE("signature", "Signature", "DancingScript-Bold.ttf"),
    INDIE("indie", "Indie", "IndieFlower-Regular.ttf"),
    HANDWRITTEN("handwritten", "Journal", "Caveat-Bold.ttf"),
    MARKER("marker", "Marker", "PermanentMarker-Regular.ttf"),
    TYPEWRITER("typewriter", "Mono", null),
    ARCADE("arcade", "Arcade", "Silkscreen-Regular.ttf"),
    MEME("meme", "Meme", "Bangers-Regular.ttf"),
    NEON("neon", "Neon", "Pacifico-Regular.ttf"),
    CHALK("chalk", "Chalk", null),
    ;

    val usesAllCaps: Boolean
        get() = when (this) {
            MODERN, OSWALD, CYBER, RETRO, STENCIL -> true
            else -> false
        }

    val defaultColorHex: String
        get() = when (this) {
            NEON -> "FF2D55"
            MARKER -> "000000"
            else -> "FFFFFF"
        }

    fun displayText(raw: String): String = if (usesAllCaps) raw.uppercase() else raw

    companion object {
        /** iOS `TextStyle.fontPickerStyles` */
        val fontPickerStyles: List<StoryTextStyle> = listOf(
            MODERN, CLASSIC, CLEAN, GROTESK, BOLD, OSWALD, STENCIL, CYBER,
            ROUNDED, POSTER, GLAM, SLAB, ELEGANT, FANCY, DECO, GROOVY,
            RETRO, SIGNATURE, INDIE, HANDWRITTEN, MARKER,
            TYPEWRITER, ARCADE, MEME, NEON, CHALK,
        )

        fun fromRaw(raw: String?): StoryTextStyle =
            entries.firstOrNull { it.raw == raw } ?: MODERN
    }
}

/** Swatches base del color picker iOS (chunk 3 — sin HSB wheel / eyedropper). */
object StoryTextColorSwatches {
    val presets: List<String> = listOf(
        "FFFFFF", "0B1215", "FAF9F6", "FF2D55", "FF9500", "FFCC00",
        "34C759", "00C7BE", "007AFF", "5856D6", "AF52DE", "FF3B30",
    )
}
