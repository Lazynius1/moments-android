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
    var following by mutableStateOf<List<AppUser>>(emptyList()); private set
    var followers by mutableStateOf<List<AppUser>>(emptyList()); private set
    var mutuals by mutableStateOf<List<AppUser>>(emptyList()); private set
    var moments by mutableStateOf<List<Moment>>(emptyList()); private set
    var taggedMoments by mutableStateOf<List<Moment>>(emptyList()); private set
    var customListNamesById by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var isLoading by mutableStateOf(true); var isLoadingMoments by mutableStateOf(true); var isLoadingTagged by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false); var isOffline by mutableStateOf(false); var errorMessage by mutableStateOf<String?>(null)
    var visits by mutableStateOf<List<AppUser>>(emptyList()); var groupedVisits by mutableStateOf<List<com.moments.android.models.GroupedVisit>>(emptyList()); var visitTimestamps by mutableStateOf<Map<String, List<Date>>>(emptyMap()); var isLoadingVisits by mutableStateOf(false)

    fun fetchProfile(userId: String) { viewModelScope.launch {
        isLoading = true; errorMessage = null
        LocalPersistenceService.loadUser(userId)?.let { userProfile = it; isLoading = false }
        LocalPersistenceService.loadProfileMoments(userId).takeIf { it.isNotEmpty() }?.let { moments = sortProfileMoments(it) }
        runCatching {
            val profile = firestoreService.fetchUser(userId); userProfile = profile; LocalPersistenceService.saveUser(profile)
            val result = listOf(async { firestoreService.fetchFollowing(userId) }, async { firestoreService.fetchFollowers(userId) }, async { firestoreService.fetchMutuals(userId) }, async { firestoreService.fetchMoments(userId) }, async { firestoreService.fetchCustomLists(userId) })
            following = result[0].await() as List<AppUser>; followers = result[1].await() as List<AppUser>; mutuals = result[2].await() as List<AppUser>
            moments = sortProfileMoments(result[3].await() as List<Moment>); customListNamesById = (result[4].await() as List<com.moments.android.models.CustomAudienceList>).mapNotNull { it.id?.let { id -> id to it.name } }.toMap()
            LocalPersistenceService.saveFollowing(userId, following); LocalPersistenceService.saveFollowers(userId, followers); LocalPersistenceService.saveMutuals(userId, mutuals); LocalPersistenceService.saveProfileMoments(moments, userId, sync = true); refreshVisits()
        }.onFailure { isOffline = true; errorMessage = it.message }.also { isLoading = false; isLoadingMoments = false }
    } }
    fun fetchTaggedMoments(userId: String) { viewModelScope.launch { isLoadingTagged = true; taggedMoments = BackendFeedService.fetchTaggedMoments(userId, limit = 50)?.moments.orEmpty(); isLoadingTagged = false } }
    fun refreshProfile() { FirebaseAuth.getInstance().currentUser?.uid?.let(::fetchProfile) }
    fun oldestPinnedMomentId(excluding: String? = null): String? = moments.filter { it.isPinned == true && it.id != excluding }.minByOrNull { it.pinnedAt ?: it.timestamp }?.id
    fun applyMomentPinState(momentId: String, isPinned: Boolean, pinnedAt: Date) { moments = sortProfileMoments(moments.map { if (it.id == momentId) it.copy(isPinned = isPinned.takeIf { it }, pinnedAt = pinnedAt.takeIf { isPinned }) else it }); persistMoments() }
    fun applyPinReplacement(unpinningMomentId: String, pinningMomentId: String, pinnedAt: Date) { moments = sortProfileMoments(moments.map { when (it.id) { unpinningMomentId -> it.copy(isPinned = null, pinnedAt = null); pinningMomentId -> it.copy(isPinned = true, pinnedAt = pinnedAt); else -> it } }); persistMoments() }
    fun applyGridPreview(momentId: String, settings: MomentGridPreviewSettings) { moments = moments.map { if (it.id == momentId) it.copy(gridPreviewScale = settings.scale.takeUnless { settings.isDefault }, gridPreviewOffsetX = settings.offsetX.takeUnless { settings.isDefault }, gridPreviewOffsetY = settings.offsetY.takeUnless { settings.isDefault }, gridPreviewFitMode = settings.fitMode.raw.takeUnless { settings.isDefault }, gridPreviewBackground = settings.background.raw.takeUnless { settings.isDefault }) else it }; persistMoments() }
    override fun followUser(userId: String) { FirebaseAuth.getInstance().currentUser?.uid?.let { currentId -> viewModelScope.launch { runCatching { firestoreService.followUser(currentId, userId); following = firestoreService.fetchFollowing(currentId); mutuals = firestoreService.fetchMutuals(currentId) }.onFailure { errorMessage = it.message } } } }
    override fun unfollowUser(userId: String) { FirebaseAuth.getInstance().currentUser?.uid?.let { currentId -> viewModelScope.launch { runCatching { firestoreService.unfollowUser(currentId, userId); following = following.filterNot { it.id == userId }; mutuals = mutuals.filterNot { it.id == userId } }.onFailure { errorMessage = it.message } } } }
    fun removeFollower(userId: String) { followers = followers.filterNot { it.id == userId }; mutuals = mutuals.filterNot { it.id == userId } }
    fun refreshVisits() { FirebaseAuth.getInstance().currentUser?.uid?.let { id -> viewModelScope.launch { isLoadingVisits = true; groupedVisits = ProfileVisitsService.fetchGroupedVisits(id); visits = groupedVisits.map { it.user }; visitTimestamps = groupedVisits.associate { it.user.id to it.visits.map { visit -> visit.timestamp } }; isLoadingVisits = false } } }
    fun updateProfileDetails(bio: String?, websiteUrl: String?, interests: List<String>? = null) { val id = FirebaseAuth.getInstance().currentUser?.uid ?: return; viewModelScope.launch { runCatching { LocalPersistenceService.updateProfile(id, bio, userProfile?.bio, websiteUrl, userProfile?.websiteUrl, interests); fetchProfile(id) }.onFailure { errorMessage = it.message } } }
    fun updateBio(newBio: String) = updateProfileDetails(newBio, null)
    fun uploadProfilePicture(context: Context, uri: Uri) { val id = FirebaseAuth.getInstance().currentUser?.uid ?: return; viewModelScope.launch { runCatching { val file = File(context.filesDir, "profile_${System.currentTimeMillis()}.jpg"); context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use(input::copyTo) } ?: error("profile_image_read_failed"); LocalPersistenceService.updateProfile(id, bio = null, website = null, interests = null, profileImageLocalPath = file.path); fetchProfile(id) }.onFailure { errorMessage = it.message } } }
    fun updateProfileNote(note: String) { val id = FirebaseAuth.getInstance().currentUser?.uid ?: return; viewModelScope.launch { runCatching { firestoreService.updateProfileNote(id, note.trim().take(80)); userProfile = userProfile?.copy(profileNote = note.trim().take(80).ifEmpty { null }) }.onFailure { errorMessage = it.message } } }
    override fun cancelFollowRequest(userId: String) { FirebaseAuth.getInstance().currentUser?.uid?.let { current -> viewModelScope.launch { runCatching { firestoreService.cancelFollowRequest(current, userId); FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, userId) }.onFailure { errorMessage = it.message } } } }
    override fun relationshipState(userId: String): FollowButtonState = when { FirebaseAuth.getInstance().currentUser?.uid == userId -> FollowButtonState.OWN_PROFILE; following.any { it.id == userId } || mutuals.any { it.id == userId } -> FollowButtonState.FOLLOWING; followers.firstOrNull { it.id == userId }?.isPrivate == true -> FollowButtonState.CAN_REQUEST_FOLLOW; else -> FollowButtonState.CAN_FOLLOW }
    override fun prefetchRelationshipState(userId: String) { val current = FirebaseAuth.getInstance().currentUser?.uid ?: return; viewModelScope.launch { PrivacyService.getFollowButtonState(current, userId).also { FollowStateStore.setState(it, userId) } } }
    fun verifyFollowingStatus(userId: String, completion: (Boolean) -> Unit) { val current = FirebaseAuth.getInstance().currentUser?.uid ?: return completion(false); viewModelScope.launch { completion(runCatching { firestoreService.isFollowing(current, userId) }.getOrDefault(false)) } }
    private fun persistMoments() { userProfile?.id?.let { LocalPersistenceService.saveProfileMoments(moments, it, sync = true) } }
    private fun sortProfileMoments(values: List<Moment>) = values.sortedWith(compareByDescending<Moment> { it.isPinned == true }.thenByDescending { it.pinnedAt ?: it.timestamp })
}
