package com.moments.android.notifications.services

import com.moments.android.models.MessageType
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.shared.ChatPreviewPrivacy
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore

/** Port de InAppNotificationPreviewResolver.swift */
object InAppNotificationPreviewResolver {
    private val db = FirebaseFirestore.getInstance()

    suspend fun resolve(
        notification: MomentsNotification,
        userInfo: Map<String, Any?>?,
    ): MomentsNotification {
        val conversationId = notification.conversationId?.trim().orEmpty()
        if (conversationId.isEmpty()) return stripUnsafeTextPreview(notification)

        val previewEnabled = ChatPreviewPrivacy.shouldRevealPreview(
            conversationId,
            ChatPreviewPrivacy.isVanishModeMessage(userInfo ?: emptyMap()),
        )

        return when (notification.type) {
            NotificationType.MESSAGE -> resolveMessagePreview(notification, conversationId, userInfo, previewEnabled)
            NotificationType.MESSAGE_REACTION -> resolveMessageReactionPreview(notification, conversationId, userInfo, previewEnabled)
            else -> stripUnsafeTextPreview(notification)
        }
    }

    private suspend fun resolveMessagePreview(
        notification: MomentsNotification,
        conversationId: String,
        userInfo: Map<String, Any?>?,
        previewEnabled: Boolean,
    ): MomentsNotification {
        if (!previewEnabled) return notification.withBannerPreview(reaction = null, title = notification.title)
        if (notification.messageType != null && notification.messageType != "text") {
            return notification.withBannerPreview(reaction = null, title = notification.title)
        }
        (userInfo?.get("encryptedContent") as? String)?.let { embedded ->
            decryptPreview(embedded, conversationId)?.let {
                return notification.withBannerPreview(reaction = truncated(it, 200), title = notification.title)
            }
        }
        notification.messageId?.let { messageId ->
            fetchAndDecryptMessage(messageId, conversationId)?.let {
                return notification.withBannerPreview(reaction = truncated(it, 200), title = notification.title)
            }
        }
        notification.reaction?.takeIf { it.isNotBlank() && !isNeutralPlaceholder(it) }?.let { reaction ->
            decryptPreview(reaction, conversationId)?.let {
                return notification.withBannerPreview(reaction = truncated(it, 200), title = notification.title)
            }
        }
        return notification.withBannerPreview(reaction = null, title = notification.title)
    }

    private suspend fun resolveMessageReactionPreview(
        notification: MomentsNotification,
        conversationId: String,
        userInfo: Map<String, Any?>?,
        previewEnabled: Boolean,
    ): MomentsNotification {
        if (!previewEnabled || notification.isReactionPlural == true ||
            (notification.messageType != null && notification.messageType != "text")
        ) {
            return notification.withBannerPreview(reaction = notification.reaction, title = null)
        }
        (userInfo?.get("encryptedContent") as? String)?.let { embedded ->
            decryptPreview(embedded, conversationId)?.let {
                return notification.withBannerPreview(reaction = notification.reaction, title = truncated(it, 120))
            }
        }
        notification.messageId?.let { messageId ->
            fetchAndDecryptMessage(messageId, conversationId)?.let {
                return notification.withBannerPreview(reaction = notification.reaction, title = truncated(it, 120))
            }
        }
        notification.title?.takeIf { it.isNotBlank() }?.let { title ->
            decryptPreview(title, conversationId)?.let {
                return notification.withBannerPreview(reaction = notification.reaction, title = truncated(it, 120))
            }
        }
        return notification.withBannerPreview(reaction = notification.reaction, title = null)
    }

    private suspend fun fetchAndDecryptMessage(messageId: String, conversationId: String): String? = runCatching {
        val snapshot = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId).get().await()
        val cipher = snapshot.getString("content")?.trim().orEmpty()
        if (cipher.isEmpty()) return null
        if (ChatPreviewPrivacy.isVanishModeMessage(snapshot.data ?: emptyMap())) return null
        decryptPreview(cipher, conversationId)
    }.getOrNull()

    private suspend fun decryptPreview(content: String, conversationId: String): String? {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || isNeutralPlaceholder(trimmed)) return null
        val decrypted = EncryptionService.decryptChatMessage(trimmed, conversationId)?.trim().orEmpty()
        if (decrypted.isEmpty() || decrypted == trimmed) return null
        return decrypted
    }

    private fun stripUnsafeTextPreview(notification: MomentsNotification): MomentsNotification = when (notification.type) {
        NotificationType.MESSAGE -> {
            val reaction = notification.reaction
            if (!reaction.isNullOrBlank() && !isNeutralPlaceholder(reaction) && !looksLikeEncryptedPayload(reaction)) {
                notification.withBannerPreview(reaction = null, title = notification.title)
            } else notification
        }
        NotificationType.MESSAGE_REACTION -> {
            val title = notification.title
            if (!title.isNullOrBlank() && looksLikeEncryptedPayload(title)) {
                notification.withBannerPreview(reaction = notification.reaction, title = null)
            } else notification
        }
        else -> notification
    }

    private fun isNeutralPlaceholder(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        val neutralPrefixes = listOf("💬", "📷", "🎥", "🎵", "🎞", "😊", "📍", "📎", "📸", "⏱")
        if (neutralPrefixes.any { trimmed.startsWith(it) }) return true
        return trimmed.equals("Message", ignoreCase = true)
    }

    private fun looksLikeEncryptedPayload(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 24) return false
        return trimmed.all { it.isLetterOrDigit() || it in "+/=_-" }
    }

    private fun truncated(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.take(maxLength - 1) + "…"

    private fun MomentsNotification.withBannerPreview(reaction: String?, title: String?) = copy(
        title = title,
        reaction = reaction,
    )
}
