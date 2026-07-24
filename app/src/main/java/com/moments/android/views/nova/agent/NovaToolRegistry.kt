package com.moments.android.views.nova.agent

import com.moments.android.views.nova.ai.NovaAIService

/** Complete tool contract supplied to Nova's Firebase AI model. */
object NovaToolRegistry {
    val allDeclarations: List<NovaAIService.FunctionDeclaration>
        get() = activityTools + socialTools + profileTools + memoryTools

    val toolSet: List<NovaAIService.FunctionDeclaration>
        get() = allDeclarations

    private fun declaration(
        name: String,
        description: String,
        parameters: Map<String, NovaAIService.FunctionParameter> = emptyMap(),
        optional: Set<String> = emptySet(),
    ) = NovaAIService.FunctionDeclaration(name, description.trimIndent(), parameters, optional)

    private fun string(description: String) = NovaAIService.FunctionParameter(NovaAIService.ParameterType.STRING, description)
    private fun integer(description: String) = NovaAIService.FunctionParameter(NovaAIService.ParameterType.INTEGER, description)
    private fun bool(description: String) = NovaAIService.FunctionParameter(NovaAIService.ParameterType.BOOLEAN, description)

    private val activityTools get() = listOf(
        declaration("get_activity_summary", "Recent activity overview: profile visits and latest story chain."),
        declaration("get_weekly_summary", "Week-over-week comparison of moments, engagement, visits, and story views."),
        declaration("get_profile_visits", "Recent profile visits with usernames and timestamps.", mapOf("limit" to integer("Max visits to return (default 5, max 10).")), setOf("limit")),
        declaration("get_story_chain_info", "Latest story chain details and optional viewer summary.", mapOf("include_viewers" to bool("Include recent viewers if true.")), setOf("include_viewers")),
    )

    private val socialTools get() = listOf(
        declaration(
            "create_moment", """
                Publish a moment with the photo attached in this chat message. After success, the upload starts automatically.
                Do not tell the user to confirm in chat — in-app approval is handled separately.
                Moments cannot be text-only — media must be attached via the chat (+ button) before calling.
                audience: everyone | mutuals | bestFriends | onlyMe | custom | customList.
                Call list_audience_lists first if the user refers to a list by vague name.
            """, mapOf(
                "content" to string("Optional caption. Include @username (no space) to mention people; mentions are resolved automatically for linking/notifications and do not change audience."),
                "audience" to string("Exactly: everyone | mutuals | bestFriends | onlyMe | custom | customList"),
                "target_username" to string("For audience=custom: username without @."),
                "custom_list_name" to string("For audience=customList: list name."),
                "custom_list_id" to string("Optional list id if already known."),
            ), setOf("content", "target_username", "custom_list_name", "custom_list_id"),
        ),
        declaration("list_audience_lists", "List the user's custom audience lists (id, name, member count) for moment publishing."),
        declaration("get_connection_suggestions", "Suggested users to connect with based on interests and mutuals.", mapOf("limit" to integer("Max suggestions (default 5, max 10).")), setOf("limit")),
    )

