package com.moments.android.views.messaging.services

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import com.moments.android.services.messaging.VanishMessageTimer
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Port de `ChatService+VanishMode.swift`. */
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
        timer?.let { payload["vanishMessageTimer"] = it.raw }
    } else {
        payload["vanishModeEnabledBy"] = FieldValue.delete()
        payload["vanishModeEnabledAt"] = FieldValue.delete()
        payload["vanishMessageTimer"] = FieldValue.delete()
    }
    firestore.collection("conversations").document(conversationId).update(payload).await()
}

suspend fun ChatService.setVanishSettingsNoticeMessageId(conversationId: String, messageId: String): Result<Unit> =
    runCatching { firestore.collection("conversations").document(conversationId).update("vanishSettingsNoticeMessageId", messageId).await() }

suspend fun ChatService.setVanishDisabledNoticeMessageId(conversationId: String, messageId: String): Result<Unit> =
    runCatching { firestore.collection("conversations").document(conversationId).update("vanishDisabledNoticeMessageId", messageId).await() }

suspend fun ChatService.clearVanishDisabledNoticeMessageId(conversationId: String): Result<Unit> =
    runCatching { firestore.collection("conversations").document(conversationId).update("vanishDisabledNoticeMessageId", FieldValue.delete()).await() }

suspend fun ChatService.clearVanishSettingsNoticeMessageId(conversationId: String): Result<Unit> =
    runCatching { firestore.collection("conversations").document(conversationId).update("vanishSettingsNoticeMessageId", FieldValue.delete()).await() }

fun EnhancedMessage.everyoneHasSeen(userId: String): Boolean {
    if (!isVanishModeMessage || type == MessageType.CHAT_NOTICE) return false
    return if (senderId == userId) isRead || status == MessageStatus.READ || readBy.orEmpty().any { it != senderId }
    else isRead || isViewed
}

fun EnhancedMessage.shouldHideVanishOnChatDismiss(userId: String, timer: VanishMessageTimer): Boolean {
    if (!isVanishModeMessage || type == MessageType.CHAT_NOTICE) return false
    val expired = vanishExpiresAt?.before(Date()) == true
    return if (timer == VanishMessageTimer.ONCE_SEEN) expired || everyoneHasSeen(userId) else expired
}
