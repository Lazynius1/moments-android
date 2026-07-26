package com.moments.android.views.messaging.services

import com.google.firebase.firestore.FieldValue
import com.moments.android.MomentsApplication
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.core.conversationPreview
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Port de `ChatService+MessageActions.swift`.
 * Reenviar texto (recifrado E2E por destino) + destacar.
 *
 * `updateConversation` / `neutralConversationPreview` viven en ChatService.swift iOS;
 * aquí se exponen para el forward (mismo contrato).
 */

/** ≡ `neutralConversationPreview(for:)` → `MessageType.conversationPreview`. */
fun ChatService.neutralConversationPreview(type: MessageType): String {
    val context = MomentsApplication.instance
        ?: return when (type) {
            MessageType.TEXT -> "New message"
            MessageType.CHAT_NOTICE -> ""
            else -> type.raw
        }
    return type.conversationPreview(context)
}

/**
 * ≡ `updateConversation(conversationId:lastMessage:senderId:messageType:completion:)`.
 * Restaura `deletedFor` solo para el remitente si aplica.
 */
suspend fun ChatService.updateConversation(
    conversationId: String,
    lastMessage: String,
    senderId: String,
    messageType: MessageType? = null,
): Result<Unit> = runCatching {
    val doc = firestore.collection("conversations").document(conversationId).get().await()
    if (!doc.exists()) {
        error("Conversation not found.")
    }
    @Suppress("UNCHECKED_CAST")
    val participants = doc.get("participants") as? List<*>
    if (participants == null) {
        error("Conversation not found.")
    }

    val deletedFor = (doc.get("deletedFor") as? List<*>)?.filterIsInstance<String>().orEmpty()
    val shouldRestoreSender = senderId in deletedFor

    val updateData = mutableMapOf<String, Any>(
        "lastMessage" to lastMessage,
        "timestamp" to FieldValue.serverTimestamp(),
        "readStatus.$senderId" to true,
        "lastMessageSenderId" to senderId,
        "lastMessageSeenAt" to FieldValue.delete(),
        "lastMessageReaction" to FieldValue.delete(),
    )
    if (messageType != null) {
        updateData["lastMessageType"] = messageType.raw
    }
    if (shouldRestoreSender) {
        updateData["deletedFor"] = FieldValue.arrayRemove(senderId)
    }
    firestore.collection("conversations").document(conversationId).update(updateData).await()
}

suspend fun ChatService.forwardTextMessage(
    plaintext: String,
    destinationConversationId: String,
    senderId: String,
): Result<EnhancedMessage> = runCatching {
    val trimmed = plaintext.trim()
    if (trimmed.isEmpty()) {
        error("Empty forward content")
    }
    val encryptedContent = EncryptionService.encryptChatMessage(trimmed, destinationConversationId)
    val messageId = UUID.randomUUID().toString()
    val message = EnhancedMessage(
        id = messageId,
        conversationId = destinationConversationId,
        senderId = senderId,
        type = MessageType.TEXT,
        content = encryptedContent,
        timestamp = Date(),
        status = MessageStatus.SENDING,
        isRead = false,
        isDeleted = false,
        isViewed = false,
        isForwarded = true,
    )
    val sentMessage = sendMessage(message, useServerTimestamp = true).getOrThrow()
    sentMessage
}

suspend fun ChatService.forwardTextMessage(
    plaintext: String,
    toUserIds: Set<String>,
    senderId: String,
): Result<Unit> = runCatching {
    if (toUserIds.isEmpty()) return@runCatching
    // ≡ DispatchGroup: paralelo; último error gana.
    coroutineScope {
        val results = toUserIds.map { userId ->
            async {
                runCatching {
                    val conversationId = getOrCreateConversation(senderId, userId).getOrThrow()
                    forwardTextMessage(plaintext, conversationId, senderId).getOrThrow()
                }
            }
        }.awaitAll()
        results.firstOrNull { it.isFailure }?.getOrThrow()
    }
}

suspend fun ChatService.toggleMessageStar(
    conversationId: String,
    messageId: String,
    userId: String,
    isStarred: Boolean,
): Result<Unit> = runCatching {
    val fieldUpdate = if (isStarred) {
        mapOf("starredBy" to FieldValue.arrayUnion(userId))
    } else {
        mapOf("starredBy" to FieldValue.arrayRemove(userId))
    }
    firestore.collection("conversations")
        .document(conversationId)
        .collection("messages")
        .document(messageId)
        .update(fieldUpdate)
        .await()
}
