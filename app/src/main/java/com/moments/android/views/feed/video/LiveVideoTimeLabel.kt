package com.moments.android.views.feed.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.services.video.SharedVideoPlayerPool

/** Port de `LiveVideoTimeLabel.DisplayMode`. */
enum class LiveVideoTimeDisplayMode {
    Standalone,
    Inline,
}

/**
 * Port de `LiveVideoTimeLabel.swift`.
 * - Sin arrancar: duración total `"0:18"`.
 * - Reproduciendo: `"0:12 / 0:18"`.
 * - Sin duración ni tiempo: no renderiza.
 */
@Composable
fun LiveVideoTimeLabel(
    consumerId: String,
    totalDuration: Double?,
    displayMode: LiveVideoTimeDisplayMode = LiveVideoTimeDisplayMode.Standalone,
    modifier: Modifier = Modifier,
) {
    var currentSeconds by remember(consumerId) { mutableDoubleStateOf(0.0) }

    DisposableEffect(consumerId) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                runCatching {
                    val player = SharedVideoPlayerPool.player(consumerId)
                    currentSeconds = player.currentPosition / 1000.0
                }
                handler.postDelayed(this, 200L)
            }
        }
        handler.post(runnable)
        onDispose { handler.removeCallbacks(runnable) }
    }

    val hasStarted = currentSeconds > 0.05
    val text = when {
        hasStarted && totalDuration != null && totalDuration > 0 ->
            "${formatSeconds(currentSeconds)} / ${formatSeconds(totalDuration)}"
        totalDuration != null && totalDuration > 0 -> formatSeconds(totalDuration)
        else -> null
    } ?: return

    val base = Modifier.then(modifier)
    val styled = if (displayMode == LiveVideoTimeDisplayMode.Standalone) {
        base
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    } else {
        base
    }

    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        modifier = styled,
    )
}

/** API legacy (solo segundos totales). */
@Composable
fun LiveVideoTimeLabel(
    seconds: Double,
    modifier: Modifier = Modifier,
) {
    if (seconds <= 0) return
    Text(
        text = formatSeconds(seconds),
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private fun formatSeconds(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return if (m > 0) "%d:%02d".format(m, r) else "0:%02d".format(r)
}
