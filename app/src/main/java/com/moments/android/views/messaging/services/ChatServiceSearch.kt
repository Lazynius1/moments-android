package com.moments.android.views.messaging.services

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

/** Port de `ChatService+Search.swift`. */
private const val remoteSearchBatchSize = 200L
private const val remoteSearchMaxMatches = 100

suspend fun ChatService.searchMessages(
    conversationId: String,
    query: String,
    excludingIds: Set<String> = emptySet(),
    limit: Int = remoteSearchMaxMatches,
): Result<List<EnhancedMessage>> = runCatching {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank()) return@runCatching emptyList()
    preloadEncryption(conversationId)
    val matches = mutableListOf<EnhancedMessage>()
    var lastDocument: DocumentSnapshot? = null
    var hasMore = true
    while (hasMore && matches.size < limit) {
        var request: Query = firestore.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(remoteSearchBatchSize)
        lastDocument?.let { request = request.startAfter(it) }
        val snapshot = request.get().await()
        if (snapshot.isEmpty) break
        lastDocument = snapshot.documents.last()
        hasMore = snapshot.size().toLong() >= remoteSearchBatchSize
        for (document in snapshot.documents) {
            val data = document.data ?: continue
            if (data["type"] != MessageType.TEXT.raw || data["isDeleted"] == true || document.id in excludingIds) continue
            val encryptedContent = data["content"] as? String ?: continue
            val plainText = decryptMessageContent(encryptedContent, conversationId)
            if (!normalizeSearchText(plainText).contains(normalizedQuery)) continue
            matches += ChatMessageMapper.buildFromMap(data, document.id, conversationId)
            if (matches.size >= limit) break
        }
    }
    matches.sortedBy(EnhancedMessage::timestamp)
}

private fun normalizeSearchText(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .lowercase()
