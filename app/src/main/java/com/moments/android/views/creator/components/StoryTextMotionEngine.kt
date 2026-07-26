package com.moments.android.views.creator.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.floor
import kotlin.math.max

/** Frame Compose equivalente a las animaciones de capa de `StoryTextMotionEngine.swift`. */
data class StoryTextMotionFrame(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationY: Float = 0f,
    val rotationZ: Float = 0f,
    val alpha: Float = 1f,
    /** 0…1 — progreso de máscara typewriter (iOS `bounds.size.width`). */
    val typewriterProgress: Float = 1f,
)

/**
 * Port de `apply(to:motion:replayToken:)`.
 * `replayToken` reinicia la transición (≡ remove + add animation en CALayer).
 * `textLength` alimenta la duración del typewriter (≡ N caracteres en Swift).
 *
 * Aliases `jump`→bounce y `shimmer`→typewriter viven también en
 * `sanitizeStoryTextMotionRaw` (legacy).
 */
@Composable
fun rememberStoryTextMotionFrame(
    motionRaw: String,
    replayToken: Int,
    textLength: Int = 10,
): StoryTextMotionFrame {
    val motion = motionRaw.lowercase()
    val transition = rememberInfiniteTransition(label = "storyTextMotion_$replayToken")

    // MARK: pop — 1.2s, values/keyTimes de applyPop
    val popScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1_200
                1.00f at 0
                1.15f at 168 // 0.14
                0.94f at 336 // 0.28
                1.05f at 504 // 0.42
                1.00f at 648 // 0.54
                1.00f at 960 // 0.80 hold
                1.00f at 1_200
            },
        ),
        label = "storyTextPop",
    )

    // MARK: bounce — 1.3s group ty/sx/sy
    val bounceY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1_300
                0f at 0
                0f at 156 // 0.12
                -20f at 364 // 0.28
                -26f at 546 // 0.42
                -12f at 728 // 0.56
                0f at 910 // 0.70
                0f at 1_105 // 0.85
                0f at 1_300
            },
        ),
        label = "storyTextBounceY",
    )
    val bounceX by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1_300
                1.00f at 0
                1.12f at 156
                0.90f at 364
                1.00f at 546
                0.92f at 728
                1.15f at 910
                0.98f at 1_105
                1.00f at 1_300
            },
        ),
        label = "storyTextBounceX",
    )
    val bounceScaleY by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1_300
                1.00f at 0
                0.86f at 156
                1.14f at 364
                1.00f at 546
                1.10f at 728
                0.83f at 910
                1.02f at 1_105
                1.00f at 1_300
            },
        ),
        label = "storyTextBounceScaleY",
    )

    // MARK: wave — rotation.z en radianes + ty
    val waveRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1_200
                0f at 0
                0.06f at 300
                -0.06f at 600
                0.04f at 900
                0f at 1_200
            },
        ),
        label = "storyTextWaveRotation",
    )
    val waveY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1_200
                0f at 0
                -4f at 300
                4f at 600
                -2f at 900
                0f at 1_200
            },
        ),
        label = "storyTextWaveY",
    )

    // MARK: typewriter — duration max(1.2, N*0.15), autoreverses
    val n = max(1, textLength)
    val typewriterDurationMs = max(1_200, (n * 150))
    val typewriter by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(typewriterDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "storyTextTypewriter",
    )

    // MARK: reveal — opacity 0.2↔1.0, 0.65s, autoreverses
    val revealAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "storyTextReveal",
    )

    return when (motion) {
        "pop" -> StoryTextMotionFrame(scaleX = popScale, scaleY = popScale)
        "bounce", "jump" -> StoryTextMotionFrame(
            scaleX = bounceX,
            scaleY = bounceScaleY,
            translationY = bounceY,
        )
        "wave" -> StoryTextMotionFrame(translationY = waveY, rotationZ = waveRotation)
        "typewriter", "shimmer" -> StoryTextMotionFrame(typewriterProgress = typewriter)
        "reveal" -> StoryTextMotionFrame(alpha = revealAlpha)
        else -> StoryTextMotionFrame() // .none
    }
}

/** Aplicación del frame ≡ transform/alpha de CALayer. */
fun Modifier.storyTextMotion(frame: StoryTextMotionFrame): Modifier =
    graphicsLayer {
        scaleX = frame.scaleX
        scaleY = frame.scaleY
        translationY = frame.translationY
        // iOS rotation.z en radianes → Compose degrees
        rotationZ = Math.toDegrees(frame.rotationZ.toDouble()).toFloat()
        alpha = frame.alpha
    }

/**
 * Aproximación de la máscara discreta de `applyTypewriter`
 * (reveal por caracteres + hold/reverse del frame).
 */
fun storyTextForMotion(text: String, motionRaw: String, frame: StoryTextMotionFrame): String {
    val motion = motionRaw.lowercase()
    if (motion != "typewriter" && motion != "shimmer") return text
    val visible = floor(text.length * frame.typewriterProgress.toDouble()).toInt()
        .coerceIn(0, text.length)
    return text.take(visible)
}
