package com.moments.android.views.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Port de `ArchiveViewModel` en `archived stories.swift`.
 * Query: `users/{uid}/stories` where `expirationDate < now`, order `timestamp` DESC, limit 100.
 */
class ArchiveViewModel : ViewModel() {
    var groupedStories by mutableStateOf<Map<String, List<Story>>>(emptyMap())
        private set
    var isLoading by mutableStateOf(false)
        private set

    private val firestore = FirestoreService()
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Flat list sorted by timestamp DESC (≡ grid iOS `storiesForGrid`). */
    val storiesForGrid: List<Story>
        get() = groupedStories.values.flatten().sortedByDescending { it.timestamp.time }

    fun loadArchivedStories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            isLoading = true
            runCatching {
                val snapshot = firestore.db.collection("users").document(userId)
                    .collection("stories")
                    .whereLessThan("expirationDate", Timestamp(Date()))
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()
                snapshot.documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    Story.from(doc.id, doc.data as? Map<String, Any?> ?: return@mapNotNull null)
                }
            }.onSuccess { stories ->
                groupStoriesByDate(stories)
                prefetchRecentImages(stories)
            }
            isLoading = false
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
