package com.moments.android.views.messaging.components

import android.net.Uri
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.ImageLoader
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType

/** Port de `Views/Messaging/Components/ChatKFImageViews.swift`. */
@Composable
fun ChatKFImage(url: String?, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) {
        ChatMediaResolvingPlaceholder(modifier)
    } else {
        SubcomposeAsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier, loading = { ChatMediaResolvingPlaceholder(Modifier.fillMaxSize()) }, success = { SubcomposeAsyncImageContent(Modifier.fillMaxSize()) })
    }
}

@Composable
fun ChatMediaResolvingPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.White.copy(.06f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White.copy(.6f)) }
}

object ChatMediaGalleryPrefetcher {
    fun prefetch(context: Context, messages: List<EnhancedMessage>, imageLoader: ImageLoader) {
        messages.mapNotNull { message -> if (message.type == MessageType.VIDEO) message.thumbnailUrl ?: message.mediaUrl else message.mediaUrl }
            .filter { !it.startsWith("file:") }
            .distinct()
            .forEach { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }
}
