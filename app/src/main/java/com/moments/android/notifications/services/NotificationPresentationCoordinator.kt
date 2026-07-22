package com.moments.android.notifications.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.views.messaging.services.ChatNavigationIntentStore
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ChatSessionEngine
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.services.messaging.MessageIngestSource
import com.moments.android.services.messaging.MessagingEvents
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class NotificationPresentationSource {
    PUSH,
    FIRESTORE,
    LOCAL,
}

/** Port completo de NotificationPresentationCoordinator.swift */
object NotificationPresentationCoordinator {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recentDedupKeys = ConcurrentHashMap<String, Long>()
    private const val DEDUP_WINDOW_MS = 5_000L

    private val _presentations = MutableSharedFlow<Pair<MomentsNotification, NotificationPresentationSource>>(
        extraBufferCapacity = 16,
    )
    val presentations: SharedFlow<Pair<MomentsNotification, NotificationPresentationSource>> =
        _presentations.asSharedFlow()

    fun present(pushData: Map<String, Any?>, source: NotificationPresentationSource) {
        mapPushPayload(pushData)?.let { present(it, source, pushData) }
    }

    fun present(
        notification: MomentsNotification,
        source: NotificationPresentationSource,
        userInfo: Map<String, Any?>? = null,
    ) {
        if (notification.senderId == FirebaseAuth.getInstance().currentUser?.uid) return
        if (!shouldShowBanner(notification)) return
        if (!registerDedup(notification)) return
        applySideEffects(notification, userInfo)
        scope.launch {
            val resolved = InAppNotificationPreviewResolver.resolve(notification, userInfo)
            if (!shouldShowBanner(resolved)) return@launch
            InAppNotificationService.display(resolved)
            _presentations.tryEmit(resolved to source)
        }
    }

    fun isSilentPush(userInfo: Map<String, Any?>): Boolean = parseBool(userInfo["silent"])

