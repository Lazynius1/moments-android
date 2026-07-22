package com.moments.android.notifications.services

import android.content.Context
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.models.MessageType
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType

data class NotificationBannerCopy(
    val title: String,
    val body: String?,
    val preview: String? = null,
)

/** Port de NotificationCopyResolver.swift */
object NotificationCopyResolver {
    fun resolve(notification: MomentsNotification): NotificationBannerCopy {
        val ctx = MomentsApplication.instance ?: return NotificationBannerCopy(notification.senderUsername, notification.message)
        if (notification.senderId == "system_time_limit") {
            return NotificationBannerCopy(notification.senderUsername, notification.reaction)
        }
        if (notification.type == NotificationType.MEDIA_MODERATION) {
            return NotificationBannerCopy(
                notification.title ?: notification.senderUsername,
                notification.message ?: notification.reaction,
            )
        }
        return when (notification.type) {
            NotificationType.MESSAGE -> messageCopy(ctx, notification)
            NotificationType.MESSAGE_REACTION -> messageReactionCopy(ctx, notification)
            NotificationType.CHAT_BUZZ -> NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.notification_chat_buzz_single),
            )
            NotificationType.GENTLE_REMINDER -> gentleReminderCopy(ctx, notification)
            NotificationType.REACTION -> momentReactionCopy(ctx, notification)
            NotificationType.STORY_REACTION -> storyReactionCopy(ctx, notification)
            NotificationType.COMMENT -> commentCopy(ctx, notification)
            NotificationType.NEW_FOLLOWER -> followerCopy(ctx, notification, accepted = false)
            NotificationType.REQUEST_ACCEPTED -> followerCopy(ctx, notification, accepted = true)
            NotificationType.FOLLOW_REQUEST -> followRequestCopy(ctx, notification)
            NotificationType.MENTION -> mentionCopy(ctx, notification)
            NotificationType.PHOTO_TAG -> photoTagCopy(ctx, notification)
            NotificationType.STORY_CHAIN_CONTINUED -> storyChainCopy(ctx, notification)
            NotificationType.MUTUAL_CONNECTION -> mutualConnectionCopy(ctx, notification)
            NotificationType.DATA_EXPORT_READY -> NotificationBannerCopy(
                ctx.getString(R.string.notification_gentle_reminder_title),
                ctx.getString(R.string.notifications_message_data_export_ready),
            )
            NotificationType.ECHO_SUGGESTION -> NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.banner_verb_echo_suggestion),
            )
            else -> NotificationBannerCopy(
                notification.senderUsername,
                notification.message ?: notification.reaction,
            )
        }
    }

    private fun messageCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val unreadCount = notification.reactionCount ?: 0
        if (unreadCount > 1) {
            return NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.notification_message_multiple, unreadCount.toString()),
            )
        }
        sanitizedPreviewLine(notification.reaction, notification.messageType)?.let {
            return NotificationBannerCopy(notification.senderUsername, it)
        }
        return NotificationBannerCopy(
            notification.senderUsername,
            ctx.getString(messageLocKey(notification.messageType)),
        )
    }

    private fun messageLocKey(messageType: String?): Int = when (messageType) {
        "text" -> R.string.notification_message_single_text
        "image" -> R.string.notification_message_single_photo
        "video" -> R.string.notification_message_single_video
        "audio" -> R.string.notification_message_single_audio
        "viewOnceImage", "viewOnceVideo", "ephemeral" -> R.string.notification_message_single_view_once
        "moment", "sharedMoment" -> R.string.notification_message_single_moment
        else -> R.string.notification_message_single_default
    }

    private fun sanitizedPreviewLine(preview: String?, messageType: String?): String? {
        if (preview.isNullOrBlank()) return null
        messageType?.let {
            if (MessageType.from(it).isViewOnce) return null
        }
        if (neutralPreviewPrefixes.any { preview.startsWith(it) }) return null
        if (looksLikeEncryptedPayload(preview)) return null
        return preview
    }

    private fun messageReactionCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val emoji = notification.reaction ?: "❤️"
        val emojiList = notification.message ?: emoji
        val isPlural = notification.isReactionPlural == true || (notification.reactionCount ?: 0) > 1
        val quotedPreview = notification.title?.trim().orEmpty()
        val body = when {
            isPlural -> ctx.getString(R.string.notification_chat_reaction_multiple, emojiList)
            quotedPreview.isNotEmpty() -> ctx.getString(R.string.notification_chat_reaction_single_quoted, emoji, quotedPreview)
            else -> ctx.getString(R.string.notification_chat_reaction_single, emoji)
        }
        return NotificationBannerCopy(notification.senderUsername, body)
    }

    private fun gentleReminderCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val variant = notification.reminderVariant ?: "neutral_day"
        val bodyRes = when (variant) {
            "neutral_available" -> R.string.notification_gentle_reminder_body_neutral_available
            "editorial_beautiful" -> R.string.notification_gentle_reminder_body_editorial_beautiful
            "editorial_yours" -> R.string.notification_gentle_reminder_body_editorial_yours
            "inactive_anymoment" -> R.string.notification_gentle_reminder_body_inactive_anymoment
            else -> R.string.notification_gentle_reminder_body_neutral_day
        }
        return NotificationBannerCopy(ctx.getString(R.string.notification_gentle_reminder_title), ctx.getString(bodyRes))
    }

    private fun momentReactionCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val emoji = reactionDisplayEmoji(notification.reaction)
        val count = notification.reactionCount ?: 1
        return if (count > 1) {
            NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.notification_moment_reaction_multiple_title, notification.senderUsername, (count - 1).toString()),
            )
        } else {
            NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.notification_moment_reaction_single_title, notification.senderUsername, emoji),
            )
        }
    }

    private fun storyReactionCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val emoji = reactionDisplayEmoji(notification.reaction)
        val count = notification.reactionCount ?: 1
        return if (count > 1) {
            NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.notification_story_reaction_multiple_title, notification.senderUsername, (count - 1).toString()),
            )
        } else {
            NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.notification_story_reaction_single_title, notification.senderUsername, emoji),
            )
        }
    }

    private fun commentCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val count = notification.reactionCount ?: 1
        if (count > 1) {
            return NotificationBannerCopy(
                notification.senderUsername,
                ctx.getString(R.string.notification_comment_multiple_title, notification.senderUsername, (count - 1).toString()),
            )
        }
        notification.reaction?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return NotificationBannerCopy(notification.senderUsername, it)
        }
        return NotificationBannerCopy(
            notification.senderUsername,
            ctx.getString(R.string.notification_comment_single_title, notification.senderUsername),
        )
    }

    private fun followerCopy(ctx: Context, notification: MomentsNotification, accepted: Boolean): NotificationBannerCopy {
        val count = notification.reactionCount ?: 1
        if (count > 1) {
            return NotificationBannerCopy(
                ctx.getString(R.string.notification_follower_multiple_title, notification.senderUsername, (count - 1).toString()),
                ctx.getString(R.string.notification_follower_multiple_body, count.toString()),
            )
        }
        return if (accepted) {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_follower_accepted_request_single_title, notification.senderUsername),
                ctx.getString(R.string.notification_follower_accepted_request_single_body),
            )
        } else {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_follower_single_title, notification.senderUsername),
                ctx.getString(R.string.notification_follower_single_body),
            )
        }
    }

    private fun followRequestCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val count = notification.reactionCount ?: 1
        return if (count > 1) {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_follow_request_multiple_title, notification.senderUsername, (count - 1).toString()),
                ctx.getString(R.string.notification_follow_request_multiple_body, count.toString()),
            )
        } else {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_follow_request_single_title, notification.senderUsername),
                ctx.getString(R.string.notification_follow_request_single_body),
            )
        }
    }

    private fun mentionCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val contentType = when (notification.mentionContext ?: if (notification.storyId != null) "story" else "moment") {
            "story" -> ctx.getString(R.string.notification_mention_content_type_story)
            else -> ctx.getString(R.string.notification_mention_content_type_moment)
        }
        return NotificationBannerCopy(
            ctx.getString(R.string.notification_mention_title, notification.senderUsername, contentType),
            ctx.getString(R.string.notification_mention_body),
        )
    }

    private fun photoTagCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val title = notification.reaction?.trim().orEmpty()
        return if (title.isNotEmpty()) {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_photo_tag_with_title_title, notification.senderUsername, title),
                ctx.getString(R.string.notification_photo_tag_body),
            )
        } else {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_photo_tag_title, notification.senderUsername),
                ctx.getString(R.string.notification_photo_tag_body),
            )
        }
    }

    private fun storyChainCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val chainTitle = notification.chainTitle ?: ctx.getString(R.string.story_chains_chain)
        val totalParts = notification.totalParts?.toString() ?: "?"
        return if (notification.chainRole == "creator") {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_story_chain_creator_title, notification.senderUsername),
                ctx.getString(R.string.notification_story_chain_creator_body, chainTitle, totalParts),
            )
        } else {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_story_chain_participant_title, chainTitle),
                ctx.getString(R.string.notification_story_chain_participant_body, notification.senderUsername, totalParts),
            )
        }
    }

    private fun mutualConnectionCopy(ctx: Context, notification: MomentsNotification): NotificationBannerCopy {
        val count = notification.reactionCount ?: 1
        return if (count > 1) {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_mutual_connection_multiple_title),
                ctx.getString(R.string.notification_mutual_connection_multiple_body, notification.senderUsername, (count - 1).toString()),
            )
        } else {
            NotificationBannerCopy(
                ctx.getString(R.string.notification_mutual_connection_title),
                ctx.getString(R.string.notification_mutual_connection_body, notification.senderUsername),
            )
        }
    }

    private fun reactionDisplayEmoji(raw: String?): String {
        if (raw == null) return "✨"
        com.moments.android.views.feed.reactions.ReactionType.fromRaw(raw)?.let { return it.icon }
        return raw
    }

    private val neutralPreviewPrefixes = listOf("💬", "📷", "🎥", "🎵", "🎞", "😊", "📍", "📎", "📸", "⏱")

    private fun looksLikeEncryptedPayload(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 24) return false
        return trimmed.all { it.isLetterOrDigit() || it in "+/=_-" }
    }
}