    private val profileTools get() = listOf(
        declaration("get_my_profile_snapshot", "Return a neutral JSON snapshot of the current user's profile and settings."),
        limited("get_followers_summary", "Return a neutral JSON summary of the current user's recent followers.", "Max users to return (default 5, max 10)."),
        limited("get_following_summary", "Return a neutral JSON summary of the current user's following list.", "Max users to return (default 5, max 10)."),
        limited("get_mutuals", "Return mutual followers for the current user in neutral JSON.", "Max users to return (default 5, max 10)."),
        limited("get_shared_interest_users", "Return users who share interests with the current user in neutral JSON.", "Max users to return (default 5, max 10)."),
        limited("get_recent_moments_summary", "Return a neutral JSON summary of the current user's most recent moments for analysis and coaching.", "Max moments to return (default 5, max 10)."),
        limited("get_recent_stories_summary", "Return a neutral JSON summary of the current user's recent stories, including active and archived counts.", "Max stories to return (default 5, max 10)."),
        declaration("get_profile_and_content_overview", "Return a combined neutral JSON overview of the current user's profile, recent moments, and recent stories.", mapOf("moment_limit" to integer("Max moments to include (default 5, max 10)."), "story_limit" to integer("Max stories to include (default 5, max 10).")), setOf("moment_limit", "story_limit")),
        declaration("find_user_by_username", "Look up a user profile by username and return a neutral JSON snapshot.", mapOf("username" to string("Username to search, with or without @."))),
        declaration("send_follow_request", "Send a follow request to a user by username. Requires user confirmation.", mapOf("username" to string("Target username, with or without @."))),
        declaration("get_profile_privacy_settings", "Return the current user's privacy settings in neutral JSON."),
        declaration("update_profile_privacy_settings", "Update the current user's privacy settings. Requires user confirmation. Only include keys the user actually wants to change.", mapOf("is_private" to bool("Whether the account should be private."), "show_mutuals" to bool("Whether mutuals should be visible."), "show_following" to bool("Whether following should be visible."), "show_followers" to bool("Whether followers should be visible.")), setOf("is_private", "show_mutuals", "show_following", "show_followers")),
        declaration("update_profile_bio", "Update the current user's profile bio. Requires user confirmation. Use an empty string only if the user explicitly wants to clear it.", mapOf("bio" to string("New bio text."))),
        declaration("update_profile_website", "Update the current user's profile website. Requires user confirmation. Use an empty string only if the user explicitly wants to clear it.", mapOf("website" to string("New website URL."))),
        declaration("update_active_hours", "Update or clear the current user's active hours. Requires user confirmation.", mapOf("start_hour" to string("Start hour in HH:mm format."), "end_hour" to string("End hour in HH:mm format."), "clear" to bool("Set true only when explicitly clearing active hours.")), setOf("start_hour", "end_hour", "clear")),
        declaration("update_notification_preferences", "Update one or more notification preferences for the current user. Requires user confirmation.", notificationParameters, notificationParameters.keys),
        declaration("get_user_profile_snapshot", "Return a neutral JSON profile snapshot. Defaults to the current user if no identifier is supplied.", mapOf("username" to string("Optional username, with or without @."), "user_id" to string("Optional target user id.")), setOf("username", "user_id")),
        declaration("get_moment_details", "Return neutral JSON details for a specific moment id if available to the current user.", mapOf("moment_id" to string("Concrete moment id."))),
        limited("get_echo_history_summary", "Return a neutral JSON summary of recent Echo history for the current user.", "Max echoes to return (default 5, max 10)."),
    )

    private fun limited(name: String, description: String, limitDescription: String) = declaration(name, description, mapOf("limit" to integer(limitDescription)), setOf("limit"))

    private val notificationParameters get() = mapOf(
        "like" to bool("Enable or disable like notifications."),
        "reaction" to bool("Enable or disable reaction notifications."),
        "comment" to bool("Enable or disable comment notifications."),
        "mention" to bool("Enable or disable mention notifications."),
        "newFollower" to bool("Enable or disable new follower notifications."),
        "followRequest" to bool("Enable or disable follow request notifications."),
        "requestAccepted" to bool("Enable or disable request accepted notifications."),
        "mutualConnection" to bool("Enable or disable mutual connection notifications."),
        "storyReaction" to bool("Enable or disable story reaction notifications."),
        "message" to bool("Enable or disable message notifications."),
        "photoTag" to bool("Enable or disable photo tag notifications."),
        "echoSuggestion" to bool("Enable or disable Echo suggestion notifications."),
        "dataExportReady" to bool("Enable or disable data export ready notifications."),
        "storyChainContinued" to bool("Enable or disable story chain notifications."),
        "mediaModeration" to bool("Enable or disable moderation notifications."),
        "gentleReminders" to bool("Enable or disable gentle reminders."),
        "commentsMutualsOnly" to bool("Enable or disable comments from mutuals only."),
        "muteOldPostReactions" to bool("Enable or disable old post reaction notifications."),
    )

    private val memoryTools get() = listOf(
        declaration("remember_fact", "Persist a durable fact the user explicitly asked to remember. Requires user confirmation. Do NOT use for names (use update_user_preference) or info already in context.", mapOf("content" to string("Fact in the user's language."), "type" to string("One of: preference, personal, professional, interest, general.")), setOf("type")),
        declaration("update_user_preference", "Update a user preference. Requires user confirmation. Use key preferred_name for names.", mapOf("key" to string("Preference key, e.g. preferred_name or tone."), "value" to string("Preference value."))),
    )

    val confirmationRequiredTools = setOf(
        "create_moment", "remember_fact", "update_user_preference", "send_follow_request", "update_profile_privacy_settings", "update_profile_bio", "update_profile_website", "update_active_hours", "update_notification_preferences",
    )
}
