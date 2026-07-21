package com.moments.android.models.cache

import java.util.Date

/** Historia cacheada localmente (espejo de Story). Room + conversiones al montar la caché. */
data class CachedStory(
    val id: String,
    val authorId: String,
    val username: String,
    val profileImagePath: String? = null,
    val timestamp: Date,
    val expirationDate: Date,
    val expirationHours: Int? = null,
    val mediaItemData: ByteArray,               // MediaItem JSON
    val audience: String? = null,
    val customListId: String? = null,
    val text: String? = null,
    val textPositionData: ByteArray? = null,    // Point JSON
    val textStyle: String? = null,
    val textOverlayMetadataData: ByteArray? = null,
    val textOverlaysData: ByteArray? = null,
    val stickersData: ByteArray? = null,        // [StickerData] JSON
    val drawingData: ByteArray? = null,
    val aspectRatio: String? = null,
    val backgroundFrameURL: String? = null,
    val backgroundBlurredFrameURL: String? = null,
    val chainId: String? = null,
    val chainPosition: Int? = null,
    val chainTitle: String? = null,
    val cachedAt: Date = Date(),
)
