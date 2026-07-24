package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

/** Port de `ChatService+Buzz.swift`. */
data class ChatBuzzEvent(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val createdAt: Date,
) {
    companion object {
        const val EVENT_LIFETIME_MILLIS = 5L * 60L * 1000L
    }
}

private val buzzListeners = mutableMapOf<String, ListenerRegistration>()

fun ChatService.listenToBuzzEvents(
    conversationId: String,
    cutoffDate: Date? = null,
    limit: Long = 80L,
    replaceExisting: Boolean = true,
    onEvent: (event: ChatBuzzEvent, isInitialSnapshot: Boolean) -> Unit,
) {
    if (conversationId.isBlank()) return
    if (!replaceExisting && conversationId in buzzListeners) return
    buzzListeners.remove(conversationId)?.remove()
    var deliveredInitialSnapshot = false
    buzzListeners[conversationId] = firestore.collection("conversations").document(conversationId)
        .collection("buzzEvents")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(limit)
        .addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val initial = !deliveredInitialSnapshot
            snapshot?.documentChanges.orEmpty().forEach { change ->
                if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                val data = change.document.data
                if (data["type"] as? String != "buzz") return@forEach
                val senderId = data["senderId"] as? String ?: return@forEach
                val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: Date()
                if (cutoffDate != null && createdAt <= cutoffDate) return@forEach
                onEvent(ChatBuzzEvent(change.document.id, conversationId, senderId, createdAt), initial)
            }
            deliveredInitialSnapshot = true
        }
}

fun ChatService.removeBuzzListener(conversationId: String) {
    buzzListeners.remove(conversationId)?.remove()
}
