package com.moments.android.notifications.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.moments.android.MainActivity
import com.moments.android.R
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.views.shared.ChatPreviewPrivacy
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.services.messaging.ChatCommunicationNotificationService
import com.moments.android.services.messaging.MessageIngestService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Port de AppDelegate.swift push setup (MessagingDelegate + UNUserNotificationCenterDelegate).
 *
 * iOS AppDelegate responsibilities mapped here:
 * - FCM token refresh → [FCMTokenService]
 * - Foreground/background push → [NotificationPresentationCoordinator]
 * - Silent push badge refresh → [NotificationBadgeService]
 * - Message delivery + ingest → [ChatService] / [MessageIngestService]
 * - Notification tap / deep link → [NotificationNavigationService] via MainActivity intent
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

        if (NotificationPresentationCoordinator.isSilentPush(userInfo)) {
            NotificationBadgeService.refreshAllCounts()
            return
        }

        NotificationPresentationCoordinator.present(userInfo, NotificationPresentationSource.PUSH)
        scope.launch { showSystemNotificationIfNeeded(message, userInfo) }
        NotificationBadgeService.setupListeners()
    }

    private suspend fun handleBackgroundSideEffects(userInfo: Map<String, Any?>) {
        val conversationId = userInfo["conversationId"] as? String
        val messageId = userInfo["messageId"] as? String
        if (!conversationId.isNullOrBlank() && !messageId.isNullOrBlank()) {
            ChatService.markMessageAsDeliveredFromNotification(conversationId, messageId)
            MessageIngestService.ingest(userInfo)
        }
        if (NotificationPresentationCoordinator.isSilentPush(userInfo) ||
            userInfo["content-available"] == "1" || userInfo["content-available"] == 1
        ) {
            NotificationBadgeService.refreshAllCounts()
        }
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
        val body = resolveSystemNotificationBody(suppliedBody, userInfo)

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

        val notification = ChatCommunicationNotificationService.buildMessagePushNotification(
            context = this,
            userInfo = userInfo,
            fallbackTitle = title,
            body = body,
            channelId = CHANNEL_ID,
            contentIntent = pendingIntent,
        ).build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(message.messageId?.hashCode() ?: body.hashCode(), notification) }
            .onFailure { Log.w(TAG, "Failed to post notification (permission denied?): $it") }
    }

    /** Android counterpart of iOS NotificationService.resolveMessagePreview. */
    private suspend fun resolveSystemNotificationBody(
        suppliedBody: String,
        userInfo: Map<String, Any?>,
    ): String {
        val genericBody = getString(R.string.notification_message_single_default)
        val type = (userInfo["type"] as? String)?.lowercase()
        val conversationId = userInfo["conversationId"] as? String
        if ((type != "message" && type != "new_message") || conversationId.isNullOrBlank() ||
            (userInfo["messageType"] as? String) != "text" ||
            !ChatPreviewPrivacy.shouldRevealPreview(
                conversationId,
                ChatPreviewPrivacy.isVanishModeMessage(userInfo),
            )
        ) {
            return ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody)
        }

        val encryptedContent = userInfo["encryptedContent"] as? String
        decryptPreview(encryptedContent, conversationId)?.let { return it }

        val messageId = userInfo["messageId"] as? String
        if (!messageId.isNullOrBlank()) {
            val fetched = runCatching {
                val snapshot = FirebaseFirestore.getInstance().collection("conversations").document(conversationId)
                    .collection("messages").document(messageId).get().await()
                if (ChatPreviewPrivacy.isVanishModeMessage(snapshot.data ?: emptyMap())) null
                else snapshot.getString("content")
            }.getOrNull()
            decryptPreview(fetched, conversationId)?.let { return it }
        }
        return ChatSystemNotificationPreviewContract.safeFallback(suppliedBody, genericBody)
    }

    private suspend fun decryptPreview(ciphertext: String?, conversationId: String): String? {
        val encrypted = ciphertext?.trim().orEmpty()
        if (encrypted.isEmpty()) return null
        val decrypted = EncryptionService.decryptChatMessage(encrypted, conversationId)?.trim().orEmpty()
        return decrypted.takeIf { it.isNotEmpty() && it != encrypted }?.take(200)
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
