package com.moments.android.services.privacy

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.FollowRequestStatus
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import com.moments.android.models.Story
import com.moments.android.services.firestore.fetchMutuals

// MARK: - Privacy settings

data class PrivacySettings(
    val isPrivate: Boolean,
    val showMutuals: Boolean,
    val showFollowing: Boolean,
    val showFollowers: Boolean,
)

data class ConnectionPermissions(
    val canViewMutuals: Boolean,
    val canViewFollowing: Boolean,
    val canViewFollowers: Boolean,
)

data class VisibleConnectionTypes(
    val canViewFollowers: Boolean,
    val canViewFollowing: Boolean,
    val canViewMutuals: Boolean,
)

// Compatibilidad para los consumidores que aún importan el enum histórico.
typealias ContentAudience = com.moments.android.views.creator.audienceselector.ContentAudience

/**
 * Port de PrivacyService.swift (clase principal + helpers privados + tipos auxiliares).
 */
object PrivacyService {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val firestoreService = FirestoreService()

    private val inFlightBlockChecks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val inFlightBestFriendChecks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val inFlightHiddenChecks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val mutedUsersCacheMutex = Mutex()
    private val mutedUsersCache = mutableMapOf<String, Pair<Set<String>, Long>>()
    private const val MUTED_USERS_CACHE_TTL_MS = 20_000L

    // MARK: - Privacy Settings Management

    suspend fun shouldShowInFeed(viewerId: String, moment: Moment): Boolean {
        if (moment.authorId == viewerId) return true

        if (checkMutualBlocks(viewerId, moment.authorId)) return false

        // isFollowing ya soft-fail → false offline (como iOS).
        if (!firestoreService.isFollowing(viewerId, moment.authorId)) return false

        return runCatching {
            val settings = fetchPrivacySettings(moment.authorId)
            if (settings.isPrivate) {
                firestoreService.isMutualConnection(viewerId, moment.authorId)
            } else {
                true
            }
        }.getOrDefault(false)
    }

    suspend fun fetchPrivacySettings(userId: String): PrivacySettings {
        val snap = db.collection("users").document(userId).get().await()
        if (!snap.exists()) error("User document not found")
        @Suppress("UNCHECKED_CAST")
        val data = snap.data as Map<String, Any?>
        return PrivacySettings(
            isPrivate = data["isPrivate"] as? Boolean ?: false,
            showMutuals = data["showMutuals"] as? Boolean ?: true,
            showFollowing = data["showFollowing"] as? Boolean ?: true,
            showFollowers = data["showFollowers"] as? Boolean ?: true,
        )
    }

    suspend fun updatePrivacySettings(
        userId: String,
        isPrivate: Boolean? = null,
        showMutuals: Boolean? = null,
        showFollowing: Boolean? = null,
        showFollowers: Boolean? = null,
    ) {
        val updateData = buildMap<String, Any> {
            isPrivate?.let { put("isPrivate", it) }
            showMutuals?.let { put("showMutuals", it) }
            showFollowing?.let { put("showFollowing", it) }
            showFollowers?.let { put("showFollowers", it) }
        }
        if (updateData.isEmpty()) return
        db.collection("users").document(userId).update(updateData).await()
    }

    // MARK: - Content Visibility Logic

    suspend fun canViewUserContent(viewerId: String, targetUserId: String): Boolean {
        if (viewerId == targetUserId) return true
        if (checkMutualBlocks(viewerId, targetUserId)) return false
        return canViewUserContentAfterBlockCheck(viewerId, targetUserId)
    }

    internal suspend fun canViewUserContentAfterBlockCheck(
        viewerId: String,
        targetUserId: String,
    ): Boolean {
        if (viewerId == targetUserId) return true
        return runCatching {
            val settings = fetchPrivacySettings(targetUserId)
            if (!settings.isPrivate) return true
            firestoreService.isFollowing(viewerId, targetUserId)
        }.getOrDefault(false)
    }

