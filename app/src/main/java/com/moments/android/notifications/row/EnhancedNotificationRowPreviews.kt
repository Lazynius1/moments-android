package com.moments.android.notifications.row

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moments.android.models.NotificationType
import com.moments.android.notifications.components.NotificationMomentThumbnail
import com.moments.android.notifications.components.NotificationStoryThumbnail
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Port de EnhancedNotificationRow+Previews.swift */
object EnhancedNotificationRowPreviews {
    @Composable
    fun momentTrailing(group: NotificationGroup, viewModel: NotificationsViewModel, isDark: Boolean) {
        val first = group.notifications.first()
        var imagePath by remember(first.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(first.momentId, first.targetAuthorId) {
            val momentId = first.momentId ?: return@LaunchedEffect
            if (!first.storyPreviewUrl.isNullOrBlank()) {
                imagePath = first.storyPreviewUrl
                return@LaunchedEffect
            }
            val authorId = first.targetAuthorId ?: first.senderId
            runCatching {
                withContext(Dispatchers.IO) {
                    FirestoreService().fetchMoment(momentId, authorId).previewImageURLString
                }
            }.getOrNull()?.let { imagePath = it }
        }
        NotificationMomentThumbnail(imageUrl = imagePath, isDark = isDark)
    }

    @Composable
    fun storyTrailing(group: NotificationGroup, viewModel: NotificationsViewModel, isDark: Boolean) {
        val first = group.notifications.first()
        var imagePath by remember(first.id) { mutableStateOf(first.storyPreviewUrl) }
        LaunchedEffect(first.storyId, first.storyAuthorId) {
            if (!imagePath.isNullOrBlank()) return@LaunchedEffect
            // Story preview fetch when Messaging/Stories layer is fully ported.
        }
        NotificationStoryThumbnail(imageUrl = imagePath, isLoading = false, isDark = isDark)
    }

    fun isMomentMention(notification: com.moments.android.models.MomentsNotification) =
        notification.type == NotificationType.MENTION &&
            (notification.mentionContext == "moment" || notification.momentId != null)

    fun isStoryMention(notification: com.moments.android.models.MomentsNotification) =
        notification.type == NotificationType.MENTION &&
            (notification.mentionContext == "story" || notification.storyId != null)
}
