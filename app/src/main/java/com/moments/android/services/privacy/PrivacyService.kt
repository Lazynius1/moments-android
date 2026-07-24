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

// MARK: - Follow button states

enum class FollowButtonState {
    OWN_PROFILE,
    BLOCKED,
    FOLLOWING,
    CAN_FOLLOW,
    CAN_REQUEST_FOLLOW,
    REQUEST_PENDING,
    REQUEST_PENDING_CANCELLABLE;

    val buttonText: String
        get() = when (this) {
            OWN_PROFILE -> "Own profile"
            BLOCKED -> "Blocked"
            FOLLOWING -> "Following"
            CAN_FOLLOW -> "Follow"
            CAN_REQUEST_FOLLOW -> "Request follow"
            REQUEST_PENDING -> "Request sent"
            REQUEST_PENDING_CANCELLABLE -> "Cancel request"
        }

    val isActionable: Boolean
        get() = when (this) {
            OWN_PROFILE, BLOCKED, REQUEST_PENDING -> false
            FOLLOWING, CAN_FOLLOW, CAN_REQUEST_FOLLOW, REQUEST_PENDING_CANCELLABLE -> true
        }

    val buttonColor: String
        get() = when (this) {
            OWN_PROFILE -> "gray"
            BLOCKED -> "red"
            FOLLOWING -> "green"
            CAN_FOLLOW, CAN_REQUEST_FOLLOW -> "blue"
            REQUEST_PENDING, REQUEST_PENDING_CANCELLABLE -> "orange"
        }
}

// MARK: - Follow state store

object FollowStateStore {
    private val lock = Mutex()
    private val statesByUserId = mutableMapOf<String, FollowButtonState>()
    private val listeners = mutableListOf<(String, FollowButtonState) -> Unit>()

    fun addListener(listener: (String, FollowButtonState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String, FollowButtonState) -> Unit) {
        listeners.remove(listener)
    }

    suspend fun state(userId: String): FollowButtonState? = lock.withLock {
        statesByUserId[userId]
    }

    suspend fun setState(state: FollowButtonState, userId: String) {
        lock.withLock { statesByUserId[userId] = state }
        listeners.forEach { it(userId, state) }
    }

    suspend fun reconciledState(authoritativeState: FollowButtonState, userId: String): FollowButtonState {
        val cachedState = lock.withLock { statesByUserId[userId] }
        if ((cachedState == FollowButtonState.REQUEST_PENDING ||
                cachedState == FollowButtonState.REQUEST_PENDING_CANCELLABLE) &&
            authoritativeState == FollowButtonState.CAN_REQUEST_FOLLOW
        ) {
            return cachedState ?: FollowButtonState.CAN_REQUEST_FOLLOW
        }
        return authoritativeState
    }
}

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

        if (!firestoreService.isFollowing(viewerId, moment.authorId)) return false

        return runCatching {
            val settings = fetchPrivacySettings(moment.authorId)
            if (settings.isPrivate) {
                firestoreService.isFollowing(moment.authorId, viewerId)
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
        if (firestoreService.isFollowing(viewerId, targetUserId)) return FollowButtonState.FOLLOWING
        if (checkPendingFollowRequest(viewerId, targetUserId)) {
            return FollowButtonState.REQUEST_PENDING_CANCELLABLE
        }
        return runCatching {
            val settings = fetchPrivacySettings(targetUserId)
            if (settings.isPrivate) FollowButtonState.CAN_REQUEST_FOLLOW else FollowButtonState.CAN_FOLLOW
        }.getOrDefault(FollowButtonState.CAN_FOLLOW)
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
        val snap = db.collection("users").document(senderId).collection("sentFollowRequests")
            .whereEqualTo("recipientId", recipientId)
            .whereEqualTo("status", FollowRequestStatus.PENDING.raw)
            .limit(1)
            .get()
            .await()
        return snap.documents.isNotEmpty()
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
        val mutedIds = firestoreService.fetchMutedUserIds(viewerId)
        mutedUsersCacheMutex.withLock {
            mutedUsersCache[viewerId] = mutedIds to now
        }
        return mutedIds
    }
}
