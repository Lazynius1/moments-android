package com.moments.android.services.content

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.moments.android.services.firestore.fetchUsersWithSharedInterests

/** Port de ForYouDiscoveryService.swift — discovery legacy Para Ti. */
object ForYouDiscoveryService {
    const val SECOND_DEGREE_SAMPLE_SIZE = 15
    const val SECOND_DEGREE_CAP = 30
    const val INTEREST_USER_CAP = 40
    const val FOLLOWER_PUBLIC_CAP = 20
    const val GLOBAL_EVERYONE_FETCH_LIMIT = 120

    data class DiscoveryResult(
        val authorIds: List<String>,
        val authorTierById: Map<String, String>,
    )

    data class GlobalStreamCursor(
        val timestamp: Date,
        val momentId: String,
        val authorId: String,
    )

    suspend fun loadDiscoveryAuthors(
        viewerId: String,
        interests: List<String>,
        followingIds: Set<String>,
        followerIds: Set<String>,
        blockedUserIds: Set<String>,
    ): DiscoveryResult = coroutineScope {
        fun isExcluded(authorId: String) =
            authorId == viewerId || authorId in followingIds || authorId in blockedUserIds

        val tierA = async {
            if (interests.isEmpty()) emptySet()
            else runCatching {
                FirestoreService().fetchUsersWithSharedInterests(interests, viewerId)
                    .asSequence()
                    .filter { !isExcluded(it.id) }
                    .take(INTEREST_USER_CAP)
                    .map { it.id }
                    .toSet()
            }.getOrDefault(emptySet())
        }
        val tierB = async {
            loadSecondDegreeUserIds(viewerId, followingIds, blockedUserIds)
        }
        val tierC = async {
            loadFollowerPublicUserIds(followerIds, followingIds, blockedUserIds, viewerId)
        }

        val a = tierA.await()
        val b = tierB.await()
        val c = tierC.await()
        val tiers = linkedMapOf<String, String>()
        a.forEach { tiers[it] = "A" }
        b.forEach { if (it !in tiers) tiers[it] = "B" }
        c.forEach { if (it !in tiers) tiers[it] = "C" }
        DiscoveryResult(authorIds = (a + b + c).toList(), authorTierById = tiers)
    }

    suspend fun fetchGlobalEveryoneMoments(
        excludingAuthorIds: Set<String>,
        excludingMomentIds: Set<String> = emptySet(),
        globalStreamCursor: GlobalStreamCursor? = null,
        limit: Int = GLOBAL_EVERYONE_FETCH_LIMIT,
    ): Pair<List<Moment>, GlobalStreamCursor?> {
        val db = FirebaseFirestore.getInstance()
        val cursorSnap: DocumentSnapshot? = if (globalStreamCursor != null) {
            val snap = db.collection("users").document(globalStreamCursor.authorId)
                .collection("moments").document(globalStreamCursor.momentId).get().await()
            if (snap.exists()) snap else null
        } else null

        var query: Query = db.collectionGroup("moments")
            .whereEqualTo("audience", "everyone")
            .orderBy("timestamp", Query.Direction.DESCENDING)
        if (cursorSnap != null) query = query.startAfter(cursorSnap)

        val documents = query.limit(limit.toLong()).get().await().documents
        val moments = documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            val moment = Moment.from(doc.id, data)
            val authorId = moment.authorId
            if (authorId.isEmpty() || authorId in excludingAuthorIds) return@mapNotNull null
            val mid = moment.id ?: doc.id
            if (mid in excludingMomentIds) return@mapNotNull null
            moment
        }

        val next = if (documents.size >= limit) {
            val last = documents.last()
            val data = last.data
            val authorId = data?.get("authorId") as? String
            if (!authorId.isNullOrEmpty()) {
                GlobalStreamCursor(
                    timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: Date(0),
                    momentId = last.id,
                    authorId = authorId,
                )
            } else null
        } else null

        return moments to next
    }

    private suspend fun loadSecondDegreeUserIds(
        viewerId: String,
        followingIds: Set<String>,
        blockedUserIds: Set<String>,
    ): Set<String> = coroutineScope {
        val sample = followingIds.take(SECOND_DEGREE_SAMPLE_SIZE)
        if (sample.isEmpty()) return@coroutineScope emptySet()
        val db = FirebaseFirestore.getInstance()
        val result = mutableSetOf<String>()
        sample.map { followingId ->
            async {
                val docs = db.collection("users").document(followingId)
                    .collection("following").limit(20).get().await().documents
                docs.map { it.id }
            }
        }.awaitAll().forEach { ids ->
            for (id in ids) {
                if (result.size >= SECOND_DEGREE_CAP) break
                if (id != viewerId && id !in followingIds && id !in blockedUserIds) {
                    result += id
                }
            }
        }
        result
    }

    private suspend fun loadFollowerPublicUserIds(
        followerIds: Set<String>,
        followingIds: Set<String>,
        blockedUserIds: Set<String>,
        viewerId: String,
    ): Set<String> = coroutineScope {
        val candidates = followerIds.filter {
            it.isNotEmpty() && it != viewerId && it !in followingIds && it !in blockedUserIds
        }
        if (candidates.isEmpty()) return@coroutineScope emptySet()
        val db = FirebaseFirestore.getInstance()
        val publicIds = mutableSetOf<String>()
        candidates.take(FOLLOWER_PUBLIC_CAP * 2).map { followerId ->
            async {
                val snap = db.collection("users").document(followerId).get().await()
                val isPrivate = snap.getBoolean("isPrivate") == true
                followerId to !isPrivate
            }
        }.awaitAll().forEach { (id, isPublic) ->
            if (isPublic && publicIds.size < FOLLOWER_PUBLIC_CAP) publicIds += id
        }
        publicIds
    }
}
