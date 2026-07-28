package com.moments.android.views.feed.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.views.components.shimmer
import com.moments.android.views.feed.core.sections.rememberShimmerBrush
import com.moments.android.views.story.StorySegmentedRing

/**
 * Port de `StoryRingTraySkeleton.swift` — mismas medidas que RealStoryCircle / YourStoryCircle.
 */

@Composable
fun StoryRingTraySkeletonCell(
    isOwnStory: Boolean,
    userId: String? = null,
    modifier: Modifier = Modifier,
) {
    val shimmerBrush = rememberShimmerBrush()
    val label = stringResource(R.string.feed_story_ring_loading)
    val avatarSize = StoryRingLayout.feedHeaderAvatarSize
    val ringLineWidth = StoryRingLayout.feedHeaderLineWidth
    val outerSize = StoryRingLayout.outerFrameSize(avatarSize, ringLineWidth)

    Column(
        modifier
            .width(StoryRingLayout.skeletonCellWidth)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(outerSize),
            contentAlignment = Alignment.Center,
        ) {
            StorySegmentedRing(
                storyCount = if (isOwnStory) 0 else 1,
                hasStory = !isOwnStory,
                hasUnseenStory = !isOwnStory,
                storyViewedStatus = if (isOwnStory) emptyList() else listOf(false),
                storyAudiences = emptyList(),
                isOwnStory = isOwnStory,
                ringSize = StoryRingLayout.ringStrokeDiameter(avatarSize, ringLineWidth),
                lineWidth = ringLineWidth,
                hapticsEnabled = false,
                modifier = Modifier.alpha(if (isOwnStory) 1f else 0.55f),
            )

            if (!userId.isNullOrBlank()) {
                AsyncProfileImageView(
                    userId = userId,
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .alpha(0.4f)
                        .shimmer(isAnimating = true),
                )
            } else {
                Box(
                    Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                        .shimmer(isAnimating = true),
                )
            }
        }
        Box(
            Modifier
                .width(if (isOwnStory) 52.dp else 44.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(shimmerBrush)
                .shimmer(isAnimating = true),
        )
    }
}

@Composable
fun StoryRingTraySkeletonRow(
    placeholderCount: Int = 6,
    currentUserId: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StoryRingTraySkeletonCell(isOwnStory = true, userId = currentUserId)
        repeat(maxOf(placeholderCount - 1, 0)) {
            StoryRingTraySkeletonCell(isOwnStory = false)
        }
    }
}

@Composable
fun StoryRingTrayLoadingTail(
    count: Int = 3,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            StoryRingTraySkeletonCell(isOwnStory = false)
        }
    }
}

/** Alias usado por `FeedHeaderSection.kt`. */
@Composable
fun StoryRingTraySkeleton(
    isOwnStory: Boolean,
    modifier: Modifier = Modifier,
) {
    StoryRingTraySkeletonCell(isOwnStory = isOwnStory, modifier = modifier)
}
