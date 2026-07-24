package com.moments.android.views.nova.tools

import android.content.Context
import com.moments.android.R
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomListDetails
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchUserByUsername
import com.moments.android.views.creator.audienceselector.ContentAudience

sealed interface NovaMomentAudience {
    val contentAudience: ContentAudience
    data object Everyone : NovaMomentAudience { override val contentAudience = ContentAudience.EVERYONE }
    data object Mutuals : NovaMomentAudience { override val contentAudience = ContentAudience.MUTUALS }
    data object BestFriends : NovaMomentAudience { override val contentAudience = ContentAudience.BEST_FRIENDS }
    data object OnlyMe : NovaMomentAudience { override val contentAudience = ContentAudience.ONLY_ME }
    data class Custom(val userIds: List<String>, val label: String) : NovaMomentAudience { override val contentAudience = ContentAudience.CUSTOM }
    data class CustomList(val listId: String, val listName: String) : NovaMomentAudience { override val contentAudience = ContentAudience.CUSTOM_LIST }
    val customViewers get() = (this as? Custom)?.userIds
    val customListId get() = (this as? CustomList)?.listId
}

sealed class NovaMomentAudienceError(val code: String) : Exception(code) {
    data object MissingTargetUsername : NovaMomentAudienceError("missing_target_username")
    data object MissingCustomListName : NovaMomentAudienceError("missing_custom_list_name")
    data object UserNotFound : NovaMomentAudienceError("user_not_found")
    data class ListNotFound(val available: List<String>) : NovaMomentAudienceError(if (available.isEmpty()) "list_not_found" else "list_not_found:available=${available.joinToString(", ")}")
    data object NoCustomLists : NovaMomentAudienceError("no_custom_lists")
    data object ListLookupFailed : NovaMomentAudienceError("list_lookup_failed")
    data object UnknownAudience : NovaMomentAudienceError("unknown_audience")
}

object NovaMomentAudienceResolver {
    fun normalizeAudienceRaw(raw: String) = when (raw.trim().lowercase()) { "best_friends", "bestfriends" -> "bestfriends"; "only_me", "onlyme" -> "onlyme"; "custom_list", "customlist" -> "customlist"; else -> raw.trim().lowercase() }
    fun audienceSummary(context: Context, audienceRaw: String, targetUsername: String?, customListName: String?): String = when (normalizeAudienceRaw(audienceRaw)) {
        "", "everyone" -> context.getString(R.string.audience_everyone)
        "mutuals" -> context.getString(R.string.audience_mutuals)
        "bestfriends" -> context.getString(R.string.audience_best_friends)
        "onlyme" -> context.getString(R.string.audience_only_me)
        "custom" -> targetUsername?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" } ?: context.getString(R.string.audience_custom)
        "customlist" -> customListName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.audience_custom_list)
        else -> audienceRaw.ifEmpty { context.getString(R.string.audience_everyone) }
    }
    suspend fun resolve(userId: String, audienceRaw: String, targetUsername: String?, customListName: String?, customListId: String?, firestoreService: FirestoreService): Result<NovaMomentAudience> = when (normalizeAudienceRaw(audienceRaw)) {
        "", "everyone" -> Result.success(NovaMomentAudience.Everyone)
        "mutuals" -> Result.success(NovaMomentAudience.Mutuals)
        "bestfriends" -> Result.success(NovaMomentAudience.BestFriends)
        "onlyme" -> Result.success(NovaMomentAudience.OnlyMe)
        "custom" -> resolveUsername(targetUsername?.trim().orEmpty(), firestoreService)
        "customlist" -> if (!customListId.isNullOrBlank()) resolveListId(customListId, userId, firestoreService) else resolveListName(customListName?.trim().orEmpty(), userId, firestoreService)
        else -> Result.failure(NovaMomentAudienceError.UnknownAudience)
    }
    private suspend fun resolveUsername(username: String, service: FirestoreService): Result<NovaMomentAudience> { if (username.isEmpty()) return Result.failure(NovaMomentAudienceError.MissingTargetUsername); return runCatching { service.fetchUserByUsername(username.removePrefix("@")).let { NovaMomentAudience.Custom(listOf(it.id), "@${it.username}") } }.recoverCatching { throw NovaMomentAudienceError.UserNotFound }
    }
    private suspend fun resolveListId(id: String, ownerId: String, service: FirestoreService): Result<NovaMomentAudience> = runCatching { service.fetchCustomListDetails(id, ownerId).let { list -> NovaMomentAudience.CustomList(list.id ?: throw NovaMomentAudienceError.ListNotFound(emptyList()), list.name) } }.recoverCatching { throw NovaMomentAudienceError.ListLookupFailed }
    private suspend fun resolveListName(name: String, ownerId: String, service: FirestoreService): Result<NovaMomentAudience> { if (name.isEmpty()) return Result.failure(NovaMomentAudienceError.MissingCustomListName); return runCatching { val lists = service.fetchCustomLists(ownerId); val needle = name.lowercase(); val list = lists.firstOrNull { it.name.lowercase() == needle } ?: lists.firstOrNull { it.name.lowercase().contains(needle) || needle.contains(it.name.lowercase()) } ?: throw if (lists.isEmpty()) NovaMomentAudienceError.NoCustomLists else NovaMomentAudienceError.ListNotFound(lists.map { it.name }); NovaMomentAudience.CustomList(list.id ?: throw NovaMomentAudienceError.ListNotFound(emptyList()), list.name) }.recoverCatching { throw if (it is NovaMomentAudienceError) it else NovaMomentAudienceError.ListLookupFailed } }
}
