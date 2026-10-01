package com.moments.android.views.messaging.media

import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.views.messaging.core.EnhancedMessage
import java.util.Arrays
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moments.android.views.story.InteractiveRevealSticker
import com.moments.android.views.story.RevealSurfaceView
import com.moments.android.views.story.rememberRevealState

@Composable
fun ChatMessageRevealOverlay(message: EnhancedMessage, interactive: Boolean = false, modifier: Modifier = Modifier) {
    val revealed = rememberRevealState(message.id)
    val reveal = message.stickers?.firstOrNull { it.type == "reveal" }
    if (!revealed && reveal != null) {
        if (interactive) {
            InteractiveRevealSticker(storyId = message.id,
                revealType = reveal.revealType, revealPattern = reveal.revealPattern,
                revealPrimaryColor = reveal.revealPrimaryColor,
                revealSecondaryColor = reveal.revealSecondaryColor,
                revealEffectColor = reveal.revealEffectColor,
                reportsDeckInteractionExclusion = false, modifier = modifier)
        } else {
            RevealSurfaceView(type = reveal.revealType, pattern = reveal.revealPattern,
                primaryColor = reveal.revealPrimaryColor, secondaryColor = reveal.revealSecondaryColor,
                effectColor = reveal.revealEffectColor, effectsActive = false, modifier = modifier)
        }
    }
}

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

@Composable
fun ChatMessageStaticOverlay(message: EnhancedMessage, interactive: Boolean = false, animates: Boolean = false, modifier: Modifier = Modifier) {
    val music = message.stickers?.firstOrNull { it.music != null }?.music
    val playing = if(animates && music != null) com.moments.android.views.creator.components.music.StoryMusicPlayback(music, null, true, 0.0) else false
    androidx.compose.runtime.CompositionLocalProvider(com.moments.android.views.creator.components.music.LocalStoryMusicPlaying provides playing) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        com.moments.android.views.story.storyviewer.StoryCanvasOverlayView(
            textOverlays = message.resolvedTextOverlays, stickers = message.stickers.orEmpty(),
            storyId = message.id, userId = message.senderId,
            renderingMode = if (animates) com.moments.android.views.story.storyviewer.StoryOverlayRenderingMode.LIVE else com.moments.android.views.story.storyviewer.StoryOverlayRenderingMode.THUMBNAIL,
            modifier = Modifier.matchParentSize(),
        )
        ChatMessageRevealOverlay(message, interactive, Modifier.matchParentSize())
    }
}
}
