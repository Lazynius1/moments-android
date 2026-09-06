package com.moments.android.views.shared

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Port de `MomentsFillScrollTabRow.swift`.
 * Rellena el ancho si las etiquetas son cortas; hace scroll si no caben.
 */
@Composable
fun <T> MomentsFillScrollTabRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    itemContent: @Composable RowScope.(item: T, itemModifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val count = items.size.coerceAtLeast(1)
        val minTabWidth = (maxWidth - horizontalPadding * 2) / count
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { item ->
                itemContent(item, Modifier.widthIn(min = minTabWidth))
            }
        }
    }
}
