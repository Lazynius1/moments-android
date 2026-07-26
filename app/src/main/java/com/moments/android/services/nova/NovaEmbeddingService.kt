package com.moments.android.services.nova

import com.moments.android.views.nova.memory.NovaFact
import java.util.Locale
import kotlin.math.sqrt

/**
 * Port de `NovaEmbeddingService.swift`.
 *
 * iOS: `NLEmbedding.sentenceEmbedding` on-device. Sin modelo → `null` / listas vacías.
 * Android: sin NLEmbedding ni TFLite cableado → mismo fallo graceful (dedup exacto por
 * `normalizedContent` sigue vivo en [isNearDuplicate]).
 */
object NovaEmbeddingService {

    fun generateEmbedding(forText: String): List<Double>? {
        val cleanText = forText.lowercase(Locale.getDefault()).trim()
        if (cleanText.isEmpty()) return null
        // Sin modelo on-device (equivalente iOS cuando NLEmbedding no carga).
        return null
    }

    fun findSimilarFacts(query: String, facts: List<NovaFact>, limit: Int = 5): List<NovaFact> {
        val queryVector = generateEmbedding(query) ?: return emptyList()
        val threshold = 0.5
        return facts.mapNotNull { fact ->
            val factVector = fact.embedding ?: generateEmbedding(fact.content) ?: return@mapNotNull null
            fact to cosineSimilarity(queryVector, factVector)
        }
            .filter { it.second > threshold }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** True when candidate is semantically redundant with any existing fact. */
    fun isNearDuplicate(
        candidate: NovaFact,
        existing: List<NovaFact>,
        threshold: Double = 0.82,
    ): Boolean {
        val candidateKey = candidate.normalizedContent
        if (existing.any { it.normalizedContent == candidateKey }) return true

        val candidateVector = candidate.embedding ?: generateEmbedding(candidate.content) ?: return false
        for (fact in existing) {
            val factVector = fact.embedding ?: generateEmbedding(fact.content) ?: continue
            if (cosineSimilarity(candidateVector, factVector) >= threshold) return true
        }
        return false
    }

    fun cosineSimilarity(v1: List<Double>, v2: List<Double>): Double {
        if (v1.size != v2.size) return 0.0

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}
