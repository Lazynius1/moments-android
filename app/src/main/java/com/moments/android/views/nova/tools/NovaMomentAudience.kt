package com.moments.android.views.nova.tools

import android.content.Context
import com.moments.android.R
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomListDetails
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchUserByUsername
import com.moments.android.views.creator.audienceselector.CaptionAudienceSetting
import com.moments.android.views.creator.audienceselector.ContentAudience

/**
 * Port de `Views/Nova/Tools/NovaMomentAudience.swift`.
 * Audiencia tipada + resolver Firestore para create_moment.
 */
sealed interface NovaMomentAudience {
    val contentAudience: ContentAudience

    /** ≡ `CaptionAndDetailsView.AudienceSetting` — customList también es `.custom`. */
    val audienceSetting: CaptionAudienceSetting

    val customViewers: List<String>?
        get() = (this as? Custom)?.userIds

    val customListId: String?
        get() = (this as? CustomList)?.listId

    fun displayLabel(context: Context): String

    data object Everyone : NovaMomentAudience {
        override val contentAudience = ContentAudience.EVERYONE
        override val audienceSetting = CaptionAudienceSetting.EVERYONE
        override fun displayLabel(context: Context) = context.getString(R.string.audience_everyone)
    }

    data object Mutuals : NovaMomentAudience {
        override val contentAudience = ContentAudience.MUTUALS
        override val audienceSetting = CaptionAudienceSetting.MUTUALS
        override fun displayLabel(context: Context) = context.getString(R.string.audience_mutuals)
    }

    data object BestFriends : NovaMomentAudience {
        override val contentAudience = ContentAudience.BEST_FRIENDS
        override val audienceSetting = CaptionAudienceSetting.BEST_FRIENDS
        override fun displayLabel(context: Context) = context.getString(R.string.audience_best_friends)
    }

    data object OnlyMe : NovaMomentAudience {
        override val contentAudience = ContentAudience.ONLY_ME
        override val audienceSetting = CaptionAudienceSetting.ONLY_ME
        override fun displayLabel(context: Context) = context.getString(R.string.audience_only_me)
    }

    data class Custom(val userIds: List<String>, val label: String) : NovaMomentAudience {
        override val contentAudience = ContentAudience.CUSTOM
        override val audienceSetting = CaptionAudienceSetting.CUSTOM
        override fun displayLabel(context: Context) = label
    }

    data class CustomList(val listId: String, val listName: String) : NovaMomentAudience {
        override val contentAudience = ContentAudience.CUSTOM_LIST
        override val audienceSetting = CaptionAudienceSetting.CUSTOM
        override fun displayLabel(context: Context) = listName
    }

    /** ≡ iOS `convertAudienceSettingToString` para el upload service. */
    fun audienceSettingRaw(): String = when (audienceSetting) {
        CaptionAudienceSetting.EVERYONE -> "everyone"
        CaptionAudienceSetting.MUTUALS -> "mutuals"
        CaptionAudienceSetting.BEST_FRIENDS -> "bestFriends"
        CaptionAudienceSetting.CUSTOM -> "custom"
        CaptionAudienceSetting.ONLY_ME -> "onlyMe"
    }
}

sealed class NovaMomentAudienceError(val code: String) : Exception(code) {
    data object MissingTargetUsername : NovaMomentAudienceError("missing_target_username")
    data object MissingCustomListName : NovaMomentAudienceError("missing_custom_list_name")
    data object UserNotFound : NovaMomentAudienceError("user_not_found")
    data class ListNotFound(val available: List<String>) : NovaMomentAudienceError(
        if (available.isEmpty()) {
            "list_not_found"
        } else {
            "list_not_found:available=${available.joinToString(", ")}"
        },
    )
    data object NoCustomLists : NovaMomentAudienceError("no_custom_lists")
    data object ListLookupFailed : NovaMomentAudienceError("list_lookup_failed")
    data object UnknownAudience : NovaMomentAudienceError("unknown_audience")
}

object NovaMomentAudienceResolver {
    /** Canonical tool values only. The LLM maps user language → these before calling tools. */
    fun normalizeAudienceRaw(raw: String): String {
        val key = raw.trim().lowercase()
        return when (key) {
            "best_friends", "bestfriends" -> "bestfriends"
            "only_me", "onlyme" -> "onlyme"
            "custom_list", "customlist" -> "customlist"
            else -> key
        }
    }

