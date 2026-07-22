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
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.StoryRingColors
import com.moments.android.views.feed.StoryRingViewed
import com.moments.android.views.feed.core.sections.rememberShimmerBrush

/** Port de `StoryRingTraySkeleton.swift`. */
@Composable
fun StoryRingTraySkeletonCell(
    isOwnStory: Boolean,
    userId: String? = null,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberShimmerBrush()
    val label = stringResource(R.string.feed_story_ring_loading)
    val outerSize = StoryRingLayout.outerFrameSize()
    val avatarSize = StoryRingLayout.feedHeaderAvatarSize
    val ringColors = if (isOwnStory) StoryRingViewed else StoryRingColors

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
            Box(
                Modifier
                    .size(StoryRingLayout.ringStrokeDiameter())
                    .alpha(if (isOwnStory) 1f else 0.55f)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(ringColors)),
            )
            Box(
                Modifier
                    .size(avatarSize + StoryRingLayout.ringGap * 2)
                    .clip(CircleShape)
                    .background(FeedCanvas),
            )
            Box(
                Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(shimmer),
            )
        }
        Box(
            Modifier
                .width(if (isOwnStory) 52.dp else 44.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(shimmer),
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
