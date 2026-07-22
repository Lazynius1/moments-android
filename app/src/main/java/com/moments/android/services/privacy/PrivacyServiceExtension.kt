package com.moments.android.services.privacy

import com.google.firebase.firestore.FieldValue
import com.moments.android.models.Moment
import com.moments.android.models.Story
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import com.moments.android.services.firestore.fetchMutuals

// MARK: - Filtrado de contenido visible (PrivacyServiceExtension.swift)

suspend fun PrivacyService.filterVisibleContent(
    moments: List<Moment>,
    viewerId: String,
): List<Moment> {
    if (moments.isEmpty()) return emptyList()
    val visibleIds = coroutineScope {
        moments.map { moment ->
            async {
                val canView = canViewMoment(moment, viewerId)
                if (canView) moment.id else null
            }
        }.awaitAll().filterNotNull().toSet()
    }
    return moments.filter { it.id != null && it.id in visibleIds }
}

suspend fun PrivacyService.canViewMoment(moment: Moment, viewerId: String): Boolean =
    canUserViewMomentEnhanced(moment, viewerId)

// MARK: - Conexión mutua y mejores amigos

suspend fun PrivacyService.checkMutualConnection(user1: String, user2: String): Boolean =
    coroutineScope {
        val user1FollowsUser2 = async { firestore.isFollowing(user1, user2) }
        val user2FollowsUser1 = async { firestore.isFollowing(user2, user1) }
        user1FollowsUser2.await() && user2FollowsUser1.await()
    }

suspend fun PrivacyService.checkIfBestFriend(userId: String, friendId: String): Boolean =
    dedupeInFlightBestFriend("$userId|$friendId") {
        val snap = database.collection("users").document(userId).get().await()
        @Suppress("UNCHECKED_CAST")
        val bestFriends = (snap.data as? Map<String, Any?>)?.get("bestFriends") as? List<*>
        bestFriends?.filterIsInstance<String>()?.contains(friendId) == true
    }

// MARK: - Audiencias personalizadas

suspend fun PrivacyService.checkCustomAudience(
    contentType: String,
    contentId: String,
    authorId: String,
    viewerId: String,
): Boolean {
    val snap = database.collection("users").document(authorId)
        .collection("customAudiences")
        .document("${contentType}_$contentId")
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val allowedUsers = (snap.data as? Map<String, Any?>)?.get("allowedUsers") as? List<*>
    return allowedUsers?.filterIsInstance<String>()?.contains(viewerId) == true
}

private suspend fun PrivacyService.checkStoryVisibilitySettings(
    authorId: String,
    viewerId: String,
): Boolean {
    val snap = database.collection("users").document(authorId).get().await()
    @Suppress("UNCHECKED_CAST")
    val data = snap.data as? Map<String, Any?> ?: return canViewUserContent(viewerId, authorId)
    @Suppress("UNCHECKED_CAST")
    val settings = data["contentVisibilitySettings"] as? Map<String, Any?> ?: run {
        return canViewUserContent(viewerId, authorId)
    }
    return when (settings["storyVisibility"] as? String) {
        "everyone" -> canViewUserContent(viewerId, authorId)
        "mutuals" -> checkMutualConnection(viewerId, authorId)
        "bestFriends" -> checkIfBestFriend(authorId, viewerId)
        "custom" -> {
            @Suppress("UNCHECKED_CAST")
            val customViewers = settings["customStoryViewers"] as? List<*>
            customViewers?.filterIsInstance<String>()?.contains(viewerId) == true
        }
        else -> false
    }
}

