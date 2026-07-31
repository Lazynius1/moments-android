package com.moments.android.views.nova.tools

import com.moments.android.views.nova.memory.NovaFact
import com.moments.android.views.nova.memory.NovaFactType
import com.moments.android.views.nova.memory.NovaMemory
import com.moments.android.views.nova.memory.NovaMemoryStore

/** Port de `Views/Nova/Tools/NovaMemoryTools.swift`. */
class NovaMemoryTools(private val store: NovaMemoryStore = NovaMemoryStore) {
    suspend fun rememberFact(userId: String, content: String, type: NovaFactType?): Map<String, Any?> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return mapOf("success" to false, "error" to "Empty fact.")

        val factType = type ?: NovaFactType.GENERAL
        val fact = NovaFact(
            content = trimmed,
            type = factType,
            importance = if (factType == NovaFactType.PREFERENCE) 5 else 3,
        )

        return runCatching {
            val memory = (store.loadMemory(userId) ?: NovaMemory(userId = userId)).upsertingFacts(listOf(fact))
            store.saveMemory(memory)
            mapOf("success" to true, "fact_id" to fact.id)
        }.getOrElse {
            mapOf("success" to false, "error" to (it.message ?: "Unknown error"))
        }
    }

    suspend fun updatePreference(userId: String, key: String, value: String): Map<String, Any?> {
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) return mapOf("success" to false, "error" to "Empty value.")

        val normalizedKey = key.lowercase()
        return when {
            normalizedKey.contains("name") ->
                rememberFact(userId, "Preferred name: $trimmedValue", NovaFactType.PREFERENCE)
            normalizedKey.contains("pronoun") || normalizedKey.contains("pronombre") ->
                rememberFact(userId, "Pronouns: $trimmedValue", NovaFactType.PREFERENCE)
            else ->
                rememberFact(userId, "$key: $trimmedValue", NovaFactType.PREFERENCE)
        }
    }
}
