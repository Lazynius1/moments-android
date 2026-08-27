package com.moments.android.views.messaging.services

import com.google.firebase.firestore.FieldValue
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.DirectMessageRoute
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.views.feed.sharing.storyMediaTypeString
import com.moments.android.views.feed.sharing.storyPreviewUrl
import com.moments.android.views.messaging.media.ChatMediaOverlayPayload
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Port de `ChatService+SharingAndViewOnce.swift`.
 * `createBidirectionalConversation` vive en [ChatService] (≡ ChatService.swift iOS).
 */
class MessageRequestRequiredException :
    Exception("A message request is required to start this conversation")

suspend fun ChatService.findExistingConversation(user1Id: String, user2Id: String): Result<String?> =
    runCatching {
        firestore.collection("conversations")
            .whereArrayContains("participants", user1Id)
            .get()
            .await()
            .documents
            .firstOrNull { document ->
                (document.get("participants") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.contains(user2Id) == true
            }
            ?.id
    }

/** ≡ `areMutualFollowers` → `users/{uid}/mutuals/{other}`. */
suspend fun ChatService.areMutualFollowers(user1Id: String, user2Id: String): Boolean =
    FirestoreService().isMutualConnection(user1Id, user2Id)

suspend fun ChatService.getOrCreateConversation(
    user1Id: String,
    user2Id: String,
    initialMessage: String? = null,
): Result<String> = runCatching {
    val coordinator = MessageRequestService()
    val conversationId = when (val route = coordinator.resolveRoute(user2Id)) {
        is DirectMessageRoute.Conversation -> route.id
        is DirectMessageRoute.ConversationDraft ->
            coordinator.activateConversationDraft(user2Id, route.threadId)
        is DirectMessageRoute.IncomingRequest ->
            coordinator.acceptIncomingThread(route.threadId).conversationId
        is DirectMessageRoute.OutgoingRequest -> throw MessageRequestRequiredException()
    }
    initialMessage?.trim()?.takeIf(String::isNotEmpty)?.let { message ->
        sendTextMessage(conversationId, user1Id, message).getOrThrow()
    }
    conversationId
}

private suspend fun ChatService.checkMutualFollowAndCreateConversation(
    user1Id: String,
    user2Id: String,
    initialMessage: String?,
): String {
    if (!areMutualFollowers(user1Id, user2Id)) {
        throw MessageRequestRequiredException()
    }
    return createBidirectionalConversation(user1Id, user2Id, initialMessage).getOrThrow()
}

suspend fun ChatService.sendSharedMomentMessage(
    conversationId: String,
    senderId: String,
    moment: Moment,
    shareText: String,
    momentUrl: String,
): Result<EnhancedMessage> = runCatching {
    val encryptedContent = EncryptionService.encryptChatMessage(shareText, conversationId)
    val author = UserCacheService.getCachedUser(moment.authorId)?.username ?: moment.username
    // iOS: String(moment.timestamp.timeIntervalSince1970) — segundos, no ms.
    val epochSeconds = (moment.timestamp.time / 1000.0).toString()
    val sharedMomentData = mapOf(
        "momentId" to moment.id.orEmpty(),
        "momentAuthor" to author,
        "momentAuthorId" to moment.authorId,
        "momentContent" to moment.content,
        "momentImageUrl" to moment.previewImageURLString.orEmpty(),
        "momentAspectRatio" to (moment.primaryVisibleMediaItem?.aspectRatio ?: moment.aspectRatio ?: "1:1"),
        "momentMediaCount" to maxOf(moment.visibleMediaCount, 1).toString(),
        "momentVideoUrl" to moment.previewVideoURLString.orEmpty(),
        "momentTimestamp" to epochSeconds,
        "shareUrl" to momentUrl,
    )
    val sent = sendMessage(
        EnhancedMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.SHARED_MOMENT,
            content = encryptedContent,
            timestamp = Date(),
            status = MessageStatus.SENDING,
            sharedMomentData = sharedMomentData,
        ),
        useServerTimestamp = true,
    ).getOrThrow()
    updateConversation(
        conversationId = conversationId,
        lastMessage = neutralConversationPreview(MessageType.SHARED_MOMENT),
        senderId = senderId,
        messageType = MessageType.SHARED_MOMENT,
    )
    sent
}

