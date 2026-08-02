package com.moments.android.views.feed

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.moments.android.views.shared.ControlDark
import com.moments.android.views.shared.ControlLight
import com.moments.android.views.shared.Ink
import com.moments.android.views.shared.MomentsBrandColors
import com.moments.android.views.shared.Surface

/**
 * Port de `AdaptiveColors` (MomentRailComponents.swift) + tokens Android de control.
 *
 * Canvas = [Ink]/[Surface]. Controles = [controlSurface] (elevados; no alpha sobre canvas).
 * Textos: [primary] / [secondary] / [tertiary] / [placeholder] — evitar `Color.Gray` suelto.
 * Accent feed = `007AFF` (iOS), no [MaterialTheme.colorScheme.primary] (púrpura marca).
 */
data class AdaptiveColors(
    val isDark: Boolean,
) {
    /** iOS: dark .black / light .white */
    val background: Color
        get() = if (isDark) Color.Black else Color.White

    /** iOS: dark 0B1215 / light FAF9F6 — fondo del feed (`modernBackgroundView`). */
    val surfaceBackground: Color
        get() = if (isDark) Ink else Surface

    /**
     * Fill de inputs / botones chrome / pills.
     * Opaco y distinto del canvas (en Android la transparencia iOS se ve mal).
     */
    val controlSurface: Color
        get() = if (isDark) ControlDark else ControlLight

    /** Borde sutil sobre [controlSurface]. */
    val controlStroke: Color
        get() = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    val primary: Color
        get() = if (isDark) Color.White else Color.Black

    val secondary: Color
        get() = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.68f)

    val tertiary: Color
        get() = if (isDark) Color.White.copy(alpha = 0.48f) else Color.Black.copy(alpha = 0.42f)

    /** Hint / placeholder de campos. */
    val placeholder: Color
        get() = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.38f)

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

    /** iOS: ChatAdaptiveColors.chatBackground — 3× canvas ([Ink] / [Surface]). */
    val chatBackground: List<Color>
        get() {
            val canvas = surfaceBackground
            return listOf(canvas, canvas, canvas)
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
    // Ancla a colorScheme.background para drift-check vs MomentsTheme (mismo Ink/Surface).
    val themeBackground = MaterialTheme.colorScheme.background
    return remember(isDark, themeBackground) { AdaptiveColors(isDark) }
}

// Aliases legacy del feed (mismos tokens que [Ink]/[Surface]).
internal val FeedCanvas = Surface
internal val FeedInk = Ink
internal val FeedTeal = Color(0xFF00A896)
internal val FeedPurple = MomentsBrandColors.purple

internal val StoryRingColors = MomentsBrandColors.storyRing
internal val StoryRingViewed = listOf(Color(0xFFC2C2C2), Color(0xFFF0F0F0))
