package com.moments.android.notifications.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.MainActivity
import com.moments.android.R
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.messaging.ChatCommunicationIntentDonor
import com.moments.android.services.messaging.SharedChatDecryptor
import com.moments.android.services.storage.StoragePathBuilder
import com.moments.android.views.shared.ChatPreviewPrivacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bandeja de sistema ≡ NSE iOS: conversation API para DMs, largeIcon+contenido para social.
 * Nunca BigPicture del avatar.
 */
object NotificationShadePoster {
    const val CHANNEL_MESSAGES = "moments_messages"
    const val CHANNEL_SOCIAL = "moments_social"
    const val CHANNEL_REMINDERS = "moments_reminders"
    const val CHANNEL_DEFAULT = MomentsFirebaseMessagingService.CHANNEL_ID

    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".notification.fileprovider"
    private const val MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024
    private val viewOnceTypes = setOf("viewOnceImage", "viewOnceVideo", "ephemeral")

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun upsert(id: String, name: Int, desc: Int, importance: Int) {
            val existing = manager.getNotificationChannel(id)
            if (existing != null) return
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(name), importance).apply {
                    description = context.getString(desc)
                },
            )
        }
        upsert(
            CHANNEL_MESSAGES,
            R.string.notification_channel_messages_name,
            R.string.notification_channel_messages_description,
            NotificationManager.IMPORTANCE_HIGH,
        )
        upsert(
            CHANNEL_SOCIAL,
            R.string.notification_channel_social_name,
            R.string.notification_channel_social_description,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        upsert(
            CHANNEL_REMINDERS,
            R.string.notification_channel_reminders_name,
            R.string.notification_channel_reminders_description,
            NotificationManager.IMPORTANCE_LOW,
        )
        if (manager.getNotificationChannel(CHANNEL_DEFAULT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DEFAULT,
                    context.getString(R.string.notification_channel_default_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_default_description)
                },
            )
        }
    }

    suspend fun post(
        context: Context,
        messageId: String?,
        userInfo: Map<String, Any?>,
        title: String,
        body: String,
    ) {
        ensureChannels(context)
        val type = (userInfo["type"] as? String)?.lowercase().orEmpty()
        val bitmaps = resolveBitmaps(userInfo)
        val channelId = channelIdFor(userInfo, type)
        val threadId = (userInfo["threadId"] as? String)?.takeIf { it.isNotBlank() }
            ?: defaultThreadId(userInfo, type)
        val collapseKey = (userInfo["collapseKey"] as? String)?.takeIf { it.isNotBlank() }
            ?: defaultCollapseKey(userInfo, type, messageId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            userInfo.forEach { (k, v) -> putExtra(k, v?.toString()) }
            putExtra(MomentsFirebaseMessagingService.EXTRA_FROM_PUSH, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            collapseKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (isConversationShade(type)) {
            val circularAvatar = bitmaps.avatar?.let { prepareAvatar(it) }
            val mediaUri = if (isChatDm(type)) {
                bitmaps.content?.let { cacheBitmapAsUri(context, it, messageId ?: collapseKey) }
                    ?.also { uri ->
                        runCatching {
                            context.grantUriPermission(
                                "com.android.systemui",
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
            } else {
                null
            }
            ChatCommunicationIntentDonor.buildMessagePushNotification(
                context = context,
                userInfo = userInfo,
                fallbackTitle = title,
                body = body,
                channelId = channelId,
                contentIntent = pendingIntent,
                avatarBitmap = circularAvatar,
                mediaUri = mediaUri,
                includeReply = isChatDm(type),
                notificationId = collapseKey.hashCode(),
            )
        } else {
            buildSocialNotification(
                context = context,
                title = title,
                body = body,
                channelId = channelId,
                contentIntent = pendingIntent,
                avatar = bitmaps.avatar,
                content = bitmaps.content,
                isQuotedBody = isQuotedSocial(type, userInfo),
            )
        }

        // Conversation API: un setGroup suelto hace que MIUI pinte el icono de la app
        // en vez del Person.icon. Solo agrupar social/sistema.
        if (!isConversationShade(type) && !threadId.isNullOrBlank()) {
            builder.setGroup(threadId)
        }
        val accent = ContextCompat.getColor(context, R.color.notification_accent)
        builder.setColor(accent)
        if (channelId == CHANNEL_MESSAGES) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(collapseKey.hashCode(), builder.build())
        }
    }

    private fun isChatDm(type: String) = type == "message" || type == "new_message"

    /** DM + reacción/buzz del mismo hilo de conversación (≡ thread-id iOS). */
    private fun isConversationShade(type: String) =
        type in setOf("message", "new_message", "message_reaction", "chat_buzz")

    private fun isQuotedSocial(type: String, userInfo: Map<String, Any?>): Boolean {
        if (type == "moment_comment") return true
        if (userInfo["mentionContext"] as? String == "reply") return true
        return false
    }

    private fun channelIdFor(userInfo: Map<String, Any?>, type: String): String {
        val fromPayload = userInfo["channelId"] as? String
        if (fromPayload in setOf(CHANNEL_MESSAGES, CHANNEL_SOCIAL, CHANNEL_REMINDERS, CHANNEL_DEFAULT)) {
            return fromPayload!!
        }
        return when (type) {
            "message", "new_message", "message_reaction", "chat_buzz", "message_request_v2" ->
                CHANNEL_MESSAGES
            "gentle_reminder" -> CHANNEL_REMINDERS
            else -> CHANNEL_SOCIAL
        }
    }

    private fun defaultThreadId(userInfo: Map<String, Any?>, type: String): String? {
        val conversationId = userInfo["conversationId"] as? String
        if (!conversationId.isNullOrBlank() && type in setOf("message", "new_message", "message_reaction", "chat_buzz")) {
            return "conversation_$conversationId"
        }
        return null
    }

    private fun defaultCollapseKey(userInfo: Map<String, Any?>, type: String, messageId: String?): String {
        val conversationId = userInfo["conversationId"] as? String
        if (type == "message" || type == "new_message") {
            return "msg_${conversationId.orEmpty()}".ifBlank { messageId ?: type }
        }
        return messageId ?: type
    }

    private fun buildSocialNotification(
        context: Context,
        title: String,
        body: String,
        channelId: String,
        contentIntent: PendingIntent,
        avatar: Bitmap?,
        content: Bitmap?,
        isQuotedBody: Boolean,
    ): NotificationCompat.Builder {
        val circularAvatar = avatar?.let { prepareAvatar(it) }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_moments)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        circularAvatar?.let { builder.setLargeIcon(it) }
        when {
            content != null -> builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(content)
                    .setSummaryText(body)
                    .bigLargeIcon(null as Bitmap?),
            )
            isQuotedBody || body.length > 48 -> builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(body),
            )
        }
        return builder
    }

    private data class ShadeBitmaps(val avatar: Bitmap?, val content: Bitmap?)

    private suspend fun resolveBitmaps(userInfo: Map<String, Any?>): ShadeBitmaps {
        val type = (userInfo["type"] as? String)?.lowercase()
        val isChat = type == "new_message" || type == "message"
        val messageType = userInfo["messageType"] as? String
        val avatar = resolveAvatarBitmap(userInfo)

        if (isChat && messageType != null && messageType !in viewOnceTypes) {
            val conversationId = userInfo["conversationId"] as? String
            val previewOn = conversationId != null && ChatPreviewPrivacy.shouldRevealPreview(
                conversationId,
                ChatPreviewPrivacy.isVanishModeMessage(userInfo),
            )
            val content = if (previewOn) {
                when (messageType) {
                    "image", "video" -> {
                        val messageId = userInfo["messageId"] as? String
                        if (messageId.isNullOrBlank() || conversationId.isNullOrBlank()) null
                        else resolveEncryptedMediaBitmap(conversationId, messageId, messageType == "image")
                    }
                    "gif", "sticker" -> (userInfo["mediaUrl"] as? String)?.let { downloadPublicBitmap(it) }
                    else -> null
                }
            } else {
                null
            }
            return ShadeBitmaps(avatar, content)
        }

        val mediaUrl = (userInfo["mediaUrl"] as? String)?.takeIf { it.isNotBlank() }
        val content = mediaUrl?.let { downloadPublicBitmap(it) }
        return ShadeBitmaps(avatar, content)
    }

    private suspend fun resolveAvatarBitmap(userInfo: Map<String, Any?>): Bitmap? {
        val fromPayload = (userInfo["senderProfileImage"] as? String)?.takeIf { it.isNotBlank() }
        val senderId = (userInfo["senderId"] as? String)?.takeIf { it.isNotBlank() }
        val cached = senderId?.let { UserCacheService.getCachedUser(it)?.profileImagePath }
        val remote = if (fromPayload.isNullOrBlank() && !senderId.isNullOrBlank()) {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(senderId)
                    .get().await().getString("profileImagePath")
            }.getOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val urlOrPath = fromPayload ?: cached ?: remote ?: return null
        return downloadAvatar(urlOrPath)
    }

    private suspend fun downloadAvatar(urlOrPath: String): Bitmap? {
        val cleaned = urlOrPath.trim().replace(":443", "")
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            downloadPublicBitmap(cleaned)?.let { return it }
            val objectPath = StoragePathBuilder.extractObjectPath(cleaned)
            if (objectPath != cleaned && !objectPath.contains("://")) {
                return downloadStorageBitmap(objectPath)
            }
            return null
        }
        return downloadStorageBitmap(cleaned)
    }

    private suspend fun downloadStorageBitmap(objectPath: String): Bitmap? {
        if (objectPath.isBlank() || objectPath.contains("://")) return null
        val bytes = runCatching {
            FirebaseStorage.getInstance().reference.child(objectPath).getBytes(2L * 1024 * 1024).await()
        }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

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
            if (!mediaPath.isNullOrBlank() && mediaMeta != null && mediaMeta.plaintextSize <= MAX_ATTACHMENT_BYTES) {
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
                setRequestProperty("User-Agent", "Moments/1.0")
            }
            try {
                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private const val AVATAR_PX = 256

    /** Recorte circular a tamaño de Person.icon (MIUI distorsiona bitmaps enormes). */
    private fun prepareAvatar(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val left = (bitmap.width - size) / 2
        val top = (bitmap.height - size) / 2
        val square = Bitmap.createBitmap(bitmap, left, top, size, size)
        val scaled = if (square.width == AVATAR_PX) square
        else Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true)
        return toCircle(scaled)
    }

    private fun toCircle(bitmap: Bitmap): Bitmap {
        val size = bitmap.width
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rect, paint)
        return output
    }

    private fun cacheBitmapAsUri(context: Context, bitmap: Bitmap, key: String): Uri? = runCatching {
        val dir = File(context.cacheDir, "notification_media").apply { mkdirs() }
        val file = File(dir, "${key.hashCode()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            file,
        )
    }.getOrNull()
}