    private fun shouldShowBanner(notification: MomentsNotification): Boolean {
        val chatTypes = setOf(
            NotificationType.MESSAGE,
            NotificationType.MESSAGE_REACTION,
            NotificationType.CHAT_BUZZ,
        )
        if (notification.type in chatTypes) {
            val conversationId = notification.conversationId
            if (!conversationId.isNullOrBlank()) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null && LocalPersistenceService.isConversationArchived(conversationId, userId)) {
                    return false
                }
                if (conversationId == ChatSessionEngine.activeConversationId) return false
            }
        }
        return true
    }

    private fun registerDedup(notification: MomentsNotification): Boolean {
        val key = dedupKey(notification)
        val now = System.currentTimeMillis()
        recentDedupKeys.entries.removeIf { now - it.value >= DEDUP_WINDOW_MS }
        return recentDedupKeys.putIfAbsent(key, now) == null
    }

    private fun dedupKey(notification: MomentsNotification): String {
        val bucket = (notification.timestamp.time / DEDUP_WINDOW_MS).toInt()
        return when (notification.type) {
            NotificationType.MESSAGE ->
                "message|${notification.conversationId}|${notification.senderId}|$bucket"
            NotificationType.MESSAGE_REACTION ->
                "messageReaction|${notification.conversationId}|${notification.messageId}|${notification.senderId}|$bucket"
            NotificationType.CHAT_BUZZ ->
                "chatBuzz|${notification.conversationId}|${notification.buzzEventId ?: notification.senderId}|$bucket"
            NotificationType.LIKE, NotificationType.REACTION, NotificationType.COMMENT,
            NotificationType.MENTION, NotificationType.PHOTO_TAG, NotificationType.STORY_REACTION,
            ->
                "${notification.type.raw}|${notification.senderId}|${notification.momentId}|${notification.storyId}|${notification.commentId}|$bucket"
            NotificationType.NEW_FOLLOWER, NotificationType.FOLLOW_REQUEST,
            NotificationType.REQUEST_ACCEPTED, NotificationType.MUTUAL_CONNECTION,
            ->
                "${notification.type.raw}|${notification.senderId}|$bucket"
            NotificationType.STORY_CHAIN_CONTINUED ->
                "${notification.type.raw}|${notification.senderId}|${notification.chainId}|${notification.chainPosition}|$bucket"
            else -> notification.id?.takeIf { it.isNotBlank() }
                ?.let { "${notification.type.raw}|$it" }
                ?: "${notification.type.raw}|${notification.senderId}|$bucket"
        }
    }

    private fun applySideEffects(notification: MomentsNotification, userInfo: Map<String, Any?>?) {
        when (notification.type) {
            NotificationType.MESSAGE -> {
                val conversationId = notification.conversationId ?: return
                val messageId = notification.messageId ?: return
                ChatService.markMessageAsDeliveredFromNotification(conversationId, messageId)
                scope.launch {
                    if (userInfo != null) MessageIngestService.ingest(userInfo)
                    else MessageIngestService.ingest(conversationId, messageId, MessageIngestSource.PUSH)
                }
            }
            NotificationType.MESSAGE_REACTION -> {
                val conversationId = notification.conversationId ?: return
                val messageId = notification.messageId ?: return
                MessagingEvents.emitMessageReactionHighlight(conversationId, messageId)
            }
            NotificationType.CHAT_BUZZ -> {
                val conversationId = notification.conversationId ?: return
                ChatNavigationIntentStore.enqueueBuzz(conversationId, notification.buzzEventId)
                if (conversationId == ChatSessionEngine.activeConversationId) {
                    MessagingEvents.emitChatBuzzHighlight(conversationId, notification.buzzEventId)
                }
            }
            else -> Unit
        }
    }

    private fun mapPushPayload(userInfo: Map<String, Any?>): MomentsNotification? {
        val rawType = userInfo["type"] as? String ?: return null
        val notificationType = mapPushType(rawType) ?: return null
        val senderId = firstString(userInfo, listOf("senderId", "userId", "followerId"))
            ?: if (notificationType == NotificationType.GENTLE_REMINDER) "gentle_reminder" else ""
        return MomentsNotification(
            id = firstString(userInfo, listOf("notificationId", "gcm.message_id"))
                ?: UUID.randomUUID().toString(),
            type = notificationType,
            senderId = senderId.ifBlank { "moments_system" },
            senderUsername = firstString(userInfo, listOf("senderUsername", "username")) ?: "Moments",
            timestamp = Date(),
            isPending = true,
            message = firstString(userInfo, listOf("reactionEmojis")),
            downloadURL = firstString(userInfo, listOf("downloadURL")),
            momentId = firstString(userInfo, listOf("momentId", "targetId")),
            storyId = firstString(userInfo, listOf("storyId")),
            storyAuthorId = firstString(userInfo, listOf("storyAuthorId", "storyOwnerId")),
            mentionContext = firstString(userInfo, listOf("mentionContext")),
            targetAuthorId = firstString(userInfo, listOf("targetAuthorId", "momentOwnerId")),
            targetAuthorUsername = firstString(userInfo, listOf("targetAuthorUsername")),
            reaction = firstString(userInfo, listOf("reactionEmoji", "reactionType", "reaction")),
            reactionCount = intValue(userInfo, listOf("reactionCount")),
            conversationId = firstString(userInfo, listOf("conversationId", "targetId")),
            echoId = firstString(userInfo, listOf("echoId")),
            chainId = firstString(userInfo, listOf("chainId")),
            chainTitle = firstString(userInfo, listOf("chainTitle")),
            chainPosition = intValue(userInfo, listOf("chainPosition")),
            totalParts = intValue(userInfo, listOf("totalParts")),
            chainRole = firstString(userInfo, listOf("chainRole")),
            messageId = firstString(userInfo, listOf("messageId", "targetMessageId")),
            messageType = firstString(userInfo, listOf("messageType")),
            buzzEventId = firstString(userInfo, listOf("buzzEventId")),
            reminderVariant = firstString(userInfo, listOf("reminderVariant")),
            isReactionPlural = parseBool(userInfo["isReactionPlural"]),
        )
    }

    private fun mapPushType(rawType: String): NotificationType? = when (rawType) {
        "new_message" -> NotificationType.MESSAGE
        "message_reaction" -> NotificationType.MESSAGE_REACTION
        "chat_buzz" -> NotificationType.CHAT_BUZZ
        "gentle_reminder" -> NotificationType.GENTLE_REMINDER
        "moment_reaction" -> NotificationType.REACTION
        "moment_comment" -> NotificationType.COMMENT
        "story_reaction" -> NotificationType.STORY_REACTION
        "story_chain_continued" -> NotificationType.STORY_CHAIN_CONTINUED
        "new_follower" -> NotificationType.NEW_FOLLOWER
        "follow_request" -> NotificationType.FOLLOW_REQUEST
        "request_accepted" -> NotificationType.REQUEST_ACCEPTED
        "photo_tag" -> NotificationType.PHOTO_TAG
        "media_moderation" -> NotificationType.MEDIA_MODERATION
        "echo_suggestion" -> NotificationType.ECHO_SUGGESTION
        "data_export_ready" -> NotificationType.DATA_EXPORT_READY
        "mutual_connection" -> NotificationType.MUTUAL_CONNECTION
        "mention" -> NotificationType.MENTION
        else -> NotificationType.from(rawType)
    }

    fun presentMessageReactionFallback(
        conversationId: String,
        messageId: String,
        senderId: String,
        senderUsername: String,
        emoji: String,
        messageType: String?,
    ) {
        present(
            MomentsNotification(
                id = UUID.randomUUID().toString(),
                type = NotificationType.MESSAGE_REACTION,
                senderId = senderId,
                senderUsername = senderUsername,
                timestamp = Date(),
                isPending = true,
                reaction = emoji,
                conversationId = conversationId,
                messageId = messageId,
                messageType = messageType,
            ),
            NotificationPresentationSource.FIRESTORE,
        )
    }

    fun presentChatBuzzFallback(
        conversationId: String,
        buzzEventId: String,
        senderId: String,
        senderUsername: String,
    ) {
        ChatNavigationIntentStore.enqueueBuzz(conversationId, buzzEventId)
        present(
            MomentsNotification(
                id = UUID.randomUUID().toString(),
                type = NotificationType.CHAT_BUZZ,
                senderId = senderId,
                senderUsername = senderUsername,
                timestamp = Date(),
                isPending = true,
                conversationId = conversationId,
                buzzEventId = buzzEventId,
            ),
            NotificationPresentationSource.FIRESTORE,
        )
    }

    suspend fun fetchSenderUsername(userId: String): String = runCatching {
        db.collection("users").document(userId).get().await().getString("username") ?: "User"
    }.getOrDefault("User")

    suspend fun isMessageAuthoredByCurrentUser(conversationId: String, messageId: String): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        return runCatching {
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId).get().await()
                .getString("senderId") == currentUserId
        }.getOrDefault(false)
    }

    private fun firstString(userInfo: Map<String, Any?>, keys: List<String>): String? {
        for (key in keys) {
            val value = (userInfo[key] as? String)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun intValue(userInfo: Map<String, Any?>, keys: List<String>): Int? {
        for (key in keys) {
            when (val value = userInfo[key]) {
                is Int -> return value
                is Long -> return value.toInt()
                is String -> value.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun parseBool(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is String -> value.trim().lowercase() in setOf("true", "1")
        is Int -> value != 0
        is Long -> value != 0L
        else -> false
    }
}
