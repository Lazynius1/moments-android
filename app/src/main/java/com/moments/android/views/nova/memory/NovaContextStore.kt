package com.moments.android.views.nova.memory

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

private fun Any?.toStringAnyMap(): Map<String, Any?>? = (this as? Map<*, *>)?.entries
    ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
    ?.toMap()

data class NovaConversationSummary(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String?,
    val summary: String,
    val createdAt: Date = Date(),
) {
    fun toFirestoreData(): Map<String, Any> = buildMap {
        put("id", id)
        put("summary", summary)
        put("createdAt", Timestamp(createdAt))
        conversationId?.let { put("conversationId", it) }
    }

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaConversationSummary? = kotlin.run {
            NovaConversationSummary(
                id = data["id"] as? String ?: return@run null,
                conversationId = data["conversationId"] as? String,
                summary = data["summary"] as? String ?: return@run null,
                createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return@run null,
            )
        }
    }
}

data class NovaUserContext(
    val userId: String,
    val conversationSummaries: List<NovaConversationSummary> = emptyList(),
) {
    fun toFirestoreData(): Map<String, Any> = mapOf(
        "userId" to userId,
        "conversationSummaries" to conversationSummaries.map(NovaConversationSummary::toFirestoreData),
    )

    fun addingSummary(summary: NovaConversationSummary): NovaUserContext {
        val filtered = conversationSummaries.filter { existing ->
            if (summary.conversationId != null && existing.conversationId == summary.conversationId) false
            else existing.summary != summary.summary
        }
        return copy(conversationSummaries = (listOf(summary) + filtered)
            .sortedByDescending { it.createdAt }
            .take(MAX_SUMMARIES))
    }

    companion object {
        const val MAX_SUMMARIES = 5

        fun fromFirestoreData(data: Map<String, Any?>): NovaUserContext? {
            val userId = data["userId"] as? String ?: return null
            val summaries = (data["conversationSummaries"] as? List<*>)
                ?.mapNotNull { raw -> raw.toStringAnyMap()?.let(NovaConversationSummary::fromFirestoreData) }
                .orEmpty()
            return NovaUserContext(userId, summaries)
        }
    }
}

/** User-scoped encrypted summaries that give Nova limited cross-conversation context. */
object NovaContextStore {
    private val db = FirebaseFirestore.getInstance()
    private val cache = mutableMapOf<String, NovaUserContext>()

    suspend fun loadContext(userId: String): NovaUserContext {
        cache[userId]?.let { return it }
        try {
            val snapshot = contextDocument(userId).get().await()
            val stored = snapshot.data?.toStringAnyMap()?.let(NovaUserContext::fromFirestoreData)
            if (snapshot.exists() && stored != null) {
                val decrypted = NovaMemoryCrypto.decryptContext(stored, userId)
                cache[userId] = decrypted
                if (NovaMemoryCrypto.contextNeedsEncryptionMigration(stored)) runCatching {
                    persistEncrypted(decrypted, userId)
                }
                return decrypted
            }
        } catch (error: Exception) {
            Log.e(TAG, "loadContext failed", error)
        }
        return NovaUserContext(userId).also { cache[userId] = it }
    }

    suspend fun saveContext(context: NovaUserContext) {
        persistEncrypted(context, context.userId)
        cache[context.userId] = context
    }

    fun invalidateCache(userId: String) {
        cache.remove(userId)
    }

    suspend fun clearContext(userId: String) {
        val empty = NovaUserContext(userId)
        persistEncrypted(empty, userId)
        cache[userId] = empty
    }

    private fun contextDocument(userId: String) = db.collection("users").document(userId)
        .collection("novaMemory").document("context")

    private suspend fun persistEncrypted(context: NovaUserContext, userId: String) {
        val encrypted = NovaMemoryCrypto.encryptContext(context, userId)
        contextDocument(userId).set(encrypted.toFirestoreData(), SetOptions.merge()).await()
    }

    private const val TAG = "NovaContextStore"
}
