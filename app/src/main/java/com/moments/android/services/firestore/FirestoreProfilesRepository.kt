package com.moments.android.services.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.moments.android.models.AppUser
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/** Port de FirestoreProfilesRepository.swift — métodos de perfil/usuario. */

suspend fun FirestoreService.fetchUser(userId: String): AppUser {
    require(userId.isNotEmpty()) { "El userId está vacío" }
    val source = if (NetworkMonitor.isConnected) Source.DEFAULT else Source.CACHE
    val snap = db.collection("users").document(userId).get(source).await()
    if (!snap.exists()) error("User not found: $userId")
    @Suppress("UNCHECKED_CAST")
    val data = snap.data as Map<String, Any?>
    return AppUser.from(snap.id, data)
}

suspend fun FirestoreService.fetchUserProfile(userId: String): AppUser = fetchUser(userId)

suspend fun FirestoreService.fetchUserProfileWithAvailability(userId: String): Pair<AppUser, PublicProfileAvailability> {
    val snap = db.collection("users").document(userId).get().await()
    if (!snap.exists()) error("Document not found")
    @Suppress("UNCHECKED_CAST")
    val data = snap.data as Map<String, Any?>
    val availability = PublicProfileAvailability.fromUserData(data)
    return AppUser.from(snap.id, data) to availability
}

suspend fun FirestoreService.checkPublicProfileAvailability(userId: String): PublicProfileAvailability {
    val snap = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
        ?: return PublicProfileAvailability.AVAILABLE
    if (!snap.exists()) return PublicProfileAvailability.UNAVAILABLE
    @Suppress("UNCHECKED_CAST")
    return PublicProfileAvailability.fromUserData(snap.data as Map<String, Any?>)
}

suspend fun FirestoreService.fetchUserByUsername(username: String): AppUser {
    val clean = username.trim().lowercase()
    val snap = db.collection("users")
        .whereEqualTo("username", clean)
        .limit(1)
        .get()
        .await()
    val doc = snap.documents.firstOrNull() ?: error("Usuario no encontrado")
    @Suppress("UNCHECKED_CAST")
    return AppUser.from(doc.id, doc.data as Map<String, Any?>)
}

suspend fun FirestoreService.createUser(
    userId: String,
    username: String,
    email: String,
    interests: List<String>,
    profileImagePath: String?,
) {
    val usernameLower = username.lowercase()
    val now = FieldValue.serverTimestamp()
    val notificationPreferences = hashMapOf(
        "like" to true,
        "newFollower" to true,
        "followRequest" to true,
        "mutualConnection" to true,
        "comment" to true,
        "storyReaction" to true,
        "gentleReminders" to true,
        "commentsMutualsOnly" to false,
        "muteOldPostReactions" to false,
    )
    val profile = hashMapOf<String, Any>(
        "id" to userId,
        "username" to usernameLower,
        "email" to email,
        "interests" to interests,
        "isPlusSubscriber" to false,
        "blockedUsers" to emptyList<String>(),
        "isPrivate" to false,
        "showMutuals" to true,
        "showFollowing" to true,
        "showFollowers" to true,
        "notificationPreferences" to notificationPreferences,
        "bestFriends" to emptyList<String>(),
        "isActive" to true,
        "isSuspended" to false,
        "ownedBadges" to emptyList<String>(),
        "showBadge" to true,
        "showPlusBadge" to true,
        "isVerified" to false,
        "onlineStatus" to "offline",
        "isOnline" to false,
        "createdAt" to now,
        "updatedAt" to now,
    )
    if (profileImagePath != null) profile["profileImagePath"] = profileImagePath

    val index = hashMapOf<String, Any>(
        "userId" to userId,
        "email" to email,
        "createdAt" to now,
        "updatedAt" to now,
    )
    db.runBatch { batch ->
        batch.set(db.collection("users").document(userId), profile)
        batch.set(db.collection("usernames").document(usernameLower), index)
    }.await()
    verifyUserCreation(userId)
}

suspend fun FirestoreService.verifyUserCreation(userId: String): Boolean {
    delay(1_000)
    val snap = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
    return snap?.exists() == true
}

