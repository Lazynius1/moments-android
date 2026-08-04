package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.views.creator.components.StoryTextOverlayLabel
import com.moments.android.views.creator.components.StoryTextStyle
import com.moments.android.views.creator.components.displayPosition
import com.moments.android.views.creator.components.motion
import com.moments.android.views.creator.components.scaledRenderConfiguration
import kotlin.math.roundToInt

/**
 * Port de `StoryLiveTextOverlayView.swift`.
 *
 * - [containerSize] explícito ≡ iOS; si es null, se usa el tamaño del Box padre (fillMaxSize).
 * - Sin pointer handlers → hits pasan al visor (≡ `.allowsHitTesting(false)`).
 */
@Composable
fun StoryLiveTextOverlayView(
    metadata: StoryTextOverlayMetadata,
    replayToken: Int,
    modifier: Modifier = Modifier,
    containerSize: Size? = null,
) {
    BoxWithConstraints(modifier.zIndex(metadata.layerOrder.toFloat())) {
        val density = LocalDensity.current
        val container = containerSize ?: Size(
            constraints.maxWidth.toFloat().coerceAtLeast(1f),
            constraints.maxHeight.toFloat().coerceAtLeast(1f),
        )
        // ≡ if let config = metadata.scaledRenderConfiguration(...) — styleRaw inválido → nil
        val styleKnown = remember(metadata.styleRaw) {
            StoryTextStyle.entries.any { it.raw.equals(metadata.styleRaw, ignoreCase = true) }
        }
        if (!styleKnown) return@BoxWithConstraints

        // iOS usa points; Android container está en px → dp para el factor 375.
        val containerWidthDp = with(density) { container.width.toDp().value }
        val config = metadata.scaledRenderConfiguration(containerWidthDp)
        val anchor = metadata.displayPosition(container)
        val maxWidth = with(density) { (container.width - 48f).coerceAtLeast(120f).toDp() }
        var contentWidthPx by remember(metadata.id) { mutableFloatStateOf(0f) }
        var contentHeightPx by remember(metadata.id) { mutableFloatStateOf(0f) }

        // ≡ .position(x:y:) ancla el centro del label
        Box(
            Modifier.offset {
                IntOffset(
                    (anchor.x - contentWidthPx / 2f).roundToInt(),
                    (anchor.y - contentHeightPx / 2f).roundToInt(),
                )
            },
        ) {
            StoryTextOverlayLabel(
                configuration = config,
                maxWidth = maxWidth,
                motionRaw = metadata.motion.raw,
                replayToken = replayToken,
                modifier = Modifier.onSizeChanged {
                    contentWidthPx = it.width.toFloat()
                    contentHeightPx = it.height.toFloat()
                },
            )
        }
    }
}
