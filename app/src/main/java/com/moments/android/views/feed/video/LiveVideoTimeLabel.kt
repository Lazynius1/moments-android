package com.moments.android.views.feed.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.services.video.GlobalVideoManager

/** Port de `LiveVideoTimeLabel.DisplayMode`. */
enum class LiveVideoTimeDisplayMode {
    /** Fondo propio (RoundedRectangle negro semitransparente). */
    Standalone,
    /** Sin fondo; el padre gestiona el contenedor. */
    Inline,
}

/**
 * Port de `LiveVideoTimeLabel.swift`.
 * - Sin arrancar: duración total `"0:18"`.
 * - Reproduciendo: `"0:12 / 0:18"`.
 * - Sin duración ni tiempo: no renderiza.
 *
 * Fuente de tiempo: `GlobalVideoManager.livePlaybackSeconds[consumerId]` (como iOS).
 */
@Composable
fun LiveVideoTimeLabel(
    consumerId: String,
    totalDuration: Double?,
    displayMode: LiveVideoTimeDisplayMode = LiveVideoTimeDisplayMode.Standalone,
    modifier: Modifier = Modifier,
) {
    val liveMap by GlobalVideoManager.livePlaybackSeconds.collectAsState()
    val currentSeconds = liveMap[consumerId] ?: 0.0

    // Bridge: iOS `VideoPlayerManager` publica vía `setPlaybackPosition` → livePlaybackSeconds.
    // Hasta portar ese tick, capturamos del pool y escribimos en el manager (misma API).
    DisposableEffect(consumerId) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                GlobalVideoManager.capturePlaybackPosition(consumerId)
                handler.postDelayed(this, 200L)
            }
        }
        handler.post(runnable)
        onDispose { handler.removeCallbacks(runnable) }
    }

    val hasStarted = currentSeconds > 0.05
    val text = when {
        hasStarted && totalDuration != null && totalDuration > 0 ->
            "${formatLiveVideoSeconds(currentSeconds)} / ${formatLiveVideoSeconds(totalDuration)}"
        totalDuration != null && totalDuration > 0 -> formatLiveVideoSeconds(totalDuration)
        else -> null
    } ?: return

    // iOS SwiftUI: .padding → .background (fondo envuelve el padding).
    // Compose: .background → .padding (mismo resultado visual).
    val styled = if (displayMode == LiveVideoTimeDisplayMode.Standalone) {
        modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    } else {
        modifier
    }

    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.SansSerif,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        modifier = styled,
    )
}

/** ≡ iOS `formatted(_ seconds:)`. */
private fun formatLiveVideoSeconds(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return if (m > 0) "%d:%02d".format(m, r) else "0:%02d".format(r)
}