suspend fun FirestoreService.changeUsername(userId: String, oldUsername: String, newUsername: String) {
    val clean = newUsername.trim().lowercase()
    require(clean.length in 3..30) { "Username must be between 3 and 30 characters" }
    require(clean.all { it.isLetterOrDigit() || it == '_' }) { "Invalid username characters" }
    require(clean != oldUsername.lowercase()) { "New username must be different" }

    val userRef = db.collection("users").document(userId)
    val userSnap = userRef.get().await()
    @Suppress("UNCHECKED_CAST")
    val userData = userSnap.data as? Map<String, Any?> ?: emptyMap()
    (userData["lastUsernameChange"] as? Timestamp)?.toDate()?.let { lastChange ->
        val sixMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.time
        if (lastChange.after(sixMonthsAgo)) error("Username change cooldown active")
    }

    val newUsernameRef = db.collection("usernames").document(clean)
    if (newUsernameRef.get().await().exists()) error("Username taken")

    val oldUsernameRef = db.collection("usernames").document(oldUsername.lowercase())
    val oldSnap = oldUsernameRef.get().await()
    @Suppress("UNCHECKED_CAST")
    val newUsernameData = (oldSnap.data as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
    newUsernameData["userId"] = userId
    newUsernameData["updatedAt"] = FieldValue.serverTimestamp()
    (userData["email"] as? String)?.takeIf { it.isNotBlank() }?.let { newUsernameData["email"] = it }
    if (newUsernameData["createdAt"] == null) newUsernameData["createdAt"] = FieldValue.serverTimestamp()

    db.runBatch { batch ->
        batch.delete(oldUsernameRef)
        batch.set(newUsernameRef, newUsernameData)
        batch.update(userRef, mapOf(
            "username" to clean,
            "lastUsernameChange" to FieldValue.serverTimestamp(),
        ))
    }.await()
}

suspend fun FirestoreService.updateProfilePicture(userId: String, profileImagePath: String) {
    db.collection("users").document(userId)
        .update("profileImagePath", profileImagePath).await()
}

suspend fun FirestoreService.removeProfilePicture(userId: String) {
    db.collection("users").document(userId)
        .update("profileImagePath", FieldValue.delete()).await()
}

suspend fun FirestoreService.fetchAvailableInterests(): List<String> {
    val snap = db.collection("interests").get().await()
    return snap.documents.mapNotNull { it.data?.get("name") as? String }
}

suspend fun FirestoreService.fetchMutuals(userId: String): List<AppUser> {
    val snap = db.collection("users").document(userId).collection("mutuals")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(1000)
        .get()
        .await()
    val userIds = snap.documents.mapNotNull { doc ->
        (doc.data?.get("userId") as? String) ?: doc.id
    }
    val users = fetchUsersByIdsClean(userIds)
    LocalPersistenceService.saveMutuals(userId, users)
    return users
}

suspend fun FirestoreService.fetchMutualsWithTimestamps(userId: String): List<Pair<AppUser, Date>> {
    val snap = db.collection("users").document(userId).collection("mutuals")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(100)
        .get()
        .await()
    val mutualData = snap.documents.mapNotNull { doc ->
        val data = doc.data ?: return@mapNotNull null
        val id = data["userId"] as? String ?: return@mapNotNull null
        val ts = (data["timestamp"] as? Timestamp)?.toDate() ?: return@mapNotNull null
        id to ts
    }
    if (mutualData.isEmpty()) return emptyList()
    val users = fetchUsersAsync(mutualData.map { it.first })
    val userDict = users.associateBy { it.id }
    return mutualData.mapNotNull { (id, ts) ->
        userDict[id]?.let { it to ts }
    }
}

suspend fun FirestoreService.fetchUsersWithSharedInterests(
    interests: List<String>,
    excludingUserId: String,
): List<AppUser> {
    if (interests.isEmpty()) return emptyList()
    val current = runCatching { fetchUser(excludingUserId) }.getOrNull()
    val blocked = current?.blockedUsers?.toSet().orEmpty()
    val seen = linkedMapOf<String, AppUser>()
    for (batch in interests.chunked(30)) {
        val snap = db.collection("users")
            .whereArrayContainsAny("interests", batch)
            .limit(50)
            .get()
            .await()
        for (doc in snap.documents) {
            if (doc.id == excludingUserId || doc.id in blocked || doc.id in seen) continue
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: continue
            seen[doc.id] = AppUser.from(doc.id, data)
        }
    }
    return seen.values.toList()
}
