package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    GridPreviewThumbnailFrame(width = size, height = size, settings = settings, content = content)
}

/**
 * Port de `GridPreviewThumbnailFrame`.
 * El offset vertical sigue el ancho, como los ajustes guardados del grid 1:1.
 */
@Composable
fun GridPreviewThumbnailFrame(
    width: Dp,
    height: Dp,
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

    Box(
        Modifier
            .size(width, height)
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
                    translationX = settings.offsetX.toFloat() * width.toPx()
                    translationY = settings.offsetY.toFloat() * width.toPx()
                },
            contentAlignment = Alignment.Center,
        ) {
            content(contentScale)
        }
    }
}

/**
 * ≡ iOS `GridPreviewModeChipIcon`: 2 esquinas en Rellenar, 4 en Ajustar.
 */
@Composable
fun GridPreviewModeChipIcon(
    fitMode: MomentGridPreviewFitMode,
    modifier: Modifier = Modifier.size(18.dp),
    tint: Color = Color.White,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val leg = 5.5.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.8.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        val corners = when (fitMode) {
            MomentGridPreviewFitMode.FILL -> listOf(
                Alignment.TopEnd to Offset(size.width, 0f),
                Alignment.BottomStart to Offset(0f, size.height),
            )
            MomentGridPreviewFitMode.FIT -> listOf(
                Alignment.TopStart to Offset(0f, 0f),
                Alignment.TopEnd to Offset(size.width, 0f),
                Alignment.BottomStart to Offset(0f, size.height),
                Alignment.BottomEnd to Offset(size.width, size.height),
            )
        }
        corners.forEach { (align, origin) ->
            val path = Path()
            when (align) {
                Alignment.TopStart -> {
                    path.moveTo(origin.x, origin.y + leg)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x + leg, origin.y)
                }
                Alignment.TopEnd -> {
                    path.moveTo(origin.x - leg, origin.y)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x, origin.y + leg)
                }
                Alignment.BottomStart -> {
                    path.moveTo(origin.x, origin.y - leg)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x + leg, origin.y)
                }
                else -> {
                    path.moveTo(origin.x - leg, origin.y)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x, origin.y - leg)
                }
            }
            drawPath(path, tint, style = stroke)
        }
    }
}
