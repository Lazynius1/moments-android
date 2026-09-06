package com.moments.android.views.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.models.NotificationType
import com.moments.android.models.cache.CachedSearch
import com.moments.android.notifications.services.NotificationService
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
import com.moments.android.services.content.BackendFeedService
import com.moments.android.services.content.FeedCursor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `ExploreViewModel.swift` (919 líneas).
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
    var followerUserIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var pendingRequests by mutableStateOf<Set<String>>(emptySet())
        private set
    var recentSearches by mutableStateOf<List<CachedSearch>>(emptyList())
        private set
    var userButtonStates by mutableStateOf<Map<String, FollowButtonState>>(emptyMap())
        private set
    var currentUserInterests by mutableStateOf<List<String>>(emptyList())
        private set
    var authorProfiles by mutableStateOf<Map<String, AppUser>>(emptyMap())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var currentUserId: String? = null
    private var blockedUsers: Set<String> = emptySet()
    private var searchJob: Job? = null
    private var activeSearchQuery: String = ""
    var isSearchingMoments by mutableStateOf(false)
        private set
    var isSearchingUsers by mutableStateOf(false)
        private set
    var searchFailed by mutableStateOf(false)
        private set
    var hasMoreSearchResults by mutableStateOf(false)
        private set
    @set:JvmName("assignSearchFilter")
    var searchFilter by mutableStateOf("mixed")
        private set
    var isLoadingMoreExplore by mutableStateOf(false)
        private set
    var hasMoreExplore by mutableStateOf(false)
        private set
    var explorePageFailed by mutableStateOf(false)
        private set
    val isSearching: Boolean get() = isSearchingMoments || isSearchingUsers
    private var searchGeneration = 0
    private var contentSearchJob: Job? = null
    private var searchCursor: String? = null
    private var contentSearchQuery = ""
    private var contentSearchMode = "mixed"
    private var exploreCursor: FeedCursor? = null
    private var exploreJob: Job? = null
    private var exploreGeneration = 0


    private val followListener: (String, FollowButtonState) -> Unit = { userId, state ->
        userButtonStates = userButtonStates + (userId to state)
    }

    init {
        FollowStateStore.addListener(followListener)
        loadRecentSearches()
    }

    override fun onCleared() {
        FollowStateStore.removeListener(followListener)
        searchJob?.cancel()
        super.onCleared()
    }

    // MARK: - Flujo principal

    fun fetchMomentsByInterests() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            errorMessage = ExploreVmErrors.AUTH_REQUIRED
            return
        }
        currentUserId = userId
        loadExplorePage(reset = true)
        isLoading = true
        errorMessage = null

        val cached = LocalPersistenceService.loadExploreMoments()
        if (cached.isNotEmpty() && moments.isEmpty()) {
            moments = cached
            if (activeSearchQuery.isEmpty()) filteredMoments = cached
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
                errorMessage = ExploreVmErrors.profileLoadFailed(e.message)
            }
        }
    }

    fun refreshContent() {
        moments = emptyList()
        filteredMoments = emptyList()
        suggestedUsers = emptyList()
        searchedUsers = emptyList()
        followedUserIds = emptySet()
        pendingRequests = emptySet()
        fetchMomentsByInterests()
    }

    fun clearData() {
        smartSearch("")
        exploreGeneration++
        exploreJob?.cancel()
        hasMoreExplore = false
        moments = emptyList()
        filteredMoments = emptyList()
        searchedUsers = emptyList()
        suggestedUsers = emptyList()
        authorProfiles = emptyMap()
        userButtonStates = emptyMap()
        errorMessage = null
        isLoading = false
    }

    fun refreshAllContent() {
        clearData()
        fetchMomentsByInterests()
    }

    fun debugVisibleContent() {
        if (currentUserId == null) return
        // Paridad iOS: solo agrega distribución por audiencia (no-op de logs).
        moments.groupingBy { it.audience ?: "everyone" }.eachCount()
    }

    // MARK: - Smart search

    fun setSearchFilter(filter: String) {
        searchFilter = filter
        smartSearch(activeSearchQuery)
    }

    fun smartSearch(query: String) {
        activeSearchQuery = query.trim()
        searchJob?.cancel()
        contentSearchJob?.cancel()
        contentSearchJob = null
        searchGeneration++
        val generation = searchGeneration
        searchCursor = null
        hasMoreSearchResults = false
        searchFailed = false
        searchedUsers = emptyList()
        filteredMoments = emptyList()
        isSearchingMoments = false
        isSearchingUsers = false
        if (activeSearchQuery.isEmpty()) {
            filteredMoments = moments
            return
        }
        val raw = activeSearchQuery
        val mode = when {
            raw.startsWith("#") -> "hashtag"
            raw.startsWith("@") -> "username"
            else -> searchFilter
        }
        val clean = if (raw.startsWith("#") || raw.startsWith("@")) raw.drop(1) else raw
        contentSearchQuery = clean
        contentSearchMode = mode
        if (clean.isEmpty()) return
        isSearchingMoments = mode != "username"
        isSearchingUsers = mode == "username" || mode == "mixed"
        searchJob = viewModelScope.launch {
            delay(300)
            if (searchGeneration != generation) return@launch
            if (isSearchingMoments) loadMoreSearchResults()
            if (isSearchingUsers) searchUsers(clean)
        }
    }

    fun searchByHashtag(hashtag: String) {
        searchFilter = "hashtag"
        smartSearch("#" + hashtag.trimStart('#'))
    }

    fun exploreByLocation(locationName: String) {
        searchFilter = "location"
        smartSearch(locationName)
    }

    fun retrySearch() = smartSearch(activeSearchQuery)

    fun loadMoreSearchResults() {
        if (contentSearchJob?.isActive == true || contentSearchMode == "username" || contentSearchQuery.isEmpty()) return
        val generation = searchGeneration
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        isSearchingMoments = true
        searchFailed = false
        contentSearchJob = viewModelScope.launch {
            val page = BackendFeedService.searchMoments(contentSearchQuery, contentSearchMode, searchCursor)
            if (searchGeneration != generation || FirebaseAuth.getInstance().currentUser?.uid != uid) return@launch
            isSearchingMoments = false
            if (page == null) { searchFailed = true; return@launch }
            filteredMoments = (filteredMoments + page.moments).distinctBy { "${it.authorId}/${it.id}" }
            searchCursor = page.nextCursor
            hasMoreSearchResults = page.nextCursor != null
        }
    }

    fun loadMoreExplore() = loadExplorePage(reset = false)

    private fun loadExplorePage(reset: Boolean) {
        if (!reset && (isLoadingMoreExplore || (!hasMoreExplore && !explorePageFailed))) return
        if (reset) {
            exploreGeneration++
            exploreJob?.cancel()
            exploreCursor = null
            hasMoreExplore = false
        }
        val generation = exploreGeneration
        val replacesResults = reset || exploreCursor == null
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        isLoadingMoreExplore = true
        explorePageFailed = false
        exploreJob = viewModelScope.launch {
            val page = BackendFeedService.fetchExplorePage(exploreCursor)
            if (exploreGeneration != generation || FirebaseAuth.getInstance().currentUser?.uid != uid) return@launch
            isLoading = false
            isLoadingMoreExplore = false
            if (page == null) { explorePageFailed = true; return@launch }
            moments = ((if (replacesResults) emptyList() else moments) + page.moments).distinctBy { "${it.authorId}/${it.id}" }
            if (activeSearchQuery.isEmpty()) filteredMoments = moments
            exploreCursor = page.nextCursor
            hasMoreExplore = page.nextCursor != null
            LocalPersistenceService.saveExploreMoments(moments.take(120), sync = true)
        }
    }

    // MARK: - Historial

    fun loadRecentSearches() {
        recentSearches = LocalPersistenceService.loadRecentSearches()
    }

    fun saveSearchRecord(query: String, type: String, targetId: String? = null) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        var finalType = type
        var finalQuery = trimmed
        if (type == "text") {
            when (val detected = detectSearchType(trimmed)) {
                is SearchType.Hashtag -> {
                    finalType = "hashtag"
                    finalQuery = "#${detected.value}"
                }
                is SearchType.Username -> {
                    finalType = "user"
                    finalQuery = "@${detected.value}"
                }
                is SearchType.Location -> {
                    finalType = "location"
                    finalQuery = detected.value
                }
                is SearchType.Mixed -> finalType = "text"
            }
        }

        LocalPersistenceService.saveSearch(finalQuery, finalType, targetId)
        loadRecentSearches()
    }

    fun deleteSearch(search: CachedSearch) {
        LocalPersistenceService.deleteSearch(search.id)
        loadRecentSearches()
    }

    fun clearAllSearches() {
        LocalPersistenceService.clearSearchHistory()
        recentSearches = emptyList()
    }

    // MARK: - Social / follow

    fun getSocialStatus(userId: String): ExploreSocialStatus? {
        val isFollowing = userId in followedUserIds
        val isFollower = userId in followerUserIds
        return when {
            isFollowing && isFollower -> ExploreSocialStatus.MUTUAL
            isFollower -> ExploreSocialStatus.FOLLOWS_YOU
            isFollowing -> ExploreSocialStatus.FOLLOWING
            else -> null
        }
    }

    fun getButtonState(userId: String): FollowButtonState =
        userButtonStates[userId] ?: FollowButtonState.CAN_FOLLOW

    fun filterFollowedUsersFromSuggestions() {
        // iOS: solo excluye followedUserIds
        suggestedUsers = suggestedUsers.filter { it.id !in followedUserIds }
    }

    fun loadAuthorProfile(userId: String) {
        if (authorProfiles.containsKey(userId)) return
        viewModelScope.launch {
            runCatching { firestore.fetchUserProfile(userId) }
                .onSuccess { authorProfiles = authorProfiles + (userId to it) }
        }
    }

    suspend fun canViewContent(userId: String): Boolean {
        val viewerId = currentUserId ?: return false
        return PrivacyService.canViewUserContent(viewerId, userId)
    }

    fun checkUserButtonState(userId: String) {
        val viewerId = currentUserId ?: return
        viewModelScope.launch {
            FollowStateStore.state(userId)?.let {
                userButtonStates = userButtonStates + (userId to it)
            }
            val reconciled = FollowStateStore.resolve(viewerId, userId) ?: return@launch
            userButtonStates = userButtonStates + (userId to reconciled)
            if (reconciled.isFollowingOrMutual) {
                followedUserIds = followedUserIds + userId
                suggestedUsers = suggestedUsers.filter { it.id != userId }
            }
        }
    }

    /**
     * Port de `followUser(userId:)` — privada → request; pública → follow;
     * `requestPendingCancellable` → cancel.
     */
    fun followUser(userId: String) {
        val viewerId = currentUserId ?: return
        viewModelScope.launch {
            if (userButtonStates[userId] == FollowButtonState.REQUEST_PENDING_CANCELLABLE) {
                try {
                    firestore.cancelFollowRequest(viewerId, userId)
                    userButtonStates = userButtonStates + (userId to FollowButtonState.CAN_REQUEST_FOLLOW)
                    pendingRequests = pendingRequests - userId
                    FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, userId)
                } catch (e: Exception) {
                    errorMessage = ExploreVmErrors.cancelRequestFailed(e.message)
                }
                return@launch
            }

            val profile = runCatching { firestore.fetchUserProfile(userId) }.getOrElse {
                errorMessage = ExploreVmErrors.fetchProfileFailed(it.message)
                return@launch
            }

            if (profile.isPrivate) {
                suggestedUsers = suggestedUsers.filter { it.id != userId }
                pendingRequests = pendingRequests + userId
                userButtonStates = userButtonStates + (userId to FollowButtonState.REQUEST_PENDING_CANCELLABLE)
                FollowStateStore.setState(FollowButtonState.REQUEST_PENDING_CANCELLABLE, userId)
                try {
                    firestore.sendFollowRequest(viewerId, userId)
                } catch (e: Exception) {
                    errorMessage = ExploreVmErrors.sendRequestFailed(e.message)
                    userButtonStates = userButtonStates + (userId to FollowButtonState.CAN_REQUEST_FOLLOW)
                    pendingRequests = pendingRequests - userId
                    FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, userId)
                }
            } else {
                suggestedUsers = suggestedUsers.filter { it.id != userId }
                followedUserIds = followedUserIds + userId
                userButtonStates = userButtonStates + (userId to FollowButtonState.FOLLOWING)
                FollowStateStore.setState(FollowButtonState.FOLLOWING, userId)
                try {
                    firestore.followUser(viewerId, userId)
                } catch (e: Exception) {
                    errorMessage = ExploreVmErrors.followUserFailed(e.message)
                    userButtonStates = userButtonStates + (userId to FollowButtonState.CAN_FOLLOW)
                    followedUserIds = followedUserIds - userId
                    FollowStateStore.setState(FollowButtonState.CAN_FOLLOW, userId)
                }
            }
        }
    }

    // MARK: - Private loaders

    private suspend fun loadConnectionsFirst(userId: String) {
        val following = runCatching { firestore.fetchFollowing(userId) }.getOrDefault(emptyList())
        val followers = runCatching { firestore.fetchFollowers(userId) }.getOrDefault(emptyList())
        val notifications = NotificationService.fetchNotificationsOnce(userId).getOrDefault(emptyList())

        val loadedFollowedIds = following.map { it.id }.toSet()
        followedUserIds = loadedFollowedIds
        followerUserIds = followers.map { it.id }.toSet()
        pendingRequests = notifications
            .filter { it.type == NotificationType.FOLLOW_REQUEST && it.isPending }
            .map { it.senderId }
            .toSet()

        suggestedUsers = suggestedUsers.filter { it.id !in loadedFollowedIds }
        updateButtonStatesForAllUsers()
    }

    private fun updateButtonStatesForAllUsers() {
        suggestedUsers.forEach { checkUserButtonState(it.id) }
        searchedUsers.forEach { checkUserButtonState(it.id) }
        filterFollowedUsersFromSuggestions()
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
        val currentFollowed = followedUserIds
        val discovered = (shared.await() + suggested.await() + popular.await())
            .distinctBy { it.id }
            .filter { user ->
                user.id != userId &&
                    user.id !in blockedUsers &&
                    userId !in user.blockedUsers &&
                    user.id !in currentFollowed
            }
            .sortedByDescending { user ->
                user.interests.toSet().intersect(currentUserInterests.toSet()).size
            }

        suggestedUsers = discovered.take(10)
        filterFollowedUsersFromSuggestions()
        // The ranked backend loads recommendations independently.
    }

    private suspend fun fetchPopularUsersForExplore(excludingUserId: String): List<AppUser> =
        runCatching { firestore.fetchPublicUsersForExplore(excludingUserId) }.getOrDefault(emptyList())

    private fun detectSearchType(query: String): SearchType {
        val trimmed = query.trim()
        if (trimmed.startsWith("#")) return SearchType.Hashtag(trimmed.drop(1).lowercase())
        if (trimmed.startsWith("@")) return SearchType.Username(trimmed.drop(1).lowercase())
        return SearchType.Mixed(trimmed)
    }

    private suspend fun searchUsers(username: String) {
        val generation = searchGeneration
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        try {
            val users = firestore.searchUsers(username.lowercase(), limit = 20)
            if (searchGeneration != generation || FirebaseAuth.getInstance().currentUser?.uid != uid) return
            searchedUsers = users.filter { it.id != uid && it.id !in blockedUsers && uid !in it.blockedUsers }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (searchGeneration == generation) searchFailed = true
        } finally {
            if (searchGeneration == generation) isSearchingUsers = false
        }
    }

    private sealed class SearchType {
        data class Hashtag(val value: String) : SearchType()
        data class Username(val value: String) : SearchType()
        data class Location(val value: String) : SearchType()
        data class Mixed(val value: String) : SearchType()
    }
}

/** Port de estados de `getSocialStatus` (ExploreViewModel.swift). */
enum class ExploreSocialStatus {
    MUTUAL,
    FOLLOWS_YOU,
    FOLLOWING,
}

/** Mensajes ≡ `errors.*` / `explore.error.*` (en iOS Localizable). */
private object ExploreVmErrors {
    const val AUTH_REQUIRED = "Please sign in to continue."
    fun profileLoadFailed(detail: String?) = "Could not load your profile: ${detail.orEmpty()}"
    fun momentsLoadFailed(detail: String?) = "Could not load moments: ${detail.orEmpty()}"
    fun cancelRequestFailed(detail: String?) = "Could not cancel request: ${detail.orEmpty()}"
    fun sendRequestFailed(detail: String?) = "Could not send follow request: ${detail.orEmpty()}"
    fun followUserFailed(detail: String?) = "Could not follow user: ${detail.orEmpty()}"
    fun fetchProfileFailed(detail: String?) = "Could not load profile: ${detail.orEmpty()}"
    fun searchUsersFailed(detail: String?) = "Could not search users: ${detail.orEmpty()}"
}
