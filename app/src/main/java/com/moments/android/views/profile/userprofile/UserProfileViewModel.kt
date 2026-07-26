package com.moments.android.views.profile.userprofile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldValue
import com.moments.android.MomentsApplication
import com.moments.android.models.AppUser
import com.moments.android.models.CustomAudienceList
import com.moments.android.models.Moment
import com.moments.android.services.content.BackendFeedService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.PublicProfileAvailability
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchMomentsWithVisibility
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.firestore.fetchUserProfileWithAvailability
import com.moments.android.services.firestore.fetchUsersWithSharedInterests
import com.moments.android.services.firestore.registerVisit
import com.moments.android.services.firestore.removeMembersFromCustomList
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.VisibleConnectionTypes
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.social.BestFriendsService
import com.moments.android.views.profile.core.UserListViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `UserProfileViewModel.swift` — perfil de otro usuario visto desde Feed/Explore.
 *
 * Traducción de patrones: `DispatchGroup` → `coroutineScope { async {} }`; callbacks de servicio →
 * `suspend fun`. Puentes conscientes respecto a iOS: (1) sin el observer de `NotificationCenter`
 * (FollowStateStore no expone flujo aquí; se reconcilia en cada acción); (2) las hápticas son de la
 * capa de UI, no del VM; (3) `checkIfBestFriend`/`canUserViewMomentEnhanced` se cubren con
 * primitivas ya existentes (bestFriends del viewer / `fetchMomentsWithVisibility`).
 */
