package com.moments.android.views.profile.core.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.models.HighlightedStory
import com.moments.android.models.Moment
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.MomentsCircularProgressIndicator
import com.moments.android.views.explore.ExploreMomentDetailView
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.maps.LocationMomentDetailView
import com.moments.android.views.profile.momentsview.ModernMomentDetailView
import com.moments.android.views.profile.momentsview.ModernSavedMomentsDetailView
import com.moments.android.views.shared.MomentsMotion
import com.moments.android.views.shared.MomentsZoomSourceCorner
import com.moments.android.views.shared.momentdetail.SingleMomentDetailView
import com.moments.android.views.shared.rememberMomentsContainerTransformEnter
import com.moments.android.views.shared.rememberMomentsContainerTransformExit

/** Port de `ProfileMomentZoomNavigation.swift`. */
enum class ProfileMomentZoomFeedKind {
    OWN_MOMENTS,
    TAGGED_MOMENTS,
    USER_PROFILE_MOMENTS,
    USER_PROFILE_TAGGED,
    SAVED_MOMENTS,
}

data class ProfileMomentZoomDestination(
    val zoomSourceID: String,
    val initialIndex: Int,
    val initialMomentId: String?,
    val feedKind: ProfileMomentZoomFeedKind,
    val restrictPlaybackToInitialIndex: Boolean = false,
    val openCommentsOnAppear: Boolean = false,
)

/** Destino genérico para zoom fuera del perfil (explore, actividad, mapa, etc.). */
data class MomentZoomDestination(
    val zoomSourceID: String,
    val initialIndex: Int,
    val initialMomentId: String?,
    val presentation: MomentZoomPresentationKind,
    val restrictPlaybackToInitialIndex: Boolean = false,
    val chromeTitle: String? = null,
)

sealed class MomentZoomPresentationKind {
    data object Carousel : MomentZoomPresentationKind()
    data object Saved : MomentZoomPresentationKind()
    data object Single : MomentZoomPresentationKind()
    data object Explorer : MomentZoomPresentationKind()
    data class Map(val locationName: String) : MomentZoomPresentationKind()
}

data class HighlightZoomDestination(val zoomSourceID: String, val highlightId: String)

object ProfileMomentZoomNavigation {
    const val profileSavedManagerZoomSourceID = "profile-saved-manager"

    fun sourceID(moment: Moment, gridIndex: Int): String = moment.id ?: "profile-grid-$gridIndex"
    fun sourceID(moment: Moment, index: Int, prefix: String): String = moment.id ?: "$prefix-$index"
    fun highlightSourceID(highlight: HighlightedStory, index: Int): String = highlight.id ?: "highlight-$index"
    fun canvasBackground(isDark: Boolean): Color = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
}

typealias MomentZoomNavigation = ProfileMomentZoomNavigation

/**
 * ≡ `profileGridNavigationChrome` / `momentZoomNavigationChrome` iOS.
 * Compose no pinta UINavigationController; el chrome nativo no aplica.
 */
fun Modifier.profileGridNavigationChrome(): Modifier = this

fun Modifier.momentZoomNavigationChrome(): Modifier = profileGridNavigationChrome()

/**
 * ≡ `profileNavigationSurface` / `momentZoomNavigationSurface` iOS —
 * canvas AdaptiveColors; el fix UIKit de NavigationController no aplica en Compose.
 */
fun Modifier.profileNavigationSurface(isDark: Boolean): Modifier =
    this.background(ProfileMomentZoomNavigation.canvasBackground(isDark))

fun Modifier.momentZoomNavigationSurface(isDark: Boolean): Modifier = profileNavigationSurface(isDark)

/**
 * Origen del container transform (grid → detail).
 *
 * Compose [SharedTransitionScope] / [sharedElementWithCallerManagedVisibility] crashea
 * aquí con `layouts are not part of the same hierarchy` (SharedBoundsNode,
 * animation 1.11.4) al abrir el detalle — el match eleva coords fuera del root del STL
 * (LazyGrid + overlays + AndroidView en el destino). El morph visual lo hace
 * [MomentZoomContainerTransformScaffold] con motion M3 (scale+fade), no shared-element.
 */
@Composable
fun Modifier.profileMomentZoomSource(
    sourceID: String?,
    cornerRadius: Dp = MomentsZoomSourceCorner,
    visible: Boolean = true,
): Modifier {
    @Suppress("UNUSED_PARAMETER")
    val unusedVisible = visible
    @Suppress("UNUSED_PARAMETER")
    val unusedId = sourceID
    if (sourceID.isNullOrBlank()) return this
    return this.clip(RoundedCornerShape(cornerRadius))
}

