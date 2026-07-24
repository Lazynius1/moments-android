package com.moments.android.views.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.services.NotificationCopyResolver
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.views.messaging.services.ChatNavigationIntentStore

/** Port base de `InAppBannerView.swift`; el quick reply vive en su archivo Swift/Kotlin propio. */
@Composable
fun InAppBannerView(modifier: Modifier = Modifier) {
    val visible by InAppNotificationService.showBanner.collectAsState()
    val notification by InAppNotificationService.currentNotification.collectAsState()
    var quickReplyExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) quickReplyExpanded = false }
    Box(modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && notification != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) { notification?.let { current ->
            if (quickReplyExpanded && current.type == NotificationType.MESSAGE) {
                InAppMessageQuickReplyPanel(current, onDismiss = { quickReplyExpanded = false })
            } else CompactInAppBanner(current, onQuickReply = { quickReplyExpanded = true })
        } }
    }
}

@Composable
private fun CompactInAppBanner(notification: MomentsNotification, onQuickReply: () -> Unit) {
    val copy = NotificationCopyResolver.resolve(notification)
    val systemTimeLimit = notification.senderId == "system_time_limit"
    val moderation = notification.type == NotificationType.MEDIA_MODERATION
    val system = systemTimeLimit || moderation
    val accent = when (notification.type) {
        NotificationType.LIKE -> Color(0xFFFF3B30)
        NotificationType.REACTION, NotificationType.MESSAGE_REACTION -> Color(0xFFAF52DE)
        NotificationType.COMMENT -> Color(0xFF0A84FF)
        NotificationType.NEW_FOLLOWER -> Color(0xFF34C759)
        NotificationType.STORY_CHAIN_CONTINUED -> Color(0xFF5856D6)
        NotificationType.ECHO_SUGGESTION, NotificationType.MEDIA_MODERATION -> Color(0xFFFF9500)
        NotificationType.CHAT_BUZZ -> Color(0xFF32ADE6)
        else -> Color.Gray
    }
    val context = LocalContext.current
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xEE1C2025))
            .pointerInput(notification.id) {
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -20f) InAppNotificationService.dismissManually() }
            }
            .pointerInput(notification.id, notification.conversationId) {
                detectTapGestures(onLongPress = { if (notification.type == NotificationType.MESSAGE && !notification.conversationId.isNullOrBlank()) onQuickReply() })
            }
            .clickable {
                InAppNotificationService.dismissManually()
                when (notification.type) {
                    NotificationType.MESSAGE -> notification.conversationId?.let(NotificationNavigationService::navigateToConversation)
                    NotificationType.MESSAGE_REACTION -> notification.conversationId?.let { conversationId ->
                        notification.messageId?.let { ChatNavigationIntentStore.enqueueHighlight(conversationId, it) }
                        NotificationNavigationService.navigateToConversation(conversationId)
                    }
                    NotificationType.CHAT_BUZZ -> notification.conversationId?.let { conversationId ->
                        ChatNavigationIntentStore.enqueueBuzz(conversationId, notification.buzzEventId)
                        NotificationNavigationService.navigateToConversation(conversationId)
                    }
                    NotificationType.DATA_EXPORT_READY -> notification.downloadURL?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    else -> NotificationNavigationService.navigateToNotifications(bannerFilter(notification.type))
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (system) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(.16f)), contentAlignment = Alignment.Center) {
                Icon(if (moderation) Icons.Filled.Security else Icons.Filled.Alarm, null, tint = accent)
            }
        } else {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) {
                Text(notification.senderUsername.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(copy.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(copy.body ?: moderationText(notification), color = Color.White.copy(.72f), fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 2)
        }
        notification.storyPreviewUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(url, null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        } ?: Icon(bannerIcon(notification.type), null, tint = accent, modifier = Modifier.size(28.dp))
    }
}

private fun bannerFilter(type: NotificationType): String? = when (type) {
    NotificationType.FOLLOW_REQUEST, NotificationType.REQUEST_ACCEPTED -> "requests"
    NotificationType.REACTION -> "reactions"
    NotificationType.COMMENT -> "comments"
    NotificationType.STORY_REACTION -> "stories"
    NotificationType.NEW_FOLLOWER, NotificationType.MUTUAL_CONNECTION -> "follows"
    else -> null
}

private fun bannerIcon(type: NotificationType) = when (type) {
    NotificationType.LIKE, NotificationType.REACTION -> Icons.Filled.Favorite
    NotificationType.COMMENT -> Icons.Filled.Comment
    NotificationType.MESSAGE, NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ -> Icons.Filled.Chat
    else -> Icons.Filled.Notifications
}

private fun moderationText(notification: MomentsNotification): String = notification.message
    ?: when (notification.moderationScope) {
        "storySticker" -> "We hid a sticker from your story"
        "postHiddenLayer" -> "We hid a hidden layer from your post"
        "story" -> if (notification.reaction == "full") "Your story is now only visible to you" else "Some content was hidden from your story"
        else -> "Some content was hidden from your post"
    }
