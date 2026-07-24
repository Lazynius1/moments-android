package com.moments.android.views.messaging.services

import com.google.firebase.firestore.FieldValue
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/** Port de `ChatService+MessageActions.swift`. */
suspend fun ChatService.forwardTextMessage(
    plaintext: String,
    destinationConversationId: String,
    senderId: String,
): Result<EnhancedMessage> = runCatching {
    val text = plaintext.trim()
    require(text.isNotEmpty())
    val message = EnhancedMessage(
        id = UUID.randomUUID().toString(),
        conversationId = destinationConversationId,
        senderId = senderId,
        type = MessageType.TEXT,
        content = EncryptionService.encryptChatMessage(text, destinationConversationId),
        timestamp = Date(),
        status = MessageStatus.SENDING,
        isRead = false,
        isDeleted = false,
        isViewed = false,
        isForwarded = true,
    )
    sendMessage(message, useServerTimestamp = true).getOrThrow()
}

suspend fun ChatService.forwardTextMessage(
    plaintext: String,
    toUserIds: Set<String>,
    senderId: String,
): Result<Unit> = runCatching {
    toUserIds.filter { it.isNotBlank() && it != senderId }.forEach { userId ->
        val conversationId = materializeConversation(userId, senderId).getOrThrow()
        forwardTextMessage(plaintext, conversationId, senderId).getOrThrow()
    }
}

suspend fun ChatService.toggleMessageStar(
    conversationId: String,
    messageId: String,
    userId: String,
    isStarred: Boolean,
): Result<Unit> = runCatching {
    val update = if (isStarred) FieldValue.arrayUnion(userId) else FieldValue.arrayRemove(userId)
    firestore.collection("conversations").document(conversationId).collection("messages").document(messageId)
        .update("starredBy", update)
        .await()
}
