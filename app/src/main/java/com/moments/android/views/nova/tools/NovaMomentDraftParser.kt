package com.moments.android.views.nova.tools

import com.google.firebase.ai.type.Schema
import com.moments.android.views.nova.ai.NovaAIService
import com.moments.android.views.nova.ai.NovaPromptCatalog
import org.json.JSONObject

object NovaMomentDraftParser {
    data class Draft(val content: String, val audience: String, val targetUsername: String?, val customListName: String?)

    suspend fun parse(userText: String): Draft? {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return null
        val prompt = "${NovaPromptCatalog.momentDraftPrompt}\n\nuser_message:\n$trimmed"
        val schema = Schema.obj(mapOf(
            "should_publish" to Schema.boolean(),
            "content" to Schema.string("Caption for the moment. Empty string if none."),
            "audience" to Schema.string("everyone | mutuals | bestFriends | onlyMe | custom | customList"),
            "target_username" to Schema.string("Only when audience is custom."),
            "custom_list_name" to Schema.string("Only when audience is customList."),
        ))
        val payload = JSONObject(NovaAIService.generateJson(prompt, schema))
        if (!payload.optBoolean("should_publish", false)) return null
        return Draft(payload.optString("content"), payload.optString("audience", "everyone"), payload.optString("target_username").takeIf { it.isNotEmpty() }, payload.optString("custom_list_name").takeIf { it.isNotEmpty() })
    }
}
