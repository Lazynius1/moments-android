package com.moments.android.views.story

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.services.social.StoryRingCacheService
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.services.social.StoryRingSnapshot
import com.moments.android.utilities.momentsPressIcon
import com.moments.android.views.feed.core.sections.FeedStoryRingAvatar

/**
 * Port 1:1 de `StoryRingLayout` + `StoryRingAvatarView` (Views/story/StoryRingAvatarView.swift).
 */
object StoryRingLayout {
    val feedHeaderAvatarSize = 50.dp
    val feedHeaderLineWidth = 3.dp
    val ringGap = 1.5.dp
    /** Ancho de celda del skeleton del tray (feed header). */
    val skeletonCellWidth = 64.dp

    fun defaultLineWidth(avatarSize: Dp): Dp {
        val scaled = avatarSize * (feedHeaderLineWidth / feedHeaderAvatarSize)
        return maxOf(2.8.dp, scaled)
    }

    fun ringStrokeDiameter(
        avatarSize: Dp = feedHeaderAvatarSize,
        lineWidth: Dp = feedHeaderLineWidth,
    ): Dp = avatarSize + ringGap * 2 + lineWidth

    fun outerFrameSize(
        avatarSize: Dp = feedHeaderAvatarSize,
        lineWidth: Dp = feedHeaderLineWidth,
    ): Dp = ringStrokeDiameter(avatarSize, lineWidth) + lineWidth + 2.dp
}

/**
 * Port 1:1 de `StoryRingAvatarView` (StoryRingAvatarView.swift).
 * Resuelve el snapshot vía `StoryRingResolverService` (como iOS).
 */
@Composable
fun StoryRingAvatarView(
    userId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    lineWidth: Dp? = null,
    refreshTrigger: Int = 0,
    isOwnStory: Boolean? = null,
    allowOwnStories: Boolean = true,
    hapticsEnabled: Boolean = false,
    showBaseStroke: Boolean = false,
    baseStrokeColor: Color = Color.White.copy(alpha = 0.2f),
    baseStrokeWidth: Dp = 1.dp,
    onTap: ((hasStory: Boolean) -> Unit)? = null,
) {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val resolvedIsOwnStory = isOwnStory ?: (viewerId != null && viewerId == userId)
    val resolvedLineWidth = lineWidth ?: StoryRingLayout.defaultLineWidth(size)
    val outerSize = StoryRingLayout.outerFrameSize(size, resolvedLineWidth)

    var snapshot by remember(userId) {
        mutableStateOf(
            StoryRingSnapshot(
                hasStory = false,
                hasUnseenStory = false,
                storyCount = 0,
                storyViewedStatus = emptyList(),
                storyAudiences = emptyList(),
            ),
        )
    }

    LaunchedEffect(userId, viewerId, refreshTrigger, allowOwnStories) {
        snapshot = resolveSnapshot(
            userId = userId,
            viewerId = viewerId,
            allowOwnStories = allowOwnStories,
            forceRefresh = refreshTrigger > 0,
        )
    }

    val interaction = remember { MutableInteractionSource() }
    val contentModifier = modifier
        .size(outerSize)
        .then(
            if (onTap != null) {
                Modifier
                    .momentsPressIcon()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onTap(snapshot.hasStory) },
                    )
            } else {
                Modifier
            },
        )

    Box(contentModifier, contentAlignment = Alignment.Center) {
        FeedStoryRingAvatar(
            avatarSize = size,
            lineWidth = resolvedLineWidth,
            imageUrl = null,
            hasStory = snapshot.hasStory,
            hasUnseenStory = snapshot.hasUnseenStory,
            storyCount = snapshot.storyCount,
            viewedStatuses = snapshot.storyViewedStatus,
            storyAudiences = snapshot.storyAudiences,
            isOwnStory = resolvedIsOwnStory,
            hapticsEnabled = hapticsEnabled,
            placeholder = {
                AsyncProfileImageView(
                    userId = userId,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            },
        )
    }
}

private suspend fun resolveSnapshot(
    userId: String,
    viewerId: String?,
    allowOwnStories: Boolean,
    forceRefresh: Boolean,
): StoryRingSnapshot {
    val empty = StoryRingSnapshot(
        hasStory = false,
        hasUnseenStory = false,
        storyCount = 0,
        storyViewedStatus = emptyList(),
        storyAudiences = emptyList(),
    )
    if (userId.isEmpty()) return empty
    if (viewerId.isNullOrEmpty()) return empty
    if (!allowOwnStories && viewerId == userId) return empty

    if (forceRefresh) {
        StoryRingCacheService.invalidate(viewerId, userId)
    }
    return StoryRingResolverService.resolve(
        viewerId = viewerId,
        authorId = userId,
        useCache = !forceRefresh,
    )
}
