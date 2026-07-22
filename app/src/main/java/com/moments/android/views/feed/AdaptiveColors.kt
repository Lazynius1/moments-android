package com.moments.android.views.feed

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Port 1:1 de `AdaptiveColors` (MomentRailComponents.swift).
 * No inventar valores: dark surface = 0B1215, light = FAF9F6;
 * background de escena = black/white; accent = 007AFF.
 */
data class AdaptiveColors(
    val isDark: Boolean,
) {
    /** iOS: dark .black / light .white */
    val background: Color
        get() = if (isDark) Color.Black else Color.White

    /** iOS: dark 0B1215 / light FAF9F6 — fondo del feed (`modernBackgroundView`). */
    val surfaceBackground: Color
        get() = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)

    val primary: Color
        get() = if (isDark) Color.White else Color.Black

    val secondary: Color
        get() = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f)

    val tertiary: Color
        get() = if (isDark) Color.Gray.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.8f)

    /** Royal Blue (Premium) */
    val accent: Color
        get() = Color(0xFF007AFF)

    val accentSecondary: Color
        get() = if (isDark) Color(0xFF007AFF).copy(alpha = 0.3f) else Color(0xFF007AFF).copy(alpha = 0.6f)

    val overlayStroke: List<Color>
        get() = if (isDark) {
            listOf(Color.White.copy(alpha = 0.2f), Color(0xFF007AFF).copy(alpha = 0.3f))
        } else {
            listOf(Color.Black.copy(alpha = 0.1f), Color(0xFF007AFF).copy(alpha = 0.4f))
        }

    val buttonStroke: List<Color>
        get() = if (isDark) {
            listOf(Color.White.copy(alpha = 0.3f), Color(0xFF007AFF).copy(alpha = 0.3f))
        } else {
            listOf(Color.Black.copy(alpha = 0.2f), Color(0xFF007AFF).copy(alpha = 0.5f))
        }

    val buttonGradient: List<Color>
        get() = if (isDark) {
            listOf(Color(0xFF007AFF), Color.White.copy(alpha = 0.8f))
        } else {
            listOf(Color(0xFF007AFF), Color.Black.copy(alpha = 0.7f))
        }

    val shadowColor: Color
        get() = if (isDark) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.15f)

    val overlayStrokeBrush: Brush
        get() = Brush.linearGradient(overlayStroke)

    val buttonStrokeBrush: Brush
        get() = Brush.linearGradient(buttonStroke)

    val buttonGradientBrush: Brush
        get() = Brush.linearGradient(buttonGradient)
}

@Composable
fun rememberAdaptiveColors(): AdaptiveColors {
    val isDark = isSystemInDarkTheme()
    return remember(isDark) { AdaptiveColors(isDark) }
}

// Aliases legacy del feed (mismos hex que surface/ink iOS).
internal val FeedCanvas = Color(0xFFFAF9F6) // light surfaceBackground
internal val FeedInk = Color(0xFF0B1215) // dark surfaceBackground
internal val FeedTeal = Color(0xFF00A896)
internal val FeedPurple = Color(0xFFAF52DE)

internal val StoryRingColors = listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55))
internal val StoryRingViewed = listOf(Color(0xFFC2C2C2), Color(0xFFF0F0F0))
