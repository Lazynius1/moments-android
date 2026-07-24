package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus

/** Port de `Views/Messaging/Components/ChatGifMessageBubble.swift`. */
object ChatGifLayout {
    val maxWidth = 240.dp
    val maxHeight = 280.dp
    val minSide = 100.dp
    val fallbackSize = DpSize(200.dp, 150.dp)

    fun displaySize(width: Int?, height: Int?): DpSize {
        if (width == null || height == null || width <= 0 || height <= 0) return fallbackSize
        val ratio = width.toFloat() / height
        var displayWidth = if (ratio >= 1f) minOf(width.dp, maxWidth) else minOf(height.dp, maxHeight) * ratio
        var displayHeight = displayWidth / ratio
        if (displayHeight > maxHeight) { displayHeight = maxHeight; displayWidth = displayHeight * ratio }
        if (displayWidth > maxWidth) { displayWidth = maxWidth; displayHeight = displayWidth / ratio }
        return DpSize(maxOf(displayWidth, minSide), maxOf(displayHeight, minSide))
    }
}

@Composable
fun ChatGifMessageBubble(message: EnhancedMessage, progress: Double?, modifier: Modifier = Modifier) {
    val size = ChatGifLayout.displaySize(message.mediaWidth, message.mediaHeight)
    val shape = RoundedCornerShape(16.dp)
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    Box(modifier.size(size).clip(shape)) {
        if (!message.mediaUrl.isNullOrBlank()) {
            AsyncImage(message.mediaUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Box(Modifier.size(size).background(if (isDark) Color.White.copy(.06f) else Color.Black.copy(.05f)), contentAlignment = Alignment.Center) {
                if (message.status == MessageStatus.SENDING) CircularProgressIndicator(color = if (isDark) Color.White.copy(.6f) else Color.Black.copy(.4f))
            }
        }
        if (message.status == MessageStatus.SENDING) {
            Box(Modifier.size(size).background(Color.Black.copy(.25f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = (progress ?: 0.0).toFloat().coerceIn(0f, 1f), color = Color.White)
            }
        }
    }
}
