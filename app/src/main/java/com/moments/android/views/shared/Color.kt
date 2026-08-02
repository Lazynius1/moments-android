package com.moments.android.views.shared

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Tokens de marca para [MomentsTheme] / Material3 `colorScheme`.
 *
 * Canvas ink/paper alineado con [com.moments.android.views.feed.AdaptiveColors.surfaceBackground].
 * Controles (inputs/botones) = [ControlDark]/[ControlLight] — elevados vs canvas
 * (en Android no hay Liquid Glass; el fill del canvas se ve “invisible”).
 * `Accent` (púrpura Material `#7251C7`) ≠ marca Moments del story ring.
 */
val Ink = Color(0xFF0B1215)
val Surface = Color(0xFFFAF9F6)
/** Elevado sobre [Ink] — chrome / inputs / botones (modo oscuro). */
val ControlDark = Color(0xFF151D21)
/** Elevado sobre [Surface] — chrome / inputs / botones (modo claro). */
val ControlLight = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFF4F2EF)
val Accent = Color(0xFF7251C7)
val AccentSoft = Color(0xFFECE4FF)
val Outline = Color(0xFFE5E0E6)

/**
 * Colores de marca Moments ≡ degradado story ring
 * (azul → púrpura sistema → rosa).
 */
object MomentsBrandColors {
    val blue = Color(0xFF007AFF)
    val purple = Color(0xFFAF52DE)
    val pink = Color(0xFFFF2D55)

    val storyRing: List<Color> = listOf(blue, purple, pink)

    val storyRingBrush: Brush
        get() = Brush.linearGradient(storyRing)
}
