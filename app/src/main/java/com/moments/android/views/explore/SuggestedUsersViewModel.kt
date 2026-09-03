package com.moments.android.views.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchPublicUsersPage
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.launch

/**
 * Port de `SuggestedUsersViewModel` (SuggestedUsersView.swift).
 */
class SuggestedUsersViewModel(
    private val firestore: FirestoreService = FirestoreService(),
) : ViewModel() {

    var users by mutableStateOf<List<AppUser>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var userButtonStates by mutableStateOf<Map<String, FollowButtonState>>(emptyMap())
        private set
    var currentUserInterests by mutableStateOf<List<String>>(emptyList())
        private set

    private var currentUserId: String? = null
    private var blockedUsers: Set<String> = emptySet()
    private var followedUserIds: Set<String> = emptySet()
    private var lastDocument: DocumentSnapshot? = null
    private val pageSize = 10

    private val followListener: (String, FollowButtonState) -> Unit = { userId, state ->
        userButtonStates = userButtonStates + (userId to state)
    }

    init {
        FollowStateStore.addListener(followListener)
    }

    override fun onCleared() {
        FollowStateStore.removeListener(followListener)
        super.onCleared()
    }

    fun loadInitialUsers() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        currentUserId = userId
        isLoading = true
        viewModelScope.launch {
            loadCurrentUserInterests(userId)
            loadBlockedUsers(userId)
            loadFollowedUsers(userId)
            loadSuggestedUsers()
        }
    }

    suspend fun refreshUsers() {
        users = emptyList()
        userButtonStates = emptyMap()
        followedUserIds = emptySet()
        lastDocument = null
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        currentUserId = userId
        isLoading = true
        loadCurrentUserInterests(userId)
        loadBlockedUsers(userId)
        loadFollowedUsers(userId)
        loadSuggestedUsers()
    }

    fun loadMoreUsers() {
        if (isLoadingMore || lastDocument == null) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val userId = currentUserId ?: return@launch
                val page = firestore.fetchPublicUsersPage(
                    excludingUserId = userId,
                    limit = pageSize,
                    startAfter = lastDocument,
                )
                if (page.users.isEmpty() && page.lastDocument == null) {
                    isLoadingMore = false
                    return@launch
                }
                val existingIds = users.map { it.id }.toSet()
                val filtered = page.users
                    .filter { user ->
                        user.id !in existingIds &&
                            user.id !in blockedUsers &&
                            userId !in user.blockedUsers &&
                            user.id !in followedUserIds
                    }
                    .sortedByDescending {
                        it.interests.toSet().intersect(currentUserInterests.toSet()).size
                    }
                users = users + filtered
                lastDocument = page.lastDocument
                filtered.forEach { checkUserButtonState(it.id) }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun followUser(userId: String) {
        val viewerId = currentUserId ?: return
        viewModelScope.launch {
            try {
                firestore.followUser(viewerId, userId)
                HapticManager.shared.mediumImpact()
                userButtonStates = userButtonStates + (userId to FollowButtonState.FOLLOWING)
                FollowStateStore.setState(FollowButtonState.FOLLOWING, userId)
                followedUserIds = followedUserIds + userId
                users = users.filter { it.id != userId }
            } catch (_: Exception) {
                checkUserButtonState(userId)
            }
        }
    }

    private suspend fun loadCurrentUserInterests(userId: String) {
        runCatching { firestore.fetchUserProfile(userId) }
            .onSuccess { currentUserInterests = it.interests }
    }

    private suspend fun loadBlockedUsers(userId: String) {
        runCatching { firestore.fetchUserProfile(userId) }
            .onSuccess { blockedUsers = it.blockedUsers.toSet() }
    }

    private suspend fun loadFollowedUsers(userId: String) {
        followedUserIds = runCatching { firestore.fetchFollowing(userId) }
            .getOrDefault(emptyList())
            .map { it.id }
            .toSet()
    }

    private suspend fun loadSuggestedUsers() {
        val userId = currentUserId ?: return
        try {
            val page = firestore.fetchPublicUsersPage(
                excludingUserId = userId,
                limit = pageSize,
                startAfter = null,
            )
            val filtered = page.users
                .filter { user ->
                    user.id !in blockedUsers &&
                        userId !in user.blockedUsers &&
                        user.id !in followedUserIds
                }
                .sortedByDescending {
                    it.interests.toSet().intersect(currentUserInterests.toSet()).size
                }
            users = filtered
            lastDocument = page.lastDocument
            filtered.forEach { checkUserButtonState(it.id) }
        } finally {
            isLoading = false
        }
    }

    private fun checkUserButtonState(userId: String) {
        val viewerId = currentUserId ?: return
        viewModelScope.launch {
            FollowStateStore.state(userId)?.let {
                userButtonStates = userButtonStates + (userId to it)
            }
            val authoritative = PrivacyService.getFollowButtonState(viewerId, userId)
            val reconciled = FollowStateStore.reconciledState(authoritative, userId)
            userButtonStates = userButtonStates + (userId to reconciled)
            FollowStateStore.setState(reconciled, userId)
            if (reconciled.isFollowingOrMutual) {
                followedUserIds = followedUserIds + userId
                users = users.filter { it.id != userId }
            }
        }
    }
}
