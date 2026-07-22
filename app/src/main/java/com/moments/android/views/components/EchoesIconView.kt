package com.moments.android.views.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R

/** Port de `EchoesIconMetrics` (EchoesIconView.swift). */
object EchoesIconMetrics {
    val categoryRow = 28.dp
    val feedToolbar = 32.dp
    val emptyState = 96.dp
    val rowThumbnail = 36.dp
    val rowAvatar = 18.dp
    val historyEmpty = 92.dp
    val historyRow = 32.dp
    val invitation = 40.dp
    val viewerLoading = 56.dp
}

object EchoesIconGradients {
    // iOS Color.orange / Color.purple
    val brandHorizontal: Brush
        get() = Brush.horizontalGradient(listOf(Color(0xFFFF9500), Color(0xFFAF52DE)))
    val brandDiagonal: Brush
        get() = Brush.linearGradient(listOf(Color(0xFFFF9500), Color(0xFFAF52DE)))
}

/**
 * Port de `EchoesIconView.swift` — asset template + tint o gradiente (SrcIn).
 */
@Composable
fun EchoesIconView(
    size: Dp,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Unspecified,
    gradient: Brush? = null,
) {
    val painter = painterResource(R.drawable.echoes_icon)
    val brush = gradient
        ?: if (tintColor != Color.Unspecified) {
            Brush.linearGradient(listOf(tintColor, tintColor))
        } else {
            EchoesIconGradients.brandHorizontal
        }

    Box(
        modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .paint(painter, contentScale = ContentScale.Fit)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                }
            },
    )
}

@Composable
fun EchoesIconViewBrandHorizontal(
    size: Dp = EchoesIconMetrics.feedToolbar,
    modifier: Modifier = Modifier,
) {
    EchoesIconView(size = size, modifier = modifier, gradient = EchoesIconGradients.brandHorizontal)
}
