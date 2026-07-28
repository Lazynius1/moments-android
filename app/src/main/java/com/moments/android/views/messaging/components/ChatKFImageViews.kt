package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType

/**
 * Port de `Views/Messaging/Components/ChatKFImageViews.swift`.
 * Coil ≡ Kingfisher; `downsamplingSize` ≡ KF `.downsampling(size:)`.
 */
@Composable
fun ChatKFImage(
    url: String?,
    modifier: Modifier = Modifier,
    downsamplingSize: DpSize? = null,
) {
    if (url.isNullOrBlank()) {
        ChatMediaResolvingPlaceholder(modifier)
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = ImageRequest.Builder(context)
        .data(url)
        .crossfade(200)
        .apply {
            if (downsamplingSize != null) {
                val w = with(density) { downsamplingSize.width.roundToPx() }
                val h = with(density) { downsamplingSize.height.roundToPx() }
                size(Size(w, h))
            }
        }
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { ChatMediaResolvingPlaceholder(Modifier.fillMaxSize()) },
        success = { SubcomposeAsyncImageContent(Modifier.fillMaxSize()) },
    )
}

/**
 * Placeholder mientras se resuelve media (iOS lo define en `ChatMediaViews`;
 * aquí vive junto a ChatKFImage porque ambos lo usan).
 */
@Composable
fun ChatMediaResolvingPlaceholder(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier.background(
            if (isDark) Color(0xFFFAF9F6).copy(alpha = .08f) else Color(0xFF0B1215).copy(alpha = .06f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = Color.White.copy(alpha = .85f),
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
        )
    }
}

/** Prefetch remoto de galería cluster; `file://` no se encola (≡ iOS). */
object ChatMediaGalleryPrefetcher {
    fun prefetch(context: android.content.Context, messages: List<EnhancedMessage>, imageLoader: ImageLoader) {
        messages.mapNotNull { message ->
            if (message.type == MessageType.VIDEO) message.thumbnailUrl ?: message.mediaUrl
            else message.mediaUrl
        }
            .filter { !it.startsWith("file:") }
            .distinct()
            .forEach { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }
}
