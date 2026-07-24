package com.moments.android.views.messaging.media

import com.moments.android.models.EnhancedMessage
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata

data class ChatMediaOverlayPayload(
    val textOverlayLive: Boolean? = null,
    val textOverlays: List<StoryTextOverlayMetadata>? = null,
    val stickers: List<StickerData>? = null,
    val drawingData: ByteArray? = null,
) {
    val isEmpty: Boolean get() = textOverlays.isNullOrEmpty() && stickers.isNullOrEmpty() && drawingData == null

    companion object { val empty = ChatMediaOverlayPayload() }
}

val EnhancedMessage.usesLiveTextOverlay: Boolean
    get() = !textOverlays.isNullOrEmpty() || textOverlayLive == true

val EnhancedMessage.resolvedTextOverlays: List<StoryTextOverlayMetadata>
    get() = textOverlays
        .orEmpty()
        .filter { it.text.isNotBlank() }
        .sortedWith(compareBy<StoryTextOverlayMetadata> { it.layerOrder }.thenBy { it.id })

val EnhancedMessage.resolvedStickers: List<StickerData>
    get() = stickers.orEmpty()
