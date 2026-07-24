package com.moments.android.views.messaging.components

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.views.creator.components.AnimatedGIFView
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
) {
    val isDark = isSystemInDarkTheme()
    val isSending = message.status == MessageStatus.SENDING
    val pendingResolution = message.isStickerMediaPendingResolution()
    val stickerUrl = message.mediaUrl.takeIf { !it.isNullOrBlank() && it.isReachableStickerUrl() }

    Box(modifier.size(ChatStickerMessageLayout.stickerSize), contentAlignment = Alignment.Center) {
        when {
            stickerUrl != null && !pendingResolution -> AnimatedGIFView(
                url = stickerUrl,
                modifier = Modifier.size(ChatStickerMessageLayout.stickerSize),
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

private fun EnhancedMessage.isStickerMediaPendingResolution(): Boolean {
    if (isDeleted || status == MessageStatus.SENDING) return false
    val canResolve = !mediaObjectPath.isNullOrBlank() && mediaEncryption != null
    val url = mediaUrl
    return canResolve && (url.isNullOrBlank() || !url.isReachableStickerUrl())
}

private fun String.isReachableStickerUrl(): Boolean {
    if (!startsWith("file://")) return true
    return Uri.parse(this).path?.let(::File)?.exists() == true
}
