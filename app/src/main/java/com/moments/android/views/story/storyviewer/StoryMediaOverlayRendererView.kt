package com.moments.android.views.story.storyviewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.moments.android.models.MediaItem
import com.moments.android.models.StickerData
import com.moments.android.models.Story
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.views.creator.components.resolvedTextOverlays
import com.moments.android.views.story.RevealSurfaceView
import com.moments.android.views.story.StoryDeckGestureGate
import com.moments.android.views.story.StoryInteractiveStickerLayer
import com.moments.android.views.story.storystickers.StoryStickerRendererLayer

/**
 * ≡ iOS: cuando false, stickers no reciben taps (ViewOnce).
 * Los clickables / pans leen este Local.
 */
val LocalStoryStickerHitTesting = staticCompositionLocalOf { true }

enum class StoryOverlayRenderingMode { LIVE, THUMBNAIL }

enum class StoryRevealThumbnailPolicy { CONCEALED, EXPOSED }

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
    renderingMode: StoryOverlayRenderingMode = StoryOverlayRenderingMode.LIVE,
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
                isThumbnail = renderingMode == StoryOverlayRenderingMode.THUMBNAIL,
                containerWidthPx = container.width,
                containerHeightPx = container.height,
            )
            StoryStickerRendererLayer(
                storyId = storyId,
                userId = userId,
                stickers = stickers,
                gestureGate = gestureGate,
                reportsDeckInteractionExclusion = exclusionEnabled,
                isThumbnail = renderingMode == StoryOverlayRenderingMode.THUMBNAIL,
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
                animates = renderingMode == StoryOverlayRenderingMode.LIVE,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(overlay.layerOrder.toFloat()),
            )
        }
    }
}

/**
 * Superficie común para chats, archivo, cadenas, destacados y notificaciones.
 * Mantiene el canvas 9:16 y congela reproducción, GIF y motion.
 */
@Composable
fun StoryStaticPreviewSurface(
    story: Story,
    modifier: Modifier = Modifier,
    revealPolicy: StoryRevealThumbnailPolicy = StoryRevealThumbnailPolicy.CONCEALED,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val targetWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val targetHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val storyRatio = 9f / 16f
        val targetRatio = targetWidthPx / targetHeightPx
        val canvasWidthPx: Float
        val canvasHeightPx: Float
        if (targetRatio > storyRatio) {
            canvasWidthPx = targetWidthPx
            canvasHeightPx = targetWidthPx / storyRatio
        } else {
            canvasHeightPx = targetHeightPx
            canvasWidthPx = targetHeightPx * storyRatio
        }
        val canvasWidth = with(density) { canvasWidthPx.toDp() }
        val canvasHeight = with(density) { canvasHeightPx.toDp() }
        // Los thumbnails pueden ser más estrechos que el tamaño base de un
        // sticker (p. ej. el slider mide 260). SwiftUI conserva primero el
        // canvas contractual de 375 pt y reduce el overlay completo. Hacemos lo
        // mismo para que Compose no constriña cada sticker antes de escalarlo.
        val referenceWidth = 375.dp
        val referenceHeight = (375f / storyRatio).dp
        val referenceWidthPx = with(density) { referenceWidth.toPx() }
        val overlayScale = canvasWidthPx / referenceWidthPx.coerceAtLeast(1f)

        Box(
            Modifier
                .size(canvasWidth, canvasHeight)
                .align(Alignment.Center),
        ) {
            StoryStaticPreviewMedia(story, Modifier.fillMaxSize())
            StoryMediaOverlayRendererView(
                textOverlays = story.resolvedTextOverlays,
                stickers = story.stickers.orEmpty(),
                drawingData = null,
                storyId = story.id.orEmpty(),
                userId = story.authorId,
                reportsDeckInteractionExclusion = false,
                allowsStickerHitTesting = false,
                renderingMode = StoryOverlayRenderingMode.THUMBNAIL,
                modifier = Modifier
                    .requiredSize(referenceWidth, referenceHeight)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = overlayScale
                        scaleY = overlayScale
                    },
            )
            if (
                revealPolicy == StoryRevealThumbnailPolicy.CONCEALED &&
                story.stickers.orEmpty().any { it.type == "reveal" }
            ) {
                val reveal = story.stickers.orEmpty().first { it.type == "reveal" }
                RevealSurfaceView(
                    type = reveal.revealType,
                    pattern = reveal.revealPattern,
                    primaryColor = reveal.revealPrimaryColor,
                    secondaryColor = reveal.revealSecondaryColor,
                    effectColor = reveal.revealEffectColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StoryStaticPreviewMedia(story: Story, modifier: Modifier = Modifier) {
    val preview = remember(story) {
        if (story.mediaItem.type == MediaItem.MediaType.VIDEO) {
            listOf(
                story.mediaItem.thumbnailUrl,
                story.backgroundFrameURL,
                story.backgroundBlurredFrameURL,
            )
        } else {
            listOf(story.mediaItem.url, story.backgroundFrameURL, story.backgroundBlurredFrameURL)
        }.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (preview != null) {
            AsyncImage(
                model = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
