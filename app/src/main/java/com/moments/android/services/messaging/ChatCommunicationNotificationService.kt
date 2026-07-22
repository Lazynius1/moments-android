package com.moments.android.services.messaging

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
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
 * Port de ChatCommunicationNotificationService.swift + ChatCommunicationIntentDonor (Shared).
 *
 * iOS dona INSendMessageIntent. Android equivalente: Shortcut dinámico + Person/LocusId
 * para Communication-style notifications / bubble / reply.
 */
object ChatCommunicationNotificationService {

    const val CATEGORY_MESSAGE_REPLY = "MOMENTS_MESSAGE_REPLY"
    const val ACTION_REPLY = "MOMENTS_REPLY_ACTION"
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val KEY_TEXT_REPLY = "key_text_reply"
    private const val SHORTCUT_PREFIX = "moments_chat_"

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

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    fun lastDonation(conversationId: String): DonatedConversation? =
        lastDonationByConversation[conversationId]

    fun personFor(conversationId: String): Person? {
        val d = lastDonationByConversation[conversationId] ?: return null
        return Person.Builder()
            .setKey(d.senderId)
            .setName(d.senderUsername)
            .setImportant(true)
            .build()
    }

    /** Helper para NotificationCompat.MessagingStyle (paridad Communication notifications). */
    fun messagingStyleFor(conversationId: String, selfName: String = "You"): NotificationCompat.MessagingStyle? {
        val donation = lastDonationByConversation[conversationId] ?: return null
        val sender = personFor(conversationId) ?: return null
        val style = NotificationCompat.MessagingStyle(
            Person.Builder().setName(selfName).build(),
        ).setConversationTitle(donation.senderUsername)
        donation.messagePreview?.let { style.addMessage(it, System.currentTimeMillis(), sender) }
        return style
    }

    /**
     * Notificación system tray para push de chat: MessagingStyle + shortcut + RemoteInput reply.
     * Usado por [com.moments.android.notifications.services.MomentsFirebaseMessagingService].
     */
    fun buildMessagePushNotification(
        context: Context,
        userInfo: Map<String, Any?>,
        fallbackTitle: String,
        body: String,
        channelId: String,
        contentIntent: PendingIntent,
    ): NotificationCompat.Builder {
        val conversationId = userInfo["conversationId"] as? String
        val type = (userInfo["type"] as? String)?.lowercase()
        val isChatMessage = (type == "message" || type == "new_message") && !conversationId.isNullOrBlank()

        if (isChatMessage) {
            donateFromPush(userInfo, body)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_moments)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (isChatMessage) {
            val convId = conversationId!!
            val person = personFor(convId)
            val style = messagingStyleFor(convId) ?: run {
                val sender = person ?: Person.Builder().setName(fallbackTitle).build()
                NotificationCompat.MessagingStyle(Person.Builder().setName(selfName).build())
                    .setConversationTitle(fallbackTitle)
                    .addMessage(body, System.currentTimeMillis(), sender)
            }
            builder.setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setShortcutId(SHORTCUT_PREFIX + convId)
            person?.let { builder.addPerson(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setLocusId(LocusIdCompat(convId))
            }
            builder.setContentTitle(fallbackTitle)
                .setContentText(body)
                .addAction(createReplyAction(context, convId))
        } else {
            builder.setContentTitle(fallbackTitle)
                .setContentText(body)
        }
        return builder
    }

    private const val selfName = "You"

    private fun createReplyAction(context: Context, conversationId: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()
        val replyIntent = Intent(context, ChatNotificationReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            conversationId.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.sym_action_chat,
            "Reply",
            pendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    fun donateFromPush(userInfo: Map<String, Any?>, previewBody: String?) {
        val type = (userInfo["type"] as? String)?.lowercase() ?: return
        if (type != "message" && type != "new_message") return
        val conversationId = userInfo["conversationId"] as? String ?: return
        val messageId = userInfo["messageId"] as? String ?: return
        val senderId = userInfo["senderId"] as? String ?: return
        val senderUsername = (userInfo["senderUsername"] as? String)?.takeIf { it.isNotBlank() } ?: "Moments"
        val avatarUrl = userInfo["senderProfileImage"] as? String
        val preview = previewBody?.trim()?.takeIf { it.isNotEmpty() }

        donateIncomingMessage(
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            senderUsername = senderUsername,
            senderProfileImageUrl = avatarUrl,
            messagePreview = preview,
        )
    }

    fun donateIncomingMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        senderUsername: String,
        senderProfileImageUrl: String?,
        messagePreview: String?,
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
        val shortcut = ShortcutInfoCompat.Builder(ctx, shortcutId)
            .setShortLabel(senderUsername.take(24))
            .setLongLabel(senderUsername)
            .setLocusId(LocusIdCompat(conversationId))
            .setPerson(person)
            .setLongLived(true)
            .setCategories(setOf("android.shortcut.conversation"))
            .setIntent(intent)
            .setIcon(IconCompat.createWithResource(ctx, android.R.drawable.sym_action_chat))
            .build()

        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcut)
        }
    }

    fun removeConversationShortcut(conversationId: String) {
        val ctx = appContext ?: return
        lastDonationByConversation.remove(conversationId)
        runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(ctx, listOf(SHORTCUT_PREFIX + conversationId))
        }
    }
}
