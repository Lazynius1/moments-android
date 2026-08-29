package com.moments.android.services.messaging

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.moments.android.MainActivity
import com.moments.android.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de Shared/ChatCommunicationIntentDonor.swift (+ ChatNotificationReply).
 *
 * iOS: INSendMessageIntent. Android: Shortcut dinámico + Person/LocusId + MessagingStyle.
 */

/** Paridad `ChatNotificationReply` (misma categoría/acción que iOS). */
object ChatNotificationReply {
    const val CATEGORY_IDENTIFIER = "MOMENTS_MESSAGE_REPLY"
    const val ACTION_IDENTIFIER = "MOMENTS_REPLY_ACTION"
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val KEY_TEXT_REPLY = "key_text_reply"
}

object ChatCommunicationIntentDonor {
    private const val SHORTCUT_PREFIX = "moments_chat_"
    private const val SELF_NAME = "You"
    private const val MAX_SHADE_MESSAGES = 7
    private const val EXTRA_SHADE_MESSAGE_IDS = "moments.shade.message_ids"

    data class DonatedConversation(
        val conversationId: String,
        val messageId: String,
        val senderId: String,
        val senderUsername: String,
        val senderProfileImageUrl: String?,
        val messagePreview: String?,
    )

    private var appContext: Context? = null
    private val lastDonationByConversation = ConcurrentHashMap<String, DonatedConversation>()
    private val shadeHistory = ConcurrentHashMap<String, MutableList<NotificationCompat.MessagingStyle.Message>>()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    fun lastDonation(conversationId: String): DonatedConversation? =
        lastDonationByConversation[conversationId]

    fun personFor(conversationId: String, avatarBitmap: Bitmap? = null): Person? {
        val d = lastDonationByConversation[conversationId] ?: return null
        val builder = Person.Builder()
            .setKey(d.senderId)
            .setName(d.senderUsername)
            .setImportant(true)
        avatarBitmap?.let { builder.setIcon(IconCompat.createWithBitmap(it)) }
        return builder.build()
    }

