package com.moments.android.notifications.row

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationGroupedActors
import com.moments.android.notifications.core.isPerActorSocialNotification
import com.moments.android.notifications.core.notificationGroupedMessage
import com.moments.android.notifications.core.styledNotificationMessage
import com.moments.android.notifications.core.uniqueSenderIds
import com.moments.android.notifications.services.NotificationCopyResolver
import com.moments.android.views.feed.reactions.ReactionType

/**
 * Port de EnhancedNotificationRow+Messages.swift
 *
 * Helpers de display names viven aquí (en iOS +Follow / shell).
 */
object EnhancedNotificationRowMessages {

    fun senderDisplayName(
        notification: MomentsNotification,
        senderUsernameOverride: String? = null,
        someoneFallback: String,
    ): String {
        val override = senderUsernameOverride?.trim().orEmpty()
        if (override.isNotEmpty()) return override
        val username = notification.senderUsername.trim()
        return username.ifEmpty { someoneFallback }
    }

    fun displayName(
        group: NotificationGroup,
        notification: MomentsNotification,
        senderUsernameOverride: String?,
        someoneFallback: String,
    ): String {
        if (notification.id == group.notifications.firstOrNull()?.id) {
            val override = senderUsernameOverride?.trim().orEmpty()
            if (override.isNotEmpty()) return override
        }
        return senderDisplayName(notification, null, someoneFallback)
    }

    fun senderDisplayNamesToUserIds(
        group: NotificationGroup,
        senderUsernameOverride: String?,
        someoneFallback: String,
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val seen = mutableSetOf<String>()
        for (notification in group.notifications) {
            val id = notification.senderId.trim()
            if (id.isEmpty() || !seen.add(id)) continue
            map[displayName(group, notification, senderUsernameOverride, someoneFallback)] = id
        }
        val first = group.notifications.firstOrNull()
        if (first != null) {
            val authorName = first.targetAuthorUsername?.trim().orEmpty()
            val authorId = first.targetAuthorId?.trim().orEmpty()
            if (authorName.isNotEmpty() && authorId.isNotEmpty()) {
                map[authorName] = authorId
            }
        }
        return map
    }

    fun groupedActorsForMessage(
        group: NotificationGroup,
        senderUsernameOverride: String?,
        someoneFallback: String,
    ): NotificationGroupedActors {
        val seen = mutableSetOf<String>()
        val names = mutableListOf<String>()
        for (notification in group.notifications) {
            val id = notification.senderId.trim()
            if (id.isEmpty() || !seen.add(id)) continue
            names.add(displayName(group, notification, senderUsernameOverride, someoneFallback))
        }
        val primary = names.firstOrNull()
            ?: senderDisplayName(group.notifications.first(), senderUsernameOverride, someoneFallback)
        return if (names.size >= 2) {
            NotificationGroupedActors(primary, names[1], maxOf(0, names.size - 2))
        } else {
            NotificationGroupedActors(primary, null, 0)
        }
    }

    /** ≡ messageForGroup(_:) */
    fun messageForGroup(
        group: NotificationGroup,
        isDark: Boolean,
        senderUsernameOverride: String? = null,
    ): AnnotatedString {
        val ctx = MomentsApplication.instance
            ?: return AnnotatedString(group.notifications.first().senderUsername)
        val someone = ctx.getString(R.string.notifications_grouped_followers_unknown_user)
        val first = group.notifications.first()
        val nameToUserId = senderDisplayNamesToUserIds(group, senderUsernameOverride, someone)
        val messageColor = if (isDark) Color.White else Color.Black
        val effectiveSenderUsername = senderDisplayName(first, senderUsernameOverride, someone)
        val reactionAggregateCount = if (first.type == NotificationType.REACTION) {
            maxOf(1, first.reactionCount ?: group.notifications.size)
        } else {
            group.notifications.size
        }
        val hasMultipleActors = when {
            first.type == NotificationType.REACTION -> reactionAggregateCount > 1
            first.type == NotificationType.NEW_FOLLOWER || first.type == NotificationType.MUTUAL_CONNECTION ->
                group.notifications.size > 1
            isPerActorSocialNotification(first.type) -> false
            else -> uniqueSenderIds(group).size > 1
        }

        return if (hasMultipleActors) {
            multiActorMessage(
                ctx, group, first, messageColor, nameToUserId,
                effectiveSenderUsername, reactionAggregateCount, someone, senderUsernameOverride,
            )
        } else {
            singleActorMessage(
                ctx, group, first, messageColor, nameToUserId,
                effectiveSenderUsername, someone, senderUsernameOverride,
            )
        }
    }

