package com.moments.android.services.firestore

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.moments.android.models.AccountHistoryEventType
import com.moments.android.models.AccountHistoryItem
import com.moments.android.models.toMap
import com.moments.android.models.AppUser
import com.moments.android.models.FollowRequest
import com.moments.android.models.FollowRequestStatus
import com.moments.android.models.FollowActionPayload
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.ReactionPayload
import com.moments.android.models.cache.CachedAction
import com.moments.android.models.encode
import com.moments.android.services.notifications.NotificationService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.models.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Nucleo de FirestoreService — port incremental de FirestoreService.swift.
 * Repositorios especializados en archivos Firestore*Repository.kt.
 */
class FirestoreService(
    internal val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    internal val firestoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val _savedMomentIds = MutableStateFlow<List<String>>(emptyList())
    val savedMomentIds: StateFlow<List<String>> = _savedMomentIds.asStateFlow()

    internal val _savedMomentsLoadedForUserId = MutableStateFlow<String?>(null)
    val savedMomentsLoadedForUserId: StateFlow<String?> = _savedMomentsLoadedForUserId.asStateFlow()

    private val followingCache = ConcurrentHashMap<String, Boolean>()
    private var lastCacheUpdate = Date()

    internal val storySummaryRebuildInFlight = ConcurrentHashMap.newKeySet<String>()
    internal val storySummaryLastRebuildAttempt = ConcurrentHashMap<String, Date>()
    internal val storySummaryRebuildCooldownMs = 60_000L
    private val storySummaryRebuildMutex = Mutex()

    // --- Perfil / usuario → FirestoreProfilesRepository.kt ---

    suspend fun updateBio(userId: String, oldBio: String? = null, newBio: String) {
        db.collection("users").document(userId).update("bio", newBio).await()
        if (oldBio != newBio) logAccountHistoryEvent(userId, AccountHistoryEventType.BIO, oldBio, newBio)
    }

    suspend fun updateProfileNote(userId: String, note: String) {
        db.collection("users").document(userId).update("profileNote", note).await()
    }

    suspend fun updateProfileDetails(
        userId: String,
        oldBio: String? = null,
        newBio: String? = null,
        oldWebsite: String? = null,
        newWebsite: String? = null,
    ) {
        val data = buildMap<String, Any> {
            newBio?.let { put("bio", it) }
            newWebsite?.let { put("websiteUrl", it) }
        }
        if (data.isEmpty()) return
        db.collection("users").document(userId).update(data).await()
        newBio?.takeIf { it != oldBio }?.let {
            logAccountHistoryEvent(userId, AccountHistoryEventType.BIO, oldBio, it)
        }
        newWebsite?.takeIf { it != oldWebsite }?.let {
            logAccountHistoryEvent(userId, AccountHistoryEventType.WEBSITE, oldWebsite, it)
        }
    }

    suspend fun logAccountHistoryEvent(
        userId: String,
        type: AccountHistoryEventType,
        oldValue: String?,
        newValue: String?,
    ) {
        if (oldValue == newValue) return
        val event = AccountHistoryItem(type = type, oldValue = oldValue, newValue = newValue)
        db.collection("users").document(userId).collection("accountHistory")
            .add(event.toMap()).await()
    }

    suspend fun fetchAccountHistory(userId: String): List<AccountHistoryItem> {
        val snap = db.collection("users").document(userId).collection("accountHistory")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
        return snap.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            AccountHistoryItem.from(doc.id, doc.data as Map<String, Any?>)
        }
    }

    // --- Seguimiento / bloqueo ---

    suspend fun isFollowing(currentUserId: String, targetUserId: String): Boolean {
        refreshFollowingCacheIfStale()
        val cacheKey = "${currentUserId}_$targetUserId"
        followingCache[cacheKey]?.let { return it }
        val snap = db.collection("users").document(currentUserId)
            .collection("following").document(targetUserId).get().await()
        val result = snap.exists()
        followingCache[cacheKey] = result
        return result
    }

    suspend fun isFollowingCached(currentUserId: String, targetUserId: String): Boolean {
        refreshFollowingCacheIfStale()
        val cacheKey = "${currentUserId}_$targetUserId"
        followingCache[cacheKey]?.let { return it }
        return isFollowing(currentUserId, targetUserId)
    }

    fun invalidateFollowingCache(currentUserId: String, targetUserId: String) {
        followingCache.remove("${currentUserId}_$targetUserId")
    }

    fun clearFollowingCache() {
        followingCache.clear()
    }

    private fun refreshFollowingCacheIfStale() {
        if (Date().time - lastCacheUpdate.time > 15_000) {
            followingCache.clear()
            lastCacheUpdate = Date()
        }
    }

    data class BlockCheckResult(
        val isBlockedByCurrentUser: Boolean,
        val isCurrentUserBlocked: Boolean,
    )

    suspend fun checkIfBlocked(currentUserId: String, targetUserId: String): BlockCheckResult =
        coroutineScope {
            val currentDeferred = async {
                runCatching {
                    val snap = db.collection("users").document(currentUserId).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val blocked = (snap.data as? Map<String, Any?>)?.get("blockedUsers") as? List<*>
                    blocked?.filterIsInstance<String>()?.contains(targetUserId) == true
                }.getOrDefault(false)
            }
            val targetDeferred = async {
                runCatching {
                    val snap = db.collection("users").document(targetUserId).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val blocked = (snap.data as? Map<String, Any?>)?.get("blockedUsers") as? List<*>
                    blocked?.filterIsInstance<String>()?.contains(currentUserId) == true
                }.getOrDefault(false)
            }
            BlockCheckResult(currentDeferred.await(), targetDeferred.await())
        }

    suspend fun blockUser(currentUserId: String, targetUserId: String) {
        db.collection("users").document(currentUserId)
            .update("blockedUsers", FieldValue.arrayUnion(targetUserId)).await()
        runCatching { unfollowUser(currentUserId, targetUserId) }
        runCatching { unfollowUser(targetUserId, currentUserId) }
        runCatching { deleteNotificationsBetweenUsers(currentUserId, targetUserId) }
        runCatching { deleteNotificationsBetweenUsers(targetUserId, currentUserId) }
        runCatching { deleteVisitsBetweenUsers(currentUserId, targetUserId) }
        runCatching { deleteVisitsBetweenUsers(targetUserId, currentUserId) }
    }

    private suspend fun deleteNotificationsBetweenUsers(recipientId: String, senderId: String) {
        val snap = db.collection("users").document(recipientId).collection("notifications")
            .whereEqualTo("senderId", senderId)
            .get()
            .await()
        if (snap.isEmpty) return
        val batch = db.batch()
        snap.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    private suspend fun deleteVisitsBetweenUsers(userId: String, visitorId: String) {
        db.collection("users").document(userId).collection("visits")
            .document(visitorId)
            .delete()
            .await()
    }

    suspend fun unblockUser(currentUserId: String, targetUserId: String) {
        db.collection("users").document(currentUserId)
            .update("blockedUsers", FieldValue.arrayRemove(targetUserId)).await()
    }

    suspend fun fetchFollowing(userId: String): List<AppUser> {
        val snap = db.collection("users").document(userId).collection("following")
            .limit(1000)
            .get()
            .await()
        val userIds = snap.documents.mapNotNull { doc ->
            (doc.data?.get("userId") as? String) ?: doc.id
        }
        val users = fetchUsersByIdsClean(userIds)
        LocalPersistenceService.saveFollowing(userId, users)
        return users
    }

    suspend fun fetchFollowers(userId: String): List<AppUser> {
        val snap = db.collection("users").document(userId).collection("followers")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1000)
            .get()
            .await()
        val userIds = snap.documents.mapNotNull { doc ->
            (doc.data?.get("userId") as? String) ?: doc.id
        }
        val users = fetchUsersByIdsClean(userIds)
        LocalPersistenceService.saveFollowers(userId, users)
        return users
    }

    suspend fun fetchFollowersWithTimestamps(userId: String): List<Pair<AppUser, Date>> {
        val snap = db.collection("users").document(userId).collection("followers")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
        val records = snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val id = data["userId"] as? String ?: doc.id
            val ts = (data["timestamp"] as? Timestamp)?.toDate() ?: Date()
            id to ts
        }
        val users = fetchUsersAsync(records.map { it.first })
        val userDict = users.associateBy { it.id }
        return records.mapNotNull { (id, ts) -> userDict[id]?.let { it to ts } }
    }

    suspend fun fetchFollowingWithTimestamps(userId: String): List<Pair<AppUser, Date>> {
        val snap = db.collection("users").document(userId).collection("following")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
        val records = snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val id = data["userId"] as? String ?: doc.id
            val ts = (data["timestamp"] as? Timestamp)?.toDate() ?: Date()
            id to ts
        }
        val users = fetchUsersAsync(records.map { it.first })
        val userDict = users.associateBy { it.id }
        return records.mapNotNull { (id, ts) -> userDict[id]?.let { it to ts } }
    }

    suspend fun fetchUsersByIdsClean(userIds: List<String>): List<AppUser> {
        if (userIds.isEmpty()) return emptyList()
        return userIds.distinct().chunked(10).flatMap { batch ->
            db.collection("users")
                .whereIn(FieldPath.documentId(), batch)
                .get()
                .await()
                .documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    runCatching {
                        val user = AppUser.from(doc.id, doc.data as Map<String, Any?>)
                        if (user.isActive) user else null
                    }.getOrNull()
                }
        }
    }

    suspend fun fetchUsersAsync(userIds: List<String>): List<AppUser> = fetchUsersByIdsClean(userIds)

    suspend fun fetchUsers(userIds: List<String>): List<AppUser> = fetchUsersByIdsClean(userIds)

    suspend fun fetchMutedUserIds(userId: String): Set<String> {
        if (userId.isEmpty()) return emptySet()
        val snap = db.collection("users").document(userId).get().await()
        @Suppress("UNCHECKED_CAST")
        val muteSettings = snap.data?.get("muteSettings") as? Map<String, Any?>
        return (muteSettings?.get("mutedUsers") as? List<*>)
            ?.filterIsInstance<String>()
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun sendFollowRequest(currentUserId: String, targetUserId: String) {
        require(currentUserId != targetUserId) { "Cannot follow yourself" }
        val block = checkIfBlocked(currentUserId, targetUserId)
        if (block.isBlockedByCurrentUser || block.isCurrentUserBlocked) {
            error("Blocked")
        }
        if (isFollowing(currentUserId, targetUserId)) error("Already following")
        val existing = checkExistingFollowRequest(currentUserId, targetUserId)
        existing?.let { req ->
            when (req.status) {
                FollowRequestStatus.PENDING -> error("Request pending")
                FollowRequestStatus.REJECTED -> {
                    val hoursSince = (Date().time - req.timestamp.time) / 3600_000
                    if (hoursSince < 24) error("Must wait before resending")
                }
                FollowRequestStatus.ACCEPTED -> error("Already following")
                FollowRequestStatus.CANCELLED -> Unit
            }
        }
        val targetUser = fetchUserProfile(targetUserId)
        if (!targetUser.isPrivate) {
            performFollow(currentUserId, targetUserId)
            return
        }
        val currentUser = fetchUserProfile(currentUserId)
        createFollowRequest(currentUser.id, currentUser.username, targetUserId)
    }

    suspend fun followUser(currentUserId: String, targetUserId: String) {
        LocalPersistenceService.toggleFollowLocally(currentUserId, targetUserId, isFollow = true)
        require(currentUserId != targetUserId) { "Cannot follow yourself" }
        if (shouldQueueFirestoreOutbox()) {
            val payload = FollowActionPayload(
                followerId = currentUserId,
                followedId = targetUserId,
                followedUsername = "",
                isFollow = true,
            )
            LocalPersistenceService.saveAction(
                CachedAction(
                    id = "follow_${currentUserId}_$targetUserId",
                    type = CachedAction.ActionType.FOLLOW.raw,
                    payloadData = payload.encode(),
                ),
            )
            return
        }
        invalidateFollowingCache(currentUserId, targetUserId)
        val block = checkIfBlocked(currentUserId, targetUserId)
        if (block.isBlockedByCurrentUser || block.isCurrentUserBlocked) error("Blocked")
        val targetUser = fetchUserProfile(targetUserId)
        if (targetUser.isPrivate) {
            sendFollowRequest(currentUserId, targetUserId)
        } else {
            performFollow(currentUserId, targetUserId)
            invalidateFollowingCache(currentUserId, targetUserId)
        }
    }

    private suspend fun createFollowRequest(senderId: String, senderUsername: String, recipientId: String) {
        val request = FollowRequest.create(senderId, senderUsername, recipientId)
        val requestData = mapOf(
            "id" to request.id,
            "senderId" to request.senderId,
            "senderUsername" to request.senderUsername,
            "recipientId" to request.recipientId,
            "status" to request.status.raw,
            "timestamp" to Timestamp(request.timestamp),
            "expirationDate" to request.expirationDate?.let { Timestamp(it) },
        )
        db.runBatch { batch ->
            batch.set(
                db.collection("users").document(senderId)
                    .collection("sentFollowRequests").document(request.id),
                requestData,
            )
            batch.set(
                db.collection("users").document(recipientId)
                    .collection("receivedFollowRequests").document(request.id),
                requestData,
            )
        }.await()
    }

    private suspend fun checkExistingFollowRequest(senderId: String, recipientId: String): FollowRequest? {
        val snap = db.collection("users").document(senderId).collection("sentFollowRequests")
            .whereEqualTo("recipientId", recipientId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        val doc = snap.documents.firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        return runCatching { FollowRequest.from(doc.data as Map<String, Any?>) }.getOrNull()
    }

    suspend fun acceptFollowRequest(notificationId: String, recipientId: String, senderId: String) {
        val request = getFollowRequestByUsers(senderId, recipientId) ?: error("Request not found")
        require(request.status == FollowRequestStatus.PENDING) { "Already processed" }
        request.expirationDate?.let { if (Date().after(it)) error("Expired") }
        performFollow(senderId, recipientId, sendNotification = false, acceptedFollowRequestId = request.id)
        db.runBatch { batch ->
            batch.delete(db.collection("users").document(senderId)
                .collection("sentFollowRequests").document(request.id))
            batch.delete(db.collection("users").document(recipientId)
                .collection("receivedFollowRequests").document(request.id))
            val notificationsRef = db.collection("users").document(recipientId).collection("notifications")
            batch.delete(notificationsRef.document(notificationId))
            batch.delete(notificationsRef.document("followRequest_$senderId"))
        }.await()
    }

    suspend fun rejectFollowRequest(notificationId: String, recipientId: String, senderId: String) {
        val request = getFollowRequestByUsers(senderId, recipientId) ?: error("Request not found")
        db.runBatch { batch ->
            batch.delete(db.collection("users").document(senderId)
                .collection("sentFollowRequests").document(request.id))
            batch.delete(db.collection("users").document(recipientId)
                .collection("receivedFollowRequests").document(request.id))
            val notificationsRef = db.collection("users").document(recipientId).collection("notifications")
            batch.delete(notificationsRef.document(notificationId))
            batch.delete(notificationsRef.document("followRequest_$senderId"))
        }.await()
    }

    suspend fun cancelFollowRequest(currentUserId: String, targetUserId: String) {
        val request = checkExistingFollowRequest(currentUserId, targetUserId) ?: error("Request not found")
        require(request.status == FollowRequestStatus.PENDING) { "Not pending" }
        db.runBatch { batch ->
            batch.delete(db.collection("users").document(currentUserId)
                .collection("sentFollowRequests").document(request.id))
            batch.delete(db.collection("users").document(targetUserId)
                .collection("receivedFollowRequests").document(request.id))
        }.await()
    }

    private suspend fun getFollowRequestByUsers(senderId: String, recipientId: String): FollowRequest? {
        val snap = db.collection("users").document(recipientId).collection("receivedFollowRequests")
            .whereEqualTo("senderId", senderId)
            .whereEqualTo("status", FollowRequestStatus.PENDING.raw)
            .limit(1)
            .get()
            .await()
        val doc = snap.documents.firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        return runCatching { FollowRequest.from(doc.data as Map<String, Any?>) }.getOrNull()
    }

    internal suspend fun performFollow(
        currentUserId: String,
        targetUserId: String,
        sendNotification: Boolean = true,
        acceptedFollowRequestId: String? = null,
    ) {
        val followingData = buildMap<String, Any> {
            put("userId", targetUserId)
            put("timestamp", Timestamp(Date()))
            acceptedFollowRequestId?.let {
                put("acceptedFollowRequestId", it)
                put("source", "followRequestAccepted")
                put("acceptedAt", Timestamp(Date()))
            }
        }
        val followerData = buildMap<String, Any> {
            put("userId", currentUserId)
            put("timestamp", Timestamp(Date()))
            acceptedFollowRequestId?.let {
                put("acceptedFollowRequestId", it)
                put("source", "followRequestAccepted")
                put("acceptedAt", Timestamp(Date()))
            }
        }
        db.runBatch { batch ->
            batch.set(
                db.collection("users").document(currentUserId)
                    .collection("following").document(targetUserId),
                followingData,
            )
            batch.set(
                db.collection("users").document(targetUserId)
                    .collection("followers").document(currentUserId),
                followerData,
            )
        }.await()
        invalidateFollowingCache(currentUserId, targetUserId)
    }

    suspend fun unfollowUser(currentUserId: String, targetUserId: String) {
        LocalPersistenceService.toggleFollowLocally(currentUserId, targetUserId, isFollow = false)
        require(currentUserId != targetUserId) { "Cannot unfollow yourself" }
        if (shouldQueueFirestoreOutbox()) {
            val payload = FollowActionPayload(
                followerId = currentUserId,
                followedId = targetUserId,
                followedUsername = "",
                isFollow = false,
            )
            LocalPersistenceService.saveAction(
                CachedAction(
                    id = "unfollow_${currentUserId}_$targetUserId",
                    type = CachedAction.ActionType.FOLLOW.raw,
                    payloadData = payload.encode(),
                ),
            )
            return
        }
        followingCache.remove("${currentUserId}_$targetUserId")
        val followingRef = db.collection("users").document(currentUserId)
            .collection("following").document(targetUserId)
        if (!followingRef.get().await().exists()) return
        db.runBatch { batch ->
            batch.delete(followingRef)
            batch.delete(db.collection("users").document(targetUserId)
                .collection("followers").document(currentUserId))
            batch.delete(db.collection("users").document(currentUserId)
                .collection("mutuals").document(targetUserId))
            batch.delete(db.collection("users").document(targetUserId)
                .collection("mutuals").document(currentUserId))
        }.await()
        followingCache.remove("${currentUserId}_$targetUserId")
        // Limpieza defensiva; servidor también limpia vía onFollowerRemoved.
        NotificationService.removeNotification(
            NotificationType.NEW_FOLLOWER, currentUserId, targetUserId,
        )
        NotificationService.removeNotification(
            NotificationType.MUTUAL_CONNECTION, currentUserId, targetUserId,
        )
        NotificationService.removeNotification(
            NotificationType.MUTUAL_CONNECTION, targetUserId, currentUserId,
        )
        // Verificación post-unfollow con delay (sin cache); force unfollow si persiste.
        delay(500)
        if (followingRef.get().await().exists()) {
            forceUnfollow(currentUserId, targetUserId)
        }
    }

    suspend fun forceUnfollow(currentUserId: String, targetUserId: String) {
        runCatching {
            db.collection("users").document(currentUserId)
                .collection("following").document(targetUserId).delete().await()
        }
        runCatching {
            db.collection("users").document(targetUserId)
                .collection("followers").document(currentUserId).delete().await()
        }
        clearFollowingCache()
    }

    // --- Momentos (core) ---

    suspend fun fetchMoment(momentId: String, userId: String): Moment {
        val snap = db.collection("users").document(userId)
            .collection("moments").document(momentId).get().await()
        if (!snap.exists()) error("Moment not found")
        @Suppress("UNCHECKED_CAST")
        val data = snap.data as Map<String, Any?>
        val moment = Moment.from(snap.id, data)
        if (moment.isArchived == true) error("Moment not found")
        return moment
    }

    suspend fun fetchMomentAuthorId(momentId: String, preferredUserId: String? = null): String? {
        preferredUserId?.let { userId ->
            val snap = db.collection("users").document(userId)
                .collection("moments").document(momentId).get().await()
            if (snap.exists()) return userId
        }
        val groupSnap = db.collectionGroup("moments")
            .whereEqualTo(FieldPath.documentId(), momentId)
            .limit(5)
            .get()
            .await()
        return groupSnap.documents.firstOrNull()?.reference?.parent?.parent?.id
    }

    suspend fun isUserPlus(userId: String): Boolean {
        val snap = db.collection("users").document(userId).get().await()
        return snap.data?.get("isPlusSubscriber") as? Boolean ?: false
    }

    suspend fun updateMomentDetails(
        userId: String,
        momentId: String,
        content: String,
        audience: String,
        customListId: String?,
        customViewers: List<String>?,
        taggedUsers: List<String>,
        mentionedUsers: List<String>,
        location: String?,
        locationCoordinate: Moment.LocationCoordinate?,
        mediaItems: List<MediaItem>? = null,
    ) {
        val momentRef = db.collection("users").document(userId).collection("moments").document(momentId)
        val updateData = buildMap<String, Any> {
            put("content", content)
            put("audience", audience)
            put("updatedAt", FieldValue.serverTimestamp())
            if (!customListId.isNullOrBlank()) put("customListId", customListId)
            else put("customListId", FieldValue.delete())
            if (taggedUsers.isEmpty()) put("taggedUsers", FieldValue.delete())
            else put("taggedUsers", taggedUsers)
            if (mentionedUsers.isEmpty()) put("mentionedUsers", FieldValue.delete())
            else put("mentionedUsers", mentionedUsers)
            if (!location.isNullOrBlank()) put("location", location)
            else put("location", FieldValue.delete())
            if (locationCoordinate != null) {
                put("locationCoordinate", mapOf(
                    "latitude" to locationCoordinate.latitude,
                    "longitude" to locationCoordinate.longitude,
                ))
            } else put("locationCoordinate", FieldValue.delete())
            mediaItems?.let { put("mediaItems", serializedMediaItems(it)) }
        }
        momentRef.update(updateData).await()
        if (audience == ContentAudience.CUSTOM.raw && !customViewers.isNullOrEmpty()) {
            saveCustomAudienceForContent("moment", momentId, userId, customViewers)
        }
    }

    suspend fun addReaction(momentId: String, reaction: String, userId: String, authorId: String) {
        LocalPersistenceService.toggleMomentReactionLocally(momentId, reaction, userId)
        if (shouldQueueFirestoreOutbox()) {
            val payload = ReactionPayload(momentId, reaction, authorId, userId)
            LocalPersistenceService.saveAction(
                CachedAction(
                    id = UUID.randomUUID().toString(),
                    type = CachedAction.ActionType.REACTION.raw,
                    payloadData = payload.encode(),
                ),
            )
            return
        }
        val reactionRef = db.collection("users").document(authorId)
            .collection("moments").document(momentId)
            .collection("reactions").document(userId)
        val snap = reactionRef.get().await()
        if (snap.exists()) {
            val existingReaction = snap.data?.get("reactionType") as? String
            if (existingReaction == reaction) {
                reactionRef.delete().await()
                if (userId != authorId) {
                    NotificationService.removeNotification(
                        NotificationType.REACTION, userId, authorId,
                        momentId = momentId, reaction = reaction,
                    )
                }
            } else {
                reactionRef.set(mapOf(
                    "userId" to userId,
                    "reactionType" to reaction,
                    "timestamp" to FieldValue.serverTimestamp(),
                )).await()
            }
        } else {
            reactionRef.set(mapOf(
                "userId" to userId,
                "reactionType" to reaction,
                "timestamp" to FieldValue.serverTimestamp(),
            )).await()
        }
    }

    suspend fun fetchRecentMomentCounts(authorIds: List<String>, since: Date): Map<String, Int> =
        coroutineScope {
            authorIds.distinct().map { authorId ->
                async {
                    runCatching {
                        val count = db.collection("users").document(authorId).collection("moments")
                            .whereGreaterThan("timestamp", Timestamp(since))
                            .get()
                            .await()
                            .size()
                        authorId to count
                    }.getOrDefault(authorId to 0)
                }
            }.awaitAll().filter { it.second > 0 }.toMap()
        }

    suspend fun fetchMomentsFromUsers(
        userIds: List<String>,
        perUserLimit: Int = 20,
        totalLimit: Int = 50,
    ): List<Moment> {
        val normalized = userIds.distinct().filter { it.isNotEmpty() }
        if (normalized.isEmpty()) return emptyList()
        val allMoments = normalized.chunked(10).flatMap { batch ->
            runCatching { fetchMomentsFromUsersBatch(batch, perUserLimit) }
                .getOrElse { fetchMomentsFromUsersLegacy(batch, perUserLimit) }
        }
        return allMoments.sortedByDescending { it.timestamp }.take(totalLimit.coerceAtLeast(1))
    }

    private suspend fun fetchMomentsFromUsersBatch(userIds: List<String>, perUserLimit: Int): List<Moment> {
        val snap = db.collectionGroup("moments")
            .whereIn("authorId", userIds)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit((perUserLimit * userIds.size).coerceAtLeast(20).toLong())
            .get()
            .await()
        val decoded = snap.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            runCatching { Moment.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
        }
        val filtered = filterScheduledMomentsForCurrentViewer(decoded)
        val perAuthorCount = mutableMapOf<String, Int>()
        return filtered.filter { moment ->
            val count = perAuthorCount.getOrDefault(moment.authorId, 0)
            if (count >= perUserLimit) false else {
                perAuthorCount[moment.authorId] = count + 1
                true
            }
        }
    }

    private suspend fun fetchMomentsFromUsersLegacy(userIds: List<String>, perUserLimit: Int): List<Moment> =
        coroutineScope {
            userIds.map { userId ->
                async {
                    val snap = db.collection("users").document(userId).collection("moments")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(perUserLimit.toLong())
                        .get()
                        .await()
                    snap.documents.mapNotNull { doc ->
                        @Suppress("UNCHECKED_CAST")
                        runCatching { Moment.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
                    }
                }
            }.awaitAll().flatten()
        }

    internal fun filterScheduledMomentsForCurrentViewer(moments: List<Moment>): List<Moment> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val now = Date()
        return moments.filter { moment ->
            if (moment.isArchived == true) return@filter false
            if (moment.authorId == currentUserId) return@filter true
            moment.scheduledDate?.let { !it.after(now) } ?: true
        }
    }

    suspend fun updateActiveHours(userId: String, startHour: String, endHour: String) {
        db.collection("users").document(userId).update(
            mapOf(
                "activeHoursStart" to startHour,
                "activeHoursEnd" to endHour,
                "notificationTimeZone" to java.util.TimeZone.getDefault().id,
            ),
        ).await()
    }

    suspend fun clearActiveHours(userId: String) {
        db.collection("users").document(userId).update(
            mapOf("activeHoursStart" to FieldValue.delete(), "activeHoursEnd" to FieldValue.delete()),
        ).await()
    }

    suspend fun updateNotificationPreferences(userId: String, preferences: Map<String, Boolean>) {
        db.collection("users").document(userId)
            .update("notificationPreferences", preferences).await()
    }

    suspend fun canViewContent(currentUserId: String, targetUserId: String): Boolean {
        if (currentUserId == targetUserId) return true
        val target = fetchUserProfile(targetUserId)
        if (target.blockedUsers.contains(currentUserId)) return false
        val current = fetchUserProfile(currentUserId)
        if (current.blockedUsers.contains(targetUserId)) return false
        if (!target.isPrivate) return true
        return fetchMutuals(currentUserId).any { it.id == targetUserId }
    }

    suspend fun checkActiveHours(user: AppUser): Boolean {
        val start = user.activeHoursStart ?: return true
        val end = user.activeHoursEnd ?: return true
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val now = format.format(Date())
        return if (start <= end) now in start..end else now >= start || now <= end
    }

    internal suspend fun scheduleStorySummaryRebuildIfNeeded(userId: String) {
        if (userId.isEmpty()) return
        storySummaryRebuildMutex.withLock {
            val now = Date()
            storySummaryLastRebuildAttempt[userId]?.let { last ->
                if (now.time - last.time < storySummaryRebuildCooldownMs) return
            }
            if (userId in storySummaryRebuildInFlight) return
            storySummaryRebuildInFlight.add(userId)
            storySummaryLastRebuildAttempt[userId] = now
        }
        runCatching { rebuildStorySummary(userId) }
        storySummaryRebuildInFlight.remove(userId)
    }

    /** Prefetch saved moments for feed cards (iOS `loadSavedMoments`). */
    suspend fun loadSavedMoments(userId: String) {
        if (userId.isEmpty()) return
        runCatching {
            db.collection("users").document(userId).collection("savedMoments").get().await()
        }
    }

    suspend fun deleteMoment(userId: String, momentId: String) {
        db.collection("users").document(userId)
            .collection("moments").document(momentId)
            .delete()
            .await()
    }
}
