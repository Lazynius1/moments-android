package com.moments.android.views.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchActiveStoriesForUsers
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.persistence.StorySeenStateService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewStoryEnhanced
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Port reducido de `StoryViewModel.swift` — carga ring + privacy; sin replies/reactions.
 */
class StoryViewModel(
    private val firestore: FirestoreService = FirestoreService(),
) : ViewModel() {

    var storiesByUser by mutableStateOf<Map<String, List<Story>>>(emptyMap())
        private set
    var userIds by mutableStateOf<List<String>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load(
        ringNavigationUserIds: List<String>,
        startAtUserId: String? = null,
    ) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
        if (viewerId == null) {
            errorMessage = "Auth required"
            return
        }
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val ordered = ringNavigationUserIds.filter { it.isNotEmpty() }.distinct()
                    .ifEmpty {
                        listOfNotNull(startAtUserId?.takeIf { it.isNotEmpty() }, viewerId).distinct()
                    }
                val withStart = if (!startAtUserId.isNullOrBlank() && startAtUserId !in ordered) {
                    listOf(startAtUserId) + ordered
                } else {
                    ordered
                }

                // Caché propia inmediata
                val ownCached = LocalPersistenceService.loadStories(viewerId)
                if (ownCached.isNotEmpty()) {
                    storiesByUser = storiesByUser + (viewerId to ownCached)
                }

                val raw = firestore.fetchActiveStoriesForUsers(withStart)
                val filtered = filterVisible(raw, viewerId)
                storiesByUser = filtered
                userIds = withStart.filter { filtered[it].orEmpty().isNotEmpty() }
                if (userIds.isEmpty() && filtered.isNotEmpty()) {
                    userIds = filtered.keys.toList()
                }
                filtered[viewerId]?.let { LocalPersistenceService.saveStories(it, sync = true) }
                isLoading = false
                if (userIds.isEmpty()) {
                    errorMessage = null // empty handled by UI
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.message ?: "Failed to load stories"
            }
        }
    }

    fun markCurrentSeen(story: Story) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        StorySeenStateService.markSeen(
            viewerId = viewerId,
            authorId = story.authorId,
            timestamp = story.timestamp,
            syncRemote = true,
        )
    }

    fun storiesFor(userId: String): List<Story> = storiesByUser[userId].orEmpty()

    private suspend fun filterVisible(
        raw: Map<String, List<Story>>,
        viewerId: String,
    ): Map<String, List<Story>> = coroutineScope {
        raw.map { (authorId, list) ->
            async {
                val visible = list.map { story ->
                    async {
                        if (PrivacyService.canUserViewStoryEnhanced(story, viewerId)) story else null
                    }
                }.awaitAll().filterNotNull()
                authorId to visible
            }
        }.awaitAll()
            .filter { it.second.isNotEmpty() }
            .toMap()
    }
}
