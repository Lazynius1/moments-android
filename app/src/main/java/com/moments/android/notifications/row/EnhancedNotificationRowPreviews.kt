package com.moments.android.notifications.row

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.components.NotificationMomentThumbnail
import com.moments.android.notifications.components.NotificationStoryThumbnailView
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Port de EnhancedNotificationRow+Previews.swift
 *
 * Helpers de resolución + fetch story/moment.
 * UI thumbs (momentTrailing/storyTrailing) = cableado Compose de setupPreviews iOS.
 */
object EnhancedNotificationRowPreviews {

    fun isModerationNotification(group: NotificationGroup): Boolean =
        group.notifications.firstOrNull()?.type == NotificationType.MEDIA_MODERATION

    /** ≡ isStoryMention(_:) */
    fun isStoryMention(notification: MomentsNotification): Boolean =
        notification.type == NotificationType.MENTION &&
            (notification.mentionContext == "story" || notification.storyId != null)

    /** ≡ isMomentMention(_:) — !storyMention && momentId != nil */
    fun isMomentMention(notification: MomentsNotification): Boolean =
        notification.type == NotificationType.MENTION &&
            !isStoryMention(notification) &&
            notification.momentId != null

    /** ≡ storyAuthorId(for:) */
    fun storyAuthorId(notification: MomentsNotification): String =
        notification.storyAuthorId
            ?: notification.targetAuthorId
            ?: notification.senderId

    /** ≡ momentAuthorId(for:) */
    fun momentAuthorId(notification: MomentsNotification): String? =
        notification.targetAuthorId

    /**
     * ≡ resolvedStoryAuthorId(for:)
     * En storyReaction la historia es del usuario actual (quien recibe la reacción).
     */
    fun resolvedStoryAuthorId(notification: MomentsNotification): String {
        val fromStory = notification.storyAuthorId?.trim().orEmpty()
        if (fromStory.isNotEmpty()) return fromStory
        if (notification.type == NotificationType.STORY_REACTION) {
            return FirebaseAuth.getInstance().currentUser?.uid ?: notification.senderId
        }
        return notification.targetAuthorId ?: notification.senderId
    }

    /** ≡ nonEmptyString(_:) */
    fun nonEmptyString(value: Any?): String? {
        val string = value as? String ?: return null
        val trimmed = string.trim()
        return trimmed.takeIf { it.isNotEmpty() }
    }

    /**
     * ≡ storyPreviewURL(from:)
     * mediaItem.type image → url / imagePath
     * mediaItem.type video → thumbnailUrl / backgroundFrameURL / backgroundBlurredFrameURL
     * else → cascada fallbacks
     */
    @Suppress("UNCHECKED_CAST")
    fun storyPreviewURL(data: Map<String, Any?>): String? {
        val mediaItem = data["mediaItem"] as? Map<String, Any?>
        val mediaType = mediaItem?.get("type") as? String

        if (mediaType == "image") {
            return nonEmptyString(mediaItem?.get("url"))
                ?: nonEmptyString(data["imagePath"])
        }

        if (mediaType == "video") {
            return nonEmptyString(mediaItem?.get("thumbnailUrl"))
                ?: nonEmptyString(data["backgroundFrameURL"])
                ?: nonEmptyString(data["backgroundBlurredFrameURL"])
        }

        return nonEmptyString(data["imagePath"])
            ?: nonEmptyString(mediaItem?.get("thumbnailUrl"))
            ?: nonEmptyString(data["backgroundFrameURL"])
            ?: nonEmptyString(data["backgroundBlurredFrameURL"])
            ?: nonEmptyString(mediaItem?.get("url"))
    }

    /** ≡ fetchStoryPreview(storyId:authorId:) → users/{uid}/stories/{id} */
    suspend fun fetchStoryPreview(storyId: String, authorId: String): String? {
        val userId = authorId.trim()
        if (userId.isEmpty() || storyId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val snap = FirebaseFirestore.getInstance()
                    .collection("users").document(userId)
                    .collection("stories").document(storyId)
                    .get()
                    .await()
                val data = snap.data ?: return@runCatching null
                @Suppress("UNCHECKED_CAST")
                storyPreviewURL(data as Map<String, Any?>)
            }.getOrNull()
        }
    }

    /**
     * ≡ fetchMomentPreview (en +Follow.swift iOS) — owner = momentAuthorId ?? currentUser.
     */
    suspend fun fetchMomentPreview(momentId: String, authorId: String?): String? {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val owner = authorId?.trim().orEmpty().ifEmpty { currentUid }
        return withContext(Dispatchers.IO) {
            runCatching {
                FirestoreService().fetchMoment(momentId, owner).previewImageURLString
            }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Thumb momento — setupPreviews like/comment/reaction/photoTag/momentMention.
     */
    @Composable
    fun momentTrailing(
        group: NotificationGroup,
        @Suppress("UNUSED_PARAMETER") viewModel: NotificationsViewModel,
        isDark: Boolean,
        onTap: (() -> Unit)? = null,
    ) {
        val first = group.notifications.first()
        var imagePath by remember(first.id) { mutableStateOf<String?>(null) }
        var loadFailed by remember(first.id) { mutableStateOf(false) }

        LaunchedEffect(first.momentId, first.targetAuthorId) {
            val momentId = first.momentId ?: return@LaunchedEffect
            val path = fetchMomentPreview(momentId, momentAuthorId(first))
            if (path != null) {
                imagePath = path
                loadFailed = false
            } else {
                loadFailed = true
                imagePath = null
            }
        }

        Box(modifier = if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier) {
            NotificationMomentThumbnail(
                imageUrl = if (loadFailed) null else imagePath,
                isDark = isDark,
            )
        }
    }

    /**
     * Thumb story — setupPreviews storyReaction / storyMention:
     * 1) storyPreviewUrl del backend si viene
     * 2) si no, fetchStoryPreview con resolvedStoryAuthorId
     */
    @Composable
    fun storyTrailing(
        group: NotificationGroup,
        @Suppress("UNUSED_PARAMETER") viewModel: NotificationsViewModel,
        isDark: Boolean,
    ) {
        val first = group.notifications.first()
        var imagePath by remember(first.id) { mutableStateOf<String?>(null) }
        var loadFailed by remember(first.id) { mutableStateOf(false) }
        var isLoading by remember(first.id) { mutableStateOf(true) }

        LaunchedEffect(first.id, first.storyId, first.storyPreviewUrl, first.storyAuthorId) {
            val attached = first.storyPreviewUrl?.trim().orEmpty()
            if (attached.isNotEmpty()) {
                imagePath = attached
                loadFailed = false
                isLoading = false
                return@LaunchedEffect
            }
            val storyId = first.storyId?.trim().orEmpty()
            if (storyId.isEmpty()) {
                loadFailed = true
                isLoading = false
                return@LaunchedEffect
            }
            isLoading = true
            val path = fetchStoryPreview(storyId, resolvedStoryAuthorId(first))
            imagePath = path
            loadFailed = path == null
            isLoading = false
        }

        NotificationStoryThumbnailView(
            imagePath = imagePath,
            reaction = first.reaction,
            isDark = isDark,
            loadFailed = loadFailed || (!isLoading && imagePath.isNullOrBlank()),
        )
    }
}
