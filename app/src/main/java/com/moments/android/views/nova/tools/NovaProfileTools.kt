package com.moments.android.views.nova.tools

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.moments.android.models.AppUser
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
import java.util.Date

class NovaProfileTools(
    private val firestoreService: FirestoreService = FirestoreService(),
    private val privacyService: PrivacyService = PrivacyService,
    private val echoService: EchoService = EchoService,
) {
    suspend fun myProfileSnapshot(userId: String) = runCatching { profileObject(fetchUser(userId)) }.getOrElse { errorObject(it) }
    suspend fun followersSummary(userId: String, limit: Int = 5): Map<String, Any?> = runCatching { val users = firestoreService.fetchFollowersWithTimestamps(userId); mapOf("total_count" to users.size, "recent_followers" to users.take(limit.coerceIn(1, 10)).map { (user, date) -> userPreview(user) + mapOf("timestamp" to NovaJSON.iso(date)) }) }.getOrElse { listError("recent_followers", it) }
    suspend fun followingSummary(userId: String, limit: Int = 5): Map<String, Any?> = runCatching { val users = firestoreService.fetchFollowing(userId); mapOf("total_count" to users.size, "following" to users.take(limit.coerceIn(1, 10)).map(::userPreview)) }.getOrElse { listError("following", it) }
    suspend fun mutuals(userId: String, limit: Int = 5): Map<String, Any?> = runCatching { val users = firestoreService.fetchMutuals(userId); mapOf("total_count" to users.size, "mutuals" to users.take(limit.coerceIn(1, 10)).map(::userPreview)) }.getOrElse { listError("mutuals", it) }
    suspend fun sharedInterestUsers(userId: String, limit: Int = 5): Map<String, Any?> = runCatching { val me = fetchUser(userId); val users = firestoreService.fetchUsersWithSharedInterests(me.interests, userId); mapOf("total_count" to users.size, "users" to users.take(limit.coerceIn(1, 10)).map { user -> userPreview(user) + mapOf("shared_interests" to user.interests.filter(me.interests::contains)) }) }.getOrElse { listError("users", it) }
    suspend fun findUser(username: String) = username.cleanUsername().takeIf { it.isNotEmpty() }?.let { runCatching { profileObject(firestoreService.fetchUserByUsername(it)) }.getOrElse(::errorObject) } ?: mapOf("error" to "missing_username")
    suspend fun sendFollowRequest(currentUserId: String, username: String): Map<String, Any?> = runCatching { val user = firestoreService.fetchUserByUsername(username.cleanUsername()); firestoreService.sendFollowRequest(currentUserId, user.id); mapOf("success" to true, "target_user_id" to user.id, "username" to user.username) }.getOrElse { failureObject(it) }
    suspend fun profilePrivacy(userId: String): Map<String, Any?> = runCatching { privacyService.fetchPrivacySettings(userId).let { mapOf("is_private" to it.isPrivate, "show_mutuals" to it.showMutuals, "show_following" to it.showFollowing, "show_followers" to it.showFollowers) } }.getOrElse(::errorObject)
    suspend fun updatePrivacy(userId: String, isPrivate: Boolean?, showMutuals: Boolean?, showFollowing: Boolean?, showFollowers: Boolean?): Map<String, Any?> = runCatching { privacyService.updatePrivacySettings(userId, isPrivate, showMutuals, showFollowing, showFollowers); mapOf("success" to true, "is_private" to isPrivate, "show_mutuals" to showMutuals, "show_following" to showFollowing, "show_followers" to showFollowers) }.getOrElse(::failureObject)
    suspend fun updateBio(userId: String, bio: String): Map<String, Any?> = runCatching { val user = fetchUser(userId); firestoreService.updateProfileDetails(userId, oldBio = user.bio, newBio = bio); mapOf("success" to true, "bio" to bio) }.getOrElse(::failureObject)
    suspend fun updateWebsite(userId: String, website: String): Map<String, Any?> = runCatching { val user = fetchUser(userId); firestoreService.updateProfileDetails(userId, oldWebsite = user.websiteUrl, newWebsite = website); mapOf("success" to true, "website" to website) }.getOrElse(::failureObject)
    suspend fun updateActiveHours(userId: String, startHour: String?, endHour: String?, clear: Boolean): Map<String, Any?> = runCatching { if (clear) { firestoreService.clearActiveHours(userId); mapOf("success" to true, "cleared" to true) } else if (startHour.isNullOrEmpty() || endHour.isNullOrEmpty()) mapOf("success" to false, "error" to "missing_hours") else { firestoreService.updateActiveHours(userId, startHour, endHour); mapOf("success" to true, "start_hour" to startHour, "end_hour" to endHour) } }.getOrElse(::failureObject)
    suspend fun updateNotificationPreferences(userId: String, preferences: Map<String, Boolean>): Map<String, Any?> = runCatching { val merged = (fetchUser(userId).notificationPreferences ?: emptyMap()) + preferences; firestoreService.updateNotificationPreferences(userId, merged); mapOf("success" to true, "preferences" to merged) }.getOrElse(::failureObject)
    suspend fun userProfileSnapshot(userId: String, username: String?, targetUserId: String?): Map<String, Any?> = runCatching { profileObject(when { !targetUserId.isNullOrBlank() -> fetchUser(targetUserId); !username.isNullOrBlank() -> firestoreService.fetchUserByUsername(username.cleanUsername()); else -> fetchUser(userId) }) }.getOrElse(::errorObject)
    suspend fun recentMomentsSummary(userId: String, limit: Int = 5): Map<String, Any?> = runCatching { val moments = firestoreService.fetchMomentsFromUsers(listOf(userId), limit.coerceIn(1, 10), limit.coerceIn(1, 10)); mapOf("total_count" to moments.size, "recent_moments" to moments.take(limit.coerceIn(1, 10)).map(::momentSummary)) }.getOrElse { listError("recent_moments", it) }
    suspend fun recentStoriesSummary(userId: String, limit: Int = 5): Map<String, Any?> = runCatching { val stories = firestoreService.fetchAllStories(userId); val now = Date(); mapOf("total_count" to stories.size, "active_count" to stories.count { it.expirationDate > now }, "archived_count" to stories.count { it.expirationDate <= now }, "recent_stories" to stories.take(limit.coerceIn(1, 10)).map { storySummary(it, now) }) }.getOrElse { mapOf("error" to (it.message ?: "Unknown error"), "total_count" to 0, "active_count" to 0, "archived_count" to 0, "recent_stories" to emptyList<Any>()) }
    suspend fun profileAndContentOverview(userId: String, momentLimit: Int = 5, storyLimit: Int = 5): Map<String, Any?> = coroutineScope {
        val profile = async { myProfileSnapshot(userId) }
        val moments = async { recentMomentsSummary(userId, momentLimit) }
        val stories = async { recentStoriesSummary(userId, storyLimit) }
        mapOf("profile" to profile.await(), "moments" to moments.await(), "stories" to stories.await())
    }
    suspend fun momentDetails(momentId: String, userId: String): Map<String, Any?> = runCatching { val moment = firestoreService.fetchMoment(momentId, userId); mapOf("moment_id" to (moment.id ?: momentId), "author_id" to moment.authorId, "username" to moment.username, "content" to moment.content, "comment_count" to moment.commentCount, "created_at" to NovaJSON.iso(moment.timestamp), "is_archived" to (moment.isArchived ?: false), "has_location" to (moment.locationCoordinate != null), "location_name" to moment.location.orEmpty()) }.getOrElse(::errorObject)
    suspend fun echoHistorySummary(userId: String, limit: Int = 5): Map<String, Any?> = runCatching { val echoes = echoService.fetchEchoHistoryOnce(userId); mapOf("total_count" to echoes.size, "echoes" to echoes.take(limit.coerceIn(1, 10)).map { echo -> mapOf("echo_id" to echo.id.orEmpty(), "status" to echo.status.raw, "participant_count" to echo.participants.size, "accepted_count" to echo.participants.count { it.status.raw == "accepted" }, "location_name" to echo.locationName.orEmpty(), "created_at" to NovaJSON.iso(echo.createdAt), "expires_at" to NovaJSON.iso(echo.expiresAt)) }) }.getOrElse { listError("echoes", it) }
    private suspend fun fetchUser(id: String) = firestoreService.fetchUser(id)
    private fun profileObject(user: AppUser): Map<String, Any?> = mapOf("user_id" to user.id, "username" to user.username, "bio" to user.bio.orEmpty(), "website" to user.websiteUrl.orEmpty(), "is_private" to user.isPrivate, "followers_count" to user.followersCount, "following_count" to user.followingCount, "moments_count" to user.momentsCount, "interests" to user.interests, "active_hours" to mapOf("start_hour" to user.activeHoursStart.orEmpty(), "end_hour" to user.activeHoursEnd.orEmpty()), "notification_preferences" to (user.notificationPreferences ?: emptyMap<String, Boolean>()))
    private fun userPreview(user: AppUser) = mapOf("user_id" to user.id, "username" to user.username, "bio_preview" to user.bio.orEmpty().take(80))
    private fun momentSummary(moment: Moment): Map<String, Any?> { val media = moment.visibleMediaItems; return mapOf("moment_id" to moment.id.orEmpty(), "created_at" to NovaJSON.iso(moment.timestamp), "content" to moment.content, "audience" to moment.audience.orEmpty(), "comment_count" to moment.commentCount, "reaction_kinds_count" to moment.reactions.keys.size, "total_reactions_count" to moment.reactions.values.sumOf(List<String>::size), "is_archived" to (moment.isArchived ?: false), "is_scheduled" to moment.isScheduled, "has_location" to (moment.locationCoordinate != null || moment.location?.isNotEmpty() == true), "location_name" to moment.location.orEmpty(), "media_count" to media.size, "primary_media_type" to media.firstOrNull()?.type?.raw.orEmpty(), "media_types" to media.map { it.type.raw }, "tagged_users_count" to (moment.taggedUsers?.size ?: 0), "has_hidden_layers" to moment.hasHiddenLayers) }
    private fun storySummary(story: Story, now: Date) = mapOf("story_id" to story.id.orEmpty(), "created_at" to NovaJSON.iso(story.timestamp), "expires_at" to NovaJSON.iso(story.expirationDate), "is_active" to (story.expirationDate > now), "audience" to story.audience.orEmpty(), "text" to story.text.orEmpty(), "media_type" to story.mediaItem.type.raw, "aspect_ratio" to story.aspectRatio.orEmpty(), "has_stickers" to !story.stickers.isNullOrEmpty(), "is_chain_story" to (story.chainId != null), "chain_title" to story.chainTitle.orEmpty())
    private fun errorObject(error: Throwable) = mapOf("error" to (error.message ?: "Unknown error"))
    private fun failureObject(error: Throwable) = mapOf("success" to false, "error" to (error.message ?: "Unknown error"))
    private fun listError(key: String, error: Throwable) = mapOf("error" to (error.message ?: "Unknown error"), "total_count" to 0, key to emptyList<Any>())
    private fun String?.cleanUsername() = this.orEmpty().trim().trimStart('@').trim()
}
