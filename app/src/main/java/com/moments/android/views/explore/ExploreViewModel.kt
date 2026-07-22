package com.moments.android.views.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.models.cache.CachedSearch
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchPublicUsersForExplore
import com.moments.android.services.firestore.fetchSuggestedUsers
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.firestore.fetchUsersWithSharedInterests
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewMomentInExplore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `ExploreViewModel.swift` — flujo principal + smart search.
 * SuggestedUsersView paginado infinito / bento exacto: fuera del MVP.
 */
class ExploreViewModel(
    private val firestore: FirestoreService = FirestoreService(),
) : ViewModel() {

    var moments by mutableStateOf<List<Moment>>(emptyList())
        private set
    var filteredMoments by mutableStateOf<List<Moment>>(emptyList())
        private set
    var searchedUsers by mutableStateOf<List<AppUser>>(emptyList())
        private set
    var suggestedUsers by mutableStateOf<List<AppUser>>(emptyList())
        private set
    var followedUserIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var recentSearches by mutableStateOf<List<CachedSearch>>(emptyList())
        private set
    var userButtonStates by mutableStateOf<Map<String, FollowButtonState>>(emptyMap())
        private set
    var currentUserInterests by mutableStateOf<List<String>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var currentUserId: String? = null
    private var blockedUsers: Set<String> = emptySet()
    private var searchJob: Job? = null
    private var activeSearchQuery: String = ""

    private val followListener: (String, FollowButtonState) -> Unit = { userId, state ->
        userButtonStates = userButtonStates + (userId to state)
    }

    init {
        FollowStateStore.addListener(followListener)
        recentSearches = LocalPersistenceService.loadRecentSearches()
    }

    override fun onCleared() {
        FollowStateStore.removeListener(followListener)
        searchJob?.cancel()
        super.onCleared()
    }

    fun fetchMomentsByInterests() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            errorMessage = "Auth required"
            return
        }
        currentUserId = userId
        isLoading = true
        errorMessage = null

        val cached = LocalPersistenceService.loadExploreMoments()
        if (cached.isNotEmpty() && moments.isEmpty()) {
            moments = cached
            filteredMoments = cached
            isLoading = false
        }

        viewModelScope.launch {
            try {
                val profile = firestore.fetchUserProfile(userId)
                currentUserInterests = profile.interests
                blockedUsers = profile.blockedUsers.toSet()
                loadConnectionsFirst(userId)
                loadUsersAndMoments(userId)
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.message ?: "Profile load failed"
            }
        }
    }

    fun refreshAllContent() {
        moments = emptyList()
        filteredMoments = emptyList()
        suggestedUsers = emptyList()
        searchedUsers = emptyList()
        errorMessage = null
        fetchMomentsByInterests()
    }

    fun smartSearch(query: String) {
        activeSearchQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            searchedUsers = emptyList()
            filteredMoments = moments
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            if (activeSearchQuery != query) return@launch
            when (val type = detectSearchType(query)) {
                is SearchType.Hashtag -> searchHashtags(type.value)
                is SearchType.Username -> searchUsersByName(type.value)
                is SearchType.Location -> searchLocations(type.value)
                is SearchType.Mixed -> searchEverything(type.value)
            }
        }
    }

    fun saveSearchRecord(query: String, type: String, targetId: String? = null) {
        val q = query.trim()
        if (q.isEmpty()) return
        LocalPersistenceService.saveSearch(q, type, targetId)
        recentSearches = LocalPersistenceService.loadRecentSearches()
    }

    fun deleteSearch(search: CachedSearch) {
        LocalPersistenceService.deleteSearch(search.id)
        recentSearches = LocalPersistenceService.loadRecentSearches()
    }

    fun clearAllSearches() {
        LocalPersistenceService.clearSearchHistory()
        recentSearches = emptyList()
    }

    fun checkUserButtonState(userId: String) {
        val viewerId = currentUserId ?: return
        viewModelScope.launch {
            FollowStateStore.state(userId)?.let {
                userButtonStates = userButtonStates + (userId to it)
            }
            val authoritative = PrivacyService.getFollowButtonState(viewerId, userId)
            val reconciled = FollowStateStore.reconciledState(authoritative, userId)
            userButtonStates = userButtonStates + (userId to reconciled)
            FollowStateStore.setState(reconciled, userId)
            filterFollowedUsersFromSuggestions()
        }
    }

    fun filterFollowedUsersFromSuggestions() {
        suggestedUsers = suggestedUsers.filter { user ->
            val state = userButtonStates[user.id]
            state != FollowButtonState.FOLLOWING && user.id !in followedUserIds
        }
    }

    suspend fun canViewContent(userId: String): Boolean {
        val viewerId = currentUserId ?: return false
        return PrivacyService.canViewUserContent(viewerId, userId)
    }

    fun followUser(userId: String) {
        val viewerId = currentUserId ?: return
        viewModelScope.launch {
            val previous = userButtonStates[userId] ?: FollowButtonState.CAN_FOLLOW
            val optimistic = when (previous) {
                FollowButtonState.FOLLOWING -> FollowButtonState.CAN_FOLLOW
                FollowButtonState.CAN_REQUEST_FOLLOW -> FollowButtonState.REQUEST_PENDING_CANCELLABLE
                FollowButtonState.REQUEST_PENDING_CANCELLABLE -> FollowButtonState.CAN_REQUEST_FOLLOW
                FollowButtonState.CAN_FOLLOW -> FollowButtonState.FOLLOWING
                else -> previous
            }
            userButtonStates = userButtonStates + (userId to optimistic)
            FollowStateStore.setState(optimistic, userId)
            try {
                when (optimistic) {
                    FollowButtonState.FOLLOWING -> firestore.followUser(viewerId, userId)
                    FollowButtonState.CAN_FOLLOW -> firestore.unfollowUser(viewerId, userId)
                    FollowButtonState.REQUEST_PENDING_CANCELLABLE ->
                        firestore.followUser(viewerId, userId)
                    FollowButtonState.CAN_REQUEST_FOLLOW ->
                        firestore.unfollowUser(viewerId, userId)
                    else -> Unit
                }
                if (optimistic == FollowButtonState.FOLLOWING) {
                    followedUserIds = followedUserIds + userId
                    filterFollowedUsersFromSuggestions()
                } else if (optimistic == FollowButtonState.CAN_FOLLOW) {
                    followedUserIds = followedUserIds - userId
                }
            } catch (_: Exception) {
                userButtonStates = userButtonStates + (userId to previous)
                FollowStateStore.setState(previous, userId)
            }
        }
    }

    private suspend fun loadConnectionsFirst(userId: String) {
        val following = runCatching { firestore.fetchFollowing(userId) }.getOrDefault(emptyList())
        followedUserIds = following.map { it.id }.toSet()
    }

    private suspend fun loadUsersAndMoments(userId: String) = coroutineScope {
        val shared = async {
            runCatching {
                firestore.fetchUsersWithSharedInterests(currentUserInterests, userId)
            }.getOrDefault(emptyList())
        }
        val suggested = async {
            runCatching { firestore.fetchSuggestedUsers() }.getOrDefault(emptyList()).take(20)
        }
        val popular = async { fetchPopularUsersForExplore(userId) }
        val discovered = (shared.await() + suggested.await() + popular.await())
            .distinctBy { it.id }
            .filter { user ->
                user.id != userId &&
                    user.id !in blockedUsers &&
                    userId !in user.blockedUsers &&
                    user.id !in followedUserIds
            }
            .sortedByDescending { user ->
                user.interests.toSet().intersect(currentUserInterests.toSet()).size
            }

        suggestedUsers = discovered.take(10)
        val userIds = discovered.take(100).map { it.id }
        loadMomentsFromUsers(userIds)
    }

    private suspend fun fetchPopularUsersForExplore(excludingUserId: String): List<AppUser> =
        runCatching { firestore.fetchPublicUsersForExplore(excludingUserId) }.getOrDefault(emptyList())

    private suspend fun loadMomentsFromUsers(userIds: List<String>) {
        try {
            val all = firestore.fetchMomentsFromUsers(userIds)
            val visible = filterMomentsForExploreVisibility(all)
            moments = visible
            filteredMoments = visible
            LocalPersistenceService.saveExploreMoments(visible, sync = true)
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
            errorMessage = e.message ?: "Moments load failed"
        }
    }

    private suspend fun filterMomentsForExploreVisibility(source: List<Moment>): List<Moment> {
        val viewerId = currentUserId ?: return emptyList()
        return coroutineScope {
            source.map { moment ->
                async {
                    if (moment.authorId == viewerId) return@async null
                    if (moment.authorId in blockedUsers) return@async null
                    if (PrivacyService.canUserViewMomentInExplore(moment, viewerId)) moment else null
                }
            }.awaitAll().filterNotNull().let { visible ->
                source.filter { m -> visible.any { it.id == m.id } }
            }
        }
    }

    private fun detectSearchType(query: String): SearchType {
        val trimmed = query.trim()
        if (trimmed.startsWith("#")) return SearchType.Hashtag(trimmed.drop(1).lowercase())
        if (trimmed.startsWith("@")) return SearchType.Username(trimmed.drop(1).lowercase())
        val locationKeywords = listOf("en ", "lugar ", "city ", "ciudad ", "beach ", "playa ", "restaurant ", "cafe ")
        if (locationKeywords.any { trimmed.lowercase().contains(it) }) {
            return SearchType.Location(trimmed)
        }
        return SearchType.Mixed(trimmed)
    }

    private suspend fun searchUsersByName(username: String) {
        filteredMoments = emptyList()
        val clean = username.lowercase().trim()
        if (clean.isEmpty()) {
            searchedUsers = emptyList()
            return
        }
        try {
            val users = firestore.searchUsers(clean, limit = 20)
            val uid = currentUserId.orEmpty()
            searchedUsers = users.filter { user ->
                user.id != uid &&
                    user.id !in blockedUsers &&
                    uid !in user.blockedUsers
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    private fun searchHashtags(hashtag: String) {
        searchedUsers = emptyList()
        val tag = "#$hashtag"
        val uid = currentUserId
        filteredMoments = moments.filter { moment ->
            moment.content.lowercase().contains(tag) &&
                moment.authorId !in blockedUsers &&
                moment.authorId != uid
        }
    }

    private fun searchLocations(location: String) {
        searchedUsers = emptyList()
        val q = location.lowercase()
        val uid = currentUserId
        filteredMoments = moments.filter { moment ->
            val loc = moment.location?.lowercase().orEmpty()
            loc.contains(q) &&
                moment.authorId !in blockedUsers &&
                moment.authorId != uid
        }
    }

    private suspend fun searchEverything(query: String) {
        val q = query.lowercase()
        searchUsersByName(q)
        val uid = currentUserId
        filteredMoments = moments.filter { moment ->
            val match = moment.content.lowercase().contains(q) ||
                (moment.location ?: "").lowercase().contains(q) ||
                moment.username.lowercase().contains(q)
            match && moment.authorId !in blockedUsers && moment.authorId != uid
        }
    }

    private sealed class SearchType {
        data class Hashtag(val value: String) : SearchType()
        data class Username(val value: String) : SearchType()
        data class Location(val value: String) : SearchType()
        data class Mixed(val value: String) : SearchType()
    }
}
