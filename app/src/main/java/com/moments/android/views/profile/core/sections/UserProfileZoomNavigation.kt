package com.moments.android.views.profile.core.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.views.shared.LocalMomentsSharedTransitionScope
import com.moments.android.views.shared.MomentsSharedTransitionLayout
import com.moments.android.views.shared.ProvideMomentsSharedAnimatedVisibilityScope

/** Port de `UserProfileZoomNavigation.swift`. */
object UserProfileZoomNavigation {
    fun sourceID(userId: String): String = "user-profile-$userId"
}

/** El host de navegación usa este source id al abrir UserProfileView. */
fun userProfileZoomDestinationSourceID(userId: String): String = UserProfileZoomNavigation.sourceID(userId)

/**
 * ≡ `userProfileZoomSource` iOS (`matchedTransitionSource`).
 * Shared element vía Compose; sin [LocalMomentsSharedTransitionScope] solo aplica clip.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.userProfileZoomSource(
    userId: String,
    visible: Boolean = true,
    cornerRadius: Dp = 22.dp,
): Modifier {
    if (userId.isBlank()) return this
    val sharedScope = LocalMomentsSharedTransitionScope.current
    val clipped = this.clip(RoundedCornerShape(cornerRadius))
    if (sharedScope == null) return clipped
    val state = with(sharedScope) {
        rememberSharedContentState(key = UserProfileZoomNavigation.sourceID(userId))
    }
    return with(sharedScope) {
        clipped.sharedElementWithCallerManagedVisibility(
            sharedContentState = state,
            visible = visible,
        )
    }
}

/**
 * ≡ `userProfileZoomDestination` iOS (`.navigationTransition(.zoom(...))`).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.userProfileZoomDestination(
    userId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    if (userId.isBlank()) return this
    val state = with(sharedTransitionScope) {
        rememberSharedContentState(key = UserProfileZoomNavigation.sourceID(userId))
    }
    return with(sharedTransitionScope) {
        this@userProfileZoomDestination.sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

/**
 * ≡ `userProfileNavigationDestination(item:namespace:)` —
 * overlay in-tree (no Dialog) para que el shared-element funcione.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UserProfileZoomNavigationHost(
    profileRoute: FeedProfileSheetRoute?,
    onProfileRouteChange: (FeedProfileSheetRoute?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (profileOpen: Boolean) -> Unit,
) {
    MomentsSharedTransitionLayout(modifier.fillMaxSize()) {
        val sharedScope = this
        Box(Modifier.fillMaxSize()) {
            content(profileRoute != null)
            AnimatedVisibility(
                visible = profileRoute != null,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = tween(280)),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(220)),
            ) {
                val route = profileRoute
                val animatedScope = this
                if (route != null) {
                    ProvideMomentsSharedAnimatedVisibilityScope(animatedScope) {
                        UserProfileView(
                            userId = route.userId,
                            onDismiss = { onProfileRouteChange(null) },
                            modifier = Modifier
                                .fillMaxSize()
                                .userProfileZoomDestination(
                                    userId = route.userId,
                                    sharedTransitionScope = sharedScope,
                                    animatedVisibilityScope = animatedScope,
                                ),
                        )
                    }
                }
            }
        }
    }
}
