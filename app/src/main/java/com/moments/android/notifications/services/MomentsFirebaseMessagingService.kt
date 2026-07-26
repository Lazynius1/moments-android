package com.moments.android.notifications.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.MainActivity
import com.moments.android.R
import com.moments.android.services.messaging.ChatCommunicationIntentDonor
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.services.messaging.SharedChatDecryptor
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.shared.ChatPreviewPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
        ensureDefaultChannel()
        val title = message.notification?.title
            ?: userInfo["senderUsername"] as? String
            ?: getString(R.string.app_name)
        val suppliedBody = message.notification?.body
            ?: userInfo["body"] as? String
            ?: userInfo["reaction"] as? String
            ?: getString(R.string.notification_message_single_default)

        // ≡ resolveMessagePreview (+ reaction)
        val resolved = resolveSystemNotificationContent(title, suppliedBody, userInfo)
        val body = resolved.body
        val resolvedTitle = resolved.title

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            userInfo.forEach { (k, v) -> putExtra(k, v?.toString()) }
            putExtra(EXTRA_FROM_PUSH, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            userInfo.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = ChatCommunicationIntentDonor.buildMessagePushNotification(
            context = this,
            userInfo = userInfo,
            fallbackTitle = resolvedTitle,
            body = body,
            channelId = CHANNEL_ID,
            contentIntent = pendingIntent,
        )

        // ≡ resolveNotificationAttachment → BigPicture / largeIcon
        resolveNotificationBitmap(userInfo)?.let { bitmap ->
            builder.setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?),
                )
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            manager.notify(message.messageId?.hashCode() ?: body.hashCode(), builder.build())
        }.onFailure { Log.w(TAG, "Failed to post notification: $it") }
    }

    private data class ResolvedContent(val title: String, val body: String)

    /** ≡ resolveMessagePreview + resolveChatReactionPreview */
    private suspend fun resolveSystemNotificationContent(
        suppliedTitle: String,
        suppliedBody: String,
        userInfo: Map<String, Any?>,
    ): ResolvedContent {
        val genericBody = getString(R.string.notification_message_single_default)
        val type = (userInfo["type"] as? String)?.lowercase()

        if (type == "message_reaction") {
            return resolveChatReactionPreview(suppliedTitle, suppliedBody, userInfo)
                ?: ResolvedContent(suppliedTitle, ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody))
        }

        val conversationId = userInfo["conversationId"] as? String
        if ((type != "message" && type != "new_message") || conversationId.isNullOrBlank() ||
            (userInfo["messageType"] as? String) != "text" ||
            !ChatPreviewPrivacy.shouldRevealPreview(
                conversationId,
                ChatPreviewPrivacy.isVanishModeMessage(userInfo),
            )
        ) {
            return ResolvedContent(
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
        return ResolvedContent(
            suppliedTitle,
            ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody),
        )
    }

    private fun applyPreviewText(
        text: String,
        userInfo: Map<String, Any?>,
        fallbackTitle: String,
    ): ResolvedContent {
        val trimmed = text.trim()
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
            val trimmed = plain.trim()
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

    /**
     * ≡ resolveNotificationAttachment — bitmap para BigPicture.
     * View-once nunca; chat image/video cifrado; gif/sticker URL; else mediaUrl/avatar.
     */
    private suspend fun resolveNotificationBitmap(userInfo: Map<String, Any?>): Bitmap? {
        val notificationType = (userInfo["type"] as? String)?.lowercase()
        val isChatMessage = notificationType == "new_message" || notificationType == "message"
        val messageType = userInfo["messageType"] as? String
        val viewOnce = setOf("viewOnceImage", "viewOnceVideo", "ephemeral")

        if (isChatMessage && messageType != null && messageType !in viewOnce) {
            val conversationId = userInfo["conversationId"] as? String ?: return null
            if (!ChatPreviewPrivacy.shouldRevealPreview(
                    conversationId,
                    ChatPreviewPrivacy.isVanishModeMessage(userInfo),
                )
            ) {
                return null
            }
            if (messageType == "image" || messageType == "video") {
                val messageId = userInfo["messageId"] as? String ?: return null
                return resolveEncryptedMediaBitmap(
                    conversationId = conversationId,
                    messageId = messageId,
                    allowFullMediaFallback = messageType == "image",
                )
            }
            if (messageType == "gif" || messageType == "sticker") {
                val url = userInfo["mediaUrl"] as? String ?: return null
                return downloadPublicBitmap(url)
            }
            return null
        }

        (userInfo["mediaUrl"] as? String)?.takeIf { it.isNotBlank() }?.let { return downloadPublicBitmap(it) }
        (userInfo["senderProfileImage"] as? String)?.takeIf { it.isNotBlank() }?.let { return downloadPublicBitmap(it) }
        return null
    }

    /** ≡ resolveEncryptedMediaAttachment */
    private suspend fun resolveEncryptedMediaBitmap(
        conversationId: String,
        messageId: String,
        allowFullMediaFallback: Boolean,
    ): Bitmap? {
        val data = runCatching {
            FirebaseFirestore.getInstance().collection("conversations").document(conversationId)
                .collection("messages").document(messageId).get().await().data
        }.getOrNull() ?: return null

        val thumbPath = data["thumbnailObjectPath"] as? String
        val thumbMeta = SharedChatDecryptor.MediaMetadata.fromMap(
            data["thumbnailEncryption"] as? Map<String, Any?>,
        )
        if (!thumbPath.isNullOrBlank() && thumbMeta != null) {
            downloadAndDecryptBitmap(thumbPath, thumbMeta, conversationId, messageId)?.let { return it }
        }

        if (allowFullMediaFallback) {
            val mediaPath = data["mediaObjectPath"] as? String
            val mediaMeta = SharedChatDecryptor.MediaMetadata.fromMap(
                data["mediaEncryption"] as? Map<String, Any?>,
            )
            if (!mediaPath.isNullOrBlank() && mediaMeta != null &&
                mediaMeta.plaintextSize <= MAX_ATTACHMENT_BYTES
            ) {
                return downloadAndDecryptBitmap(mediaPath, mediaMeta, conversationId, messageId)
            }
        }
        return null
    }

    private suspend fun downloadAndDecryptBitmap(
        objectPath: String,
        metadata: SharedChatDecryptor.MediaMetadata,
        conversationId: String,
        messageId: String,
    ): Bitmap? {
        val maxSize = minOf(
            maxOf(metadata.plaintextSize + 256 * 1024, 2L * 1024 * 1024),
            MAX_ATTACHMENT_BYTES + 256 * 1024,
        )
        val encrypted = runCatching {
            FirebaseStorage.getInstance().reference.child(objectPath).getBytes(maxSize).await()
        }.getOrNull() ?: return null
        val decrypted = SharedChatDecryptor.decryptMedia(encrypted, metadata, conversationId, messageId)
            ?: return null
        return BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
    }

    private suspend fun downloadPublicBitmap(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun ensureDefaultChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_default_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notification_channel_default_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "moments_default"
        const val EXTRA_FROM_PUSH = "from_push"
        private const val TAG = "MomentsFCM"
        /** ≡ maxAttachmentBytes NSE */
        private const val MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024
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
