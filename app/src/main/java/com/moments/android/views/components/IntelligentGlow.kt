package com.moments.android.views.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy

/**
 * Port de `IntelligentGlow.swift` — halo angular de tres capas (Apple Intelligence style).
 *
 * iOS: TimelineView 30fps, ciclo 3s → rotación 0…360.
 * Capas: stroke 6 + blur 12 @0.6 · stroke 3.5 + blur 4 @0.9 · stroke 1.5 sin blur @1.0.
 * `MotionPolicy.reduceMotion` → sin animación (rotation = 0).
 */
@Composable
fun IntelligentGlow(
    isFocused: Boolean,
    cornerRadius: Dp,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (colors.isEmpty()) return

    val shouldAnimate = isFocused && !MotionPolicy.reduceMotion
    // Siempre recordar la transición (regla Compose); solo aplicamos el valor si anima.
    val transition = rememberInfiniteTransition(label = "intelligentGlow")
    val animatedRotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing), RepeatMode.Restart),
        label = "intelligentGlowRotation",
    ).value
    val rotation = if (shouldAnimate) animatedRotation else 0f

    val palette = remember(colors) { colors }
    val primary = palette.first()
    val secondary = palette.getOrElse(1) { primary }
    val looped = remember(palette) { palette + primary }
    val highlight = remember(primary, secondary) {
        listOf(
            Color.White.copy(alpha = 0.8f),
            primary.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.8f),
            secondary.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.8f),
        )
    }

    // iOS: cuando !isFocused → lineWidth 0 / opacity 0 (invisible pero en jerarquía).
    if (!isFocused) return

    Canvas(modifier) {
        val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        val rectSize = Size(this.size.width, this.size.height)
        val topLeft = Offset.Zero

        rotate(rotation, pivot = center) {
            // Capa 1: blur 12, lineWidth 6, opacity 0.6
            drawBlurredRoundRectStroke(
                colors = looped,
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = radius,
                strokeWidthPx = 6.dp.toPx(),
                blurRadiusPx = 12.dp.toPx(),
                alpha = 0.6f,
            )
            // Capa 2: blur 4, lineWidth 3.5, opacity 0.9
            drawBlurredRoundRectStroke(
                colors = looped,
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = radius,
                strokeWidthPx = 3.5.dp.toPx(),
                blurRadiusPx = 4.dp.toPx(),
                alpha = 0.9f,
            )
            // Capa 3: sin blur, lineWidth 1.5, opacity 1.0
            drawRoundRect(
                brush = Brush.sweepGradient(highlight, center),
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = radius,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

/**
 * Stroke redondeado con BlurMaskFilter — equivalente a `.stroke(...).blur(radius:)` de SwiftUI.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlurredRoundRectStroke(
    colors: List<Color>,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
    strokeWidthPx: Float,
    blurRadiusPx: Float,
    alpha: Float,
) {
    // Aproximación: sweep como shader nativo + mask blur.
    // Si el canvas software no soporta blur, cae al stroke Compose sin blur.
    drawIntoCanvas { canvas ->
        val framework = canvas.nativeCanvas
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = strokeWidthPx
            this.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            shader = android.graphics.SweepGradient(
                center.x,
                center.y,
                colors.map { it.toArgb() }.toIntArray(),
                null,
            )
            maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
        val path = android.graphics.Path().apply {
            addRoundRect(
                android.graphics.RectF(
                    topLeft.x,
                    topLeft.y,
                    topLeft.x + size.width,
                    topLeft.y + size.height,
                ),
                cornerRadius.x,
                cornerRadius.y,
                android.graphics.Path.Direction.CW,
            )
        }
        runCatching { framework.drawPath(path, paint) }
            .onFailure {
                // Fallback sin blur hardware
                drawRoundRect(
                    brush = Brush.sweepGradient(colors, center),
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = cornerRadius,
                    alpha = alpha,
                    style = Stroke(width = strokeWidthPx),
                )
            }
    }
}
