package com.moments.android.views.profile.userprofile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.PublicProfileAvailability
import com.moments.android.services.firestore.fetchMomentsWithVisibility
import com.moments.android.services.firestore.fetchUserProfileWithAvailability
import com.moments.android.services.firestore.registerVisit
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import kotlinx.coroutines.launch

/**
 * Port reducido de `UserProfileViewModel` — perfil de otro usuario desde Feed.
 */
class UserProfileViewModel(
    private val firestore: FirestoreService = FirestoreService(),
) : ViewModel() {

    var user by mutableStateOf<AppUser?>(null)
        private set
    var moments by mutableStateOf<List<Moment>>(emptyList())
        private set
    var followState by mutableStateOf(FollowButtonState.CAN_FOLLOW)
        private set
    var canViewContent by mutableStateOf(false)
        private set
    var availability by mutableStateOf(PublicProfileAvailability.AVAILABLE)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isOwnProfile by mutableStateOf(false)
        private set

    private var targetUserId: String = ""

    fun load(userId: String) {
        targetUserId = userId
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
        if (viewerId == null) {
            errorMessage = "Auth required"
            isLoading = false
            return
        }
        isOwnProfile = viewerId == userId
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val (profile, avail) = firestore.fetchUserProfileWithAvailability(userId)
                user = profile
                availability = avail
                if (avail != PublicProfileAvailability.AVAILABLE) {
                    isLoading = false
                    return@launch
                }
                canViewContent = firestore.canViewContent(viewerId, userId) || isOwnProfile
                val authoritative = PrivacyService.getFollowButtonState(viewerId, userId)
                followState = FollowStateStore.reconciledState(authoritative, userId)
                FollowStateStore.setState(followState, userId)
                if (canViewContent) {
                    moments = firestore.fetchMomentsWithVisibility(userId, viewerId)
                        .filter { it.isArchived != true }
                        .sortedByDescending { it.timestamp.time }
                }
                isLoading = false
                if (!isOwnProfile) {
                    launch {
                        kotlinx.coroutines.delay(1000)
                        runCatching { firestore.registerVisit(viewerId, userId) }
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.message ?: "Profile load failed"
            }
        }
    }

    fun toggleFollow() {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (isOwnProfile) return
        viewModelScope.launch {
            val previous = followState
            val optimistic = when (previous) {
                FollowButtonState.FOLLOWING -> FollowButtonState.CAN_FOLLOW
                FollowButtonState.CAN_REQUEST_FOLLOW -> FollowButtonState.REQUEST_PENDING_CANCELLABLE
                FollowButtonState.REQUEST_PENDING_CANCELLABLE -> FollowButtonState.CAN_REQUEST_FOLLOW
                FollowButtonState.CAN_FOLLOW -> FollowButtonState.FOLLOWING
                else -> previous
            }
            followState = optimistic
            FollowStateStore.setState(optimistic, targetUserId)
            try {
                when (optimistic) {
                    FollowButtonState.FOLLOWING,
                    FollowButtonState.REQUEST_PENDING_CANCELLABLE,
                    -> firestore.followUser(viewerId, targetUserId)
                    FollowButtonState.CAN_FOLLOW,
                    FollowButtonState.CAN_REQUEST_FOLLOW,
                    -> firestore.unfollowUser(viewerId, targetUserId)
                    else -> Unit
                }
                canViewContent = firestore.canViewContent(viewerId, targetUserId) || isOwnProfile
                if (canViewContent && moments.isEmpty()) {
                    moments = firestore.fetchMomentsWithVisibility(targetUserId, viewerId)
                        .filter { it.isArchived != true }
                        .sortedByDescending { it.timestamp.time }
                }
            } catch (_: Exception) {
                followState = previous
                FollowStateStore.setState(previous, targetUserId)
            }
        }
    }
}
