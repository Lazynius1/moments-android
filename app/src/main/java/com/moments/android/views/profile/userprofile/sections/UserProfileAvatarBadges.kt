package com.moments.android.views.profile.userprofile.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.models.AppUser
import com.moments.android.views.story.StoryRingAvatarView

/**
 * Port de `UserModernAvatarWithBadges.swift`.
 *
 * Como iOS tras el fix: sin clickable interno del aro (`onTap = null`);
 * tap + long-press viven solo aquí (si no, el clickable del hijo se come el hold).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserModernAvatarWithBadges(
    userProfile: AppUser?,
    size: Dp,
    storyRingRefreshTrigger: Int,
    onOpenStories: () -> Unit,
    onShowProfileImageFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    onPreviewStory: ((Rect) -> Unit)? = null,
) {
    var hasActiveStory by remember(userProfile?.id) { mutableStateOf(false) }
    var avatarBounds by remember { mutableStateOf(Rect.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "profileAvatarPressScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(120),
        label = "profileAvatarPressAlpha",
    )

    Box(
        modifier
            .onGloballyPositioned { avatarBounds = it.boundsInWindow() }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = {
                    if (hasActiveStory && onPreviewStory != null) {
                        onPreviewStory(avatarBounds)
                    } else {
                        onShowProfileImageFullscreen()
                    }
                },
                onClick = {
                    if (hasActiveStory) onOpenStories() else onShowProfileImageFullscreen()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Sin onTap → sin clickable hijo que trague el long-press.
        StoryRingAvatarView(
            userId = userProfile?.id.orEmpty(),
            size = size,
            lineWidth = 3.dp,
            refreshTrigger = storyRingRefreshTrigger,
            isOwnStory = false,
            onTap = null,
            onHasStoryChange = { hasActiveStory = it },
        )
    }
}