    /**
     * Equivalente Android de `applyCommunicationIntent` + tray FCM:
     * MessagingStyle + shortcut + RemoteInput reply. 1:1 sin conversationTitle.
     */
    fun buildMessagePushNotification(
        context: Context,
        userInfo: Map<String, Any?>,
        fallbackTitle: String,
        body: String,
        channelId: String,
        contentIntent: PendingIntent,
        avatarBitmap: Bitmap? = null,
        mediaUri: Uri? = null,
        includeReply: Boolean = true,
        notificationId: Int,
    ): NotificationCompat.Builder {
        initialize(context)
        val conversationId = userInfo["conversationId"] as? String
        val type = (userInfo["type"] as? String)?.lowercase()
        val isConversation = !conversationId.isNullOrBlank() && type in setOf(
            "message",
            "new_message",
            "message_reaction",
            "chat_buzz",
        )
        val isChatMessage = type == "message" || type == "new_message"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_moments)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isConversation) {
            val convId = conversationId!!
            val senderName = (userInfo["senderUsername"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: fallbackTitle
            val person = Person.Builder()
                .setKey((userInfo["senderId"] as? String) ?: convId)
                .setName(senderName)
                .setImportant(true)
                .apply { avatarBitmap?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()
            val self = Person.Builder().setName(SELF_NAME).build()
            val incoming = NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), person)
            if (mediaUri != null) {
                incoming.setData("image/jpeg", mediaUri)
            }
            val (style, shadeIds) = conversationStyle(
                context = context,
                notificationId = notificationId,
                conversationId = convId,
                self = self,
                incoming = incoming,
                incomingMessageId = userInfo["messageId"] as? String,
            )
            donateIncomingMessage(
                conversationId = convId,
                messageId = userInfo["messageId"] as? String ?: convId,
                senderId = userInfo["senderId"] as? String ?: "",
                senderUsername = senderName,
                senderProfileImageUrl = userInfo["senderProfileImage"] as? String,
                messagePreview = body,
                avatarBitmap = avatarBitmap,
            )
            builder.setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setShortcutId(SHORTCUT_PREFIX + convId)
                .addPerson(person)
            builder.extras.putStringArrayList(EXTRA_SHADE_MESSAGE_IDS, ArrayList(shadeIds))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setLocusId(LocusIdCompat(convId))
            }
            if (includeReply && isChatMessage) {
                builder.addAction(createReplyAction(context, convId))
            }
        } else {
            builder.setContentTitle(fallbackTitle)
                .setContentText(body)
        }
        return builder
    }

    private fun createReplyAction(context: Context, conversationId: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(ChatNotificationReply.KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.notification_action_placeholder))
            .build()
        val replyIntent = Intent(context, ChatNotificationReplyReceiver::class.java).apply {
            action = ChatNotificationReply.ACTION_IDENTIFIER
            putExtra(ChatNotificationReply.EXTRA_CONVERSATION_ID, conversationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            conversationId.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.sym_action_chat,
            context.getString(R.string.notification_action_reply),
            pendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    /**
     * Android shade: al expandir se ven los mensajes del hilo (MessagingStyle),
     * no solo el último. iOS no acumula; aquí sí. Fuente: notificación activa.
     */
    private fun conversationStyle(
        context: Context,
        notificationId: Int,
        conversationId: String,
        self: Person,
        incoming: NotificationCompat.MessagingStyle.Message,
        incomingMessageId: String?,
    ): Pair<NotificationCompat.MessagingStyle, List<String>> {
        val existing = NotificationManagerCompat.from(context).activeNotifications
            .firstOrNull { it.id == notificationId }
        if (existing == null) shadeHistory.remove(conversationId)
        val previousIds = existing?.notification?.extras
            ?.getStringArrayList(EXTRA_SHADE_MESSAGE_IDS)
            .orEmpty()
        val recovered = existing?.notification?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        }?.messages.orEmpty()
        val fromMemory = shadeHistory[conversationId].orEmpty()
        val priorMessages = when {
            recovered.isNotEmpty() -> recovered
            existing != null && fromMemory.isNotEmpty() -> fromMemory
            else -> emptyList()
        }
        val alreadyPosted = !incomingMessageId.isNullOrBlank() && incomingMessageId in previousIds
        val mergedMessages = if (alreadyPosted) {
            priorMessages.ifEmpty { listOf(incoming) }
        } else {
            priorMessages + incoming
        }.takeLast(MAX_SHADE_MESSAGES)
        shadeHistory[conversationId] = mergedMessages.toMutableList()
        val mergedIds = buildList {
            if (alreadyPosted) addAll(previousIds.takeLast(MAX_SHADE_MESSAGES))
            else {
                addAll(previousIds)
                incomingMessageId?.takeIf { it.isNotBlank() }?.let { add(it) }
                while (size > MAX_SHADE_MESSAGES) removeAt(0)
            }
        }
        val style = NotificationCompat.MessagingStyle(self).setGroupConversation(false)
        mergedMessages.forEach { style.addMessage(it) }
        return style to mergedIds
    }

    fun donateIncomingMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        senderUsername: String,
        senderProfileImageUrl: String?,
        messagePreview: String?,
        avatarBitmap: Bitmap? = null,
    ) {
        val ctx = appContext ?: return
        val donation = DonatedConversation(
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            senderUsername = senderUsername,
            senderProfileImageUrl = senderProfileImageUrl,
            messagePreview = messagePreview,
        )
        lastDonationByConversation[conversationId] = donation

        val person = Person.Builder()
            .setKey(senderId)
            .setName(senderUsername)
            .setImportant(true)
            .apply { avatarBitmap?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("moments://chat/$conversationId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
            putExtra("messageId", messageId)
            putExtra("senderId", senderId)
        }

        val shortcutId = SHORTCUT_PREFIX + conversationId
        val shortcutBuilder = ShortcutInfoCompat.Builder(ctx, shortcutId)
            .setShortLabel(senderUsername.take(24))
            .setLongLabel(senderUsername)
            .setLocusId(LocusIdCompat(conversationId))
            .setPerson(person)
            .setLongLived(true)
            .setCategories(setOf("android.shortcut.conversation"))
            .setIntent(intent)
        avatarBitmap?.let { shortcutBuilder.setIcon(IconCompat.createWithBitmap(it)) }
        val shortcut = shortcutBuilder.build()

        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcut)
        }
    }

    fun removeConversationShortcut(conversationId: String) {
        val ctx = appContext ?: return
        lastDonationByConversation.remove(conversationId)
        shadeHistory.remove(conversationId)
        runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(ctx, listOf(SHORTCUT_PREFIX + conversationId))
        }
    }
}
