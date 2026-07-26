package com.moments.android.views.messaging.components

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.moments.android.views.creator.components.AnimatedGIFView
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import java.io.File

/** Port de `Views/Messaging/Components/ChatStickerMessageBubble.swift`. */
object ChatStickerMessageLayout {
    val stickerSize = 140.dp
}

@Composable
fun ChatStickerMessageBubble(
    message: EnhancedMessage,
    progress: Double?,
    modifier: Modifier = Modifier,
    isSending: Boolean = message.status == MessageStatus.SENDING,
) {
    val isDark = isSystemInDarkTheme()
    val pendingResolution = message.isMediaPendingResolution
    val stickerUrl = message.mediaUrl.takeIf { !it.isNullOrBlank() && it.isReachableStickerUrl() }

    Box(
        modifier
            .size(ChatStickerMessageLayout.stickerSize)
            .alpha(if (isSending) 0.7f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        when {
            stickerUrl != null && !pendingResolution -> AnimatedGIFView(
                url = stickerUrl,
                modifier = Modifier
                    .size(ChatStickerMessageLayout.stickerSize)
                    .clip(RectangleShape),
            )
            pendingResolution -> CircularProgressIndicator(
                color = if (isDark) Color.White.copy(alpha = .6f) else Color.Black.copy(alpha = .4f),
            )
        }
        if (isSending) {
            CircularProgressIndicator(
                progress = { (progress ?: 0.0).toFloat().coerceIn(0f, 1f) },
                color = if (isDark) Color.White else Color.Black,
            )
        }
    }
}

private fun String.isReachableStickerUrl(): Boolean {
    if (!startsWith("file://")) return true
    return Uri.parse(this).path?.let(::File)?.exists() == true
}
