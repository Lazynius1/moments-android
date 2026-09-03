package com.moments.android.views.profile.core

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.moments.android.models.AppUser
import com.moments.android.models.GroupedVisit
import com.moments.android.models.Moment
import com.moments.android.models.MomentGridPreviewSettings
import com.moments.android.services.content.BackendFeedService
import com.moments.android.services.content.ProfileVisitsService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.archiveMoment
import com.moments.android.services.firestore.deleteMoment
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchMoments
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.firestore.pinMoment
import com.moments.android.services.firestore.pinMomentReplacingOldestIfNeeded
import com.moments.android.services.firestore.unpinMoment
import com.moments.android.services.firestore.updateMomentGridPreview
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.core.EditMomentPayload
import com.moments.android.views.profile.core.sections.ProfileAvatarNoteMetrics
import com.moments.android.widget.MomentsWidgetStore
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

/** Port de `ProfileViewModel.swift`. */
class ProfileViewModel(
    private val firestoreService: FirestoreService = FirestoreService(),
) : ViewModel(), UserListViewModel {
    var userProfile by mutableStateOf<AppUser?>(null); private set
    var profileImagePath by mutableStateOf<String?>(null); private set
    var following by mutableStateOf<List<AppUser>>(emptyList()); private set
    var followers by mutableStateOf<List<AppUser>>(emptyList()); private set
    var mutuals by mutableStateOf<List<AppUser>>(emptyList()); private set
    /**
     * [Moment.equals] solo compara `id` (paridad iOS Equatable). Con la policy
     * estructural por defecto, `moments = map { copy(audience=…) }` no notifica
     * (la lista “sigue igual”) y el editor reabre con datos viejos aunque Firestore
     * ya tenga el cambio. iOS `@Published` siempre emite al asignar → neverEqual.
     */
    var moments by mutableStateOf(emptyList<Moment>(), neverEqualPolicy()); private set
    var taggedMoments by mutableStateOf(emptyList<Moment>(), neverEqualPolicy()); private set
    var customListNamesById by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var isLoading by mutableStateOf(true)
    var isLoadingMoments by mutableStateOf(true)
    var isLoadingTagged by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var isOffline by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var visits by mutableStateOf<List<AppUser>>(emptyList())
    var groupedVisits by mutableStateOf<List<GroupedVisit>>(emptyList())
    var visitTimestamps by mutableStateOf<Map<String, List<Date>>>(emptyMap())
    var isLoadingVisits by mutableStateOf(false)

    private val recentUnfollows = mutableSetOf<String>()
    private val lastUnfollowTime = mutableMapOf<String, Date>()
    private val db = FirebaseFirestore.getInstance()
    /** Nota en vuelo: evita que un fetch stale pise el optimistic update. */
    private var pendingProfileNote: String? = null
    private var lastProfileNoteWriteAt: Long = 0L

    fun fetchProfile(userId: String) {
        isLoading = true
        errorMessage = null

        // ≡ SwiftData: pintar caché sin esperar a Firestore.
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
        // iOS: solo aplica caché de moments si la lista en memoria está vacía.
        if (moments.isEmpty()) {
            LocalPersistenceService.loadProfileMoments(userId).takeIf { it.isNotEmpty() }?.let {
                moments = sortProfileMoments(it)
            }
        }

        viewModelScope.launch {
            // Moments independientes del doc de perfil (como iOS `fetchMoments`).
            val momentsJob = async { runCatching { firestoreService.fetchMoments(userId) } }
            val profileResult = runCatching { firestoreService.fetchUser(userId) }

            profileResult.onSuccess { profile ->
                isOffline = false
                applyFetchedProfile(profile)
                fetchConnections(userId)
                fetchVisits(userId)
                fetchCustomAudienceListNames(userId)
            }.onFailure { error ->
                if (isNetworkError(error)) {
                    isOffline = true
                } else if (userProfile == null) {
                    errorMessage = error.message
                }
                isLoading = false
            }

            momentsJob.await().onSuccess { fetched ->
                moments = sortProfileMoments(fetched)
                LocalPersistenceService.saveProfileMoments(moments, userId, sync = true)
            }.onFailure { error ->
                if (!isNetworkError(error) && moments.isEmpty()) {
                    errorMessage = error.message
                }
            }
            isLoadingMoments = false
        }
    }

    private fun isNetworkError(error: Throwable): Boolean {
        if (error is IOException || error.cause is IOException) return true
        val firestore = error as? FirebaseFirestoreException
            ?: error.cause as? FirebaseFirestoreException
            ?: return false
        return firestore.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            firestore.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }

    private suspend fun fetchConnections(userId: String) {
        runCatching {
            coroutineScope {
                val followingRequest = async { firestoreService.fetchFollowing(userId) }
                val followersRequest = async { firestoreService.fetchFollowers(userId) }
                val mutualsRequest = async { firestoreService.fetchMutuals(userId) }
                val now = Date()
                val followingUsers = followingRequest.await().filter { user ->
                    val unfollowedAt = lastUnfollowTime[user.id] ?: return@filter true
                    if (now.time - unfollowedAt.time < 5_000L) {
                        false
                    } else {
                        lastUnfollowTime.remove(user.id)
                        recentUnfollows.remove(user.id)
                        true
                    }
                }
                applyConnectionSnapshots(
                    userId,
                    followingUsers,
                    followersRequest.await(),
                    mutualsRequest.await(),
                )
            }
        }.onFailure {
            errorMessage = it.message
            isLoading = false
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

    fun refreshVisits() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch { fetchVisits(userId) }
    }

    private suspend fun fetchVisits(userId: String) {
        isLoadingVisits = true
        try {
            val grouped = ProfileVisitsService.fetchGroupedVisits(userId)
            groupedVisits = grouped
            visits = grouped.map { it.user }
            visitTimestamps = grouped.associate { it.user.id to it.visits.map { visit -> visit.timestamp } }
            val cal = Calendar.getInstance()
            val todayStart = cal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            val todayCount = grouped.sumOf { group ->
                group.visits.count { visit ->
                    val day = Calendar.getInstance().apply {
                        time = visit.timestamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                    day == todayStart
                }
            }
            MomentsWidgetStore.putInt(MomentsWidgetStore.KEY_PROFILE_VISITS_TODAY, todayCount)
        } finally {
            isLoadingVisits = false
        }
    }

    private suspend fun fetchCustomAudienceListNames(userId: String) {
        runCatching {
            customListNamesById = firestoreService.fetchCustomLists(userId)
                .mapNotNull { list -> list.id?.let { it to list.name } }
                .toMap()
        }
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
            delay(500) // delay mínimo para que Firestore procese cambios recientes
            performRefresh(userId)
        }
    }

    /** ≡ `performRefresh` — sin poner `isLoading` (evita skeleton en pull-to-refresh). */
    private suspend fun performRefresh(userId: String) {
        var hasErrors = false
        coroutineScope {
            val profileJob = async {
                runCatching { firestoreService.fetchUser(userId) }
                    .onSuccess { applyFetchedProfile(it) }
                    .onFailure {
                        hasErrors = true
                        errorMessage = it.message
                    }
            }
            val connectionsJob = async { fetchConnections(userId) }
            val visitsJob = async { fetchVisits(userId) }
            val momentsJob = async {
                runCatching { firestoreService.fetchMoments(userId) }
                    .onSuccess {
                        moments = sortProfileMoments(it)
                        LocalPersistenceService.saveProfileMoments(moments, userId, sync = true)
                    }
                    .onFailure { if (!isNetworkError(it)) hasErrors = true }
            }
            val listsJob = async { fetchCustomAudienceListNames(userId) }
            listOf(profileJob, connectionsJob, visitsJob, momentsJob, listsJob).awaitAll()
        }
        isRefreshing = false
        isLoadingMoments = false
        if (!hasErrors) HapticManager.shared.lightImpact()
    }

    fun oldestPinnedMomentId(excluding: String? = null): String? =
        moments
            .filter { it.isPinned == true && it.id != excluding }
            .minByOrNull { it.pinnedAt ?: it.timestamp }
            ?.id

    fun applyMomentPinState(momentId: String, isPinned: Boolean, pinnedAt: Date) {
        moments = sortProfileMoments(
            moments.map {
                if (it.id == momentId) {
                    it.copy(
                        isPinned = isPinned.takeIf { pinned -> pinned },
                        pinnedAt = pinnedAt.takeIf { isPinned },
                    )
                } else {
                    it
                }
            },
        )
        persistMoments()
    }

    fun applyPinReplacement(unpinningMomentId: String, pinningMomentId: String, pinnedAt: Date) {
        moments = sortProfileMoments(
            moments.map {
                when (it.id) {
                    unpinningMomentId -> it.copy(isPinned = null, pinnedAt = null)
                    pinningMomentId -> it.copy(isPinned = true, pinnedAt = pinnedAt)
                    else -> it
                }
            },
        )
        persistMoments()
    }

    fun applyGridPreview(momentId: String, settings: MomentGridPreviewSettings) {
        moments = moments.map {
            if (it.id != momentId) {
                it
            } else {
                it.copy(
                    gridPreviewScale = settings.scale.takeUnless { settings.isDefault },
                    gridPreviewOffsetX = settings.offsetX.takeUnless { settings.isDefault },
                    gridPreviewOffsetY = settings.offsetY.takeUnless { settings.isDefault },
                    gridPreviewFitMode = settings.fitMode.raw.takeUnless { settings.isDefault },
                    gridPreviewBackground = settings.background.raw.takeUnless { settings.isDefault },
                )
            }
        }
        persistMoments()
    }

    /** Paridad post-save de EditMoment: actualizar lista local ya (no esperar refresh). */
    fun applyMomentEdit(momentId: String, payload: EditMomentPayload) {
        val coord = if (payload.locationLatitude != null && payload.locationLongitude != null) {
            Moment.LocationCoordinate(payload.locationLatitude, payload.locationLongitude)
        } else {
            null
        }
        moments = sortProfileMoments(
            moments.map {
                if (it.id != momentId) {
                    it
                } else {
                    it.copy(
                        content = payload.content,
                        audience = payload.audience,
                        customListId = payload.customListId,
                        taggedUsers = payload.taggedUsers.ifEmpty { null },
                        mentionedUsers = payload.mentionedUsers.ifEmpty { null },
                        location = payload.locationName.ifBlank { null },
                        locationCoordinate = coord,
                        mediaItems = payload.mediaItems ?: it.mediaItems,
                    )
                }
            },
        )
        persistMoments()
    }

    fun handleGridPin(moment: Moment, shouldPin: Boolean, replaceOldest: Boolean) {
        val momentId = moment.id ?: return
        val pinnedAt = Date()
        viewModelScope.launch {
            runCatching {
                if (shouldPin) {
                    if (replaceOldest) {
                        firestoreService.pinMomentReplacingOldestIfNeeded(moment.authorId, momentId, moments)
                        val oldestId = oldestPinnedMomentId(excluding = momentId)
                        if (oldestId != null) {
                            applyPinReplacement(oldestId, momentId, pinnedAt)
                        } else {
                            applyMomentPinState(momentId, isPinned = true, pinnedAt = pinnedAt)
                        }
                    } else {
                        firestoreService.pinMoment(moment.authorId, momentId)
                        applyMomentPinState(momentId, isPinned = true, pinnedAt = pinnedAt)
                    }
                } else {
                    firestoreService.unpinMoment(moment.authorId, momentId)
                    applyMomentPinState(momentId, isPinned = false, pinnedAt = pinnedAt)
                }
            }.onFailure { errorMessage = it.message }
        }
    }

    fun archiveMomentLocally(moment: Moment) {
        val momentId = moment.id ?: return
        viewModelScope.launch {
            runCatching {
                firestoreService.archiveMoment(moment.authorId, momentId)
                moments = moments.filterNot { it.id == momentId }
                persistMoments()
            }.onFailure { errorMessage = it.message }
        }
    }

    fun deleteMomentLocally(moment: Moment) {
        val momentId = moment.id ?: return
        viewModelScope.launch {
            runCatching {
                firestoreService.deleteMoment(moment.authorId, momentId)
                moments = moments.filterNot { it.id == momentId }
                LocalPersistenceService.deleteMoment(momentId)
            }.onFailure { errorMessage = it.message }
        }
    }

    override fun followUser(userId: String) {
        val currentId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            errorMessage = "Usuario no autenticado. Por favor, inicia sesión."
            return
        }
        recentUnfollows.remove(userId)
        lastUnfollowTime.remove(userId)
        viewModelScope.launch {
            runCatching {
                val target = firestoreService.fetchUser(userId)
                firestoreService.followUser(currentId, userId)
                val next = if (target.isPrivate) {
                    FollowButtonState.REQUEST_PENDING_CANCELLABLE
                } else {
                    FollowButtonState.FOLLOWING
                }
                FollowStateStore.setState(next, userId)
                HapticManager.shared.mediumImpact()
                if (target.isPrivate) return@runCatching
                followers.firstOrNull { it.id == userId }?.let { follower ->
                    if (mutuals.none { it.id == userId }) mutuals = mutuals + follower
                }
                if (following.none { it.id == userId }) {
                    following = following + target
                }
            }.onFailure { errorMessage = it.message }
        }
    }

    override fun unfollowUser(userId: String) {
        val currentId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            errorMessage = "Usuario no autenticado."
            return
        }
        recentUnfollows += userId
        lastUnfollowTime[userId] = Date()
        viewModelScope.launch {
            runCatching {
                firestoreService.unfollowUser(currentId, userId)
                HapticManager.shared.lightImpact()
                mutuals = mutuals.filterNot { it.id == userId }
                following = following.filterNot { it.id == userId }
                val known = followers.firstOrNull { it.id == userId }
                FollowStateStore.setState(
                    if (known?.isPrivate == true) FollowButtonState.CAN_REQUEST_FOLLOW else FollowButtonState.CAN_FOLLOW,
                    userId,
                )
            }.onFailure {
                recentUnfollows -= userId
                lastUnfollowTime.remove(userId)
                errorMessage = it.message
            }
        }
    }

    override fun cancelFollowRequest(userId: String) {
        val current = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                firestoreService.cancelFollowRequest(current, userId)
                FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, userId)
            }.onFailure { errorMessage = it.message }
        }
    }

    /** ≡ iOS batch delete `users/{me}/followers/{userId}`. */
    fun removeFollower(userId: String) {
        val currentId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            errorMessage = "Usuario no autenticado."
            return
        }
        viewModelScope.launch {
            runCatching {
                db.collection("users").document(currentId)
                    .collection("followers").document(userId)
                    .delete().await()
                followers = followers.filterNot { it.id == userId }
                mutuals = mutuals.filterNot { it.id == userId }
                LocalPersistenceService.saveFollowers(currentId, followers)
                LocalPersistenceService.saveMutuals(currentId, mutuals)
            }.onFailure { errorMessage = it.message }
        }
    }

    override fun relationshipState(userId: String): FollowButtonState {
        if (FirebaseAuth.getInstance().currentUser?.uid == userId) return FollowButtonState.OWN_PROFILE
        if (mutuals.any { it.id == userId }) return FollowButtonState.MUTUALS
        if (following.any { it.id == userId }) return FollowButtonState.FOLLOWING
        FollowStateStore.state(userId)?.let { return it }
        val known = followers.firstOrNull { it.id == userId }
            ?: following.firstOrNull { it.id == userId }
            ?: mutuals.firstOrNull { it.id == userId }
        return if (known?.isPrivate == true) {
            FollowButtonState.CAN_REQUEST_FOLLOW
        } else {
            FollowButtonState.CAN_FOLLOW
        }
    }

    override fun prefetchRelationshipState(userId: String) {
        val current = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (current == userId) return
        if (mutuals.any { it.id == userId }) {
            FollowStateStore.setState(FollowButtonState.MUTUALS, userId)
            return
        }
        viewModelScope.launch {
            val state = PrivacyService.getFollowButtonState(current, userId)
            FollowStateStore.setState(FollowStateStore.reconciledState(state, userId), userId)
        }
    }

    fun verifyFollowingStatus(userId: String, completion: (Boolean) -> Unit) {
        val current = FirebaseAuth.getInstance().currentUser?.uid ?: return completion(false)
        viewModelScope.launch {
            completion(runCatching { firestoreService.isFollowing(current, userId) }.getOrDefault(false))
        }
    }

    fun updateProfileDetails(bio: String?, websiteUrl: String?, interests: List<String>? = null) {
        val id = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            errorMessage = "Usuario no autenticado. Por favor, inicia sesión."
            return
        }
        viewModelScope.launch {
            runCatching {
                LocalPersistenceService.updateProfile(
                    id,
                    bio,
                    userProfile?.bio,
                    websiteUrl,
                    userProfile?.websiteUrl,
                    interests,
                )
                fetchProfile(id)
            }.onFailure { errorMessage = it.message }
        }
    }

    fun updateBio(newBio: String) = updateProfileDetails(newBio, null)

    fun uploadProfilePicture(context: Context, uri: Uri) {
        val id = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            errorMessage = "Usuario no autenticado. Por favor, inicia sesión."
            return
        }
        viewModelScope.launch {
            runCatching {
                val file = File(context.filesDir, "temp_profile_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use(input::copyTo)
                } ?: error("profile_image_read_failed")
                LocalPersistenceService.updateProfile(
                    id,
                    bio = null,
                    website = null,
                    interests = null,
                    profileImageLocalPath = file.path,
                )
                fetchProfile(id)
            }.onFailure { errorMessage = it.message }
        }
    }

    fun updateProfileNote(note: String) {
        val id = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val trimmed = note.take(ProfileAvatarNoteMetrics.maxLength).trim()
        val now = android.os.SystemClock.elapsedRealtime()
        // Evitar 2º save vacío (IME Done+blur) que pisa el optimistic update.
        if (trimmed.isEmpty() &&
            !userProfile?.profileNote.isNullOrEmpty() &&
            now - lastProfileNoteWriteAt < 800L
        ) {
            return
        }
        lastProfileNoteWriteAt = now
        pendingProfileNote = trimmed
        val updated = userProfile?.copy(profileNote = trimmed.ifEmpty { null }) ?: return
        userProfile = updated
        LocalPersistenceService.saveUser(updated)
        viewModelScope.launch {
            runCatching {
                firestoreService.updateProfileNote(id, trimmed)
            }.onSuccess {
                if (pendingProfileNote == trimmed) pendingProfileNote = null
            }.onFailure {
                pendingProfileNote = null
                fetchProfile(id)
            }
        }
    }

    /** Aplica fetch sin pisar una nota optimistic pendiente de confirmar en Firestore. */
    private fun applyFetchedProfile(profile: AppUser) {
        val pending = pendingProfileNote
        val merged = if (pending != null) {
            profile.copy(profileNote = pending.ifEmpty { null })
        } else {
            profile
        }
        userProfile = merged
        profileImagePath = merged.profileImagePath
        LocalPersistenceService.saveUser(merged)
    }

    fun saveGridPreview(moment: Moment, settings: MomentGridPreviewSettings) {
        val momentId = moment.id ?: return
        val previous = moment.gridPreviewSettings
        applyGridPreview(momentId, settings)
        viewModelScope.launch {
            runCatching {
                firestoreService.updateMomentGridPreview(moment.authorId, momentId, settings)
            }.onFailure {
                applyGridPreview(momentId, previous)
            }
        }
    }

    private fun persistMoments() {
        userProfile?.id?.let { LocalPersistenceService.saveProfileMoments(moments, it, sync = true) }
    }

    private fun sortProfileMoments(values: List<Moment>): List<Moment> =
        values.sortedWith(
            compareByDescending<Moment> { it.isPinned == true }
                .thenByDescending { it.pinnedAt ?: it.timestamp }
                .thenByDescending { it.timestamp },
        )
}
