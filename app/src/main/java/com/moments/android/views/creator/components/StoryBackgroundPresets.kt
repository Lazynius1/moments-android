package com.moments.android.views.creator.components

import androidx.compose.ui.graphics.Color

/** Port de `StoryBackgroundPreset` de `StoryBackgroundPresets.swift`. */
data class StoryBackgroundPreset(
    val id: String,
    val name: String,
    val hexColors: List<String>,
    val usesAutoPalette: Boolean = false,
) {
    /** Equivalente de `uiColors`; auto delega en la paleta de la imagen. */
    val colors: List<Color>
        get() = if (usesAutoPalette) emptyList() else hexColors.map(::storyColorFromHex)

    companion object {
        val auto = StoryBackgroundPreset(
            id = "auto",
            name = "Auto",
            hexColors = emptyList(),
            usesAutoPalette = true,
        )

        val presets = listOf(
            auto,
            StoryBackgroundPreset("sunset", "Sunset", listOf("FF7A59", "FFB347", "FFD56F")),
            StoryBackgroundPreset("berry", "Berry", listOf("5B2A86", "A4508B", "F764A1")),
            StoryBackgroundPreset("lagoon", "Lagoon", listOf("005AA7", "43C6AC", "E4FDF9")),
            StoryBackgroundPreset("lime", "Lime", listOf("1E9600", "93F9B9", "F9F871")),
            StoryBackgroundPreset("ember", "Ember", listOf("2B061E", "875053", "D1A080")),
            StoryBackgroundPreset("midnight", "Midnight", listOf("0F2027", "203A43", "2C5364")),
            StoryBackgroundPreset("candy", "Candy", listOf("FF5F6D", "FFC371", "FFE29F")),
            StoryBackgroundPreset("aurora", "Aurora", listOf("00C9A7", "845EC2", "FF6F91")),
            StoryBackgroundPreset("ocean", "Ocean", listOf("114B5F", "028090", "E4FDE1")),
            StoryBackgroundPreset("pride", "Pride", listOf("E40303", "FF8C00", "FFED00", "008026", "24408E", "732982")),
        )
    }
}

private fun storyColorFromHex(value: String): Color {
    val normalized = value.trim().removePrefix("#")
    val rgb = normalized.takeLast(6).toLongOrNull(16) ?: 0L
    return Color(0xFF000000L or rgb)
}
