package com.moments.android.services.messaging

import com.moments.android.views.messaging.core.ChatTextMarkup

/**
 * Donación de shortcut de conversación al recibir un push (People API).
 */
object ChatCommunicationNotificationService {

    fun donateFromPush(userInfo: Map<String, Any?>, previewBody: String?) {
        val type = (userInfo["type"] as? String)?.lowercase() ?: return
        if (!ChatNotificationThread.isChatMessage(type)) return
        val conversationId = ChatNotificationThread.conversationId(userInfo) ?: return
        val messageId = userInfo["messageId"] as? String ?: return
        val senderId = userInfo["senderId"] as? String ?: return
        val senderUsername = (userInfo["senderUsername"] as? String)?.takeIf { it.isNotBlank() } ?: "Moments"
        val avatarUrl = userInfo["senderProfileImage"] as? String
        val preview = previewBody
            ?.let { ChatTextMarkup.plainText(it, hidesSpoilers = true) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val isGroup = ChatNotificationThread.isGroup(userInfo)
        ChatCommunicationIntentDonor.donateIncomingMessage(
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            senderUsername = senderUsername,
            senderProfileImageUrl = avatarUrl,
            messagePreview = preview,
            conversationLabel = ChatNotificationThread.resolvedGroupName(userInfo),
            isGroup = isGroup,
        )
    }
}
