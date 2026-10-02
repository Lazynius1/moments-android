package com.moments.android.views.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Port de `ArchiveViewModel` en `archived stories.swift`.
 * Query: `users/{uid}/stories` where `expirationDate < now`, order `timestamp` DESC, páginas de 36.
 */
class ArchiveViewModel : ViewModel() {
    var groupedStories by mutableStateOf<Map<String, List<Story>>>(emptyMap())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var canLoadMore by mutableStateOf(true)
        private set
    var isFillingAll by mutableStateOf(false)
        private set

    var hasLoadError by mutableStateOf(false)
        private set

    private val firestore = FirestoreService()
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val pageSize = 36L
    private var lastDocument: DocumentSnapshot? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private var fillJob: kotlinx.coroutines.Job? = null
    private var generation = 0

    /** Flat list sorted by timestamp DESC (≡ grid iOS `storiesForGrid`). */
    val storiesForGrid: List<Story>
        get() = groupedStories.values.flatten().sortedByDescending { it.timestamp.time }

    fun loadArchivedStories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        loadJob?.cancel()
        fillJob?.cancel()
        generation++
        hasLoadError = false
        isLoadingMore = false
        lastDocument = null
        canLoadMore = true
        isFillingAll = false
        groupedStories = emptyMap()
        isLoading = true
        loadJob = viewModelScope.launch {
            loadPage(userId, reset = true)
        }
    }

    fun loadMoreArchivedStories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!canLoadMore || hasLoadError || isLoading || isLoadingMore) return
        loadJob = viewModelScope.launch { loadPage(userId, reset = false) }
    }

    fun retryArchivedStories(fillAll: Boolean = false) {
        if (!hasLoadError || isLoading || isLoadingMore) return
        hasLoadError = false
        if (lastDocument == null && groupedStories.isEmpty()) {
            loadArchivedStories()
            if (fillAll) loadAllArchivedStories()
        } else if (fillAll) loadAllArchivedStories()
        else loadMoreArchivedStories()
    }

    fun loadAllArchivedStories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (isFillingAll || hasLoadError) return
        isFillingAll = true
        val id = generation
        fillJob = viewModelScope.launch {
            try {
                while (canLoadMore && !hasLoadError && generation == id) {
                    currentCoroutineContext().ensureActive()
                    if (isLoading || isLoadingMore) kotlinx.coroutines.delay(100)
                    else loadPage(userId, reset = false)
                }
            } finally {
                if (generation == id) isFillingAll = false
            }
        }
    }

    private suspend fun loadPage(userId: String, reset: Boolean) {
        currentCoroutineContext().ensureActive()
        if (!reset && (!canLoadMore || hasLoadError || isLoadingMore)) return
        val id = generation
        if (!reset) isLoadingMore = true
        var query: Query = firestore.db.collection("users").document(userId).collection("stories")
            .whereLessThan("expirationDate", Timestamp(Date()))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)
        if (!reset) lastDocument?.let { query = query.startAfter(it) }
        try {
            val snapshot = query.get().await()
            currentCoroutineContext().ensureActive()
            if (generation != id) return
            val page = snapshot.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                Story.from(doc.id, doc.data as? Map<String, Any?> ?: return@mapNotNull null)
            }
            val merged = ((if (reset) emptyList() else storiesForGrid) + page)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp.time }
            lastDocument = snapshot.documents.lastOrNull()
            canLoadMore = snapshot.size() == pageSize.toInt()
            groupStoriesByDate(merged)
            prefetchRecentImages(page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == id) hasLoadError = true
        } finally {
            if (generation == id) {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    private fun groupStoriesByDate(stories: List<Story>) {
        groupedStories = stories.groupBy { dayFormatter.format(it.timestamp) }
    }

    private fun prefetchRecentImages(stories: List<Story>) {
        val urls = stories
            .filter { it.mediaItem.type == MediaItem.MediaType.IMAGE }
            .take(10)
            .map { it.mediaItem.url }
            .filter { it.isNotBlank() }
        if (urls.isNotEmpty()) ImagePrefetchManager.prefetch(urls)
    }
}
