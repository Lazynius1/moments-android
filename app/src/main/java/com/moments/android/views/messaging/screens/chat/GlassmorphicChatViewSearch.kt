package com.moments.android.views.messaging.screens.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de `GlassmorphicChatView+Search.swift`. */
@Stable
class GlassmorphicChatSearchController(
    private val viewModel: EnhancedChatViewModel,
    private val scrollController: GlassmorphicChatScrollController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var restoreLayoutJob: Job? = null

    var isSearchVisible by mutableStateOf(false)
        private set
    var isSearchFieldFocused by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchMatchIds by mutableStateOf(emptyList<String>())
        private set
    var currentSearchMatchIndex by mutableIntStateOf(0)
        private set
    var pendingSearchTargetId by mutableStateOf<String?>(null)
        private set

    val currentSearchMatchId: String?
        get() = searchMatchIds.getOrNull(currentSearchMatchIndex).takeIf { isSearchVisible }
    val activeSearchHighlightTerm: String get() = if (isSearchVisible) searchQuery.trim() else ""

    fun toggleChatSearch() {
        isSearchVisible = !isSearchVisible
        scrollController.clearSearchHighlight()
        searchQuery = ""
        viewModel.clearSearch()
        searchMatchIds = emptyList()
        currentSearchMatchIndex = 0
        pendingSearchTargetId = null
        isSearchFieldFocused = isSearchVisible
    }

    fun updateSearchQuery(value: String) {
        searchQuery = value
        viewModel.performSearch(value)
    }

    fun restoreLayoutAfterClosingSearch() {
        if (scrollController.hasCompletedInitialScroll && scrollController.isPinnedToBottom) {
            scrollController.scheduleListBottomSnap(ListBottomSnapReason.COMPOSER_RESIZED)
        }
        restoreLayoutJob?.cancel()
        restoreLayoutJob = scope.launch {
            delay(80L)
            if (!isSearchVisible && scrollController.hasCompletedInitialScroll && scrollController.isPinnedToBottom) {
                scrollController.scheduleListBottomSnap(ListBottomSnapReason.COMPOSER_RESIZED)
            }
        }
    }

    fun syncSearchMatchesFromViewModel() {
        if (searchQuery.trim().isEmpty()) {
            searchMatchIds = emptyList()
            currentSearchMatchIndex = 0
            return
        }
        searchMatchIds = viewModel.searchResults.value
        if (searchMatchIds.isEmpty()) currentSearchMatchIndex = 0
        else if (currentSearchMatchIndex >= searchMatchIds.size) currentSearchMatchIndex = searchMatchIds.lastIndex
    }

    fun scrollToCurrentSearchMatch() {
        currentSearchMatchId?.let(scrollController::scheduleSearchHighlightScroll)
    }

    fun moveSearchSelection(step: Int) {
        if (step >= 0 || searchMatchIds.isEmpty()) return
        currentSearchMatchIndex = (currentSearchMatchIndex + step + searchMatchIds.size) % searchMatchIds.size
        scrollToCurrentSearchMatch()
    }

    fun advanceSearchSelection() {
        val canScrollToBottom = !scrollController.isPinnedToBottom || scrollController.distanceFromBottom() > 16
        if (searchMatchIds.isEmpty() || currentSearchMatchIndex >= searchMatchIds.lastIndex) {
            if (canScrollToBottom) scrollController.scrollToBottomFromUserAction(animated = true)
            return
        }
        currentSearchMatchIndex += 1
        scrollToCurrentSearchMatch()
    }

    fun consumePendingSearchTarget() {
        pendingSearchTargetId?.let { target ->
            pendingSearchTargetId = null
            scrollController.scheduleSearchHighlightScroll(target)
        }
    }

    fun setPendingSearchTarget(messageId: String?) { pendingSearchTargetId = messageId }
    fun dispose() { restoreLayoutJob?.cancel() }
}
