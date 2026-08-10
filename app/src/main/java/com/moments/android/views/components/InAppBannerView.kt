package com.moments.android.views.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.MomentsGlassStyle
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.services.NotificationBannerCopy
import com.moments.android.notifications.services.NotificationCopyResolver
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.services.ChatNavigationIntentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val BannerCapsule = RoundedCornerShape(percent = 50)

/**
 * Port de `InAppBannerView.swift`.
 * Quick reply: `InAppMessageQuickReplyPanel` (archivo propio, como en iOS).
 */
@Composable
fun InAppBannerView(modifier: Modifier = Modifier) {
    val visible by InAppNotificationService.showBanner.collectAsState()
    val notification by InAppNotificationService.currentNotification.collectAsState()
    var isQuickReplyExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible) isQuickReplyExpanded = false
    }

    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // iOS: allowsHitTesting(isBannerInteractive) — solo cuando showBanner
        AnimatedVisibility(
            visible = visible && notification != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { -it },
            ) + fadeIn(),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                targetOffsetY = { -it },
            ) + fadeOut(),
        ) {
            notification?.let { current ->
                // iOS: padding(.top, 8)
                Box(Modifier.padding(top = 8.dp)) {
                    if (isQuickReplyExpanded && current.type == NotificationType.MESSAGE) {
                        InAppMessageQuickReplyPanel(
                            notification = current,
                            onDismiss = { isQuickReplyExpanded = false },
                        )
                    } else {
                        CompactInAppBanner(
                            notification = current,
                            onExpandQuickReply = { isQuickReplyExpanded = true },
                            onCollapseQuickReply = { isQuickReplyExpanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInAppBanner(
    notification: MomentsNotification,
    onExpandQuickReply: () -> Unit,
    onCollapseQuickReply: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val copy = remember(notification) { NotificationCopyResolver.resolve(notification) }
    val isSystem = isSystemBanner(notification)
    val accent = if (isSystem) Color(0xFFFF9500) else colorFor(notification.type)
    val lines = bannerTextLines(copy, notification)
    // iOS: legacyPoppinsSize(13/12)
    val headlineSp = with(density) { legacyPoppinsSize(context, 13).toSp() }
    val detailSp = with(density) { legacyPoppinsSize(context, 12).toSp() }

    var contentPreviewImage by remember(notification.id) { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var suppressTapUntilMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(notification.id) {
        contentPreviewImage = null
        HapticManager.shared.success()
        contentPreviewImage = loadPreviewImage(notification)
    }

    // iOS: wash sutil (sin borde gradient gordo)
    val accentWash = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = 0.18f),
            accent.copy(alpha = 0.08f),
            Color.Transparent,
        ),
    )

    // Mismo gestos de siempre; tamaño + look tipo iOS SToasts.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .fillMaxWidth()
                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                .shadow(
                    elevation = 8.dp,
                    shape = BannerCapsule,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .momentsChromeGlass(
                    shape = BannerCapsule,
                    interactive = false,
                    style = MomentsGlassStyle.NATIVE,
                )
                .pointerInput(notification.id) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount < 0f) dragOffsetY += dragAmount
                        },
                        onDragEnd = {
                            if (dragOffsetY < -20f) {
                                onCollapseQuickReply()
                                InAppNotificationService.dismissManually()
                            }
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                    )
                }
                .pointerInput(notification.id, notification.conversationId) {
                    detectTapGestures(
                        onLongPress = {
                            if (notification.type == NotificationType.MESSAGE &&
                                !notification.conversationId.isNullOrBlank()
                            ) {
                                suppressTapUntilMs = System.currentTimeMillis() + 600L
                                onExpandQuickReply()
                                HapticManager.shared.mediumImpact()
                            }
                        },
                        onTap = {
                            if (System.currentTimeMillis() < suppressTapUntilMs) return@detectTapGestures
                            onCollapseQuickReply()
                            InAppNotificationService.dismissManually()
                            scope.launch {
                                routeBannerTap(notification, context)
                            }
                        },
                    )
                },
        ) {
            // Wash encima del fill AdaptiveColors (paridad iOS).
            Box(
                Modifier
                    .matchParentSize()
                    .clip(BannerCapsule)
                    .background(accentWash),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // iOS: .primary/.secondary sobre glass — Android fuerza contentColor del chrome.
                CompositionLocalProvider(
                    LocalContentColor provides MomentsChromeGlass.contentColor(isDark),
                ) {
                    BannerAvatar(notification = notification, isSystem = isSystem, isDark = isDark)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        lines.headline?.let { headline ->
                            Text(
                                text = headline,
                                color = LocalContentColor.current,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = headlineSp,
                                maxLines = 1,
                            )
                        }
                        when {
                            lines.detail != null -> Text(
                                text = lines.detail,
                                color = LocalContentColor.current.copy(alpha = 0.72f),
                                fontWeight = FontWeight.Medium,
                                fontSize = detailSp,
                                maxLines = 2,
                            )
                            isSystemModerationBanner(notification) -> Text(
                                text = moderationBannerText(notification),
                                color = LocalContentColor.current.copy(alpha = 0.92f * 0.72f),
                                fontWeight = FontWeight.Medium,
                                fontSize = detailSp,
                                maxLines = 2,
                            )
                        }
                    }

                    BannerTrailingIcon(
                        notification = notification,
                        isSystem = isSystem,
                        accentColor = accent,
                        contentPreviewImage = contentPreviewImage,
                    )
                }
            }
        }
    }
}

