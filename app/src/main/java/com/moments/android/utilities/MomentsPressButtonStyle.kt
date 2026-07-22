package com.moments.android.utilities

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.moments.android.services.performance.MotionPolicy

/**
 * Estilo de press unificado: escala + opacidad con spring y haptic opcional al tocar.
 * Encadenar con `.clickable(interactionSource, indication = null)` compartiendo la misma source.
 */
object MomentsPressDefaults {
    enum class PressHaptic {
        NONE,
        SELECTION,
        LIGHT,
        MEDIUM;

        fun fire() {
            when (this) {
                NONE -> Unit
                SELECTION -> HapticManager.shared.selection()
                LIGHT -> HapticManager.shared.lightImpact()
                MEDIUM -> HapticManager.shared.mediumImpact()
            }
        }
    }

    val momentsPress = MomentsPressSpec()
    val momentsPressSubtle = MomentsPressSpec(scale = 0.96f, pressedOpacity = 0.92f, haptic = PressHaptic.LIGHT)
    val momentsPressIcon = MomentsPressSpec(scale = 0.92f, pressedOpacity = 0.9f, haptic = PressHaptic.SELECTION)
}

data class MomentsPressSpec(
    val scale: Float = 0.94f,
    val pressedOpacity: Float = 0.88f,
    val haptic: MomentsPressDefaults.PressHaptic = MomentsPressDefaults.PressHaptic.NONE,
)

fun Modifier.momentsPress(
    interactionSource: MutableInteractionSource,
    spec: MomentsPressSpec = MomentsPressDefaults.momentsPress,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) spec.scale else 1f,
        animationSpec = if (MotionPolicy.reduceMotion) snap() else spring(dampingRatio = 0.72f),
        label = "momentsPressScale",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPressed) spec.pressedOpacity else 1f,
        animationSpec = if (MotionPolicy.reduceMotion) snap() else spring(dampingRatio = 0.72f),
        label = "momentsPressAlpha",
    )

    LaunchedEffect(isPressed) {
        if (isPressed && spec.haptic != MomentsPressDefaults.PressHaptic.NONE) {
            spec.haptic.fire()
        }
    }

    graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
        alpha = animatedAlpha
    }
}

fun Modifier.momentsPress(
    spec: MomentsPressSpec = MomentsPressDefaults.momentsPress,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    momentsPress(interactionSource, spec)
}

fun Modifier.momentsPress(
    scale: Float = 0.94f,
    pressedOpacity: Float = 0.88f,
    haptic: MomentsPressDefaults.PressHaptic = MomentsPressDefaults.PressHaptic.NONE,
): Modifier = momentsPress(MomentsPressSpec(scale, pressedOpacity, haptic))

fun Modifier.momentsPressSubtle(): Modifier =
    momentsPress(MomentsPressDefaults.momentsPressSubtle)

fun Modifier.momentsPressIcon(): Modifier =
    momentsPress(MomentsPressDefaults.momentsPressIcon)
