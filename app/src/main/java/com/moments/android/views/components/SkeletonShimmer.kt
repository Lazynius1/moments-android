package com.moments.android.views.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import com.moments.android.services.performance.MotionPolicy

/** Port de `SkeletonShimmer.swift`: pulso blanco suave superpuesto al contenido. */
fun Modifier.shimmer(isAnimating: Boolean): Modifier = composed {
    val shouldAnimate = isAnimating && !MotionPolicy.reduceMotion
    val pulse = if (shouldAnimate) rememberInfiniteTransition(label = "skeletonPulse").animateFloat(
        initialValue = .04f,
        targetValue = .22f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonPulseAlpha",
    ).value else 0f
    drawWithContent {
        drawContent()
        if (shouldAnimate) drawRect(Color.White.copy(alpha = pulse))
    }
}
