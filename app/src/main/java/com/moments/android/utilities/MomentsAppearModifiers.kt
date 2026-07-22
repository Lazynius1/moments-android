package com.moments.android.utilities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object MomentsAppearSpring {
    /** Port de MotionPolicy.Spring.onboarding (response 0.55, damping 0.82). */
    val onboarding = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * Aparición de empty states: offset Y + fade-in con spring de onboarding.
 * Equivalente de `momentsEmptyStateAppear` (SwiftUI).
 */
fun Modifier.momentsEmptyStateAppear(
    appearedOffsetY: Float = 0f,
    initialOffsetY: Float = 14f,
): Modifier = composed {
    val offsetY = remember { Animatable(initialOffsetY) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (MotionPolicy.reduceMotion) {
            offsetY.snapTo(appearedOffsetY)
            alpha.snapTo(1f)
        } else {
            delay(80)
            launch { offsetY.animateTo(appearedOffsetY, MomentsAppearSpring.onboarding) }
            launch { alpha.animateTo(1f, MomentsAppearSpring.onboarding) }
        }
    }

    this.graphicsLayer {
        translationY = offsetY.value
        this.alpha = alpha.value
    }
}
