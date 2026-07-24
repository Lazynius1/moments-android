package com.moments.android.views.creator.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.StickerData
import com.moments.android.notifications.services.NotificationService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomListDetails
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.privacy.ContentVisibilityService
import com.moments.android.services.privacy.ContentVisibilityType
import com.moments.android.utilities.momentsPress
import kotlinx.coroutines.launch

/** Port de `glow(color:radius:)` de SwiftUI. */
fun Modifier.glow(color: Color, radius: Dp): Modifier =
    shadow(radius / 3, spotColor = color)
        .shadow(radius / 3, spotColor = color)
        .shadow(radius / 3, spotColor = color)

/** Port de `pressAnimation()`; delega al estilo compartido del proyecto. */
fun Modifier.pressAnimation(): Modifier = momentsPress()

/** Conserva el alias con la errata que existe en el fuente Swift. */
fun Modifier.pressAnimatioon(): Modifier = pressAnimation()

/** Fallback Android de `MeshGradient` para los consumidores de este archivo. */
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
    androidx.compose.foundation.layout.Box(
        modifier.background(Brush.linearGradient(listOf(start, end))),
    )
}

/** Resultado equivalente a `StoryMentionNotificationResult`. */
data class StoryMentionNotificationResult(
    val sentUserIds: List<String>,
    val skippedOutsideAudienceUserIds: List<String>,
)

/** Equivalente de `sendMentionNotificationsForStory(storyId:stickers:)`. */
fun sendMentionNotificationsForStory(
    storyId: String,
    stickers: List<StickerData>,
) {
    val authorId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
 * Port de la sobrecarga Swift que sólo entrega notificaciones si la persona
 * mencionada puede ver la historia bajo su audiencia efectiva.
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

    mentionedUserIds.forEach { userId ->
        if (!canNotifyStoryMention(userId, storyAuthorId, audience, customViewers, customListId)) {
            skipped += userId
            return@forEach
        }
        NotificationService.sendStoryMentionNotification(userId, storyId, storyAuthorId)
        sent += userId
    }
    return StoryMentionNotificationResult(sent, skipped)
}

private suspend fun canNotifyStoryMention(
    mentionedUserId: String,
    storyAuthorId: String,
    audience: ContentAudience,
    customViewers: List<String>?,
    customListId: String?,
): Boolean {
    val visibility = when (audience) {
        ContentAudience.EVERYONE -> ContentVisibilityType.EVERYONE
        ContentAudience.MUTUALS -> ContentVisibilityType.MUTUALS
        ContentAudience.BEST_FRIENDS -> ContentVisibilityType.BEST_FRIENDS
        ContentAudience.ONLY_ME -> return false
        ContentAudience.CUSTOM, ContentAudience.CUSTOM_LIST -> ContentVisibilityType.CUSTOM
    }
    val allowedUsers = when (audience) {
        ContentAudience.CUSTOM -> customViewers.orEmpty()
        ContentAudience.CUSTOM_LIST -> customViewers.orEmpty().ifEmpty {
            val id = customListId?.takeIf(String::isNotBlank) ?: return false
            runCatching {
                FirestoreService().fetchCustomListDetails(id, storyAuthorId).members
            }.getOrDefault(emptyList())
        }
        else -> null
    }
    if (visibility == ContentVisibilityType.CUSTOM && allowedUsers.isNullOrEmpty()) return false
    return ContentVisibilityService.canUserSeeContent(
        contentOwnerId = storyAuthorId,
        viewerId = mentionedUserId,
        contentType = visibility,
        customViewers = allowedUsers,
    )
}

/** Equivalente de `extractUserIdFromMentionSticker`. */
fun extractUserIdFromMentionSticker(sticker: StickerData): String? =
    sticker.userId?.takeIf { sticker.type == "mention" && it.isNotBlank() }
