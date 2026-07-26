package com.moments.android.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy

/**
 * Port de `RefreshControl.swift`.
 *
 * Colocar como **primer hijo** del contenido scrolleable (como en iOS).
 * Cuando el midY del control sube >50dp respecto al baseline (pull),
 * dispara [onRefresh] y muestra el spinner hasta salir de composición.
 */
@Composable
fun RefreshControl(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 50.dp.toPx() }
    var isRefreshing by remember { mutableStateOf(false) }
    var baselineMidY by remember { mutableFloatStateOf(Float.NaN) }

    // iOS: `.frame(height: 0)` — el spinner se dibuja encima sin empujar el layout.
    Box(
        modifier
            .fillMaxWidth()
            .height(0.dp)
            .onGloballyPositioned { coords ->
                val midY = coords.positionInWindow().y + coords.size.height / 2f
                if (baselineMidY.isNaN()) {
                    baselineMidY = midY
                    return@onGloballyPositioned
                }
                // Equivalente a `midY > 50` del GeometryReader iOS al tirar del scroll.
                if ((midY - baselineMidY) > thresholdPx && !isRefreshing) {
                    isRefreshing = true
                    onRefresh()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            RefreshControlSpinner()
            DisposableEffect(Unit) {
                onDispose {
                    isRefreshing = false
                    baselineMidY = Float.NaN
                }
            }
        }
    }
}

/** Spinner del refresh (chrome iOS: `#00A896` + círculo glass). */
@Composable
fun RefreshControlSpinner(modifier: Modifier = Modifier) {
    val rotation = if (MotionPolicy.reduceMotion) {
        0f
    } else {
        rememberInfiniteTransition(label = "refreshControl").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Restart),
            label = "refreshControlRotation",
        ).value
    }
    Box(
        modifier
            .padding(8.dp)
            .size(44.dp)
            .shadow(5.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.2f))
            .momentsChromeGlass(CircleShape, interactive = false),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint = Color(0xFF00A896),
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
        )
    }
}

/** Overload cuando el padre ya gestiona [isRefreshing]. */
@Composable
fun RefreshControl(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isRefreshing) return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RefreshControlSpinner()
    }
}
