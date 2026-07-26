package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.services.persistence.LocalPersistenceService
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Port de `ChatService+VanishMode.swift`
 * (+ helpers `EnhancedMessage` del mismo archivo).
 */
suspend fun ChatService.setVanishMode(
    conversationId: String,
    active: Boolean,
    userId: String,
    timer: VanishMessageTimer? = null,
): Result<Unit> = runCatching {
    val payload = mutableMapOf<String, Any>("vanishModeActive" to active)
    if (active) {
        payload["vanishModeEnabledBy"] = userId
        payload["vanishModeEnabledAt"] = FieldValue.serverTimestamp()
        if (timer != null) {
            payload["vanishMessageTimer"] = timer.raw
        }
    } else {
        payload["vanishModeEnabledBy"] = FieldValue.delete()
        payload["vanishModeEnabledAt"] = FieldValue.delete()
        payload["vanishMessageTimer"] = FieldValue.delete()
    }
    firestore.collection("conversations").document(conversationId).update(payload).await()
}

suspend fun ChatService.setVanishMessageTimer(
    conversationId: String,
    timer: VanishMessageTimer,
): Result<Unit> = runCatching {
    firestore.collection("conversations").document(conversationId)
        .update("vanishMessageTimer", timer.raw)
        .await()
}

suspend fun ChatService.setVanishSettingsNoticeMessageId(
    conversationId: String,
    messageId: String,
): Result<Unit> = runCatching {
    firestore.collection("conversations").document(conversationId)
        .update("vanishSettingsNoticeMessageId", messageId)
        .await()
}

suspend fun ChatService.setVanishDisabledNoticeMessageId(
    conversationId: String,
    messageId: String,
): Result<Unit> = runCatching {
    firestore.collection("conversations").document(conversationId)
        .update("vanishDisabledNoticeMessageId", messageId)
        .await()
}

suspend fun ChatService.clearVanishDisabledNoticeMessageId(conversationId: String): Result<Unit> =
    runCatching {
        firestore.collection("conversations").document(conversationId)
            .update("vanishDisabledNoticeMessageId", FieldValue.delete())
            .await()
    }

suspend fun ChatService.clearVanishSettingsNoticeMessageId(conversationId: String): Result<Unit> =
    runCatching {
        firestore.collection("conversations").document(conversationId)
            .update("vanishSettingsNoticeMessageId", FieldValue.delete())
            .await()
    }

suspend fun ChatService.sendChatNotice(
    conversationId: String,
    senderId: String,
    noticeKey: String,
): Result<EnhancedMessage> {
    val messageId = UUID.randomUUID().toString()
    return sendMessage(
        EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.CHAT_NOTICE,
            content = noticeKey,
            timestamp = Date(),
            status = MessageStatus.SENT,
            isRead = true,
            isDeleted = false,
            isViewed = true,
        ),
        useServerTimestamp = true,
    )
}

suspend fun ChatService.updateChatNotice(
    conversationId: String,
    messageId: String,
    noticeKey: String,
): Result<Unit> = runCatching {
    firestore.collection("conversations").document(conversationId)
        .collection("messages").document(messageId)
        .update("content", noticeKey)
        .await()
    LocalPersistenceService.updateMessageNoticeContent(conversationId, messageId, noticeKey)
}

suspend fun ChatService.stampVanishExpiry(
    conversationId: String,
    messageId: String,
    expiresAt: Date,
): Result<Unit> = runCatching {
    firestore.collection("conversations").document(conversationId)
        .collection("messages").document(messageId)
        .update("vanishExpiresAt", Timestamp(expiresAt))
        .await()
    LocalPersistenceService.updateMessageVanishExpiresAt(conversationId, messageId, expiresAt)
}

suspend fun ChatService.markVanishMessagesVanishedForMe(
    conversationId: String,
    messageIds: Collection<String>,
    userId: String,
): Result<Unit> = runCatching {
    if (messageIds.isEmpty()) return@runCatching
    val batch = firestore.batch()
    val messages = firestore.collection("conversations").document(conversationId).collection("messages")
    for (messageId in messageIds.distinct()) {
        batch.update(messages.document(messageId), "vanishedFor", FieldValue.arrayUnion(userId))
    }
    batch.commit().await()
}

fun ChatService.purgeVanishMessagesLocally(conversationId: String, messageIds: Collection<String>) {
    if (messageIds.isEmpty()) return
    for (messageId in messageIds) {
        LocalPersistenceService.removeCachedMessage(conversationId, messageId)
        ChatCacheStore.deleteMessageFiles(conversationId, messageId)
    }
}

/** ≡ `reportVanishScreenshot` — notice en el chat, no colección inventada. */
suspend fun ChatService.reportVanishScreenshot(
    conversationId: String,
    reporterId: String,
): Result<Unit> = sendChatNotice(
    conversationId,
    reporterId,
    VanishMessageTimer.SCREENSHOT_NOTICE_TOKEN,
).map { }

/** ≡ `reportVanishScreenRecording`. */
suspend fun ChatService.reportVanishScreenRecording(
    conversationId: String,
    reporterId: String,
): Result<Unit> = sendChatNotice(
    conversationId,
    reporterId,
    VanishMessageTimer.SCREEN_RECORDING_NOTICE_TOKEN,
).map { }

// MARK: - EnhancedMessage helpers (mismo archivo iOS)

/** ≡ `recipientHasAcknowledgedRead()`. */
private fun EnhancedMessage.recipientHasAcknowledgedRead(): Boolean {
    if (isRead || status == MessageStatus.READ) return true
    val readBy = readBy ?: return false
    return readBy.any { it != senderId }
}

/** ≡ `everyoneHasSeen(for:)`. */
fun EnhancedMessage.everyoneHasSeen(userId: String): Boolean {
    if (!isVanishModeMessage || type == MessageType.CHAT_NOTICE) return false
    return if (senderId == userId) {
        recipientHasAcknowledgedRead()
    } else {
        isRead || isViewed
    }
}

/** ≡ `shouldHideVanishOnChatDismiss(for:timer:)`. */
fun EnhancedMessage.shouldHideVanishOnChatDismiss(
    userId: String,
    timer: VanishMessageTimer,
): Boolean {
    if (!isVanishModeMessage || type == MessageType.CHAT_NOTICE) return false
    if (VanishMessageTimer.isExpired(vanishExpiresAt)) return true
    return when (timer) {
        VanishMessageTimer.ONCE_SEEN -> everyoneHasSeen(userId)
        VanishMessageTimer.HOURS_24, VanishMessageTimer.DAYS_7 ->
            VanishMessageTimer.isExpired(vanishExpiresAt)
    }
}
