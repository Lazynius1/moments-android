package com.moments.android.views.components.hiddenlayers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moments.android.models.MomentHiddenLayer
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port de `HiddenLayerHintAppearanceView` (iOS):
 * núcleo + estrellas animadas, sin arco rotatorio.
 * B&W se adapta al canvas Moments (blanco en dark / negro en light).
 */
@Composable
fun HiddenLayerHintAppearanceView(
    type: MomentHiddenLayer.LayerType,
    shape: MomentHiddenLayer.LayerShape,
    style: MomentHiddenLayer.HintStyle,
    isSeen: Boolean,
    delaySec: Double = 0.0,
    isIntro: Boolean = false,
    isActive: Boolean = true,
    isAnimated: Boolean = true,
    showsParticles: Boolean = true,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedShape = shape

    val isDark = isSystemInDarkTheme()
    val starA = hintStarA(style, isDark)
    val starB = hintStarB(style, isDark)
    val usesAdaptive = style == MomentHiddenLayer.HintStyle.BLACK_AND_WHITE
    val coreColor = if (usesAdaptive) {
        (if (isDark) Color.White else Color.Black).copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.78f)
    }
    val radiusDp = when (type) {
        MomentHiddenLayer.LayerType.TEXT -> 16.dp
        MomentHiddenLayer.LayerType.AUDIO -> 14.dp
        MomentHiddenLayer.LayerType.IMAGE -> 18.dp
    }
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }

    var started by remember(isActive, isAnimated) { mutableStateOf(!isAnimated) }
    LaunchedEffect(isActive, isAnimated, delaySec) {
        if (!isActive) {
            started = false
            return@LaunchedEffect
        }
        if (!isAnimated) {
            started = true
            return@LaunchedEffect
        }
        started = false
        delay((delaySec * 1000).toLong().coerceAtLeast(0L))
        started = true
    }

    val showParticles = isActive && showsParticles && (isIntro || !isSeen)
    val infinite = rememberInfiniteTransition(label = "hlHint")
    val pulseRaw by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (isIntro) 1100 else 1300),
            RepeatMode.Reverse,
        ),
        label = "hlPulse",
    )
    val orbitPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart),
        label = "hlOrbit",
    )
    val glintRaw by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "hlGlint",
    )

    val pulse = if (started && isAnimated) pulseRaw else 0f
    val orbit = if (started && isAnimated) orbitPhase else 0f
    val glint = if (started && isAnimated) glintRaw else 0f

    val outerAlpha = if (usesAdaptive) {
        if (isDark) {
            if (pulse > 0.5f) 0.52f else 0.34f
        } else {
            if (pulse > 0.5f) 0.34f else 0.20f
        }
    } else {
        if (pulse > 0.5f) 0.52f else 0.34f
    }
    val midAlpha = if (usesAdaptive) {
        if (isDark) 0.18f else 0.08f
    } else {
        0.18f
    }
    val outerBlend = if (usesAdaptive && !isDark) BlendMode.Multiply else BlendMode.Plus
    val coreBlend = if (usesAdaptive && !isDark) BlendMode.SrcOver else BlendMode.Screen
    val scale = 0.94f + pulse * 0.12f
    val glintOpacity = 0.28f + glint * 0.68f

    Box(
        modifier
            .size(radiusDp * 2.5f)
            .drawBehind {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            starB.copy(alpha = outerAlpha),
                            starA.copy(alpha = midAlpha),
                            Color.Transparent,
                        ),
                        center = c,
                        radius = radiusPx * 1.75f,
                    ),
                    radius = radiusPx * 1.75f,
                    center = c,
                    blendMode = outerBlend,
                )
                withTransform({
                    scale(scale, scale, c)
                }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                coreColor,
                                starB.copy(alpha = if (isDark) 0.46f else 0.30f),
                                Color.Transparent,
                            ),
                            center = c,
                            radius = radiusPx,
                        ),
                        radius = radiusPx,
                        center = c,
                        blendMode = coreBlend,
                    )
                }
                val glintCenter = Offset(c.x - radiusPx * 0.4f, c.y - radiusPx * 0.4f)
                drawCircle(
                    color = (if (isDark) Color.White else starA).copy(alpha = glintOpacity),
                    radius = 3.5f,
                    center = glintCenter,
                )

                if (showParticles) {
                    for (index in 0 until 12) {
                        drawMagicStar(
                            index = index,
                            center = c,
                            radiusPx = radiusPx,
                            orbitPhase = orbit,
                            starA = starA,
                            starB = starB,
                            shadowAlpha = if (usesAdaptive && !isDark) 0.38f else 0.72f,
                        )
                    }
                }
            },
    )
}

