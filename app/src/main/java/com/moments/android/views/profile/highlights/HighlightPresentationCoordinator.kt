package com.moments.android.views.profile.highlights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moments.android.models.HighlightedStory
import com.moments.android.views.feed.maps.MapSheetPresentationDelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de `HighlightSheet`. */
sealed interface HighlightSheet {
    data object Create : HighlightSheet
    data class Edit(val highlight: HighlightedStory) : HighlightSheet

    val id: String
        get() = when (this) {
            is Create -> "create"
            is Edit -> "edit-${highlight.id.orEmpty()}"
        }
}

/** Port de `HighlightPresentationCoordinator`: una sola presentación viva a la vez. */
class HighlightPresentationCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var presentJob: Job? = null

    var sheet by mutableStateOf<HighlightSheet?>(null)
        private set
    var viewerHighlight by mutableStateOf<HighlightedStory?>(null)
        private set

    val isSheetPresented: Boolean get() = sheet != null
    val isViewerPresented: Boolean get() = viewerHighlight != null

    fun presentCreate() {
        presentJob?.cancel()
        viewerHighlight = null
        sheet = HighlightSheet.Create
    }

    fun presentEdit(highlight: HighlightedStory) {
        presentJob?.cancel()
        viewerHighlight = null
        sheet = HighlightSheet.Edit(highlight)
    }

    fun presentViewer(highlight: HighlightedStory) {
        presentJob?.cancel()
        if (sheet != null || viewerHighlight != null) {
            sheet = null
            viewerHighlight = null
            presentJob = scope.launch {
                delay(MapSheetPresentationDelay.DISMISS_BEFORE_NEXT_PRESENTATION_MS)
                viewerHighlight = highlight
            }
        } else {
            viewerHighlight = highlight
        }
    }

    fun dismissSheet() {
        sheet = null
    }

    fun dismissViewer() {
        viewerHighlight = null
    }

    fun closeAll() {
        presentJob?.cancel()
        sheet = null
        viewerHighlight = null
    }
}
