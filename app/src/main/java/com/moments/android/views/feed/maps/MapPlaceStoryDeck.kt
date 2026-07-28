package com.moments.android.views.feed.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchStoriesByIds
import com.moments.android.views.components.StoryViewerSkeletonView
import com.moments.android.views.story.storyviewer.StoryViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Port de `MapPlaceStoryDeckView` / `MapPlaceStoryFetcher` en `MapPlaceStoryDeck.swift`.
 */
@Composable
fun MapPlaceStoryDeck(
    previews: List<MapStoryPreview>,
    initialPreviewId: String? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(previews, initialPreviewId) {
        isLoading = true
        val fetched = MapPlaceStoryFetcher.fetchStories(previews)
        stories = fetched
        isLoading = false
        currentIndex = when {
            initialPreviewId != null ->
                fetched.indexOfFirst { it.id == initialPreviewId }.takeIf { it >= 0 } ?: 0
            else -> 0
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            isLoading -> StoryViewerSkeletonView(segmentCount = maxOf(previews.size, 1))
            stories.getOrNull(currentIndex) != null -> {
                val story = stories[currentIndex]
                StoryViewerScreen(
                    story = story,
                    segmentCount = stories.size,
                    segmentIndex = currentIndex,
                    onNext = {
                        if (currentIndex < stories.lastIndex) currentIndex += 1
                        else onClose()
                    },
                    onPrevious = {
                        if (currentIndex > 0) currentIndex -= 1
                    },
                    onDismiss = onClose,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                LaunchedEffect(Unit) { onClose() }
            }
        }
    }
}

/** Alias iOS `MapPlaceStoryDeckView`. */
@Composable
fun MapPlaceStoryDeckView(
    previews: List<MapStoryPreview>,
    initialPreviewId: String? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) = MapPlaceStoryDeck(previews, initialPreviewId, onClose, modifier)

/** ≡ iOS `MapPlaceStoryFetcher`. */
object MapPlaceStoryFetcher {
    private val firestore = FirestoreService()

    suspend fun fetchStories(previews: List<MapStoryPreview>): List<Story> {
        if (previews.isEmpty()) return emptyList()
        val sorted = previews.sortedByDescending { it.timestamp.time }
        val grouped = sorted.groupBy { it.authorId }
        val fetchedByKey = withContext(Dispatchers.IO) {
            coroutineScope {
                grouped.map { (authorId, authorPreviews) ->
                    async {
                        val ids = authorPreviews.map { it.id }
                        runCatching { firestore.fetchStoriesByIds(authorId, ids) }
                            .getOrDefault(emptyList())
                            .mapNotNull { story ->
                                val id = story.id ?: return@mapNotNull null
                                "$authorId:$id" to story
                            }
                    }
                }.awaitAll().flatten().toMap()
            }
        }
        return sorted.mapNotNull { preview ->
            fetchedByKey["${preview.authorId}:${preview.id}"]
        }
    }
}
