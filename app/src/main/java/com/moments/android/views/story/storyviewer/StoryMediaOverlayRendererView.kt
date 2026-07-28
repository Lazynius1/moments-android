package com.moments.android.views.story.storyviewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.views.story.StoryDeckGestureGate
import com.moments.android.views.story.StoryInteractiveStickerLayer
import com.moments.android.views.story.storystickers.StoryStickerRendererLayer

/**
 * ≡ iOS: cuando false, stickers no reciben taps (ViewOnce).
 * Los clickables / pans leen este Local.
 */
val LocalStoryStickerHitTesting = staticCompositionLocalOf { true }

/**
 * Port de `StoryMediaOverlayRendererView.swift`.
 * Reveal se mantiene separado (scratch overlay).
 *
 * Drawing / texto / stickers son hermanos en el mismo Box para que
 * `layerOrder` / `zIndex` interleven como el ZStack iOS.
 */
@Composable
fun StoryMediaOverlayRendererView(
    textOverlays: List<StoryTextOverlayMetadata>,
    stickers: List<StickerData>,
    drawingData: ByteArray?,
    storyId: String,
    userId: String,
    replayToken: Int = 0,
    gestureGate: StoryDeckGestureGate? = null,
    reportsDeckInteractionExclusion: Boolean = true,
    allowsStickerHitTesting: Boolean = true,
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {},
    onMomentTap: (momentId: String, authorId: String) -> Unit = { _, _ -> },
    onMentionTap: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drawing = remember(drawingData) {
        drawingData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val exclusionEnabled = reportsDeckInteractionExclusion && allowsStickerHitTesting

    BoxWithConstraints(modifier) {
        val container = Size(
            constraints.maxWidth.toFloat().coerceAtLeast(1f),
            constraints.maxHeight.toFloat().coerceAtLeast(1f),
        )

        // ≡ drawingImage zIndex(-1), fill+clipped, allowsHitTesting(false)
        drawing?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(-1f),
                contentScale = ContentScale.Crop,
            )
        }

        CompositionLocalProvider(LocalStoryStickerHitTesting provides allowsStickerHitTesting) {
            // Frames + stickers como hermanos (sin Box fillMaxSize intermedio)
            StoryInteractiveStickerLayer(
                storyId = storyId,
                stickers = stickers,
                onPauseStory = onPauseStory,
                onResumeStory = onResumeStory,
                gestureGate = gestureGate,
                reportsDeckInteractionExclusion = exclusionEnabled,
                containerWidthPx = container.width,
                containerHeightPx = container.height,
            )
            StoryStickerRendererLayer(
                storyId = storyId,
                userId = userId,
                stickers = stickers,
                gestureGate = gestureGate,
                reportsDeckInteractionExclusion = exclusionEnabled,
                onPauseStory = onPauseStory,
                onResumeStory = onResumeStory,
                onMentionTap = onMentionTap,
                onMomentTap = onMomentTap,
                containerWidthPx = container.width,
                containerHeightPx = container.height,
            )
        }

        textOverlays.forEach { overlay ->
            StoryLiveTextOverlayView(
                metadata = overlay,
                replayToken = replayToken,
                containerSize = container,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(overlay.layerOrder.toFloat()),
            )
        }
    }
}
