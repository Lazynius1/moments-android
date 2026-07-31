package com.moments.android.views.nova.tools

import com.moments.android.models.AppUser
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchAllStories
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.firestore.fetchUserByUsername
import com.moments.android.services.firestore.fetchUsersWithSharedInterests
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.social.EchoService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Date

/**
 * Port de `Views/Nova/Tools/NovaProfileTools.swift`.
 * Snapshots de perfil, social, privacy writes y resúmenes de contenido para Nova.
 */
class NovaProfileTools(
    private val firestoreService: FirestoreService = FirestoreService(),
    private val privacyService: PrivacyService = PrivacyService,
    private val echoService: EchoService = EchoService,
) {
    suspend fun myProfileSnapshot(userId: String): Map<String, Any?> =
        runCatching { profileObject(fetchUser(userId)) }
            .getOrElse(::errorObject)

    suspend fun followersSummary(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val recent = firestoreService.fetchFollowersWithTimestamps(userId)
            mapOf(
                "total_count" to NovaJSON.int(recent.size),
                "recent_followers" to recent.take(capped).map { (user, timestamp) ->
                    mapOf(
                        "user_id" to user.id,
                        "username" to user.username,
                        "bio_preview" to user.bio.orEmpty().take(80),
                        "timestamp" to NovaJSON.iso(timestamp),
                    )
                },
            )
        }.getOrElse { listError("recent_followers", it) }
    }

    suspend fun followingSummary(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val following = firestoreService.fetchFollowing(userId)
            mapOf(
                "total_count" to NovaJSON.int(following.size),
                "following" to following.take(capped).map(::userPreview),
            )
        }.getOrElse { listError("following", it) }
    }

    suspend fun mutuals(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val mutuals = firestoreService.fetchMutuals(userId)
            mapOf(
                "total_count" to NovaJSON.int(mutuals.size),
                "mutuals" to mutuals.take(capped).map(::userPreview),
            )
        }.getOrElse { listError("mutuals", it) }
    }

    suspend fun sharedInterestUsers(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val me = fetchUser(userId)
            val users = firestoreService.fetchUsersWithSharedInterests(me.interests, userId)
            mapOf(
                "total_count" to NovaJSON.int(users.size),
                "users" to users.take(capped).map { user ->
                    mapOf(
                        "user_id" to user.id,
                        "username" to user.username,
                        "shared_interests" to user.interests.filter(me.interests::contains),
                        "bio_preview" to user.bio.orEmpty().take(80),
                    )
                },
            )
        }.getOrElse { listError("users", it) }
    }

    suspend fun findUser(username: String): Map<String, Any?> {
        val clean = username.cleanUsername()
        if (clean.isEmpty()) return mapOf("error" to "missing_username")
        return runCatching { profileObject(firestoreService.fetchUserByUsername(clean)) }
            .getOrElse(::errorObject)
    }

    suspend fun sendFollowRequest(currentUserId: String, username: String): Map<String, Any?> =
        runCatching {
            val user = firestoreService.fetchUserByUsername(username.cleanUsername())
            firestoreService.sendFollowRequest(currentUserId, user.id)
            mapOf(
                "success" to true,
                "target_user_id" to user.id,
                "username" to user.username,
            )
        }.getOrElse(::failureObject)

    suspend fun profilePrivacy(userId: String): Map<String, Any?> =
        runCatching {
            val settings = privacyService.fetchPrivacySettings(userId)
            mapOf(
                "is_private" to settings.isPrivate,
                "show_mutuals" to settings.showMutuals,
                "show_following" to settings.showFollowing,
                "show_followers" to settings.showFollowers,
            )
        }.getOrElse(::errorObject)

    suspend fun updatePrivacy(
        userId: String,
        isPrivate: Boolean?,
        showMutuals: Boolean?,
        showFollowing: Boolean?,
        showFollowers: Boolean?,
    ): Map<String, Any?> =
        runCatching {
            privacyService.updatePrivacySettings(
                userId = userId,
                isPrivate = isPrivate,
                showMutuals = showMutuals,
                showFollowing = showFollowing,
                showFollowers = showFollowers,
            )
            mapOf(
                "success" to true,
                "is_private" to isPrivate,
                "show_mutuals" to showMutuals,
                "show_following" to showFollowing,
                "show_followers" to showFollowers,
            )
        }.getOrElse(::failureObject)

    suspend fun updateBio(userId: String, bio: String): Map<String, Any?> =
        runCatching {
            val me = fetchUser(userId)
            firestoreService.updateProfileDetails(
                userId = userId,
                oldBio = me.bio,
                newBio = bio,
                oldWebsite = null,
                newWebsite = null,
            )
            mapOf("success" to true, "bio" to bio)
        }.getOrElse(::failureObject)

    suspend fun updateWebsite(userId: String, website: String): Map<String, Any?> =
        runCatching {
            val me = fetchUser(userId)
            firestoreService.updateProfileDetails(
                userId = userId,
                oldBio = null,
                newBio = null,
                oldWebsite = me.websiteUrl,
                newWebsite = website,
            )
            mapOf("success" to true, "website" to website)
        }.getOrElse(::failureObject)

    suspend fun updateActiveHours(
        userId: String,
        startHour: String?,
        endHour: String?,
        clear: Boolean,
    ): Map<String, Any?> =
        runCatching {
            if (clear) {
                firestoreService.clearActiveHours(userId)
                return@runCatching mapOf("success" to true, "cleared" to true)
            }
            if (startHour.isNullOrEmpty() || endHour.isNullOrEmpty()) {
                return@runCatching mapOf("success" to false, "error" to "missing_hours")
            }
            firestoreService.updateActiveHours(userId, startHour, endHour)
            mapOf("success" to true, "start_hour" to startHour, "end_hour" to endHour)
        }.getOrElse(::failureObject)

    suspend fun updateNotificationPreferences(
        userId: String,
        preferences: Map<String, Boolean>,
    ): Map<String, Any?> =
        runCatching {
            val current = fetchUser(userId)
            val merged = (current.notificationPreferences ?: emptyMap()) + preferences
            firestoreService.updateNotificationPreferences(userId, merged)
            mapOf("success" to true, "preferences" to merged)
        }.getOrElse(::failureObject)

    suspend fun userProfileSnapshot(
        userId: String,
        username: String?,
        targetUserId: String?,
    ): Map<String, Any?> =
        runCatching {
            val user = when {
                !targetUserId.isNullOrBlank() -> fetchUser(targetUserId)
                !username.isNullOrBlank() -> firestoreService.fetchUserByUsername(username.cleanUsername())
                else -> fetchUser(userId)
            }
            profileObject(user)
        }.getOrElse(::errorObject)

    suspend fun recentMomentsSummary(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val moments = firestoreService.fetchMomentsFromUsers(
                userIds = listOf(userId),
                perUserLimit = capped,
                totalLimit = capped,
            )
            mapOf(
                "total_count" to NovaJSON.int(moments.size),
                "recent_moments" to moments.take(capped).map(::momentSummaryObject),
            )
        }.getOrElse { listError("recent_moments", it) }
    }

    suspend fun recentStoriesSummary(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val stories = firestoreService.fetchAllStories(userId)
            val now = Date()
            val activeCount = stories.count { it.expirationDate > now }
            mapOf(
                "total_count" to NovaJSON.int(stories.size),
                "active_count" to NovaJSON.int(activeCount),
                "archived_count" to NovaJSON.int(stories.size - activeCount),
                "recent_stories" to stories.take(capped).map { storySummaryObject(it, now) },
            )
        }.getOrElse {
            mapOf(
                "error" to (it.message ?: "Unknown error"),
                "total_count" to 0,
                "active_count" to 0,
                "archived_count" to 0,
                "recent_stories" to emptyList<Any>(),
            )
        }
    }

    suspend fun profileAndContentOverview(
        userId: String,
        momentLimit: Int = 5,
        storyLimit: Int = 5,
    ): Map<String, Any?> = coroutineScope {
        val profile = async { myProfileSnapshot(userId) }
        val moments = async { recentMomentsSummary(userId, momentLimit) }
        val stories = async { recentStoriesSummary(userId, storyLimit) }
        mapOf(
            "profile" to profile.await(),
            "moments" to moments.await(),
            "stories" to stories.await(),
        )
    }

    suspend fun momentDetails(momentId: String, userId: String): Map<String, Any?> =
        runCatching {
            val moment = firestoreService.fetchMoment(momentId, userId)
            mapOf(
                "moment_id" to (moment.id ?: momentId),
                "author_id" to moment.authorId,
                "username" to moment.username,
                "content" to moment.content,
                "comment_count" to NovaJSON.int(moment.commentCount),
                "created_at" to NovaJSON.iso(moment.timestamp),
                "is_archived" to (moment.isArchived ?: false),
                "has_location" to (moment.locationCoordinate != null),
                "location_name" to moment.location.orEmpty(),
            )
        }.getOrElse(::errorObject)

    suspend fun echoHistorySummary(userId: String, limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val echoes = echoService.fetchEchoHistoryOnce(userId)
            mapOf(
                "total_count" to NovaJSON.int(echoes.size),
                "echoes" to echoes.take(capped).map { echo ->
                    mapOf(
                        "echo_id" to echo.id.orEmpty(),
                        "status" to echo.status.raw,
                        "participant_count" to NovaJSON.int(echo.participants.size),
                        "accepted_count" to NovaJSON.int(
                            echo.participants.count { it.status == EchoParticipantStatus.ACCEPTED },
                        ),
                        "location_name" to echo.locationName.orEmpty(),
                        "created_at" to NovaJSON.iso(echo.createdAt),
                        "expires_at" to NovaJSON.iso(echo.expiresAt),
                    )
                },
            )
        }.getOrElse { listError("echoes", it) }
    }

    // MARK: - Private

    private suspend fun fetchUser(userId: String): AppUser = firestoreService.fetchUser(userId)

    private fun profileObject(user: AppUser): Map<String, Any?> = mapOf(
        "user_id" to user.id,
        "username" to user.username,
        "bio" to user.bio.orEmpty(),
        "website" to user.websiteUrl.orEmpty(),
        "is_private" to user.isPrivate,
        "followers_count" to NovaJSON.int(user.followersCount),
        "following_count" to NovaJSON.int(user.followingCount),
        "moments_count" to NovaJSON.int(user.momentsCount),
        "interests" to user.interests,
        "active_hours" to mapOf(
            "start_hour" to user.activeHoursStart.orEmpty(),
            "end_hour" to user.activeHoursEnd.orEmpty(),
        ),
        "notification_preferences" to (user.notificationPreferences ?: emptyMap<String, Boolean>()),
    )

    private fun userPreview(user: AppUser): Map<String, Any?> = mapOf(
        "user_id" to user.id,
        "username" to user.username,
        "bio_preview" to user.bio.orEmpty().take(80),
    )

    private fun momentSummaryObject(moment: Moment): Map<String, Any?> {
        val visibleMedia = moment.visibleMediaItems
        return mapOf(
            "moment_id" to moment.id.orEmpty(),
            "created_at" to NovaJSON.iso(moment.timestamp),
            "content" to moment.content,
            "audience" to moment.audience.orEmpty(),
            "comment_count" to NovaJSON.int(moment.commentCount),
            "reaction_kinds_count" to NovaJSON.int(moment.reactions.keys.size),
            "total_reactions_count" to NovaJSON.int(moment.reactions.values.sumOf(List<String>::size)),
            "is_archived" to (moment.isArchived ?: false),
            "is_scheduled" to moment.isScheduled,
            "has_location" to (moment.locationCoordinate != null || moment.location.orEmpty().isNotEmpty()),
            "location_name" to moment.location.orEmpty(),
            "media_count" to NovaJSON.int(visibleMedia.size),
            "primary_media_type" to (visibleMedia.firstOrNull()?.type?.raw.orEmpty()),
            "media_types" to visibleMedia.map { it.type.raw },
            "tagged_users_count" to NovaJSON.int(moment.taggedUsers?.size ?: 0),
            "has_hidden_layers" to moment.hasHiddenLayers,
        )
    }

    private fun storySummaryObject(story: Story, now: Date): Map<String, Any?> = mapOf(
        "story_id" to story.id.orEmpty(),
        "created_at" to NovaJSON.iso(story.timestamp),
        "expires_at" to NovaJSON.iso(story.expirationDate),
        "is_active" to (story.expirationDate > now),
        "audience" to story.audience.orEmpty(),
        "text" to story.text.orEmpty(),
        "media_type" to story.mediaItem.type.raw,
        "aspect_ratio" to story.aspectRatio.orEmpty(),
        "has_stickers" to !story.stickers.isNullOrEmpty(),
        "is_chain_story" to (story.chainId != null),
        "chain_title" to story.chainTitle.orEmpty(),
    )

    private fun errorObject(error: Throwable): Map<String, Any?> =
        mapOf("error" to (error.message ?: "Unknown error"))

    private fun failureObject(error: Throwable): Map<String, Any?> =
        mapOf("success" to false, "error" to (error.message ?: "Unknown error"))

    private fun listError(key: String, error: Throwable): Map<String, Any?> =
        mapOf(
            "error" to (error.message ?: "Unknown error"),
            "total_count" to 0,
            key to emptyList<Any>(),
        )

    /** ≡ iOS `CharacterSet(charactersIn: "@ ").union(.whitespacesAndNewlines)` trim. */
    private fun String?.cleanUsername(): String =
        orEmpty().trim().trimStart('@').trim()
}
