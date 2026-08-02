package com.moments.android.views.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import com.moments.android.services.performance.MotionPolicy

/**
 * Host único de skeleton / content placeholder (phone).
 *
 * Layout = paridad iOS; color/motion = M3 (`colorScheme.onSurface` + pulse).
 * No usar Wear `placeholder` / libs de terceros.
 *
 * Ref: progress/loading en m3.material.io (no hay Skeleton phone oficial).
 */

/** Fill base del placeholder — ≡ iOS white/black 0.08/0.06 sobre canvas. */
@Composable
fun rememberMomentsSkeletonColor(): Color {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isDark = isSystemInDarkTheme()
    return remember(onSurface, isDark) {
        onSurface.copy(alpha = if (isDark) 0.08f else 0.06f)
    }
}

/**
 * Port de `SkeletonShimmer.swift`: pulso suave superpuesto al contenido.
 * Respeta [MotionPolicy.reduceMotion].
 */
fun Modifier.shimmer(isAnimating: Boolean): Modifier = composed {
    val shouldAnimate = isAnimating && !MotionPolicy.reduceMotion
    val pulseColor = MaterialTheme.colorScheme.onSurface
    val pulse = if (shouldAnimate) {
        rememberInfiniteTransition(label = "skeletonPulse").animateFloat(
            initialValue = 0.04f,
            targetValue = 0.14f,
            animationSpec = infiniteRepeatable(
                tween(1_400, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "skeletonPulseAlpha",
        ).value
    } else {
        0f
    }
    drawWithContent {
        drawContent()
        if (shouldAnimate) drawRect(pulseColor.copy(alpha = pulse))
    }
}
