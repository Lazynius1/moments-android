package com.moments.android.views.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
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
    /** Mismo contrato que `StoryViewModel.storyViewers`/`storyReactions` en Swift. */
    var storyReactions by mutableStateOf<Map<String, List<StoryReaction>>>(emptyMap())
        private set
    var storyViewers by mutableStateOf<Map<String, List<StoryViewer>>>(emptyMap())
        private set
    private val storyRepository = StoryRepository(firestore)
    private val reactionListeners = mutableMapOf<String, ListenerRegistration>()

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
                preloadStoryInsights(filtered)
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

    /**
     * Reproduce una lista explícita de historias (destacados y cadenas), equivalente al
     * init `chainStories:` de `StoriesView` en iOS: un solo carril, en el orden dado.
     */
    fun loadExplicitStories(stories: List<Story>, applyPrivacyFilter: Boolean = true) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
        if (viewerId == null) {
            errorMessage = "Auth required"
            return
        }
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val visible = if (applyPrivacyFilter) {
                filterVisible(mapOf(EXPLICIT_RAIL_ID to stories), viewerId)[EXPLICIT_RAIL_ID].orEmpty()
            } else {
                stories
            }
            storiesByUser = mapOf(EXPLICIT_RAIL_ID to visible)
            userIds = if (visible.isEmpty()) emptyList() else listOf(EXPLICIT_RAIL_ID)
            preloadStoryInsights(storiesByUser)
            isLoading = false
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

    fun fetchViewers(userId: String, storyId: String) {
        if (storyId.isBlank()) return
        viewModelScope.launch {
            storyViewers = storyViewers + (storyId to runCatching {
                storyRepository.fetchViewers(userId, storyId)
            }.getOrDefault(emptyList()))
        }
    }

    private fun preloadStoryInsights(stories: Map<String, List<Story>>) {
        stories.forEach { (authorId, authorStories) -> authorStories.forEach { story ->
            val storyId = story.id ?: return@forEach
            fetchViewers(authorId, storyId)
            reactionListeners.remove(storyId)?.remove()
            reactionListeners[storyId] = storyRepository.observeReactions(authorId, storyId) { reactions ->
                storyReactions = storyReactions + (storyId to reactions)
            }
        } }
    }

    override fun onCleared() {
        reactionListeners.values.forEach { it.remove() }
        reactionListeners.clear()
        super.onCleared()
    }

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

    companion object {
        /** Clave del carril sintético usado por `loadExplicitStories`. */
        const val EXPLICIT_RAIL_ID = "__explicit__"
    }
}
