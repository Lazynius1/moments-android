package com.moments.android.notifications.row

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.components.NotificationLeadingAvatarView
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationProfileLink
import com.moments.android.notifications.core.NotificationRowMetrics
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.notifications.core.normalizedCommentPreview
import com.moments.android.notifications.core.uniqueSenderIds
import com.moments.android.notifications.row.EnhancedNotificationRowFollow.resolveSenderUsername
import com.moments.android.notifications.row.EnhancedNotificationRowMessages.messageForGroup
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.FeedInk

/**
 * Port de EnhancedNotificationRow.swift (shell).
 * Mensajes / trailing / follow / previews → extensions (+Messages/+Trailing/+Follow/+Previews).
 */
@Composable
fun EnhancedNotificationRow(
    group: NotificationGroup,
    viewModel: NotificationsViewModel,
    isDark: Boolean,
    onTapAction: () -> Unit,
    onShowGroupedFollowers: ((NotificationGroup) -> Unit)? = null,
    onModerationReviewTap: ((MomentsNotification) -> Unit)? = null,
    onOpenProfile: ((String) -> Unit)? = null,
) {
    val first = group.notifications.firstOrNull() ?: return
    var isPressed by remember { mutableStateOf(false) }
    var senderUsernameOverride by remember(group.id) { mutableStateOf<String?>(null) }
    val senderIds = remember(group) { uniqueSenderIds(group) }
    // ≡ displaySenderIds: 3+ → solo el más reciente; si no, hasta 2
    val displaySenderIds = remember(senderIds) {
        if (senderIds.size >= 3) senderIds.take(1) else senderIds.take(2)
    }
    val commentPreview = remember(group) {
        if (first.type == NotificationType.COMMENT || first.mentionContext == "reply") {
            group.notifications.firstNotNullOfOrNull { normalizedCommentPreview(it) }
        } else {
            null
        }
    }
    val opensSenderProfileOnTap = first.type in setOf(
        NotificationType.NEW_FOLLOWER,
        NotificationType.FOLLOW_REQUEST,
        NotificationType.MUTUAL_CONNECTION,
        NotificationType.REQUEST_ACCEPTED,
    )
    val isModeration = first.type == NotificationType.MEDIA_MODERATION
    val leadingInset = if (displaySenderIds.size > 1) {
        NotificationRowMetrics.stackedRowWidthDp.dp + 16.dp
    } else {
        NotificationRowMetrics.AVATAR_SIZE_DP.dp + 16.dp
    }
    val unreadLabel = stringResource(R.string.notifications_unread_indicator)
    val message = remember(group, isDark, senderUsernameOverride) {
        messageForGroup(group, isDark, senderUsernameOverride)
    }
    // ≡ canvas de NotificationsView (0B1215 / FAF9F6) — no Transparent (tapaba el swipe rojo)
    val canvas = if (isDark) FeedInk else FeedCanvas
    val highlight = when {
        isPressed -> if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f)
        group.isUnread -> if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
        else -> Color.Transparent
    }

    // ≡ resolveSenderDisplayData (onAppear)
    LaunchedEffect(group.id, first.senderId, first.senderUsername) {
        resolveSenderUsername(group)?.let { senderUsernameOverride = it }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(canvas),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(highlight)
                .pointerInput(opensSenderProfileOnTap, displaySenderIds, group) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = {
                            if (opensSenderProfileOnTap && displaySenderIds.isNotEmpty()) {
                                onOpenProfile?.invoke(displaySenderIds.first())
                            } else {
                                onTapAction()
                            }
                        },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isModeration) {
                ModerationLeadingAvatar(isDark = isDark)
            } else if (displaySenderIds.isNotEmpty()) {
                NotificationLeadingAvatarView(
                    senderIds = displaySenderIds,
                    isDark = isDark,
                    onPrimaryTap = { displaySenderIds.firstOrNull()?.let { onOpenProfile?.invoke(it) } },
                    onSecondaryTap = displaySenderIds.getOrNull(1)?.let { id -> { onOpenProfile?.invoke(id) } },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(NotificationRowMetrics.AVATAR_SIZE_DP.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                ClickableText(
                    text = message,
                    style = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isDark) Color.White else Color.Black,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        val ann = message.getStringAnnotations(
                            NotificationProfileLink.ANNOTATION_TAG,
                            offset,
                            offset,
                        ).firstOrNull()
                        val userId = ann?.let { NotificationProfileLink.userIdFromPath(it.item) }
                        if (userId != null) {
                            onOpenProfile?.invoke(userId)
                        } else if (opensSenderProfileOnTap && displaySenderIds.isNotEmpty()) {
                            onOpenProfile?.invoke(displaySenderIds.first())
                        } else {
                            onTapAction()
                        }
                    },
                )
                commentPreview?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.5f),
                        maxLines = 2,
                    )
                }
                Text(
                    text = MomentsFormat.relativeTime(first.timestamp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray.copy(alpha = 0.72f),
                )
            }

            // ≡ trailingContent (+Trailing; Follow/Previews como helpers)
            EnhancedNotificationRowTrailing.trailingContent(
                group = group,
                viewModel = viewModel,
                isDark = isDark,
                onTapAction = onTapAction,
                onShowGroupedFollowers = onShowGroupedFollowers,
                onModerationReviewTap = onModerationReviewTap,
            )

            if (group.isUnread) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isDark) Color.White else Color.Black, CircleShape)
                        .semantics { contentDescription = unreadLabel },
                )
            }
        }

        // Separator ≡ overlay bottom (leading inset = avatar width + 16)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = leadingInset)
                .height(0.5.dp)
                .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)),
        )
    }
}

/** ≡ moderation leading: SplashLogo light/dark (fallback launcher si no hay asset Splash). */
@Composable
private fun ModerationLeadingAvatar(isDark: Boolean) {
    val ctx = LocalContext.current
    val splashId = remember(isDark) {
        val name = if (isDark) "splash_logo_light" else "splash_logo_dark"
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            .takeIf { it != 0 }
            ?: ctx.resources.getIdentifier("ic_launcher_foreground", "drawable", ctx.packageName)
                .takeIf { it != 0 }
            ?: R.mipmap.ic_launcher
    }
    Box(
        modifier = Modifier
            .size(NotificationRowMetrics.AVATAR_SIZE_DP.dp)
            .clip(CircleShape)
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.1f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(splashId),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
    }
}
