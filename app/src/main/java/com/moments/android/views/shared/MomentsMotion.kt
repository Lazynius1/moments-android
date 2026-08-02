package com.moments.android.views.shared

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de motion Material 3 para Moments.
 *
 * Referencia: [m3.material.io — Transition patterns](https://m3.material.io/styles/motion/transitions/transition-patterns)
 * (container transform / shared axis / fade through).
 *
 * Nota: `MaterialTheme.motionScheme` aún es API internal en el BOM actual —
 * usamos springs M3 equivalentes (sin bounce, stiffness medium-low spatial).
 */
object MomentsMotion {
    /** Duración típica container-transform (M3 emphasized spatial). */
    val ContainerTransformDurationMs = 500

    /**
     * Spec espacial M3 (container transform / sharedBounds).
     * ≡ defaultSpatial: damping no-bouncy + stiffness medium-low.
     */
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Effects (fade/opacity) — overlays / enter-exit del host. */
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/**
 * Spec espacial para `sharedBounds` / container transform (zoom grid → detail).
 * No usar matchedGeometry iOS bounce.
 */
@Composable
fun rememberMomentsBoundsTransformSpec(): FiniteAnimationSpec<Rect> {
    return remember { MomentsMotion.defaultSpatialSpec() }
}

/** Effects (fade/opacity) — forward/backward within sheets o overlays. */
@Composable
fun <T> rememberMomentsEffectsSpec(): FiniteAnimationSpec<T> {
    return remember { MomentsMotion.defaultEffectsSpec() }
}

/**
 * Enter del overlay host (contenido no compartido).
 * El morph espacial lo hace `sharedBounds`; aquí solo fade M3 (no scale/bounce iOS).
 */
@Composable
fun rememberMomentsContainerTransformEnter(): EnterTransition {
    val effects = rememberMomentsEffectsSpec<Float>()
    return remember(effects) { fadeIn(animationSpec = effects) }
}

/** Exit del overlay host — pareja de [rememberMomentsContainerTransformEnter]. */
@Composable
fun rememberMomentsContainerTransformExit(): ExitTransition {
    val effects = rememberMomentsEffectsSpec<Float>()
    return remember(effects) { fadeOut(animationSpec = effects) }
}

/** Esquinas del origen del zoom en grid (M3 shape, no iOS continuous corner). */
val MomentsZoomSourceCorner: Dp = 4.dp

/** ≡ iOS `story-ring-\(userId)` en FeedHeader / FeedPresentationModifier. */
object StoryZoomNavigation {
    fun sourceID(userId: String): String = "story-ring-$userId"
}
