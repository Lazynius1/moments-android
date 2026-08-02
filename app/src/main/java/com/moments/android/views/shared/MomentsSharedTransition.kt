package com.moments.android.views.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Host de shared-element Compose para **container transform** M3
 * ([transition patterns](https://m3.material.io/styles/motion/transitions/transition-patterns)).
 *
 * iOS: `matchedTransitionSource` + `.navigationTransition(.zoom(sourceID:in:))`.
 * Android: [SharedTransitionLayout] + `sharedBounds` + [MomentsMotion].
 *
 * Specs de bounds → [rememberMomentsBoundsTransformSpec].
 * No usar springs/bounce de matchedGeometry iOS. No montar destinos en `Dialog`
 * (rompe el shared scope).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalMomentsSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalMomentsSharedAnimatedVisibilityScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Source id del momento cuyo detalle está abierto (container transform activo).
 * Las celdas origen con caller-managed ponen `visible=false` mientras coincide.
 */
val LocalActiveMomentZoomSourceId = staticCompositionLocalOf<String?> { null }

/**
 * User id del perfil abierto vía zoom. Las avatares origen ocultan mientras
 * el destino hace match.
 */
val LocalActiveUserProfileZoomUserId = staticCompositionLocalOf<String?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MomentsSharedTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalMomentsSharedTransitionScope provides this) {
            content()
        }
    }
}

@Composable
fun ProvideMomentsSharedAnimatedVisibilityScope(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMomentsSharedAnimatedVisibilityScope provides scope, content = content)
}

/**
 * Overlay in-tree ≡ iOS `navigationDestination` + `.navigationTransition(.zoom)`.
 * Debe vivir dentro de [MomentsSharedTransitionLayout] (o un host que lo provea).
 */
@Composable
fun MomentsContainerTransformOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val enter = rememberMomentsContainerTransformEnter()
    val exit = rememberMomentsContainerTransformExit()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = enter,
        exit = exit,
    ) {
        val animatedScope = this
        ProvideMomentsSharedAnimatedVisibilityScope(animatedScope) {
            Box(Modifier.fillMaxSize()) {
                animatedScope.content()
            }
        }
    }
}