suspend fun PrivacyService.saveCustomAudience(
    contentType: String,
    contentId: String,
    authorId: String,
    allowedUsers: List<String>,
) {
    val data = mapOf(
        "contentType" to contentType,
        "contentId" to contentId,
        "allowedUsers" to allowedUsers,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    database.collection("users").document(authorId)
        .collection("customAudiences")
        .document("${contentType}_$contentId")
        .set(data)
        .await()
}

// MARK: - Content viewers

suspend fun PrivacyService.getContentViewers(moment: Moment): List<String> {
    val audience = ContentAudience.from(moment.audience)
    return when (audience) {
        ContentAudience.EVERYONE -> fetchPotentialViewers(moment.authorId)
        ContentAudience.MUTUALS -> fetchMutualsUserIds(moment.authorId)
        ContentAudience.BEST_FRIENDS -> fetchBestFriendsUserIds(moment.authorId)
        ContentAudience.CUSTOM -> fetchCustomAudienceUserIds(
            contentType = "moment",
            contentId = moment.id.orEmpty(),
            authorId = moment.authorId,
        )
        ContentAudience.CUSTOM_LIST -> fetchCustomListViewersForMoment(moment)
        ContentAudience.ONLY_ME -> listOf(moment.authorId)
    }
}

private suspend fun PrivacyService.fetchCustomListViewersForMoment(moment: Moment): List<String> {
    val momentId = moment.id ?: return emptyList()
    val snap = database.collection("users").document(moment.authorId)
        .collection("moments").document(momentId)
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val customListId = (snap.data as? Map<String, Any?>)?.get("customListId") as? String
        ?: return emptyList()
    return getCustomListViewers(customListId, moment.authorId)
}

suspend fun PrivacyService.checkCustomList(
    contentType: String,
    contentId: String,
    authorId: String,
    viewerId: String,
): Boolean {
    val contentCollection = if (contentType == "story") "stories" else "moments"
    val snap = database.collection("users").document(authorId)
        .collection(contentCollection).document(contentId)
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val customListId = (snap.data as? Map<String, Any?>)?.get("customListId") as? String
        ?: return false
    return checkUserInList(viewerId, customListId, authorId)
}

private suspend fun PrivacyService.fetchPotentialViewers(userId: String): List<String> =
    runCatching {
        fetchPrivacySettings(userId)
        firestore.fetchFollowers(userId).map { it.id }
    }.getOrDefault(emptyList())

private suspend fun PrivacyService.fetchMutualsUserIds(userId: String): List<String> =
    runCatching {
        firestore.fetchMutuals(userId).map { it.id }
    }.getOrDefault(emptyList())

private suspend fun PrivacyService.fetchBestFriendsUserIds(userId: String): List<String> {
    val snap = database.collection("users").document(userId).get().await()
    @Suppress("UNCHECKED_CAST")
    val bestFriends = (snap.data as? Map<String, Any?>)?.get("bestFriends") as? List<*>
    return bestFriends?.filterIsInstance<String>() ?: emptyList()
}

private suspend fun PrivacyService.fetchCustomAudienceUserIds(
    contentType: String,
    contentId: String,
    authorId: String,
): List<String> {
    val snap = database.collection("users").document(authorId)
        .collection("customAudiences")
        .document("${contentType}_$contentId")
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val allowedUsers = (snap.data as? Map<String, Any?>)?.get("allowedUsers") as? List<*>
    return allowedUsers?.filterIsInstance<String>() ?: emptyList()
}

// MARK: - Listas personalizadas reutilizables

interface PrivacyContent {
    val authorId: String
    val customListId: String?
}

suspend fun PrivacyService.canUserViewContentWithCustomList(
    content: PrivacyContent,
    viewerId: String,
): Boolean {
    if (content.authorId == viewerId) return true
    if (checkMutualBlocks(viewerId, content.authorId)) return false
    val listId = content.customListId?.takeIf { it.isNotEmpty() } ?: return false
    return checkUserInList(viewerId, listId, content.authorId)
}

val Moment.asPrivacyContent: PrivacyContent
    get() = object : PrivacyContent {
        override val authorId: String = this@asPrivacyContent.authorId
        override val customListId: String? = this@asPrivacyContent.customListId
    }

val Story.asPrivacyContent: PrivacyContent
    get() = object : PrivacyContent {
        override val authorId: String = this@asPrivacyContent.authorId
        override val customListId: String? = this@asPrivacyContent.customListId
    }

private suspend fun PrivacyService.checkCustomListMembership(story: Story, viewerId: String): Boolean {
    val customListId = story.customListId?.takeIf { it.isNotEmpty() } ?: return false
    return checkUserInList(viewerId, customListId, story.authorId)
}

private suspend fun PrivacyService.checkCustomListMembership(moment: Moment, viewerId: String): Boolean {
    val customListId = moment.customListId?.takeIf { it.isNotEmpty() } ?: return false
    return checkUserInList(viewerId, customListId, moment.authorId)
}

private suspend fun PrivacyService.checkUserInList(
    userId: String,
    listId: String,
    listOwnerId: String,
): Boolean {
    val snap = database.collection("users").document(listOwnerId)
        .collection("customAudienceLists").document(listId)
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val members = (snap.data as? Map<String, Any?>)?.get("members") as? List<*>
    return members?.filterIsInstance<String>()?.contains(userId) == true
}

suspend fun PrivacyService.getCustomListViewers(listId: String, ownerId: String): List<String> {
    val snap = database.collection("users").document(ownerId)
        .collection("customAudienceLists").document(listId)
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val members = (snap.data as? Map<String, Any?>)?.get("members") as? List<*>
    return members?.filterIsInstance<String>() ?: emptyList()
}

// MARK: - Visibilidad mejorada de momentos e historias

suspend fun PrivacyService.canUserViewMomentEnhanced(moment: Moment, viewerId: String): Boolean {
    val momentId = moment.id?.takeIf { it.isNotEmpty() } ?: return false
    if (moment.authorId == viewerId) return true
    if (isAuthorMutedForViewer(viewerId, moment.authorId)) return false
    if (checkMutualBlocks(viewerId, moment.authorId)) return false
    if (isViewerHiddenFromAuthorContent(moment.authorId, viewerId)) return false

    return when (moment.audience ?: "everyone") {
        "everyone" -> canViewUserContentAfterBlockCheck(viewerId, moment.authorId)
        "mutuals" -> checkMutualConnection(viewerId, moment.authorId)
        "bestFriends" -> checkIfBestFriend(moment.authorId, viewerId)
        "custom" -> checkCustomAudience("moment", momentId, moment.authorId, viewerId)
        "customList" -> checkCustomListMembership(moment, viewerId)
        else -> false
    }
}

suspend fun PrivacyService.canUserViewStoryEnhanced(story: Story, viewerId: String): Boolean {
    if (story.authorId == viewerId) return true
    if (isAuthorMutedForViewer(viewerId, story.authorId)) return false
    if (checkMutualBlocks(viewerId, story.authorId)) return false
    if (isViewerHiddenFromAuthorContent(story.authorId, viewerId)) return false

    return when (story.audience ?: "everyone") {
        "everyone" -> canViewUserContentAfterBlockCheck(viewerId, story.authorId)
        "mutuals" -> checkMutualConnection(viewerId, story.authorId)
        "bestFriends" -> checkIfBestFriend(story.authorId, viewerId)
        "custom" -> checkCustomAudience(
            contentType = "story",
            contentId = story.id.orEmpty(),
            authorId = story.authorId,
            viewerId = viewerId,
        )
        "customList" -> checkCustomListMembership(story, viewerId)
        "onlyMe" -> false
        else -> false
    }
}

// MARK: - Hidden / muted checks

private suspend fun PrivacyService.isViewerHiddenFromAuthorContent(
    authorId: String,
    viewerId: String,
): Boolean = dedupeInFlightHidden("$authorId|$viewerId") {
    val snap = database.collection("users").document(authorId).get().await()
    @Suppress("UNCHECKED_CAST")
    val data = snap.data as? Map<String, Any?> ?: return@dedupeInFlightHidden false
    @Suppress("UNCHECKED_CAST")
    val settings = data["contentVisibilitySettings"] as? Map<String, Any?> ?: return@dedupeInFlightHidden false
    @Suppress("UNCHECKED_CAST")
    val hiddenFromUsers = settings["hiddenFromUsers"] as? List<*>
    hiddenFromUsers?.filterIsInstance<String>()?.contains(viewerId) == true
}

private suspend fun PrivacyService.isAuthorMutedForViewer(
    viewerId: String,
    authorId: String,
): Boolean {
    if (viewerId.isEmpty() || authorId.isEmpty()) return false
    if (viewerId == authorId) return false
    val mutedIds = fetchMutedUsersCached(viewerId)
    return authorId in mutedIds
}

// MARK: - Explore (más permisivo)

suspend fun PrivacyService.canUserViewMomentInExplore(moment: Moment, viewerId: String): Boolean {
    if (moment.authorId == viewerId) return true
    if (isAuthorMutedForViewer(viewerId, moment.authorId)) return false
    if (checkMutualBlocks(viewerId, moment.authorId)) return false
    if (isViewerHiddenFromAuthorContent(moment.authorId, viewerId)) return false

    return when (moment.audience ?: "everyone") {
        "everyone" -> canViewUserContentForExplore(viewerId, moment.authorId)
        "mutuals" -> checkMutualConnection(viewerId, moment.authorId)
        "bestFriends" -> checkIfBestFriend(moment.authorId, viewerId)
        "custom" -> checkCustomAudience(
            contentType = "moment",
            contentId = moment.id.orEmpty(),
            authorId = moment.authorId,
            viewerId = viewerId,
        )
        "customList" -> checkCustomListMembership(moment, viewerId)
        "onlyMe" -> false
        else -> false
    }
}

suspend fun PrivacyService.canViewUserContentForExplore(
    viewerId: String,
    targetUserId: String,
): Boolean {
    if (viewerId == targetUserId) return true
    return runCatching {
        val settings = fetchPrivacySettings(targetUserId)
        if (!settings.isPrivate) true else firestore.isFollowing(viewerId, targetUserId)
    }.getOrDefault(false)
}

fun PrivacyService.canShareMoment(moment: Moment): Boolean {
    val audience = moment.audience ?: "everyone"
    return audience == "everyone"
}