class UserProfileViewModel(
    val userId: String,
    private val firestoreService: FirestoreService = FirestoreService(),
    private val bestFriendsService: BestFriendsService = BestFriendsService(),
) : ViewModel(), UserListViewModel {

    var userProfile by mutableStateOf<AppUser?>(null); private set
    var viewerProfile by mutableStateOf<AppUser?>(null); private set
    var following by mutableStateOf<List<AppUser>>(emptyList()); private set
    var mutuals by mutableStateOf<List<AppUser>>(emptyList()); private set
    var followers by mutableStateOf<List<AppUser>>(emptyList()); private set
    var commonConnections by mutableStateOf<List<AppUser>>(emptyList()); private set
    var suggestedConnectionsForViewer by mutableStateOf<List<AppUser>>(emptyList()); private set
    var moments by mutableStateOf<List<Moment>>(emptyList()); private set
    var isLoadingMoments by mutableStateOf(true); private set
    var taggedMoments by mutableStateOf<List<Moment>>(emptyList()); private set
    var isLoadingTagged by mutableStateOf(false); private set
    var isFollowing by mutableStateOf(false); private set
    var isBlockedByCurrentUser by mutableStateOf(false); private set
    var isCurrentUserBlocked by mutableStateOf(false); private set
    var isLoading by mutableStateOf(true); private set
    var followButtonState by mutableStateOf(FollowButtonState.CAN_FOLLOW); private set
    var canViewContent by mutableStateOf(false); private set
    var canViewSocialLists by mutableStateOf(false); private set
    var isRefreshing by mutableStateOf(false); private set
    var isProfileUnavailable by mutableStateOf(false); private set
    var isOffline by mutableStateOf(false); private set
    var isInBestFriends by mutableStateOf(false); private set
    var isMutedByCurrentUser by mutableStateOf(false); private set
    var isMutualRelationship by mutableStateOf(false); private set
    var customListMembershipCount by mutableStateOf(0); private set
    var customListsContainingProfile by mutableStateOf<List<CustomAudienceList>>(emptyList()); private set
    var isUpdatingBestFriend by mutableStateOf(false); private set
    var isUpdatingMute by mutableStateOf(false); private set
    var isUpdatingLists by mutableStateOf(false); private set
    var viewerInterests by mutableStateOf<List<String>>(emptyList()); private set
    var visibleConnectionTypes by mutableStateOf(
        VisibleConnectionTypes(canViewFollowers = false, canViewFollowing = false, canViewMutuals = false),
    ); private set

    private var viewerNetworkIds: Set<String> = emptySet()
    private var viewerFollowingIds: Set<String> = emptySet()
    private var viewerFollowerIds: Set<String> = emptySet()
    private var viewerBlockedUserIds: Set<String> = emptySet()
    private var targetVisibleFollowingIds: Set<String> = emptySet()
    private var targetVisibleFollowerIds: Set<String> = emptySet()
    private var lastSuggestionsSignature: String? = null
    private val recentUnfollows = mutableSetOf<String>()

    private val currentUserId: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    private val db get() = firestoreService.db

    /** Conveniencia para la UI (iOS lo calcula inline como `currentUserId == userId`). */
    val isOwnProfile: Boolean get() = currentUserId == userId

    private val prefs
        get() = MomentsApplication.instance?.getSharedPreferences("user_profile_vm", Context.MODE_PRIVATE)

    // MARK: - Carga principal

    fun fetchProfile() {
        val current = currentUserId ?: run { isLoading = false; return }
        isLoading = true
        isProfileUnavailable = false

        viewModelScope.launch {
            loadViewerContext(current)

            // Restaurar la última decisión de privacidad conocida (no caer en "privado" sin red).
            cachedCanViewContent(current)?.let { canViewContent = it }

            // Caché local: perfil y moments (evita el flash a estado vacío sin red).
            LocalPersistenceService.loadUser(userId)?.let { cached ->
                if (cached.isActive) {
                    userProfile = cached
                    isLoading = false
                } else {
                    userProfile = null
                    canViewContent = false
                    isProfileUnavailable = true
                    isLoading = false
                }
            }
            val cachedMoments = LocalPersistenceService.loadProfileMoments(userId, current)
            if (cachedMoments.isNotEmpty() && moments.isEmpty()) moments = cachedMoments

            val (cachedFollowers, cachedFollowing, cachedMutuals) = LocalPersistenceService.loadConnections(userId)
            if (cachedFollowers.isNotEmpty() || cachedFollowing.isNotEmpty() || cachedMutuals.isNotEmpty()) {
                categorizeConnectionsWithPrivacy(cachedFollowing, cachedFollowers, cachedMutuals)
            }

            checkIfBlocked()

            runCatching { firestoreService.fetchUserProfileWithAvailability(userId) }
                .onSuccess { (profile, availability) ->
                    if (availability == PublicProfileAvailability.UNAVAILABLE) {
                        userProfile = null
                        canViewContent = false
                        isProfileUnavailable = true
                        isLoading = false
                        return@onSuccess
                    }
                    if (!profile.isActive) {
                        userProfile = null
                        canViewContent = false
                        isProfileUnavailable = true
                        isLoading = false
                        return@onSuccess
                    }
                    isProfileUnavailable = false
                    userProfile = profile
                    refreshMutedUserIds()
                    checkContentVisibility(current)
                }
                .onFailure {
                    // Sin red: mantener lo cacheado, no marcar como privado/no disponible.
                    isOffline = true
                    isLoading = false
                }
        }
    }

    // MARK: - Refresh

    fun refreshProfile() {
        val current = currentUserId ?: return
        if (isRefreshing || isLoading) return
        isRefreshing = true
        viewModelScope.launch {
            delay(500) // deja a Firestore procesar cambios recientes
            coroutineScope {
                val profileJob = async { runCatching { firestoreService.fetchUser(userId) }.getOrNull() }
                checkConnectionsVisibility(current)
                fetchConnectionsDirect()
                val momentsJob = async { fetchMoments() }
                val taggedJob = async { fetchTaggedMoments() }
                profileJob.await()?.let { userProfile = it }
                momentsJob.await()
                taggedJob.await()
            }
            isRefreshing = false
        }
    }

    // MARK: - Visibilidad

    private suspend fun checkContentVisibility(current: String) {
        if (current == userId) {
            canViewContent = true
            persistCanViewContent(true, current)
            checkConnectionsVisibility(current)
            fetchConnectionsDirect()
            return
        }
        val canView = runCatching { PrivacyService.canViewUserContent(current, userId) }.getOrDefault(false)
        canViewContent = canView
        persistCanViewContent(canView, current)
        if (canView) {
            checkConnectionsVisibility(current)
            fetchConnectionsDirect()
        } else {
            isLoading = false
        }
    }

    private suspend fun checkConnectionsVisibility(current: String) {
        val types = runCatching { PrivacyService.getVisibleConnectionTypes(current, userId) }
            .getOrDefault(VisibleConnectionTypes(false, false, false))
        visibleConnectionTypes = types
        canViewSocialLists = types.canViewFollowers || types.canViewFollowing
    }

    private suspend fun fetchConnectionsDirect() {
        coroutineScope {
            val followingJob = async {
                if (visibleConnectionTypes.canViewFollowing) {
                    runCatching { firestoreService.fetchFollowing(userId) }.getOrDefault(emptyList())
                } else emptyList()
            }
            val followersJob = async {
                if (visibleConnectionTypes.canViewFollowers) {
                    runCatching { firestoreService.fetchFollowers(userId) }.getOrDefault(emptyList())
                } else emptyList()
            }
            categorizeConnectionsWithPrivacy(followingJob.await(), followersJob.await(), emptyList())
        }
    }

    // MARK: - Momentos etiquetados

    suspend fun fetchTaggedMoments() {
        if (currentUserId == null) return
        isLoadingTagged = true
        taggedMoments = BackendFeedService.fetchTaggedMoments(targetUserId = userId, limit = 50)?.moments ?: emptyList()
        isLoadingTagged = false
    }

    // MARK: - Categorización de conexiones

    private suspend fun categorizeConnectionsWithPrivacy(
        targetFollowingUsers: List<AppUser>,
        targetFollowerUsers: List<AppUser>,
        targetMutualUsers: List<AppUser>,
    ) {
        targetVisibleFollowingIds = if (visibleConnectionTypes.canViewFollowing) targetFollowingUsers.map { it.id }.toSet() else emptySet()
        targetVisibleFollowerIds = if (visibleConnectionTypes.canViewFollowers) targetFollowerUsers.map { it.id }.toSet() else emptySet()

        val filteredFollowing = if (visibleConnectionTypes.canViewFollowing) targetFollowingUsers else emptyList()
        val filteredFollowers = if (visibleConnectionTypes.canViewFollowers) targetFollowerUsers else emptyList()

        mutuals = emptyList()
        following = filteredFollowing
        followers = filteredFollowers

        recomputeVisitorSections()
        fetchMoments()
        isLoading = false

        LocalPersistenceService.saveFollowers(userId, filteredFollowers)
        LocalPersistenceService.saveFollowing(userId, filteredFollowing)
    }

    // MARK: - Contexto del viewer

    private suspend fun loadViewerContext(current: String) {
        coroutineScope {
            val profileJob = async { runCatching { firestoreService.fetchUser(current) }.getOrNull() }
            val followingJob = async {
                runCatching {
                    db.collection("users").document(current).collection("following").get().await()
                        .documents.map { (it.data?.get("userId") as? String) ?: it.id }.toSet()
                }.getOrDefault(emptySet())
            }
            val followerJob = async {
                runCatching {
                    db.collection("users").document(current).collection("followers").get().await()
                        .documents.map { (it.data?.get("userId") as? String) ?: it.id }.toSet()
                }.getOrDefault(emptySet())
            }
            val profile = profileJob.await()
            viewerProfile = profile
            viewerInterests = profile?.interests ?: emptyList()
            viewerBlockedUserIds = profile?.blockedUsers?.toSet() ?: emptySet()
            viewerFollowingIds = followingJob.await()
            viewerFollowerIds = followerJob.await()
            viewerNetworkIds = viewerFollowingIds union viewerFollowerIds
            recomputeVisitorSections()
        }
    }

    private fun recomputeVisitorSections() {
        val viewerId = currentUserId ?: run {
            commonConnections = emptyList()
            suggestedConnectionsForViewer = emptyList()
            return
        }
        val targetVisibleNetworkIds = targetVisibleFollowingIds union targetVisibleFollowerIds
        val commonIds = (viewerNetworkIds intersect targetVisibleNetworkIds) - setOf(viewerId, userId)
        val visibleUsers = uniqueUsersPreservingOrder(mutuals + followers + following)
        commonConnections = visibleUsers.filter { it.id in commonIds }
        viewModelScope.launch {
            refreshSuggestedConnections(commonConnections.map { it.id }.toSet() + setOf(viewerId, userId))
        }
    }

    private suspend fun refreshSuggestedConnections(excludingIds: Set<String>) {
        val viewerId = currentUserId ?: return
        if (viewerInterests.isEmpty()) {
            suggestedConnectionsForViewer = emptyList()
            return
        }
        val signature = (listOf(viewerId, userId) + excludingIds.sorted() + viewerInterests.sorted()).joinToString("|")
        if (signature == lastSuggestionsSignature) return
        lastSuggestionsSignature = signature

        val users = runCatching { firestoreService.fetchUsersWithSharedInterests(viewerInterests, viewerId) }
            .getOrNull() ?: run { suggestedConnectionsForViewer = emptyList(); return }

        val excludedNetworkIds = viewerFollowingIds union viewerFollowerIds
        val filtered = uniqueUsersPreservingOrder(users).filter { user ->
            user.id !in excludingIds &&
                user.id !in excludedNetworkIds &&
                user.id !in viewerBlockedUserIds &&
                viewerId !in user.blockedUsers
        }.sortedWith(
            compareByDescending<AppUser> { (it.interests intersect viewerInterests.toSet()).size }
                .thenBy { it.username.lowercase() },
        )

        suggestedConnectionsForViewer = filtered.take(8)
        suggestedConnectionsForViewer.forEach { prefetchRelationshipState(it.id) }
    }

    private fun uniqueUsersPreservingOrder(users: List<AppUser>): List<AppUser> {
        val seen = mutableSetOf<String>()
        return users.filter { seen.add(it.id) }
    }

    // MARK: - Momentos

    suspend fun fetchMoments() {
        val current = currentUserId ?: run { isLoadingMoments = false; return }
        val backend = BackendFeedService.fetchProfileMoments(targetUserId = userId, limit = 50)
        if (backend != null) {
            moments = backend.moments
            isLoadingMoments = false
            LocalPersistenceService.saveProfileMoments(backend.moments, userId, current, sync = true)
            return
        }
        // Fallback: visibilidad por-momento vía repositorio (equivale a filterMomentsForAudience en iOS).
        runCatching { firestoreService.fetchMomentsWithVisibility(userId, current) }
            .onSuccess { filtered ->
                moments = filtered
                isLoadingMoments = false
                LocalPersistenceService.saveProfileMoments(filtered, userId, current, sync = true)
            }
            .onFailure { isLoadingMoments = false }
    }

    // MARK: - Estado del botón de seguir

    fun checkFollowButtonState() {
        val current = currentUserId ?: return
        viewModelScope.launch {
            FollowStateStore.state(userId)?.let { cached ->
                followButtonState = cached
                isFollowing = cached == FollowButtonState.FOLLOWING
            }
            val state = PrivacyService.getFollowButtonState(current, userId)
            val reconciled = FollowStateStore.reconciledState(state, userId)
            followButtonState = reconciled
            isFollowing = reconciled == FollowButtonState.FOLLOWING
            FollowStateStore.setState(reconciled, userId)
        }
    }

    // MARK: - Gestión de relación (best friend, mute, listas)

    fun loadRelationshipManagementState() {
        val current = currentUserId ?: return
        if (current == userId) return
        viewModelScope.launch {
            isInBestFriends = runCatching {
                bestFriendsService.fetchBestFriends(current).any { it.id == userId }
            }.getOrDefault(false)

            isMutedByCurrentUser = runCatching { firestoreService.fetchMutedUserIds(current).contains(userId) }
                .getOrDefault(false)

            isMutualRelationship = runCatching { PrivacyService.checkMutualConnection(current, userId) }
                .getOrDefault(false)

            runCatching { firestoreService.fetchCustomLists(current) }
                .onSuccess { lists ->
                    val matching = lists.filter { it.members.contains(userId) }
                    customListsContainingProfile = matching
                    customListMembershipCount = matching.size
                }
                .onFailure {
                    customListsContainingProfile = emptyList()
                    customListMembershipCount = 0
                }
        }
    }

    private suspend fun refreshMutedUserIds() {
        val current = currentUserId ?: return
        isMutedByCurrentUser = runCatching { firestoreService.fetchMutedUserIds(current).contains(userId) }
            .getOrDefault(false)
    }

    fun removeFromCustomList(list: CustomAudienceList) {
        val current = currentUserId ?: return
        val listId = list.id ?: return
        if (current == userId || isUpdatingLists) return
        isUpdatingLists = true
        viewModelScope.launch {
            val ok = runCatching { firestoreService.removeMembersFromCustomList(listId, current, listOf(userId)) }.isSuccess
            isUpdatingLists = false
            if (ok) {
                customListsContainingProfile = customListsContainingProfile.filterNot { it.id == list.id }
                customListMembershipCount = customListsContainingProfile.size
            }
        }
    }

    fun toggleBestFriend() {
        val current = currentUserId ?: return
        if (current == userId || isUpdatingBestFriend) return
        isUpdatingBestFriend = true
        val shouldAdd = !isInBestFriends
        viewModelScope.launch {
            val ok = runCatching {
                if (shouldAdd) bestFriendsService.addBestFriend(current, userId)
                else bestFriendsService.removeBestFriend(current, userId)
            }.isSuccess
            isUpdatingBestFriend = false
            if (ok) isInBestFriends = shouldAdd
        }
    }

    fun toggleMute() {
        val current = currentUserId ?: return
        if (current == userId || isUpdatingMute) return
        isUpdatingMute = true
        val shouldMute = !isMutedByCurrentUser
        viewModelScope.launch {
            val ok = runCatching {
                val field = if (shouldMute) FieldValue.arrayUnion(userId) else FieldValue.arrayRemove(userId)
                db.collection("users").document(current)
                    .update("muteSettings.mutedUsers", field).await()
            }.isSuccess
            isUpdatingMute = false
            if (ok) isMutedByCurrentUser = shouldMute
        }
    }

    // MARK: - Visitas

    fun registerVisit() {
        val current = currentUserId ?: return
        if (current == userId) return
        AffinityTracker.trackInteraction(AffinityInteractionType.PROFILE_VISIT, userId)
        if (IncognitoModeService.isActive.value) return
        viewModelScope.launch {
            runCatching { firestoreService.registerVisit(current, userId) }
        }
    }

    // MARK: - Follow / Unfollow / Request

    override fun followUser(targetId: String) {
        val current = currentUserId ?: return
        recentUnfollows.remove(targetId)
        viewModelScope.launch {
            if (targetId == userId) {
                val profile = userProfile
                if (profile?.isPrivate == true) {
                    val ok = runCatching { firestoreService.sendFollowRequest(current, targetId) }.isSuccess
                    if (ok) {
                        followButtonState = FollowButtonState.REQUEST_PENDING_CANCELLABLE
                        FollowStateStore.setState(FollowButtonState.REQUEST_PENDING_CANCELLABLE, targetId)
                    }
                } else {
                    val ok = runCatching { firestoreService.followUser(current, targetId) }.isSuccess
                    if (ok) {
                        followButtonState = FollowButtonState.FOLLOWING
                        isFollowing = true
                        FollowStateStore.setState(FollowButtonState.FOLLOWING, targetId)
                        if (visibleConnectionTypes.canViewMutuals) {
                            followers.firstOrNull { it.id == targetId }?.let { follower ->
                                if (mutuals.none { it.id == targetId }) mutuals = mutuals + follower
                            }
                        }
                        if (visibleConnectionTypes.canViewFollowing && following.none { it.id == targetId }) {
                            runCatching { firestoreService.fetchUser(targetId) }.getOrNull()?.let { user ->
                                if (following.none { it.id == user.id }) following = following + user
                            }
                        }
                    }
                }
                return@launch
            }

            // Otro usuario (tarjeta de sugerencia/lista).
            val state = PrivacyService.getFollowButtonState(current, targetId)
            when (FollowStateStore.reconciledState(state, targetId)) {
                FollowButtonState.CAN_REQUEST_FOLLOW -> {
                    if (runCatching { firestoreService.sendFollowRequest(current, targetId) }.isSuccess) {
                        FollowStateStore.setState(FollowButtonState.REQUEST_PENDING_CANCELLABLE, targetId)
                    }
                }
                FollowButtonState.CAN_FOLLOW -> {
                    if (runCatching { firestoreService.followUser(current, targetId) }.isSuccess) {
                        FollowStateStore.setState(FollowButtonState.FOLLOWING, targetId)
                        suggestedConnectionsForViewer = suggestedConnectionsForViewer.filterNot { it.id == targetId }
                    }
                }
                else -> Unit
            }
        }
    }

    /** Conveniencia para el botón principal: despacha follow/unfollow/cancel según el estado actual. */
    fun toggleFollow() {
        when (followButtonState) {
            FollowButtonState.FOLLOWING -> unfollowUser(userId)
            FollowButtonState.REQUEST_PENDING_CANCELLABLE, FollowButtonState.REQUEST_PENDING -> cancelFollowRequest(userId)
            FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW -> followUser(userId)
            else -> Unit
        }
    }

    override fun cancelFollowRequest(targetId: String) {
        val current = currentUserId ?: return
        viewModelScope.launch {
            if (runCatching { firestoreService.cancelFollowRequest(current, targetId) }.isSuccess) {
                if (targetId == userId) followButtonState = FollowButtonState.CAN_REQUEST_FOLLOW
                FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, targetId)
            }
        }
    }

    override fun unfollowUser(targetId: String) {
        val current = currentUserId ?: return
        recentUnfollows.add(targetId)
        viewModelScope.launch {
            if (!runCatching { firestoreService.unfollowUser(current, targetId) }.isSuccess) {
                recentUnfollows.remove(targetId)
                return@launch
            }
            val nextState = if (targetId == userId) {
                if (userProfile?.isPrivate == true) FollowButtonState.CAN_REQUEST_FOLLOW else FollowButtonState.CAN_FOLLOW
            } else {
                val known = followers.firstOrNull { it.id == targetId }
                    ?: following.firstOrNull { it.id == targetId }
                    ?: mutuals.firstOrNull { it.id == targetId }
                if (known?.isPrivate == true) FollowButtonState.CAN_REQUEST_FOLLOW else FollowButtonState.CAN_FOLLOW
            }
            FollowStateStore.setState(nextState, targetId)
            if (targetId != userId) return@launch

            followButtonState = nextState
            isFollowing = false
            if (userProfile?.isPrivate == true) canViewContent = false
            if (visibleConnectionTypes.canViewMutuals) mutuals = mutuals.filterNot { it.id == targetId }
            if (visibleConnectionTypes.canViewFollowing) following = following.filterNot { it.id == targetId }
        }
    }

    override fun relationshipState(targetId: String): FollowButtonState {
        // Versión no-suspend para pintar: usa lo conocido (following/mutuals) sin ir a red.
        val current = currentUserId
        if (current == targetId) return FollowButtonState.OWN_PROFILE
        if (following.any { it.id == targetId } || mutuals.any { it.id == targetId }) return FollowButtonState.FOLLOWING
        val known = followers.firstOrNull { it.id == targetId }
            ?: following.firstOrNull { it.id == targetId }
            ?: mutuals.firstOrNull { it.id == targetId }
        return if (known?.isPrivate == true) FollowButtonState.CAN_REQUEST_FOLLOW else FollowButtonState.CAN_FOLLOW
    }

    override fun prefetchRelationshipState(targetId: String) {
        val current = currentUserId ?: return
        if (current == targetId) return
        viewModelScope.launch {
            if (FollowStateStore.state(targetId) != null) return@launch
            val state = PrivacyService.getFollowButtonState(current, targetId)
            FollowStateStore.setState(FollowStateStore.reconciledState(state, targetId), targetId)
        }
    }

    fun checkIfFollowing() {
        val current = currentUserId ?: return
        viewModelScope.launch {
            isFollowing = runCatching { firestoreService.isFollowing(current, userId) }.getOrDefault(false)
        }
    }

    // MARK: - Block / Unblock

    fun checkIfBlocked() {
        val current = currentUserId ?: return
        viewModelScope.launch {
            runCatching { firestoreService.checkIfBlocked(current, userId) }.getOrNull()?.let { result ->
                isBlockedByCurrentUser = result.isBlockedByCurrentUser
                isCurrentUserBlocked = result.isCurrentUserBlocked
                if (result.isBlockedByCurrentUser || result.isCurrentUserBlocked) {
                    canViewContent = false
                    isProfileUnavailable = result.isCurrentUserBlocked
                }
            }
        }
    }

    fun blockUser(targetId: String) {
        val current = currentUserId ?: return
        viewModelScope.launch {
            if (runCatching { firestoreService.blockUser(current, targetId) }.isSuccess) {
                isBlockedByCurrentUser = true
                isProfileUnavailable = true
                followButtonState = FollowButtonState.BLOCKED
                isFollowing = false
            }
        }
    }

    fun unblockUser(targetId: String) {
        val current = currentUserId ?: return
        viewModelScope.launch {
            if (runCatching { firestoreService.unblockUser(current, targetId) }.isSuccess) {
                isBlockedByCurrentUser = false
                isProfileUnavailable = false
                checkFollowButtonState()
                fetchProfile()
            }
        }
    }

    // MARK: - Caché de canViewContent (por par viewer/perfil, como iOS con UserDefaults)

    private fun canViewContentKey(current: String) = "userProfile.canViewContent.$current.$userId"

    private fun cachedCanViewContent(current: String): Boolean? {
        val p = prefs ?: return null
        val key = canViewContentKey(current)
        return if (p.contains(key)) p.getBoolean(key, false) else null
    }

    private fun persistCanViewContent(value: Boolean, current: String) {
        prefs?.edit()?.putBoolean(canViewContentKey(current), value)?.apply()
    }
}
