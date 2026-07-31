package com.moments.android.views.nova.memory

import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID

/**
 * Port de `Views/Nova/Memory/NovaMemoryModels.swift`.
 * Facts tipados + memoria durable con upsert/compactación y preferred name.
 */
enum class NovaFactType(val rawValue: String, val priority: Int, val emoji: String) {
    PREFERENCE("preference", 5, "⚙️"),
    PERSONAL("personal", 4, "👤"),
    PROFESSIONAL("professional", 3, "💼"),
    INTEREST("interest", 2, "❤️"),
    GENERAL("general", 1, "💭"),
    ;

    companion object {
        fun fromRaw(value: String?): NovaFactType? =
            entries.firstOrNull { it.rawValue == value }
    }
}

data class NovaFact(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: NovaFactType,
    val timestamp: Date = Date(),
    val importance: Int = 3,
    val lastVerified: Date = Date(),
    val embedding: List<Double>? = null,
) {
    /** ≡ iOS `max(1, min(5, importance))` en el init. */
    val clampedImportance: Int
        get() = importance.coerceIn(1, 5)

    val relevanceScore: Int
        get() = type.priority * 10 + clampedImportance

    val normalizedContent: String
        get() = content.lowercase().trim()

    fun toFirestoreData(): Map<String, Any?> = buildMap {
        put("id", id)
        put("content", content)
        put("type", type.rawValue)
        put("timestamp", Timestamp(timestamp))
        put("importance", clampedImportance)
        put("lastVerified", Timestamp(lastVerified))
        put("lastProbedAt", null)
        embedding?.let { put("embedding", it) }
    }

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaFact? {
            val id = data["id"] as? String ?: return null
            val content = data["content"] as? String ?: return null
            val type = NovaFactType.fromRaw(data["type"] as? String) ?: return null
            val timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: return null
            return NovaFact(
                id = id,
                content = content,
                type = type,
                timestamp = timestamp,
                importance = ((data["importance"] as? Number)?.toInt() ?: 3).coerceIn(1, 5),
                lastVerified = (data["lastVerified"] as? Timestamp)?.toDate() ?: timestamp,
                embedding = (data["embedding"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() },
            )
        }
    }
}

data class NovaMemory(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val facts: List<NovaFact> = emptyList(),
    val lastUpdated: Date = Date(),
    val createdAt: Date = Date(),
) {
    val preferredName: String?
        get() {
            val mostRecent = facts
                .filter { it.type == NovaFactType.PREFERENCE }
                .maxByOrNull { it.timestamp.time }
            return extractName(mostRecent?.content)
        }

    fun toFirestoreData(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "facts" to facts.map { it.toFirestoreData() },
        "lastUpdated" to Timestamp(lastUpdated),
        "createdAt" to Timestamp(createdAt),
    )

    fun addingFacts(newFacts: List<NovaFact>): NovaMemory = upsertingFacts(newFacts)

    /** Merge new facts, replacing duplicates by normalized content. Cap 20 by relevance. */
    fun upsertingFacts(newFacts: List<NovaFact>): NovaMemory {
        val merged = facts.toMutableList()
        for (raw in newFacts) {
            val incoming = normalizedFact(raw)
            if (isPreferredNameFact(incoming)) {
                merged.removeAll(::isPreferredNameFact)
            }
            merged.removeAll { it.normalizedContent == incoming.normalizedContent }
            merged += incoming
        }
        val capped = merged.sortedByDescending { it.relevanceScore }.take(20)
        return copy(facts = capped, lastUpdated = Date())
    }

    /** Deduplicate existing stored facts (e.g. on load). */
    fun compacted(): NovaMemory {
        if (facts.size <= 1) return this
        var working = copy(facts = emptyList())
        for (fact in facts.sortedBy { it.timestamp }) {
            working = working.upsertingFacts(listOf(fact))
        }
        return copy(facts = working.facts, lastUpdated = Date())
    }

    fun removingFact(id: String): NovaMemory =
        copy(facts = facts.filterNot { it.id == id }, lastUpdated = Date())

    fun updatingFact(id: String, content: String? = null, importance: Int? = null): NovaMemory {
        val updatedFacts = facts.map { fact ->
            if (fact.id != id) return@map fact
            val resolvedContent = content ?: fact.content
            fact.copy(
                content = resolvedContent,
                importance = importance ?: fact.importance,
                lastVerified = Date(),
                embedding = if (resolvedContent == fact.content) fact.embedding else null,
            )
        }
        return copy(facts = updatedFacts, lastUpdated = Date())
    }

    fun clearingFacts(): NovaMemory = copy(facts = emptyList(), lastUpdated = Date())

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaMemory? {
            val id = data["id"] as? String ?: return null
            val userId = data["userId"] as? String ?: return null
            val rawFacts = data["facts"] as? List<*> ?: return null
            val lastUpdated = (data["lastUpdated"] as? Timestamp)?.toDate() ?: return null
            val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return null
            val facts = rawFacts.mapNotNull { raw ->
                val map = (raw as? Map<*, *>)?.entries
                    ?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }
                    ?.toMap()
                    ?: return@mapNotNull null
                NovaFact.fromFirestoreData(map)
            }
            return NovaMemory(
                id = id,
                userId = userId,
                facts = facts,
                lastUpdated = lastUpdated,
                createdAt = createdAt,
            )
        }

        fun extractName(preference: String?): String? {
            val trimmed = preference?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            for (separator in listOf(":", " - ", " — ")) {
                if (trimmed.contains(separator)) {
                    val candidate = trimmed.substringAfter(separator).trim()
                    if (candidate.isNotEmpty()) return candidate
                }
            }
            val words = trimmed.split(Regex("\\s+"))
            return if (words.size <= 3) trimmed else words.takeLast(2).joinToString(" ")
        }

        private fun normalizedFact(fact: NovaFact): NovaFact {
            val name = extractPreferredName(fact.content) ?: return fact
            return NovaFact(content = "Preferred name: $name", type = NovaFactType.PREFERENCE, importance = 5)
        }

        private fun isPreferredNameFact(fact: NovaFact): Boolean {
            val lower = fact.normalizedContent
            return lower.startsWith("preferred name:") || lower.startsWith("call me ")
        }

        private fun extractPreferredName(content: String): String? {
            val trimmed = content.trim()
            val lower = trimmed.lowercase()
            for (prefix in listOf("preferred name:", "nombre preferido:", "nombre:")) {
                if (lower.startsWith(prefix)) {
                    return trimmed.drop(prefix.length).trim().takeIf { it.isNotEmpty() }
                }
            }
            for (prefix in listOf("call me ", "me llamo ", "my name is ", "i'm ", "soy ")) {
                if (lower.startsWith(prefix)) {
                    val name = trimmed.drop(prefix.length).trim()
                    if (name.isNotEmpty() && name.split(Regex("\\s+")).size <= 3) return name
                }
            }
            return null
        }
    }
}