    suspend fun canViewUserConnections(
        viewerId: String,
        targetUserId: String,
    ): Result<ConnectionPermissions> {
        if (viewerId == targetUserId) {
            return Result.success(
                ConnectionPermissions(
                    canViewMutuals = true,
                    canViewFollowing = true,
                    canViewFollowers = true,
                ),
            )
        }

        return runCatching {
            val settings = fetchPrivacySettings(targetUserId)
            if (checkMutualBlocks(viewerId, targetUserId)) {
                return@runCatching ConnectionPermissions(false, false, false)
            }

            val canViewMutuals = settings.showMutuals
            if (!settings.isPrivate) {
                ConnectionPermissions(
                    canViewMutuals = canViewMutuals,
                    canViewFollowing = settings.showFollowing,
                    canViewFollowers = settings.showFollowers,
                )
            } else if (firestoreService.isFollowing(viewerId, targetUserId)) {
                ConnectionPermissions(
                    canViewMutuals = canViewMutuals,
                    canViewFollowing = settings.showFollowing,
                    canViewFollowers = settings.showFollowers,
                )
            } else {
                ConnectionPermissions(false, false, false)
            }
        }
    }

    suspend fun getVisibleConnectionTypes(
        viewerId: String,
        targetUserId: String,
    ): VisibleConnectionTypes {
        return canViewUserConnections(viewerId, targetUserId).fold(
            onSuccess = { permissions ->
                VisibleConnectionTypes(
                    canViewFollowers = permissions.canViewFollowers,
                    canViewFollowing = permissions.canViewFollowing,
                    canViewMutuals = permissions.canViewMutuals,
                )
            },
            onFailure = {
                VisibleConnectionTypes(false, false, false)
            },
        )
    }

    // MARK: - Helper Methods

    suspend fun checkMutualBlocks(viewerId: String, targetUserId: String): Boolean =
        dedupeInFlight(inFlightBlockChecks, "$viewerId|$targetUserId") {
            runCatching {
                val result = firestoreService.checkIfBlocked(viewerId, targetUserId)
                result.isBlockedByCurrentUser || result.isCurrentUserBlocked
            }.getOrDefault(true) // Fail closed on error.
        }

    private suspend fun checkIfBlocked(viewerId: String, targetUserId: String): Boolean {
        val snap = db.collection("users").document(targetUserId).get().await()
        if (!snap.exists()) return false
        @Suppress("UNCHECKED_CAST")
        val blockedUsers = (snap.data as? Map<String, Any?>)?.get("blockedUsers") as? List<*>
        return blockedUsers?.filterIsInstance<String>()?.contains(viewerId) == true
    }

    internal suspend fun dedupeInFlightBestFriend(
        key: String,
        block: suspend () -> Boolean,
    ): Boolean = dedupeInFlight(inFlightBestFriendChecks, key, block)

    internal suspend fun dedupeInFlightHidden(
        key: String,
        block: suspend () -> Boolean,
    ): Boolean = dedupeInFlight(inFlightHiddenChecks, key, block)

    private suspend fun <T> dedupeInFlight(
        map: ConcurrentHashMap<String, CompletableDeferred<T>>,
        key: String,
        block: suspend () -> T,
    ): T {
        while (true) {
            map[key]?.let { return it.await() }
            val deferred = CompletableDeferred<T>()
            if (map.putIfAbsent(key, deferred) == null) {
                try {
                    val result = block()
                    deferred.complete(result)
                    return result
                } catch (e: Exception) {
                    deferred.completeExceptionally(e)
                    throw e
                } finally {
                    map.remove(key)
                }
            }
        }
    }

    // MARK: - Profile Interaction Logic

    suspend fun canSendFollowRequest(viewerId: String, targetUserId: String): Boolean {
        if (viewerId == targetUserId) return false
        if (checkMutualBlocks(viewerId, targetUserId)) return false
        if (firestoreService.isFollowing(viewerId, targetUserId)) return false
        return runCatching {
            fetchPrivacySettings(targetUserId).isPrivate
        }.getOrDefault(false)
    }

    suspend fun getFollowButtonState(viewerId: String, targetUserId: String): FollowButtonState {
        if (viewerId == targetUserId) return FollowButtonState.OWN_PROFILE
        if (checkMutualBlocks(viewerId, targetUserId)) return FollowButtonState.BLOCKED
        // iOS: isFollowing / pending request fallan soft (false) → canFollow por defecto.
        if (firestoreService.isFollowing(viewerId, targetUserId)) {
            return if (firestoreService.isMutualConnection(viewerId, targetUserId))
                FollowButtonState.MUTUALS else FollowButtonState.FOLLOWING
        }
        if (checkPendingFollowRequest(viewerId, targetUserId)) {
            return FollowButtonState.REQUEST_PENDING_CANCELLABLE
        }
        return runCatching {
            val settings = fetchPrivacySettings(targetUserId)
            if (settings.isPrivate) FollowButtonState.CAN_REQUEST_FOLLOW else FollowButtonState.CAN_FOLLOW
        }.getOrDefault(FollowButtonState.CAN_FOLLOW)
    }

