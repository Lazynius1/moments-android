package com.moments.android.views.messaging.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.moments.android.views.creator.components.AnimatedGIFView
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import java.io.File

/** Port de `Views/Messaging/Components/ChatGifMessageBubble.swift`. */
object ChatGifLayout {
    val maxWidth = 240.dp
    val maxHeight = 280.dp
    val minSide = 100.dp
    val fallbackSize = DpSize(200.dp, 150.dp)
    val cornerRadius = 16.dp

    fun displaySize(width: Int?, height: Int?): DpSize {
        if (width == null || height == null || width <= 0 || height <= 0) return fallbackSize
        val ratio = width.toFloat() / height
        var displayWidth: Float
        var displayHeight: Float
        if (ratio >= 1f) {
            displayWidth = minOf(width.toFloat(), maxWidth.value)
            displayHeight = displayWidth / ratio
            if (displayHeight > maxHeight.value) {
                displayHeight = maxHeight.value
                displayWidth = displayHeight * ratio
            }
        } else {
            displayHeight = minOf(height.toFloat(), maxHeight.value)
            displayWidth = displayHeight * ratio
            if (displayWidth > maxWidth.value) {
                displayWidth = maxWidth.value
                displayHeight = displayWidth / ratio
            }
        }
        return DpSize(
            maxOf(displayWidth, minSide.value).dp,
            maxOf(displayHeight, minSide.value).dp,
        )
    }
}

@Composable
fun ChatGifMessageBubble(
    message: EnhancedMessage,
    progress: Double?,
    modifier: Modifier = Modifier,
) {
    val size = ChatGifLayout.displaySize(message.mediaWidth, message.mediaHeight)
    val shape = RoundedCornerShape(ChatGifLayout.cornerRadius)
    val isDark = isSystemInDarkTheme()
    val isSending = message.status == MessageStatus.SENDING
    val pendingResolution = message.isMediaPendingResolution
    val gifUrl = message.mediaUrl.takeIf { !it.isNullOrBlank() && it.isReachableGifUrl() }
    val placeholderFill = if (isDark) Color.White.copy(alpha = .06f) else Color.Black.copy(alpha = .05f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            gifUrl != null && !pendingResolution -> AnimatedGIFView(
                url = gifUrl,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
            )
            pendingResolution -> Box(
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(placeholderFill),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = if (isDark) Color.White.copy(alpha = .6f) else Color.Black.copy(alpha = .4f),
                )
            }
            else -> Box(
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(placeholderFill),
            )
        }
        if (isSending) {
            Box(
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = .25f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { (progress ?: 0.0).toFloat().coerceIn(0f, 1f) },
                    color = Color.White,
                )
            }
        }
    }
}

private fun String.isReachableGifUrl(): Boolean {
    if (!startsWith("file://")) return true
    return Uri.parse(this).path?.let(::File)?.exists() == true
}
