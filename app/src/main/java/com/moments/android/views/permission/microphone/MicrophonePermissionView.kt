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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
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
        icon = { tint ->
            Icon(
                if (denied) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        },
        title = stringResource(
            if (denied) R.string.permission_microphone_denied_title
            else R.string.permission_microphone_primer_title,
        ),
        description = stringResource(
            if (denied) R.string.permission_microphone_denied_subtitle
            else R.string.permission_microphone_primer_subtitle,
        ),
        primaryActionTitle = stringResource(
            if (denied) R.string.permission_microphone_denied_open_settings
            else R.string.permission_microphone_primer_allow,
        ),
        secondaryActionTitle = stringResource(R.string.permission_microphone_primer_not_now),
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    ) {
        PermissionPhoneFrame(
            animated = false,
            showsIslandIndicators = !denied,
            appliesDeniedChrome = denied,
            screen = { size, _ ->
                MicrophonePulseScreen(size = size, isActive = !denied)
            },
            island = { ratio, _ ->
                // ≡ orange privacy mic indicator · offset x: 12*ratio
                Box(
                    Modifier
                        .offset(x = (12f * ratio).dp)
                        .size((10f * ratio).dp)
                        .background(Color(0xFFFF9800), CircleShape),
                )
            },
        )
    }
}

/** ≡ MicrophonePulseScreen — ondas + mic centrado. */
@Composable
private fun MicrophonePulseScreen(size: DpSize, isActive: Boolean) {
    val density = LocalDensity.current
    val widthPx = with(density) { size.width.toPx() }
    // period 2.4s ≡ iOS
    val phase = if (isActive) {
        rememberInfiniteTransition(label = "microphone-pulse")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 2400, easing = LinearEasing),
                    RepeatMode.Restart,
                ),
                label = "microphone-pulse-phase",
            ).value
    } else {
        0f
    }

    Box(Modifier.fillMaxSize()) {
        PermissionPhoneWallpaper(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        SoundWaves(
            phase = phase,
            active = isActive,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = if (isActive) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (isActive) 1f else 0.55f),
            // ≡ size.width * 0.22
            modifier = Modifier
                .align(Alignment.Center)
                .size(with(density) { (widthPx * 0.22f).toDp() }),
        )
    }
}

/**
 * ≡ waves(at:) — 3 anillos, cada uno L/R arcs, gradient FF9F45→FF3D71,
 * arcSize = width*(0.24 + phase*0.5), opacity 1-phase.
 */
@Composable
private fun SoundWaves(
    phase: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (!active) return@Canvas
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val strokeW = this.size.width * 0.018f
        val waveCount = 3
        repeat(waveCount) { index ->
            val local = (phase + index / waveCount.toFloat()) % 1f
            // frame arcSize → radius = arcSize/2
            val arcSize = this.size.width * (0.24f + local * 0.5f)
            val radius = arcSize / 2f
            val brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF9F45), Color(0xFFFF3D71)),
                startY = center.y - radius,
                endY = center.y + radius,
            )
            val alpha = 1f - local
            // facingRight: -45…45 ; facingLeft: 135…225
            listOf(-45f to 90f, 135f to 90f).forEach { (start, sweep) ->
                drawArc(
                    brush = brush,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    alpha = alpha,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
        }
    }
}
