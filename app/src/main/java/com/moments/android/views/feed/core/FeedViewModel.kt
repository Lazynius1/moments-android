package com.moments.android.views.feed.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.content.BackendFeedService
import com.moments.android.services.content.FeedCursor
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.content.ForYouDiscoveryService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.performance.VideoMomentsIndex
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewMomentEnhanced
import com.moments.android.services.privacy.canUserViewStoryEnhanced
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.views.feed.controls.FeedType
import com.moments.android.views.feed.controls.FeedTypePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de `FeedViewModel.swift` — caches duales following/forYou, paginación
 * backend, pause/resume, shutdown, privacy filter + listeners de visibilidad.
 */
class FeedViewModel {
    var moments by mutableStateOf<List<FeedMoment>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var userProfileImage by mutableStateOf<String?>(null)
    var currentFeedType by mutableStateOf(FeedType.Following)
    var isPausedForUploads by mutableStateOf(false)
    var forYouMoments by mutableStateOf<List<FeedMoment>>(emptyList())
    var followingMoments by mutableStateOf<List<FeedMoment>>(emptyList())
    var connections by mutableStateOf<List<FeedConnection>>(emptyList())
    var followers by mutableStateOf<List<FeedFollowerRecord>>(emptyList())

    private val cursors = mutableMapOf<FeedType, FeedCursor?>()
    private val reachedEnd = mutableMapOf(
        FeedType.Following to false,
        FeedType.ForYou to false,
    )
    private val feedLoadedFromBackend = mutableMapOf(
        FeedType.Following to false,
        FeedType.ForYou to false,
    )
    private var activeJob: Job? = null
    private var momentListeners = mutableMapOf<String, ListenerRegistration>()
    private var commentListeners = mutableMapOf<String, ListenerRegistration>()
    private var latestVisibilitySnapshot: Map<String, Float> = emptyMap()
    private val listenerVisibilityThreshold = 0.08f
    private val listenerIndexBuffer = 5
    private val updateDebounceMs = 300L
    private var listenerSyncJob: Job? = null
    private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingUpdateJobs = mutableMapOf<String, Job>()
    private val lastUpdateHashes = mutableMapOf<String, Int>()
    private val privacyDecisionCache = ConcurrentHashMap<String, Boolean>()
    private val privacyInFlight = ConcurrentHashMap<String, MutableList<(Boolean) -> Unit>>()
    private val privacyMutex = Mutex()
    private val firestoreService = FirestoreService()
    private var appContext: Context? = null

    private var userListener: ListenerRegistration? = null
    private var pauseResumeJob: Job? = null

    // iOS mutedUserIdsCache + TTL 20s
    private var mutedUserIdsCache: Set<String> = emptySet()
    private var mutedUserIdsCacheTimestampMs: Long = 0L
    private val mutedUserIdsCacheTtlMs = 20_000L
    private var cachedFollowingIds: Set<String> = emptySet()
    private var forYouLegacyGlobalStreamCursor: ForYouDiscoveryService.GlobalStreamCursor? = null

    // Rename maps to match iOS naming mentally: backendCursors / backendReachedEnd
    private val backendCursors get() = cursors
    private val backendReachedEnd get() = reachedEnd

    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    private suspend fun resolveMutedUserIds(
        viewerId: String,
        forceRefresh: Boolean = false,
    ): Set<String> {
        if (viewerId.isEmpty()) return emptySet()
        val age = System.currentTimeMillis() - mutedUserIdsCacheTimestampMs
        if (!forceRefresh && age < mutedUserIdsCacheTtlMs) {
            return mutedUserIdsCache
        }
        val muted = runCatching { firestoreService.fetchMutedUserIds(viewerId) }.getOrDefault(emptySet())
        mutedUserIdsCache = muted
        mutedUserIdsCacheTimestampMs = System.currentTimeMillis()
        return muted
    }

    private suspend fun resolveFollowingIds(viewerId: String): Set<String> {
        return runCatching {
            firestoreService.fetchFollowing(viewerId).map { it.id }.toSet()
        }.getOrDefault(emptySet()).also { cachedFollowingIds = it }
    }

    private fun sortMomentsChronologically(
        list: List<FeedMoment>,
        limit: Int? = null,
    ): List<FeedMoment> {
        val sorted = list.sortedByDescending { it.timestamp }
        return if (limit != null) sorted.take(limit) else sorted
    }

