package com.moments.android.views.profile.highlights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moments.android.models.HighlightedStory

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
    var sheet by mutableStateOf<HighlightSheet?>(null)
        private set
    var viewerHighlight by mutableStateOf<HighlightedStory?>(null)
        private set

    val isSheetPresented: Boolean get() = sheet != null
    val isViewerPresented: Boolean get() = viewerHighlight != null

    fun presentCreate() {
        viewerHighlight = null
        sheet = HighlightSheet.Create
    }

    fun presentEdit(highlight: HighlightedStory) {
        viewerHighlight = null
        sheet = HighlightSheet.Edit(highlight)
    }

    fun presentViewer(highlight: HighlightedStory) {
        // iOS cierra lo presentado antes de abrir el visor; en Compose basta con reemplazar.
        sheet = null
        viewerHighlight = highlight
    }

    fun dismissSheet() {
        sheet = null
    }

    fun dismissViewer() {
        viewerHighlight = null
    }

    fun closeAll() {
        sheet = null
        viewerHighlight = null
    }
}
