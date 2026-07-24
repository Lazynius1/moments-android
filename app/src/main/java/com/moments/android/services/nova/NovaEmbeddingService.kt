package com.moments.android.services.nova

import com.moments.android.views.nova.memory.NovaFact
import java.util.Locale
import kotlin.math.sqrt

/** Hecho de memoria Nova (mínimo para embeddings; modelo completo en Views/Nova). */
/**
 * Port de NovaEmbeddingService.swift.
 *
 * **Limitación Android documentada:** iOS usa `NLEmbedding.sentenceEmbedding` on-device (NaturalLanguage).
 * No hay equivalente empaquetado en Android ni fallback Cloud Function en iOS cuando falta el modelo;
 * ambas plataformas devuelven `null` sin modelo. Semantic dedup / búsqueda vectorial de hechos Nova
 * requiere integrar TFLite/MediaPipe o un endpoint backend compartido — fuera de scope Services hasta
 * que Nova Views defina el contrato server-side.
 */
object NovaEmbeddingService {

    fun generateEmbedding(forText: String): List<Double>? {
        val clean = forText.lowercase(Locale.getDefault()).trim()
        if (clean.isEmpty()) return null
        // Sin NLEmbedding / TFLite cableado aún → null (comportamiento "sin modelo").
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
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
