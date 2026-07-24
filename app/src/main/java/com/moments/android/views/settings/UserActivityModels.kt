package com.moments.android.views.settings

import com.moments.android.models.Moment
import com.moments.android.models.Story
import java.util.Date

/** Port de `UserActivityModels.swift`. */
enum class ActivityOverlayBadgeStyle { NONE, AUDIENCE, REACTION_DISCREET }

data class ActivityReactionItem(
    val id: String,
    val authorId: String,
    val momentId: String,
    val reactionType: String,
    val reactedAt: Date,
    val moment: Moment?,
    val canView: Boolean,
)

data class ActivityDeletedStoryItem(
    val id: String,
    val story: Story,
    val deletedAt: Date,
)

data class ActivityCommentItem(
    val id: String,
    val authorId: String,
    val momentId: String,
    val commentId: String,
    val commentText: String,
    val commentedAt: Date,
    val moment: Moment?,
    val canView: Boolean,
)

data class ActivityEventItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: Date,
    val icon: String,
    val actorId: String? = null,
    val actorUsername: String? = null,
    val actorProfileImagePath: String? = null,
    val actionText: String? = null,
    val kind: String? = null,
    val targetAuthorId: String? = null,
    val targetUsername: String? = null,
    val storyId: String? = null,
    val sourceId: String? = null,
    val contextText: String? = null,
    val thumbnailUrl: String? = null,
    val echoStatusRaw: String? = null,
    val echoParticipantsCount: Int? = null,
    val echoExpiresAt: Date? = null,
)
