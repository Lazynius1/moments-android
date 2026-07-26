package com.moments.android.views.messaging.services

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot
import kotlinx.coroutines.tasks.await

/**
 * Port de `ChatService+MessageReactions.swift`.
 * Collection group: `messageReactions`.
 */
data class MessageReactionUpdate(
    val reactionsByMessage: Map<String, Map<String, List<String>>>,
    val changedMessageIds: Set<String>,
)

/** Listeners por clave `reactions_{conversationId}` (≡ `activeListeners` iOS). */
private val reactionListeners = mutableMapOf<String, ListenerRegistration>()
private val reactionListenerGenerations = mutableMapOf<String, Int>()

private fun beginReactionListenerGeneration(key: String): Int {
    val next = (reactionListenerGenerations[key] ?: 0) + 1
    reactionListenerGenerations[key] = next
    return next
}

private fun isCurrentReactionListenerGeneration(generation: Int, key: String): Boolean =
    reactionListenerGenerations[key] == generation

fun ChatService.listenToMessageReactions(
    conversationId: String,
    replaceExisting: Boolean = true,
    completion: (Result<MessageReactionUpdate>) -> Unit,
) {
    val listenerKey = "reactions_$conversationId"
    if (!replaceExisting && reactionListeners[listenerKey] != null) return

    val generation = beginReactionListenerGeneration(listenerKey)
    reactionListeners.remove(listenerKey)?.remove()

    reactionListeners[listenerKey] = firestore.collectionGroup("messageReactions")
        .whereEqualTo("conversationId", conversationId)
        .addSnapshotListener { snapshot, error ->
            if (!isCurrentReactionListenerGeneration(generation, listenerKey)) return@addSnapshotListener
            if (error != null) {
                completion(Result.failure(error))
                return@addSnapshotListener
            }
            val docs = snapshot?.documents.orEmpty().filterIsInstance<QueryDocumentSnapshot>()
            val changedMessageIds = snapshot?.documentChanges.orEmpty()
                .mapNotNull { it.document.getString("messageId") }
                .toSet()
            completion(
                Result.success(
                    MessageReactionUpdate(
                        reactionsByMessage = aggregateReactionMap(docs),
                        changedMessageIds = changedMessageIds,
                    ),
                ),
            )
        }
}

/** ≡ parte reactions de `ChatService.removeListener(for:)` iOS. */
fun ChatService.removeMessageReactionsListener(conversationId: String) {
    val listenerKey = "reactions_$conversationId"
    beginReactionListenerGeneration(listenerKey)
    reactionListeners.remove(listenerKey)?.remove()
}

/** Usado por `ChatService.removeAllListeners()`. */
fun ChatService.removeAllMessageReactionsListeners() {
    reactionListeners.values.forEach { it.remove() }
    reactionListeners.clear()
    reactionListenerGenerations.clear()
}

suspend fun ChatService.fetchReactionMap(
    conversationId: String,
    messageIds: List<String>,
): Map<String, Map<String, List<String>>> {
    val ids = messageIds.filter { it.isNotEmpty() }.distinct()
    if (ids.isEmpty()) return emptyMap()

    val aggregated = mutableMapOf<String, Map<String, List<String>>>()
    for (chunk in chunkMessageIds(ids, size = 10)) {
        runCatching {
            firestore.collectionGroup("messageReactions")
                .whereEqualTo("conversationId", conversationId)
                .whereIn("messageId", chunk)
                .get()
                .await()
        }.getOrNull()?.let { snapshot ->
            val partial = aggregateReactionMap(
                snapshot.documents.filterIsInstance<QueryDocumentSnapshot>(),
            )
            for ((messageId, incoming) in partial) {
                val current = aggregated[messageId]
                aggregated[messageId] = mergeReactionBuckets(current, incoming)
            }
        }
    }
    return aggregated
}

/** ≡ `mergeLegacyAndLiveReactions(legacy:live:)`. */
fun ChatService.mergeLegacyAndLiveReactions(
    legacy: Map<String, List<String>>?,
    live: Map<String, List<String>>?,
): Map<String, List<String>>? = mergeReactionMaps(legacy, live)

private fun mergeReactionBuckets(
    current: Map<String, List<String>>?,
    incoming: Map<String, List<String>>,
): Map<String, List<String>> = mergeReactionMaps(current, incoming) ?: incoming

private fun mergeReactionMaps(
    legacy: Map<String, List<String>>?,
    live: Map<String, List<String>>?,
): Map<String, List<String>>? {
    val merged = legacy.orEmpty().mapValues { it.value.toMutableList() }.toMutableMap()
    for ((emoji, userIds) in live.orEmpty()) {
        for (userId in userIds) {
            for (key in merged.keys.toList()) {
                merged[key]?.removeAll { it == userId }
                if (merged[key].isNullOrEmpty()) merged.remove(key)
            }
            val updatedUserIds = merged.getOrPut(emoji) { mutableListOf() }
            if (userId !in updatedUserIds) updatedUserIds += userId
        }
    }
    return merged.takeIf { it.isNotEmpty() }
}

private fun aggregateReactionMap(
    documents: List<QueryDocumentSnapshot>,
): Map<String, Map<String, List<String>>> {
    val map = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
    for (document in documents) {
        val data = document.data
        val messageId = data["messageId"] as? String
        val emoji = data["emoji"] as? String
        val userId = data["userId"] as? String
        if (messageId.isNullOrEmpty() || emoji.isNullOrEmpty() || userId.isNullOrEmpty()) continue

        val reactions = map.getOrPut(messageId) { mutableMapOf() }
        for (key in reactions.keys.toList()) {
            reactions[key]?.removeAll { it == userId }
            if (reactions[key].isNullOrEmpty()) reactions.remove(key)
        }
        val userIds = reactions.getOrPut(emoji) { mutableListOf() }
        if (userId !in userIds) userIds += userId
    }
    return map.mapValues { (_, reactions) -> reactions.mapValues { it.value.toList() } }
}

private fun chunkMessageIds(ids: List<String>, size: Int): List<List<String>> {
    if (size <= 0) return if (ids.isEmpty()) emptyList() else listOf(ids)
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(size)
}
