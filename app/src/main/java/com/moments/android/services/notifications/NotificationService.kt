package com.moments.android.services.notifications

import com.moments.android.models.NotificationType
import com.moments.android.notifications.services.NotificationService as NotificationsNotificationService

/**
 * @deprecated Import [com.moments.android.notifications.services.NotificationService] instead.
 */
@Deprecated(
    message = "Use com.moments.android.notifications.services.NotificationService",
    replaceWith = ReplaceWith(
        "NotificationService",
        "com.moments.android.notifications.services.NotificationService",
    ),
)
object NotificationService {

    fun removeNotification(
        type: NotificationType,
        senderId: String,
        recipientId: String,
        momentId: String? = null,
        storyId: String? = null,
        commentId: String? = null,
        reaction: String? = null,
    ) = NotificationsNotificationService.removeNotification(
        type, senderId, recipientId, momentId, storyId, commentId, reaction,
    )

    fun sendInteractionNotification(
        type: NotificationType,
        senderId: String,
        recipientId: String,
        momentId: String? = null,
        commentId: String? = null,
        content: String? = null,
    ) = NotificationsNotificationService.sendInteractionNotification(
        type = type,
        targetUserId = recipientId,
        momentId = momentId,
        commentId = commentId,
        reaction = content,
    )

    fun sendCommentMentionNotification(
        mentionedUserId: String,
        fromUserId: String,
        fromUsername: String,
        momentId: String,
        momentAuthorId: String,
        commentId: String,
        content: String,
    ) = NotificationsNotificationService.sendCommentMentionNotification(
        targetUserId = mentionedUserId,
        momentId = momentId,
        momentAuthorId = momentAuthorId,
        commentId = commentId,
        commentText = content,
        senderUsername = fromUsername,
    )
}
