package com.moments.android.extensions

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * Same behavior as `Modifier.padding`, but allows negative values (used to let content
 * overflow its nominal layout bounds). Compose's own `padding()` throws for negative dp.
 */
fun Modifier.rawPadding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier = this.layout { measurable, constraints ->
    val startPx = start.roundToPx()
    val topPx = top.roundToPx()
    val endPx = end.roundToPx()
    val bottomPx = bottom.roundToPx()
    val horizontal = startPx + endPx
    val vertical = topPx + bottomPx
    val childConstraints = constraints.offset(-horizontal, -vertical)
    val placeable = measurable.measure(childConstraints)
    val width = (placeable.width + horizontal).coerceAtLeast(0)
    val height = (placeable.height + vertical).coerceAtLeast(0)
    layout(width, height) {
        placeable.place(startPx, topPx)
    }
}

fun Modifier.rawPadding(all: Dp): Modifier = rawPadding(all, all, all, all)

fun Modifier.rawPadding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): Modifier =
    rawPadding(horizontal, vertical, horizontal, vertical)
