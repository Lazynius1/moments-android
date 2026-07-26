package com.moments.android.views.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R

/** Medidas ópticas — port 1:1 de `EchoesIconMetrics`. */
object EchoesIconMetrics {
    /** Fila de categoría en actividad (slot 36pt; alineado con reacciones ~22pt). */
    val categoryRow = 28.dp
    /** Barra superior del feed (heart/paperplane ~22pt; icono custom un poco más grande). */
    val feedToolbar = 32.dp
    /** Empty state de Echoes en actividad (sin círculo). */
    val emptyState = 96.dp
    /** Miniatura en fila de echo (slot 56pt). */
    val rowThumbnail = 36.dp
    /** Avatar fallback en filas de actividad. */
    val rowAvatar = 18.dp
    /** Empty state historial de echoes (sheet desde el feed). */
    val historyEmpty = 92.dp
    /** Celda en historial. */
    val historyRow = 32.dp
    /** Cabecera del sheet de invitación. */
    val invitation = 40.dp
    /** Espera en visor de echo (sin círculo). */
    val viewerLoading = 56.dp
}

/**
 * Gradientes de marca — port de `EchoesIconView.echoesBrandGradient*`.
 * Colores = system orange / purple de iOS (`#FF9500`, `#AF52DE`).
 */
object EchoesIconGradients {
    /** iOS: `.leading` → `.trailing` */
    val brandHorizontal: Brush
        get() = Brush.horizontalGradient(listOf(Color(0xFFFF9500), Color(0xFFAF52DE)))

    /** iOS: `.topLeading` → `.bottomTrailing` */
    val brandDiagonal: Brush
        get() = Brush.linearGradient(listOf(Color(0xFFFF9500), Color(0xFFAF52DE)))
}

/**
 * Port de `EchoesIconView.swift`.
 * Asset template `EchoesIcon` → `R.drawable.echoes_icon`.
 * Tint sólido (`foregroundStyle(tint)`) o gradiente (`foregroundStyle(gradient)` vía SrcIn).
 */
@Composable
fun EchoesIconView(
    size: Dp,
    modifier: Modifier = Modifier,
    tintColor: Color = LocalContentColor.current, // iOS: .primary por defecto
    gradient: Brush? = null,
) {
    val painter = painterResource(R.drawable.echoes_icon)
    val base = modifier
        .size(size)
        .clearAndSetSemantics { } // iOS: accessibilityHidden(true)

    if (gradient != null) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = base
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = gradient, blendMode = BlendMode.SrcIn)
                    }
                },
        )
    } else {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tintColor),
            modifier = base,
        )
    }
}

/** Convenience: toolbar feed — `EchoesIconView(size:, gradient: echoesBrandGradientHorizontal)`. */
@Composable
fun EchoesIconViewBrandHorizontal(
    size: Dp = EchoesIconMetrics.feedToolbar,
    modifier: Modifier = Modifier,
) {
    EchoesIconView(size = size, modifier = modifier, gradient = EchoesIconGradients.brandHorizontal)
}
