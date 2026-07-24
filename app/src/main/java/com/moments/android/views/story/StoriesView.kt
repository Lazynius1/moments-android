package com.moments.android.views.story

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.Story
import com.moments.android.views.story.storyviewer.StoryViewerScreen

/**
 * Port MVP de `StoriesView.swift` — orquestador ring + viewer.
 * Ads / chains / stickers / reply = stubs honestos (omitidos).
 */
@Composable
fun StoriesView(
    startAtUserId: String? = null,
    ringNavigationUserIds: List<String> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    explicitStories: List<Story>? = null,
    startAtIndex: Int = 0,
    highlightTitle: String? = null,
) {
    val viewModel = remember { StoryViewModel() }
    var userIndex by remember { mutableIntStateOf(0) }
    var storyIndex by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val deckGestureGate = remember { StoryDeckGestureGate() }

    LaunchedEffect(startAtUserId, ringNavigationUserIds, explicitStories) {
        // Modo lista explícita (destacados / cadenas), como el init `chainStories:` de iOS.
        if (explicitStories != null) {
            viewModel.loadExplicitStories(explicitStories)
        } else {
            viewModel.load(ringNavigationUserIds, startAtUserId)
        }
    }

    LaunchedEffect(viewModel.userIds, startAtUserId, viewModel.isLoading) {
        if (viewModel.isLoading || viewModel.userIds.isEmpty()) return@LaunchedEffect
        if (explicitStories != null) {
            userIndex = 0
            storyIndex = startAtIndex.coerceIn(0, (explicitStories.size - 1).coerceAtLeast(0))
            return@LaunchedEffect
        }
        val start = startAtUserId?.takeIf { it.isNotBlank() }
        val idx = if (start != null) {
            viewModel.userIds.indexOf(start).takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        userIndex = idx
        storyIndex = 0
    }

    val userIds = viewModel.userIds
    val currentUserId = userIds.getOrNull(userIndex)
    val userStories = currentUserId?.let { viewModel.storiesFor(it) }.orEmpty()
    val currentStory = userStories.getOrNull(storyIndex)

    val goNext: () -> Unit = {
        if (storyIndex < userStories.lastIndex) {
            storyIndex += 1
        } else if (userIndex < userIds.lastIndex) {
            userIndex += 1
            storyIndex = 0
        } else {
            onDismiss()
        }
    }
    val goPrevious: () -> Unit = {
        if (storyIndex > 0) {
            storyIndex -= 1
        } else if (userIndex > 0) {
            userIndex -= 1
            val prevStories = viewModel.storiesFor(userIds[userIndex])
            storyIndex = (prevStories.size - 1).coerceAtLeast(0)
        }
    }
    val goNextState = rememberUpdatedState(goNext)
    val goPreviousState = rememberUpdatedState(goPrevious)

    LaunchedEffect(currentStory?.id) {
        currentStory?.let { viewModel.markCurrentSeen(it) }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (deckGestureGate.suppressDeckNavigation) {
                            dragAccum = 0f
                            return@detectHorizontalDragGestures
                        }
                        when {
                            dragAccum < -80f -> goNextState.value()
                            dragAccum > 80f -> goPreviousState.value()
                        }
                        dragAccum = 0f
                    },
                    onHorizontalDrag = { _, dx ->
                        if (!deckGestureGate.suppressDeckNavigation) dragAccum += dx
                    },
                )
            },
    ) {
        when {
            viewModel.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            viewModel.errorMessage != null && userIds.isEmpty() -> {
                StoriesEmptyState(
                    message = viewModel.errorMessage.orEmpty(),
                    onClose = onDismiss,
                    showRetry = true,
                    onRetry = { viewModel.load(ringNavigationUserIds, startAtUserId) },
                )
            }
            userIds.isEmpty() || currentStory == null -> {
                StoriesEmptyState(
                    message = stringResource(R.string.stories_no_stories_available),
                    onClose = onDismiss,
                )
            }
            else -> {
                StoryViewerScreen(
                    story = currentStory,
                    segmentCount = userStories.size,
                    segmentIndex = storyIndex,
                    onNext = { goNextState.value() },
                    onPrevious = { goPreviousState.value() },
                    onDismiss = onDismiss,
                    gestureGate = deckGestureGate,
                    viewers = viewModel.storyViewers[currentStory.id.orEmpty()].orEmpty(),
                    reactions = viewModel.storyReactions[currentStory.id.orEmpty()].orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StoriesEmptyState(
    message: String,
    onClose: () -> Unit,
    showRetry: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(message, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        if (showRetry) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.explore_error_retry), color = Color.White)
            }
        }
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.common_close), color = Color.White)
        }
        Spacer(Modifier.weight(1f))
    }
}