    /** Resolución estricta para FollowStateStore: ningún error se convierte en estado UI. */
    suspend fun resolveFollowButtonState(viewerId: String, targetUserId: String): FollowButtonState {
        if (viewerId == targetUserId) return FollowButtonState.OWN_PROFILE

        val viewerSnapshot = db.collection("users").document(viewerId).get().await()
        val targetSnapshot = db.collection("users").document(targetUserId).get().await()
        check(viewerSnapshot.exists() && targetSnapshot.exists()) { "Relationship user document not found" }

        val viewerBlocked = (viewerSnapshot.get("blockedUsers") as? List<*>)
            ?.filterIsInstance<String>()
            ?.contains(targetUserId) == true
        val targetBlocked = (targetSnapshot.get("blockedUsers") as? List<*>)
            ?.filterIsInstance<String>()
            ?.contains(viewerId) == true
        if (viewerBlocked || targetBlocked) return FollowButtonState.BLOCKED

        val following = db.collection("users").document(viewerId)
            .collection("following").document(targetUserId)
            .get().await().exists()
        if (following) {
            val mutual = db.collection("users").document(viewerId)
                .collection("mutuals").document(targetUserId)
                .get().await().exists()
            return if (mutual) FollowButtonState.MUTUALS else FollowButtonState.FOLLOWING
        }

        val pending = db.collection("users").document(viewerId).collection("sentFollowRequests")
            .whereEqualTo("recipientId", targetUserId)
            .whereEqualTo("status", FollowRequestStatus.PENDING.raw)
            .limit(1)
            .get().await()
            .documents
            .isNotEmpty()
        if (pending) return FollowButtonState.REQUEST_PENDING_CANCELLABLE

        return if (targetSnapshot.getBoolean("isPrivate") == true) {
            FollowButtonState.CAN_REQUEST_FOLLOW
        } else {
            FollowButtonState.CAN_FOLLOW
        }
    }

    suspend fun canUsersInteract(user1Id: String, user2Id: String): Boolean =
        !checkMutualBlocks(user1Id, user2Id)

    suspend fun canSendMessage(senderId: String, recipientId: String): Boolean {
        if (senderId == recipientId) return false
        if (checkMutualBlocks(senderId, recipientId)) return false
        if (firestoreService.isFollowing(senderId, recipientId)) return true
        return runCatching {
            !fetchPrivacySettings(recipientId).isPrivate
        }.getOrDefault(false)
    }