/** ≡ `HighlightZoomSourceModifier` iOS. */
@Composable
fun Modifier.highlightZoomSource(
    sourceID: String?,
    size: Dp = 64.dp,
    visible: Boolean = true,
): Modifier = profileMomentZoomSource(sourceID = sourceID, cornerRadius = size / 2, visible = visible)

/**
 * Destino del zoom (pareja de [profileMomentZoomSource]).
 * Sin shared-element Compose — ver nota en [profileMomentZoomSource].
 * El morph lo aplica [MomentZoomContainerTransformScaffold].
 */
@Composable
fun Modifier.momentZoomDestination(zoomSourceID: String): Modifier {
    @Suppress("UNUSED_PARAMETER")
    val unused = zoomSourceID
    return this
}

/**
 * Presentación del detalle con container-transform **aproximado** M3
 * (fade + scale espacial) sin SharedTransitionScope — estable en LazyGrid/perfil.
 */
@Composable
private fun MomentZoomContainerTransformScaffold(
    zoomSourceID: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedId = zoomSourceID
    val isDark = isSystemInDarkTheme()
    val enter = rememberMomentsContainerTransformEnter() +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = MomentsMotion.defaultSpatialSpec(),
        )
    val exit = rememberMomentsContainerTransformExit() +
        scaleOut(
            targetScale = 0.92f,
            animationSpec = MomentsMotion.defaultSpatialSpec(),
        )
    AnimatedVisibility(
        visible = true,
        modifier = modifier.fillMaxSize(),
        enter = enter,
        exit = exit,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ProfileMomentZoomNavigation.canvasBackground(isDark)),
        ) {
            content()
        }
    }
}

