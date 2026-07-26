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
import com.moments.android.notifications.services.NotificationService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.privacy.ContentVisibilityService
import com.moments.android.services.privacy.ContentVisibilityType
import com.moments.android.utilities.momentsPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
        withContext(Dispatchers.Main) {
            NotificationService.sendStoryMentionNotification(userId, storyId, storyAuthorId)
        }
        sent += userId
    }

    return StoryMentionNotificationResult(
        sentUserIds = sent,
        skippedOutsideAudienceUserIds = skipped,
    )
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
