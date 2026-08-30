package com.moments.android.notifications.services

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.moments.android.R
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.services.messaging.SharedChatDecryptor
import com.moments.android.views.messaging.core.MessageRequestFolder
import com.moments.android.views.messaging.core.ChatTextMarkup
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.shared.ChatPreviewPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Port de push FCM ≈ `AppDelegate` + **NSE** `MomentsNotificationService/NotificationService.swift`.
 *
 * Tap → [MainActivity]; quick reply → [ChatNotificationReplyReceiver].
 * Preview E2E / reaction / rich media / server counts ≡ NSE.
 */
class MomentsFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
            FCMTokenService.saveFCMTokenDirectly(token, userId)
        } ?: FCMTokenService.updateFCMToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val userInfo = message.data.mapValues { it.value as Any? }
        if (userInfo.isEmpty()) return
        if ((userInfo["type"] as? String)?.lowercase() == "message_request_v2") {
            scope.launch { handleMessageRequestPush(message, userInfo) }
            return
        }

        scope.launch { handleBackgroundSideEffects(userInfo) }
        NotificationPresentationCoordinator.present(userInfo, NotificationPresentationSource.PUSH)

        if (NotificationPresentationCoordinator.isSilentPush(userInfo)) {
            NotificationBadgeService.refreshAllCounts()
            NotificationBadgeService.setupListeners()
            return
        }

        if (!isAppInForeground()) {
            scope.launch { showSystemNotificationIfNeeded(message, userInfo) }
        }
        NotificationBadgeService.setupListeners()
    }

    private suspend fun handleMessageRequestPush(
        message: RemoteMessage,
        userInfo: Map<String, Any?>,
    ) {
        val threadId = userInfo["threadId"] as? String ?: return
        val shouldPresent = runCatching {
            val service = MessageRequestService()
            service.loadHiddenWordsPreferences()
            service.loadIncomingRequest(threadId).folder != MessageRequestFolder.HIDDEN
        }.getOrDefault(true)
        if (!shouldPresent) {
            Log.d(TAG, "Suppressed locally hidden message request")
            return
        }
        NotificationBadgeService.setupListeners()
        if (!isAppInForeground()) showSystemNotificationIfNeeded(message, userInfo)
    }

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private suspend fun handleBackgroundSideEffects(userInfo: Map<String, Any?>) {
        // ≡ enqueueMessageIngestIfNeeded + mark delivered / badge
        val handledByServer = handleServerCounts(userInfo)
        val type = (userInfo["type"] as? String)?.lowercase()
        val conversationId = userInfo["conversationId"] as? String
        val messageId = userInfo["messageId"] as? String
        if (!conversationId.isNullOrBlank() && !messageId.isNullOrBlank()) {
            ChatService.markMessageAsDeliveredFromNotification(conversationId, messageId)
            MessageIngestService.ingest(userInfo)
        }
        if (!handledByServer &&
            (NotificationPresentationCoordinator.isSilentPush(userInfo) ||
                userInfo["content-available"] == "1" || userInfo["content-available"] == 1)
        ) {
            NotificationBadgeService.refreshAllCounts()
        }
        if (!handledByServer && type != null && type != "message" && type != "new_message") {
            NotificationBadgeService.refreshAllCounts()
        }
    }

    /** ≡ handleServerCounts */
    private fun handleServerCounts(userInfo: Map<String, Any?>): Boolean {
        fun parseCount(key: String): Int? {
            val raw = userInfo[key] ?: return null
            return when (raw) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull()
                else -> null
            }
        }
        val messages = parseCount("unreadMessages") ?: return false
        val notifications = parseCount("unreadNotifications") ?: return false
        val echoes = parseCount("unreadEchoes") ?: 0
        val tags = parseCount("unreadTags") ?: 0
        return NotificationBadgeService.applyServerCounts(messages, notifications, echoes, tags)
    }

    private suspend fun showSystemNotificationIfNeeded(message: RemoteMessage, userInfo: Map<String, Any?>) {
        NotificationShadePoster.ensureChannels(this)
        val title = message.notification?.title
            ?: userInfo["senderUsername"] as? String
            ?: getString(R.string.app_name)
        val suppliedBody = message.notification?.body
            ?: userInfo["body"] as? String
            ?: userInfo["reaction"] as? String
            ?: getString(R.string.notification_message_single_default)

        val resolved = resolveSystemNotificationContent(title, suppliedBody, userInfo)
        NotificationShadePoster.post(
            context = this,
            messageId = message.messageId,
            userInfo = userInfo,
            title = resolved.title,
            body = resolved.body,
        )
    }

    private data class ResolvedContent(val title: String, val body: String)

    /** ≡ resolveMessagePreview + resolveChatReactionPreview; resto → NotificationCopyResolver. */
    private suspend fun resolveSystemNotificationContent(
        suppliedTitle: String,
        suppliedBody: String,
        userInfo: Map<String, Any?>,
    ): ResolvedContent {
        val genericBody = getString(R.string.notification_message_single_default)
        val type = (userInfo["type"] as? String)?.lowercase()

        if (type == "message_request_v2") {
            return ResolvedContent(
                getString(R.string.app_name),
                getString(R.string.notification_message_request_generic),
            )
        }

        if (type == "message_reaction") {
            return resolveChatReactionPreview(suppliedTitle, suppliedBody, userInfo)
                ?: copyFromResolver(userInfo, suppliedTitle, suppliedBody, genericBody)
                ?: ResolvedContent(suppliedTitle, ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody))
        }

        val conversationId = userInfo["conversationId"] as? String
        if (type == "message" || type == "new_message") {
            if (conversationId.isNullOrBlank() ||
                (userInfo["messageType"] as? String) != "text" ||
                !ChatPreviewPrivacy.shouldRevealPreview(
                    conversationId,
                    ChatPreviewPrivacy.isVanishModeMessage(userInfo),
                )
            ) {
                return copyFromResolver(userInfo, suppliedTitle, suppliedBody, genericBody)
                    ?: ResolvedContent(
                        suppliedTitle,
                        ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody),
                    )
            }

            val encryptedContent = userInfo["encryptedContent"] as? String
            SharedChatDecryptor.decrypt(encryptedContent.orEmpty(), conversationId)?.let { plain ->
                return applyPreviewText(plain, userInfo, suppliedTitle)
            }

            val messageId = userInfo["messageId"] as? String
            if (!messageId.isNullOrBlank()) {
                val fetched = runCatching {
                    val snapshot = FirebaseFirestore.getInstance().collection("conversations").document(conversationId)
                        .collection("messages").document(messageId).get().await()
                    if (ChatPreviewPrivacy.isVanishModeMessage(snapshot.data ?: emptyMap())) null
                    else snapshot.getString("content")
                }.getOrNull()
                SharedChatDecryptor.decrypt(fetched.orEmpty(), conversationId)?.let { plain ->
                    return applyPreviewText(plain, userInfo, suppliedTitle)
                }
            }
            return copyFromResolver(userInfo, suppliedTitle, suppliedBody, genericBody)
                ?: ResolvedContent(
                    suppliedTitle,
                    ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody),
                )
        }

        // Data-only FCM (sin APNs loc-key): localizar con NotificationCopyResolver.
        NotificationPresentationCoordinator.notificationFromPush(userInfo)?.let { mapped ->
            val copy = NotificationCopyResolver.resolve(mapped)
            val resolvedTitle = copy.title.ifBlank { suppliedTitle }
            val resolvedBody = copy.body?.takeIf { it.isNotBlank() }
                ?: ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody)
            return ResolvedContent(resolvedTitle, resolvedBody)
        }

        return ResolvedContent(
            suppliedTitle,
            ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody),
        )
    }

    private fun copyFromResolver(
        userInfo: Map<String, Any?>,
        suppliedTitle: String,
        suppliedBody: String,
        genericBody: String,
    ): ResolvedContent? {
        val mapped = NotificationPresentationCoordinator.notificationFromPush(userInfo) ?: return null
        val copy = NotificationCopyResolver.resolve(mapped)
        val resolvedTitle = copy.title.ifBlank { suppliedTitle }
        val resolvedBody = copy.body?.takeIf { it.isNotBlank() }
            ?: ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody)
        return ResolvedContent(resolvedTitle, resolvedBody)
    }

    private fun applyPreviewText(
        text: String,
        userInfo: Map<String, Any?>,
        fallbackTitle: String,
    ): ResolvedContent {
        val trimmed = ChatTextMarkup.plainText(text, hidesSpoilers = true).trim()
        if (trimmed.isEmpty()) return ResolvedContent(fallbackTitle, trimmed)
        val title = (userInfo["senderUsername"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle
        val body = if (trimmed.length > 200) trimmed.take(199) + "…" else trimmed
        return ResolvedContent(title, body)
    }

    /** ≡ resolveChatReactionPreview */
    private suspend fun resolveChatReactionPreview(
        suppliedTitle: String,
        suppliedBody: String,
        userInfo: Map<String, Any?>,
    ): ResolvedContent? {
        if (userInfo["isReactionPlural"] as? String == "1") return null
        if ((userInfo["messageType"] as? String) != "text") return null
        val conversationId = userInfo["conversationId"] as? String ?: return null
        val emoji = (userInfo["reactionEmoji"] as? String)?.trim().orEmpty()
        if (emoji.isEmpty()) return null
        if (!ChatPreviewPrivacy.shouldRevealPreview(
                conversationId,
                ChatPreviewPrivacy.isVanishModeMessage(userInfo),
            )
        ) {
            return null
        }
        val title = (userInfo["senderUsername"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: suppliedTitle

        suspend fun quoted(plain: String): ResolvedContent {
            val trimmed = ChatTextMarkup.plainText(plain, hidesSpoilers = true).trim()
            if (trimmed.isEmpty()) return ResolvedContent(title, suppliedBody)
            val quoted = if (trimmed.length > 120) trimmed.take(119) + "…" else trimmed
            val body = getString(R.string.notification_chat_reaction_single_quoted, emoji, quoted)
            return ResolvedContent(title, body)
        }

        val embedded = userInfo["encryptedContent"] as? String
        SharedChatDecryptor.decrypt(embedded.orEmpty(), conversationId)?.let { return quoted(it) }

        val messageId = userInfo["messageId"] as? String ?: return null
        val fetched = runCatching {
            val snapshot = FirebaseFirestore.getInstance().collection("conversations").document(conversationId)
                .collection("messages").document(messageId).get().await()
            if (ChatPreviewPrivacy.isVanishModeMessage(snapshot.data ?: emptyMap())) null
            else snapshot.getString("content")
        }.getOrNull()
        SharedChatDecryptor.decrypt(fetched.orEmpty(), conversationId)?.let { return quoted(it) }
        return null
    }

    companion object {
        const val CHANNEL_ID = "moments_default"
        const val EXTRA_FROM_PUSH = "from_push"
        private const val TAG = "MomentsFCM"
    }
}

/** Keeps the generic iOS fallback when delivery contains an encrypted payload. */
internal object ChatSystemNotificationPreviewContract {
    fun safeFallback(suppliedBody: String, genericBody: String): String =
        if (looksLikeEncryptedPayload(suppliedBody)) genericBody else suppliedBody

    private fun looksLikeEncryptedPayload(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length >= 24 && trimmed.all { it.isLetterOrDigit() || it in "+/=_-" }
    }
}