@Composable
fun ProfileMomentZoomDetailDestination(
    destination: ProfileMomentZoomDestination,
    moments: List<Moment>,
    onDismiss: () -> Unit,
    onRemoveSavedMoment: ((Moment) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MomentZoomContainerTransformScaffold(
        zoomSourceID = destination.zoomSourceID,
        modifier = modifier,
    ) {
        when (destination.feedKind) {
            ProfileMomentZoomFeedKind.SAVED_MOMENTS -> {
                ModernSavedMomentsDetailView(
                    moments = moments,
                    initialIndex = destination.initialIndex,
                    onDismiss = onDismiss,
                    onRemoveMoment = onRemoveSavedMoment,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                val selected = MomentZoomOpener.resolvedProfileMoment(destination, moments)
                if (selected == null) {
                    MomentZoomSingleFallbackView(Modifier.fillMaxSize())
                } else {
                    ModernMomentDetailView(
                        moments = moments.ifEmpty { listOf(selected) },
                        onDismiss = onDismiss,
                        initialIndex = destination.initialIndex,
                        initialMomentId = destination.initialMomentId ?: selected.id,
                        restrictPlaybackToInitialIndex = destination.restrictPlaybackToInitialIndex,
                        openCommentsOnAppear = destination.openCommentsOnAppear,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun MomentZoomDetailDestination(
    destination: MomentZoomDestination,
    moments: List<Moment>,
    onDismiss: () -> Unit,
    onRemoveSavedMoment: ((Moment) -> Unit)? = null,
    onMapPresentedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    MomentZoomContainerTransformScaffold(
        zoomSourceID = destination.zoomSourceID,
        modifier = modifier,
    ) {
        val contentMod = Modifier.fillMaxSize()
        when (val presentation = destination.presentation) {
            MomentZoomPresentationKind.Carousel -> {
                ModernMomentDetailView(
                    moments = moments,
                    onDismiss = {
                        onMapPresentedChanged(false)
                        onDismiss()
                    },
                    initialIndex = destination.initialIndex,
                    initialMomentId = destination.initialMomentId,
                    restrictPlaybackToInitialIndex = destination.restrictPlaybackToInitialIndex,
                    modifier = contentMod,
                )
            }
            MomentZoomPresentationKind.Saved -> {
                ModernSavedMomentsDetailView(
                    moments = moments,
                    initialIndex = destination.initialIndex,
                    onDismiss = onDismiss,
                    onRemoveMoment = onRemoveSavedMoment,
                    modifier = contentMod,
                )
            }
            MomentZoomPresentationKind.Single -> {
                val selected = MomentZoomOpener.resolvedSingleMoment(moments, destination)
                if (selected == null) {
                    MomentZoomSingleFallbackView(contentMod)
                } else {
                    SingleMomentDetailView(
                        moment = selected.toExploreFeedMoment(),
                        onDismiss = onDismiss,
                        chromeTitle = destination.chromeTitle,
                        modifier = contentMod,
                    )
                }
            }
            MomentZoomPresentationKind.Explorer -> {
                val selected = MomentZoomOpener.resolvedSingleMoment(moments, destination)
                if (selected == null) {
                    MomentZoomSingleFallbackView(contentMod)
                } else {
                    ExploreMomentDetailView(
                        moments = moments.ifEmpty { listOf(selected) },
                        initialIndex = destination.initialIndex,
                        initialMomentId = destination.initialMomentId ?: selected.id,
                        onNavigateBack = onDismiss,
                        modifier = contentMod,
                    )
                }
            }
            is MomentZoomPresentationKind.Map -> LocationMomentDetailView(
                moments = moments,
                initialIndex = destination.initialIndex,
                locationName = presentation.locationName,
                onDismiss = {
                    onMapPresentedChanged(false)
                    onDismiss()
                },
                modifier = contentMod,
            )
        }
    }
}

/** ≡ `HighlightZoomDetailDestination` — el viewer se monta desde highlights; aquí route + zoom. */
@Composable
fun HighlightZoomDetailDestination(
    destination: HighlightZoomDestination,
    highlight: HighlightedStory,
    onDismiss: () -> Unit,
    content: @Composable (HighlightedStory, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    MomentZoomContainerTransformScaffold(
        zoomSourceID = destination.zoomSourceID,
        modifier = modifier,
    ) {
        content(highlight, onDismiss)
    }
}

@Composable
private fun MomentZoomSingleFallbackView(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier
            .fillMaxSize()
            .background(ProfileMomentZoomNavigation.canvasBackground(isDark)),
        contentAlignment = Alignment.Center,
    ) {
        MomentsCircularProgressIndicator()
    }
}

/** Helpers de resolución y apertura; el pool se lee vivo en el destino, igual que iOS. */
object MomentZoomOpener {
    fun resolvedMoments(destination: MomentZoomDestination, pool: List<Moment>): List<Moment> = when (destination.presentation) {
        MomentZoomPresentationKind.Single -> resolvedSingleMoment(pool, destination)?.let(::listOf).orEmpty()
        MomentZoomPresentationKind.Carousel,
        MomentZoomPresentationKind.Saved,
        MomentZoomPresentationKind.Explorer,
        is MomentZoomPresentationKind.Map -> pool
    }

    fun resolvedProfileMoment(destination: ProfileMomentZoomDestination, pool: List<Moment>): Moment? =
        destination.initialMomentId?.let { id -> pool.firstOrNull { it.id == id } }
            ?: pool.getOrNull(destination.initialIndex)
            ?: pool.firstOrNull()

    fun resolvedSingleMoment(pool: List<Moment>, destination: MomentZoomDestination): Moment? =
        destination.initialMomentId?.let { id -> pool.firstOrNull { it.id == id } }
            ?: pool.getOrNull(destination.initialIndex)
            ?: pool.firstOrNull()

    fun open(
        moment: Moment,
        moments: List<Moment>,
        initialIndex: Int,
        presentation: MomentZoomPresentationKind,
        setDestination: (MomentZoomDestination) -> Unit,
        zoomIDPrefix: String? = null,
        chromeTitle: String? = null,
    ) {
        val prefix = zoomIDPrefix ?: presentationPrefix(presentation)
        setDestination(
            MomentZoomDestination(
                zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, initialIndex, prefix),
                initialIndex = initialIndex,
                initialMomentId = moment.id,
                presentation = presentation,
                chromeTitle = chromeTitle,
            ),
        )
        HapticManager.shared.lightImpact()
    }

    private fun presentationPrefix(presentation: MomentZoomPresentationKind): String = when (presentation) {
        MomentZoomPresentationKind.Carousel -> "carousel"
        MomentZoomPresentationKind.Saved -> "saved"
        MomentZoomPresentationKind.Single -> "single"
        MomentZoomPresentationKind.Explorer -> "explore"
        is MomentZoomPresentationKind.Map -> "map"
    }
}
