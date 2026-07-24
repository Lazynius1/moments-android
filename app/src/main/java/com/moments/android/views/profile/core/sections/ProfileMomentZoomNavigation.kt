package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.models.HighlightedStory
import com.moments.android.models.Moment
import com.moments.android.utilities.HapticManager
import com.moments.android.views.explore.ExploreMomentDetailView
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.maps.LocationMomentDetailView
import com.moments.android.views.profile.momentsview.ModernMomentDetailView
import com.moments.android.views.shared.momentdetail.SingleMomentDetailView

/** Port de `ProfileMomentZoomNavigation.swift`. */
enum class ProfileMomentZoomFeedKind { OWN_MOMENTS, TAGGED_MOMENTS, USER_PROFILE_MOMENTS, USER_PROFILE_TAGGED, SAVED_MOMENTS }

data class ProfileMomentZoomDestination(
    val zoomSourceID: String,
    val initialIndex: Int,
    val initialMomentId: String?,
    val feedKind: ProfileMomentZoomFeedKind,
    val restrictPlaybackToInitialIndex: Boolean = false,
    val openCommentsOnAppear: Boolean = false,
)

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
 * Android no dispone del `matchedTransitionSource` de iOS 18 en Compose estable.
 * Conserva el recorte de la fuente y el id de destino para el coordinador de transición.
 */
fun Modifier.profileMomentZoomSource(sourceID: String?, cornerRadius: androidx.compose.ui.unit.Dp = 4.dp): Modifier =
    if (sourceID == null) this else clip(RoundedCornerShape(cornerRadius))

fun Modifier.highlightZoomSource(sourceID: String?, size: androidx.compose.ui.unit.Dp = 64.dp): Modifier =
    if (sourceID == null) this else clip(RoundedCornerShape(size / 2))

@Composable
fun ProfileMomentZoomDetailDestination(
    destination: ProfileMomentZoomDestination,
    moments: List<Moment>,
    onDismiss: () -> Unit,
    onRemoveSavedMoment: ((Moment) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val selected = MomentZoomOpener.resolvedProfileMoment(destination, moments)
    if (selected == null) {
        MomentZoomSingleFallbackView(modifier)
        return
    }
    // El pool, el índice y el cierre viajan vivos en el route para no congelar un snapshot.
    ModernMomentDetailView(
        moments = moments.ifEmpty { listOf(selected) },
        onDismiss = onDismiss,
        initialIndex = destination.initialIndex,
        initialMomentId = destination.initialMomentId ?: selected.id,
        modifier = modifier.fillMaxSize(),
    )
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
    when (val presentation = destination.presentation) {
        // Carrusel de la rejilla: pager sobre toda la lista, como `ModernMomentDetailView` en iOS.
        MomentZoomPresentationKind.Carousel,
        MomentZoomPresentationKind.Saved -> {
            val selected = MomentZoomOpener.resolvedSingleMoment(moments, destination)
            if (selected == null) {
                MomentZoomSingleFallbackView(modifier)
            } else {
                ModernMomentDetailView(
                    moments = moments.ifEmpty { listOf(selected) },
                    onDismiss = onDismiss,
                    initialIndex = destination.initialIndex,
                    initialMomentId = selected.id,
                    modifier = modifier.fillMaxSize(),
                )
            }
        }
        MomentZoomPresentationKind.Single -> {
            val selected = MomentZoomOpener.resolvedSingleMoment(moments, destination)
            if (selected == null) MomentZoomSingleFallbackView(modifier) else SingleMomentDetailView(selected.toExploreFeedMoment(), onDismiss, chromeTitle = destination.chromeTitle, modifier = modifier.fillMaxSize())
        }
        MomentZoomPresentationKind.Explorer -> {
            val selected = MomentZoomOpener.resolvedSingleMoment(moments, destination)
            if (selected == null) {
                MomentZoomSingleFallbackView(modifier)
            } else {
                ExploreMomentDetailView(
                    moments = moments.ifEmpty { listOf(selected) },
                    initialIndex = moments.indexOfFirst { it.id == selected.id }.coerceAtLeast(0),
                    onNavigateBack = onDismiss,
                )
            }
        }
        is MomentZoomPresentationKind.Map -> LocationMomentDetailView(
            moments = moments,
            initialIndex = destination.initialIndex,
            locationName = presentation.locationName,
            onDismiss = { onMapPresentedChanged(false); onDismiss() },
            modifier = modifier.fillMaxSize(),
        )
    }
}

/** `HighlightViewer` se monta desde el archivo de highlights; este destino conserva su route y dismiss. */
@Composable
fun HighlightZoomDetailDestination(
    destination: HighlightZoomDestination,
    highlight: HighlightedStory,
    onDismiss: () -> Unit,
    content: @Composable (HighlightedStory, () -> Unit) -> Unit,
) {
    content(highlight, onDismiss)
}

@Composable
private fun MomentZoomSingleFallbackView(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
