package com.moments.android.views.story.storystickers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.util.UUID

/**
 * Port de `AnimatedWeatherSticker` y sus partículas de clima de
 * `StoryStickers/StoryStickerEffects.swift`.
 */
@Composable
fun AnimatedWeatherSticker(
    weatherSymbol: String,
    temperature: String,
    modifier: Modifier = Modifier,
) {
    val animation = rememberInfiniteTransition(label = "weatherSticker")
    val phase by animation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = LinearEasing)),
        label = "weatherPhase",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = weatherColors(weatherSymbol).first().copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = temperature,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.shadow(2.dp),
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
            Text(weatherSymbol, fontSize = 20.sp)
            WeatherAnimationOverlay(weatherSymbol, phase)
        }
    }
}

@Composable
private fun WeatherAnimationOverlay(weatherSymbol: String, phase: Float) {
    when (weatherSymbol) {
        "☀️" -> SunAnimation(phase)
        "🌧️" -> RainAnimation(phase)
        "❄️" -> SnowAnimation(phase)
        "💨" -> WindAnimation(phase)
        "⛈️" -> ThunderAnimation(phase)
        "🌙" -> NightAnimation(phase)
    }
}

@Composable
private fun SunAnimation(phase: Float) {
    Box(Modifier.fillMaxSize()) {
        repeat(8) { index ->
            val angle = index * PI / 4
            val pulse = (0.5f + 0.5f * sin(phase + index * 0.5f)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (cos(angle) * 25).dp, y = (sin(angle) * 25).dp)
                    .size(4.dp)
                    .scale(pulse)
                    .alpha((0.3f + 0.7f * sin(phase + index * 0.3f)).coerceIn(0f, 1f))
                    .background(Color.Yellow.copy(alpha = 0.6f), RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun RainAnimation(phase: Float) {
    Box(Modifier.fillMaxSize()) {
        repeat(6) { index ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = ((index - 3) * 8).dp, y = (-20f + phase * 40f).dp)
                    .width(3.dp)
                    .height(6.dp)
                    .alpha((0.5f + 0.5f * sin(phase + index * 0.5f)).coerceIn(0f, 1f))
                    .background(Color.Blue.copy(alpha = 0.7f), RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun SnowAnimation(phase: Float) {
    Box(Modifier.fillMaxSize()) {
        repeat(5) { index ->
            Text(
                text = "❄️",
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = ((index - 2) * 12).dp, y = (-15f + phase * 30f).dp)
                    .rotate(phase * 360f)
                    .alpha((0.6f + 0.4f * sin(phase + index * 0.7f)).coerceIn(0f, 1f)),
            )
        }
    }
}

@Composable
private fun WindAnimation(phase: Float) {
    Box(Modifier.fillMaxSize()) {
        repeat(3) { index ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (phase * 15f + index * 10).dp, y = ((index - 1) * 5).dp)
                    .size(6.dp)
                    .alpha((0.4f + 0.6f * sin(phase + index * 0.8f)).coerceIn(0f, 1f))
                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun ThunderAnimation(phase: Float) {
    Box(Modifier.fillMaxSize()) {
        repeat(2) { index ->
            Text(
                text = "⚡",
                color = Color.Yellow,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = ((index - 0.5f) * 20).dp, y = (-8f + phase * 15f).dp)
                    .alpha((0.3f + 0.7f * sin(phase * 2f + index)).coerceIn(0f, 1f)),
            )
        }
    }
}

@Composable
private fun NightAnimation(phase: Float) {
    Box(Modifier.fillMaxSize()) {
        repeat(6) { index ->
            val angle = index * PI / 3
            Text(
                text = "⭐",
                fontSize = 6.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (cos(angle) * 20).dp, y = (sin(angle) * 20).dp)
                    .scale((0.3f + 0.7f * sin(phase + index * 0.8f)).coerceIn(0f, 1f))
                    .alpha((0.4f + 0.6f * sin(phase + index * 0.6f)).coerceIn(0f, 1f)),
            )
        }
    }
}

private fun weatherColors(symbol: String): List<Color> = when (symbol) {
    "☀️", "🌤️", "⛅" -> listOf(Color(0xFFFF9500), Color.Yellow)
    "🌥️", "☁️" -> listOf(Color.Gray, Color.Blue)
    "🌧️", "⛈️" -> listOf(Color.Blue, Color(0xFF5856D6))
    "❄️", "🌨️", "🥶" -> listOf(Color.Cyan, Color.Blue)
    "🔥" -> listOf(Color.Red, Color(0xFFFF9500))
    "💨" -> listOf(Color.White, Color.Gray)
    "🌙", "🌃" -> listOf(Color(0xFF5856D6), Color(0xFFAF52DE))
    "🌅" -> listOf(Color(0xFFFF9500), Color(0xFFFF2D55))
    "🌄" -> listOf(Color(0xFFFF9500), Color.Red)
    else -> listOf(Color(0xFFFF9500), Color.Yellow)
}

/** Datos y trayecto de una partícula de reacción, port de `FloatingHeart`. */
data class FloatingHeart(
    val emoji: String,
    val startX: Float,
    val startY: Float,
    val fontSize: Float = 44f,
    val rotation: Float = 0f,
    val delay: Long = 0L,
    val duration: Long = 2_000L,
    val lateralDrift: Float = 0f,
    val verticalTravel: Float = 400f,
    val peakScale: Float = 1.08f,
    val targetScale: Float = 1.1f,
    val rotationDelta: Float = 0f,
    val swayAmplitude: Float = 0f,
    val swayFrequency: Float = 0f,
    val id: String = UUID.randomUUID().toString(),
)

/** Port de `FloatingHeartsView`; no intercepta los gestos del visor. */
@Composable
fun FloatingHeartsView(
    hearts: List<FloatingHeart>,
    containerSize: DpSize = DpSize.Zero,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(containerSize)) {
        hearts.forEach { heart ->
            FloatingHeartParticleView(heart)
        }
    }
}

@Composable
private fun FloatingHeartParticleView(heart: FloatingHeart) {
    val progress = remember(heart.id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(heart.id) {
        progress.snapTo(0f)
        if (MotionPolicy.reduceMotion) {
            progress.animateTo(1f, tween(min(heart.duration, 1_400L).toInt()))
        } else {
            delay(heart.delay)
            progress.animateTo(1f, tween(heart.duration.toInt()))
        }
    }
    val value = progress.value
    val xOffset = if (MotionPolicy.reduceMotion) {
        value * heart.lateralDrift * 0.5f
    } else {
        sin(value * PI.toFloat() * heart.swayFrequency) * heart.swayAmplitude + value * heart.lateralDrift
    }
    val yOffset = if (MotionPolicy.reduceMotion) {
        -value * heart.verticalTravel * 0.55f
    } else {
        -value * heart.verticalTravel
    }
    val scale = when {
        MotionPolicy.reduceMotion && value < 0.2f -> value / 0.2f * heart.peakScale
        MotionPolicy.reduceMotion -> heart.peakScale
        value < 0.15f -> value / 0.15f * heart.peakScale
        else -> heart.peakScale + ((value - 0.15f) / 0.85f) * (heart.targetScale - heart.peakScale)
    }
    val alpha = when {
        value < 0.05f -> value / 0.05f
        value > 0.75f -> 1f - (value - 0.75f) / 0.25f
        else -> 1f
    }

    Text(
        text = heart.emoji,
        fontSize = heart.fontSize.sp,
        modifier = Modifier
            .offset(x = (heart.startX + xOffset).dp, y = (heart.startY + yOffset).dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha.coerceIn(0f, 1f)
                rotationZ = heart.rotation + value * heart.rotationDelta
                shadowElevation = if (heart.fontSize > 42f) 3.dp.toPx() else 1.5.dp.toPx()
            },
    )
}

/**
 * Port de `StickerVideoPlayer`.
 * El ExoPlayer se silencia y repite indefinidamente, igual que el AVPlayer de iOS.
 */
@Composable
fun StickerVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}

// `KeyboardIgnoringContainer` no requiere equivalente: Compose no hereda el
// ajuste automático de teclado de UIKit que ese wrapper neutraliza en iOS.
