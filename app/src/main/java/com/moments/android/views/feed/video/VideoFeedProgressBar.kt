package com.moments.android.views.feed.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Port de `VideoFeedProgressBar.swift`.
 * Barra aislada para no invalidar el reproductor completo en cada tick.
 */
@Composable
fun VideoFeedProgressBar(
    progress: Double,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.0, 1.0).toFloat())
                .height(2.dp)
                .background(Color.White),
        )
    }
}
