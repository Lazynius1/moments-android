package com.moments.android.services.firestore

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import kotlinx.coroutines.tasks.await

/**
 * Port de FirestoreSearchRepository.swift.
 * `fetchUsers` / `fetchMutedUserIds` viven en FirestoreService.kt (mismo API).
 * `fetchPublicUsersForExplore` = helper de ExploreViewModel.swift (no está en este .swift).
 */
suspend fun FirestoreService.searchUsers(query: String, limit: Int = 10): List<AppUser> {
    // Port de searchUsers(query:limit:) — limit Firestore 20, luego prefix(limit).
    if (query.isEmpty()) return emptyList()
    val lower = query.lowercase()
    val snap = db.collection("users")
        .whereGreaterThanOrEqualTo("username", lower)
        .whereLessThanOrEqualTo("username", lower + "\uF8FF")
        .limit(20)
        .get()
        .await()
    val users = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { AppUser.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }
    return applySearchMuteFilterIfNeeded(users).take(limit.coerceAtLeast(1))
}

/** Port de searchUsers(query:) sin limit — trim + exclude self + limit 30. */
suspend fun FirestoreService.searchUsersUncapped(query: String): List<AppUser> {
    val cleanQuery = query.lowercase().trim()
    if (cleanQuery.isEmpty()) return emptyList()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val snap = db.collection("users")
        .whereGreaterThanOrEqualTo("username", cleanQuery)
        .whereLessThanOrEqualTo("username", cleanQuery + "\uF8FF")
        .limit(30)
        .get()
        .await()
    val users = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { AppUser.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }.filter { it.id != currentUserId }
    return applySearchMuteFilterIfNeeded(users)
}

suspend fun FirestoreService.fetchUsersInBatches(userIds: List<String>): List<AppUser> {
    if (userIds.isEmpty()) return emptyList()
    val allUsers = userIds.distinct().chunked(10).flatMap { batch ->
        fetchUsers(batch)
    }
    val seen = mutableSetOf<String>()
    return allUsers.filter { seen.add(it.id) }
}

suspend fun FirestoreService.fetchUserDataForNova(userId: String): AppUser = fetchUser(userId)

suspend fun FirestoreService.fetchSuggestedUsers(): List<AppUser> =
    fetchNewConversationSuggestions(recentPartnerIds = emptyList())

/** Port de ExploreViewModel.fetchPopularUsersForExplore (privado en iOS). */
suspend fun FirestoreService.fetchPublicUsersForExplore(
    excludingUserId: String,
    limit: Int = 30,
): List<AppUser> {
    val snap = db.collection("users")
        .whereEqualTo("isPrivate", false)
        .limit(limit.toLong())
        .get()
        .await()
    return snap.documents.mapNotNull { doc ->
        if (doc.id == excludingUserId) return@mapNotNull null
        @Suppress("UNCHECKED_CAST")
        AppUser.from(doc.id, doc.data as Map<String, Any?>)
    }
}

suspend fun FirestoreService.fetchNewConversationSuggestions(
    recentPartnerIds: List<String>,
    limit: Int = 40,
): List<AppUser> {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
    val orderedRecentIds = uniquePreservingOrder(
        recentPartnerIds
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != currentUserId },
    )

    val recentUsers = if (orderedRecentIds.isNotEmpty()) {
        runCatching { fetchUsers(orderedRecentIds) }.getOrDefault(emptyList())
    } else {
        emptyList()
    }
    val mutualUsers = runCatching { fetchMutuals(currentUserId) }.getOrDefault(emptyList())
    val followingUsers = runCatching { fetchFollowing(currentUserId) }.getOrDefault(emptyList())

    val merged = mergeNewConversationSuggestions(
        orderedRecentIds, recentUsers, mutualUsers, followingUsers, currentUserId, limit,
    )
    return applySearchMuteFilterIfNeeded(merged)
}

private fun uniquePreservingOrder(values: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return values.filter { seen.add(it) }
}

private fun mergeNewConversationSuggestions(
    orderedRecentIds: List<String>,
    recentUsers: List<AppUser>,
    mutualUsers: List<AppUser>,
    followingUsers: List<AppUser>,
    currentUserId: String,
    limit: Int,
): List<AppUser> {
    val seen = mutableSetOf<String>()
    val merged = mutableListOf<AppUser>()
    fun appendUnique(users: List<AppUser>) {
        for (user in users) {
            if (user.id != currentUserId && seen.add(user.id)) merged += user
        }
    }
    val recentById = recentUsers.associateBy { it.id }
    appendUnique(orderedRecentIds.mapNotNull { recentById[it] })
    appendUnique(mutualUsers)
    appendUnique(followingUsers)
    return merged.take(limit)
}

private suspend fun FirestoreService.applySearchMuteFilterIfNeeded(users: List<AppUser>): List<AppUser> {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return users
    val snap = db.collection("users").document(currentUserId).get().await()
    @Suppress("UNCHECKED_CAST")
    val muteSettings = snap.data?.get("muteSettings") as? Map<String, Any?> ?: return users
    val hideFromSearch = muteSettings["hideFromSearch"] as? Boolean ?: false
    if (!hideFromSearch) return users
    @Suppress("UNCHECKED_CAST")
    val mutedUsers = (muteSettings["mutedUsers"] as? List<*>)?.filterIsInstance<String>()
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        .orEmpty()
    if (mutedUsers.isEmpty()) return users
    return users.filter { it.id !in mutedUsers }
}
