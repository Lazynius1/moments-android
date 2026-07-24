package com.moments.android.views.nova.memory

import android.util.Base64
import com.moments.android.services.messaging.EncryptionService

object NovaMemoryCrypto {
    fun isProbablyEncrypted(text: String): Boolean = runCatching { Base64.decode(text, Base64.DEFAULT).size >= 28 }.getOrDefault(false)

    suspend fun decryptMemory(memory: NovaMemory, userId: String): NovaMemory =
        memory.copy(facts = decryptFacts(memory.facts, userId))

    suspend fun encryptMemory(memory: NovaMemory, userId: String): NovaMemory =
        memory.copy(facts = encryptFacts(memory.facts, userId))

    suspend fun decryptContext(context: NovaUserContext, userId: String): NovaUserContext = context.copy(
        conversationSummaries = context.conversationSummaries.map { it.copy(summary = EncryptionService.decryptNovaData(it.summary, userId)) },
    )

    suspend fun encryptContext(context: NovaUserContext, userId: String): NovaUserContext = context.copy(
        conversationSummaries = context.conversationSummaries.map { it.copy(summary = EncryptionService.encryptNovaData(it.summary, userId)) },
    )

    fun memoryNeedsEncryptionMigration(memory: NovaMemory) = memory.facts.any { it.content.isNotEmpty() && !isProbablyEncrypted(it.content) }
    fun contextNeedsEncryptionMigration(context: NovaUserContext) = context.conversationSummaries.any { it.summary.isNotEmpty() && !isProbablyEncrypted(it.summary) }

    private suspend fun decryptFacts(facts: List<NovaFact>, userId: String) = facts.map { it.copy(content = EncryptionService.decryptNovaData(it.content, userId)) }
    private suspend fun encryptFacts(facts: List<NovaFact>, userId: String) = facts.map { it.copy(content = EncryptionService.encryptNovaData(it.content, userId)) }
}
