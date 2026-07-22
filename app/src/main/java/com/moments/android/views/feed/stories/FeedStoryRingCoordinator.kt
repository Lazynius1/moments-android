package com.moments.android.views.feed.stories

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.services.content.BackendStoryTrayResponse
import com.moments.android.services.content.StoryRingCursor
import com.moments.android.services.content.StoryTrayService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.StoryAuthorSummary
import com.moments.android.services.firestore.fetchActiveStoriesForUsers
import com.moments.android.services.firestore.fetchStorySummariesForUsers
import com.moments.android.services.firestore.prefetchStoriesForUser
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.social.StoryRingCacheService
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.services.social.StoryRingSnapshot
import com.moments.android.views.feed.core.sections.FeedStoryUserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Port de `FeedStoryRingCoordinator.swift`. */
class FeedStoryRingCoordinator(
    private val firestoreService: FirestoreService = FirestoreService(),
    private val appContext: Context? = null,
) {
    var storyUsers by mutableStateOf<List<FeedStoryUserState>>(emptyList())
    var isLoadingStories by mutableStateOf(true)
    var isLoadingMoreRing by mutableStateOf(false)

    private var ringNextCursor: StoryRingCursor? = null
    private val ringPageSize = 16
    private val cachedStories = mutableMapOf<String, Boolean>()
    private val cachedUnseenStories = mutableMapOf<String, Boolean>()
    private var cachedStoriesTimestampMs = 0L
    private var prefetchJob: kotlinx.coroutines.Job? = null
    private var widgetReloadJob: kotlinx.coroutines.Job? = null

    val ringNavigationUserIds: List<String>
        get() = storyUsers.filter { it.hasStory }.map { it.userId }

    fun loadStoryUsers(scope: CoroutineScope, userId: String, allowInstantCache: Boolean = true) {
        scope.launch { reloadStoryUsers(userId, allowInstantCache) }
    }

    fun clearCacheIfNeeded() {
        val cacheAgeSec = (System.currentTimeMillis() - cachedStoriesTimestampMs) / 1000.0
        if (cacheAgeSec > 600) {
            cachedStories.clear()
            cachedStoriesTimestampMs = System.currentTimeMillis()
        }
    }

    fun resetCache() {
        cachedStories.clear()
        cachedUnseenStories.clear()
        cachedStoriesTimestampMs = System.currentTimeMillis()
        storyUsers = emptyList()
        ringNextCursor = null
        isLoadingStories = true
        isLoadingMoreRing = false
        StoryTrayService.invalidate()
    }

    fun prefetchTopStoryUsers(excluding: String?, scope: CoroutineScope) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(3_000)
            storyUsers.take(5).forEach { user ->
                if (user.hasStory && user.userId != excluding) {
                    runCatching { firestoreService.prefetchStoriesForUser(user.userId) }
                }
            }
        }
    }

    fun loadMoreRingUsersIfNeeded(
        visibleIndex: Int,
        currentUserId: String,
        scope: CoroutineScope,
    ) {
        val threshold = maxOf(storyUsers.size - 4, 0)
        val cursor = ringNextCursor
        if (visibleIndex < threshold || cursor == null || isLoadingMoreRing) return

        scope.launch {
            isLoadingMoreRing = true
            runCatching {
                StoryTrayService.fetchStoryRingPage(limit = ringPageSize, cursor = cursor)
            }.onSuccess { tray ->
                if (tray != null) {
                    applyTray(tray, currentUserId = currentUserId, append = true)
                    ringNextCursor = tray.nextCursor
                }
            }
            isLoadingMoreRing = false
        }
    }

    /** Compat con `FeedView.kt` — delega en [loadMoreRingUsersIfNeeded]. */
    fun loadMoreRing(scope: CoroutineScope, userId: String) {
        loadMoreRingUsersIfNeeded(
            visibleIndex = maxOf(storyUsers.size - 1, 0),
            currentUserId = userId,
            scope = scope,
        )
    }

    private suspend fun reloadStoryUsers(userId: String, allowInstantCache: Boolean) {
        isLoadingStories = true

        if (allowInstantCache) {
            StoryTrayService.cachedTray(userId)?.let { cachedTray ->
                applyTray(cachedTray, currentUserId = userId, append = false)
                ringNextCursor = cachedTray.nextCursor
                isLoadingStories = false
                return
            }
            val skeleton = loadCachedStoryUsers(userId)
            if (skeleton.isNotEmpty()) {
                storyUsers = skeleton
            }
        }

        runCatching {
            StoryTrayService.fetchStoryRingPage(limit = ringPageSize, cursor = null)
        }.onSuccess { tray ->
            if (tray != null) {
                applyTray(tray, currentUserId = userId, append = false)
                ringNextCursor = tray.nextCursor
            } else {
                loadLegacyRing(userId, allowInstantCache)
            }
        }.onFailure {
            loadLegacyRing(userId, allowInstantCache)
        }
        isLoadingStories = false
    }

    private suspend fun applyTray(
        tray: BackendStoryTrayResponse,
        currentUserId: String,
        append: Boolean = false,
    ) {
        val usernames = fetchUsernames(tray.items.map { it.userId })
        val entries = tray.items.map { item ->
            val viewedStatus = item.segments.map { it.viewed }
            val audiences = item.segments.map { it.audience }
            val storyCount = maxOf(item.storyCount, item.segments.size)
            FeedStoryUserState(
                userId = item.userId,
                username = usernames[item.userId] ?: "moments",
                hasStory = storyCount > 0,
                hasUnseenStory = item.userId != currentUserId && item.hasUnseenStory,
                storyCount = storyCount,
                storyViewedStatus = viewedStatus,
                storyAudiences = audiences,
            )
        }

        var finalUsers = entries.toMutableList()
        if (finalUsers.firstOrNull()?.userId != currentUserId) {
            finalUsers.removeAll { it.userId == currentUserId }
            finalUsers.add(0, emptyCurrentUserEntry(currentUserId, usernames[currentUserId]))
        } else {
            finalUsers[0] = finalUsers[0].copy(hasUnseenStory = false)
        }

        storyUsers = if (append) {
            val existingIds = storyUsers.map { it.userId }.toSet()
            storyUsers + finalUsers.filter { it.userId !in existingIds }
        } else {
            finalUsers
        }

        cachedStoriesTimestampMs = System.currentTimeMillis()
        finalUsers.forEach { entry ->
            cachedStories[entry.userId] = entry.hasStory
            cachedUnseenStories[entry.userId] = entry.hasUnseenStory
            StoryRingCacheService.set(
                viewerId = currentUserId,
                authorId = entry.userId,
                snapshot = StoryRingSnapshot(
                    hasStory = entry.hasStory,
                    hasUnseenStory = entry.hasUnseenStory,
                    storyCount = entry.storyCount,
                    storyViewedStatus = entry.storyViewedStatus,
                    storyAudiences = entry.storyAudiences,
                ),
            )
        }
        updateStoryWidgetCount(from = storyUsers)
    }

    private fun emptyCurrentUserEntry(userId: String, username: String?): FeedStoryUserState =
        FeedStoryUserState(
            userId = userId,
            username = username ?: "moments",
            hasStory = false,
            hasUnseenStory = false,
            storyCount = 0,
            storyViewedStatus = emptyList(),
            storyAudiences = emptyList(),
        )

    /** Port de `loadCachedStoryUsers` — skeleton desde LocalPersistence. */
    private fun loadCachedStoryUsers(userId: String): List<FeedStoryUserState> {
        val (_, following, _) = LocalPersistenceService.loadConnections(userId)
        val candidateIds = listOf(userId) + following.map { it.id }
        val entries = mutableListOf<FeedStoryUserState>()
        for (candidateId in candidateIds) {
            val stories = LocalPersistenceService.loadStories(candidateId)
            val hasStory = stories.isNotEmpty()
            if (candidateId != userId && !hasStory) continue
            val username = LocalPersistenceService.loadUser(candidateId)?.username ?: "moments"
            entries += FeedStoryUserState(
                userId = candidateId,
                username = username,
                hasStory = hasStory,
                hasUnseenStory = if (candidateId == userId) false else hasStory,
                storyCount = stories.size,
                storyViewedStatus = List(stories.size) { candidateId == userId },
                storyAudiences = stories.map { it.audience },
            )
        }
        return buildSortedStoryUsers(entries, userId)
    }

    /**
     * Fallback legacy iOS: muted + following → summaries → batched stories → evaluate.
     */
    private suspend fun loadLegacyRing(userId: String, allowInstantCache: Boolean) {
        val muted = runCatching { firestoreService.fetchMutedUserIds(userId) }.getOrDefault(emptySet())
        val following = runCatching { firestoreService.fetchFollowing(userId) }.getOrNull()
        if (following == null) {
            val snap = StoryRingResolverService.resolve(viewerId = userId, authorId = userId)
            storyUsers = listOf(
                emptyCurrentUserEntry(userId, null).copy(
                    hasStory = snap.hasStory,
                    storyCount = snap.storyCount,
                    storyViewedStatus = snap.storyViewedStatus,
                    storyAudiences = snap.storyAudiences,
                ),
            )
            return
        }

        val followingIds = following.map { it.id }.filter { it !in muted }
        val allUserIds = listOf(userId) + followingIds
        val cacheAgeSec = (System.currentTimeMillis() - cachedStoriesTimestampMs) / 1000.0

        if (allowInstantCache && cacheAgeSec < 20 && cachedStories.isNotEmpty()) {
            val cachedEntries = mutableListOf<FeedStoryUserState>()
            val ownHas = cachedStories[userId] == true
            val ownStories = if (ownHas) LocalPersistenceService.loadStories(userId) else emptyList()
            val ownCount = if (ownStories.isEmpty()) (if (ownHas) 1 else 0) else ownStories.size
            cachedEntries += FeedStoryUserState(
                userId = userId,
                username = LocalPersistenceService.loadUser(userId)?.username ?: "moments",
                hasStory = ownHas,
                hasUnseenStory = false,
                storyCount = ownCount,
                storyViewedStatus = List(ownCount) { true },
                storyAudiences = if (ownStories.isEmpty()) {
                    if (ownHas) listOf(null) else emptyList()
                } else {
                    ownStories.map { it.audience }
                },
            )
            for (followingId in followingIds) {
                if (cachedStories[followingId] != true) continue
                val hasUnseen = cachedUnseenStories[followingId] ?: true
                val cached = LocalPersistenceService.loadStories(followingId)
                val count = if (cached.isEmpty()) 1 else cached.size
                cachedEntries += FeedStoryUserState(
                    userId = followingId,
                    username = LocalPersistenceService.loadUser(followingId)?.username ?: "moments",
                    hasStory = true,
                    hasUnseenStory = hasUnseen,
                    storyCount = count,
                    storyViewedStatus = List(count) { !hasUnseen },
                    storyAudiences = if (cached.isEmpty()) listOf(null) else cached.map { it.audience },
                )
            }
            storyUsers = buildSortedStoryUsers(cachedEntries, userId)
            updateStoryWidgetCount(from = storyUsers)
            return
        }

        val summaries = runCatching {
            firestoreService.fetchStorySummariesForUsers(allUserIds)
        }.getOrDefault(emptyMap())
        val candidateUserIds = allUserIds.filter {
            shouldFetchDetailedStories(it, userId, summaries)
        }

        val storiesByUser = runCatching {
            firestoreService.fetchActiveStoriesForUsers(candidateUserIds)
        }.getOrNull()

        val finalUsers = if (storiesByUser != null) {
            loadStoryUsersFromBatchedStories(allUserIds, userId, storiesByUser)
        } else {
            loadStoryUsersLegacy(allUserIds, userId)
        }

        cachedStoriesTimestampMs = System.currentTimeMillis()
        storyUsers = finalUsers
        updateStoryWidgetCount(from = finalUsers)
    }

    private fun shouldFetchDetailedStories(
        authorId: String,
        currentUserId: String,
        summaries: Map<String, StoryAuthorSummary>,
    ): Boolean {
        if (authorId == currentUserId) return true
        val summary = summaries[authorId] ?: return true
        return !summary.shouldSkipDetailedFetch()
    }

    private suspend fun loadStoryUsersFromBatchedStories(
        allUserIds: List<String>,
        currentUserId: String,
        storiesByUser: Map<String, List<com.moments.android.models.Story>>,
    ): List<FeedStoryUserState> = coroutineScope {
        val usernames = fetchUsernames(allUserIds)
        val entries = allUserIds.map { authorId ->
            async {
                val stories = storiesByUser[authorId].orEmpty()
                val snap = StoryRingResolverService.evaluate(
                    viewerId = currentUserId,
                    authorId = authorId,
                    stories = stories,
                )
                FeedStoryUserState(
                    userId = authorId,
                    username = usernames[authorId] ?: "moments",
                    hasStory = snap.hasStory,
                    hasUnseenStory = if (authorId == currentUserId) false else snap.hasUnseenStory,
                    storyCount = snap.storyCount,
                    storyViewedStatus = snap.storyViewedStatus,
                    storyAudiences = snap.storyAudiences,
                ).also {
                    cachedStories[authorId] = it.hasStory
                    cachedUnseenStories[authorId] = it.hasUnseenStory
                }
            }
        }.awaitAll()
        buildSortedStoryUsers(entries, currentUserId)
    }

    private suspend fun loadStoryUsersLegacy(
        allUserIds: List<String>,
        currentUserId: String,
    ): List<FeedStoryUserState> = coroutineScope {
        val usernames = fetchUsernames(allUserIds)
        val entries = allUserIds.map { authorId ->
            async {
                val snap = StoryRingResolverService.resolve(
                    viewerId = currentUserId,
                    authorId = authorId,
                    useCache = true,
                )
                FeedStoryUserState(
                    userId = authorId,
                    username = usernames[authorId] ?: "moments",
                    hasStory = snap.hasStory,
                    hasUnseenStory = if (authorId == currentUserId) false else snap.hasUnseenStory,
                    storyCount = snap.storyCount,
                    storyViewedStatus = snap.storyViewedStatus,
                    storyAudiences = snap.storyAudiences,
                ).also {
                    cachedStories[authorId] = it.hasStory
                    cachedUnseenStories[authorId] = it.hasUnseenStory
                }
            }
        }.awaitAll()
        buildSortedStoryUsers(entries, currentUserId)
    }

    /** Port de `buildSortedStoryUsers` — unseen primero, luego afinidad + BF/mutuals. */
    private fun buildSortedStoryUsers(
        entries: List<FeedStoryUserState>,
        currentUserId: String,
    ): List<FeedStoryUserState> {
        val current = entries.firstOrNull { it.userId == currentUserId }
            ?: emptyCurrentUserEntry(currentUserId, null)
        val normalizedCurrent = current.copy(hasUnseenStory = false)
        var sortedOthers = entries.filter { it.userId != currentUserId && it.hasStory }.toMutableList()

        val affinityScores = runCatching {
            AffinityTracker.getScores(sortedOthers.map { it.userId })
        }.getOrDefault(emptyMap())

        val bestFriends = LocalPersistenceService.loadUser(currentUserId)?.bestFriends?.toSet().orEmpty()
        val mutuals = LocalPersistenceService.loadConnections(currentUserId).third.map { it.id }.toSet()

        sortedOthers.sortWith { user1, user2 ->
            when {
                user1.hasUnseenStory && !user2.hasUnseenStory -> -1
                user2.hasUnseenStory && !user1.hasUnseenStory -> 1
                else -> {
                    var score1 = (affinityScores[user1.userId] ?: 0.0) * 1000
                    var score2 = (affinityScores[user2.userId] ?: 0.0) * 1000
                    when {
                        user1.userId in bestFriends -> score1 += 50_000
                        user1.userId in mutuals -> score1 += 20_000
                    }
                    when {
                        user2.userId in bestFriends -> score2 += 50_000
                        user2.userId in mutuals -> score2 += 20_000
                    }
                    score2.compareTo(score1)
                }
            }
        }

        return listOf(normalizedCurrent) + sortedOthers
    }

    private fun updateStoryWidgetCount(from: List<FeedStoryUserState>) {
        val ctx = appContext ?: return
        val count = from.count { it.hasUnseenStory }
        ctx.getSharedPreferences("group.com.glowsyapp", Context.MODE_PRIVATE)
            .edit()
            .putInt("widget_new_stories_count", count)
            .apply()
        scheduleWidgetReload()
    }

    private fun scheduleWidgetReload(delayMs: Long = 2_000) {
        widgetReloadJob?.cancel()
        // Android AppWidgetManager se engancha cuando exista el widget; por ahora solo persistimos el count.
        widgetReloadJob = CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
        }
    }

    private suspend fun fetchUsernames(ids: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        ids.distinct().associateWith { id ->
            LocalPersistenceService.loadUser(id)?.username?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    FirebaseFirestore.getInstance().collection("users").document(id).get().await()
                        .getString("username")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "moments"
        }
    }
}