private fun hintStarA(style: MomentHiddenLayer.HintStyle, isDark: Boolean): Color = when (style) {
    MomentHiddenLayer.HintStyle.BLACK_AND_WHITE -> if (isDark) Color.White else Color.Black
    MomentHiddenLayer.HintStyle.ACTUAL -> Color(1f, 0.92f, 0.62f)
    MomentHiddenLayer.HintStyle.POLAR -> Color(0.55f, 0.95f, 1f)
    MomentHiddenLayer.HintStyle.EMBER -> Color(1f, 0.55f, 0.28f)
    MomentHiddenLayer.HintStyle.ULTRAVIOLET -> Color(0.78f, 0.42f, 1f)
    MomentHiddenLayer.HintStyle.AURORA -> Color(0.42f, 1f, 0.78f)
    MomentHiddenLayer.HintStyle.ROSE -> Color(1f, 0.62f, 0.78f)
    MomentHiddenLayer.HintStyle.PLASMA -> Color(1f, 0.55f, 0.92f)
    MomentHiddenLayer.HintStyle.DAYLIGHT -> Color(0.86f, 0.94f, 1f)
}

private fun hintStarB(style: MomentHiddenLayer.HintStyle, isDark: Boolean): Color = when (style) {
    MomentHiddenLayer.HintStyle.BLACK_AND_WHITE -> if (isDark) Color.White else Color.Black
    MomentHiddenLayer.HintStyle.ACTUAL -> Color(0.98f, 0.82f, 0.42f)
    MomentHiddenLayer.HintStyle.POLAR -> Color(0.62f, 0.48f, 1f)
    MomentHiddenLayer.HintStyle.EMBER -> Color(1f, 0.78f, 0.18f)
    MomentHiddenLayer.HintStyle.ULTRAVIOLET -> Color(0.78f, 1f, 0.28f)
    MomentHiddenLayer.HintStyle.AURORA -> Color(0.22f, 0.82f, 0.95f)
    MomentHiddenLayer.HintStyle.ROSE -> Color(1f, 0.32f, 0.58f)
    MomentHiddenLayer.HintStyle.PLASMA -> Color(0.95f, 0.25f, 0.72f)
    MomentHiddenLayer.HintStyle.DAYLIGHT -> Color(0.35f, 0.72f, 1f)
}

private fun DrawScope.drawMagicStar(
    index: Int,
    center: Offset,
    radiusPx: Float,
    orbitPhase: Float,
    starA: Color,
    starB: Color,
    shadowAlpha: Float,
) {
    val sizes = floatArrayOf(6.5f, 4.0f, 7.5f, 3.5f, 5.5f, 4.5f, 7.0f, 3.8f, 6.0f, 5.0f, 7.2f, 3.2f)
    val starSize = sizes[index % sizes.size]
    val speed = 1f + (index % 3) * 0.2f
    val angle = (index * Math.PI * 2 / 12) + orbitPhase * Math.PI * 2 * speed
    val orbitRadius = radiusPx + 3f + (index % 3) * 2f
    val ox = cos(angle).toFloat() * orbitRadius
    val oy = sin(angle).toFloat() * orbitRadius
    val twinklePhase = orbitPhase * Math.PI * (8 + index % 5) + index * 1.3
    val twinkle = (0.28 + abs(sin(twinklePhase)) * 1.45).toFloat()
    val opacityPhase = orbitPhase * Math.PI * (6 + index % 4) + index * 0.9
    val opacity = (0.22 + abs(sin(opacityPhase)) * 0.78).toFloat()
    val fill = if (index % 3 == 0) starB else starA
    val points = if (index % 2 == 0) 4 else 5
    val starCenter = Offset(center.x + ox, center.y + oy)
    val rotationDeg = index * 16f + orbitPhase * 100f

    withTransform({
        translate(starCenter.x, starCenter.y)
        rotate(rotationDeg, Offset.Zero)
        scale(twinkle, twinkle, Offset.Zero)
    }) {
        val path = magicStarPath(starSize, points)
        drawPath(path, color = fill.copy(alpha = opacity * shadowAlpha * 0.55f))
        drawPath(path, color = fill.copy(alpha = opacity))
    }
}

private fun magicStarPath(size: Float, points: Int): Path {
    val outer = size / 2f
    val inner = outer * (if (points == 4) 0.28f else 0.38f)
    val count = maxOf(points, 3)
    val path = Path()
    for (index in 0 until count * 2) {
        val radius = if (index % 2 == 0) outer else inner
        val angle = index * Math.PI / count - Math.PI / 2
        val x = cos(angle).toFloat() * radius
        val y = sin(angle).toFloat() * radius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