suspend fun ChatService.sendSharedStoryMessage(
    conversationId: String,
    senderId: String,
    story: Story,
    shareText: String,
): Result<EnhancedMessage> = runCatching {
    val storyId = requireNotNull(story.id) { "Missing story id" }
    val encryptedContent = EncryptionService.encryptChatMessage(shareText, conversationId)
    val author = UserCacheService.getCachedUser(story.authorId)?.username ?: story.username
    val sharedStoryData = mapOf(
        "storyId" to storyId,
        "storyAuthor" to author,
        "storyAuthorId" to story.authorId,
        "storyPreviewUrl" to storyPreviewUrl(story),
        "storyMediaType" to storyMediaTypeString(story),
        "storyExpiration" to (story.expirationDate.time / 1000.0).toString(),
        "storyTimestamp" to (story.timestamp.time / 1000.0).toString(),
    )
    val sent = sendMessage(
        EnhancedMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.SHARED_STORY,
            content = encryptedContent,
            timestamp = Date(),
            status = MessageStatus.SENDING,
            sharedStoryData = sharedStoryData,
        ),
        useServerTimestamp = true,
    ).getOrThrow()
    updateConversation(
        conversationId = conversationId,
        lastMessage = neutralConversationPreview(MessageType.SHARED_STORY),
        senderId = senderId,
        messageType = MessageType.SHARED_STORY,
    )
    sent
}

suspend fun ChatService.sendSharedProfileMessage(
    conversationId: String,
    senderId: String,
    sharedProfileData: Map<String, String>,
    shareText: String,
): Result<EnhancedMessage> = runCatching {
    val encryptedContent = EncryptionService.encryptChatMessage(shareText, conversationId)
    val sent = sendMessage(
        EnhancedMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.SHARED_PROFILE,
            content = encryptedContent,
            timestamp = Date(),
            status = MessageStatus.SENDING,
            sharedProfileData = sharedProfileData,
        ),
        useServerTimestamp = true,
    ).getOrThrow()
    updateConversation(
        conversationId = conversationId,
        lastMessage = neutralConversationPreview(MessageType.SHARED_PROFILE),
        senderId = senderId,
        messageType = MessageType.SHARED_PROFILE,
    )
    sent
}

// MARK: - View Once

fun ChatService.deleteViewOnceAfterViewing(
    conversationId: String,
    messageId: String,
    completion: (Exception?) -> Unit,
) {
    ViewOnceConsumptionService.consume(
        conversationId,
        messageId,
        ViewOnceConsumptionReason.VIEW_ONCE,
        completion,
    )
}

fun ChatService.cleanupConsumedViewOnceMessages(conversationId: String) {
    if (conversationId.isBlank()) return
    firestore.collection("conversations").document(conversationId).collection("messages")
        .whereEqualTo("isViewOnce", true)
        .whereEqualTo("isDeleted", false)
        .limit(50)
        .get()
        .addOnSuccessListener { snapshot ->
            snapshot.documents.forEach { document ->
                val data = document.data.orEmpty()
                val reason = consumptionReasonForConsumedViewOnce(data) ?: return@forEach
                val messageId = document.getString("id") ?: document.id
                ViewOnceConsumptionService.consume(conversationId, messageId, reason) { }
            }
        }
}

/** ≡ `consumptionReasonForConsumedViewOnce(_:)`. */
private fun consumptionReasonForConsumedViewOnce(data: Map<String, Any?>): ViewOnceConsumptionReason? {
    if (data["isViewOnce"] != true) return null
    if (data["isDeleted"] == true) return null
    val hasMedia = listOf("mediaObjectPath", "thumbnailObjectPath", "mediaUrl", "thumbnailUrl")
        .mapNotNull { data[it] as? String }
        .any { it.isNotEmpty() }
    if (!hasMedia) return null
    val allowReplay = data["allowReplay"] == true
    val viewed = (data["viewedBy"] as? List<*>)?.isNotEmpty() == true || data["isViewed"] == true
    if (allowReplay) {
        if ((data["replayedBy"] as? List<*>)?.isNotEmpty() == true) {
            return ViewOnceConsumptionReason.REPLAY
        }
        if (viewed) return ViewOnceConsumptionReason.ABANDON_REPLAY
        return null
    }
    return if (viewed) ViewOnceConsumptionReason.VIEW_ONCE else null
}

