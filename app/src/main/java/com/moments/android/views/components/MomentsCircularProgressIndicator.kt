package com.moments.android.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.views.shared.MomentsBrandColors

/**
 * Spinner Moments: el color rota por el degradado del story ring
 * (`007AFF` → `AF52DE` → `FF2D55` → …).
 *
 * Usar en lugar de [CircularProgressIndicator] sin color (hereda primary
 * morado `#7251C7` del theme).
 */
@Composable
fun MomentsCircularProgressIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = Color.Transparent,
) {
    val palette = MomentsBrandColors.storyRing
    val transition = rememberInfiniteTransition(label = "momentsProgress")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "momentsProgressT",
    )
    val tint = colorAlongPalette(palette, t)

    CircularProgressIndicator(
        modifier = modifier,
        color = tint,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        strokeCap = StrokeCap.Round,
    )
}

/** Interpola a lo largo de [palette] en ciclo cerrado (último → primero). */
private fun colorAlongPalette(palette: List<Color>, t: Float): Color {
    if (palette.isEmpty()) return Color.White
    if (palette.size == 1) return palette[0]
    val segments = palette.size
    val scaled = (t.coerceIn(0f, 1f) * segments).let { if (it >= segments) 0f else it }
    val index = scaled.toInt().coerceIn(0, segments - 1)
    val frac = scaled - index
    val from = palette[index]
    val to = palette[(index + 1) % segments]
    return lerp(from, to, frac)
}
