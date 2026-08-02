package com.moments.android.views.permission.shared

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection

/**
 * Mide el hijo a [width]×[height] fijos aunque el padre (pantalla del mock) sea más
 * estrecho/bajo — necesario para pans iOS (`width*2`, `height*1.8`, feed 2 sets).
 *
 * Reporta el tamaño del viewport al padre y coloca el placeable sobredimensionado
 * según [alignment] (puede quedar en coords negativas). El padre debe `clipToBounds`.
 */
fun Modifier.permissionMockOverflowSize(
    width: Dp,
    height: Dp,
    alignment: Alignment = Alignment.Center,
): Modifier = layout { measurable, constraints ->
    val w = width.roundToPx().coerceAtLeast(1)
    val h = height.roundToPx().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    val viewportW = constraints.maxWidth.let { if (it == Constraints.Infinity) w else it.coerceAtLeast(1) }
    val viewportH = constraints.maxHeight.let { if (it == Constraints.Infinity) h else it.coerceAtLeast(1) }
    layout(viewportW, viewportH) {
        val offset = alignment.align(
            size = androidx.compose.ui.unit.IntSize(placeable.width, placeable.height),
            space = androidx.compose.ui.unit.IntSize(viewportW, viewportH),
            layoutDirection = LayoutDirection.Ltr,
        )
        placeable.place(offset)
    }
}

/** Columna/fila más alta que el viewport; reporta viewport y coloca desde arriba. */
fun Modifier.permissionMockOverflowHeight(minHeight: Dp): Modifier = layout { measurable, constraints ->
    val minH = minHeight.roundToPx().coerceAtLeast(1)
    val childConstraints = constraints.copy(
        minHeight = 0,
        maxHeight = Constraints.Infinity,
        minWidth = constraints.minWidth,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity,
    )
    val placeable = measurable.measure(childConstraints)
    val viewportW = constraints.maxWidth.let { if (it == Constraints.Infinity) placeable.width else it.coerceAtLeast(1) }
    val viewportH = constraints.maxHeight.let { if (it == Constraints.Infinity) placeable.height else it.coerceAtLeast(1) }
    layout(viewportW, viewportH) {
        placeable.place(IntOffset.Zero)
    }
}
