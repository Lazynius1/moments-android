package com.moments.android.views.permission.microphone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPhoneWallpaper
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage

/** Port de `MicrophonePermissionView.swift`. */
@Composable
fun MicrophonePermissionView(
    stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER,
    primaryAction: () -> Unit,
    secondaryAction: () -> Unit,
) {
    val denied = stage == PermissionPrimerStage.DENIED
    PermissionPrimerScaffold(
        stage = stage,
        icon = { tint -> Icon(if (denied) Icons.Default.MicOff else Icons.Default.Mic, null, tint = tint, modifier = Modifier.fillMaxSize()) },
        title = stringResource(if (denied) R.string.permission_microphone_denied_title else R.string.permission_microphone_primer_title),
        description = stringResource(if (denied) R.string.permission_microphone_denied_subtitle else R.string.permission_microphone_primer_subtitle),
        primaryActionTitle = stringResource(if (denied) R.string.permission_microphone_denied_open_settings else R.string.permission_microphone_primer_allow),
        secondaryActionTitle = stringResource(R.string.permission_microphone_primer_not_now),
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    ) {
        PermissionPhoneFrame(
            animated = false,
            showsIslandIndicators = !denied,
            appliesDeniedChrome = denied,
            screen = { MicrophonePulseScreen(!denied) },
            island = { Box(Modifier.size(10.dp).background(Color(0xFFFF9800), androidx.compose.foundation.shape.CircleShape)) },
        )
    }
}

@Composable
private fun MicrophonePulseScreen(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "microphone-pulse")
    val phase = if (isActive) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "microphone-pulse-phase").value else 0f
    Box(Modifier.fillMaxSize()) {
        PermissionPhoneWallpaper(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        SoundWaves(phase, isActive, Modifier.fillMaxSize())
        Icon(if (isActive) Icons.Default.Mic else Icons.Default.MicOff, null, tint = Color.White.copy(alpha = if (isActive) 1f else .55f), modifier = Modifier.align(Alignment.Center).size(52.dp))
    }
}

@Composable
private fun SoundWaves(phase: Float, active: Boolean, modifier: Modifier = Modifier) = Canvas(modifier) {
    if (!active) return@Canvas
    val center = Offset(size.width / 2, size.height / 2)
    repeat(3) { index ->
        val local = (phase + index / 3f) % 1f
        val radius = size.width * (.12f + local * .25f)
        val color = if (local < .5f) Color(0xFFFF9F45) else Color(0xFFFF3D71)
        listOf(-45f to 45f, 135f to 225f).forEach { (start, sweep) ->
            drawArc(color.copy(alpha = 1f - local), start, sweep - start, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(size.width * .018f, cap = StrokeCap.Round))
        }
    }
}
