package com.moments.android.extensions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

/**
 * Aproximación ligera al `scrollEdgeEffectStyle(.soft)` de iOS.
 *
 * Debe aplicarse al viewport del scroll, debajo de su chrome fijo. Observa el
 * nested scroll sin consumirlo y solo cambia estado al abandonar/regresar al
 * borde superior. El degradado queda cacheado: no usa blur, RenderEffect ni
 * capas offscreen.
 */
fun Modifier.momentsScrollEdgeChrome(hardBottomEdge: Boolean = false): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val surface = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    var topEdgeTarget by remember { mutableFloatStateOf(0f) }
    val topEdgeAlpha = animateFloatAsState(
        targetValue = topEdgeTarget,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "momentsSoftScrollEdge",
    )
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y < -0.5f && topEdgeTarget == 0f) {
                    topEdgeTarget = 1f
                }
                if (available.y > 0.5f && topEdgeTarget != 0f) {
                    topEdgeTarget = 0f
                }
                return Offset.Zero
            }
        }
    }

    nestedScroll(connection)
        .drawWithCache {
            val topHeight = 48.dp.toPx()
            val topBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to surface.copy(alpha = 0.98f),
                    0.32f to surface.copy(alpha = 0.76f),
                    0.7f to surface.copy(alpha = 0.24f),
                    1f to Color.Transparent,
                ),
                startY = 0f,
                endY = topHeight,
            )
            val bottomHeight = 24.dp.toPx()
            val bottomBrush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, surface),
                startY = size.height - bottomHeight,
                endY = size.height,
            )
            onDrawWithContent {
                drawContent()
                val currentTopAlpha = topEdgeAlpha.value
                if (currentTopAlpha > 0.001f) {
                    drawRect(
                        brush = topBrush,
                        size = Size(size.width, topHeight),
                        alpha = currentTopAlpha,
                    )
                }
                if (hardBottomEdge) {
                    drawRect(
                        brush = bottomBrush,
                        topLeft = Offset(0f, size.height - bottomHeight),
                        size = Size(size.width, bottomHeight),
                    )
                }
            }
        }
}
