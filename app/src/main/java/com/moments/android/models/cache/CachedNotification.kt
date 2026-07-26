package com.moments.android.models.cache

import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import java.util.Date
import java.util.UUID

/**
 * Port de `Models/Cache/CachedNotification.swift`.
 * SwiftData @Model → data class; conversiones ↔ [MomentsNotification].
 */
data class CachedNotification(
    val id: String,
    val type: String,
    val senderId: String,
    val senderUsername: String,
    val timestamp: Date,
    val isPending: Boolean,
    val title: String? = null,
    val message: String? = null,
    val downloadURL: String? = null,
    val momentId: String? = null,
    val visitCount: Int? = null,
    val storyId: String? = null,
    val storyAuthorId: String? = null,
    val storyPreviewUrl: String? = null,
    val reaction: String? = null,
    val reactionCount: Int? = null,
    val commentId: String? = null,
    val echoId: String? = null,
    val moderationScope: String? = null,
    val totalParts: Int? = null,
    val chainRole: String? = null,
    val lastSyncedAt: Date = Date(),
) {
    /** ≡ iOS `toNotification()`; tipo desconocido → [NotificationType.NEW_FOLLOWER]. */
    fun toNotification(): MomentsNotification = MomentsNotification(
        id = id,
        type = NotificationType.from(type) ?: NotificationType.NEW_FOLLOWER,
        senderId = senderId,
        senderUsername = senderUsername,
        timestamp = timestamp,
        isPending = isPending,
        title = title,
        message = message,
        downloadURL = downloadURL,
        momentId = momentId,
        visitCount = visitCount,
        storyId = storyId,
        storyAuthorId = storyAuthorId,
        storyPreviewUrl = storyPreviewUrl,
        reaction = reaction,
        reactionCount = reactionCount,
        commentId = commentId,
        echoId = echoId,
        moderationScope = moderationScope,
        totalParts = totalParts,
        chainRole = chainRole,
    )

    companion object {
        /** ≡ iOS `CachedNotification.from(_:)`. */
        fun from(notification: MomentsNotification): CachedNotification = CachedNotification(
            id = notification.id ?: UUID.randomUUID().toString(),
            type = notification.type.raw,
            senderId = notification.senderId,
            senderUsername = notification.senderUsername,
            timestamp = notification.timestamp,
            isPending = notification.isPending,
            title = notification.title,
            message = notification.message,
            downloadURL = notification.downloadURL,
            momentId = notification.momentId,
            visitCount = notification.visitCount,
            storyId = notification.storyId,
            storyAuthorId = notification.storyAuthorId,
            storyPreviewUrl = notification.storyPreviewUrl,
            reaction = notification.reaction,
            reactionCount = notification.reactionCount,
            commentId = notification.commentId,
            echoId = notification.echoId,
            moderationScope = notification.moderationScope,
            totalParts = notification.totalParts,
            chainRole = notification.chainRole,
        )
    }
}
