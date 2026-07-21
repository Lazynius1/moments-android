package com.moments.android.models.cache

import java.util.Date

/** Momento cacheado localmente (espejo de Moment). Room + conversiones al montar la caché. */
data class CachedMoment(
    val momentId: String,
    val authorId: String,
    val username: String,
    val content: String,
    val imagePath: String? = null,
    val videoUrl: String? = null,
    val timestamp: Date,
    val commentCount: Int? = 0,
    val profileImagePath: String? = null,
    val location: String? = null,
    val audience: String? = null,
    val aspectRatio: String? = null,
    val thumbnailUrl: String? = null,
    val videoDuration: Double? = null,
    val videoFileSize: Long? = null,
    val videoResolution: String? = null,
    val customListId: String? = null,
    val disableComments: Boolean? = false,
    val hideLikeCounts: Boolean? = false,
    val allowSharing: Boolean? = true,
    val scheduledDate: Date? = null,
    val isPinned: Boolean? = null,
    val pinnedAt: Date? = null,
    val gridPreviewScale: Double? = null,
    val gridPreviewOffsetX: Double? = null,
    val gridPreviewOffsetY: Double? = null,
    val gridPreviewFitMode: String? = null,
    val gridPreviewBackground: String? = null,
    val hasHiddenLayers: Boolean? = false,
    val hiddenLayerCount: Int? = 0,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val reactionsData: ByteArray? = null,      // [String:[String]] JSON
    val mediaItemsData: ByteArray? = null,     // [MediaItem] JSON
    val taggedUsersData: ByteArray? = null,    // [String] JSON
    val mentionedUsersData: ByteArray? = null, // [String] JSON
    val lastSyncedAt: Date = Date(),
    val feedSection: String = "feed", // "feed" | "explore" | "profile"
)
