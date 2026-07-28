package com.moments.android.views.feed.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Port de `MapWeatherEffects.swift` / `WeatherEffectsUIView`.
 *
 * MARK: Lluvia (2 planos) · Nieve (deriva + spin) · Tormenta (lluvia + flash).
 */

@Composable
fun MapWeatherEffects(
    weather: WeatherData?,
    modifier: Modifier = Modifier,
) {
    if (weather == null) return
    MapWeatherEffectsView(weather = weather, modifier = modifier)
}

@Composable
fun MapWeatherEffectsView(
    weather: WeatherData,
    modifier: Modifier = Modifier,
) {
    if (MotionPolicy.reduceMotion || MotionPolicy.maxParticleCount <= 0) return
    when (weather.condition) {
        WeatherCondition.Rain,
        WeatherCondition.Snow,
        WeatherCondition.Thunderstorm,
        -> Unit
        else -> return
    }

    val density = LocalDensity.current
    val budget = MotionPolicy.maxParticleCount.toFloat()
    val intensity = rainIntensity(weather)
    val isThunder = weather.condition == WeatherCondition.Thunderstorm
    val isSnow = weather.condition == WeatherCondition.Snow

    var particles by remember(weather.condition, weather.precipitation, weather.isNight) {
        mutableStateOf(buildParticles(weather, budget, intensity))
    }
    var flashAlpha by remember { mutableFloatStateOf(0f) }
    var flashCenterX by remember { mutableFloatStateOf(0.5f) }
    var flashStartedAt by remember { mutableLongStateOf(0L) }
    var nextLightningAt by remember { mutableLongStateOf(0L) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(weather.condition, weather.precipitation, weather.isNight) {
        particles = buildParticles(weather, budget, intensity)
        flashAlpha = 0f
        flashStartedAt = 0L
        nextLightningAt = if (isThunder) {
            System.currentTimeMillis() + Random.nextLong(4_000, 10_001)
        } else {
            0L
        }
    }

    LaunchedEffect(weather.condition, isThunder) {
        var lastFrame = 0L
        while (true) {
            withFrameMillis { now ->
                val dt = if (lastFrame == 0L) {
                    1f / 60f
                } else {
                    ((now - lastFrame) / 1000f).coerceIn(0.008f, 0.033f)
                }
                lastFrame = now

                val w = canvasWidth
                val h = canvasHeight
                if (w > 0f && h > 0f) {
                    particles = particles.map { p ->
                        val moved = p.copy(
                            x = p.x + p.vx * dt,
                            y = p.y + p.vy * dt,
                            rotationDeg = p.rotationDeg + p.spinDegPerSec * dt,
                            vx = p.vx + p.ax * dt,
                        )
                        if (moved.y > h + 48f || moved.x < -96f || moved.x > w + 96f) {
                            respawn(moved, w, isSnow)
                        } else {
                            moved
                        }
                    }
                }

                if (isThunder && nextLightningAt > 0L) {
                    val wall = System.currentTimeMillis()
                    if (wall >= nextLightningAt) {
                        flashStartedAt = wall
                        flashCenterX = Random.nextFloat() * 0.6f + 0.2f
                        nextLightningAt = wall + Random.nextLong(4_000, 10_001)
                    }
                    flashAlpha = lightningPulse(wall - flashStartedAt)
                } else {
                    flashAlpha = 0f
                }
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                canvasWidth = it.width.toFloat()
                canvasHeight = it.height.toFloat()
            },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        if (isSnow) {
            particles.forEach { p ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = p.alpha),
                            Color.White.copy(alpha = 0f),
                        ),
                        center = Offset(p.x, p.y),
                        radius = p.size * 1.6f,
                    ),
                    radius = p.size,
                    center = Offset(p.x, p.y),
                )
            }
        } else {
            val streakColor = Color(red = 0.75f, green = 0.88f, blue = 1f)
            val farW = with(density) { 1.5.dp.toPx() }
            val nearW = with(density) { 2.5.dp.toPx() }
            particles.forEach { p ->
                val streakW = if (p.far) farW else nearW
                rotate(degrees = p.rotationDeg, pivot = Offset(p.x, p.y)) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                streakColor.copy(alpha = p.alpha),
                            ),
                        ),
                        topLeft = Offset(p.x - streakW / 2f, p.y - p.size / 2f),
                        size = Size(streakW, p.size),
                        cornerRadius = CornerRadius(streakW / 2f, streakW / 2f),
                    )
                }
            }
        }

        if (flashAlpha > 0.01f) {
            val cx = w * flashCenterX
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f * flashAlpha),
                        Color.White.copy(alpha = 0.18f * flashAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx, 0f),
                    radius = max(w, h) * 0.95f,
                ),
                size = size,
            )
        }
    }
}

