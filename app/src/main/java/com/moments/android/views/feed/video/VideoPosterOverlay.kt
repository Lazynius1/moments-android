package com.moments.android.views.feed.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** Port de `VideoPosterOverlay.swift`. */
@Composable
fun VideoPosterOverlay(
    posterUrl: String?,
    isReadyToPlay: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isReadyToPlay) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "posterFade",
    )
    if (alpha <= 0f && isReadyToPlay) return

    Box(modifier.fillMaxSize()) {
        if (posterUrl.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).graphicsLayer { this.alpha = alpha })
        } else {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
            )
        }
    }
}
