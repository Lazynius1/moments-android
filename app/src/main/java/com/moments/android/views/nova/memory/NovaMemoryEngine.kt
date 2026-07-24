package com.moments.android.views.nova.memory

import android.util.Log
import com.google.firebase.ai.type.Schema
import com.moments.android.services.nova.NovaEmbeddingService
import com.moments.android.views.nova.ai.NovaAIService
import com.moments.android.views.nova.ai.NovaPromptCatalog
import com.moments.android.views.nova.novacore.NovaChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Post-conversation memory: extracts durable facts and a rolling conversation summary. */
object NovaMemoryEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightKeys = mutableSetOf<String>()
    private val inFlightMutex = Mutex()

    fun scheduleConversationFinalize(userId: String, conversationId: String?, messages: List<NovaChatMessage>) {
        scope.launch { finalizeConversation(userId, conversationId, messages) }
    }

    suspend fun finalizeConversation(userId: String, conversationId: String?, messages: List<NovaChatMessage>) {
        val meaningful = messages.filter { !it.isSystem && it.text.trim().isNotEmpty() }
        if (meaningful.none { it.isUser } || meaningful.none { !it.isUser }) return

        val key = "${conversationId ?: "draft"}-${meaningful.size}-${meaningful.last().id}"
        if (!inFlightMutex.withLock { inFlightKeys.add(key) }) return
        try {
            val memory = NovaMemoryStore.loadMemory(userId) ?: NovaMemory(userId = userId)
            val context = NovaContextStore.loadContext(userId)
            val existingFacts = memory.facts
            val transcript = meaningful.joinToString("\n") { "${if (it.isUser) "User" else "Nova"}: ${it.text.take(800)}" }
            val existingList = existingFacts.joinToString("\n") { "- [${it.type.rawValue}] ${it.content}" }
            val prompt = """
                ${NovaPromptCatalog.conversationFinalizePrompt}

                existing_facts:
                ${if (existingList.isEmpty()) "none" else existingList}

                transcript:
                ${transcript.take(6000)}
            """.trimIndent()

            val schema = Schema.obj(
                mapOf(
                    "facts_to_add" to Schema.array(Schema.obj(mapOf(
                        "content" to Schema.string(),
                        "type" to Schema.string("preference|personal|professional|interest|general"),
                    ))),
                    "conversation_summary" to Schema.string(),
                ),
            )
            val payload = JSONObject(NovaAIService.generateJson(prompt, schema))
            val extractedFacts = payload.optJSONArray("facts_to_add")?.let { facts ->
                List(facts.length()) { index -> facts.optJSONObject(index) }
                    .mapNotNull { item ->
                        val content = item?.optString("content")?.trim().orEmpty()
                        if (content.length < 3) null
                        else NovaFact(content = content, type = NovaFactType.fromRaw(item.optString("type")) ?: NovaFactType.GENERAL)
                    }
            }.orEmpty()
            val newFacts = sanitizeExtractedFacts(extractedFacts, existingFacts, transcript)
            if (newFacts.isNotEmpty()) NovaMemoryStore.saveMemory(memory.upsertingFacts(newFacts))

            val summary = payload.optString("conversation_summary").trim()
            if (summary.length >= 20) {
                NovaContextStore.saveContext(context.addingSummary(NovaConversationSummary(conversationId = conversationId, summary = summary)))
            }
            NovaMemoryStore.notifyMemoryUpdated(userId)
        } catch (error: Exception) {
            Log.e(TAG, "finalizeConversation failed", error)
        } finally {
            inFlightMutex.withLock { inFlightKeys.remove(key) }
        }
    }

    private fun sanitizeExtractedFacts(facts: List<NovaFact>, existingFacts: List<NovaFact>, transcript: String): List<NovaFact> {
        val existing = existingFacts.mapTo(mutableSetOf()) { it.normalizedContent }
        val seen = mutableSetOf<String>()
        return facts.mapNotNull { fact ->
            val normalized = normalizedDurableContent(fact.content.trim(), fact.type, transcript) ?: return@mapNotNull null
            val key = normalized.lowercase().trim()
            if (key in existing || !seen.add(key)) return@mapNotNull null
            val candidate = NovaFact(content = normalized, type = fact.type)
            if (NovaEmbeddingService.isNearDuplicate(candidate, existingFacts)) return@mapNotNull null
            val type = refinedFactType(normalized, fact.type)
            NovaFact(content = normalized, type = type, importance = inferredImportance(normalized, type))
        }
    }

    private fun normalizedDurableContent(content: String, type: NovaFactType, transcript: String): String? {
        val trimmed = content.replace("\n", " ").replace("  ", " ").trim()
        if (trimmed.length !in 3..180) return null
        val lower = trimmed.lowercase()
        val transientFragments = listOf(
            "today", "tomorrow", "right now", "this morning", "this afternoon", "this evening",
            "hoy", "mañana", "ahora mismo", "esta mañana", "esta tarde", "esta noche",
            "failed", "cancelled", "canceled", "publish attempt", "intent to post", "draft caption",
            "acción fallida", "falló", "cancelado", "cancelada", "intento de publicar",
        )
        if (transientFragments.any(lower::contains) || lower.startsWith("user asked") || lower.startsWith("the user asked") || lower.startsWith("nova:") || '?' in trimmed) return null
        val sensitivePatterns = listOf(
            Regex("\\b\\d{3}[-.\\s]?\\d{2,4}[-.\\s]?\\d{3,4}\\b"),
            Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"),
            Regex("\\b\\d{1,4}\\s+\\w+\\s+(street|st|avenue|ave|road|rd|calle|plaza|paseo)\\b"),
        )
        if (sensitivePatterns.any { it.containsMatchIn(lower) }) return null
        if (lower.startsWith("preferred name:")) {
            return trimmed.removePrefixIgnoreCase("preferred name:").trim().takeIf(String::isNotEmpty)?.let { "Preferred name: $it" }
        }
        listOf("pronouns:", "my pronouns are ", "pronombres:", "mis pronombres son ").firstOrNull(lower::startsWith)?.let { prefix ->
            return trimmed.drop(prefix.length).trim().takeIf(String::isNotEmpty)?.let { "Pronouns: $it" }
        }
        return trimmed.takeUnless { type == NovaFactType.GENERAL && looksLikeWeakGeneralFact(lower) }
    }

    private fun refinedFactType(content: String, originalType: NovaFactType): NovaFactType {
        val lower = content.lowercase()
        return when {
            lower.startsWith("preferred name:") || lower.startsWith("pronouns:") || listOf("privacy", "profile", "posting", "stories", "moments").any(lower::contains) -> NovaFactType.PREFERENCE
            listOf("goal", "studying", "working", "career", "exam").any(lower::contains) -> NovaFactType.PROFESSIONAL
            listOf("partner", "girlfriend", "boyfriend", "family", "friend").any(lower::contains) -> NovaFactType.PERSONAL
            else -> originalType
        }
    }

    private fun inferredImportance(content: String, type: NovaFactType): Int {
        val lower = content.lowercase()
        return when {
            lower.startsWith("preferred name:") || lower.startsWith("pronouns:") -> 5
            listOf("privacy", "profile", "posting preference", "moments").any(lower::contains) -> 5
            listOf("goal", "important", "always", "never").any(lower::contains) -> 4
            else -> maxOf(3, type.priority)
        }
    }

    private fun looksLikeWeakGeneralFact(lower: String) = listOf(
        "likes ", "wants ", "asked about ", "talked about ", "mentioned ", "is feeling ", "felt ",
        "le gusta ", "quiere ", "preguntó por ", "habló de ", "mencionó ", "se siente ",
    ).any(lower::startsWith)

    private fun String.removePrefixIgnoreCase(prefix: String): String = if (startsWith(prefix, ignoreCase = true)) drop(prefix.length) else this

    private const val TAG = "NovaMemoryEngine"
}
