package com.moments.android.views.shared

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Port de `MomentsFillScrollTabRow.swift`.
 * Abraza el contenido y hace scroll si no cabe.
 */
@Composable
fun <T> MomentsFillScrollTabRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    spacing: Dp = 8.dp,
    itemContent: @Composable RowScope.(item: T, itemModifier: Modifier) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            itemContent(item, Modifier)
        }
    }
}
