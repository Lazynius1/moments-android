package com.moments.android.views.nova.agent

import com.moments.android.views.nova.ai.NovaPromptCatalog
import com.moments.android.views.nova.novacore.NovaLocaleContext
import com.moments.android.views.nova.memory.NovaMemory
import com.moments.android.views.nova.memory.NovaUserContext

/** Builds Nova's system context from durable memory and recent conversation summaries. */
object NovaContextAssembler {
    fun systemInstruction(
        username: String,
        memory: NovaMemory?,
        context: NovaUserContext?,
        internalHistorySummary: String? = null,
    ): String = buildList {
        add(NovaPromptCatalog.systemInstruction)
        add(NovaPromptCatalog.sessionContext(username, memory?.preferredName, NovaLocaleContext.appLocaleIdentifier))
        memoryFactsBlock(memory)?.let(::add)
        conversationSummariesBlock(context)?.let(::add)
        NovaPromptCatalog.internalHistoryContext(internalHistorySummary)?.let(::add)
    }.joinToString("\n\n")

    private fun memoryFactsBlock(memory: NovaMemory?): String? {
        val facts = memory?.facts?.takeIf { it.isNotEmpty() } ?: return null
        val lines = facts.sortedByDescending { it.relevanceScore }
            .take(10)
            .joinToString("\n") { "- [${it.type.rawValue}] ${it.content}" }
        return """
            Known facts about this user (already in context — do not call remember_fact or update_user_preference for duplicates):
            $lines
        """.trimIndent()
    }

    private fun conversationSummariesBlock(context: NovaUserContext?): String? {
        val summaries = context?.conversationSummaries?.takeIf { it.isNotEmpty() } ?: return null
        val lines = summaries.sortedByDescending { it.createdAt }
            .take(5)
            .joinToString("\n") { "- ${it.summary}" }
        return """
            Recent summaries from past Nova chats:
            $lines
        """.trimIndent()
    }
}
