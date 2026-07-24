package com.moments.android.views.creator.components

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

/** Frame Compose equivalente a las animaciones de capa de `StoryTextMotionEngine.swift`. */
data class StoryTextMotionFrame(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationY: Float = 0f,
    val rotationZ: Float = 0f,
    val alpha: Float = 1f,
    val typewriterProgress: Float = 1f,
)

/**
 * Port de `apply(to:motion:replayToken:)`.
 * `replayToken` reinicia la transición cuando se vuelve a abrir/reproducir el
 * overlay, como Swift elimina y añade de nuevo la animación de layer.
 */
@Composable
fun rememberStoryTextMotionFrame(
    motionRaw: String,
    replayToken: Int,
): StoryTextMotionFrame {
    val transition = rememberInfiniteTransition(label = "storyTextMotion_$replayToken")
    val popScale by transition.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 1_200
            1.15f at 168
            .94f at 336
            1.05f at 504
            1f at 648
        }),
        label = "storyTextPop",
    )
    val bounceY by transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes {
            durationMillis = 1_300
            -20f at 364
            -26f at 546
            -12f at 728
            0f at 910
        }),
        label = "storyTextBounceY",
    )
    val bounceX by transition.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 1_300
            1.12f at 156; .90f at 364; 1f at 546; .92f at 728; 1.15f at 910; .98f at 1_105
        }),
        label = "storyTextBounceX",
    )
    val bounceScaleY by transition.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 1_300
            .86f at 156; 1.14f at 364; 1f at 546; 1.10f at 728; .83f at 910; 1.02f at 1_105
        }),
        label = "storyTextBounceScaleY",
    )
    val waveRotation by transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes {
            durationMillis = 1_200
            .06f at 300; -.06f at 600; .04f at 900
        }),
        label = "storyTextWaveRotation",
    )
    val waveY by transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes {
            durationMillis = 1_200
            -4f at 300; 4f at 600; -2f at 900
        }),
        label = "storyTextWaveY",
    )
    val typewriter by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            keyframes { durationMillis = 1_500; 1f at 1_100 },
            repeatMode = RepeatMode.Reverse,
        ),
        label = "storyTextTypewriter",
    )
    val revealAlpha by transition.animateFloat(
        .2f, 1f,
        infiniteRepeatable(tween(650), repeatMode = RepeatMode.Reverse),
        label = "storyTextReveal",
    )

    return when (motionRaw.lowercase()) {
        "pop" -> StoryTextMotionFrame(scaleX = popScale, scaleY = popScale)
        "bounce", "jump" -> StoryTextMotionFrame(scaleX = bounceX, scaleY = bounceScaleY, translationY = bounceY)
        "wave" -> StoryTextMotionFrame(translationY = waveY, rotationZ = waveRotation)
        "typewriter", "shimmer" -> StoryTextMotionFrame(typewriterProgress = typewriter)
        "reveal" -> StoryTextMotionFrame(alpha = revealAlpha)
        else -> StoryTextMotionFrame()
    }
}

/** Aplicación del frame sobre un contenedor, equivalente a transform/alpha de CALayer. */
fun Modifier.storyTextMotion(frame: StoryTextMotionFrame): Modifier =
    graphicsLayer {
        scaleX = frame.scaleX
        scaleY = frame.scaleY
        translationY = frame.translationY
        rotationZ = Math.toDegrees(frame.rotationZ.toDouble()).toFloat()
        alpha = frame.alpha
    }

/** Máscara discreta de `applyTypewriter`, con hold/reverse gestionado por el frame. */
fun storyTextForMotion(text: String, motionRaw: String, frame: StoryTextMotionFrame): String {
    if (motionRaw.lowercase() !in setOf("typewriter", "shimmer")) return text
    val visible = floor(text.length * frame.typewriterProgress).toInt().coerceIn(0, text.length)
    return text.take(visible)
}