    /** Compat: texto plano (tests / call sites antiguos). */
    fun messageForGroupPlain(group: NotificationGroup, isDark: Boolean): String =
        messageForGroup(group, isDark).text

    private fun multiActorMessage(
        ctx: Context,
        group: NotificationGroup,
        first: MomentsNotification,
        messageColor: Color,
        nameToUserId: Map<String, String>,
        effectiveSenderUsername: String,
        reactionAggregateCount: Int,
        someone: String,
        override: String?,
    ): AnnotatedString {
        val actors = groupedActorsForMessage(group, override, someone)
        return when (first.type) {
            NotificationType.LIKE -> notificationGroupedMessage(
                ctx,
                R.string.notifications_message_like_two,
                R.string.notifications_message_like_three_plus,
                R.string.notifications_message_like_multiple,
                actors, nameToUserId, messageColor,
            )
            NotificationType.REACTION -> reactionMulti(ctx, first, actors, nameToUserId, messageColor, reactionAggregateCount)
            NotificationType.MENTION -> mentionMessage(ctx, first, actors, nameToUserId, messageColor)
            NotificationType.NEW_FOLLOWER -> notificationGroupedMessage(
                ctx,
                R.string.notifications_message_follow_two,
                R.string.notifications_message_follow_three_plus,
                R.string.notifications_message_follow_multiple,
                actors, nameToUserId, messageColor,
            )
            NotificationType.FOLLOW_REQUEST -> notificationGroupedMessage(
                ctx,
                R.string.notifications_message_request_two,
                R.string.notifications_message_request_three_plus,
                R.string.notifications_message_request_multiple,
                actors, nameToUserId, messageColor,
            )
            NotificationType.REQUEST_ACCEPTED -> notificationGroupedMessage(
                ctx,
                R.string.notifications_message_request_accepted_two,
                R.string.notifications_message_request_accepted_three_plus,
                R.string.notifications_message_request_accepted_multiple,
                actors, nameToUserId, messageColor,
            )
            NotificationType.MUTUAL_CONNECTION -> notificationGroupedMessage(
                ctx,
                R.string.notifications_message_mutual_two,
                R.string.notifications_message_mutual_three_plus,
                R.string.notifications_message_mutual_multiple,
                actors, nameToUserId, messageColor,
            )
            NotificationType.COMMENT -> commentMessage(ctx, first, actors, nameToUserId, messageColor)
            NotificationType.STORY_REACTION -> notificationGroupedMessage(
                ctx,
                R.string.notifications_message_story_two,
                R.string.notifications_message_story_three_plus,
                R.string.notifications_message_story_multiple,
                actors, nameToUserId, messageColor,
            )
            NotificationType.MESSAGE -> styledNotificationMessage(
                ctx.getString(
                    R.string.notifications_message_message_multiple,
                    effectiveSenderUsername,
                    group.notifications.size - 1,
                ),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.PHOTO_TAG -> photoTagMessage(ctx, first, actors, nameToUserId, messageColor)
            NotificationType.ECHO_SUGGESTION ->
                AnnotatedString(ctx.getString(R.string.notifications_message_echo))
            NotificationType.DATA_EXPORT_READY -> exportMessage(ctx, first)
            NotificationType.STORY_CHAIN_CONTINUED -> {
                val chainTitle = first.chainTitle.orEmpty()
                val isCreator = first.chainRole != "participant"
                val key = if (isCreator) {
                    R.string.notifications_message_story_chain_creator_multiple
                } else {
                    R.string.notifications_message_story_chain_participant_multiple
                }
                // Localizable: "%1$s and %2$d others … \"%3$s\""
                styledNotificationMessage(
                    ctx.getString(key, effectiveSenderUsername, group.notifications.size - 1, chainTitle),
                    listOf(effectiveSenderUsername), nameToUserId, messageColor,
                )
            }
            NotificationType.MEDIA_MODERATION ->
                AnnotatedString(ctx.getString(R.string.notifications_message_media_moderation))
            NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ, NotificationType.GENTLE_REMINDER -> {
                val copy = NotificationCopyResolver.resolve(first)
                AnnotatedString(copy.body ?: copy.title)
            }
            else -> AnnotatedString(first.message ?: first.senderUsername)
        }
    }

    private fun singleActorMessage(
        ctx: Context,
        group: NotificationGroup,
        first: MomentsNotification,
        messageColor: Color,
        nameToUserId: Map<String, String>,
        effectiveSenderUsername: String,
        someone: String,
        override: String?,
    ): AnnotatedString {
        val actors = groupedActorsForMessage(group, override, someone)
        return when (first.type) {
            NotificationType.LIKE -> styledNotificationMessage(
                ctx.getString(R.string.notifications_message_like_single, effectiveSenderUsername),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.REACTION -> {
                val reactionType = ReactionType.fromRaw(first.reaction)
                if (reactionType != null) {
                    styledNotificationMessage(
                        ctx.getString(
                            R.string.notifications_message_reaction_single_with_type,
                            effectiveSenderUsername,
                            reactionType.icon,
                        ),
                        listOf(effectiveSenderUsername), nameToUserId, messageColor,
                        largeEmoji = reactionType.icon,
                    )
                } else {
                    styledNotificationMessage(
                        ctx.getString(R.string.notifications_message_reaction_single, effectiveSenderUsername),
                        listOf(effectiveSenderUsername), nameToUserId, messageColor,
                    )
                }
            }
            NotificationType.MENTION -> mentionMessage(ctx, first, actors, nameToUserId, messageColor)
            NotificationType.NEW_FOLLOWER -> styledNotificationMessage(
                ctx.getString(R.string.notifications_message_follow_single, effectiveSenderUsername),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.FOLLOW_REQUEST -> styledNotificationMessage(
                ctx.getString(R.string.notifications_message_request_single, effectiveSenderUsername),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.REQUEST_ACCEPTED -> styledNotificationMessage(
                ctx.getString(R.string.notifications_message_request_accepted_single, effectiveSenderUsername),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.MUTUAL_CONNECTION -> styledNotificationMessage(
                ctx.getString(R.string.notifications_message_mutual_single, effectiveSenderUsername),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.COMMENT -> commentMessage(ctx, first, actors, nameToUserId, messageColor)
            NotificationType.STORY_REACTION -> styledNotificationMessage(
                ctx.getString(R.string.notifications_message_story_single, effectiveSenderUsername),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.MESSAGE -> styledNotificationMessage(
                ctx.getString(R.string.notifications_message_message_single, effectiveSenderUsername),
                listOf(effectiveSenderUsername), nameToUserId, messageColor,
            )
            NotificationType.PHOTO_TAG -> photoTagMessage(ctx, first, actors, nameToUserId, messageColor)
            NotificationType.ECHO_SUGGESTION ->
                AnnotatedString(ctx.getString(R.string.notifications_message_echo))
            NotificationType.DATA_EXPORT_READY -> exportMessage(ctx, first)
            NotificationType.STORY_CHAIN_CONTINUED -> {
                val chainTitle = first.chainTitle.orEmpty()
                val isCreator = first.chainRole != "participant"
                val totalParts = first.totalParts ?: first.chainPosition ?: 1
                val key = if (isCreator) {
                    R.string.notifications_message_story_chain_creator_single
                } else {
                    R.string.notifications_message_story_chain_participant_single
                }
                styledNotificationMessage(
                    ctx.getString(key, effectiveSenderUsername, chainTitle, totalParts.toString()),
                    listOf(effectiveSenderUsername), nameToUserId, messageColor,
                )
            }
            NotificationType.MEDIA_MODERATION ->
                AnnotatedString(ctx.getString(R.string.notifications_message_media_moderation))
            NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ, NotificationType.GENTLE_REMINDER -> {
                val copy = NotificationCopyResolver.resolve(first)
                AnnotatedString(copy.body ?: copy.title)
            }
            else -> AnnotatedString(first.message ?: first.senderUsername)
        }
    }

    private fun exportMessage(ctx: Context, first: MomentsNotification): AnnotatedString {
        val exportMessage = first.message?.trim().orEmpty()
        return if (exportMessage.isEmpty()) {
            AnnotatedString(ctx.getString(R.string.notifications_message_data_export_ready))
        } else {
            AnnotatedString(exportMessage)
        }
    }

    private fun reactionMulti(
        ctx: Context,
        first: MomentsNotification,
        actors: NotificationGroupedActors,
        nameToUserId: Map<String, String>,
        messageColor: Color,
        reactionAggregateCount: Int,
    ): AnnotatedString {
        val reactionType = ReactionType.fromRaw(first.reaction)
        if (reactionType == null) {
            return notificationGroupedMessage(
                ctx,
                R.string.notifications_message_reaction_two,
                R.string.notifications_message_reaction_three_plus,
                R.string.notifications_message_reaction_multiple,
                actors, nameToUserId, messageColor,
            )
        }
        val boldNames = buildList {
            add(actors.primary)
            actors.secondary?.let { add(it) }
        }
        val text = when {
            actors.hasExactlyTwo && actors.secondary != null ->
                ctx.getString(
                    R.string.notifications_message_reaction_two_with_type,
                    actors.primary,
                    reactionType.icon,
                    actors.secondary,
                )
            actors.secondary != null && actors.othersCount > 0 ->
                ctx.getString(
                    R.string.notifications_message_reaction_three_plus_with_type,
                    actors.primary,
                    reactionType.icon,
                    actors.secondary,
                    actors.othersCount,
                )
            else ->
                ctx.getString(
                    R.string.notifications_message_reaction_multiple_with_type,
                    actors.primary,
                    reactionType.icon,
                    maxOf(actors.othersCount, reactionAggregateCount - 1),
                )
        }
        return styledNotificationMessage(
            text, boldNames, nameToUserId, messageColor, largeEmoji = reactionType.icon,
        )
    }

    /** ≡ mentionMessage(for:actors:…) */
    fun mentionMessage(
        ctx: Context,
        notification: MomentsNotification,
        actors: NotificationGroupedActors,
        nameToUserId: Map<String, String>,
        baseColor: Color,
    ): AnnotatedString {
        val context = notification.mentionContext
            ?: when {
                notification.storyId != null -> "story"
                notification.commentId != null -> "comment"
                else -> "moment"
            }
        val keyPrefix = when (context) {
            "story" -> "story"
            "comment" ->
                if (!notification.targetAuthorUsername.isNullOrBlank()) "comment_with_author" else "comment"
            else -> "moment"
        }
        fun res(suffix: String): Int = when (keyPrefix) {
            "story" -> when (suffix) {
                "single" -> R.string.notifications_message_mention_story_single
                "two" -> R.string.notifications_message_mention_story_two
                "threePlus" -> R.string.notifications_message_mention_story_three_plus
                else -> R.string.notifications_message_mention_story_multiple
            }
            "comment_with_author" -> when (suffix) {
                "single" -> R.string.notifications_message_mention_comment_with_author_single
                "two" -> R.string.notifications_message_mention_comment_with_author_two
                "threePlus" -> R.string.notifications_message_mention_comment_with_author_three_plus
                else -> R.string.notifications_message_mention_comment_with_author_multiple
            }
            "comment" -> when (suffix) {
                "single" -> R.string.notifications_message_mention_comment_single
                "two" -> R.string.notifications_message_mention_comment_two
                "threePlus" -> R.string.notifications_message_mention_comment_three_plus
                else -> R.string.notifications_message_mention_comment_multiple
            }
            else -> when (suffix) {
                "single" -> R.string.notifications_message_mention_moment_single
                "two" -> R.string.notifications_message_mention_moment_two
                "threePlus" -> R.string.notifications_message_mention_moment_three_plus
                else -> R.string.notifications_message_mention_moment_multiple
            }
        }
        fun boldMentionNames(extra: String? = null): List<String> = buildList {
            add(actors.primary)
            actors.secondary?.let { add(it) }
            if (!extra.isNullOrEmpty()) add(extra)
        }
        val author = notification.targetAuthorUsername?.trim().orEmpty()
        val withAuthor = context == "comment" && author.isNotEmpty()

        if (actors.hasExactlyTwo && actors.secondary != null) {
            val text = if (withAuthor) {
                ctx.getString(res("two"), actors.primary, actors.secondary, author)
            } else {
                ctx.getString(res("two"), actors.primary, actors.secondary)
            }
            return styledNotificationMessage(
                text, boldMentionNames(if (withAuthor) author else null), nameToUserId, baseColor,
            )
        }
        if (actors.secondary != null && actors.othersCount > 0) {
            val text = if (withAuthor) {
                ctx.getString(res("threePlus"), actors.primary, actors.secondary, actors.othersCount, author)
            } else {
                ctx.getString(res("threePlus"), actors.primary, actors.secondary, actors.othersCount)
            }
            return styledNotificationMessage(
                text, boldMentionNames(if (withAuthor) author else null), nameToUserId, baseColor,
            )
        }
        if (actors.othersCount > 0) {
            val text = if (withAuthor) {
                ctx.getString(res("multiple"), actors.primary, actors.othersCount, author)
            } else {
                ctx.getString(res("multiple"), actors.primary, actors.othersCount)
            }
            return styledNotificationMessage(
                text, boldMentionNames(if (withAuthor) author else null), nameToUserId, baseColor,
            )
        }
        val text = if (withAuthor) {
            ctx.getString(res("single"), actors.primary, author)
        } else {
            ctx.getString(res("single"), actors.primary)
        }
        return styledNotificationMessage(
            text, boldMentionNames(if (withAuthor) author else null), nameToUserId, baseColor,
        )
    }

    /** ≡ photoTagMessage(for:actors:…) */
    fun photoTagMessage(
        ctx: Context,
        notification: MomentsNotification,
        actors: NotificationGroupedActors,
        nameToUserId: Map<String, String>,
        baseColor: Color,
    ): AnnotatedString {
        val momentTitle = notification.reaction?.trim().orEmpty().ifEmpty { null }
        fun boldTagNames(): List<String> = buildList {
            add(actors.primary)
            actors.secondary?.let { add(it) }
        }
        if (actors.hasExactlyTwo && actors.secondary != null) {
            val text = if (momentTitle != null) {
                ctx.getString(
                    R.string.notifications_message_tagged_with_title_two,
                    actors.primary, actors.secondary, momentTitle,
                )
            } else {
                ctx.getString(R.string.notifications_message_tagged_two, actors.primary, actors.secondary)
            }
            return styledNotificationMessage(text, boldTagNames(), nameToUserId, baseColor)
        }
        if (actors.secondary != null && actors.othersCount > 0) {
            val text = if (momentTitle != null) {
                ctx.getString(
                    R.string.notifications_message_tagged_with_title_three_plus,
                    actors.primary, actors.secondary, actors.othersCount, momentTitle,
                )
            } else {
                ctx.getString(
                    R.string.notifications_message_tagged_three_plus,
                    actors.primary, actors.secondary, actors.othersCount,
                )
            }
            return styledNotificationMessage(text, boldTagNames(), nameToUserId, baseColor)
        }
        if (actors.othersCount > 0) {
            val text = if (momentTitle != null) {
                ctx.getString(
                    R.string.notifications_message_tagged_with_title_multiple,
                    actors.primary, actors.othersCount, momentTitle,
                )
            } else {
                ctx.getString(R.string.notifications_message_tagged_multiple, actors.primary, actors.othersCount)
            }
            return styledNotificationMessage(text, listOf(actors.primary), nameToUserId, baseColor)
        }
        val text = if (momentTitle != null) {
            ctx.getString(R.string.notifications_message_tagged_with_title_single, actors.primary, momentTitle)
        } else {
            ctx.getString(R.string.notifications_message_tagged_single, actors.primary)
        }
        return styledNotificationMessage(text, listOf(actors.primary), nameToUserId, baseColor)
    }

    /** ≡ commentMessage(for:actors:…) */
    fun commentMessage(
        ctx: Context,
        notification: MomentsNotification,
        actors: NotificationGroupedActors,
        nameToUserId: Map<String, String>,
        baseColor: Color,
    ): AnnotatedString {
        val isReply = notification.mentionContext == "reply"
        fun res(suffix: String): Int = if (isReply) {
            when (suffix) {
                "single" -> R.string.notifications_message_reply_single
                "two" -> R.string.notifications_message_reply_two
                "threePlus" -> R.string.notifications_message_reply_three_plus
                else -> R.string.notifications_message_reply_multiple
            }
        } else {
            when (suffix) {
                "single" -> R.string.notifications_message_comment_single
                "two" -> R.string.notifications_message_comment_two
                "threePlus" -> R.string.notifications_message_comment_three_plus
                else -> R.string.notifications_message_comment_multiple
            }
        }
        fun boldCommentNames(): List<String> = buildList {
            add(actors.primary)
            actors.secondary?.let { add(it) }
        }
        if (actors.hasExactlyTwo && actors.secondary != null) {
            return styledNotificationMessage(
                ctx.getString(res("two"), actors.primary, actors.secondary),
                boldCommentNames(), nameToUserId, baseColor,
            )
        }
        if (actors.secondary != null && actors.othersCount > 0) {
            return styledNotificationMessage(
                ctx.getString(res("threePlus"), actors.primary, actors.secondary, actors.othersCount),
                boldCommentNames(), nameToUserId, baseColor,
            )
        }
        if (actors.othersCount > 0) {
            return styledNotificationMessage(
                ctx.getString(res("multiple"), actors.primary, actors.othersCount),
                listOf(actors.primary), nameToUserId, baseColor,
            )
        }
        return styledNotificationMessage(
            ctx.getString(res("single"), actors.primary),
            listOf(actors.primary), nameToUserId, baseColor,
        )
    }
}
