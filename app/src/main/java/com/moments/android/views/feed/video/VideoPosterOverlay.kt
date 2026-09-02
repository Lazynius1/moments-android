package com.moments.android.views.feed.video

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Port de `VideoPosterOverlay.swift`.
 * Poster obligatorio hasta que el vídeo esté listo para reproducir.
 */
@Composable
fun VideoPosterOverlay(
    posterUrl: String?,
    isReadyToPlay: Boolean,
    modifier: Modifier = Modifier,
    /** ≡ iOS `contentMode` (`.fill` → Crop, `.fit` → Fit). */
    contentScale: ContentScale = ContentScale.Crop,
    /** ≡ iOS `cornerRadius`. */
    cornerRadius: Dp = 0.dp,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isReadyToPlay) 0f else 1f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "posterFade",
    )

    // ≡ iOS .allowsHitTesting(!isReadyToPlay): fuera del árbol cuando ya no se ve.
    if (isReadyToPlay && alpha <= 0.01f) return

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        val url = posterUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (cornerRadius > 0.dp) {
                            Modifier.clip(RoundedCornerShape(cornerRadius))
                        } else {
                            Modifier
                        },
                    ),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
            )
        }
    }
}
