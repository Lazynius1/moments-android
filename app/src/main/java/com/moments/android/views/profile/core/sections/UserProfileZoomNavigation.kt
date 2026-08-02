package com.moments.android.views.profile.core.sections

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.views.shared.LocalActiveUserProfileZoomUserId
import com.moments.android.views.shared.LocalMomentsSharedAnimatedVisibilityScope
import com.moments.android.views.shared.LocalMomentsSharedTransitionScope
import com.moments.android.views.shared.MomentsContainerTransformOverlay
import com.moments.android.views.shared.MomentsSharedTransitionLayout
import com.moments.android.views.shared.rememberMomentsBoundsTransformSpec
import com.moments.android.views.shared.rememberMomentsContainerTransformEnter
import com.moments.android.views.shared.rememberMomentsContainerTransformExit

/**
 * Port de `UserProfileZoomNavigation.swift`.
 *
 * iOS: `matchedTransitionSource` + `.navigationTransition(.zoom)` + `Namespace.ID`.
 * Android: Compose `SharedTransitionLayout` / `sharedBounds` + [MomentsMotion]
 * (container transform M3 — no Dialog).
 *
 * Importante: el `sharedBounds` del destino va en una **capa morph hermana**,
 * no envolviendo [UserProfileView]. Si el perfil entero lleva sharedBounds,
 * el grid de momentos queda anidado dentro y el zoom a un momento crashea
 * ("layouts are not part of the same hierarchy").
 */
object UserProfileZoomNavigation {
    fun sourceID(userId: String): String = "user-profile-$userId"
}

/** El host de navegación usa este source id al abrir UserProfileView. */
fun userProfileZoomDestinationSourceID(userId: String): String = UserProfileZoomNavigation.sourceID(userId)

/**
 * ≡ `userProfileZoomSource` iOS (`matchedTransitionSource`).
 * Pareja con [userProfileZoomDestination] vía container transform M3.
 * Origen fuera de AnimatedVisibility → [sharedElementWithCallerManagedVisibility]
 * (API pública; `sharedBoundsWithCallerManagedVisibility` es internal en animation 1.11).
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
    val activeUserId = LocalActiveUserProfileZoomUserId.current
    val sourceVisible = visible && (activeUserId == null || activeUserId != userId)
    val state = with(sharedScope) {
        rememberSharedContentState(key = UserProfileZoomNavigation.sourceID(userId))
    }
    val boundsSpec = rememberMomentsBoundsTransformSpec()
    return with(sharedScope) {
        clipped.sharedElementWithCallerManagedVisibility(
            sharedContentState = state,
            visible = sourceVisible,
            boundsTransform = { _, _ -> boundsSpec },
            renderInOverlayDuringTransition = false,
        )
    }
}

/**
 * ≡ `userProfileZoomDestination` iOS (`.navigationTransition(.zoom(...))`).
 * Solo en la capa morph — no en [UserProfileView].
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
    val boundsSpec = rememberMomentsBoundsTransformSpec()
    val enter = rememberMomentsContainerTransformEnter()
    val exit = rememberMomentsContainerTransformExit()
    return with(sharedTransitionScope) {
        this@userProfileZoomDestination.sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = animatedVisibilityScope,
            enter = enter,
            exit = exit,
            boundsTransform = { _, _ -> boundsSpec },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}

/**
 * ≡ `userProfileNavigationDestination(item:namespace:)` —
 * overlay in-tree (no Dialog) para que el shared-element funcione.
 *
 * Si ya hay [LocalMomentsSharedTransitionScope] (p. ej. Feed con story zoom),
 * reutiliza ese layout; si no, crea uno.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UserProfileZoomNavigationHost(
    profileRoute: FeedProfileSheetRoute?,
    onProfileRouteChange: (FeedProfileSheetRoute?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (profileOpen: Boolean) -> Unit,
) {
    val existingScope = LocalMomentsSharedTransitionScope.current
    if (existingScope != null) {
        UserProfileZoomOverlayBody(
            sharedScope = existingScope,
            profileRoute = profileRoute,
            onProfileRouteChange = onProfileRouteChange,
            modifier = modifier,
            content = content,
        )
    } else {
        MomentsSharedTransitionLayout(modifier.fillMaxSize()) {
            UserProfileZoomOverlayBody(
                sharedScope = this,
                profileRoute = profileRoute,
                onProfileRouteChange = onProfileRouteChange,
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UserProfileZoomOverlayBody(
    sharedScope: SharedTransitionScope,
    profileRoute: FeedProfileSheetRoute?,
    onProfileRouteChange: (FeedProfileSheetRoute?) -> Unit,
    modifier: Modifier,
    content: @Composable (profileOpen: Boolean) -> Unit,
) {
    CompositionLocalProvider(LocalActiveUserProfileZoomUserId provides profileRoute?.userId) {
        Box(modifier.fillMaxSize()) {
            content(profileRoute != null)
            MomentsContainerTransformOverlay(visible = profileRoute != null) {
                val animatedVisibilityScope = this
                val route = profileRoute
                if (route != null) {
                    val isDark = isSystemInDarkTheme()
                    // Capa morph (sharedBounds) + perfil como hermano — el grid
                    // de momentos no queda anidado dentro de SharedBoundsNode.
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .userProfileZoomDestination(
                                    userId = route.userId,
                                    sharedTransitionScope = sharedScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                                .background(ProfileMomentZoomNavigation.canvasBackground(isDark)),
                        )
                        // Aislar el perfil del SharedTransitionScope del feed/perfil-zoom.
                        // El zoom grid→momento usa su propio SharedTransitionLayout interno;
                        // reutilizar el del host provoca hierarchy crash al hacer match.
                        CompositionLocalProvider(
                            LocalMomentsSharedTransitionScope provides null,
                            LocalMomentsSharedAnimatedVisibilityScope provides null,
                        ) {
                            UserProfileView(
                                userId = route.userId,
                                onDismiss = { onProfileRouteChange(null) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
