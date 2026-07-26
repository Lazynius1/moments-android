package com.moments.android.models.cache

import com.moments.android.models.MediaItem
import com.moments.android.models.Point
import com.moments.android.models.StickerData
import com.moments.android.models.Story
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.toMap
import com.moments.android.views.creator.components.resolvedTextOverlayMetadata
import com.moments.android.views.creator.components.resolvedTextOverlays
import org.json.JSONArray
import org.json.JSONObject
import java.util.Arrays
import java.util.Date

/**
 * Port de `Models/Cache/CachedStory.swift`.
 * Blobs JSON ≡ `Data` / JSONEncoder en iOS.
 */
data class CachedStory(
    val id: String,
    val authorId: String,
    val username: String,
    val profileImagePath: String? = null,
    val timestamp: Date,
    val expirationDate: Date,
    val expirationHours: Int? = null,
    val mediaItemData: ByteArray,
    val audience: String? = null,
    val customListId: String? = null,
    val text: String? = null,
    val textPositionData: ByteArray? = null,
    val textStyle: String? = null,
    val textOverlayMetadataData: ByteArray? = null,
    val textOverlaysData: ByteArray? = null,
    val stickersData: ByteArray? = null,
    val drawingData: ByteArray? = null,
    val aspectRatio: String? = null,
    val backgroundFrameURL: String? = null,
    val backgroundBlurredFrameURL: String? = null,
    val chainId: String? = null,
    val chainPosition: Int? = null,
    val chainTitle: String? = null,
    val cachedAt: Date = Date(),
) {
    /** ≡ iOS `toStory()`. */
    fun toStory(): Story {
        val mediaItem = decodeMediaItem(mediaItemData)
            ?: MediaItem(type = MediaItem.MediaType.IMAGE, url = "")
        val textPosition = textPositionData?.let(::decodePoint)
        val overlayMetadata = textOverlayMetadataData?.let(::decodeOverlayMetadata)
        val textOverlays = textOverlaysData?.let(::decodeOverlayList)
        val stickers = stickersData?.let(::decodeStickers)

        return Story(
            id = id,
            authorId = authorId,
            username = username,
            mediaItem = mediaItem,
            duration = 15.0,
            timestamp = timestamp,
            expirationHours = expirationHours ?: if (chainId != null) 48 else 24,
            expirationDate = expirationDate,
            profileImagePath = profileImagePath,
            audience = audience,
            customListId = customListId,
            text = text,
            textPosition = textPosition,
            textStyle = overlayMetadata?.styleRaw ?: textStyle,
            textPositionNormX = overlayMetadata?.let { it.normalizedPosition.x },
            textPositionNormY = overlayMetadata?.let { it.normalizedPosition.y },
            textColorHex = overlayMetadata?.colorHex,
            textFontSize = overlayMetadata?.fontSize,
            textAlignment = overlayMetadata?.alignmentRaw,
            textBackgroundFill = overlayMetadata?.backgroundFillRaw,
            textStroke = overlayMetadata?.strokeRaw,
            textVisualEffect = overlayMetadata?.visualEffectRaw,
            textMotion = overlayMetadata?.motionRaw,
            forcesAllCaps = overlayMetadata?.forcesAllCaps,
            textLayerOrder = overlayMetadata?.layerOrder,
            textOverlayLive = overlayMetadata?.isLiveOverlay,
            textOverlays = textOverlays,
            stickers = stickers,
            drawingData = drawingData,
            aspectRatio = aspectRatio,
            backgroundFrameURL = backgroundFrameURL,
            backgroundBlurredFrameURL = backgroundBlurredFrameURL,
            chainId = chainId,
            chainPosition = chainPosition,
            chainTitle = chainTitle,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedStory) return false
        return id == other.id &&
            authorId == other.authorId &&
            username == other.username &&
            profileImagePath == other.profileImagePath &&
            timestamp == other.timestamp &&
            expirationDate == other.expirationDate &&
            expirationHours == other.expirationHours &&
            Arrays.equals(mediaItemData, other.mediaItemData) &&
            audience == other.audience &&
            customListId == other.customListId &&
            text == other.text &&
            Arrays.equals(textPositionData, other.textPositionData) &&
            textStyle == other.textStyle &&
            Arrays.equals(textOverlayMetadataData, other.textOverlayMetadataData) &&
            Arrays.equals(textOverlaysData, other.textOverlaysData) &&
            Arrays.equals(stickersData, other.stickersData) &&
            Arrays.equals(drawingData, other.drawingData) &&
            aspectRatio == other.aspectRatio &&
            backgroundFrameURL == other.backgroundFrameURL &&
            backgroundBlurredFrameURL == other.backgroundBlurredFrameURL &&
            chainId == other.chainId &&
            chainPosition == other.chainPosition &&
            chainTitle == other.chainTitle &&
            cachedAt == other.cachedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + authorId.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + (profileImagePath?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + expirationDate.hashCode()
        result = 31 * result + (expirationHours ?: 0)
        result = 31 * result + Arrays.hashCode(mediaItemData)
        result = 31 * result + (audience?.hashCode() ?: 0)
        result = 31 * result + (customListId?.hashCode() ?: 0)
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (textPositionData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (textStyle?.hashCode() ?: 0)
        result = 31 * result + (textOverlayMetadataData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (textOverlaysData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (stickersData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (drawingData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (aspectRatio?.hashCode() ?: 0)
        result = 31 * result + (backgroundFrameURL?.hashCode() ?: 0)
        result = 31 * result + (backgroundBlurredFrameURL?.hashCode() ?: 0)
        result = 31 * result + (chainId?.hashCode() ?: 0)
        result = 31 * result + (chainPosition ?: 0)
        result = 31 * result + (chainTitle?.hashCode() ?: 0)
        result = 31 * result + cachedAt.hashCode()
        return result
    }

    companion object {
        /** ≡ iOS `fromStory(_:)`. */
        fun fromStory(story: Story): CachedStory? {
            val id = story.id ?: return null
            val mediaItemData = encodeMap(story.mediaItem.toMap()) ?: ByteArray(0)
            val textPositionData = story.textPosition?.let { encodeMap(it.toMap()) }
            val overlay = story.resolvedTextOverlayMetadata
            val textOverlayMetadataData = overlay?.let { encodeMap(it.toMap()) }
            val overlays = story.resolvedTextOverlays
            val textOverlaysData = if (overlays.isEmpty()) {
                null
            } else {
                runCatching {
                    JSONArray().apply {
                        overlays.forEach { put(JSONObject(it.toMap())) }
                    }.toString().toByteArray()
                }.getOrNull()
            }
            val stickersData = story.stickers?.let { list ->
                runCatching {
                    JSONArray().apply {
                        list.forEach { put(JSONObject(it.toMap())) }
                    }.toString().toByteArray()
                }.getOrNull()
            }
            return CachedStory(
                id = id,
                authorId = story.authorId,
                username = story.username,
                profileImagePath = story.profileImagePath,
                timestamp = story.timestamp,
                expirationDate = story.expirationDate,
                expirationHours = story.expirationHours,
                mediaItemData = mediaItemData,
                audience = story.audience,
                customListId = story.customListId,
                text = story.text,
                textPositionData = textPositionData,
                textStyle = story.textStyle,
                textOverlayMetadataData = textOverlayMetadataData,
                textOverlaysData = textOverlaysData,
                stickersData = stickersData,
                drawingData = story.drawingData,
                aspectRatio = story.aspectRatio,
                backgroundFrameURL = story.backgroundFrameURL,
                backgroundBlurredFrameURL = story.backgroundBlurredFrameURL,
                chainId = story.chainId,
                chainPosition = story.chainPosition,
                chainTitle = story.chainTitle,
            )
        }

        private fun encodeMap(map: Map<String, Any>): ByteArray? =
            runCatching { JSONObject(map).toString().toByteArray() }.getOrNull()

        private fun decodeMediaItem(data: ByteArray): MediaItem? = runCatching {
            MediaItem.from(JSONObject(String(data)).toAnyMap())
        }.getOrNull()

        private fun decodePoint(data: ByteArray): Point? = runCatching {
            Point.from(JSONObject(String(data)).toAnyMap())
        }.getOrNull()

        private fun decodeOverlayMetadata(data: ByteArray): StoryTextOverlayMetadata? = runCatching {
            StoryTextOverlayMetadata.from(JSONObject(String(data)).toAnyMap())
        }.getOrNull()

        private fun decodeOverlayList(data: ByteArray): List<StoryTextOverlayMetadata>? = runCatching {
            val arr = JSONArray(String(data))
            (0 until arr.length()).map { i ->
                StoryTextOverlayMetadata.from(arr.getJSONObject(i).toAnyMap())
            }
        }.getOrNull()

        private fun decodeStickers(data: ByteArray): List<StickerData>? = runCatching {
            val arr = JSONArray(String(data))
            (0 until arr.length()).map { i ->
                StickerData.from(arr.getJSONObject(i).toAnyMap())
            }
        }.getOrNull()

        private fun JSONObject.toAnyMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
            when (val value = opt(key)) {
                null, JSONObject.NULL -> null
                is JSONObject -> value.toAnyMap()
                is JSONArray -> (0 until value.length()).map { i ->
                    when (val item = value.opt(i)) {
                        null, JSONObject.NULL -> null
                        is JSONObject -> item.toAnyMap()
                        else -> item
                    }
                }
                else -> value
            }
        }
    }
}
