package com.moments.android.views.profile.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.models.MomentGridPreviewSettings
import com.moments.android.services.content.BackendFeedService
import com.moments.android.services.content.ProfileVisitsService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchMoments
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Date
import java.io.File
import java.io.FileOutputStream

/** Port de `ProfileViewModel.swift`. */
class ProfileViewModel(private val firestoreService: FirestoreService = FirestoreService()) : ViewModel(), UserListViewModel {
    var userProfile by mutableStateOf<AppUser?>(null); private set
    var profileImagePath by mutableStateOf<String?>(null); private set
    var following by mutableStateOf<List<AppUser>>(emptyList()); private set
    var followers by mutableStateOf<List<AppUser>>(emptyList()); private set
    var mutuals by mutableStateOf<List<AppUser>>(emptyList()); private set
    var moments by mutableStateOf<List<Moment>>(emptyList()); private set
    var taggedMoments by mutableStateOf<List<Moment>>(emptyList()); private set
    var customListNamesById by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var isLoading by mutableStateOf(true); var isLoadingMoments by mutableStateOf(true); var isLoadingTagged by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false); var isOffline by mutableStateOf(false); var errorMessage by mutableStateOf<String?>(null)
    var visits by mutableStateOf<List<AppUser>>(emptyList()); var groupedVisits by mutableStateOf<List<com.moments.android.models.GroupedVisit>>(emptyList()); var visitTimestamps by mutableStateOf<Map<String, List<Date>>>(emptyMap()); var isLoadingVisits by mutableStateOf(false)
    private val recentUnfollows = mutableSetOf<String>()
    private val lastUnfollowTime = mutableMapOf<String, Date>()

    fun fetchProfile(userId: String) {
        isLoading = true
        isLoadingMoments = true
        errorMessage = null

        // ≡ SwiftData: pinta el último estado válido sin esperar a Firestore.
        LocalPersistenceService.loadUser(userId)?.let {
            userProfile = it
            profileImagePath = it.profileImagePath
            isLoading = false
        }
        LocalPersistenceService.loadConnections(userId).let { (cachedFollowers, cachedFollowing, cachedMutuals) ->
            if (cachedFollowing.isNotEmpty() || cachedFollowers.isNotEmpty() || cachedMutuals.isNotEmpty()) {
                applyConnectionSnapshots(userId, cachedFollowing, cachedFollowers, cachedMutuals)
            }
        }
        LocalPersistenceService.loadProfileMoments(userId).takeIf { it.isNotEmpty() }?.let {
            moments = sortProfileMoments(it)
            isLoadingMoments = false
        }

        viewModelScope.launch {
            // Los momentos no dependen del documento de perfil: un fallo de éste no vacía la rejilla.
            val momentsRequest = async { runCatching { firestoreService.fetchMoments(userId) } }
            val profileResult = runCatching { firestoreService.fetchUser(userId) }

            profileResult.onSuccess { profile ->
                isOffline = false
                userProfile = profile
                profileImagePath = profile.profileImagePath
                LocalPersistenceService.saveUser(profile)
                runCatching {
                    val followingRequest = async { firestoreService.fetchFollowing(userId) }
                    val followersRequest = async { firestoreService.fetchFollowers(userId) }
                    val mutualsRequest = async { firestoreService.fetchMutuals(userId) }
                    val listsRequest = async { firestoreService.fetchCustomLists(userId) }
                    val now = Date()
                    val following = followingRequest.await().filter { user ->
                        val unfollowedAt = lastUnfollowTime[user.id] ?: return@filter true
                        if (now.time - unfollowedAt.time < 5_000L) false else {
                            lastUnfollowTime.remove(user.id)
                            recentUnfollows.remove(user.id)
                            true
                        }
                    }
                    applyConnectionSnapshots(userId, following, followersRequest.await(), mutualsRequest.await())
                    customListNamesById = listsRequest.await().mapNotNull { list -> list.id?.let { it to list.name } }.toMap()
                    refreshVisits()
                }.onFailure { errorMessage = it.message }
            }.onFailure {
                isOffline = true
                if (userProfile == null) errorMessage = it.message
                isLoading = false
            }

            momentsRequest.await().onSuccess { fetched ->
                moments = sortProfileMoments(fetched)
                LocalPersistenceService.saveProfileMoments(moments, userId, sync = true)
            }.onFailure {
                if (moments.isEmpty() && !isOffline) errorMessage = it.message
            }
            isLoadingMoments = false
        }
    }

    private fun applyConnectionSnapshots(
        userId: String,
        followingUsers: List<AppUser>,
        followerUsers: List<AppUser>,
        mutualUsers: List<AppUser>,
    ) {
        following = followingUsers
        followers = followerUsers
        mutuals = mutualUsers
        isLoading = false
        LocalPersistenceService.saveFollowing(userId, followingUsers)
        LocalPersistenceService.saveFollowers(userId, followerUsers)
        LocalPersistenceService.saveMutuals(userId, mutualUsers)
    }
    fun fetchTaggedMoments(userId: String) {
        viewModelScope.launch {
            isLoadingTagged = true
            try {
                taggedMoments = BackendFeedService.fetchTaggedMoments(userId, limit = 50)?.moments.orEmpty()
            } finally {
                isLoadingTagged = false
            }
        }
    }
    fun refreshProfile() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            errorMessage = "Usuario no autenticado. Por favor, inicia sesión."
            return
        }
        if (isRefreshing || isLoading) return
        isRefreshing = true
        errorMessage = null
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            fetchProfile(userId)
            isRefreshing = false
        }
    }
    fun oldestPinnedMomentId(excluding: String? = null): String? = moments.filter { it.isPinned == true && it.id != excluding }.minByOrNull { it.pinnedAt ?: it.timestamp }?.id
    fun applyMomentPinState(momentId: String, isPinned: Boolean, pinnedAt: Date) { moments = sortProfileMoments(moments.map { if (it.id == momentId) it.copy(isPinned = isPinned.takeIf { it }, pinnedAt = pinnedAt.takeIf { isPinned }) else it }); persistMoments() }
    fun applyPinReplacement(unpinningMomentId: String, pinningMomentId: String, pinnedAt: Date) { moments = sortProfileMoments(moments.map { when (it.id) { unpinningMomentId -> it.copy(isPinned = null, pinnedAt = null); pinningMomentId -> it.copy(isPinned = true, pinnedAt = pinnedAt); else -> it } }); persistMoments() }
    fun applyGridPreview(momentId: String, settings: MomentGridPreviewSettings) { moments = moments.map { if (it.id == momentId) it.copy(gridPreviewScale = settings.scale.takeUnless { settings.isDefault }, gridPreviewOffsetX = settings.offsetX.takeUnless { settings.isDefault }, gridPreviewOffsetY = settings.offsetY.takeUnless { settings.isDefault }, gridPreviewFitMode = settings.fitMode.raw.takeUnless { settings.isDefault }, gridPreviewBackground = settings.background.raw.takeUnless { settings.isDefault }) else it }; persistMoments() }
    override fun followUser(userId: String) { FirebaseAuth.getInstance().currentUser?.uid?.let { currentId -> viewModelScope.launch { runCatching { val target = firestoreService.fetchUser(userId); firestoreService.followUser(currentId, userId); FollowStateStore.setState(if (target.isPrivate) FollowButtonState.REQUEST_PENDING_CANCELLABLE else FollowButtonState.FOLLOWING, userId); if (!target.isPrivate && following.none { it.id == userId }) following = following + target; if (!target.isPrivate && followers.any { it.id == userId } && mutuals.none { it.id == userId }) mutuals = mutuals + target }.onFailure { errorMessage = it.message } } } }
    override fun unfollowUser(userId: String) { FirebaseAuth.getInstance().currentUser?.uid?.let { currentId -> viewModelScope.launch { recentUnfollows += userId; lastUnfollowTime[userId] = Date(); runCatching { firestoreService.unfollowUser(currentId, userId); val known = followers.firstOrNull { it.id == userId } ?: following.firstOrNull { it.id == userId } ?: mutuals.firstOrNull { it.id == userId }; FollowStateStore.setState(if (known?.isPrivate == true) FollowButtonState.CAN_REQUEST_FOLLOW else FollowButtonState.CAN_FOLLOW, userId); following = following.filterNot { it.id == userId }; mutuals = mutuals.filterNot { it.id == userId } }.onFailure { recentUnfollows -= userId; lastUnfollowTime.remove(userId); errorMessage = it.message } } } }
    /** La colección followers se muta en FirestoreService al portar esa API; la UI mantiene el snapshot inmediato. */
    fun removeFollower(userId: String) {
        followers = followers.filterNot { it.id == userId }
        mutuals = mutuals.filterNot { it.id == userId }
        FirebaseAuth.getInstance().currentUser?.uid?.let { currentId ->
            LocalPersistenceService.saveFollowers(currentId, followers)
            LocalPersistenceService.saveMutuals(currentId, mutuals)
        }
    }
    fun refreshVisits() { FirebaseAuth.getInstance().currentUser?.uid?.let { id -> viewModelScope.launch { isLoadingVisits = true; groupedVisits = ProfileVisitsService.fetchGroupedVisits(id); visits = groupedVisits.map { it.user }; visitTimestamps = groupedVisits.associate { it.user.id to it.visits.map { visit -> visit.timestamp } }; isLoadingVisits = false } } }
    fun updateProfileDetails(bio: String?, websiteUrl: String?, interests: List<String>? = null) { val id = FirebaseAuth.getInstance().currentUser?.uid ?: return; viewModelScope.launch { runCatching { LocalPersistenceService.updateProfile(id, bio, userProfile?.bio, websiteUrl, userProfile?.websiteUrl, interests); fetchProfile(id) }.onFailure { errorMessage = it.message } } }
    fun updateBio(newBio: String) = updateProfileDetails(newBio, null)
    fun uploadProfilePicture(context: Context, uri: Uri) { val id = FirebaseAuth.getInstance().currentUser?.uid ?: return; viewModelScope.launch { runCatching { val file = File(context.filesDir, "profile_${System.currentTimeMillis()}.jpg"); context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use(input::copyTo) } ?: error("profile_image_read_failed"); LocalPersistenceService.updateProfile(id, bio = null, website = null, interests = null, profileImageLocalPath = file.path); fetchProfile(id) }.onFailure { errorMessage = it.message } } }
    fun updateProfileNote(note: String) { val id = FirebaseAuth.getInstance().currentUser?.uid ?: return; viewModelScope.launch { runCatching { firestoreService.updateProfileNote(id, note.trim().take(80)); userProfile = userProfile?.copy(profileNote = note.trim().take(80).ifEmpty { null }) }.onFailure { errorMessage = it.message } } }
    override fun cancelFollowRequest(userId: String) { FirebaseAuth.getInstance().currentUser?.uid?.let { current -> viewModelScope.launch { runCatching { firestoreService.cancelFollowRequest(current, userId); FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, userId) }.onFailure { errorMessage = it.message } } } }
    override fun relationshipState(userId: String): FollowButtonState = when { FirebaseAuth.getInstance().currentUser?.uid == userId -> FollowButtonState.OWN_PROFILE; following.any { it.id == userId } || mutuals.any { it.id == userId } -> FollowButtonState.FOLLOWING; followers.firstOrNull { it.id == userId }?.isPrivate == true -> FollowButtonState.CAN_REQUEST_FOLLOW; else -> FollowButtonState.CAN_FOLLOW }
    override fun prefetchRelationshipState(userId: String) { val current = FirebaseAuth.getInstance().currentUser?.uid ?: return; if (current == userId) return; viewModelScope.launch { if (FollowStateStore.state(userId) == null) { val state = PrivacyService.getFollowButtonState(current, userId); FollowStateStore.setState(FollowStateStore.reconciledState(state, userId), userId) } } }
    fun verifyFollowingStatus(userId: String, completion: (Boolean) -> Unit) { val current = FirebaseAuth.getInstance().currentUser?.uid ?: return completion(false); viewModelScope.launch { completion(runCatching { firestoreService.isFollowing(current, userId) }.getOrDefault(false)) } }
    private fun persistMoments() { userProfile?.id?.let { LocalPersistenceService.saveProfileMoments(moments, it, sync = true) } }
    private fun sortProfileMoments(values: List<Moment>) = values.sortedWith(compareByDescending<Moment> { it.isPinned == true }.thenByDescending { it.pinnedAt ?: it.timestamp })
}
