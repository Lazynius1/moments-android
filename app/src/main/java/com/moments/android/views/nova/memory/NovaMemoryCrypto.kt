package com.moments.android.views.nova.memory

import android.util.Base64
import com.moments.android.services.messaging.EncryptionService

/**
 * Port de `Views/Nova/Memory/NovaMemoryCrypto.swift`.
 * Cifrado/descifrado de facts y resúmenes vía [EncryptionService] (AES-GCM + Base64).
 */
object NovaMemoryCrypto {
    fun isProbablyEncrypted(text: String): Boolean =
        runCatching { Base64.decode(text, Base64.DEFAULT).size >= 28 }.getOrDefault(false)

    suspend fun decryptMemory(memory: NovaMemory, userId: String): NovaMemory =
        memory.copy(facts = decryptFacts(memory.facts, userId))

    suspend fun encryptMemory(memory: NovaMemory, userId: String): NovaMemory =
        memory.copy(facts = encryptFacts(memory.facts, userId))

    suspend fun decryptContext(context: NovaUserContext, userId: String): NovaUserContext {
        val summaries = context.conversationSummaries.map { summary ->
            summary.copy(summary = EncryptionService.decryptNovaData(summary.summary, userId))
        }
        return NovaUserContext(userId = context.userId, conversationSummaries = summaries)
    }

    suspend fun encryptContext(context: NovaUserContext, userId: String): NovaUserContext {
        val summaries = context.conversationSummaries.map { summary ->
            summary.copy(summary = EncryptionService.encryptNovaData(summary.summary, userId))
        }
        return NovaUserContext(userId = context.userId, conversationSummaries = summaries)
    }

    fun memoryNeedsEncryptionMigration(memory: NovaMemory): Boolean =
        memory.facts.any { it.content.isNotEmpty() && !isProbablyEncrypted(it.content) }

    fun contextNeedsEncryptionMigration(context: NovaUserContext): Boolean =
        context.conversationSummaries.any { it.summary.isNotEmpty() && !isProbablyEncrypted(it.summary) }

    private suspend fun decryptFacts(facts: List<NovaFact>, userId: String): List<NovaFact> =
        facts.map { fact ->
            fact.copy(content = EncryptionService.decryptNovaData(fact.content, userId))
        }

    private suspend fun encryptFacts(facts: List<NovaFact>, userId: String): List<NovaFact> =
        facts.map { fact ->
            fact.copy(content = EncryptionService.encryptNovaData(fact.content, userId))
        }
}
