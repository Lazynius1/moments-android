package com.moments.android.views.story

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.cache.VideoPreloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `Views/story/StoryPlaybackCoordinator.swift`.
 * Progreso/pausa, timer de imagen, preload (Coil ≡ Kingfisher, VideoPreloader) y memory trim.
 */
class StoryPlaybackCoordinator(
    context: Context? = null,
) {
    private val appContext = context?.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var imageTimer: Job? = null
    private var currentStoryId: String? = null
    private var memoryCallbacks: ComponentCallbacks2? = null

    private val maxPreloadedStories = 6
    private val storiesToPreloadAhead = 5
    private val defaultStoryDuration = 15.0

    /** ≡ `@Published private(set) var preloadedStories` */
    val preloadedStories = mutableStateMapOf<String, Story>()

    /** ≡ `@Published private(set) var preloadedImages` (UIImage → Bitmap) */
    val preloadedImages = mutableStateMapOf<String, Bitmap>()

    var progress by mutableFloatStateOf(0f)
        private set
    var isPaused by mutableStateOf(false)
        private set

    init {
        val ctx = appContext
        if (ctx != null) {
            val callbacks = object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit
                override fun onLowMemory() = handleMemoryWarning()
                override fun onTrimMemory(level: Int) {
                    // ≡ `.momentsDidReceiveMemoryWarning`
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                        handleMemoryWarning()
                    }
                }
            }
            ctx.registerComponentCallbacks(callbacks)
            memoryCallbacks = callbacks
        }
    }

    /** Conservamos la story actual en reproducción; soltamos precargas (paridad iOS: removeAll). */
    private fun handleMemoryWarning() {
        scope.launch {
            preloadedImages.clear()
            preloadedStories.clear()
        }
    }

    fun prepareStory(story: Story, onImageComplete: () -> Unit) {
        progress = 0f
        isPaused = false
        currentStoryId = story.id
        if (story.mediaItem.type == MediaItem.MediaType.IMAGE) {
            startImageTimer(story, onImageComplete)
        }
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
        if (story.mediaItem.type == MediaItem.MediaType.IMAGE) {
            startImageTimer(story, onImageComplete)
        }
    }

    fun setPausedFromVideoBinding(shouldPause: Boolean) {
        isPaused = shouldPause
    }

    fun updateVideoProgress(newProgress: Float, story: Story) {
        if (currentStoryId != story.id) return
        val storyId = story.id
        val clamped = newProgress.coerceIn(0f, 1f)
        scope.launch {
            if (currentStoryId != storyId || progress == clamped) return@launch
            progress = clamped
        }
    }

    fun canAdvanceAfterVideoComplete(): Boolean = !isPaused

    fun progressForSegment(index: Int, storyIndex: Int): Float = when {
        index < storyIndex -> 1f
        index == storyIndex -> progress
        else -> 0f
    }

    fun preloadNextStory(currentStoryId: String, allStories: List<Story>) {
        val currentIndex = allStories.indexOfFirst { it.id == currentStoryId }
        if (currentIndex < 0) return
        val endIndex = minOf(currentIndex + storiesToPreloadAhead, allStories.lastIndex)
        if (currentIndex >= endIndex) return
        for (index in (currentIndex + 1)..endIndex) {
            preloadStory(allStories[index])
        }
    }

    fun preloadStory(story: Story) {
        val storyId = story.id ?: return
        if (preloadedStories.containsKey(storyId)) return
        if (preloadedStories.size >= maxPreloadedStories) {
            clearOldestPreloadedStory()
        }
        preloadedStories[storyId] = story
        when (story.mediaItem.type) {
            MediaItem.MediaType.IMAGE -> preloadImage(story)
            MediaItem.MediaType.VIDEO -> {
                preloadVideo(story)
                preloadVideoPoster(story)
            }
        }
    }

    fun clearPreloadCache() {
        preloadedStories.clear()
        preloadedImages.clear()
    }

    fun getPreloadedStory(storyId: String): Story? = preloadedStories[storyId]

    fun getPreloadedImage(storyId: String): Bitmap? = preloadedImages[storyId]

    /** ≡ deinit + cancel timer; desregistra memory callbacks. */
    fun close() {
        invalidateImageTimer()
        memoryCallbacks?.let { callbacks ->
            appContext?.unregisterComponentCallbacks(callbacks)
        }
        memoryCallbacks = null
        scope.cancel()
    }

    private fun preloadImage(story: Story) {
        val storyId = story.id ?: return
        val ctx = appContext ?: return
        val url = story.mediaItem.url
        if (url.isBlank()) return
        // ≡ KingfisherManager.shared.retrieveImage
        val request = ImageRequest.Builder(ctx)
            .data(url)
            .listener(
                onSuccess = { _, result ->
                    bitmapFrom(result)?.let { bmp ->
                        scope.launch { preloadedImages[storyId] = bmp }
                    }
                },
            )
            .build()
        ctx.imageLoader.enqueue(request)
    }

    private fun preloadVideo(story: Story) {
        if (story.id == null) return
        val trimmed = story.mediaItem.url.trim()
        if (trimmed.isEmpty()) return
        VideoPreloader.preloadAssets(listOf(trimmed))
    }

    private fun preloadVideoPoster(story: Story) {
        val storyId = story.id ?: return
        val ctx = appContext ?: return
        val thumb = story.mediaItem.thumbnailUrl?.trim().orEmpty()
        if (thumb.isEmpty()) return
        val request = ImageRequest.Builder(ctx)
            .data(thumb)
            .listener(
                onSuccess = { _, result ->
                    bitmapFrom(result)?.let { bmp ->
                        scope.launch { preloadedImages[storyId] = bmp }
                    }
                },
            )
            .build()
        ctx.imageLoader.enqueue(request)
    }

    private fun bitmapFrom(result: SuccessResult): Bitmap? =
        (result.drawable as? BitmapDrawable)?.bitmap

    private fun clearOldestPreloadedStory() {
        val oldestStoryId = preloadedStories.keys.firstOrNull() ?: return
        preloadedStories.remove(oldestStoryId)
        preloadedImages.remove(oldestStoryId)
    }

    private fun startImageTimer(story: Story, onComplete: () -> Unit) {
        val duration = if (story.duration > 0) story.duration else defaultStoryDuration
        invalidateImageTimer()
        // ≡ Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true)
        imageTimer = scope.launch {
            while (true) {
                delay(100)
                if (tickImageStory(duration, onComplete)) break
            }
        }
    }

    /** @return true si el timer terminó (progress ≥ 1). */
    private fun tickImageStory(duration: Double, onComplete: () -> Unit): Boolean {
        if (isPaused) return false
        progress += (0.1 / duration).toFloat()
        if (progress >= 1f) {
            progress = 1f
            imageTimer = null
            onComplete()
            return true
        }
        return false
    }

    private fun invalidateImageTimer() {
        imageTimer?.cancel()
        imageTimer = null
    }
}
