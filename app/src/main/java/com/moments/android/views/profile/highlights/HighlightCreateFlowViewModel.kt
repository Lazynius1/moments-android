package com.moments.android.views.profile.highlights

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.moments.android.R
import com.moments.android.models.HighlightedStory
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.createHighlight
import com.moments.android.services.firestore.deleteHighlight
import com.moments.android.services.firestore.fetchArchivedStoriesPaginated
import com.moments.android.services.firestore.fetchStoriesByIds
import com.moments.android.services.firestore.updateHighlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class HighlightFlowMode {
    data object Create : HighlightFlowMode()
    data class Edit(val highlight: HighlightedStory) : HighlightFlowMode()
}

enum class HighlightCreateStep { SELECT_STORIES, NAME_AND_COVER }

/** Port de `HighlightCreateFlowViewModel.swift`. */
class HighlightCreateFlowViewModel(
    val mode: HighlightFlowMode,
    private val appContext: Context? = null,
    private val firestore: FirestoreService = FirestoreService(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var step by mutableStateOf(HighlightCreateStep.SELECT_STORIES)
    var allStories by mutableStateOf<List<Story>>(emptyList())
    var selectedStories by mutableStateOf<List<Story>>(emptyList())
    var title by mutableStateOf(if (mode is HighlightFlowMode.Edit) mode.highlight.title else "")
    var coverStory by mutableStateOf<Story?>(null)
    var isLoading by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var showCoverPicker by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var hasMoreStories by mutableStateOf(true)

    private var lastDocument: DocumentSnapshot? = null
    private var didLoadInitialSelection = false

    val isEditMode: Boolean get() = mode is HighlightFlowMode.Edit
    val editingHighlight: HighlightedStory? get() = (mode as? HighlightFlowMode.Edit)?.highlight
    val canAdvance: Boolean get() = selectedStories.isNotEmpty()
    val canSave: Boolean get() = selectedStories.isNotEmpty()

    val resolvedTitle: String
        get() {
            val trimmed = title.trim()
            if (trimmed.isNotEmpty()) return trimmed
            return appContext?.getString(R.string.highlighted_stories_default_title) ?: "Highlight"
        }

    val saveActionTitleRes: Int
        get() = if (isEditMode) R.string.common_save else R.string.highlighted_stories_add

    val sortedArchiveStories: List<Story>
        get() = allStories.sortedByDescending { it.timestamp }

    fun clear() = scope.cancel()

    fun loadIfNeeded() {
        if (allStories.isNotEmpty() || isLoading) return
        loadArchivedStories(isInitial = true)
        loadInitialSelectionIfNeeded()
    }

    private fun loadInitialSelectionIfNeeded() {
        val edit = editingHighlight ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (didLoadInitialSelection) return
        didLoadInitialSelection = true
        scope.launch {
            runCatching { firestore.fetchStoriesByIds(userId, edit.storyIds) }
                .onSuccess { stories ->
                    selectedStories = stories
                    coverStory = stories.firstOrNull { it.mediaItem.url == edit.coverImageUrl }
                        ?: stories.firstOrNull()
                    mergeSelectedIntoArchive()
                }
        }
    }

    fun loadArchivedStories(isInitial: Boolean = false) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (isInitial) {
            isLoading = true
            lastDocument = null
            allStories = emptyList()
            hasMoreStories = true
            if (!isEditMode) {
                selectedStories = emptyList()
                coverStory = null
            }
        } else if (!hasMoreStories || isLoading) {
            return
        } else {
            isLoading = true
        }

        scope.launch {
            val result = runCatching {
                firestore.fetchArchivedStoriesPaginated(userId, 24, lastDocument)
            }
            isLoading = false
            result.onSuccess { page ->
                val fresh = page.stories.filter { story -> allStories.none { it.id == story.id } }
                allStories = allStories + fresh
                lastDocument = page.lastDocument
                hasMoreStories = page.stories.isNotEmpty() && page.stories.size == 24
                mergeSelectedIntoArchive()
            }.onFailure {
                if (isInitial) allStories = emptyList()
                errorMessage = appContext?.getString(R.string.highlighted_stories_load_failed)
                    ?: it.message
            }
        }
    }

    private fun mergeSelectedIntoArchive() {
        val missing = selectedStories.filter { story -> allStories.none { it.id == story.id } }
        if (missing.isEmpty()) return
        allStories = allStories + missing
    }

    fun toggleSelection(story: Story) {
        val existing = selectedStories.indexOfFirst { it.id == story.id }
        if (existing >= 0) {
            selectedStories = selectedStories.toMutableList().also { it.removeAt(existing) }
            if (coverStory?.id == story.id) coverStory = selectedStories.firstOrNull()
        } else {
            selectedStories = selectedStories + story
            if (coverStory == null) coverStory = story
        }
    }

    fun advanceToNameAndCover() {
        if (!canAdvance) return
        if (coverStory == null) coverStory = selectedStories.firstOrNull()
        step = HighlightCreateStep.NAME_AND_COVER
    }

    fun backToSelectStories() {
        step = HighlightCreateStep.SELECT_STORIES
    }

    fun save(onDone: (Throwable?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!canSave) return
        isSaving = true
        errorMessage = null
        val storyIds = selectedStories.mapNotNull { it.id }.filter { it.isNotEmpty() }
        val coverUrl = coverStory?.mediaItem?.url

        scope.launch {
            val result = runCatching {
                when (val current = mode) {
                    is HighlightFlowMode.Create ->
                        firestore.createHighlight(userId, resolvedTitle, storyIds, coverUrl)
                    is HighlightFlowMode.Edit -> {
                        val highlightId = current.highlight.id
                        if (highlightId.isNullOrEmpty()) {
                            throw IllegalStateException("invalid")
                        }
                        firestore.updateHighlight(
                            userId,
                            highlightId,
                            resolvedTitle,
                            storyIds,
                            coverUrl ?: current.highlight.coverImageUrl,
                        )
                    }
                }
            }
            isSaving = false
            result.onFailure { error ->
                errorMessage = if (error.message == "invalid") {
                    appContext?.getString(R.string.highlighted_stories_invalid_highlight)
                        ?: error.message
                } else {
                    error.message
                }
            }
            onDone(result.exceptionOrNull())
        }
    }

    fun deleteHighlight(onDone: (Throwable?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val id = editingHighlight?.id ?: return
        isSaving = true
        scope.launch {
            val result = runCatching { firestore.deleteHighlight(userId, id) }
            isSaving = false
            result.onFailure { errorMessage = it.message }
            onDone(result.exceptionOrNull())
        }
    }
}