    private suspend fun checkPendingFollowRequest(senderId: String, recipientId: String): Boolean {
        // iOS: getDocuments { if error != nil { completion(false) } }
        return runCatching {
            val snap = db.collection("users").document(senderId).collection("sentFollowRequests")
                .whereEqualTo("recipientId", recipientId)
                .whereEqualTo("status", FollowRequestStatus.PENDING.raw)
                .limit(1)
                .get()
                .await()
            snap.documents.isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun saveCustomAudienceForMoment(
        momentId: String,
        authorId: String,
        allowedUsers: List<String>,
    ) {
        val data = mapOf(
            "contentType" to "moment",
            "allowedUsers" to allowedUsers,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.collection("users").document(authorId)
            .collection("customAudiences")
            .document("moment_$momentId")
            .set(data)
            .await()
    }

    suspend fun saveCustomAudienceForStory(
        storyId: String,
        authorId: String,
        allowedUsers: List<String>,
    ) {
        val data = mapOf(
            "contentType" to "story",
            "allowedUsers" to allowedUsers,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.collection("users").document(authorId)
            .collection("customAudiences")
            .document("story_$storyId")
            .set(data)
            .await()
    }

    /** Debug — alineado con iOS debugCustomAudiences (no-op silencioso en error). */
    suspend fun debugCustomAudiences(authorId: String) {
        runCatching {
            db.collection("users").document(authorId)
                .collection("customAudiences")
                .get()
                .await()
        }
    }

    internal val firestore: FirestoreService get() = firestoreService
    internal val database: FirebaseFirestore get() = db

    internal suspend fun fetchMutedUsersCached(viewerId: String): Set<String> {
        val now = System.currentTimeMillis()
        mutedUsersCacheMutex.withLock {
            mutedUsersCache[viewerId]?.let { (ids, updatedAt) ->
                if (now - updatedAt <= MUTED_USERS_CACHE_TTL_MS) return ids
            }
        }
        // iOS: error en mute → set vacío (no muteado).
        val mutedIds = runCatching { firestoreService.fetchMutedUserIds(viewerId) }.getOrDefault(emptySet())
        mutedUsersCacheMutex.withLock {
            mutedUsersCache[viewerId] = mutedIds to now
        }
        return mutedIds
    }

    // MARK: - Conexión mutua y mejores amigos

    suspend fun checkMutualConnection(user1: String, user2: String): Boolean =
        firestore.isMutualConnection(user1, user2)

    suspend fun checkIfBestFriend(userId: String, friendId: String): Boolean =
        dedupeInFlightBestFriend("$userId|$friendId") {
            runCatching {
                val snap = database.collection("users").document(userId).get().await()
                @Suppress("UNCHECKED_CAST")
                val bestFriends = (snap.data as? Map<String, Any?>)?.get("bestFriends") as? List<*>
                bestFriends?.filterIsInstance<String>()?.contains(friendId) == true
            }.getOrDefault(false)
        }

    // MARK: - Audiencias personalizadas

    suspend fun checkCustomAudience(
        contentType: String,
        contentId: String,
        authorId: String,
        viewerId: String,
    ): Boolean = runCatching {
        val snap = database.collection("users").document(authorId)
            .collection("customAudiences")
            .document("${contentType}_$contentId")
            .get()
            .await()
        @Suppress("UNCHECKED_CAST")
        val allowedUsers = (snap.data as? Map<String, Any?>)?.get("allowedUsers") as? List<*>
        allowedUsers?.filterIsInstance<String>()?.contains(viewerId) == true
    }.getOrDefault(false)

    private suspend fun checkStoryVisibilitySettings(
        authorId: String,
        viewerId: String,
    ): Boolean {
        val snap = database.collection("users").document(authorId).get().await()
        @Suppress("UNCHECKED_CAST")
        val data = snap.data as? Map<String, Any?> ?: return canViewUserContent(viewerId, authorId)
        @Suppress("UNCHECKED_CAST")
        val settings = data["contentVisibilitySettings"] as? Map<String, Any?> ?: run {
            return canViewUserContent(viewerId, authorId)
        }
        return when (settings["storyVisibility"] as? String) {
            "everyone" -> canViewUserContent(viewerId, authorId)
            "mutuals" -> checkMutualConnection(viewerId, authorId)
            "bestFriends" -> checkIfBestFriend(authorId, viewerId)
            "custom" -> {
                @Suppress("UNCHECKED_CAST")
                val customViewers = settings["customStoryViewers"] as? List<*>
                customViewers?.filterIsInstance<String>()?.contains(viewerId) == true
            }
            else -> false
        }
    }

    suspend fun saveCustomAudience(
        contentType: String,
        contentId: String,
        authorId: String,
        allowedUsers: List<String>,
    ) {
        val data = mapOf(
            "contentType" to contentType,
            "contentId" to contentId,
            "allowedUsers" to allowedUsers,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        database.collection("users").document(authorId)
            .collection("customAudiences")
            .document("${contentType}_$contentId")
            .set(data)
            .await()
    }

    // MARK: - Content viewers

    suspend fun getContentViewers(moment: Moment): List<String> {
        val audience = ContentAudience.from(moment.audience)
        return when (audience) {
            ContentAudience.EVERYONE -> fetchPotentialViewers(moment.authorId)
            ContentAudience.MUTUALS -> fetchMutualsUserIds(moment.authorId)
            ContentAudience.BEST_FRIENDS -> fetchBestFriendsUserIds(moment.authorId)
            ContentAudience.CUSTOM -> fetchCustomAudienceUserIds(
                contentType = "moment",
                contentId = moment.id.orEmpty(),
                authorId = moment.authorId,
            )
            ContentAudience.CUSTOM_LIST -> fetchCustomListViewersForMoment(moment)
            ContentAudience.ONLY_ME -> listOf(moment.authorId)
        }
    }

    private suspend fun fetchCustomListViewersForMoment(moment: Moment): List<String> {
        val momentId = moment.id ?: return emptyList()
        val snap = database.collection("users").document(moment.authorId)
            .collection("moments").document(momentId)
            .get()
            .await()
        @Suppress("UNCHECKED_CAST")
        val customListId = (snap.data as? Map<String, Any?>)?.get("customListId") as? String
            ?: return emptyList()
        return getCustomListViewers(customListId, moment.authorId)
    }

    suspend fun checkCustomList(
        contentType: String,
        contentId: String,
        authorId: String,
        viewerId: String,
    ): Boolean = runCatching {
        val contentCollection = if (contentType == "story") "stories" else "moments"
        val snap = database.collection("users").document(authorId)
            .collection(contentCollection).document(contentId)
            .get()
            .await()
        @Suppress("UNCHECKED_CAST")
        val customListId = (snap.data as? Map<String, Any?>)?.get("customListId") as? String
            ?: return@runCatching false
        checkUserInList(viewerId, customListId, authorId)
    }.getOrDefault(false)

    private suspend fun fetchPotentialViewers(userId: String): List<String> =
        runCatching {
            fetchPrivacySettings(userId)
            firestore.fetchFollowers(userId).map { it.id }
        }.getOrDefault(emptyList())

    private suspend fun fetchMutualsUserIds(userId: String): List<String> =
        runCatching {
            firestore.fetchMutuals(userId).map { it.id }
        }.getOrDefault(emptyList())

    private suspend fun fetchBestFriendsUserIds(userId: String): List<String> {
        val snap = database.collection("users").document(userId).get().await()
        @Suppress("UNCHECKED_CAST")
        val bestFriends = (snap.data as? Map<String, Any?>)?.get("bestFriends") as? List<*>
        return bestFriends?.filterIsInstance<String>() ?: emptyList()
    }

    private suspend fun fetchCustomAudienceUserIds(
        contentType: String,
        contentId: String,
        authorId: String,
    ): List<String> {
        val snap = database.collection("users").document(authorId)
            .collection("customAudiences")
            .document("${contentType}_$contentId")
            .get()
            .await()
        @Suppress("UNCHECKED_CAST")
        val allowedUsers = (snap.data as? Map<String, Any?>)?.get("allowedUsers") as? List<*>
        return allowedUsers?.filterIsInstance<String>() ?: emptyList()
    }

    // MARK: - Listas personalizadas reutilizables

    suspend fun canUserViewContentWithCustomList(
        content: CustomListContent,
        viewerId: String,
    ): Boolean {
        if (content.authorId == viewerId) return true
        if (checkMutualBlocks(viewerId, content.authorId)) return false
        val listId = content.customListId?.takeIf { it.isNotEmpty() } ?: return false
        return checkUserInList(viewerId, listId, content.authorId)
    }

    private suspend fun checkCustomListMembership(story: Story, viewerId: String): Boolean {
        val customListId = story.customListId?.takeIf { it.isNotEmpty() } ?: return false
        return checkUserInList(viewerId, customListId, story.authorId)
    }

    private suspend fun checkCustomListMembership(moment: Moment, viewerId: String): Boolean {
        val customListId = moment.customListId?.takeIf { it.isNotEmpty() } ?: return false
        return checkUserInList(viewerId, customListId, moment.authorId)
    }

    private suspend fun checkUserInList(
        userId: String,
        listId: String,
        listOwnerId: String,
    ): Boolean = runCatching {
        val snap = database.collection("users").document(listOwnerId)
            .collection("customAudienceLists").document(listId)
            .get()
            .await()
        @Suppress("UNCHECKED_CAST")
        val members = (snap.data as? Map<String, Any?>)?.get("members") as? List<*>
        members?.filterIsInstance<String>()?.contains(userId) == true
    }.getOrDefault(false)

    suspend fun getCustomListViewers(listId: String, ownerId: String): List<String> {
        val snap = database.collection("users").document(ownerId)
            .collection("customAudienceLists").document(listId)
            .get()
            .await()
        @Suppress("UNCHECKED_CAST")
        val members = (snap.data as? Map<String, Any?>)?.get("members") as? List<*>
        return members?.filterIsInstance<String>() ?: emptyList()
    }

    // MARK: - Visibilidad mejorada de momentos e historias

    suspend fun canUserViewMomentEnhanced(moment: Moment, viewerId: String): Boolean {
        val momentId = moment.id?.takeIf { it.isNotEmpty() } ?: return false
        if (moment.authorId == viewerId) return true
        if (isAuthorMutedForViewer(viewerId, moment.authorId)) return false
        if (checkMutualBlocks(viewerId, moment.authorId)) return false
        if (isViewerHiddenFromAuthorContent(moment.authorId, viewerId)) return false

        return when (moment.audience ?: "everyone") {
            "everyone" -> canViewUserContentAfterBlockCheck(viewerId, moment.authorId)
            "mutuals" -> checkMutualConnection(viewerId, moment.authorId)
            "bestFriends" -> checkIfBestFriend(moment.authorId, viewerId)
            "custom" -> checkCustomAudience("moment", momentId, moment.authorId, viewerId)
            "customList" -> checkCustomListMembership(moment, viewerId)
            else -> false
        }
    }

    suspend fun canUserViewStoryEnhanced(story: Story, viewerId: String): Boolean {
        if (story.authorId == viewerId) return true
        if (isAuthorMutedForViewer(viewerId, story.authorId)) return false
        if (checkMutualBlocks(viewerId, story.authorId)) return false
        if (isViewerHiddenFromAuthorContent(story.authorId, viewerId)) return false

        return when (story.audience ?: "everyone") {
            "everyone" -> canViewUserContentAfterBlockCheck(viewerId, story.authorId)
            "mutuals" -> checkMutualConnection(viewerId, story.authorId)
            "bestFriends" -> checkIfBestFriend(story.authorId, viewerId)
            "custom" -> checkCustomAudience(
                contentType = "story",
                contentId = story.id.orEmpty(),
                authorId = story.authorId,
                viewerId = viewerId,
            )
            "customList" -> checkCustomListMembership(story, viewerId)
            "onlyMe" -> false
            else -> false
        }
    }

    // MARK: - Hidden / muted checks

    private suspend fun isViewerHiddenFromAuthorContent(
        authorId: String,
        viewerId: String,
    ): Boolean = dedupeInFlightHidden("$authorId|$viewerId") {
        runCatching {
            val snap = database.collection("users").document(authorId).get().await()
            @Suppress("UNCHECKED_CAST")
            val data = snap.data as? Map<String, Any?> ?: return@runCatching false
            @Suppress("UNCHECKED_CAST")
            val settings = data["contentVisibilitySettings"] as? Map<String, Any?> ?: return@runCatching false
            @Suppress("UNCHECKED_CAST")
            val hiddenFromUsers = settings["hiddenFromUsers"] as? List<*>
            hiddenFromUsers?.filterIsInstance<String>()?.contains(viewerId) == true
        }.getOrDefault(false)
    }

    private suspend fun isAuthorMutedForViewer(
        viewerId: String,
        authorId: String,
    ): Boolean {
        if (viewerId.isEmpty() || authorId.isEmpty()) return false
        if (viewerId == authorId) return false
        val mutedIds = fetchMutedUsersCached(viewerId)
        return authorId in mutedIds
    }

    // MARK: - Explore (más permisivo)

    suspend fun canUserViewMomentInExplore(moment: Moment, viewerId: String): Boolean {
        if (moment.authorId == viewerId) return true
        if (isAuthorMutedForViewer(viewerId, moment.authorId)) return false
        if (checkMutualBlocks(viewerId, moment.authorId)) return false
        if (isViewerHiddenFromAuthorContent(moment.authorId, viewerId)) return false

        return when (moment.audience ?: "everyone") {
            "everyone" -> canViewUserContentForExplore(viewerId, moment.authorId)
            "mutuals" -> checkMutualConnection(viewerId, moment.authorId)
            "bestFriends" -> checkIfBestFriend(moment.authorId, viewerId)
            "custom" -> checkCustomAudience(
                contentType = "moment",
                contentId = moment.id.orEmpty(),
                authorId = moment.authorId,
                viewerId = viewerId,
            )
            "customList" -> checkCustomListMembership(moment, viewerId)
            "onlyMe" -> false
            else -> false
        }
    }

    suspend fun canViewUserContentForExplore(
        viewerId: String,
        targetUserId: String,
    ): Boolean {
        if (viewerId == targetUserId) return true
        return runCatching {
            val settings = fetchPrivacySettings(targetUserId)
            if (!settings.isPrivate) true else firestore.isFollowing(viewerId, targetUserId)
        }.getOrDefault(false)
    }

    fun canShareMoment(moment: Moment): Boolean {
        val audience = moment.audience ?: "everyone"
        return audience == "everyone"
    }

}

// MARK: - Protocolo para contenido con lista personalizada (CustomListContent.swift section)

interface CustomListContent {
    val authorId: String
    val customListId: String?
}
