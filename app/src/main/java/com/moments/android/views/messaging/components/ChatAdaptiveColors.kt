package com.moments.android.views.messaging.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.moments.android.views.feed.AdaptiveColors

/** Port de `Views/Messaging/Components/ChatAdaptiveColors.swift`. */
val LocalChatOutgoingBubbleColor = staticCompositionLocalOf { Color(0xFF3F6F8F) }
val LocalChatMessageRowFrame = staticCompositionLocalOf { Rect.Zero }
val LocalChatMessageBubbleFrame = staticCompositionLocalOf { Rect.Zero }
val LocalChatMessageBubbleCornerRadius = staticCompositionLocalOf { 16f }

val AdaptiveColors.chatInputBackground: Color
    get() = if (isDark) Color(0xFF0B1215).copy(alpha = 0.78f) else Color(0xFFFAF9F6).copy(alpha = 0.94f)

val AdaptiveColors.chatNavigationBackground: Color
    get() = chatInputBackground

val AdaptiveColors.searchBarStroke: Color
    get() = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)

val AdaptiveColors.mediaIconColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

val AdaptiveColors.recordingIndicator: Color
    get() = if (isDark) Color.White else Color.Black

val AdaptiveColors.messageBubbleBackground: Color
    get() = if (isDark) Color(0xFFFAF9F6).copy(alpha = 0.14f) else Color(0xFF0B1215).copy(alpha = 0.07f)

val AdaptiveColors.messageBubbleStroke: Color
    get() = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.15f)

val AdaptiveColors.messageTextColor: Color
    get() = if (isDark) Color.White else Color.Black

val AdaptiveColors.timestampColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)

val AdaptiveColors.dateHeaderColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f)

val AdaptiveColors.typingIndicatorColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)

val AdaptiveColors.replyBarBackground: Color
    get() = if (isDark) Color(0xFFFAF9F6).copy(alpha = 0.1f) else Color(0xFF0B1215).copy(alpha = 0.05f)

val AdaptiveColors.replyBarText: Color
    get() = if (isDark) Color.White else Color.Black

val AdaptiveColors.replyBarSecondaryText: Color
    get() = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)

val AdaptiveColors.userAccentColor: Color
    get() = Color(0xFF3F6F8F)

val AdaptiveColors.accentColorRed: Color
    get() = Color(0xFFFF3B30)

val AdaptiveColors.receivedAccentColor: Color
    get() = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f)

val AdaptiveColors.chatBackground: List<Color>
    get() = List(3) { if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6) }

val AdaptiveColors.messagingBackground: List<Color>
    get() = if (isDark) {
        listOf(userAccentColor.copy(alpha = 0.3f), Color.Blue.copy(alpha = 0.2f), Color(0xFF0B1215))
    } else {
        listOf(userAccentColor.copy(alpha = 0.1f), Color(0xFFFAF9F6), Color(0xFFFAF9F6))
    }
