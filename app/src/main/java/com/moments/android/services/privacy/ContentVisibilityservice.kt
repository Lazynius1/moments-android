package com.moments.android.services.privacy

import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

// MARK: - UserVisibilitySettings

data class UserVisibilitySettings(
    var defaultStoryVisibility: ContentVisibilityType = ContentVisibilityType.EVERYONE,
    var defaultPostVisibility: ContentVisibilityType = ContentVisibilityType.EVERYONE,
    var customStoryViewers: List<String> = emptyList(),
    var customPostViewers: List<String> = emptyList(),
    var hiddenFromUsers: List<String> = emptyList(),
)

// MARK: - ContentVisibilityType

enum class ContentVisibilityType(val raw: String) {
    EVERYONE("everyone"),
    MUTUALS("mutuals"),
    BEST_FRIENDS("bestFriends"),
    CUSTOM("custom"),
    ONLY_ME("onlyMe");

    companion object {
        fun from(raw: String?): ContentVisibilityType =
            entries.firstOrNull { it.raw == raw } ?: EVERYONE
    }
}

// MARK: - ContentProtocol

interface ContentProtocol {
    val id: String?
    val authorId: String
    val visibilityType: ContentVisibilityType
    val customViewers: List<String>?
    val hiddenFrom: List<String>?
}

// MARK: - ContentVisibilityService

/**
 * Port de ContentVisibilityservice.swift.
 */
object ContentVisibilityService {
    private val firestoreService = FirestoreService()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun canUserSeeContent(
        contentOwnerId: String,
        viewerId: String,
        contentType: ContentVisibilityType,
        customViewers: List<String>? = null,
        @Suppress("UNUSED_PARAMETER") hiddenFrom: List<String>? = null,
    ): Boolean {
        if (contentOwnerId == viewerId) return true
        if (PrivacyService.checkMutualBlocks(viewerId, contentOwnerId)) return false

        return when (contentType) {
            ContentVisibilityType.EVERYONE ->
                checkEveryoneVisibility(contentOwnerId, viewerId)
            ContentVisibilityType.MUTUALS ->
                checkConnectionsVisibility(contentOwnerId, viewerId)
            ContentVisibilityType.BEST_FRIENDS ->
                checkBestFriendsVisibility(contentOwnerId, viewerId)
            ContentVisibilityType.CUSTOM ->
                checkCustomVisibility(contentOwnerId, viewerId, customViewers)
            ContentVisibilityType.ONLY_ME -> false
        }
    }

    private suspend fun checkEveryoneVisibility(contentOwnerId: String, viewerId: String): Boolean =
        runCatching {
            val settings = PrivacyService.fetchPrivacySettings(contentOwnerId)
            if (settings.isPrivate) {
                firestoreService.isFollowing(viewerId, contentOwnerId)
            } else {
                true
            }
        }.getOrDefault(false)

    private suspend fun checkConnectionsVisibility(contentOwnerId: String, viewerId: String): Boolean =
        checkMutualConnection(viewerId, contentOwnerId)

    private suspend fun checkBestFriendsVisibility(contentOwnerId: String, viewerId: String): Boolean {
        val snap = db.collection("users").document(contentOwnerId).get().await()
        @Suppress("UNCHECKED_CAST")
        val bestFriends = (snap.data as? Map<String, Any?>)?.get("bestFriends") as? List<*>
        return bestFriends?.filterIsInstance<String>()?.contains(viewerId) == true
    }

    private suspend fun checkCustomVisibility(
        contentOwnerId: String,
        viewerId: String,
        customViewers: List<String>?,
    ): Boolean {
        if (customViewers != null) return viewerId in customViewers
        return runCatching {
            getUserVisibilitySettings(contentOwnerId).customPostViewers.contains(viewerId)
        }.getOrDefault(false)
    }

    private suspend fun checkMutualConnection(user1: String, user2: String): Boolean =
        coroutineScope {
            val user1FollowsUser2 = async { firestoreService.isFollowing(user1, user2) }
            val user2FollowsUser1 = async { firestoreService.isFollowing(user2, user1) }
            user1FollowsUser2.await() && user2FollowsUser1.await()
        }

