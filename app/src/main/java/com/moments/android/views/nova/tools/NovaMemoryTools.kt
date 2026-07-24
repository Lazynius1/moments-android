package com.moments.android.views.nova.tools

import com.moments.android.views.nova.memory.NovaFact
import com.moments.android.views.nova.memory.NovaFactType
import com.moments.android.views.nova.memory.NovaMemory
import com.moments.android.views.nova.memory.NovaMemoryStore

class NovaMemoryTools(private val store: NovaMemoryStore = NovaMemoryStore) {
    suspend fun rememberFact(userId: String, content: String, type: NovaFactType?): Map<String, Any?> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return mapOf("success" to false, "error" to "Empty fact.")
        val factType = type ?: NovaFactType.GENERAL
        val fact = NovaFact(content = trimmed, type = factType, importance = if (factType == NovaFactType.PREFERENCE) 5 else 3)
        return runCatching {
            val memory = (store.loadMemory(userId) ?: NovaMemory(userId = userId)).upsertingFacts(listOf(fact))
            store.saveMemory(memory)
            mapOf("success" to true, "fact_id" to fact.id)
        }.getOrElse { mapOf("success" to false, "error" to (it.message ?: "Unknown error")) }
    }

    suspend fun updatePreference(userId: String, key: String, value: String): Map<String, Any?> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return mapOf("success" to false, "error" to "Empty value.")
        val normalized = key.lowercase()
        return when {
            normalized.contains("name") -> rememberFact(userId, "Preferred name: $trimmed", NovaFactType.PREFERENCE)
            normalized.contains("pronoun") || normalized.contains("pronombre") -> rememberFact(userId, "Pronouns: $trimmed", NovaFactType.PREFERENCE)
            else -> rememberFact(userId, "$key: $trimmed", NovaFactType.PREFERENCE)
        }
    }
}
