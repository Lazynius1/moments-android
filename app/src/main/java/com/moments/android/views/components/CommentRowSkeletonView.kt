package com.moments.android.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Port de `CommentRowSkeletonView.swift`.
 * Imita `InlineCommentRow`/`EnhancedModernCommentRow`: avatar + línea de usuario + 1-2 líneas.
 */
@Composable
fun CommentRowSkeletonView(
    textLineCount: Int = 2,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = rememberMomentsSkeletonColor()
    Row(
        modifier = modifier
            .shimmer(isAnimating = true)
            .clearAndSetSemantics { }, // iOS: accessibilityHidden(true)
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).background(surfaceColor, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .width(84.dp)
                    .height(11.dp)
                    .background(surfaceColor, RoundedCornerShape(4.dp)),
            )
            repeat(textLineCount.coerceAtLeast(0)) { index ->
                val lineMod = Modifier
                    .height(12.dp)
                    .background(surfaceColor, RoundedCornerShape(4.dp))
                Box(
                    if (index == textLineCount - 1) {
                        lineMod.width(140.dp)
                    } else {
                        lineMod.fillMaxWidth()
                    },
                )
            }
        }
    }
}

/**
 * Lista vertical de N filas de comentario en estado de carga.
 * Port de `CommentRowSkeletonList` — sin padding propio (iOS lo aplica en el call site).
 */
@Composable
fun CommentRowSkeletonList(
    rows: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        repeat(rows.coerceAtLeast(0)) {
            CommentRowSkeletonView()
        }
    }
}
