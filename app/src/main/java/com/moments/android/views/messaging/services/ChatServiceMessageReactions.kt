package com.moments.android.views.messaging.services

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class MessageReactionUpdate(
    val reactionsByMessage: Map<String, Map<String, List<String>>>,
    val changedMessageIds: Set<String>,
)

/** Port de `ChatService+MessageReactions.swift`. */
private object MessageReactionListeners {
    val registrations = ConcurrentHashMap<String, ListenerRegistration>()
    val generations = ConcurrentHashMap<String, AtomicLong>()
}

fun ChatService.listenToMessageReactions(
    conversationId: String,
    replaceExisting: Boolean = true,
    completion: (Result<MessageReactionUpdate>) -> Unit,
) {
    val key = "reactions_$conversationId"
    if (!replaceExisting && MessageReactionListeners.registrations.containsKey(key)) return
    val generation = MessageReactionListeners.generations.getOrPut(key, ::AtomicLong).incrementAndGet()
    MessageReactionListeners.registrations.remove(key)?.remove()
    MessageReactionListeners.registrations[key] = firestore.collectionGroup("messageReactions")
        .whereEqualTo("conversationId", conversationId)
        .addSnapshotListener { snapshot, error ->
            if (MessageReactionListeners.generations[key]?.get() != generation) return@addSnapshotListener
            if (error != null) completion(Result.failure(error))
            else {
                val documents = snapshot?.toList().orEmpty()
                val changed = snapshot?.documentChanges.orEmpty().mapNotNull { it.document.getString("messageId") }.toSet()
                completion(Result.success(MessageReactionUpdate(aggregateReactionMap(documents), changed)))
            }
        }
}

fun ChatService.removeMessageReactionsListener(conversationId: String) {
    val key = "reactions_$conversationId"
    MessageReactionListeners.generations.getOrPut(key, ::AtomicLong).incrementAndGet()
    MessageReactionListeners.registrations.remove(key)?.remove()
}

suspend fun ChatService.fetchReactionMap(conversationId: String, messageIds: List<String>): Map<String, Map<String, List<String>>> {
    val result = mutableMapOf<String, Map<String, List<String>>>()
    messageIds.filter(String::isNotBlank).distinct().chunked(10).forEach { ids ->
        runCatching {
            firestore.collectionGroup("messageReactions")
                .whereEqualTo("conversationId", conversationId)
                .whereIn("messageId", ids)
                .get()
                .await()
                .toList()
        }.getOrNull()?.let { documents ->
            aggregateReactionMap(documents).forEach { (messageId, reactions) ->
                result[messageId] = mergeLegacyAndLiveReactions(result[messageId], reactions) ?: reactions
            }
        }
    }
    return result
}

fun mergeLegacyAndLiveReactions(
    legacy: Map<String, List<String>>?,
    live: Map<String, List<String>>?,
): Map<String, List<String>>? {
    val merged = legacy.orEmpty().mapValues { it.value.toMutableList() }.toMutableMap()
    live.orEmpty().forEach { (emoji, userIds) -> userIds.forEach { userId ->
        merged.keys.toList().forEach { key ->
            merged[key]?.remove(userId)
            if (merged[key].isNullOrEmpty()) merged.remove(key)
        }
        merged.getOrPut(emoji) { mutableListOf() }.also { if (userId !in it) it += userId }
    } }
    return merged.takeIf { it.isNotEmpty() }
}

private fun aggregateReactionMap(documents: List<QueryDocumentSnapshot>): Map<String, Map<String, List<String>>> =
    documents.fold(mutableMapOf<String, Map<String, List<String>>>()) { result, document ->
        val messageId = document.getString("messageId")
        val emoji = document.getString("emoji")
        val userId = document.getString("userId")
        if (!messageId.isNullOrBlank() && !emoji.isNullOrBlank() && !userId.isNullOrBlank()) {
            result[messageId] = mergeLegacyAndLiveReactions(result[messageId], mapOf(emoji to listOf(userId))) ?: emptyMap()
        }
        result
    }
