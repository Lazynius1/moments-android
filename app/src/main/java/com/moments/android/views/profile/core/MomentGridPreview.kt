package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.moments.android.models.Moment
import com.moments.android.models.MomentGridPreviewSettings

/** Alias 1:1 de `MomentGridPreviewFitMode` (iOS). */
typealias MomentGridPreviewFitMode = MomentGridPreviewSettings.FitMode

/** Alias 1:1 de `MomentGridPreviewBackground` (iOS). */
typealias MomentGridPreviewBackground = MomentGridPreviewSettings.Background

/**
 * Port de `MomentGridPreview.swift`:
 * settings + `Moment.gridPreviewSettings` / `canAdjustGridPreview` + `GridPreviewThumbnailFrame`.
 */
val Moment.gridPreviewSettings: MomentGridPreviewSettings
    get() = MomentGridPreviewSettings(
        scale = gridPreviewScale ?: 1.0,
        offsetX = gridPreviewOffsetX ?: 0.0,
        offsetY = gridPreviewOffsetY ?: 0.0,
        fitMode = MomentGridPreviewFitMode.entries.firstOrNull {
            it.raw == (gridPreviewFitMode ?: "fill")
        } ?: MomentGridPreviewFitMode.FILL,
        background = MomentGridPreviewBackground.entries.firstOrNull {
            it.raw == (gridPreviewBackground ?: "black")
        } ?: MomentGridPreviewBackground.BLACK,
    )

val Moment.canAdjustGridPreview: Boolean
    get() = previewImageURLString != null

/**
 * Port de `GridPreviewThumbnailFrame`.
 * [content] recibe el [ContentScale] equivalente a `.aspectRatio(contentMode:)`.
 */
@Composable
fun GridPreviewThumbnailFrame(
    size: Dp,
    settings: MomentGridPreviewSettings,
    content: @Composable (ContentScale) -> Unit,
) {
    val contentScale = if (settings.fitMode == MomentGridPreviewFitMode.FIT) {
        ContentScale.Fit
    } else {
        ContentScale.Crop
    }
    val background = if (settings.background == MomentGridPreviewBackground.BLACK) {
        Color.Black
    } else {
        Color.White
    }

    // iOS ZStack centra por defecto — crítico en fit (letterbox).
    Box(
        Modifier
            .size(size)
            .clipToBounds()
            .then(
                if (settings.fitMode == MomentGridPreviewFitMode.FIT) {
                    Modifier.background(background)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = settings.scale.toFloat()
                    scaleY = settings.scale.toFloat()
                    translationX = settings.offsetX.toFloat() * size.toPx()
                    translationY = settings.offsetY.toFloat() * size.toPx()
                },
            contentAlignment = Alignment.Center,
        ) {
            content(contentScale)
        }
    }
}
