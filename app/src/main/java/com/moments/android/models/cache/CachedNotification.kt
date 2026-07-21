package com.moments.android.models.cache

import java.util.Date

/** Notificación cacheada localmente. Room + conversiones ↔ MomentsNotification al montar la caché. */
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
)
