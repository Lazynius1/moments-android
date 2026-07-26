package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Port de `ChatService+Buzz.swift`.
 * Colección Firestore: `conversations/{id}/buzzEvents`.
 */
data class ChatBuzzEvent(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val createdAt: Date,
) {
    companion object {
        /** TTL ≡ `ChatBuzzEvent.eventLifetime` / `ChatBuzzProcessedStore.replayWindow` (~5 min). */
        val eventLifetimeMillis: Long get() = ChatBuzzProcessedStore.replayWindowMillis
    }
}

private val buzzListeners = mutableMapOf<String, ListenerRegistration>()
private val buzzListenerGenerations = mutableMapOf<String, Int>()

private fun buzzListenerKey(conversationId: String) = "buzz_$conversationId"

private fun beginBuzzListenerGeneration(key: String): Int {
    val next = (buzzListenerGenerations[key] ?: 0) + 1
    buzzListenerGenerations[key] = next
    return next
}

private fun isCurrentBuzzListenerGeneration(generation: Int, key: String): Boolean =
    buzzListenerGenerations[key] == generation

suspend fun ChatService.sendBuzz(conversationId: String, senderId: String): Result<Unit> = runCatching {
    val now = Date()
    firestore.collection("conversations")
        .document(conversationId)
        .collection("buzzEvents")
        .document()
        .set(
            mapOf(
                "senderId" to senderId,
                "type" to "buzz",
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(now.time + ChatBuzzEvent.eventLifetimeMillis)),
                "intensity" to "normal",
                "clientNonce" to UUID.randomUUID().toString(),
            ),
        )
        .await()
}

fun ChatService.listenToBuzzEvents(
    conversationId: String,
    cutoffDate: Date? = null,
    limit: Int = 80,
    replaceExisting: Boolean = true,
    onEvent: (event: ChatBuzzEvent, isInitialSnapshot: Boolean) -> Unit,
) {
    val listenerKey = buzzListenerKey(conversationId)
    if (!replaceExisting && buzzListeners[listenerKey] != null) return

    val generation = beginBuzzListenerGeneration(listenerKey)
    buzzListeners.remove(listenerKey)?.remove()
    var hasDeliveredInitialSnapshot = false

    val query = firestore.collection("conversations")
        .document(conversationId)
        .collection("buzzEvents")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(limit.toLong())

    buzzListeners[listenerKey] = query.addSnapshotListener { snapshot, error ->
        if (!isCurrentBuzzListenerGeneration(generation, listenerKey)) return@addSnapshotListener
        if (error != null) return@addSnapshotListener
        val changes = snapshot?.documentChanges ?: return@addSnapshotListener
        val isInitialSnapshot = !hasDeliveredInitialSnapshot

        for (change in changes) {
            if (change.type != DocumentChange.Type.ADDED) continue
            val data = change.document.data
            if (data["type"] as? String != "buzz") continue
            val senderId = data["senderId"] as? String ?: continue
            val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: Date()
            if (cutoffDate != null && !createdAt.after(cutoffDate)) continue
            onEvent(
                ChatBuzzEvent(
                    id = change.document.id,
                    conversationId = conversationId,
                    senderId = senderId,
                    createdAt = createdAt,
                ),
                isInitialSnapshot,
            )
        }
        hasDeliveredInitialSnapshot = true
    }
}

fun ChatService.removeBuzzListener(conversationId: String) {
    val key = buzzListenerKey(conversationId)
    buzzListeners.remove(key)?.remove()
}

/** Usado por `ChatService.removeAllListeners()`. */
fun ChatService.removeAllBuzzListeners() {
    buzzListeners.values.forEach { it.remove() }
    buzzListeners.clear()
    buzzListenerGenerations.clear()
}