private data class BannerTextLines(val headline: String?, val detail: String?)

private fun bannerTextLines(
    copy: NotificationBannerCopy,
    notification: MomentsNotification,
): BannerTextLines {
    val name = notification.senderUsername
    if (isSystemTimeLimitBanner(notification)) {
        return BannerTextLines(copy.title, copy.body)
    }
    if (notification.type == NotificationType.GENTLE_REMINDER) {
        return BannerTextLines(copy.title, copy.body)
    }
    val body = copy.body?.trim().orEmpty()
    if (body.isNotEmpty()) {
        return if (body.startsWith(name)) {
            BannerTextLines(null, body)
        } else {
            BannerTextLines(name, body)
        }
    }
    if (copy.title != name) {
        return BannerTextLines(name, copy.title)
    }
    return BannerTextLines(name, null)
}

@Composable
private fun BannerAvatar(
    notification: MomentsNotification,
    isSystem: Boolean,
    isDark: Boolean,
) {
    if (isSystem) {
        SystemBannerAvatar(notification = notification, isDark = isDark)
    } else {
        AsyncProfileImageView(
            userId = notification.senderId,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        )
    }
}

@Composable
private fun SystemBannerAvatar(notification: MomentsNotification, isDark: Boolean) {
    if (isSystemModerationBanner(notification)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f))
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(0.14f) else Color.Black.copy(0.1f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    if (isDark) R.drawable.splash_logo_light else R.drawable.splash_logo_dark,
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9500).copy(alpha = 0.16f))
                .border(1.dp, Color(0xFFFF9500).copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.HourglassFull,
                contentDescription = null,
                tint = Color(0xFFFF9500),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun BannerTrailingIcon(
    notification: MomentsNotification,
    isSystem: Boolean,
    accentColor: Color,
    contentPreviewImage: String?,
) {
    if (!isSystem && !contentPreviewImage.isNullOrBlank()) {
        AsyncImage(
            model = contentPreviewImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(7.dp)),
        )
        return
    }

    val tint = if (isSystemModerationBanner(notification)) {
        LocalContentColor.current.copy(alpha = 0.85f)
    } else {
        accentColor
    }
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (!isSystemTimeLimitBanner(notification) &&
            !isSystemModerationBanner(notification) &&
            notification.type == NotificationType.PHOTO_TAG
        ) {
            AttachmentIconView(
                icon = AttachmentIcon.TAGGED,
                preset = AttachmentIconPreset.IN_APP_BANNER,
                tintColor = tint,
            )
        } else {
            Icon(
                imageVector = trailingSystemIcon(notification),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun trailingSystemIcon(notification: MomentsNotification): ImageVector = when {
    isSystemTimeLimitBanner(notification) -> Icons.Filled.HourglassFull
    isSystemModerationBanner(notification) -> Icons.Filled.Security
    else -> when (notification.type) {
        NotificationType.LIKE, NotificationType.REACTION -> Icons.Filled.Favorite
        NotificationType.COMMENT -> Icons.Filled.Comment
        NotificationType.MESSAGE, NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ -> Icons.Filled.Chat
        NotificationType.NEW_FOLLOWER, NotificationType.FOLLOW_REQUEST, NotificationType.REQUEST_ACCEPTED -> Icons.Filled.Person
        NotificationType.ECHO_SUGGESTION -> Icons.Filled.Star
        else -> Icons.Filled.Notifications
    }
}

private fun isSystemTimeLimitBanner(notification: MomentsNotification): Boolean =
    notification.senderId == "system_time_limit"

private fun isSystemModerationBanner(notification: MomentsNotification): Boolean =
    notification.type == NotificationType.MEDIA_MODERATION

private fun isSystemBanner(notification: MomentsNotification): Boolean =
    isSystemTimeLimitBanner(notification) || isSystemModerationBanner(notification)

private suspend fun loadPreviewImage(notification: MomentsNotification): String? {
    if (isSystemBanner(notification)) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            when {
                notification.type == NotificationType.MENTION && notification.storyId != null ->
                    fetchStoryPreview(notification.storyId, storyAuthorId(notification))
                notification.type in setOf(
                    NotificationType.LIKE,
                    NotificationType.COMMENT,
                    NotificationType.REACTION,
                    NotificationType.MENTION,
                ) && notification.momentId != null -> {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@runCatching null
                    FirestoreService().fetchMoment(notification.momentId, uid).previewImageURLString
                }
                notification.type == NotificationType.STORY_REACTION && notification.storyId != null ->
                    fetchStoryPreview(notification.storyId, notification.storyAuthorId)
                notification.type == NotificationType.STORY_CHAIN_CONTINUED && notification.storyId != null ->
                    fetchStoryPreview(notification.storyId, notification.senderId)
                else -> null
            }
        }.getOrNull()
    }
}

private suspend fun fetchStoryPreview(storyId: String, authorId: String?): String? {
    val userId = authorId ?: return null
    val snap = FirebaseFirestore.getInstance()
        .collection("users").document(userId)
        .collection("stories").document(storyId)
        .get().await()
    @Suppress("UNCHECKED_CAST")
    val mediaItem = snap.data?.get("mediaItem") as? Map<String, Any?> ?: return null
    val thumbnail = mediaItem["thumbnailUrl"] as? String
    if (!thumbnail.isNullOrBlank()) return thumbnail
    return mediaItem["url"] as? String
}

private fun storyAuthorId(notification: MomentsNotification): String? {
    if (notification.type == NotificationType.STORY_REACTION) {
        return notification.storyAuthorId
            ?: notification.targetAuthorId
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: notification.senderId
    }
    return notification.storyAuthorId ?: notification.targetAuthorId ?: notification.senderId
}

private fun routeBannerTap(notification: MomentsNotification, context: android.content.Context) {
    when (notification.type) {
        NotificationType.MESSAGE ->
            notification.conversationId?.let(NotificationNavigationService::navigateToConversation)
        NotificationType.MESSAGE_REACTION -> notification.conversationId?.let { conversationId ->
            notification.messageId?.let { ChatNavigationIntentStore.enqueueHighlight(conversationId, it) }
            NotificationNavigationService.navigateToConversation(conversationId)
        }
        NotificationType.CHAT_BUZZ -> notification.conversationId?.let { conversationId ->
            ChatNavigationIntentStore.enqueueBuzz(conversationId, notification.buzzEventId)
            NotificationNavigationService.navigateToConversation(conversationId)
        }
        NotificationType.DATA_EXPORT_READY -> notification.downloadURL?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        else -> NotificationNavigationService.navigateToNotifications(notificationsFilter(notification.type))
    }
}

private fun notificationsFilter(type: NotificationType): String? = when (type) {
    NotificationType.FOLLOW_REQUEST, NotificationType.REQUEST_ACCEPTED -> "requests"
    NotificationType.REACTION -> "reactions"
    NotificationType.COMMENT -> "comments"
    NotificationType.STORY_REACTION -> "stories"
    NotificationType.NEW_FOLLOWER, NotificationType.MUTUAL_CONNECTION -> "follows"
    else -> null
}

private fun colorFor(type: NotificationType): Color = when (type) {
    NotificationType.LIKE -> Color(0xFFFF3B30) // .red
    NotificationType.REACTION, NotificationType.MESSAGE_REACTION -> Color(0xFFAF52DE) // .purple
    NotificationType.COMMENT -> Color(0xFF007AFF) // .blue
    NotificationType.NEW_FOLLOWER -> Color(0xFF34C759) // .green
    NotificationType.STORY_CHAIN_CONTINUED -> Color(0xFF5856D6) // .indigo
    NotificationType.ECHO_SUGGESTION, NotificationType.MEDIA_MODERATION -> Color(0xFFFF9500) // .orange
    NotificationType.CHAT_BUZZ -> Color(0xFF32ADE6) // .cyan
    NotificationType.GENTLE_REMINDER -> Color(0xFF00C7BE) // .mint
    else -> Color.Gray
}

@Composable
private fun moderationBannerText(notification: MomentsNotification): String {
    notification.message?.takeIf { it.isNotEmpty() }?.let { return it }
    val moderationType = notification.reaction ?: "partial"
    val scope = notification.moderationScope ?: "post"
    return when (scope) {
        "storySticker" -> stringResource(R.string.banner_verb_media_moderation_story_sticker_partial)
        "postHiddenLayer" -> stringResource(R.string.banner_verb_media_moderation_post_hidden_layer_partial)
        "story" -> if (moderationType == "full") {
            stringResource(R.string.banner_verb_media_moderation_story_full)
        } else {
            stringResource(R.string.banner_verb_media_moderation_story_partial)
        }
        else -> stringResource(R.string.banner_verb_media_moderation_partial)
    }
}