    suspend fun getUserVisibilitySettings(userId: String): UserVisibilitySettings {
        val snap = db.collection("users").document(userId).get().await()
        if (!snap.exists()) return UserVisibilitySettings()
        @Suppress("UNCHECKED_CAST")
        val data = snap.data as? Map<String, Any?> ?: return UserVisibilitySettings()
        val settings = UserVisibilitySettings()
        @Suppress("UNCHECKED_CAST")
        val contentSettings = data["contentVisibilitySettings"] as? Map<String, Any?> ?: return settings
        (contentSettings["storyVisibility"] as? String)?.let {
            settings.defaultStoryVisibility = ContentVisibilityType.from(it)
        }
        (contentSettings["postVisibility"] as? String)?.let {
            settings.defaultPostVisibility = ContentVisibilityType.from(it)
        }
        (contentSettings["customStoryViewers"] as? List<*>)?.filterIsInstance<String>()?.let {
            settings.customStoryViewers = it
        }
        (contentSettings["customPostViewers"] as? List<*>)?.filterIsInstance<String>()?.let {
            settings.customPostViewers = it
        }
        (contentSettings["hiddenFromUsers"] as? List<*>)?.filterIsInstance<String>()?.let {
            settings.hiddenFromUsers = it
        }
        return settings
    }

    suspend fun <T : ContentProtocol> filterVisibleContent(
        content: List<T>,
        viewerId: String,
    ): List<T> = coroutineScope {
        val visibilityResults = content.map { item ->
            async {
                val canSee = canUserSeeContent(
                    contentOwnerId = item.authorId,
                    viewerId = viewerId,
                    contentType = item.visibilityType,
                    customViewers = item.customViewers,
                    hiddenFrom = item.hiddenFrom,
                )
                item.id to canSee
            }
        }.awaitAll()
        val visibleIds = visibilityResults.filter { it.second }.mapNotNull { it.first }.toSet()
        content.filter { it.id != null && it.id in visibleIds }
    }

    suspend fun saveUserVisibilitySettings(userId: String, settings: UserVisibilitySettings) {
        val contentSettings = mapOf(
            "storyVisibility" to settings.defaultStoryVisibility.raw,
            "postVisibility" to settings.defaultPostVisibility.raw,
            "customStoryViewers" to settings.customStoryViewers,
            "customPostViewers" to settings.customPostViewers,
            "hiddenFromUsers" to settings.hiddenFromUsers,
        )
        db.collection("users").document(userId)
            .update("contentVisibilitySettings", contentSettings)
            .await()
    }
}

// MARK: - ContentProtocol conformances (equivalente a extension Moment/Story en iOS)

private fun visibilityTypeFromAudience(audience: String?): ContentVisibilityType = when (audience) {
    "everyone" -> ContentVisibilityType.EVERYONE
    "mutuals" -> ContentVisibilityType.MUTUALS
    "bestFriends" -> ContentVisibilityType.BEST_FRIENDS
    "custom" -> ContentVisibilityType.CUSTOM
    else -> ContentVisibilityType.EVERYONE
}

val Moment.asContentProtocol: ContentProtocol
    get() = object : ContentProtocol {
        override val id: String? = this@asContentProtocol.id
        override val authorId: String = this@asContentProtocol.authorId
        override val visibilityType: ContentVisibilityType =
            visibilityTypeFromAudience(this@asContentProtocol.audience)
        override val customViewers: List<String>? = null
        override val hiddenFrom: List<String>? = null
    }

val Story.asContentProtocol: ContentProtocol
    get() = object : ContentProtocol {
        override val id: String? = this@asContentProtocol.id
        override val authorId: String = this@asContentProtocol.authorId
        override val visibilityType: ContentVisibilityType =
            visibilityTypeFromAudience(this@asContentProtocol.audience)
        override val customViewers: List<String>? = null
        override val hiddenFrom: List<String>? = null
    }
