package com.moments.android.views.messaging.services

import com.google.firebase.firestore.FieldValue
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.feed.sharing.storyMediaTypeString
import com.moments.android.views.feed.sharing.storyPreviewUrl
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/** Port, por tramos, de `ChatService+SharingAndViewOnce.swift`. */
suspend fun ChatService.findExistingConversation(user1Id: String, user2Id: String): Result<String?> = runCatching {
    firestore.collection("conversations").whereArrayContains("participants", user1Id).get().await()
        .documents.firstOrNull { document ->
            (document.get("participants") as? List<*>)?.filterIsInstance<String>()?.contains(user2Id) == true
        }?.id
}

suspend fun ChatService.areMutualFollowers(user1Id: String, user2Id: String): Boolean {
    val service = FirestoreService()
    return service.isFollowing(user1Id, user2Id) && service.isFollowing(user2Id, user1Id)
}

suspend fun ChatService.getOrCreateConversation(
    user1Id: String,
    user2Id: String,
    initialMessage: String? = null,
): Result<String> = runCatching {
    val existing = findExistingConversation(user1Id, user2Id).getOrThrow()
    val conversationId = existing ?: run {
        check(areMutualFollowers(user1Id, user2Id))
        materializeConversation(user2Id, user1Id).getOrThrow()
    }
    initialMessage?.trim()?.takeIf(String::isNotBlank)?.let { text ->
        sendTextMessage(conversationId, user1Id, text).getOrThrow()
    }
    conversationId
}

suspend fun ChatService.sendSharedMomentMessage(
    conversationId: String,
    senderId: String,
    moment: Moment,
    shareText: String,
    momentUrl: String,
): Result<EnhancedMessage> = runCatching {
    val author = UserCacheService.getCachedUser(moment.authorId)?.username ?: moment.username
    val payload = mapOf(
        "momentId" to moment.id.orEmpty(),
        "momentAuthor" to author,
        "momentAuthorId" to moment.authorId,
        "momentContent" to moment.content,
        "momentImageUrl" to (moment.thumbnailUrl ?: moment.imagePath).orEmpty(),
        "momentAspectRatio" to (moment.aspectRatio ?: "1:1"),
        "momentVideoUrl" to moment.videoUrl.orEmpty(),
        "momentTimestamp" to moment.timestamp.time.toString(),
        "shareUrl" to momentUrl,
    )
    sendMessage(
        EnhancedMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.SHARED_MOMENT,
            content = com.moments.android.services.messaging.EncryptionService.encryptChatMessage(shareText, conversationId),
            timestamp = Date(),
            status = MessageStatus.SENDING,
            sharedMomentData = payload,
        ),
    ).getOrThrow()
}

suspend fun ChatService.sendSharedStoryMessage(
    conversationId: String,
    senderId: String,
    story: Story,
    shareText: String,
): Result<EnhancedMessage> = runCatching {
    val storyId = requireNotNull(story.id)
    val author = UserCacheService.getCachedUser(story.authorId)?.username ?: story.username
    val payload = mapOf(
        "storyId" to storyId,
        "storyAuthor" to author,
        "storyAuthorId" to story.authorId,
        "storyPreviewUrl" to storyPreviewUrl(story.backgroundFrameURL, story.backgroundBlurredFrameURL, story.mediaItem.url),
        "storyMediaType" to storyMediaTypeString(story.mediaItem.type == com.moments.android.models.MediaItem.MediaType.VIDEO),
        "storyExpiration" to story.expirationDate.time.toString(),
        "storyTimestamp" to story.timestamp.time.toString(),
    )
    sendMessage(
        EnhancedMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.SHARED_STORY,
            content = com.moments.android.services.messaging.EncryptionService.encryptChatMessage(shareText, conversationId),
            timestamp = Date(),
            status = MessageStatus.SENDING,
            sharedStoryData = payload,
        ),
    ).getOrThrow()
}

fun ChatService.deleteViewOnceAfterViewing(conversationId: String, messageId: String, completion: (Exception?) -> Unit) {
    ViewOnceConsumptionService.consume(conversationId, messageId, ViewOnceConsumptionReason.VIEW_ONCE, completion)
}

fun ChatService.cleanupConsumedViewOnceMessages(conversationId: String) {
    if (conversationId.isBlank()) return
    firestore.collection("conversations").document(conversationId).collection("messages")
        .whereEqualTo("isViewOnce", true)
        .whereEqualTo("isDeleted", false)
        .limit(50)
        .get()
        .addOnSuccessListener { snapshot -> snapshot.documents.forEach { document ->
            viewOnceConsumptionReason(document.data.orEmpty())?.let { reason ->
                ViewOnceConsumptionService.consume(conversationId, document.getString("id") ?: document.id, reason) { }
            }
        } }
}

suspend fun ChatService.markViewOnceAsViewed(conversationId: String, messageId: String, viewerId: String): Result<Unit> = runCatching {
    val reference = firestore.collection("conversations").document(conversationId).collection("messages").document(messageId)
    firestore.runTransaction { transaction ->
        val viewedBy = (transaction.get(reference).get("viewedBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
        transaction.update(reference, mapOf(
            "viewedBy" to (viewedBy + viewerId).distinct(),
            "isViewed" to true,
            "status" to MessageStatus.READ.raw,
        ))
    }.await()
}

suspend fun ChatService.markViewOnceReplayed(conversationId: String, messageId: String, viewerId: String): Result<Unit> = runCatching {
    firestore.collection("conversations").document(conversationId).collection("messages").document(messageId)
        .update("replayedBy", FieldValue.arrayUnion(viewerId))
        .await()
}

private fun viewOnceConsumptionReason(data: Map<String, Any?>): ViewOnceConsumptionReason? {
    if (data["isViewOnce"] != true || data["isDeleted"] == true) return null
    val hasMedia = listOf("mediaObjectPath", "thumbnailObjectPath", "mediaUrl", "thumbnailUrl")
        .mapNotNull { data[it] as? String }
        .any(String::isNotBlank)
    if (!hasMedia) return null
    val viewed = (data["viewedBy"] as? List<*>)?.isNotEmpty() == true || data["isViewed"] == true
    if (data["allowReplay"] == true) {
        return when {
            (data["replayedBy"] as? List<*>)?.isNotEmpty() == true -> ViewOnceConsumptionReason.REPLAY
            viewed -> ViewOnceConsumptionReason.ABANDON_REPLAY
            else -> null
        }
    }
    return if (viewed) ViewOnceConsumptionReason.VIEW_ONCE else null
}
