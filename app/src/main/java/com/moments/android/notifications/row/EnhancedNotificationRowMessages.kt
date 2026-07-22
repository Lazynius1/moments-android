package com.moments.android.notifications.row

import android.content.Context
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.models.NotificationType
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationGroupedActors
import com.moments.android.notifications.core.uniqueSenderIds

/** Port de EnhancedNotificationRow+Messages.swift */
object EnhancedNotificationRowMessages {
    fun messageForGroup(group: NotificationGroup, isDark: Boolean): String {
        val ctx = MomentsApplication.instance ?: return group.notifications.first().senderUsername
        val first = group.notifications.first()
        val actors = groupedActorsForMessage(group)
        return when (first.type) {
            NotificationType.MESSAGE -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_message_single,
                R.string.notifications_message_message_two,
                R.string.notifications_message_message_three_plus,
                R.string.notifications_message_message_multiple,
            )
            NotificationType.NEW_FOLLOWER -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_follow_single,
                R.string.notifications_message_follow_two,
                R.string.notifications_message_follow_three_plus,
                R.string.notifications_message_follow_multiple,
            )
            NotificationType.FOLLOW_REQUEST -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_request_single,
                R.string.notifications_message_request_two,
                R.string.notifications_message_request_three_plus,
                R.string.notifications_message_request_multiple,
            )
            NotificationType.REACTION -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_reaction_single,
                R.string.notifications_message_reaction_two,
                R.string.notifications_message_reaction_three_plus,
                R.string.notifications_message_reaction_multiple,
            )
            NotificationType.COMMENT -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_comment_single,
                R.string.notifications_message_comment_two,
                R.string.notifications_message_comment_three_plus,
                R.string.notifications_message_comment_multiple,
            )
            NotificationType.STORY_REACTION -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_story_single,
                R.string.notifications_message_story_two,
                R.string.notifications_message_story_three_plus,
                R.string.notifications_message_story_multiple,
            )
            NotificationType.MENTION -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_mention_single,
                R.string.notifications_message_mention_single,
                R.string.notifications_message_mention_multiple,
                R.string.notifications_message_mention_multiple,
            )
            NotificationType.PHOTO_TAG -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_tagged_single,
                R.string.notifications_message_tagged_two,
                R.string.notifications_message_tagged_three_plus,
                R.string.notifications_message_tagged_multiple,
            )
            NotificationType.REQUEST_ACCEPTED -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_request_accepted_single,
                R.string.notifications_message_request_accepted_two,
                R.string.notifications_message_request_accepted_three_plus,
                R.string.notifications_message_request_accepted_multiple,
            )
            NotificationType.MUTUAL_CONNECTION -> formatGrouped(
                ctx, actors,
                R.string.notifications_message_mutual_single,
                R.string.notifications_message_mutual_two,
                R.string.notifications_message_mutual_three_plus,
                R.string.notifications_message_mutual_multiple,
            )
            NotificationType.STORY_CHAIN_CONTINUED -> {
                val chainTitle = first.chainTitle ?: "Chain"
                if (group.notifications.size > 1) {
                    ctx.getString(
                        R.string.notifications_message_story_chain_multiple,
                        actors.primary,
                        chainTitle,
                        group.notifications.size - 1,
                    )
                } else {
                    ctx.getString(R.string.notifications_message_story_chain_single, actors.primary, chainTitle)
                }
            }
            NotificationType.ECHO_SUGGESTION -> ctx.getString(R.string.notifications_message_echo, actors.primary)
            NotificationType.MEDIA_MODERATION -> first.title ?: first.message ?: first.senderUsername
            else -> first.message ?: first.senderUsername
        }
    }

    private fun groupedActorsForMessage(group: NotificationGroup): NotificationGroupedActors {
        val names = uniqueSenderIds(group).map { senderId ->
            group.notifications.firstOrNull { it.senderId == senderId }?.senderUsername?.trim().orEmpty()
                .ifEmpty { "Someone" }
        }
        val primary = names.firstOrNull() ?: group.notifications.first().senderUsername
        return if (names.size >= 2) {
            NotificationGroupedActors(primary, names[1], maxOf(0, names.size - 2))
        } else {
            NotificationGroupedActors(primary, null, 0)
        }
    }

    private fun formatGrouped(
        ctx: Context,
        actors: NotificationGroupedActors,
        single: Int,
        two: Int,
        threePlus: Int,
        multiple: Int,
    ): String = when {
        actors.hasExactlyTwo && actors.secondary != null ->
            ctx.getString(two, actors.primary, actors.secondary)
        actors.secondary != null && actors.othersCount > 0 ->
            ctx.getString(threePlus, actors.primary, actors.secondary, actors.othersCount)
        actors.othersCount > 0 ->
            ctx.getString(multiple, actors.primary, maxOf(actors.othersCount, 1))
        else -> ctx.getString(single, actors.primary)
    }
}
