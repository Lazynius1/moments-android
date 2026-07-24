package com.moments.android.views.nova.memory

import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID

enum class NovaFactType(val rawValue: String, val priority: Int, val emoji: String) {
    PREFERENCE("preference", 5, "⚙️"), PERSONAL("personal", 4, "👤"), PROFESSIONAL("professional", 3, "💼"), INTEREST("interest", 2, "❤️"), GENERAL("general", 1, "💭");
    companion object { fun fromRaw(value: String?) = entries.firstOrNull { it.rawValue == value } }
}

data class NovaFact(
    val id: String = UUID.randomUUID().toString(), val content: String, val type: NovaFactType,
    val timestamp: Date = Date(), val importance: Int = 3, val lastVerified: Date = Date(), val embedding: List<Double>? = null,
) {
    val clampedImportance get() = importance.coerceIn(1, 5)
    val relevanceScore get() = type.priority * 10 + clampedImportance
    val normalizedContent get() = content.lowercase().trim()
    fun toFirestoreData(): Map<String, Any?> = buildMap {
        put("id", id); put("content", content); put("type", type.rawValue); put("timestamp", Timestamp(timestamp)); put("importance", clampedImportance); put("lastVerified", Timestamp(lastVerified)); put("lastProbedAt", null); embedding?.let { put("embedding", it) }
    }
    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaFact? = kotlin.run {
            NovaFact(
                id = data["id"] as? String ?: return@run null, content = data["content"] as? String ?: return@run null,
                type = NovaFactType.fromRaw(data["type"] as? String) ?: return@run null,
                timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: return@run null,
                importance = (data["importance"] as? Number)?.toInt() ?: 3,
                lastVerified = (data["lastVerified"] as? Timestamp)?.toDate() ?: (data["timestamp"] as? Timestamp)?.toDate() ?: return@run null,
                embedding = (data["embedding"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() },
            )
        }
    }
}

data class NovaMemory(
    val id: String = UUID.randomUUID().toString(), val userId: String, val facts: List<NovaFact> = emptyList(),
    val lastUpdated: Date = Date(), val createdAt: Date = Date(),
) {
    val preferredName: String? get() = extractName(facts.filter { it.type == NovaFactType.PREFERENCE }.maxByOrNull { it.timestamp.time }?.content)
    fun toFirestoreData(): Map<String, Any> = mapOf("id" to id, "userId" to userId, "facts" to facts.map { it.toFirestoreData() }, "lastUpdated" to Timestamp(lastUpdated), "createdAt" to Timestamp(createdAt))
    fun addingFacts(newFacts: List<NovaFact>) = upsertingFacts(newFacts)
    fun upsertingFacts(newFacts: List<NovaFact>): NovaMemory {
        val merged = facts.toMutableList()
        newFacts.forEach { raw ->
            val incoming = normalizedFact(raw)
            if (isPreferredNameFact(incoming)) merged.removeAll(::isPreferredNameFact)
            merged.removeAll { it.normalizedContent == incoming.normalizedContent }
            merged += incoming
        }
        return copy(facts = merged.sortedByDescending { it.relevanceScore }.take(20), lastUpdated = Date())
    }
    fun compacted(): NovaMemory {
        if (facts.size <= 1) return this
        val compacted = facts.sortedBy { it.timestamp }.fold(copy(facts = emptyList())) { memory, fact -> memory.upsertingFacts(listOf(fact)) }
        return copy(facts = compacted.facts, lastUpdated = Date())
    }
    fun removingFact(id: String) = copy(facts = facts.filterNot { it.id == id }, lastUpdated = Date())
    fun updatingFact(id: String, content: String? = null, importance: Int? = null) = copy(
        facts = facts.map { fact -> if (fact.id != id) fact else fact.copy(content = content ?: fact.content, importance = importance ?: fact.importance, lastVerified = Date(), embedding = if (content == null || content == fact.content) fact.embedding else null) }, lastUpdated = Date(),
    )
    fun clearingFacts() = copy(facts = emptyList(), lastUpdated = Date())
    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaMemory? {
            val rawFacts = data["facts"] as? List<*> ?: return null
            return NovaMemory(
                id = data["id"] as? String ?: return null, userId = data["userId"] as? String ?: return null,
                facts = rawFacts.mapNotNull { (it as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }?.toMap()?.let(NovaFact::fromFirestoreData) },
                lastUpdated = (data["lastUpdated"] as? Timestamp)?.toDate() ?: return null, createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return null,
            )
        }
        fun extractName(preference: String?): String? {
            val trimmed = preference?.trim().orEmpty(); if (trimmed.isEmpty()) return null
            listOf(":", " - ", " — ").firstOrNull { trimmed.contains(it) }?.let { separator -> return trimmed.substringAfter(separator).trim().takeIf { it.isNotEmpty() } }
            val words = trimmed.split(Regex("\\s+")); return if (words.size <= 3) trimmed else words.takeLast(2).joinToString(" ")
        }
        private fun normalizedFact(fact: NovaFact): NovaFact = extractPreferredName(fact.content)?.let { NovaFact(content = "Preferred name: $it", type = NovaFactType.PREFERENCE, importance = 5) } ?: fact
        private fun isPreferredNameFact(fact: NovaFact) = fact.normalizedContent.startsWith("preferred name:") || fact.normalizedContent.startsWith("call me ")
        private fun extractPreferredName(content: String): String? {
            val text = content.trim(); val lower = text.lowercase()
            listOf("preferred name:", "nombre preferido:", "nombre:").firstOrNull { lower.startsWith(it) }?.let { return text.drop(it.length).trim().takeIf { name -> name.isNotEmpty() } }
            listOf("call me ", "me llamo ", "my name is ", "i'm ", "soy ").firstOrNull { lower.startsWith(it) }?.let { return text.drop(it.length).trim().takeIf { name -> name.isNotEmpty() && name.split(Regex("\\s+")).size <= 3 } }
            return null
        }
    }
}
