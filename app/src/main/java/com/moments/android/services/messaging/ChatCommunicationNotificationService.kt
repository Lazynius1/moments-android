package com.moments.android.services.messaging

/**
 * Port de ChatCommunicationNotificationService.swift — solo `donateFromPush`.
 * La donación real / shortcuts viven en [ChatCommunicationIntentDonor].
 */
object ChatCommunicationNotificationService {

    fun donateFromPush(userInfo: Map<String, Any?>, previewBody: String?) {
        val type = (userInfo["type"] as? String)?.lowercase() ?: return
        if (type != "message" && type != "new_message") return
        val conversationId = userInfo["conversationId"] as? String ?: return
        val messageId = userInfo["messageId"] as? String ?: return
        val senderId = userInfo["senderId"] as? String ?: return
        val senderUsername = (userInfo["senderUsername"] as? String)?.takeIf { it.isNotBlank() } ?: "Moments"
        val avatarUrl = userInfo["senderProfileImage"] as? String
        val preview = previewBody?.trim()?.takeIf { it.isNotEmpty() }

        ChatCommunicationIntentDonor.donateIncomingMessage(
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            senderUsername = senderUsername,
            senderProfileImageUrl = avatarUrl,
            messagePreview = preview,
        )
    }
}
