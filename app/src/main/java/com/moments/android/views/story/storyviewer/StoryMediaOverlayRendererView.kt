package com.moments.android.views.story.storyviewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.views.story.StoryDeckGestureGate
import com.moments.android.views.story.StoryInteractiveStickerLayer
import com.moments.android.views.story.storystickers.StoryStickerRendererLayer

/** Port de `StoryMediaOverlayRendererView.swift`. Reveal se mantiene separado: requiere scratch. */
@Composable
fun StoryMediaOverlayRendererView(
    textOverlays: List<StoryTextOverlayMetadata>,
    stickers: List<StickerData>,
    drawingData: ByteArray?,
    storyId: String,
    userId: String,
    replayToken: Int = 0,
    gestureGate: StoryDeckGestureGate? = null,
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drawing = remember(drawingData) { drawingData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    Box(modifier) {
        drawing?.let { bitmap ->
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        StoryInteractiveStickerLayer(
            storyId = storyId,
            stickers = stickers,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            modifier = Modifier.fillMaxSize(),
        )
        StoryStickerRendererLayer(
            storyId = storyId,
            userId = userId,
            stickers = stickers,
            gestureGate = gestureGate,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            modifier = Modifier.fillMaxSize(),
        )
        textOverlays.sortedBy { it.layerOrder }.forEach { overlay ->
            StoryLiveTextOverlayView(overlay, replayToken, Modifier.fillMaxSize())
        }
    }
}
