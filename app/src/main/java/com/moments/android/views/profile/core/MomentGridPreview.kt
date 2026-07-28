package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.moments.android.models.Moment
import com.moments.android.models.MomentGridPreviewSettings

/** Port de `MomentGridPreview.swift`. */
val Moment.gridPreviewSettings: MomentGridPreviewSettings
    get() = MomentGridPreviewSettings(
        scale = gridPreviewScale ?: 1.0,
        offsetX = gridPreviewOffsetX ?: 0.0,
        offsetY = gridPreviewOffsetY ?: 0.0,
        fitMode = MomentGridPreviewSettings.FitMode.entries.firstOrNull { it.raw == gridPreviewFitMode } ?: MomentGridPreviewSettings.FitMode.FILL,
        background = MomentGridPreviewSettings.Background.entries.firstOrNull { it.raw == gridPreviewBackground } ?: MomentGridPreviewSettings.Background.BLACK,
    )

val Moment.canAdjustGridPreview: Boolean get() = previewImageURLString != null

@Composable
fun GridPreviewThumbnailFrame(
    size: Dp,
    settings: MomentGridPreviewSettings,
    content: @Composable (ContentScale) -> Unit,
) {
    val contentScale = if (settings.fitMode == MomentGridPreviewSettings.FitMode.FIT) {
        ContentScale.Fit
    } else {
        ContentScale.Crop
    }
    val background = if (settings.background == MomentGridPreviewSettings.Background.BLACK) Color.Black else Color.White

    Box(
        Modifier
            .size(size)
            .clipToBounds()
            .then(if (settings.fitMode == MomentGridPreviewSettings.FitMode.FIT) Modifier.background(background) else Modifier),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = settings.scale.toFloat()
                    scaleY = settings.scale.toFloat()
                    translationX = settings.offsetX.toFloat() * size.toPx()
                    translationY = settings.offsetY.toFloat() * size.toPx()
                },
        ) {
            content(contentScale)
        }
    }
}
