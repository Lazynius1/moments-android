package com.moments.android.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy

/** Port de `IntelligentGlow.swift`: halo angular de tres capas para controles enfocados. */
@Composable
fun IntelligentGlow(
    isFocused: Boolean,
    cornerRadius: Dp,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (!isFocused || colors.isEmpty()) return
    val animate = !MotionPolicy.reduceMotion
    val rotation = if (animate) rememberInfiniteTransition(label = "intelligentGlow").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing), RepeatMode.Restart),
        label = "intelligentGlowRotation",
    ).value else 0f
    val primary = colors.first()
    val secondary = colors.getOrElse(1) { primary }

    Canvas(modifier) {
        val inset = 3.5.dp.toPx()
        val radius = CornerRadius(cornerRadius.toPx())
        val topLeft = Offset(inset, inset)
        val size = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
        rotate(rotation, pivot = center) {
            drawRoundRect(brush = Brush.sweepGradient(colors + primary, center), topLeft = topLeft, size = size, cornerRadius = radius, alpha = .60f, style = Stroke(6.dp.toPx()))
            drawRoundRect(brush = Brush.sweepGradient(colors + primary, center), topLeft = topLeft, size = size, cornerRadius = radius, alpha = .90f, style = Stroke(3.5.dp.toPx()))
            drawRoundRect(brush = Brush.sweepGradient(listOf(Color.White.copy(.8f), primary.copy(.5f), Color.White.copy(.8f), secondary.copy(.5f), Color.White.copy(.8f)), center), topLeft = topLeft, size = size, cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
        }
    }
}