    fun audienceSummary(
        context: Context,
        audienceRaw: String,
        targetUsername: String?,
        customListName: String?,
    ): String = when (normalizeAudienceRaw(audienceRaw)) {
        "everyone", "" -> context.getString(R.string.audience_everyone)
        "mutuals" -> context.getString(R.string.audience_mutuals)
        "bestfriends" -> context.getString(R.string.audience_best_friends)
        "onlyme" -> context.getString(R.string.audience_only_me)
        "custom" -> {
            val username = targetUsername?.takeIf { it.isNotBlank() }
            when {
                username == null -> context.getString(R.string.audience_custom)
                username.startsWith("@") -> username
                else -> "@$username"
            }
        }
        "customlist" -> customListName?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.audience_custom_list)
        else -> audienceRaw.ifEmpty { context.getString(R.string.audience_everyone) }
    }

    suspend fun resolve(
        userId: String,
        audienceRaw: String,
        targetUsername: String?,
        customListName: String?,
        customListId: String?,
        firestoreService: FirestoreService,
    ): Result<NovaMomentAudience> = when (normalizeAudienceRaw(audienceRaw)) {
        "everyone", "" -> Result.success(NovaMomentAudience.Everyone)
        "mutuals" -> Result.success(NovaMomentAudience.Mutuals)
        "bestfriends" -> Result.success(NovaMomentAudience.BestFriends)
        "onlyme" -> Result.success(NovaMomentAudience.OnlyMe)
        "custom" -> {
            val username = targetUsername?.trim().orEmpty()
            if (username.isEmpty()) {
                Result.failure(NovaMomentAudienceError.MissingTargetUsername)
            } else {
                resolveUsername(username, firestoreService)
            }
        }
        "customlist" -> {
            if (!customListId.isNullOrBlank()) {
                resolveListId(customListId, ownerId = userId, firestoreService)
            } else {
                val listName = customListName?.trim().orEmpty()
                if (listName.isEmpty()) {
                    Result.failure(NovaMomentAudienceError.MissingCustomListName)
                } else {
                    resolveListName(listName, ownerId = userId, firestoreService)
                }
            }
        }
        else -> Result.failure(NovaMomentAudienceError.UnknownAudience)
    }

    private suspend fun resolveUsername(
        username: String,
        firestoreService: FirestoreService,
    ): Result<NovaMomentAudience> {
        val cleaned = username.removePrefix("@")
        return runCatching {
            val user = firestoreService.fetchUserByUsername(cleaned)
            NovaMomentAudience.Custom(userIds = listOf(user.id), label = "@${user.username}")
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(NovaMomentAudienceError.UserNotFound) },
        )
    }

    private suspend fun resolveListId(
        listId: String,
        ownerId: String,
        firestoreService: FirestoreService,
    ): Result<NovaMomentAudience> =
        runCatching {
            val list = firestoreService.fetchCustomListDetails(listId, ownerId)
            val id = list.id ?: return Result.failure(NovaMomentAudienceError.ListNotFound(emptyList()))
            NovaMomentAudience.CustomList(listId = id, listName = list.name)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (error is NovaMomentAudienceError) Result.failure(error)
                else Result.failure(NovaMomentAudienceError.ListLookupFailed)
            },
        )

    private suspend fun resolveListName(
        listName: String,
        ownerId: String,
        firestoreService: FirestoreService,
    ): Result<NovaMomentAudience> =
        runCatching {
            val lists = firestoreService.fetchCustomLists(ownerId)
            val needle = listName.lowercase()
            val exact = lists.firstOrNull { it.name.lowercase() == needle }
            if (exact != null) {
                val id = exact.id ?: return@runCatching Result.failure(
                    NovaMomentAudienceError.ListNotFound(emptyList()),
                )
                return@runCatching Result.success(
                    NovaMomentAudience.CustomList(listId = id, listName = exact.name),
                )
            }
            val partial = lists.firstOrNull {
                val name = it.name.lowercase()
                name.contains(needle) || needle.contains(name)
            }
            if (partial != null) {
                val id = partial.id ?: return@runCatching Result.failure(
                    NovaMomentAudienceError.ListNotFound(emptyList()),
                )
                return@runCatching Result.success(
                    NovaMomentAudience.CustomList(listId = id, listName = partial.name),
                )
            }
            val available = lists.map { it.name }
            Result.failure(
                if (available.isEmpty()) NovaMomentAudienceError.NoCustomLists
                else NovaMomentAudienceError.ListNotFound(available),
            )
        }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(NovaMomentAudienceError.ListLookupFailed) },
        )
}
