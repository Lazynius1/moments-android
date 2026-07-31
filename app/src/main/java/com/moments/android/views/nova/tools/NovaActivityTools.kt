package com.moments.android.views.nova.tools

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchMoments
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/** Helpers JSON neutros ≡ `NovaJSON` en `NovaActivityTools.swift`. */
object NovaJSON {
    fun iso(date: Date): String = date.toInstant().toString()

    fun string(value: String?): String = value.orEmpty()

    fun int(value: Int): Int = value

    fun pctChange(current: Int, previous: Int): Int {
        if (previous <= 0) return if (current > 0) 100 else 0
        return (((current - previous).toDouble() / previous) * 100).roundToInt()
    }
}

/**
 * Port de `Views/Nova/Tools/NovaActivityTools.swift`.
 * Consultas Firestore de actividad → mapas JSON-shaped para el modelo.
 */
class NovaActivityTools(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    suspend fun activitySummary(userId: String): Map<String, Any?> = coroutineScope {
        val visitsDeferred = async { profileVisits(userId, limit = 5) }
        val chainDeferred = async { latestStoryChain(userId) }
        val visitData = visitsDeferred.await()
        mapOf(
            "recent_visits" to (visitData["visits"] as? List<*> ?: emptyList<Any>()),
            "total_visits" to (visitData["total_count"] ?: 0),
            "latest_story_chain" to chainDeferred.await(),
        )
    }

    suspend fun weeklySummary(userId: String): Map<String, Any?> {
        val startOfThisWeek = startOfWeek() ?: return mapOf("error" to "Could not compute week boundaries.")
        val startOfLastWeek = Calendar.getInstance().apply {
            time = startOfThisWeek
            add(Calendar.WEEK_OF_YEAR, -1)
        }.time
        val endOfLastWeek = Date(startOfThisWeek.time - 1000L)

        return coroutineScope {
            val thisWeekMoments = async { fetchMoments(userId, startOfThisWeek, to = null) }
            val lastWeekMoments = async { fetchMoments(userId, startOfLastWeek, endOfLastWeek) }
            val thisWeekVisits = async { countVisits(userId, startOfThisWeek, to = null) }
            val lastWeekVisits = async { countVisits(userId, startOfLastWeek, endOfLastWeek) }
            val thisWeekStoryViews = async { countStoryViews(userId, startOfThisWeek, to = null) }
            val lastWeekStoryViews = async { countStoryViews(userId, startOfLastWeek, endOfLastWeek) }

            val twM = thisWeekMoments.await()
            val lwM = lastWeekMoments.await()
            val twV = thisWeekVisits.await()
            val lwV = lastWeekVisits.await()
            val twS = thisWeekStoryViews.await()
            val lwS = lastWeekStoryViews.await()
            val twEngagement = engagement(twM)
            val lwEngagement = engagement(lwM)

            mapOf(
                "this_week" to mapOf(
                    "moments" to NovaJSON.int(twM.size),
                    "reactions" to NovaJSON.int(twEngagement.reactions),
                    "comments" to NovaJSON.int(twEngagement.comments),
                    "profile_visits" to NovaJSON.int(twV),
                    "story_views" to NovaJSON.int(twS),
                ),
                "last_week" to mapOf(
                    "moments" to NovaJSON.int(lwM.size),
                    "reactions" to NovaJSON.int(lwEngagement.reactions),
                    "comments" to NovaJSON.int(lwEngagement.comments),
                    "profile_visits" to NovaJSON.int(lwV),
                    "story_views" to NovaJSON.int(lwS),
                ),
                "change_pct" to mapOf(
                    "moments" to NovaJSON.int(NovaJSON.pctChange(twM.size, lwM.size)),
                    "profile_visits" to NovaJSON.int(NovaJSON.pctChange(twV, lwV)),
                    "story_views" to NovaJSON.int(NovaJSON.pctChange(twS, lwS)),
                ),
            )
        }
    }

    suspend fun profileVisits(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        val visitsRef = db.collection("users").document(userId).collection("visits")
        val totalCount = visitsRef.get().await().size()

        var visits = visitsRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(capped.toLong())
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val visitorId = data["visitorId"] as? String ?: return@mapNotNull null
                val timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: return@mapNotNull null
                ProfileVisitRecord(visitorId = visitorId, timestamp = timestamp)
            }

        val visitorIds = visits.map { it.visitorId }.distinct()
        if (visitorIds.isNotEmpty()) {
            val users = firestoreService.fetchUsers(visitorIds).associateBy { it.id }
            visits = visits.map { visit ->
                visit.copy(username = users[visit.visitorId]?.username)
            }
        }

        return mapOf(
            "total_count" to NovaJSON.int(totalCount),
            "visits" to visits.map { visit ->
                mapOf(
                    "username" to NovaJSON.string(visit.username ?: "unknown"),
                    "visitor_id" to visit.visitorId,
                    "timestamp" to NovaJSON.iso(visit.timestamp),
                )
            },
        )
    }

    suspend fun storyChainInfo(userId: String, includeViewers: Boolean): Map<String, Any?> {
        val chain = latestStoryChainRecord(userId) ?: return mapOf("latest_chain" to null)
        return buildMap {
            put(
                "latest_chain",
                mapOf(
                    "chain_id" to chain.chainId,
                    "title" to chain.chainTitle,
                    "story_count" to NovaJSON.int(chain.storyCount),
                    "created_at" to NovaJSON.iso(chain.createdAt),
                ),
            )
            if (includeViewers) {
                put("viewers", storyChainViewers(userId, chain.chainId))
            }
        }
    }

    // MARK: - Private

    private data class ProfileVisitRecord(
        val visitorId: String,
        val timestamp: Date,
        val username: String? = null,
    )

    private data class StoryChainRecord(
        val chainId: String,
        val chainTitle: String,
        val storyCount: Int,
        val createdAt: Date,
    )

    private data class EngagementTotals(val reactions: Int, val comments: Int)

    /** ≡ iOS `Calendar` `.yearForWeekOfYear` + `.weekOfYear`. */
    private fun startOfWeek(now: Date = Date()): Date? = runCatching {
        val cal = Calendar.getInstance()
        cal.time = now
        cal.setWeekDate(cal.getWeekYear(), cal.get(Calendar.WEEK_OF_YEAR), cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.time
    }.getOrNull()

    private suspend fun fetchMoments(userId: String, from: Date, to: Date?): List<Moment> =
        firestoreService.fetchMoments(userId).filter { moment ->
            moment.timestamp >= from && (to == null || moment.timestamp <= to)
        }

    private suspend fun countVisits(userId: String, from: Date, to: Date?): Int {
        var query: Query = db.collection("users").document(userId).collection("visits")
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(from))
        if (to != null) {
            query = query.whereLessThanOrEqualTo("timestamp", Timestamp(to))
        }
        return query.get().await().size()
    }

    private suspend fun countStoryViews(userId: String, from: Date, to: Date?): Int {
        var query: Query = db.collection("users").document(userId).collection("stories")
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(from))
        if (to != null) {
            query = query.whereLessThanOrEqualTo("timestamp", Timestamp(to))
        }
        val snapshot = query.get().await()
        var total = 0
        for (doc in snapshot.documents) {
            val viewers = db.collection("users").document(userId).collection("stories")
                .document(doc.id).collection("viewers").get().await()
            total += viewers.size()
        }
        return total
    }

    private fun engagement(moments: List<Moment>): EngagementTotals {
        var reactions = 0
        var comments = 0
        for (moment in moments) {
            for (userIds in moment.reactions.values) {
                reactions += userIds.size
            }
            comments += moment.commentCount
        }
        return EngagementTotals(reactions = reactions, comments = comments)
    }

    private suspend fun latestStoryChain(userId: String): Map<String, Any?>? {
        val chain = latestStoryChainRecord(userId) ?: return null
        return mapOf(
            "chain_id" to chain.chainId,
            "title" to chain.chainTitle,
            "story_count" to NovaJSON.int(chain.storyCount),
            "created_at" to NovaJSON.iso(chain.createdAt),
        )
    }

    private suspend fun latestStoryChainRecord(userId: String): StoryChainRecord? {
        val stories = db.collection("users").document(userId).collection("stories")
        val document = stories
            .whereNotEqualTo("chainId", null)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull() ?: return null

        val data = document.data ?: return null
        val chainId = data["chainId"] as? String ?: return null
        val chainTitle = data["chainTitle"] as? String ?: return null
        val chainStories = stories.whereEqualTo("chainId", chainId).get().await()

        return StoryChainRecord(
            chainId = chainId,
            chainTitle = chainTitle,
            storyCount = chainStories.size(),
            createdAt = (data["timestamp"] as? Timestamp)?.toDate() ?: Date(),
        )
    }

    private suspend fun storyChainViewers(userId: String, chainId: String): List<Map<String, Any?>> {
        val stories = db.collection("users").document(userId).collection("stories")
            .whereEqualTo("chainId", chainId)
            .get()
            .await()
            .documents

        val viewersByUser = mutableMapOf<String, Pair<String?, Date>>()
        for (document in stories) {
            val viewersSnapshot = db.collection("users").document(userId).collection("stories")
                .document(document.id).collection("viewers").get().await()
            for (viewerDoc in viewersSnapshot.documents) {
                val data = viewerDoc.data ?: continue
                val viewerId = data["userId"] as? String ?: continue
                val timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: continue
                val existing = viewersByUser[viewerId]
                if (existing != null && existing.second > timestamp) continue
                viewersByUser[viewerId] = (data["username"] as? String) to timestamp
            }
        }

        return viewersByUser.entries
            .sortedByDescending { it.value.second }
            .take(5)
            .map { entry ->
                mapOf(
                    "username" to NovaJSON.string(entry.value.first ?: "unknown"),
                    "viewer_id" to entry.key,
                    "timestamp" to NovaJSON.iso(entry.value.second),
                )
            }
    }
}
