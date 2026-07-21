package com.moments.android.models.cache

import java.util.Date

/**
 * Mensaje de chat cacheado localmente. Room + conversiones ↔ EnhancedMessage al portar Messaging.
 * type/status se guardan como raw string; los blobs Data son JSON.
 */
data class CachedMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val typeString: String,
    val content: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaObjectPath: String? = null,
    val thumbnailObjectPath: String? = null,
    val mediaEncryptionData: ByteArray? = null,
    val thumbnailEncryptionData: ByteArray? = null,
    val mediaBatchId: String? = null,
    val duration: Double? = null,
    val audioWaveformData: ByteArray? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Date,
    val statusString: String,
    val isRead: Boolean,
    val isDeleted: Boolean,
    val deletedAt: Date? = null,
    val editedAt: Date? = null,
    val reactionsData: ByteArray? = null,
    val replyTo: String? = null,
    val expirationDate: Date? = null,
    val isViewed: Boolean,
    val storyReplyDataEncoded: ByteArray? = null,
    val sharedMomentDataEncoded: ByteArray? = null,
    val sharedStoryDataEncoded: ByteArray? = null,
    val textOverlayLive: Boolean? = null,
    val textOverlaysData: ByteArray? = null,
    val stickersData: ByteArray? = null,
    val drawingData: ByteArray? = null,
    val viewedBy: List<String>? = null,
    val lastSyncedAt: Date = Date(),
    val isVanishModeMessage: Boolean = false,
    val vanishedFor: List<String> = emptyList(),
    val vanishExpiresAt: Date? = null,
)
