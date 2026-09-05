package com.moments.android.views.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Light is composited only onto the caption glyphs, never across its background. */
@Composable
internal fun Modifier.captionTranslationEffect(active: Boolean): Modifier {
    val sweep = remember { Animatable(-0.65f) }
    LaunchedEffect(active) {
        sweep.snapTo(-0.65f)
        while (active) {
            if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
                sweep.snapTo(-0.65f)
                delay(250)
            } else {
                sweep.animateTo(1.1f, tween(1800, easing = LinearEasing))
                sweep.snapTo(-0.65f)
            }
        }
    }
    return this.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        alpha = if (active) 0.64f else 1f
    }.drawWithContent {
        drawContent()
        if (active && sweep.value > -0.65f) {
            val start = size.width * sweep.value
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.95f), Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + size.width * 0.6f, 0f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}

internal fun Modifier.captionTranslationReveal(progress: Float, translated: Boolean): Modifier = graphicsLayer {
    alpha = progress
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        val radius = if (translated) (1f - progress) * 2.5f * density else 0f
        renderEffect = if (radius > 0.01f) android.graphics.RenderEffect.createBlurEffect(
            radius, radius, android.graphics.Shader.TileMode.DECAL,
        ).asComposeRenderEffect() else null
    }
}