    /**
     * Port de `applyForYouClientTuning` —
     * exclude followed authors + viewer; optional affinity reorder.
     */
    private fun applyForYouClientTuning(
        moments: List<FeedMoment>,
        followingIds: Set<String>,
        viewerId: String,
        preserveOrder: Boolean,
    ): List<FeedMoment> {
        val filtered = moments.filter { moment ->
            moment.authorId != viewerId && moment.authorId !in followingIds
        }
        if (preserveOrder) return filtered
        val affinityScores = runCatching {
            AffinityTracker.getScores(filtered.map { it.authorId })
        }.getOrDefault(emptyMap())
        return filtered
            .map { moment ->
                val base = moment.timestamp.toDouble()
                val affinity = affinityScores[moment.authorId] ?: 0.0
                moment to (base + affinity * 500.0)
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun preloadFeedVideos(list: List<FeedMoment>) {
        val urls = list.asSequence()
            .flatMap { it.visibleMediaItems.asSequence() }
            .filter { it.type.equals("video", ignoreCase = true) }
            .map { it.url }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
            .toList()
        if (urls.isNotEmpty()) VideoPreloader.preloadAssets(urls)
        // VideoMomentsIndex espera Moment; reconstrucción vía conversión ligera
        VideoMomentsIndex.rebuild(list.map { it.toIndexMoment() })
    }

    /** iOS FeedListSection.onChange(moments.count) → VideoMomentsIndex.shared.rebuild */
    fun rebuildVideoMomentsIndex() {
        VideoMomentsIndex.rebuild(moments.map { it.toIndexMoment() })
    }

    /** iOS VideoPlaybackSelector.shared.preloadURLStrings(from:maxMoments:) */
    fun videoPreloadUrls(from: List<FeedMoment>, maxMoments: Int = 4): List<String> =
        VideoPlaybackSelector.preloadUrlStrings(from.map { it.toIndexMoment() }, maxMoments)

    /** Port 1:1 de `fetchMoments(userId:feedType:)`. */
    fun fetchMoments(scope: CoroutineScope, userId: String, feedType: FeedType? = null) {
        val targetFeedType = feedType ?: currentFeedType
        val cached = loadFeedFromCache(targetFeedType)

        currentFeedType = targetFeedType
        isLoading = true
        errorMessage = null

        if (cached.isNotEmpty() && moments.isEmpty()) {
            applyCached(targetFeedType, cached)
            preloadFeedVideos(cached)
            isLoading = false
        }

        if (!NetworkMonitor.isConnected) {
            if (cached.isNotEmpty()) {
                applyCached(targetFeedType, cached)
                preloadFeedVideos(cached)
            }
            isLoading = false
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            val mutedUserIds = resolveMutedUserIds(userId)
            val visibleCached = cached.filter { it.authorId !in mutedUserIds }
            if (visibleCached.isNotEmpty()) {
                applyCached(targetFeedType, visibleCached)
                preloadFeedVideos(visibleCached)
            }

            clearListeners()

            when (targetFeedType) {
                FeedType.Following -> fetchFollowingMoments(userId)
                FeedType.ForYou -> fetchForYouMoments(userId)
            }
        }
    }

    /** Alias compat — algunos call sites pasan solo scope + feedType. */
    fun fetchMoments(scope: CoroutineScope, feedType: FeedType) {
        val uid = viewerId ?: return
        fetchMoments(scope, uid, feedType)
    }

    /** Port 1:1 de `loadMoreMoments(userId:)`. */
    fun loadMoreMoments(scope: CoroutineScope, userId: String) {
        if (isLoadingMore || isPausedForUploads) return
        isLoadingMore = true
        val feed = currentFeedType

        if (feedLoadedFromBackend[feed] == true) {
            if (backendReachedEnd[feed] == true) {
                isLoadingMore = false
                return
            }
            val cursor = backendCursors[feed]
            if (cursor == null) {
                backendReachedEnd[feed] = true
                isLoadingMore = false
                return
            }

            scope.launch {
                val mutedUserIds = resolveMutedUserIds(userId)
                val result = BackendFeedService.fetchFeedPage(
                    feedType = feed.rawValue,
                    cursor = cursor,
                    limit = 20,
                )
                if (result != null) {
                    var newMoments = result.moments
                        .filter { it.isArchived != true }
                        .filter { it.authorId !in mutedUserIds }
                    newMoments = if (feed == FeedType.ForYou) {
                        applyForYouClientTuning(
                            moments = newMoments,
                            followingIds = cachedFollowingIds,
                            viewerId = userId,
                            preserveOrder = true,
                        )
                    } else {
                        sortMomentsChronologically(newMoments)
                    }
                    val existingIds = moments.map { it.id }.toSet()
                    val uniqueNew = newMoments.filter { it.id !in existingIds }

                    if (result.nextCursor != null) {
                        backendCursors[feed] = result.nextCursor
                        backendReachedEnd[feed] = false
                    } else {
                        backendCursors[feed] = null
                        backendReachedEnd[feed] = true
                    }

                    moments = moments + uniqueNew
                    when (feed) {
                        FeedType.Following -> followingMoments = followingMoments + uniqueNew
                        FeedType.ForYou -> forYouMoments = forYouMoments + uniqueNew
                    }
                    isLoadingMore = false
                    saveFeedToCache(feed, momentsFor(feed))
                    preloadFeedVideos(uniqueNew)
                } else {
                    feedLoadedFromBackend[feed] = false
                    loadMoreMomentsLegacy(scope, userId)
                }
            }
            return
        }

        loadMoreMomentsLegacy(scope, userId)
    }

    fun loadMore(scope: CoroutineScope) {
        val uid = viewerId ?: return
        loadMoreMoments(scope, uid)
    }

    private fun loadMoreMomentsLegacy(scope: CoroutineScope, userId: String) {
        scope.launch {
            when (currentFeedType) {
                FeedType.Following -> {
                    val ids = if (cachedFollowingIds.isNotEmpty()) {
                        cachedFollowingIds
                    } else {
                        resolveFollowingIds(userId)
                    }
                    if (ids.isEmpty()) {
                        isLoadingMore = false
                        return@launch
                    }
                    fetchMoreMomentsFromUsers(ids.toList(), userId, FeedType.Following)
                }
                FeedType.ForYou -> fetchMoreForYouMoments(userId)
            }
        }
    }

    fun refresh(scope: CoroutineScope, onComplete: (() -> Unit)? = null) {
        val uid = viewerId ?: return
        scope.launch {
            isRefreshing = true
            refreshMoments(uid)
            isRefreshing = false
            onComplete?.invoke()
        }
    }

    /** Alias iOS `refreshMoments(userId:)`. */
    suspend fun refreshMoments(userId: String) {
        cursors.clear()
        feedLoadedFromBackend[FeedType.Following] = false
        feedLoadedFromBackend[FeedType.ForYou] = false
        reachedEnd[FeedType.Following] = false
        reachedEnd[FeedType.ForYou] = false
        forYouLegacyGlobalStreamCursor = null
        clearListeners()
        mutedUserIdsCache = emptySet()
        mutedUserIdsCacheTimestampMs = 0L
        // iOS kicks fetchMoments then sleeps 0.5s
        val scopeJob = listenerScope
        fetchMoments(scopeJob, userId, currentFeedType)
        delay(500)
    }

    /**
     * Port 1:1 de `switchFeedType(to:userId:)`.
     */
    fun switchFeedType(scope: CoroutineScope, feedType: FeedType, userId: String? = null) {
        val uid = userId ?: viewerId ?: return
        currentFeedType = feedType
        clearListeners()
        when (feedType) {
            FeedType.Following -> {
                if (followingMoments.isNotEmpty()) {
                    moments = followingMoments
                } else {
                    moments = emptyList()
                    isLoading = true
                    fetchMoments(scope, uid, feedType)
                }
            }
            FeedType.ForYou -> {
                if (forYouMoments.isNotEmpty()) {
                    moments = forYouMoments
                } else {
                    moments = emptyList()
                    isLoading = true
                    fetchMoments(scope, uid, feedType)
                }
            }
        }
    }

    private suspend fun fetchFollowingMoments(userId: String) {
        val mutedUserIds = resolveMutedUserIds(userId)
        cachedFollowingIds = resolveFollowingIds(userId)
        val result = BackendFeedService.fetchFeedPage(feedType = "following", limit = 40)
        if (result != null) {
            val finalMoments = sortMomentsChronologically(
                result.moments
                    .filter { it.isArchived != true }
                    .filter { it.authorId !in mutedUserIds },
            )
            isLoading = false
            followingMoments = finalMoments
            moments = finalMoments
            preloadFeedVideos(finalMoments)
            feedLoadedFromBackend[FeedType.Following] = true
            if (result.nextCursor != null) {
                backendCursors[FeedType.Following] = result.nextCursor
                backendReachedEnd[FeedType.Following] = false
            } else {
                backendCursors[FeedType.Following] = null
                backendReachedEnd[FeedType.Following] = true
            }
            saveFeedToCache(FeedType.Following, finalMoments)
            return
        }
        feedLoadedFromBackend[FeedType.Following] = false
        backendCursors[FeedType.Following] = null
        backendReachedEnd[FeedType.Following] = false
        fetchFollowingMomentsLegacy(userId)
    }

    private suspend fun fetchFollowingMomentsLegacy(userId: String) {
        val followingUsers = runCatching { firestoreService.fetchFollowing(userId) }.getOrNull()
        if (followingUsers == null) {
            isLoading = false
            followingMoments = emptyList()
            moments = emptyList()
            errorMessage = "feed_error"
            return
        }
        val targetUserIds = followingUsers.map { it.id }
        cachedFollowingIds = targetUserIds.toSet()
        if (targetUserIds.isEmpty()) {
            isLoading = false
            followingMoments = emptyList()
            moments = emptyList()
            return
        }
        fetchMomentsFromUsers(targetUserIds, userId, FeedType.Following)
    }

    private suspend fun fetchForYouMoments(userId: String) {
        val mutedUserIds = resolveMutedUserIds(userId)
        cachedFollowingIds = resolveFollowingIds(userId)
        val result = BackendFeedService.fetchFeedPage(feedType = "forYou", limit = 60)
        if (result != null) {
            val finalMoments = applyForYouClientTuning(
                moments = result.moments
                    .filter { it.isArchived != true }
                    .filter { it.authorId !in mutedUserIds },
                followingIds = cachedFollowingIds,
                viewerId = userId,
                preserveOrder = true,
            )
            isLoading = false
            forYouMoments = finalMoments
            moments = finalMoments
            preloadFeedVideos(finalMoments)
            feedLoadedFromBackend[FeedType.ForYou] = true
            if (result.nextCursor != null) {
                backendCursors[FeedType.ForYou] = result.nextCursor
                backendReachedEnd[FeedType.ForYou] = false
            } else {
                backendCursors[FeedType.ForYou] = null
                backendReachedEnd[FeedType.ForYou] = true
            }
            saveFeedToCache(FeedType.ForYou, finalMoments)
            return
        }
        feedLoadedFromBackend[FeedType.ForYou] = false
        backendCursors[FeedType.ForYou] = null
        backendReachedEnd[FeedType.ForYou] = false
        fetchForYouMomentsLegacy(userId)
    }

    private suspend fun fetchForYouMomentsLegacy(userId: String) {
        forYouLegacyGlobalStreamCursor = null
        try {
            val finalMoments = loadLegacyForYouPage(
                userId = userId,
                existingMomentIds = emptySet(),
                isInitialLoad = true,
            )
            isLoading = false
            forYouMoments = finalMoments
            moments = finalMoments
            preloadFeedVideos(finalMoments)
            saveFeedToCache(FeedType.ForYou, finalMoments)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            isLoading = false
            errorMessage = t.localizedMessage
        }
    }

    private suspend fun loadLegacyForYouPage(
        userId: String,
        existingMomentIds: Set<String>,
        isInitialLoad: Boolean,
    ): List<FeedMoment> {
        val user = firestoreService.fetchUserProfile(userId)
        var followingIds = cachedFollowingIds
        if (followingIds.isEmpty()) {
            followingIds = resolveFollowingIds(userId)
        }
        val followerIds = runCatching {
            firestoreService.fetchFollowers(userId).map { it.id }.toSet()
        }.getOrDefault(emptySet())
        cachedFollowingIds = followingIds

        val blockedUserIds = user.blockedUsers.toSet()
        val excludingAuthors = followingIds + userId + blockedUserIds
        val streamCursor = if (isInitialLoad) null else forYouLegacyGlobalStreamCursor

        val discovery = ForYouDiscoveryService.loadDiscoveryAuthors(
            viewerId = userId,
            interests = user.interests,
            followingIds = followingIds,
            followerIds = followerIds,
            blockedUserIds = blockedUserIds,
        )

        val authorMoments = runCatching {
            firestoreService.fetchMomentsFromUsers(
                userIds = discovery.authorIds,
                perUserLimit = 8,
                totalLimit = 120,
            )
        }.getOrDefault(emptyList())

        val (globalMoments, nextCursor) = ForYouDiscoveryService.fetchGlobalEveryoneMoments(
            excludingAuthorIds = excludingAuthors,
            excludingMomentIds = existingMomentIds,
            globalStreamCursor = streamCursor,
        )
        if (nextCursor != null) {
            forYouLegacyGlobalStreamCursor = nextCursor
        }

        val mergedById = linkedMapOf<String, Moment>()
        for (moment in authorMoments + globalMoments) {
            val momentId = moment.id ?: continue
            if (momentId in existingMomentIds) continue
            mergedById[momentId] = moment
        }

        val visible = filterMomentsForPrivacy(
            userId,
            mergedById.values.map { it.toFeedMoment() },
        )
        val tuned = applyForYouClientTuning(
            moments = visible,
            followingIds = followingIds,
            viewerId = userId,
            preserveOrder = false,
        )
        return if (isInitialLoad) tuned.take(60) else tuned
    }

    private suspend fun fetchMoreForYouMoments(userId: String) {
        val existingMomentIds = moments.map { it.id }.toSet()
        try {
            val newMoments = loadLegacyForYouPage(
                userId = userId,
                existingMomentIds = existingMomentIds,
                isInitialLoad = false,
            )
            isLoadingMore = false
            if (newMoments.isEmpty()) return
            forYouMoments = forYouMoments + newMoments
            moments = moments + newMoments
            saveFeedToCache(FeedType.ForYou, forYouMoments)
            preloadFeedVideos(newMoments)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Throwable) {
            isLoadingMore = false
        }
    }

    private suspend fun fetchMoreMomentsFromUsers(
        userIds: List<String>,
        userId: String,
        feedType: FeedType,
    ) {
        val limitPerUser = if (feedType == FeedType.ForYou) 8 else 12
        val totalLimit = if (feedType == FeedType.ForYou) 120 else 80
        val existingMomentIds = moments.map { it.id }.toSet()
        val fetched = runCatching {
            firestoreService.fetchMomentsFromUsers(
                userIds = userIds,
                perUserLimit = limitPerUser,
                totalLimit = totalLimit,
            )
        }.getOrElse {
            isLoadingMore = false
            return
        }

        val filteredNew = fetched.map { it.toFeedMoment() }
            .filter { it.isArchived != true }
            .filter { it.id !in existingMomentIds }
        val sortedNew = filteredNew.sortedByDescending { it.timestamp }
        val tuned = when (feedType) {
            FeedType.Following -> sortedNew
            FeedType.ForYou -> applyForYouClientTuning(
                moments = sortedNew,
                followingIds = cachedFollowingIds,
                viewerId = userId,
                preserveOrder = false,
            )
        }
        val filtered = filterMomentsForPrivacy(userId, tuned)
        isLoadingMore = false
        when (feedType) {
            FeedType.ForYou -> {
                forYouMoments = forYouMoments + filtered
                moments = moments + filtered
            }
            FeedType.Following -> {
                followingMoments = followingMoments + filtered
                moments = moments + filtered
            }
        }
        preloadFeedVideos(filtered)
    }

    /**
     * Paridad iOS `fetchConnections` → `firestoreService.fetchFollowing` (subcolección `following`).
     * Nunca `connections` (LEGACY denegada en rules; iOS no la escribe).
     */
    fun fetchConnections(scope: CoroutineScope, userId: String) {
        scope.launch {
            runCatching { firestoreService.fetchFollowing(userId) }
            // iOS descarta el Result; no asigna a `connections`.
        }
    }

    /** Paridad iOS `fetchFollowers` → `fetchFollowersWithTimestamps`. */
    fun fetchFollowers(scope: CoroutineScope, userId: String) {
        scope.launch {
            try {
                val rows = firestoreService.fetchFollowersWithTimestamps(userId)
                followers = rows.map { (user, timestamp) ->
                    // Paridad iOS FollowerRecord(id:userId:timestamp:) — sin username inventado
                    FeedFollowerRecord(
                        id = user.id,
                        userId = user.id,
                        timestamp = timestamp,
                    )
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Throwable) {
                errorMessage = e.message
                followers = emptyList()
            }
        }
    }

    fun removeMoment(momentId: String) {
        moments = moments.filterNot { it.id == momentId }
        followingMoments = followingMoments.filterNot { it.id == momentId }
        forYouMoments = forYouMoments.filterNot { it.id == momentId }
        removeMomentListeners(momentId)
    }

    /**
     * Port de `syncMomentListeners(visibilityByMomentId:)` —
     * debounce 0.2s + buffer de índices alrededor de momentos visibles.
     */
    fun syncMomentListeners(visibilityByMomentId: Map<String, Float>) {
        if (isPausedForUploads) return
        latestVisibilitySnapshot = visibilityByMomentId
        listenerSyncJob?.cancel()
        listenerSyncJob = listenerScope.launch {
            delay(200)
            performSyncMomentListeners(latestVisibilitySnapshot)
        }
    }

    private fun performSyncMomentListeners(visibilityByMomentId: Map<String, Float>) {
        if (isPausedForUploads) return
        val eligibleIds = linkedSetOf<String>()
        val authorByMomentId = mutableMapOf<String, String>()

        moments.forEachIndexed { index, moment ->
            authorByMomentId[moment.id] = moment.authorId
            val fraction = visibilityByMomentId[moment.id] ?: 0f
            if (fraction < listenerVisibilityThreshold) return@forEachIndexed

            val lower = maxOf(0, index - listenerIndexBuffer)
            val upper = minOf(moments.lastIndex, index + listenerIndexBuffer)
            for (bufferIndex in lower..upper) {
                eligibleIds.add(moments[bufferIndex].id)
            }
        }

        for (momentId in eligibleIds) {
            val authorId = authorByMomentId[momentId] ?: continue
            listenForCommentUpdates(momentId, authorId)
        }

        val activeIds = momentListeners.keys + commentListeners.keys
        for (momentId in activeIds) {
            if (momentId !in eligibleIds) removeMomentListeners(momentId)
        }
    }

    fun listenForCommentUpdates(momentId: String, authorId: String) {
        if (commentListeners.containsKey(momentId) || momentListeners.containsKey(momentId)) return
        if (isPausedForUploads) return

        val currentUserId = viewerId ?: return

        // Placeholder registration so concurrent calls don't double-attach while permission resolves.
        momentListeners[momentId] = FirebaseFirestore.getInstance()
            .collection("users").document(authorId)
            .collection("moments").document(momentId)
            .addSnapshotListener { _, _ -> }

        listenerScope.launch {
            val canView = runCatching {
                firestoreService.canViewContent(currentUserId, authorId)
            }.getOrDefault(false)
            if (!canView) {
                removeMomentListeners(momentId)
                return@launch
            }
            if (!momentListeners.containsKey(momentId)) return@launch

            // Replace placeholder with real moment listener.
            momentListeners.remove(momentId)?.remove()

            val momentListener = FirebaseFirestore.getInstance()
                .collection("users").document(authorId)
                .collection("moments").document(momentId)
                .addSnapshotListener { document, error ->
                    if (error != null || document == null || !document.exists()) return@addSnapshotListener
                    if (isPausedForUploads) return@addSnapshotListener
                    val data = document.data ?: return@addSnapshotListener
                    listenerScope.launch {
                        if (!shouldUpdateMoment(momentId, data)) return@launch
                        debouncedUpdateMoment(momentId, data)
                    }
                }
            momentListeners[momentId] = momentListener

            if (commentListeners.containsKey(momentId)) return@launch
            val commentListener = FirebaseFirestore.getInstance()
                .collection("users").document(authorId)
                .collection("moments").document(momentId)
                .collection("comments")
                .addSnapshotListener { _, _ ->
                    // iOS postea NotificationCenter "CommentAdded"; cards escuchan vía state.
                }
            commentListeners[momentId] = commentListener
        }
    }

    fun removeCommentListener(momentId: String) {
        commentListeners.remove(momentId)?.remove()
    }

    fun removeMomentListeners(momentId: String) {
        momentListeners.remove(momentId)?.remove()
        removeCommentListener(momentId)
        pendingUpdateJobs.remove(momentId)?.cancel()
        lastUpdateHashes.remove(momentId)
    }

    fun pauseListenersForUpload() {
        isPausedForUploads = true
        // iOS: auto-resume después de 10 segundos (safety)
        pauseResumeJob?.cancel()
        pauseResumeJob = listenerScope.launch {
            delay(10_000)
            resumeListenersAfterUpload()
        }
    }

    fun resumeListenersAfterUpload() {
        pauseResumeJob?.cancel()
        pauseResumeJob = null
        isPausedForUploads = false
        // iOS solo baja el flag; re-sync ayuda a reenganchar viewport actual
        syncMomentListeners(latestVisibilitySnapshot)
    }

    fun shutdown() {
        activeJob?.cancel()
        activeJob = null
        listenerSyncJob?.cancel()
        listenerSyncJob = null
        pauseResumeJob?.cancel()
        pauseResumeJob = null
        pendingUpdateJobs.values.forEach { it.cancel() }
        pendingUpdateJobs.clear()
        isPausedForUploads = false
        clearListeners()
        userListener?.remove()
        userListener = null
    }

    fun fetchUserData(scope: CoroutineScope, userId: String) {
        // iOS: snapshot listener sobre users/{id} → profileImagePath
        userListener?.remove()
        userListener = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .addSnapshotListener { document, error ->
                if (error != null || document == null) return@addSnapshotListener
                val data = document.data ?: return@addSnapshotListener
                userProfileImage = data["profileImagePath"] as? String
                    ?: data["profileImageURL"] as? String
                    ?: data["profileImageUrl"] as? String
            }
        // scope reserved for API parity with other fetch* helpers
        @Suppress("UNUSED_PARAMETER")
        val unused = scope
    }

    /** Paridad iOS: solo borra `selectedFeedType` — no wipea el feed. */
    fun resetFeedPreferences() {
        val ctx = appContext ?: return
        FeedTypePreferences.clear(ctx)
    }

    fun trackFeedUsage() {
        // Analytics hook — mirror iOS no-op-safe call site.
    }

    /** Port de `filterStoriesForVisibility` iOS. */
    fun filterStoriesForVisibility(
        viewerId: String,
        stories: List<Story>,
        completion: (List<Story>) -> Unit,
    ) {
        listenerScope.launch {
            if (viewerId.isEmpty() || stories.isEmpty()) {
                completion(emptyList())
                return@launch
            }
            val visibleIds = mutableSetOf<String>()
            for (story in stories) {
                val canSee = withTimeoutOrNull(8_000L) {
                    PrivacyService.canUserViewStoryEnhanced(story, viewerId)
                } ?: false
                val id = story.id
                if (canSee && !id.isNullOrEmpty()) visibleIds.add(id)
            }
            completion(stories.filter { it.id != null && it.id in visibleIds })
        }
    }

    // MARK: - Privacy Filter (port iOS filterMomentsForPrivacy)

    private suspend fun filterMomentsForPrivacy(
        viewerId: String,
        moments: List<FeedMoment>,
    ): List<FeedMoment> {
        if (viewerId.isEmpty() || moments.isEmpty()) return emptyList()
        val batchSize = 10
        val filtered = mutableListOf<FeedMoment>()
        var start = 0
        while (start < moments.size) {
            val end = minOf(start + batchSize, moments.size)
            val batch = moments.subList(start, end)
            val visibleIds = mutableSetOf<String>()
            for (moment in batch) {
                if (moment.id.isEmpty()) continue
                if (evaluateMomentAccess(moment, viewerId)) {
                    visibleIds.add(moment.id)
                }
            }
            filtered += batch.filter { it.id in visibleIds }
            start = end
        }
        return filtered
    }

    private suspend fun evaluateMomentAccess(moment: FeedMoment, viewerId: String): Boolean {
        if (moment.authorId == viewerId) return true
        val cacheKey = privacyDecisionCacheKey(moment, viewerId)
        privacyDecisionCache[cacheKey]?.let { return it }

        // Coalesce in-flight decisions for the same key (paridad iOS).
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val shouldFetch = privacyMutex.withLock {
            privacyDecisionCache[cacheKey]?.let {
                deferred.complete(it)
                return@withLock false
            }
            val waiters = privacyInFlight[cacheKey]
            if (waiters != null) {
                waiters.add { canView -> deferred.complete(canView) }
                false
            } else {
                privacyInFlight[cacheKey] =
                    mutableListOf<(Boolean) -> Unit>({ canView -> deferred.complete(canView) })
                true
            }
        }

        if (shouldFetch) {
            val canView = withTimeoutOrNull(8_000L) {
                PrivacyService.canUserViewMomentEnhanced(moment.toPrivacyMoment(), viewerId)
            } ?: false // fail closed
            privacyMutex.withLock {
                privacyDecisionCache[cacheKey] = canView
                val callbacks = privacyInFlight.remove(cacheKey).orEmpty()
                callbacks.forEach { it(canView) }
            }
        }
        return deferred.await()
    }

    private fun privacyDecisionCacheKey(moment: FeedMoment, viewerId: String): String {
        val audience = moment.audience ?: "everyone"
        val base = "$viewerId|${moment.authorId}|$audience"
        return when (audience) {
            "custom" -> "$base|moment:${moment.id}"
            "customList" -> {
                val listId = moment.customListId
                if (!listId.isNullOrEmpty()) "$base|list:$listId" else "$base|moment:${moment.id}"
            }
            else -> base
        }
    }

    private fun FeedMoment.toPrivacyMoment(): Moment = Moment(
        id = id,
        authorId = authorId,
        username = username,
        content = content,
        audience = audience,
        customListId = customListId,
        hasHiddenLayers = hasHiddenLayers,
        hiddenLayerCount = hiddenLayerCount,
        commentCount = commentCount,
        hideLikeCounts = hideLikeCounts,
        disableComments = disableComments,
        profileImagePath = profileImagePath,
        location = location,
        locationCoordinate = locationCoordinate,
        aspectRatio = aspectRatio,
        timestamp = Date(timestamp),
        isArchived = isArchived,
    )

    /** Conversión para VideoMomentsIndex / preloader (incluye media). */
    private fun FeedMoment.toIndexMoment(): Moment = toPrivacyMoment().copy(
        mediaItems = mediaItems.map { item ->
            MediaItem(
                id = item.id,
                type = MediaItem.MediaType.from(item.type),
                url = item.url,
                aspectRatio = item.aspectRatio,
                thumbnailUrl = item.thumbnailUrl,
                videoDuration = item.videoDuration,
                tags = item.tags,
                moderationState = if (item.isHiddenByModeration) {
                    MediaItem.ModerationState.HIDDEN
                } else {
                    null
                },
            )
        },
    )

    private fun Moment.toFeedMoment(): FeedMoment {
        val media = visibleMediaItems.map { item ->
            com.moments.android.services.content.FeedMediaItem(
                id = item.id,
                type = item.type.raw,
                url = item.url,
                thumbnailUrl = item.thumbnailUrl,
                aspectRatio = item.aspectRatio,
                isHiddenByModeration = item.isHiddenByModeration,
                tags = item.tags,
                videoDuration = item.videoDuration,
            )
        }
        return FeedMoment(
            id = id.orEmpty(),
            authorId = authorId,
            username = username,
            content = content,
            timestamp = timestamp.time,
            profileImagePath = profileImagePath,
            location = location,
            mediaItems = media,
            aspectRatio = aspectRatio,
            commentCount = commentCount,
            reactionCount = reactions.values.sumOf { it.size },
            hideLikeCounts = hideLikeCounts,
            disableComments = disableComments,
            hasHiddenLayers = hasHiddenLayers,
            hiddenLayerCount = hiddenLayerCount,
            audience = audience,
            customListId = customListId,
            isArchived = isArchived,
            locationCoordinate = locationCoordinate,
        )
    }

    private suspend fun fetchMomentsFromUsers(
        userIds: List<String>,
        userId: String,
        feedType: FeedType,
    ) {
        val limitPerUser = if (feedType == FeedType.ForYou) 8 else 12
        val totalLimit = if (feedType == FeedType.ForYou) 120 else 80
        val fetched = runCatching {
            firestoreService.fetchMomentsFromUsers(
                userIds = userIds,
                perUserLimit = limitPerUser,
                totalLimit = totalLimit,
            )
        }.getOrElse { emptyList() }

        val mapped = fetched.map { it.toFeedMoment() }.filter { it.isArchived != true }
        val tuned: List<FeedMoment> = when (feedType) {
            FeedType.Following -> sortMomentsChronologically(mapped, limit = 40)
            FeedType.ForYou -> applyForYouClientTuning(
                moments = mapped,
                followingIds = cachedFollowingIds,
                viewerId = userId,
                preserveOrder = false,
            ).take(60)
        }

        val filtered = filterMomentsForPrivacy(userId, tuned)
        isLoading = false
        when (feedType) {
            FeedType.Following -> followingMoments = filtered
            FeedType.ForYou -> forYouMoments = filtered
        }
        moments = filtered
        preloadFeedVideos(filtered)
        saveFeedToCache(feedType, filtered)
    }

    private fun shouldUpdateMoment(momentId: String, data: Map<String, Any?>): Boolean {
        if (moments.none { it.id == momentId }) return true
        val newHash = generateMomentHash(data)
        val current = lastUpdateHashes[momentId] ?: 0
        if (newHash != current) {
            lastUpdateHashes[momentId] = newHash
            return true
        }
        return false
    }

    private fun generateMomentHash(data: Map<String, Any?>): Int {
        @Suppress("UNCHECKED_CAST")
        val reactions = data["reactions"] as? Map<String, List<*>> ?: emptyMap()
        var hash = reactions.size
        reactions.toSortedMap().forEach { (type, users) ->
            hash = 31 * hash + type.hashCode()
            hash = 31 * hash + users.size
        }
        hash = 31 * hash + ((data["commentCount"] as? Number)?.toInt() ?: 0)
        hash = 31 * hash + (data["content"] as? String).orEmpty().hashCode()
        hash = 31 * hash + ((data["timestamp"] as? Number)?.toLong() ?: 0L).hashCode()
        hash = 31 * hash + (data["imageUrl"] as? String).orEmpty().hashCode()
        hash = 31 * hash + (data["videoUrl"] as? String).orEmpty().hashCode()
        hash = 31 * hash + (data["aspectRatio"] as? String).orEmpty().hashCode()
        hash = 31 * hash + ((data["hasHiddenLayers"] as? Boolean) == true).hashCode()
        hash = 31 * hash + ((data["hiddenLayerCount"] as? Number)?.toInt() ?: 0)
        return hash
    }

    private fun debouncedUpdateMoment(momentId: String, data: Map<String, Any?>) {
        pendingUpdateJobs.remove(momentId)?.cancel()
        pendingUpdateJobs[momentId] = listenerScope.launch {
            delay(updateDebounceMs)
            applyLiveMomentData(momentId, data)
            pendingUpdateJobs.remove(momentId)
        }
    }

    private fun applyLiveMomentData(momentId: String, data: Map<String, Any?>) {
        if (data["isArchived"] as? Boolean == true) {
            removeMoment(momentId)
            saveFeedToCache(FeedType.Following, followingMoments)
            saveFeedToCache(FeedType.ForYou, forYouMoments)
            return
        }

        @Suppress("UNCHECKED_CAST")
        val reactions = data["reactions"] as? Map<String, List<*>>
        val reactionCount = reactions?.values?.sumOf { it.size } ?: -1

        fun patch(list: List<FeedMoment>): List<FeedMoment> = list.map { m ->
            if (m.id != momentId) m else m.copy(
                content = data["content"] as? String ?: m.content,
                commentCount = (data["commentCount"] as? Number)?.toInt() ?: m.commentCount,
                reactionCount = if (reactionCount >= 0) reactionCount else m.reactionCount,
                hasHiddenLayers = data["hasHiddenLayers"] as? Boolean ?: m.hasHiddenLayers,
                hiddenLayerCount = (data["hiddenLayerCount"] as? Number)?.toInt()
                    ?: m.hiddenLayerCount,
                audience = data["audience"] as? String ?: m.audience,
                customListId = data["customListId"] as? String ?: m.customListId,
                aspectRatio = data["aspectRatio"] as? String ?: m.aspectRatio,
                hideLikeCounts = data["hideLikeCounts"] as? Boolean ?: m.hideLikeCounts,
                disableComments = data["disableComments"] as? Boolean ?: m.disableComments,
            )
        }

        moments = patch(moments)
        followingMoments = patch(followingMoments)
        forYouMoments = patch(forYouMoments)
    }

    private fun clearListeners() {
        pendingUpdateJobs.values.forEach { it.cancel() }
        pendingUpdateJobs.clear()
        lastUpdateHashes.clear()
        momentListeners.values.forEach { it.remove() }
        momentListeners.clear()
        commentListeners.values.forEach { it.remove() }
        commentListeners.clear()
    }

    private fun applyCached(feedType: FeedType, cached: List<FeedMoment>) {
        moments = cached
        when (feedType) {
            FeedType.Following -> followingMoments = cached
            FeedType.ForYou -> forYouMoments = cached
        }
    }

    private suspend fun applyPage(
        feedType: FeedType,
        pageMoments: List<FeedMoment>,
        nextCursor: FeedCursor?,
        replace: Boolean,
    ) {
        val base = if (replace) emptyList() else momentsFor(feedType)
        val seen = base.map { it.id }.toMutableSet()
        val merged = base + pageMoments.filter { seen.add(it.id) }
        val uid = viewerId
        val filtered = if (uid.isNullOrEmpty()) {
            merged
        } else {
            filterMomentsForPrivacy(uid, merged)
        }
        moments = filtered
        when (feedType) {
            FeedType.Following -> followingMoments = filtered
            FeedType.ForYou -> forYouMoments = filtered
        }
        cursors[feedType] = nextCursor
        reachedEnd[feedType] = nextCursor == null
    }

    private fun momentsFor(feedType: FeedType): List<FeedMoment> = when (feedType) {
        FeedType.Following -> followingMoments
        FeedType.ForYou -> forYouMoments
    }

    private fun cacheKey(type: FeedType): String =
        "cached_feed_${if (type == FeedType.Following) "following" else "foryou"}"

    private fun saveFeedToCache(type: FeedType, list: List<FeedMoment>) {
        val ctx = appContext ?: return
        val arr = JSONArray()
        list.take(40).forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("authorId", m.authorId)
                    .put("username", m.username)
                    .put("content", m.content)
                    .put("timestamp", m.timestamp)
                    .put("profileImagePath", m.profileImagePath)
                    .put("location", m.location)
                    .put("aspectRatio", m.aspectRatio)
                    .put("commentCount", m.commentCount)
                    .put("reactionCount", m.reactionCount)
                    .put("hideLikeCounts", m.hideLikeCounts)
                    .put("disableComments", m.disableComments)
                    .put("hasHiddenLayers", m.hasHiddenLayers)
                    .put("hiddenLayerCount", m.hiddenLayerCount)
                    .put("audience", m.audience)
                    .put("customListId", m.customListId)
                    .also { obj ->
                        m.isArchived?.let { obj.put("isArchived", it) }
                        m.locationCoordinate?.let { coord ->
                            obj.put(
                                "locationCoordinate",
                                JSONObject()
                                    .put("latitude", coord.latitude)
                                    .put("longitude", coord.longitude),
                            )
                        }
                    }
                    .put(
                        "mediaItems",
                        JSONArray().also { mediaArr ->
                            m.mediaItems.forEach { item ->
                                mediaArr.put(
                                    JSONObject()
                                        .put("id", item.id)
                                        .put("type", item.type)
                                        .put("url", item.url)
                                        .put("thumbnailUrl", item.thumbnailUrl)
                                        .put("aspectRatio", item.aspectRatio),
                                )
                            }
                        },
                    ),
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(cacheKey(type), arr.toString())
            .apply()
        // Paridad iOS: también persiste en LocalPersistenceService (offline dual).
        runCatching {
            LocalPersistenceService.saveFeedMoments(list.map { it.toIndexMoment() })
        }
    }

    private fun loadFeedFromCache(type: FeedType): List<FeedMoment> {
        val ctx = appContext ?: return emptyList()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(cacheKey(type), null)
        if (raw != null) {
            val fromPrefs = runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val mediaArr = o.optJSONArray("mediaItems") ?: JSONArray()
                        val media = buildList {
                            for (j in 0 until mediaArr.length()) {
                                val m = mediaArr.getJSONObject(j)
                                add(
                                    com.moments.android.services.content.FeedMediaItem(
                                        id = m.optString("id"),
                                        type = m.optString("type"),
                                        url = m.optString("url"),
                                        thumbnailUrl = m.optString("thumbnailUrl").ifBlank { null },
                                        aspectRatio = m.optString("aspectRatio").ifBlank { null },
                                        isHiddenByModeration = m.optString("moderationState") == "hidden",
                                    ),
                                )
                            }
                        }
                        val coordObj = o.optJSONObject("locationCoordinate")
                        val locationCoordinate = if (coordObj != null) {
                            val lat = if (coordObj.has("latitude")) coordObj.optDouble("latitude") else null
                            val lon = if (coordObj.has("longitude")) coordObj.optDouble("longitude") else null
                            if (lat != null && lon != null) {
                                Moment.LocationCoordinate(lat, lon)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                        add(
                            FeedMoment(
                                id = o.optString("id"),
                                authorId = o.optString("authorId"),
                                username = o.optString("username"),
                                content = o.optString("content"),
                                timestamp = o.optLong("timestamp"),
                                profileImagePath = o.optString("profileImagePath").ifBlank { null },
                                location = o.optString("location").ifBlank { null },
                                mediaItems = media,
                                aspectRatio = o.optString("aspectRatio").ifBlank { null },
                                commentCount = o.optInt("commentCount"),
                                reactionCount = o.optInt("reactionCount"),
                                hideLikeCounts = o.optBoolean("hideLikeCounts"),
                                disableComments = o.optBoolean("disableComments"),
                                hasHiddenLayers = o.optBoolean("hasHiddenLayers"),
                                hiddenLayerCount = o.optInt("hiddenLayerCount"),
                                audience = o.optString("audience").ifBlank { null },
                                customListId = o.optString("customListId").ifBlank { null },
                                isArchived = if (o.has("isArchived")) o.optBoolean("isArchived") else null,
                                locationCoordinate = locationCoordinate,
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
            if (fromPrefs.isNotEmpty()) return fromPrefs.filter { it.isArchived != true }
        }
        // Paridad iOS: fallback LocalPersistence solo para following.
        if (type != FeedType.Following) return emptyList()
        return runCatching {
            LocalPersistenceService.loadFeedMoments()
                .map { it.toFeedMoment() }
                .filter { it.isArchived != true }
        }.getOrDefault(emptyList())
    }

    private fun clearCache() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(cacheKey(FeedType.Following))
            .remove(cacheKey(FeedType.ForYou))
            .apply()
    }

    val viewerId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    companion object {
        private const val PREFS = "moments_feed_cache"
    }
}

/** Paridad iOS `Connection` (Models.swift) — id/userId/timestamp. */
data class FeedConnection(
    val id: String,
    val userId: String,
    val timestamp: java.util.Date,
)

/** Paridad iOS `FollowerRecord` (Models.swift) — id/userId/timestamp. */
data class FeedFollowerRecord(
    val id: String,
    val userId: String,
    val timestamp: java.util.Date,
)
