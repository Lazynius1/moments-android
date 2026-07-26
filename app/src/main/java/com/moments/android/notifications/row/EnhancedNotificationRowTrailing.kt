package com.moments.android.notifications.row

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.components.GlassmorphicActionButton
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.notifications.core.uniqueSenderIds
import com.moments.android.notifications.row.EnhancedNotificationRowFollow.followTrailing
import com.moments.android.notifications.row.EnhancedNotificationRowPreviews.isStoryMention
import com.moments.android.notifications.row.EnhancedNotificationRowPreviews.momentTrailing
import com.moments.android.notifications.row.EnhancedNotificationRowPreviews.storyTrailing

/**
 * Port de EnhancedNotificationRow+Trailing.swift
 *
 * Follow button → [EnhancedNotificationRowFollow.followTrailing]
 * Moment/story fetch base → Previews helpers (inline KFImage en iOS).
 */
object EnhancedNotificationRowTrailing {

    /** ≡ trailingContent */
    @Composable
    fun trailingContent(
        group: NotificationGroup,
        viewModel: NotificationsViewModel,
        isDark: Boolean,
        onTapAction: () -> Unit,
        onShowGroupedFollowers: ((NotificationGroup) -> Unit)? = null,
        onModerationReviewTap: ((MomentsNotification) -> Unit)? = null,
    ) {
        val first = group.notifications.firstOrNull() ?: return
        val senderIds = remember(group) { uniqueSenderIds(group) }
        val hasMultipleGroupedFollowActors =
            (first.type == NotificationType.NEW_FOLLOWER || first.type == NotificationType.MUTUAL_CONNECTION) &&
                senderIds.size > 1

        when (first.type) {
            NotificationType.LIKE,
            NotificationType.COMMENT,
            NotificationType.REACTION,
            NotificationType.PHOTO_TAG,
            -> momentTrailing(group, viewModel, isDark, onTap = onTapAction)

            NotificationType.MENTION ->
                if (isStoryMention(first)) {
                    storyTrailing(group, viewModel, isDark)
                } else {
                    momentTrailing(group, viewModel, isDark, onTap = onTapAction)
                }

            NotificationType.STORY_REACTION -> storyTrailing(group, viewModel, isDark)

            NotificationType.STORY_CHAIN_CONTINUED -> storyChainTrailing(group, isDark)

            NotificationType.FOLLOW_REQUEST -> requestTrailing(group, viewModel, isDark)

            NotificationType.NEW_FOLLOWER, NotificationType.MUTUAL_CONNECTION ->
                if (hasMultipleGroupedFollowActors) {
                    GroupedFollowersViewAction(isDark = isDark) {
                        onShowGroupedFollowers?.invoke(group)
                    }
                } else {
                    followTrailing(group, viewModel, isDark)
                }

            NotificationType.ECHO_SUGGESTION -> EchoViewAction(onClick = onTapAction)

            NotificationType.DATA_EXPORT_READY -> ExportDownloadAction(onClick = onTapAction)

            NotificationType.MEDIA_MODERATION ->
                ModerationReviewAction(isDark = isDark) {
                    onModerationReviewTap?.invoke(first)
                }

            // iOS default: EmptyView() — sin trailing inventado para chat/buzz
            else -> Unit
        }
    }

    /** ≡ followRequest Accept/Reject (Glassmorphic 007AFF / red) */
    @Composable
    fun requestTrailing(group: NotificationGroup, viewModel: NotificationsViewModel, isDark: Boolean) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassmorphicActionButton(
                text = stringResource(R.string.notifications_accept),
                color = Color(0xFF007AFF),
                isDark = isDark,
                onClick = { viewModel.acceptFollowRequest(group) },
            )
            GlassmorphicActionButton(
                text = stringResource(R.string.notifications_reject),
                color = Color.Red,
                isDark = isDark,
                onClick = { viewModel.rejectFollowRequest(group) },
            )
        }
    }

    /** ≡ storyChainContinued trailing (thumb + link badge / placeholder) */
    @Composable
    private fun storyChainTrailing(group: NotificationGroup, isDark: Boolean) {
        val first = group.notifications.first()
        var imagePath by remember(first.id) { mutableStateOf<String?>(null) }
        var loadFailed by remember(first.id) { mutableStateOf(false) }

        // ≡ setupPreviews: storyPreviewUrl adjunto, si no fetchStoryPreview(resolvedStoryAuthorId)
        LaunchedEffect(first.id, first.storyId, first.storyPreviewUrl, first.storyAuthorId) {
            val attached = first.storyPreviewUrl?.trim().orEmpty()
            if (attached.isNotEmpty()) {
                imagePath = attached
                loadFailed = false
                return@LaunchedEffect
            }
            val storyId = first.storyId?.trim().orEmpty()
            if (storyId.isEmpty()) {
                loadFailed = true
                return@LaunchedEffect
            }
            val path = EnhancedNotificationRowPreviews.fetchStoryPreview(
                storyId,
                EnhancedNotificationRowPreviews.resolvedStoryAuthorId(first),
            )
            imagePath = path
            loadFailed = path == null
        }

        val path = imagePath
        if (!path.isNullOrBlank() && !loadFailed) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                val corner = RoundedCornerShape(8.dp)
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(corner)
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.Blue.copy(alpha = 0.85f),
                                    Color(0xFF9C27B0).copy(alpha = 0.85f),
                                ),
                            ),
                            corner,
                        ),
                    contentScale = ContentScale.Crop,
                )
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .offset(x = 4.dp, y = 4.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(2.dp)
                        .size(14.dp),
                )
            }
        } else {
            val corner = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(corner)
                    .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.Blue.copy(alpha = 0.35f),
                                Color(0xFF9C27B0).copy(alpha = 0.35f),
                            ),
                        ),
                        corner,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }

    /** ≡ Button View grouped followers */
    @Composable
    fun GroupedFollowersViewAction(isDark: Boolean, onClick: () -> Unit) {
        Text(
            text = stringResource(R.string.notifications_grouped_followers_view_action),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White else Color.Black,
            modifier = Modifier
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    /** ≡ echoSuggestion View Echo */
    @Composable
    fun EchoViewAction(onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFFF9500).copy(alpha = 0.2f), Color.Yellow.copy(alpha = 0.15f)),
                    ),
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color(0xFFFF9500).copy(alpha = 0.6f), Color.Yellow.copy(alpha = 0.4f)),
                    ),
                    CircleShape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFFF9500),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.notifications_echo_view_action),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF9500),
            )
        }
    }

    /** ≡ dataExportReady Download */
    @Composable
    fun ExportDownloadAction(onClick: () -> Unit) {
        val blue = Color(0xFF007AFF)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, blue.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = blue,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(R.string.notifications_export_download),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = blue,
            )
        }
    }

    /** ≡ mediaModeration reviewAction */
    @Composable
    fun ModerationReviewAction(isDark: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
                .border(1.dp, Color(0xFFFF9500).copy(alpha = 0.28f), CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.84f),
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(R.string.notifications_media_moderation_review_action),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.84f),
            )
        }
    }
}
