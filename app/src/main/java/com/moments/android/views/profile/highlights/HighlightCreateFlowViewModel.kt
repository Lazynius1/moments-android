package com.moments.android.views.profile.highlights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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

sealed class HighlightFlowMode { data object Create : HighlightFlowMode(); data class Edit(val highlight: HighlightedStory) : HighlightFlowMode() }
enum class HighlightCreateStep { SELECT_STORIES, NAME_AND_COVER }
/** Port de `HighlightCreateFlowViewModel.swift`. */
class HighlightCreateFlowViewModel(val mode: HighlightFlowMode, private val firestore: FirestoreService = FirestoreService()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var step by mutableStateOf(HighlightCreateStep.SELECT_STORIES); var allStories by mutableStateOf<List<Story>>(emptyList()); var selectedStories by mutableStateOf<List<Story>>(emptyList()); var title by mutableStateOf(if (mode is HighlightFlowMode.Edit) mode.highlight.title else ""); var coverStory by mutableStateOf<Story?>(null); var isLoading by mutableStateOf(false); var isSaving by mutableStateOf(false); var showCoverPicker by mutableStateOf(false); var errorMessage by mutableStateOf<String?>(null); var hasMoreStories by mutableStateOf(true)
    private var lastDocument: DocumentSnapshot? = null; private var initialSelectionLoaded = false
    val isEditMode get() = mode is HighlightFlowMode.Edit; val editingHighlight get() = (mode as? HighlightFlowMode.Edit)?.highlight; val canAdvance get() = selectedStories.isNotEmpty(); val canSave get() = selectedStories.isNotEmpty(); val resolvedTitle get() = title.trim().ifEmpty { "Highlights" }; val sortedArchiveStories get() = allStories.sortedByDescending { it.timestamp }
    fun clear() = scope.cancel()
    fun loadIfNeeded() { if (allStories.isEmpty() && !isLoading) { loadArchivedStories(true); loadInitialSelectionIfNeeded() } }
    private fun loadInitialSelectionIfNeeded() { val edit = editingHighlight ?: return; val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return; if (initialSelectionLoaded) return; initialSelectionLoaded = true; scope.launch { runCatching { firestore.fetchStoriesByIds(userId, edit.storyIds) }.onSuccess { stories -> selectedStories = stories; coverStory = stories.firstOrNull { it.mediaItem.url == edit.coverImageUrl } ?: stories.firstOrNull(); mergeSelectedIntoArchive() } } }
    fun loadArchivedStories(initial: Boolean = false) { val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return; if (!initial && (!hasMoreStories || isLoading)) return; if (initial) { allStories = emptyList(); lastDocument = null; hasMoreStories = true; if (!isEditMode) { selectedStories = emptyList(); coverStory = null } }; isLoading = true; scope.launch { runCatching { firestore.fetchArchivedStoriesPaginated(userId, 24, lastDocument) }.onSuccess { page -> allStories = allStories + page.stories.filter { fresh -> allStories.none { it.id == fresh.id } }; lastDocument = page.lastDocument; hasMoreStories = page.stories.size == 24; mergeSelectedIntoArchive() }.onFailure { errorMessage = it.message }.also { isLoading = false } } }
    private fun mergeSelectedIntoArchive() { allStories = allStories + selectedStories.filter { current -> allStories.none { it.id == current.id } } }
    fun toggleSelection(story: Story) { selectedStories = if (selectedStories.any { it.id == story.id }) selectedStories.filterNot { it.id == story.id } else selectedStories + story; if (coverStory?.id == story.id) coverStory = selectedStories.firstOrNull(); if (coverStory == null) coverStory = selectedStories.firstOrNull() }
    fun advanceToNameAndCover() { if (canAdvance) { coverStory = coverStory ?: selectedStories.first(); step = HighlightCreateStep.NAME_AND_COVER } }
    fun backToSelectStories() { step = HighlightCreateStep.SELECT_STORIES }
    fun save(onDone: (Throwable?) -> Unit) { val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return; if (!canSave) return; isSaving = true; scope.launch { val result = runCatching { val storyIds = selectedStories.mapNotNull(Story::id); val cover = coverStory?.mediaItem?.url; editingHighlight?.let { firestore.updateHighlight(userId, it.id.orEmpty(), resolvedTitle, storyIds, cover ?: it.coverImageUrl) } ?: firestore.createHighlight(userId, resolvedTitle, storyIds, cover) }; isSaving = false; result.onFailure { errorMessage = it.message }; onDone(result.exceptionOrNull()) } }
    fun deleteHighlight(onDone: (Throwable?) -> Unit) { val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return; val id = editingHighlight?.id ?: return; isSaving = true; scope.launch { val result = runCatching { firestore.deleteHighlight(userId, id) }; isSaving = false; result.onFailure { errorMessage = it.message }; onDone(result.exceptionOrNull()) } }
}
