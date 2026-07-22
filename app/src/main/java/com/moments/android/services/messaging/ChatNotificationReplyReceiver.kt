package com.moments.android.services.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inline reply desde notificación FCM (equivalente Android de INSendMessageIntent / bubble reply).
 */
class ChatNotificationReplyReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChatCommunicationNotificationService.ACTION_REPLY) return
        val conversationId = intent.getStringExtra(ChatCommunicationNotificationService.EXTRA_CONVERSATION_ID)
            ?: return
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = results.getCharSequence(ChatCommunicationNotificationService.KEY_TEXT_REPLY)
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
            pendingResult.finish()
        }
    }
}
