package com.moments.android.notifications.row

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.NotificationType
import com.moments.android.notifications.components.NotificationLeadingAvatarView
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationRowMetrics
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.notifications.core.normalizedCommentPreview
import com.moments.android.notifications.core.uniqueSenderIds
import com.moments.android.notifications.row.EnhancedNotificationRowFollow.followTrailing
import com.moments.android.notifications.row.EnhancedNotificationRowMessages.messageForGroup
import com.moments.android.notifications.row.EnhancedNotificationRowPreviews.storyTrailing
import com.moments.android.notifications.row.EnhancedNotificationRowPreviews.momentTrailing
import com.moments.android.notifications.row.EnhancedNotificationRowTrailing.requestTrailing
import com.moments.android.notifications.row.EnhancedNotificationRowTrailing.defaultTrailing
import com.moments.android.utilities.MomentsFormat

@Composable
fun EnhancedNotificationRow(
    group: NotificationGroup,
    viewModel: NotificationsViewModel,
    isDark: Boolean,
    onTapAction: () -> Unit,
    onShowGroupedFollowers: ((NotificationGroup) -> Unit)? = null,
    onOpenProfile: ((String) -> Unit)? = null,
) {
    val first = group.notifications.firstOrNull() ?: return
    var isPressed by remember { mutableStateOf(false) }
    val senderIds = remember(group) { uniqueSenderIds(group) }
    val displaySenderIds = remember(senderIds) {
        if (senderIds.size >= 3) senderIds.take(1) else senderIds.take(2)
    }
    val profilePaths = displaySenderIds.associateWith { viewModel.getProfileImagePath(it) }
    val commentPreview = remember(group) {
        if (first.type == NotificationType.COMMENT || first.mentionContext == "reply") {
            group.notifications.firstNotNullOfOrNull { normalizedCommentPreview(it) }
        } else null
    }
    val opensProfile = first.type in setOf(
        NotificationType.NEW_FOLLOWER,
        NotificationType.FOLLOW_REQUEST,
        NotificationType.MUTUAL_CONNECTION,
        NotificationType.REQUEST_ACCEPTED,
    )
    val hasMultipleGroupedFollowActors = (first.type == NotificationType.NEW_FOLLOWER ||
        first.type == NotificationType.MUTUAL_CONNECTION) && senderIds.size > 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isPressed -> if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f)
                    group.isUnread -> if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                    else -> Color.Transparent
                },
            )
            .clickable {
                if (opensProfile && displaySenderIds.isNotEmpty()) {
                    onOpenProfile?.invoke(displaySenderIds.first())
                } else if (hasMultipleGroupedFollowActors) {
                    onShowGroupedFollowers?.invoke(group)
                } else {
                    onTapAction()
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (first.type == NotificationType.MEDIA_MODERATION) {
            Box(
                modifier = Modifier
                    .size(NotificationRowMetrics.AVATAR_SIZE_DP.dp)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("M", fontWeight = FontWeight.Bold)
            }
        } else {
            NotificationLeadingAvatarView(
                senderIds = displaySenderIds,
                profilePaths = profilePaths,
                isDark = isDark,
                onPrimaryTap = { displaySenderIds.firstOrNull()?.let { onOpenProfile?.invoke(it) } },
                onSecondaryTap = displaySenderIds.getOrNull(1)?.let { { onOpenProfile?.invoke(it) } },
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = messageForGroup(group, isDark),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = if (isDark) Color.White else Color.Black,
                maxLines = 2,
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

        when (first.type) {
            NotificationType.FOLLOW_REQUEST -> requestTrailing(group, viewModel, isDark)
            NotificationType.NEW_FOLLOWER, NotificationType.MUTUAL_CONNECTION ->
                if (!hasMultipleGroupedFollowActors) followTrailing(group, viewModel, isDark) else defaultTrailing(group, isDark)
            NotificationType.LIKE, NotificationType.COMMENT, NotificationType.REACTION, NotificationType.PHOTO_TAG ->
                momentTrailing(group, viewModel, isDark)
            NotificationType.STORY_REACTION, NotificationType.STORY_CHAIN_CONTINUED ->
                storyTrailing(group, viewModel, isDark)
            NotificationType.MESSAGE, NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ ->
                EnhancedNotificationRowMessagesTrailing.chatTrailing(isDark)
            else -> defaultTrailing(group, isDark)
        }

        if (group.isUnread) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isDark) Color.White else Color.Black, CircleShape),
            )
        }
    }
}
