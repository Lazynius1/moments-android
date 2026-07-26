package com.moments.android.views.messaging.media

import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.views.messaging.core.EnhancedMessage
import java.util.Arrays

/**
 * Port de `Views/Messaging/Media/ChatMediaOverlayPayload.swift`.
 * En Android los stickers se pasan como [StickerData] al renderer Compose
 * (iOS convierte a `StickerItem` vía shim `Story` + UIKit).
 */
data class ChatMediaOverlayPayload(
    val textOverlayLive: Boolean? = null,
    val textOverlays: List<StoryTextOverlayMetadata>? = null,
    val stickers: List<StickerData>? = null,
    val drawingData: ByteArray? = null,
) {
    val isEmpty: Boolean
        get() = textOverlays.isNullOrEmpty() && stickers.isNullOrEmpty() && drawingData == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMediaOverlayPayload) return false
        return textOverlayLive == other.textOverlayLive &&
            textOverlays == other.textOverlays &&
            stickers == other.stickers &&
            Arrays.equals(drawingData, other.drawingData)
    }

    override fun hashCode(): Int {
        var result = textOverlayLive?.hashCode() ?: 0
        result = 31 * result + (textOverlays?.hashCode() ?: 0)
        result = 31 * result + (stickers?.hashCode() ?: 0)
        result = 31 * result + (drawingData?.let { Arrays.hashCode(it) } ?: 0)
        return result
    }

    companion object {
        val empty = ChatMediaOverlayPayload()
    }
}

/** ≡ iOS `EnhancedMessage.usesLiveTextOverlay`. */
val EnhancedMessage.usesLiveTextOverlay: Boolean
    get() = !textOverlays.isNullOrEmpty() || textOverlayLive == true

/** ≡ iOS `EnhancedMessage.resolvedTextOverlays` (filtro blank + sort layerOrder/id). */
val EnhancedMessage.resolvedTextOverlays: List<StoryTextOverlayMetadata>
    get() = textOverlays
        .orEmpty()
        .filter { it.text.trim().isNotEmpty() }
        .sortedWith(compareBy<StoryTextOverlayMetadata> { it.layerOrder }.thenBy { it.id })

/**
 * Stickers del mensaje listos para overlay.
 * ≡ iOS `resolvedStickerItems` en contrato de datos; sin conversión UIKit→StickerItem.
 */
val EnhancedMessage.resolvedStickers: List<StickerData>
    get() = stickers.orEmpty()
