package com.moments.android.views.creator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.StickerData
import com.moments.android.R
import com.moments.android.MomentsApplication
import com.moments.android.services.messaging.DirectMessageRoute
import com.moments.android.services.messaging.MessageRequestInteractionContext
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.privacy.ContentVisibilityService
import com.moments.android.services.privacy.ContentVisibilityType
import com.moments.android.utilities.momentsPress
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.sendSharedStoryMessage
import com.moments.android.views.story.StoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Port de `glow(color:radius:)` de SwiftUI (triple shadow). */
fun Modifier.glow(color: Color, radius: Dp): Modifier =
    shadow(elevation = radius / 3, ambientColor = color, spotColor = color)
        .shadow(elevation = radius / 3, ambientColor = color, spotColor = color)
        .shadow(elevation = radius / 3, ambientColor = color, spotColor = color)

/** Port de `pressAnimation()` → `.momentsPress`. */
fun Modifier.pressAnimation(): Modifier = momentsPress()

/**
 * Port del typo Swift `pressAnimatioon()` — stub (`scaleEffect(1)` + tap vacío).
 * No aplica press real.
 */
fun Modifier.pressAnimatioon(): Modifier = this

/** Fallback Android de `MeshGradient` (iOS < 18 → LinearGradient first/last). */
@Composable
fun MeshGradient(
    width: Int,
    height: Int,
    points: List<List<Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredMeshGeometry = Triple(width, height, points)
    val start = colors.firstOrNull() ?: Color.Black
    val end = colors.lastOrNull() ?: Color.Black
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(start, end),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        ),
    )
}

/** ≡ `StoryMentionNotificationResult`. */
data class StoryMentionNotificationResult(
    val sentUserIds: List<String>,
    val skippedOutsideAudienceUserIds: List<String>,
    val failedDeliveryUserIds: List<String>,
)

/**
 * ≡ `StickerPickerView.sendMentionNotificationsForStory(storyId:stickers:)`
 * (audience = everyone, author = current user).
 */
fun sendMentionNotificationsForStory(
    storyId: String,
    stickers: List<StickerData>,
) {
    val authorId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    CoroutineScope(Dispatchers.IO).launch {
        sendMentionNotificationsForStory(
            storyId = storyId,
            storyAuthorId = authorId,
            audience = ContentAudience.EVERYONE,
            customViewers = null,
            customListId = null,
            stickers = stickers,
        )
    }
}

/**
 * ≡ sobrecarga async con audiencia.
 * Solo notifica si el mencionado puede ver la historia.
 */
suspend fun sendMentionNotificationsForStory(
    storyId: String,
    storyAuthorId: String,
    audience: ContentAudience,
    customViewers: List<String>?,
    customListId: String?,
    stickers: List<StickerData>,
): StoryMentionNotificationResult {
    val mentionedUserIds = stickers.asSequence()
        .filter { it.type == "mention" }
        .mapNotNull { it.userId?.takeIf(String::isNotBlank) }
        .filter { it != storyAuthorId }
        .distinct()
        .toList()

    val sent = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    val failed = mutableListOf<String>()
    val story = runCatching { StoryRepository().fetchStory(storyAuthorId, storyId) }.getOrElse {
        return StoryMentionNotificationResult(emptyList(), emptyList(), mentionedUserIds)
    }

    for (userId in mentionedUserIds) {
        val canNotify = canNotifyStoryMention(
            mentionedUserId = userId,
            storyAuthorId = storyAuthorId,
            audience = audience,
            customViewers = customViewers,
            customListId = customListId,
        )
        if (!canNotify) {
            skipped += userId
            continue
        }
        runCatching {
            sendStoryMentionMessage(storyAuthorId, userId, story)
        }.onSuccess {
            sent += userId
        }.onFailure {
            failed += userId
        }
    }

    return StoryMentionNotificationResult(
        sentUserIds = sent,
        skippedOutsideAudienceUserIds = skipped,
        failedDeliveryUserIds = failed,
    )
}