// MARK: - Particle model

private data class WeatherParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val ax: Float,
    val size: Float,
    val alpha: Float,
    val rotationDeg: Float,
    val spinDegPerSec: Float,
    val far: Boolean,
)

private fun rainIntensity(weather: WeatherData): Float {
    if (weather.condition == WeatherCondition.Thunderstorm) return 1f
    val normalized = min(max(weather.precipitation / 8.0, 0.0), 1.0).toFloat()
    return 0.4f + normalized * 0.6f
}

private fun buildParticles(
    weather: WeatherData,
    budget: Float,
    intensity: Float,
): List<WeatherParticle> = when (weather.condition) {
    WeatherCondition.Snow -> {
        val farCount = (budget * 0.25f).toInt().coerceAtLeast(4)
        val nearCount = (budget * 0.12f).toInt().coerceAtLeast(2)
        List(farCount) { snowParticle(far = true) } + List(nearCount) { snowParticle(far = false) }
    }
    WeatherCondition.Rain, WeatherCondition.Thunderstorm -> {
        val farCount = (budget * 0.45f * intensity).toInt().coerceAtLeast(6)
        val nearCount = (budget * 0.3f * intensity).toInt().coerceAtLeast(4)
        List(farCount) { rainParticle(far = true) } + List(nearCount) { rainParticle(far = false) }
    }
    else -> emptyList()
}

/** iOS emissionLongitude = π + 0.12 → caída casi vertical con inclinación. */
private fun rainParticle(far: Boolean): WeatherParticle {
    val angle = (PI + 0.12).toFloat()
    val speed = if (far) Random.nextFloat() * 80f + 280f else Random.nextFloat() * 160f + 480f
    return WeatherParticle(
        x = Random.nextFloat() * 1400f,
        y = -Random.nextFloat() * 500f,
        vx = sin(angle) * speed * 0.08f,
        vy = kotlin.math.abs(cos(angle)) * speed,
        ax = 0f,
        size = if (far) Random.nextFloat() * 6f + 10f else Random.nextFloat() * 10f + 18f,
        alpha = if (far) 0.35f else 0.55f,
        rotationDeg = Math.toDegrees(0.12).toFloat(),
        spinDegPerSec = 0f,
        far = far,
    )
}

private fun snowParticle(far: Boolean): WeatherParticle {
    val speed = if (far) Random.nextFloat() * 24f + 14f else Random.nextFloat() * 50f + 30f
    return WeatherParticle(
        x = Random.nextFloat() * 1400f,
        y = -Random.nextFloat() * 300f,
        vx = Random.nextFloat() * 20f - 10f,
        vy = speed,
        ax = if (far) 6f else -8f,
        size = if (far) Random.nextFloat() * 3f + 3f else Random.nextFloat() * 5f + 6f,
        alpha = if (far) 0.5f else 0.85f,
        rotationDeg = Random.nextFloat() * 360f,
        spinDegPerSec = if (far) {
            (Random.nextFloat() * 0.6f + 0.15f) * (180f / PI.toFloat())
        } else {
            (Random.nextFloat() * 1.0f + 0.25f) * (180f / PI.toFloat())
        },
        far = far,
    )
}

private fun respawn(p: WeatherParticle, width: Float, snow: Boolean): WeatherParticle {
    val base = if (snow) snowParticle(p.far) else rainParticle(p.far)
    return base.copy(
        x = Random.nextFloat() * (width + 120f) - 60f,
        y = -Random.nextFloat() * 80f - 12f,
        size = p.size,
        alpha = p.alpha,
        far = p.far,
    )
}

/** iOS keyValues [0,1,0.1,0.6,0] @ keyTimes [0,0.08,0.25,0.4,1], duration 0.55s. */
private fun lightningPulse(elapsedMs: Long): Float {
    if (elapsedMs < 0L || elapsedMs > 550L) return 0f
    val t = elapsedMs / 550f
    return when {
        t < 0.08f -> t / 0.08f
        t < 0.25f -> 1f - ((t - 0.08f) / 0.17f) * 0.9f
        t < 0.40f -> 0.1f + ((t - 0.25f) / 0.15f) * 0.5f
        else -> 0.6f * (1f - (t - 0.40f) / 0.60f)
    }.coerceIn(0f, 1f)
}
