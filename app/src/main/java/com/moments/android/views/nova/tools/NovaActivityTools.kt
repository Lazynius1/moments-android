package com.moments.android.views.nova.tools

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchMoments
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

object NovaJSON {
    fun iso(date: Date): String = date.toInstant().toString()
    fun string(value: String?): String = value.orEmpty()
    fun int(value: Int): Int = value
    fun pctChange(current: Int, previous: Int): Int = if (previous <= 0) if (current > 0) 100 else 0 else (((current - previous).toDouble() / previous) * 100).toInt()
}

/** Firestore-backed activity queries returning neutral JSON-shaped data for Nova's model. */
class NovaActivityTools(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    suspend fun activitySummary(userId: String): Map<String, Any?> = coroutineScope {
        val visits = async { profileVisits(userId, 5) }; val chain = async { latestStoryChain(userId) }
        val visitData = visits.await(); mapOf("recent_visits" to visitData["visits"].orEmptyList(), "total_visits" to (visitData["total_count"] ?: 0), "latest_story_chain" to chain.await())
    }
    suspend fun weeklySummary(userId: String): Map<String, Any?> = coroutineScope {
        val now = Instant.now(); val startThis = now.truncatedTo(ChronoUnit.DAYS).minus((java.time.ZonedDateTime.now().dayOfWeek.value - 1).toLong(), ChronoUnit.DAYS); val startLast = startThis.minus(7, ChronoUnit.DAYS); val endLast = startThis.minusMillis(1)
        val twM = async { fetchMoments(userId, Date.from(startThis), null) }; val lwM = async { fetchMoments(userId, Date.from(startLast), Date.from(endLast)) }; val twV = async { countVisits(userId, Date.from(startThis), null) }; val lwV = async { countVisits(userId, Date.from(startLast), Date.from(endLast)) }; val twS = async { countStoryViews(userId, Date.from(startThis), null) }; val lwS = async { countStoryViews(userId, Date.from(startLast), Date.from(endLast)) }
        val thisMoments = twM.await(); val lastMoments = lwM.await(); val thisVisits = twV.await(); val lastVisits = lwV.await(); val thisStories = twS.await(); val lastStories = lwS.await(); val thisEngagement = engagement(thisMoments); val lastEngagement = engagement(lastMoments)
        mapOf("this_week" to mapOf("moments" to thisMoments.size, "reactions" to thisEngagement.reactions, "comments" to thisEngagement.comments, "profile_visits" to thisVisits, "story_views" to thisStories), "last_week" to mapOf("moments" to lastMoments.size, "reactions" to lastEngagement.reactions, "comments" to lastEngagement.comments, "profile_visits" to lastVisits, "story_views" to lastStories), "change_pct" to mapOf("moments" to NovaJSON.pctChange(thisMoments.size, lastMoments.size), "profile_visits" to NovaJSON.pctChange(thisVisits, lastVisits), "story_views" to NovaJSON.pctChange(thisStories, lastStories)))
    }
    suspend fun profileVisits(userId: String, limit: Int = 5): Map<String, Any?> { val capped = limit.coerceIn(1, 10); val ref = db.collection("users").document(userId).collection("visits"); val total = ref.get().await().size(); var visits = ref.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(capped.toLong()).get().await().documents.mapNotNull { doc -> val data = doc.data ?: return@mapNotNull null; val id = data["visitorId"] as? String ?: return@mapNotNull null; val timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: return@mapNotNull null; Visit(id, timestamp) }; val users = firestoreService.fetchUsers(visits.map { it.visitorId }.distinct()).associateBy { it.id }; visits = visits.map { it.copy(username = users[it.visitorId]?.username) }; return mapOf("total_count" to total, "visits" to visits.map { mapOf("username" to NovaJSON.string(it.username ?: "unknown"), "visitor_id" to it.visitorId, "timestamp" to NovaJSON.iso(it.timestamp)) }) }
    suspend fun storyChainInfo(userId: String, includeViewers: Boolean): Map<String, Any?> { val chain = latestStoryChainRecord(userId) ?: return mapOf("latest_chain" to null); return buildMap { put("latest_chain", chain.toMap()); if (includeViewers) put("viewers", storyChainViewers(userId, chain.chainId)) } }
    private data class Visit(val visitorId: String, val timestamp: Date, val username: String? = null)
    private data class StoryChain(val chainId: String, val title: String, val storyCount: Int, val createdAt: Date) { fun toMap() = mapOf("chain_id" to chainId, "title" to title, "story_count" to storyCount, "created_at" to NovaJSON.iso(createdAt)) }
    private data class Engagement(val reactions: Int, val comments: Int)
    private suspend fun fetchMoments(userId: String, from: Date, to: Date?) = firestoreService.fetchMoments(userId).filter { it.timestamp >= from && (to == null || it.timestamp <= to) }
    private suspend fun countVisits(userId: String, from: Date, to: Date?): Int { var query: com.google.firebase.firestore.Query = db.collection("users").document(userId).collection("visits").whereGreaterThanOrEqualTo("timestamp", Timestamp(from)); if (to != null) query = query.whereLessThanOrEqualTo("timestamp", Timestamp(to)); return query.get().await().size() }
    private suspend fun countStoryViews(userId: String, from: Date, to: Date?): Int { var query: com.google.firebase.firestore.Query = db.collection("users").document(userId).collection("stories").whereGreaterThanOrEqualTo("timestamp", Timestamp(from)); if (to != null) query = query.whereLessThanOrEqualTo("timestamp", Timestamp(to)); return query.get().await().documents.sumOf { db.collection("users").document(userId).collection("stories").document(it.id).collection("viewers").get().await().size() } }
    private fun engagement(moments: List<Moment>) = Engagement(moments.sumOf { it.reactions.values.sumOf(List<String>::size) }, moments.sumOf { it.commentCount })
    private suspend fun latestStoryChain(userId: String): Map<String, Any?>? = latestStoryChainRecord(userId)?.toMap()
    private suspend fun latestStoryChainRecord(userId: String): StoryChain? { val stories = db.collection("users").document(userId).collection("stories"); val doc = stories.whereNotEqualTo("chainId", null).orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(1).get().await().documents.firstOrNull() ?: return null; val data = doc.data ?: return null; val id = data["chainId"] as? String ?: return null; val title = data["chainTitle"] as? String ?: return null; val count = stories.whereEqualTo("chainId", id).get().await().size(); return StoryChain(id, title, count, (data["timestamp"] as? Timestamp)?.toDate() ?: Date()) }
    private suspend fun storyChainViewers(userId: String, chainId: String): List<Map<String, Any?>> { val stories = db.collection("users").document(userId).collection("stories").whereEqualTo("chainId", chainId).get().await().documents; val viewers = mutableMapOf<String, Pair<String?, Date>>(); stories.forEach { story -> story.reference.collection("viewers").get().await().documents.forEach { viewer -> val data = viewer.data ?: return@forEach; val id = data["userId"] as? String ?: return@forEach; val date = (data["timestamp"] as? Timestamp)?.toDate() ?: return@forEach; if (viewers[id]?.second?.before(date) != false) viewers[id] = data["username"] as? String to date } }; return viewers.entries.sortedByDescending { it.value.second }.take(5).map { mapOf("username" to NovaJSON.string(it.value.first ?: "unknown"), "viewer_id" to it.key, "timestamp" to NovaJSON.iso(it.value.second)) } }
    private fun Any?.orEmptyList(): List<Any?> = this as? List<Any?> ?: emptyList()
}
