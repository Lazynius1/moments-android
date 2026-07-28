package com.moments.android.views.shared

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Scope de shared-element Compose (≡ Namespace.ID + matchedTransitionSource / zoom de iOS).
 * Los hosts envuelven con [MomentsSharedTransitionLayout]; los modifiers leen estos locals.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalMomentsSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalMomentsSharedAnimatedVisibilityScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

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
