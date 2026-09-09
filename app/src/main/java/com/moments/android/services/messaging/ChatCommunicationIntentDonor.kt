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
import com.moments.android.views.shared.ChatPreviewPrivacy
import java.util.concurrent.ConcurrentHashMap

/**
 * Contrato Android de conversaciones (People / MessagingStyle / shortcuts).
 * No copia el stacking de iOS: un shade por conversación.
 */

/** Paridad `ChatNotificationReply` (misma categoría/acción que iOS). */
object ChatNotificationReply {
    const val CATEGORY_IDENTIFIER = "MOMENTS_MESSAGE_REPLY"
    const val ACTION_IDENTIFIER = "MOMENTS_REPLY_ACTION"
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val KEY_TEXT_REPLY = "key_text_reply"
}

object ChatNotificationThread {
    fun conversationId(userInfo: Map<String, Any?>): String? {
        val type = (userInfo["type"] as? String)?.lowercase()
        val fromConversation = (userInfo["conversationId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val fromGroup = (userInfo["groupId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return if (type == "group_message") fromConversation ?: fromGroup else fromConversation
    }

    fun isChatMessage(type: String?) = type in setOf("message", "new_message", "group_message")

    fun isGroupConversationId(id: String?): Boolean = GroupChatScope.isGroup(id)

    fun isGroup(userInfo: Map<String, Any?>): Boolean {
        val type = (userInfo["type"] as? String)?.lowercase()
        val cid = conversationId(userInfo)
        return type == "group_message" || (cid != null && isGroupConversationId(cid))
    }

    /** Título de grupo para Conversations API — sin esto Android pinta 1:1. */
    fun resolvedGroupName(userInfo: Map<String, Any?>, fallbackTitle: String? = null): String? {
        if (!isGroup(userInfo)) return null
        val sender = (userInfo["senderUsername"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        (userInfo["groupName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val candidates = listOf(
            userInfo["title"] as? String,
            fallbackTitle,
        )
        for (raw in candidates) {
            val name = raw?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (sender != null && name.equals(sender, ignoreCase = true)) continue
            return name
        }
        return null
    }

    fun previewLabel(context: Context, messageType: String?): String = context.getString(when (messageType) {
        "image" -> R.string.chat_preview_photo
        "video" -> R.string.chat_preview_video
        "audio" -> R.string.chat_preview_audio
        "gif" -> R.string.chat_preview_gif
        "sticker" -> R.string.chat_preview_sticker
        "location" -> R.string.chat_preview_location
        "file" -> R.string.chat_preview_file
        "viewOnceImage" -> R.string.chat_preview_view_once_photo
        "viewOnceVideo" -> R.string.chat_preview_view_once_video
        "ephemeral" -> R.string.chat_preview_ephemeral
        "moment", "sharedMoment" -> R.string.chat_preview_shared_moment
        "sharedStory", "storyMention" -> R.string.chat_preview_shared_story
        "sharedProfile" -> R.string.chat_preview_shared_profile
        else -> R.string.notification_chat_summary_single
    })

    fun shadeKey(conversationId: String) = "conversation_$conversationId"
}

object ChatCommunicationIntentDonor {
    private const val SHORTCUT_PREFIX = "moments_chat_"
    private const val MAX_SHADE_MESSAGES = 7
    private const val EXTRA_SHADE_MESSAGE_IDS = "moments.shade.message_ids"
    private const val EXTRA_PREVIEW_VISIBLE = "moments.shade.preview_visible"
    private const val EXTRA_MESSAGE_COUNT = "moments.shade.message_count"

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
        conversationAvatarBitmap: Bitmap? = null,
        mediaUri: Uri? = null,
        includeReply: Boolean = true,
        notificationId: Int,
    ): NotificationCompat.Builder {
        initialize(context)
        val conversationId = ChatNotificationThread.conversationId(userInfo)
        val type = (userInfo["type"] as? String)?.lowercase()
        val isConversation = !conversationId.isNullOrBlank() && type in setOf(
            "message",
            "new_message",
            "group_message",
            "message_reaction",
            "chat_buzz",
        )
        val isChatMessage = ChatNotificationThread.isChatMessage(type)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_moments)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isConversation) {
            val convId = conversationId!!
            val isGroup = ChatNotificationThread.isGroup(userInfo)
            val groupTitle = ChatNotificationThread.resolvedGroupName(userInfo, fallbackTitle)
                ?: context.getString(R.string.notification_group_untitled).takeIf { isGroup }
            val senderName = (userInfo["senderUsername"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: fallbackTitle
            val person = Person.Builder()
                .setKey((userInfo["senderId"] as? String) ?: convId)
                .setName(senderName)
                .setImportant(true)
                .apply { avatarBitmap?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()
            val self = Person.Builder()
                .setName(context.getString(R.string.notification_chat_self))
                .build()
            val previewOn = ChatPreviewPrivacy.shouldRevealPreview(
                convId,
                ChatPreviewPrivacy.isVanishModeMessage(userInfo),
            )
            val safeBody = if (previewOn || !isChatMessage) body else context.getString(R.string.notification_chat_summary_single)
            val incoming = NotificationCompat.MessagingStyle.Message(safeBody, System.currentTimeMillis(), person)
            if (previewOn && mediaUri != null) incoming.setData("image/jpeg", mediaUri)
            val (style, shadeIds, count) = conversationStyle(
                context = context,
                notificationId = notificationId,
                self = self,
                incoming = incoming,
                incomingMessageId = userInfo["messageId"] as? String,
                previewOn = previewOn,
                isGroup = isGroup,
                groupTitle = groupTitle,
                summarizeHidden = isChatMessage,
                mention = if (userInfo["isMention"] == "1" || userInfo["isMention"] == true)
                    context.getString(R.string.groups_notification_mention, senderName) else null,
            )
            val conversationIcon = conversationAvatarBitmap ?: avatarBitmap
            donateIncomingMessage(
                conversationId = convId,
                messageId = userInfo["messageId"] as? String ?: convId,
                senderId = userInfo["senderId"] as? String ?: "",
                senderUsername = senderName,
                senderProfileImageUrl = userInfo["senderProfileImage"] as? String,
                messagePreview = safeBody,
                avatarBitmap = conversationIcon,
                senderAvatarBitmap = avatarBitmap,
                conversationLabel = groupTitle,
                isGroup = isGroup,
            )
            // Título explícito: OEMs (MIUI) a veces ignoran solo MessagingStyle.
            builder.setContentTitle(if (isGroup) groupTitle ?: senderName else senderName)
                .setContentText(body)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setShortcutId(SHORTCUT_PREFIX + convId)
                .addPerson(person)
            conversationIcon?.let { builder.setLargeIcon(it) }
            builder.extras.putStringArrayList(EXTRA_SHADE_MESSAGE_IDS, ArrayList(shadeIds))
            builder.extras.putBoolean(EXTRA_PREVIEW_VISIBLE, previewOn)
            builder.extras.putInt(EXTRA_MESSAGE_COUNT, count)
            if (!previewOn) builder.setContentText(style.messages.lastOrNull()?.text)
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
    private data class ConversationStyle(
        val style: NotificationCompat.MessagingStyle,
        val messageIds: List<String>,
        val count: Int,
    )

    private fun conversationStyle(
        context: Context,
        notificationId: Int,
        self: Person,
        incoming: NotificationCompat.MessagingStyle.Message,
        incomingMessageId: String?,
        previewOn: Boolean,
        isGroup: Boolean,
        groupTitle: String?,
        summarizeHidden: Boolean,
        mention: String?,
    ): ConversationStyle {
        val existing = NotificationManagerCompat.from(context).activeNotifications
            .firstOrNull { it.id == notificationId }?.notification
        val previousIds = existing?.extras?.getStringArrayList(EXTRA_SHADE_MESSAGE_IDS).orEmpty()
        val recovered = existing?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        }?.messages.orEmpty()
        val alreadyPosted = !incomingMessageId.isNullOrBlank() && incomingMessageId in previousIds
        val priorCount = existing?.extras?.getInt(EXTRA_MESSAGE_COUNT, recovered.size) ?: 0
        val count = (priorCount + if (alreadyPosted) 0 else 1).coerceAtLeast(1)
        val style = NotificationCompat.MessagingStyle(self).setGroupConversation(isGroup)
        if (isGroup) style.conversationTitle = groupTitle ?: context.getString(R.string.notification_group_untitled)
        val ids = (previousIds + listOfNotNull(incomingMessageId)).distinct().takeLast(100)

        if (!previewOn) {
            val summary = if (!summarizeHidden) incoming.text.toString()
                else if (count > 1) context.getString(R.string.notification_chat_summary_multiple, count.toString())
                else context.getString(R.string.notification_chat_summary_single)
            val text = if (mention != null) "$summary\n$mention" else summary
            style.addMessage(NotificationCompat.MessagingStyle.Message(text, incoming.timestamp, incoming.person))
        } else {
            // A previous hidden summary (or legacy notification) is never restored as chat history.
            val previous = if (existing?.extras?.getBoolean(EXTRA_PREVIEW_VISIBLE) == true) recovered else emptyList()
            val messages = if (alreadyPosted && previous.isNotEmpty()) previous else previous + incoming
            messages.takeLast(MAX_SHADE_MESSAGES).forEach { style.addMessage(it) }
        }
        return ConversationStyle(style, ids, count)
    }

    fun donateIncomingMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        senderUsername: String,
        senderProfileImageUrl: String?,
        messagePreview: String?,
        avatarBitmap: Bitmap? = null,
        conversationLabel: String? = null,
        isGroup: Boolean = false,
        senderAvatarBitmap: Bitmap? = avatarBitmap,
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
            .apply { senderAvatarBitmap?.let { setIcon(IconCompat.createWithBitmap(it)) } }
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
        val shortcutLabel = conversationLabel?.takeIf { it.isNotBlank() }
            ?: if (isGroup) ctx.getString(R.string.notification_group_untitled) else senderUsername
        val shortcutBuilder = ShortcutInfoCompat.Builder(ctx, shortcutId)
            .setShortLabel(shortcutLabel.take(24))
            .setLongLabel(shortcutLabel)
            .setLocusId(LocusIdCompat(conversationId))
            .setLongLived(true)
            .setCategories(setOf("android.shortcut.conversation"))
            .setIntent(intent)
        if (isGroup) {
            shortcutBuilder.setPersons(arrayOf(person))
        } else {
            shortcutBuilder.setPerson(person)
        }
        avatarBitmap?.let { shortcutBuilder.setIcon(IconCompat.createWithBitmap(it)) }
        val shortcut = shortcutBuilder.build()

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
