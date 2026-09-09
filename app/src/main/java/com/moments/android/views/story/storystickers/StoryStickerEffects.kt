package com.moments.android.views.story.storystickers

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.moments.android.views.story.storyviewer.LocalStoryExportVideoFrames
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import com.moments.android.extensions.fromHex
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.MomentsAudioSession
import com.moments.android.views.components.MomentsTapCycleForeground
import com.moments.android.views.components.TapCycleForegroundText
import com.moments.android.views.components.momentsTapCycleStickerBackground
import com.moments.android.views.components.momentsTapCycleStickerForeground
import com.moments.android.views.components.momentsTapCycleStickerStroke
import com.moments.android.views.components.momentsTapCycleStickerStrokeWidth
import com.moments.android.views.components.weatherStickerImageVector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import java.util.UUID

/**
 * Port de `AnimatedWeatherSticker` — cápsula tap-cycle como hashtag/hora.
 */
@Composable
fun AnimatedWeatherSticker(
    weatherSymbol: String,
    temperature: String,
    styleVariant: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val foreground = momentsTapCycleStickerForeground(isDark, styleVariant)
    val bg = momentsTapCycleStickerBackground(isDark, styleVariant)
    val stroke = momentsTapCycleStickerStroke(isDark, styleVariant)
    val strokeW = momentsTapCycleStickerStrokeWidth(styleVariant)
    val iconTint = when (foreground) {
        is MomentsTapCycleForeground.Solid -> foreground.color
        is MomentsTapCycleForeground.Rainbow -> Color.fromHex("FF5F6D")
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .then(
                if (strokeW > 0.dp) Modifier.border(strokeW, stroke, RoundedCornerShape(percent = 50))
                else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = weatherStickerImageVector(weatherSymbol),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        TapCycleForegroundText(
            text = temperature,
            foreground = foreground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
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

/**
 * Port de `FloatingHeartsView`.
 * Sin pointer handlers → hits pasan al visor (≡ `.allowsHitTesting(false)`).
 * `onHeartExpired` ≡ callback de `StoryReactionBurst.emit` tras delay+duration+0.2s.
 */
@Composable
fun FloatingHeartsView(
    hearts: List<FloatingHeart>,
    containerSize: DpSize = DpSize.Zero,
    onHeartExpired: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(containerSize)) {
        hearts.forEach { heart ->
            FloatingHeartParticleView(heart = heart, onExpired = onHeartExpired)
        }
    }
}

@Composable
private fun FloatingHeartParticleView(
    heart: FloatingHeart,
    onExpired: (String) -> Unit,
) {
    val progress = remember(heart.id) { androidx.compose.animation.core.Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(heart.id) {
        // ≡ iOS `DispatchQueue.main.asyncAfter(deadline: .now() + delay + duration + 0.2)`
        launch {
            delay(heart.delay + heart.duration + 200L)
            onExpired(heart.id)
        }
        progress.snapTo(0f)
        if (MotionPolicy.reduceMotion) {
            // ≡ iOS `withAnimation(.easeOut(duration: min(heart.duration, 1.4)))`
            progress.animateTo(
                1f,
                tween(min(heart.duration, 1_400L).toInt(), easing = EaseOut),
            )
        } else {
            delay(heart.delay)
            progress.animateTo(
                1f,
                tween(heart.duration.toInt(), easing = EaseOut),
            )
        }
    }
    val value = progress.value
    // Coordenadas en dp (≡ puntos iOS); verticalTravel/lateralDrift vienen en dp desde emit.
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
    val shadowRadius = if (heart.fontSize > 42f) 3.dp else 1.5.dp

    // Box transforma (position/scale/α/rot); Text lleva sombra ≡ iOS shadow luego flight modifier
    Box(
        modifier = Modifier.graphicsLayer {
            val pxX = with(density) { (heart.startX + xOffset).dp.toPx() }
            val pxY = with(density) { (heart.startY + yOffset).dp.toPx() }
            translationX = pxX - size.width / 2f
            translationY = pxY - size.height / 2f
            scaleX = scale
            scaleY = scale
            this.alpha = alpha.coerceIn(0f, 1f)
            rotationZ = heart.rotation + value * heart.rotationDelta
        },
    ) {
        Text(
            text = heart.emoji,
            fontSize = heart.fontSize.sp,
            // ≡ iOS `.shadow(color: .black.opacity(0.25), radius: …, x: 0, y: 1)`
            modifier = Modifier.shadow(
                elevation = shadowRadius,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.25f),
                clip = false,
            ),
        )
    }
}

/**
 * Port de `StickerVideoPlayer`.
 * El ExoPlayer se silencia y repite indefinidamente, igual que el AVPlayer de iOS.
 */
@Composable
fun StickerVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    isMuted: Boolean = true,
    onDurationMs: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val exportFrames = LocalStoryExportVideoFrames.current
    if (exportFrames != null) {
        exportFrames[url]?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = null,
                modifier = modifier, contentScale = ContentScale.Crop)
        }
        return
    }
    val durationCallback by rememberUpdatedState(onDurationMs)
    val player = remember(url) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (isMuted) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(player, isMuted) {
        player.volume = if (isMuted) 0f else 1f
        if (!isMuted) {
            MomentsAudioSession.initialize(context)
            MomentsAudioSession.activate(
                usage = android.media.AudioAttributes.USAGE_MEDIA,
                contentType = android.media.AudioAttributes.CONTENT_TYPE_MOVIE,
            )
        }
    }
    DisposableEffect(player) {
        var reported = false
        val handler = Handler(Looper.getMainLooper())
        fun emitDuration() {
            val duration = player.duration
            if (reported || duration <= 0L) return
            reported = true
            durationCallback?.invoke(duration)
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) emitDuration()
            }
        }
        player.addListener(listener)
        // ≡ iOS: no emitir durante el update; si ya está READY, posponer al siguiente loop.
        if (player.playbackState == Player.STATE_READY) {
            handler.post { emitDuration() }
        }
        onDispose {
            handler.removeCallbacksAndMessages(null)
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                // ≡ iOS playerLayer.backgroundColor = clear
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = player
            if (!player.isPlaying) player.play()
        },
        modifier = modifier,
    )
}

// `KeyboardIgnoringContainer` no requiere equivalente: Compose no hereda el
// ajuste automático de teclado de UIKit que ese wrapper neutraliza en iOS.
