package com.moments.android.services.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inline reply desde notificación FCM (≡ AppDelegate.handleQuickReply / ChatNotificationReply).
 */
class ChatNotificationReplyReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChatNotificationReply.ACTION_IDENTIFIER) return
        val conversationId = intent.getStringExtra(ChatNotificationReply.EXTRA_CONVERSATION_ID)
            ?: return
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = results.getCharSequence(ChatNotificationReply.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (replyText.isEmpty()) return
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val pendingResult = goAsync()
        scope.launch {
            runCatching {
                ChatService.sendTextMessage(
                    conversationId = conversationId,
                    senderId = senderId,
                    content = replyText,
                )
            }
            // ≡ NotificationBadgeService.shared.setupListeners() tras quick reply iOS
            NotificationBadgeService.setupListeners()
            pendingResult.finish()
        }
    }
}
