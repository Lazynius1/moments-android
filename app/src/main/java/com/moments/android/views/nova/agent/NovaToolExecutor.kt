package com.moments.android.views.nova.agent

import android.content.Context
import android.graphics.Bitmap
import com.moments.android.R
import com.moments.android.views.nova.ai.NovaAIService
import com.moments.android.views.nova.memory.NovaFactType
import com.moments.android.views.nova.memory.NovaMemory
import com.moments.android.views.nova.memory.NovaMemoryStore
import com.moments.android.views.nova.tools.NovaActivityTools
import com.moments.android.views.nova.tools.NovaMemoryTools
import com.moments.android.views.nova.tools.NovaProfileTools
import com.moments.android.views.nova.tools.NovaSocialTools

/** Executes the function calls issued by Nova, including the sensitive-action gate. */
class NovaToolExecutor(
    context: Context,
    private val userId: String,
    private val activityTools: NovaActivityTools = NovaActivityTools(),
    private val socialTools: NovaSocialTools = NovaSocialTools(context),
    private val profileTools: NovaProfileTools = NovaProfileTools(),
    private val memoryStore: NovaMemoryStore = NovaMemoryStore,
) {
    private val appContext = context.applicationContext
    private val memoryTools = NovaMemoryTools(memoryStore)
    private val executedSignatures = mutableSetOf<String>()

    var attachedImageForTurn: Bitmap? = null
    var onMemoryUpdated: ((NovaMemory) -> Unit)? = null
    var onMomentCreated: (() -> Unit)? = null
    var requestUserConfirmation: (suspend (NovaPendingAction) -> Boolean)? = null

    fun resetTurn() {
        executedSignatures.clear()
    }

    suspend fun execute(calls: List<NovaAIService.FunctionCall>): List<NovaAIService.FunctionResponse> = calls.map { call ->
        val signature = "${call.name}:${call.arguments}"
        val payload = if (!executedSignatures.add(signature)) {
            mapOf("error" to "Duplicate tool call skipped.")
        } else {
            dispatch(call.name, call.arguments)
        }
        NovaAIService.FunctionResponse(call.name, payload, call.id)
    }

    suspend fun executeCreateMoment(args: Map<String, Any?>, image: Bitmap): Map<String, Any?> {
        attachedImageForTurn = image
        val result = socialTools.createMoment(
            userId = userId,
            content = stringArg(args["content"]).orEmpty(),
            audienceRaw = stringArg(args["audience"]) ?: "everyone",
            targetUsername = stringArg(args["target_username"]),
            customListName = stringArg(args["custom_list_name"]),
            customListId = stringArg(args["custom_list_id"]),
            attachedImage = image,
        )
        if (result["success"] == true) onMomentCreated?.invoke()
        return result
    }

    private suspend fun dispatch(name: String, args: Map<String, Any?>): Map<String, Any?> {
        if (name in NovaToolRegistry.confirmationRequiredTools) {
            if (name == "create_moment" && attachedImageForTurn == null) return missingMediaObject()
            val action = NovaPendingAction.from(
                appContext,
                name,
                args,
                if (name == "create_moment") attachedImageForTurn else null,
            ) ?: return errorObject("invalid_action_args")
            if (requestUserConfirmation?.invoke(action) != true) {
                return mapOf("success" to false, "status" to "cancelled_by_user", "message" to "The user declined this action in the app.")
            }
        }

        return when (name) {
            "get_activity_summary" -> runTool("activity_summary_failed") { activityTools.activitySummary(userId) }
            "get_weekly_summary" -> runTool("weekly_summary_failed") { activityTools.weeklySummary(userId) }
            "get_profile_visits" -> runTool("profile_visits_failed") { activityTools.profileVisits(userId, intArg(args["limit"]) ?: 5) }
            "get_story_chain_info" -> runTool("story_chain_failed") { activityTools.storyChainInfo(userId, boolArg(args["include_viewers"]) ?: false) }
            "create_moment" -> createMomentFromTool(args)
            "list_audience_lists" -> socialTools.listAudienceLists(userId)
            "get_connection_suggestions" -> socialTools.connectionSuggestions(intArg(args["limit"]) ?: 5)
            "get_followers_summary" -> profileTools.followersSummary(userId, intArg(args["limit"]) ?: 5)
            "get_following_summary" -> profileTools.followingSummary(userId, intArg(args["limit"]) ?: 5)
            "get_my_profile_snapshot" -> profileTools.myProfileSnapshot(userId)
            "get_recent_moments_summary" -> profileTools.recentMomentsSummary(userId, intArg(args["limit"]) ?: 5)
            "get_recent_stories_summary" -> profileTools.recentStoriesSummary(userId, intArg(args["limit"]) ?: 5)
            "get_profile_and_content_overview" -> profileTools.profileAndContentOverview(userId, intArg(args["moment_limit"]) ?: 5, intArg(args["story_limit"]) ?: 5)
            "get_mutuals", "get_mutual_connections" -> profileTools.mutuals(userId, intArg(args["limit"]) ?: 5)
            "get_shared_interest_users" -> profileTools.sharedInterestUsers(userId, intArg(args["limit"]) ?: 5)
            "find_user_by_username" -> stringArg(args["username"])?.let { profileTools.findUser(it) } ?: errorObject("missing_username")
            "send_follow_request" -> stringArg(args["username"])?.let { profileTools.sendFollowRequest(userId, it) } ?: errorObject("missing_username")
            "get_profile_privacy_settings" -> profileTools.profilePrivacy(userId)
            "update_profile_privacy_settings" -> profileTools.updatePrivacy(
                userId = userId,
                isPrivate = boolArg(args["is_private"]),
                showMutuals = boolArg(args["show_mutuals"]) ?: boolArg(args["show_mutual_connections"]),
                showFollowing = boolArg(args["show_following"]),
                showFollowers = boolArg(args["show_followers"]),
            )
            "update_profile_bio" -> stringArg(args["bio"])?.let { profileTools.updateBio(userId, it) } ?: errorObject("missing_bio")
            "update_profile_website" -> stringArg(args["website"])?.let { profileTools.updateWebsite(userId, it) } ?: errorObject("missing_website")
            "update_active_hours" -> profileTools.updateActiveHours(userId, stringArg(args["start_hour"]), stringArg(args["end_hour"]), boolArg(args["clear"]) ?: false)
            "update_notification_preferences" -> boolDictionaryArgs(args).takeIf { it.isNotEmpty() }
                ?.let { profileTools.updateNotificationPreferences(userId, it) } ?: errorObject("missing_preferences")
            "get_user_profile_snapshot" -> profileTools.userProfileSnapshot(userId, stringArg(args["username"]), stringArg(args["user_id"]))
            "get_moment_details" -> stringArg(args["moment_id"])?.takeIf { it.isNotEmpty() }
                ?.let { profileTools.momentDetails(it, userId) } ?: errorObject("missing_moment_id")
            "get_echo_history_summary" -> profileTools.echoHistorySummary(userId, intArg(args["limit"]) ?: 5)
            "remember_fact" -> rememberFact(args)
            "update_user_preference" -> updatePreference(args)
            else -> errorObject("unknown_tool")
        }
    }

    private suspend fun createMomentFromTool(args: Map<String, Any?>): Map<String, Any?> {
        val image = attachedImageForTurn ?: return missingMediaObject()
        val result = socialTools.createMoment(
            userId, stringArg(args["content"]).orEmpty(), stringArg(args["audience"]) ?: "everyone",
            stringArg(args["target_username"]), stringArg(args["custom_list_name"]), stringArg(args["custom_list_id"]), image,
        )
        if (result["success"] == true) onMomentCreated?.invoke()
        return result
    }

    private suspend fun rememberFact(args: Map<String, Any?>): Map<String, Any?> {
        val content = stringArg(args["content"]) ?: return errorObject("missing_content")
        val result = memoryTools.rememberFact(userId, content, stringArg(args["type"])?.let(NovaFactType::fromRaw))
        refreshMemory()
        return result
    }

    private suspend fun updatePreference(args: Map<String, Any?>): Map<String, Any?> {
        val key = stringArg(args["key"]) ?: return errorObject("missing_key_or_value")
        val value = stringArg(args["value"]) ?: return errorObject("missing_key_or_value")
        val result = memoryTools.updatePreference(userId, key, value)
        refreshMemory()
        return result
    }

    private suspend fun refreshMemory() {
        memoryStore.loadMemory(userId)?.let { onMemoryUpdated?.invoke(it) }
    }

    private suspend fun runTool(error: String, block: suspend () -> Map<String, Any?>): Map<String, Any?> =
        runCatching { block() }.getOrElse { errorObject(error) }

    private fun missingMediaObject(): Map<String, Any?> = mapOf(
        "success" to false,
        "error" to "missing_media",
        "hint" to "Moments require a photo or video. Ask the user to attach media in the chat (+ button).",
    )

    private fun errorObject(code: String): Map<String, Any?> = mapOf("success" to false, "error" to code)
    private fun stringArg(value: Any?): String? = value as? String
    private fun intArg(value: Any?): Int? = when (value) { is Number -> value.toInt(); is String -> value.toIntOrNull(); else -> null }
    private fun boolArg(value: Any?): Boolean? = value as? Boolean
    private fun boolDictionaryArgs(args: Map<String, Any?>): Map<String, Boolean> = args.mapNotNull { (key, value) -> (value as? Boolean)?.let { key to it } }.toMap()

    companion object {
        const val maxStepsPerTurn = 15

        fun momentSuccessMessage(context: Context, responses: List<NovaAIService.FunctionResponse>): String? {
            val response = responses.firstOrNull { it.name == "create_moment" }?.payload ?: return null
            if (response["success"] != true) return null
            return (response["audience_label"] as? String)?.takeIf { it.isNotEmpty() }
                ?.let { context.getString(R.string.nova_moment_published, it) }
                ?: context.getString(R.string.nova_moment_published_generic)
        }
    }
}
