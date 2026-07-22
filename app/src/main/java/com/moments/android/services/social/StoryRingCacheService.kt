package com.moments.android.services.social

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.models.Story
import com.moments.android.services.persistence.StorySeenStateService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewStoryEnhanced
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class StoryRingSnapshot(
    val hasStory: Boolean,
    val hasUnseenStory: Boolean,
    val storyCount: Int,
    val storyViewedStatus: List<Boolean>,
    val storyAudiences: List<String?>,
)

/** Port de StoryRingCacheService (actor) en StoryRingCacheService.swift. */
object StoryRingCacheService {
    private data class Entry(val snapshot: StoryRingSnapshot, val expiresAt: Long)

    private val storage = ConcurrentHashMap<String, Entry>()

    private fun key(viewerId: String, authorId: String) = "$viewerId|$authorId"

    fun get(viewerId: String, authorId: String): StoryRingSnapshot? {
        val cacheKey = key(viewerId, authorId)
        val entry = storage[cacheKey] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            storage.remove(cacheKey)
            return null
        }
        return entry.snapshot
    }

    fun set(viewerId: String, authorId: String, snapshot: StoryRingSnapshot, ttlSeconds: Long = 30) {
        storage[key(viewerId, authorId)] = Entry(
            snapshot = snapshot,
            expiresAt = System.currentTimeMillis() + ttlSeconds * 1000,
        )
    }

    fun invalidate(viewerId: String, authorId: String) {
        storage.remove(key(viewerId, authorId))
    }

    fun invalidateAuthor(authorId: String) {
        val suffix = "|$authorId"
        storage.keys.filter { it.endsWith(suffix) }.forEach { storage.remove(it) }
    }

    fun clear() {
        storage.clear()
    }
}

/** Port de StoryRingResolverService.swift. */
object StoryRingResolverService {
    private val viewerLookupTimeoutMs = 5_000L

    private val emptySnapshot = StoryRingSnapshot(
        hasStory = false,
        hasUnseenStory = false,
        storyCount = 0,
        storyViewedStatus = emptyList(),
        storyAudiences = emptyList(),
    )

    suspend fun resolve(
        viewerId: String,
        authorId: String,
        privacyService: PrivacyService = PrivacyService,
        db: FirebaseFirestore = FirebaseFirestore.getInstance(),
        useCache: Boolean = true,
    ): StoryRingSnapshot {
        if (viewerId.isEmpty() || authorId.isEmpty()) return emptySnapshot
        if (useCache) {
            StoryRingCacheService.get(viewerId, authorId)?.let { return it }
        }
        return fetchFromFirestore(viewerId, authorId, privacyService, db)
    }

    /** Evalúa stories ya cargadas (paridad `evaluateVisibleStoriesForRing`). */
    suspend fun evaluate(
        viewerId: String,
        authorId: String,
        stories: List<Story>,
        privacyService: PrivacyService = PrivacyService,
        db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    ): StoryRingSnapshot {
        if (stories.isEmpty()) return cacheAndReturn(viewerId, authorId, emptySnapshot)
        val snapshot = evaluateStories(stories, viewerId, authorId, privacyService, db)
        return cacheAndReturn(viewerId, authorId, snapshot)
    }

    private suspend fun fetchFromFirestore(
        viewerId: String,
        authorId: String,
        privacyService: PrivacyService,
        db: FirebaseFirestore,
    ): StoryRingSnapshot {
        val documents = try {
            db.collection("users").document(authorId).collection("stories")
                .whereGreaterThan("expirationDate", Timestamp.now())
                .orderBy("expirationDate")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
        } catch (_: Exception) {
            // Fallback sin índice compuesto: filtrar en cliente.
            db.collection("users").document(authorId).collection("stories")
                .whereGreaterThan("expirationDate", Timestamp.now())
                .get()
                .await()
                .documents
                .sortedBy { (it.get("timestamp") as? Timestamp)?.toDate()?.time ?: 0L }
        }

        if (documents.isEmpty()) {
            return cacheAndReturn(viewerId, authorId, emptySnapshot)
        }

        val stories = documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            Story.from(doc.id, data)
        }
        if (stories.isEmpty()) {
            return cacheAndReturn(viewerId, authorId, emptySnapshot)
        }

        val snapshot = evaluateStories(stories, viewerId, authorId, privacyService, db)
        return cacheAndReturn(viewerId, authorId, snapshot)
    }

    private suspend fun evaluateStories(
        stories: List<Story>,
        viewerId: String,
        authorId: String,
        privacyService: PrivacyService,
        db: FirebaseFirestore,
    ): StoryRingSnapshot = coroutineScope {
        val effectiveLastSeenAt = StorySeenStateService.fetchEffectiveLastSeen(viewerId, authorId)
        val mutex = Mutex()
        val visibleStories = mutableListOf<Pair<Story, Boolean>>()
        var hasUnseenStory = false

        stories.map { story ->
            async {
                val canView = withTimeoutOrNull(viewerLookupTimeoutMs) {
                    privacyService.canUserViewStoryEnhanced(story, viewerId)
                } ?: return@async

                if (!canView) return@async

                val supportsShortcut = StorySeenStateService.supportsShortcut(story.audience)
                if (supportsShortcut &&
                    effectiveLastSeenAt != null &&
                    !story.timestamp.after(effectiveLastSeenAt)
                ) {
                    mutex.withLock { visibleStories.add(story to true) }
                    return@async
                }

                val storyId = story.id
                val wasViewed = if (!storyId.isNullOrEmpty()) {
                    val viewerDoc = db.collection("users").document(story.authorId)
                        .collection("stories").document(storyId)
                        .collection("viewers").document(viewerId)
                        .get()
                        .await()
                    val viewed = viewerDoc.exists()
                    if (viewed && supportsShortcut) {
                        StorySeenStateService.markSeen(
                            viewerId = viewerId,
                            authorId = authorId,
                            timestamp = story.timestamp,
                            syncRemote = true,
                        )
                    }
                    viewed
                } else {
                    supportsShortcut &&
                        effectiveLastSeenAt != null &&
                        !story.timestamp.after(effectiveLastSeenAt)
                }

                mutex.withLock {
                    visibleStories.add(story to wasViewed)
                    if (!wasViewed) hasUnseenStory = true
                }
            }
        }.awaitAll()

        val sorted = mutex.withLock {
            visibleStories.sortedBy { it.first.timestamp.time }
        }
        val viewedStatus = sorted.map { it.second }
        val audiences = sorted.map { it.first.audience }
        val hasStory = sorted.isNotEmpty()
        val unseen = if (hasStory) (hasUnseenStory || viewedStatus.contains(false)) else false

        StoryRingSnapshot(
            hasStory = hasStory,
            hasUnseenStory = unseen,
            storyCount = sorted.size,
            storyViewedStatus = viewedStatus,
            storyAudiences = audiences,
        )
    }

    private fun cacheAndReturn(
        viewerId: String,
        authorId: String,
        snapshot: StoryRingSnapshot,
    ): StoryRingSnapshot {
        StoryRingCacheService.set(viewerId, authorId, snapshot)
        return snapshot
    }
}
