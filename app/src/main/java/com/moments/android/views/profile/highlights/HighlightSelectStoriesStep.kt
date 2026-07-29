package com.moments.android.views.profile.highlights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.shared.AppErrorBanner

/** Port de `HighlightSelectStoriesStep.swift`. */
@Composable
fun HighlightSelectStoriesStep(viewModel: HighlightCreateFlowViewModel) {
    Column(Modifier.fillMaxSize()) {
        viewModel.errorMessage?.let { errorMessage ->
            AppErrorBanner(
                message = errorMessage,
                onRetry = {
                    viewModel.errorMessage = null
                    viewModel.loadArchivedStories(isInitial = true)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        HighlightStoryGrid(
            stories = viewModel.sortedArchiveStories,
            selectedIds = viewModel.selectedStories.mapNotNull { it.id }.toSet(),
            isLoading = viewModel.isLoading,
            isEmpty = viewModel.allStories.isEmpty() && !viewModel.isLoading,
            emptyMessage = R.string.highlighted_stories_archive_empty,
            onToggle = viewModel::toggleSelection,
            onStoryAppear = { story ->
                if (story.id == viewModel.sortedArchiveStories.lastOrNull()?.id) {
                    viewModel.loadArchivedStories(isInitial = false)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