private suspend fun sendStoryMentionMessage(
    authorId: String,
    recipientId: String,
    story: com.moments.android.models.Story,
) {
    val storyId = requireNotNull(story.id).takeIf(String::isNotBlank) ?: error("Missing story id")
    val coordinator = MessageRequestService()
    val deliveryMessageId = "storyMention_${storyId}_$recipientId"
    val interaction = MessageRequestInteractionContext(
        kind = MessageRequestInteractionContext.Kind.SHARE_STORY,
        storyId = storyId,
        storyOwnerId = authorId,
        sharedContentId = storyId,
        sharedContentOwnerId = authorId,
        isStoryMention = true,
    )
    val conversationId = when (val route = coordinator.resolveRoute(recipientId, interaction)) {
        is DirectMessageRoute.Conversation -> route.id
        is DirectMessageRoute.ConversationDraft -> coordinator.activateConversationDraft(recipientId, route.threadId)
        is DirectMessageRoute.IncomingRequest -> coordinator.acceptIncomingThread(route.threadId).conversationId
        is DirectMessageRoute.OutgoingRequest -> {
            coordinator.appendRequestMessage(
                receiverId = recipientId,
                text = MomentsApplication.instance?.getString(R.string.chat_preview_shared_story).orEmpty(),
                messageType = MessageType.SHARED_STORY,
                interaction = interaction,
                requestedMessageId = deliveryMessageId,
            )
            null
        }
    }
    conversationId?.let {
        val exists = FirebaseFirestore.getInstance()
            .collection("conversations").document(it)
            .collection("messages").document(deliveryMessageId)
            .get().await().exists()
        if (exists) return
        ChatService.sendSharedStoryMessage(
            conversationId = it,
            senderId = authorId,
            story = story,
            shareText = MomentsApplication.instance?.getString(R.string.chat_preview_shared_story).orEmpty(),
            isStoryMention = true,
            messageId = deliveryMessageId,
        ).getOrThrow()
    }
}

/** ≡ `canNotifyStoryMention` — switch audiencia 1:1 con Swift. */
private suspend fun canNotifyStoryMention(
    mentionedUserId: String,
    storyAuthorId: String,
    audience: ContentAudience,
    customViewers: List<String>?,
    customListId: String?,
): Boolean = when (audience) {
    ContentAudience.ONLY_ME -> false
    ContentAudience.CUSTOM, ContentAudience.CUSTOM_LIST -> {
        if (!customViewers.isNullOrEmpty()) {
            canUserSeeContent(
                ownerId = storyAuthorId,
                viewerId = mentionedUserId,
                visibility = ContentVisibilityType.CUSTOM,
                customViewers = customViewers,
            )
        } else if (audience == ContentAudience.CUSTOM_LIST && !customListId.isNullOrBlank()) {
            val members = fetchCustomListMembers(listId = customListId, ownerId = storyAuthorId)
            canUserSeeContent(
                ownerId = storyAuthorId,
                viewerId = mentionedUserId,
                visibility = ContentVisibilityType.CUSTOM,
                customViewers = members,
            )
        } else {
            false
        }
    }
    ContentAudience.EVERYONE -> canUserSeeContent(
        ownerId = storyAuthorId,
        viewerId = mentionedUserId,
        visibility = ContentVisibilityType.EVERYONE,
    )
    ContentAudience.MUTUALS -> canUserSeeContent(
        ownerId = storyAuthorId,
        viewerId = mentionedUserId,
        visibility = ContentVisibilityType.MUTUALS,
    )
    ContentAudience.BEST_FRIENDS -> canUserSeeContent(
        ownerId = storyAuthorId,
        viewerId = mentionedUserId,
        visibility = ContentVisibilityType.BEST_FRIENDS,
    )
}

private suspend fun canUserSeeContent(
    ownerId: String,
    viewerId: String,
    visibility: ContentVisibilityType,
    customViewers: List<String>? = null,
): Boolean = ContentVisibilityService.canUserSeeContent(
    contentOwnerId = ownerId,
    viewerId = viewerId,
    contentType = visibility,
    customViewers = customViewers,
)

/**
 * ≡ `fetchCustomListMembers` —
 * `users/{ownerId}/customAudienceLists/{listId}.members`
 */
private suspend fun fetchCustomListMembers(listId: String, ownerId: String): List<String> {
    val snap = FirebaseFirestore.getInstance()
        .collection("users")
        .document(ownerId)
        .collection("customAudienceLists")
        .document(listId)
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    return (snap.data?.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}

/**
 * ≡ `extractUserIdFromMentionSticker` —
 * `interactionData?.userId` (en Android el userId vive en [StickerData]).
 */
fun extractUserIdFromMentionSticker(sticker: StickerData): String? =
    sticker.userId?.takeIf { it.isNotBlank() }
