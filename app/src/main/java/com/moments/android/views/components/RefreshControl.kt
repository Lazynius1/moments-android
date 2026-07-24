package com.moments.android.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy

/** Indicador visual del port de `RefreshControl.swift`; el gesto lo gestiona `PullToRefreshBox`. */
@Composable
fun RefreshControl(isRefreshing: Boolean, modifier: Modifier = Modifier) {
    if (!isRefreshing) return
    val rotation = if (MotionPolicy.reduceMotion) 0f else rememberInfiniteTransition(label = "refreshControl").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Restart),
        label = "refreshControlRotation",
    ).value
    Box(modifier.size(44.dp).background(Color.White.copy(.12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Refresh, null, tint = Color(0xFF00A896), modifier = Modifier.size(20.dp).rotate(rotation))
    }
}
