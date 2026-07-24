package com.moments.android.views.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de `Views/story/StoryPlaybackCoordinator.swift` — progreso y pausa de la story actual. */
class StoryPlaybackCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var imageTimer: Job? = null
    private var currentStoryId: String? = null

    val preloadedStories = mutableStateMapOf<String, Story>()
    var progress by mutableFloatStateOf(0f)
        private set
    var isPaused by mutableStateOf(false)
        private set

    fun prepareStory(story: Story, onImageComplete: () -> Unit) {
        progress = 0f
        isPaused = false
        currentStoryId = story.id
        invalidateImageTimer()
        if (story.mediaItem.type == MediaItem.MediaType.IMAGE) startImageTimer(story, onImageComplete)
    }

    fun stopStory() {
        isPaused = true
        progress = 0f
        currentStoryId = null
        invalidateImageTimer()
    }

    fun pauseStory() {
        isPaused = true
        invalidateImageTimer()
    }

    fun resumeStory(story: Story, canResume: Boolean, onImageComplete: () -> Unit) {
        if (!canResume) return
        isPaused = false
        if (story.mediaItem.type == MediaItem.MediaType.IMAGE) startImageTimer(story, onImageComplete)
    }

    fun setPausedFromVideoBinding(shouldPause: Boolean) {
        isPaused = shouldPause
    }

    fun updateVideoProgress(newProgress: Float, story: Story) {
        if (currentStoryId == story.id) progress = newProgress.coerceIn(0f, 1f)
    }

    fun canAdvanceAfterVideoComplete() = !isPaused

    fun progressForSegment(index: Int, storyIndex: Int): Float = when {
        index < storyIndex -> 1f
        index == storyIndex -> progress
        else -> 0f
    }

    fun preloadNextStory(currentStoryId: String, allStories: List<Story>) {
        val currentIndex = allStories.indexOfFirst { it.id == currentStoryId }
        if (currentIndex < 0) return
        allStories.drop(currentIndex + 1).take(5).forEach(::preloadStory)
    }

    fun preloadStory(story: Story) {
        val id = story.id ?: return
        if (preloadedStories.containsKey(id)) return
        if (preloadedStories.size >= 6) preloadedStories.remove(preloadedStories.keys.firstOrNull())
        preloadedStories[id] = story
    }

    fun clearPreloadCache() = preloadedStories.clear()
    fun getPreloadedStory(storyId: String) = preloadedStories[storyId]
    fun close() { invalidateImageTimer(); scope.cancel() }

    private fun startImageTimer(story: Story, onComplete: () -> Unit) {
        val durationSeconds = if (story.duration > 0) story.duration else 15.0
        invalidateImageTimer()
        imageTimer = scope.launch {
            while (!isPaused && progress < 1f) {
                delay(100)
                if (isPaused) break
                progress = (progress + (0.1 / durationSeconds).toFloat()).coerceAtMost(1f)
            }
            if (!isPaused && progress >= 1f) onComplete()
        }
    }

    private fun invalidateImageTimer() {
        imageTimer?.cancel()
        imageTimer = null
    }
}
