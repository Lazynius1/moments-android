package com.moments.android.views.feed.reactions

import androidx.compose.ui.graphics.Color

/** Port de `reacciones.swift` — tipos de reacción del feed. */
enum class ReactionType(
    val rawValue: String,
    val icon: String,
    val displayName: String,
    val color: Color,
) {
    Vibe("vibe", "✌🏻", "Vibe", Color(0xFF007AFF)),
    Fire("fire", "🔥", "Fire", Color.Red),
    Real("real", "✅", "Real", Color(0xFFAF52DE)),
    Mood("mood", "😊", "Mood", Color(0xFFFFCC00)),
    Glow("glow", "✨", "Glow", Color(0xFFFF9500)),
    Feel("feel", "❤️", "Feel", Color(0xFFFF2D55)),
    Love("love", "💕", "Love", Color.Red),
    Wow("wow", "😮", "Wow", Color(0xFF007AFF)),
    Laugh("laugh", "😂", "Laugh", Color(0xFFFFCC00)),
    Cry("cry", "😢", "Cry", Color(0xFF32ADE6)),
    Respect("respect", "🙏🏻", "Respect", Color(0xFF34C759)),
    Power("power", "⚡", "Power", Color(0xFFFF9500)),
    Genius("genius", "🧠", "Genius", Color(0xFF5856D6)),
    Creative("creative", "🎨", "Creative", Color(0xFFAF52DE)),
    Chill("chill", "😌", "Chill", Color(0xFF34C759)),
    Hype("hype", "🎉", "Hype", Color(0xFFFF2D55)),
    ;

    val filledIcon: String get() = icon

    companion object {
        val allCases: List<ReactionType> = entries

        fun fromRaw(raw: String?): ReactionType? =
            entries.firstOrNull { it.rawValue == raw }
    }
}

data class MomentReactionState(
    val type: ReactionType?,
    val count: Int,
    val hasReacted: Boolean,
)

object ReactionButtonMetrics {
    const val buttonSizeDp = 44f
    const val emojiSizeSp = 24f
    const val badgeFontSp = 10f
}