suspend fun ChatService.sendViewOnceMessage(
    conversationId: String,
    senderId: String,
    mediaData: ByteArray,
    isImage: Boolean,
    messageId: String? = null,
    isVanishModeMessage: Boolean = false,
    allowReplay: Boolean = false,
    replyTo: String? = null,
    overlayPayload: ChatMediaOverlayPayload? = null,
): Result<EnhancedMessage> = runCatching {
    val messageType = if (isImage) MessageType.VIEW_ONCE_IMAGE else MessageType.VIEW_ONCE_VIDEO
    val finalMessageId = messageId ?: UUID.randomUUID().toString()
    val uploadResult = ChatServiceMediaPipeline.uploadMedia(
        data = mediaData,
        type = messageType,
        conversationId = conversationId,
        messageId = finalMessageId,
    ).getOrThrow()
    val message = EnhancedMessage(
        id = finalMessageId,
        conversationId = conversationId,
        senderId = senderId,
        type = messageType,
        content = null,
        mediaUrl = uploadResult.mediaUrl,
        thumbnailUrl = uploadResult.thumbnailUrl,
        mediaObjectPath = uploadResult.mediaObjectPath,
        thumbnailObjectPath = uploadResult.thumbnailObjectPath,
        mediaEncryption = uploadResult.mediaEncryption,
        thumbnailEncryption = uploadResult.thumbnailEncryption,
        fileSize = mediaData.size.toLong(),
        timestamp = Date(),
        status = MessageStatus.SENDING,
        replyTo = replyTo,
        isViewed = false,
        textOverlayLive = overlayPayload?.textOverlayLive,
        textOverlays = overlayPayload?.textOverlays,
        stickers = overlayPayload?.stickers,
        drawingData = overlayPayload?.drawingData,
        viewedBy = emptyList(),
        allowReplay = allowReplay.takeIf { it },
        replayedBy = if (allowReplay) emptyList() else null,
        isVanishModeMessage = isVanishModeMessage,
    )
    val messageData = createBasicMessageData(message).toMutableMap()
    messageData["isViewOnce"] = true
    messageData["viewedBy"] = emptyList<String>()
    if (allowReplay) {
        messageData["allowReplay"] = true
        messageData["replayedBy"] = emptyList<String>()
    }
    saveViewOnceMessage(message, messageData)
}

private suspend fun ChatService.saveViewOnceMessage(
    message: EnhancedMessage,
    customData: Map<String, Any>,
): EnhancedMessage {
    val messageRef = firestore.collection("conversations")
        .document(message.conversationId)
        .collection("messages")
        .document(message.id)
    try {
        messageRef.set(customData).await()
    } catch (error: Exception) {
        updateLocalMessageStatus(message.conversationId, message.id, MessageStatus.FAILED)
        throw error
    }
    updateMessageStatus(message.conversationId, message.id, MessageStatus.SENT)
    val previewType = if (message.type == MessageType.VIEW_ONCE_IMAGE) {
        MessageType.VIEW_ONCE_IMAGE
    } else {
        MessageType.VIEW_ONCE_VIDEO
    }
    updateConversation(
        conversationId = message.conversationId,
        lastMessage = neutralConversationPreview(previewType),
        senderId = message.senderId,
        messageType = message.type,
    )
    return message.copy(status = MessageStatus.SENT)
}

suspend fun ChatService.markViewOnceAsViewed(
    conversationId: String,
    messageId: String,
    viewerId: String,
): Result<Unit> = runCatching {
    val messageRef = firestore.collection("conversations")
        .document(conversationId)
        .collection("messages")
        .document(messageId)
    // firestore.rules `onlyViewOnceFieldsUpdated` solo permite isViewed/viewedBy/replayedBy.
    // Incluir `status` hace fallar el write (PERMISSION_DENIED) → CF replay ve viewedBy vacío.
    firestore.runTransaction { transaction ->
        val snapshot = transaction.get(messageRef)
        @Suppress("UNCHECKED_CAST")
        val viewedBy = snapshot.get("viewedBy") as? List<*>
        if (viewedBy == null) {
            transaction.update(
                messageRef,
                mapOf(
                    "viewedBy" to listOf(viewerId),
                    "isViewed" to true,
                ),
            )
        } else {
            val list = viewedBy.filterIsInstance<String>().toMutableList()
            if (viewerId !in list) {
                list += viewerId
                transaction.update(
                    messageRef,
                    mapOf(
                        "viewedBy" to list,
                        "isViewed" to true,
                    ),
                )
            }
        }
        null
    }.await()
}

suspend fun ChatService.markViewOnceReplayed(
    conversationId: String,
    messageId: String,
    viewerId: String,
): Result<Unit> = runCatching {
    firestore.collection("conversations")
        .document(conversationId)
        .collection("messages")
        .document(messageId)
        .update("replayedBy", FieldValue.arrayUnion(viewerId))
        .await()
}
