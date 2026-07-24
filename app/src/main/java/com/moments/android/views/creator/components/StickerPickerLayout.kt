package com.moments.android.views.creator.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sin

/** Port de `StickerPillFlowLayout` de `StickerPickerLayout.swift`. */
@Composable
fun StickerPillFlowLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    rowSpacing: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val rowSpacingPx = rowSpacing.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 320.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        placeables.forEach { item ->
            val current = rows.lastOrNull()
            val currentWidth = rowWidths.lastOrNull() ?: 0
            val proposedWidth = if (current.isNullOrEmpty()) item.width else currentWidth + spacingPx + item.width
            if (current != null && current.isNotEmpty() && proposedWidth > maxWidth) {
                rows += mutableListOf(item)
                rowWidths += item.width
                rowHeights += item.height
            } else if (current == null) {
                rows += mutableListOf(item)
                rowWidths += item.width
                rowHeights += item.height
            } else {
                current += item
                rowWidths[rows.lastIndex] = proposedWidth
                rowHeights[rows.lastIndex] = max(rowHeights.last(), item.height)
            }
        }

        val contentHeight = rowHeights.sum() + max(0, rows.size - 1) * rowSpacingPx
        val width = maxWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var currentY = 0
            rows.forEachIndexed { index, row ->
                val rowWidth = rowWidths[index]
                val rowShift = when (index % 4) {
                    1 -> 8.dp.roundToPx()
                    2 -> -6.dp.roundToPx()
                    3 -> 4.dp.roundToPx()
                    else -> 0
                }
                val centeredX = (width - rowWidth) / 2
                var currentX = (centeredX + rowShift)
                    .coerceIn(0, (width - rowWidth).coerceAtLeast(0))
                val rowY = currentY + if (index % 2 == 0) 0 else 2.dp.roundToPx()
                row.forEach { placeable ->
                    placeable.placeRelative(
                        x = currentX,
                        y = rowY + (rowHeights[index] - placeable.height) / 2,
                    )
                    currentX += placeable.width + spacingPx
                }
                currentY += rowHeights[index] + rowSpacingPx
            }
        }
    }
}

/** Port animado de `StickerEmojiSliderPillGlyph` de SwiftUI. */
@Composable
fun StickerEmojiSliderPillGlyph(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "emojiSliderPill")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3_490, easing = LinearEasing)),
        label = "emojiSliderPillPhase",
    )
    val progress = 0.10f + (((sin(phase.toDouble() * 1.8).toFloat() + 1f) / 2f) * 0.80f)

    BoxWithConstraints(modifier) {
        val trackHeight = 5.dp
        val emojiSize = 19.dp + 8.dp * progress
        val horizontalInset = 3.dp
        val trackWidth = (maxWidth - emojiSize - horizontalInset * 2).coerceAtLeast(12.dp)
        val trackX = horizontalInset + emojiSize / 2
        val trackY = (maxHeight - trackHeight) / 2
        val emojiX = trackX + trackWidth * progress - emojiSize / 2
        val emojiY = (maxHeight - emojiSize) / 2

        Canvas(Modifier.fillMaxSize()) {
            val trackTop = trackY.toPx()
            val trackLeft = trackX.toPx()
            val height = trackHeight.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.10f),
                topLeft = androidx.compose.ui.geometry.Offset(trackLeft, trackTop),
                size = androidx.compose.ui.geometry.Size(trackWidth.toPx(), height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2),
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFFFA62B), Color(0xFFFF5C93), Color(0xFF8D62FF)),
                ),
                topLeft = androidx.compose.ui.geometry.Offset(trackLeft, trackTop),
                size = androidx.compose.ui.geometry.Size(
                    (trackWidth.toPx() * progress).coerceAtLeast(height),
                    height,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2),
            )
        }
        Text(
            text = "😍",
            fontSize = (15f + progress * 5f).sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.TopStart)
                .requiredSize(emojiSize)
                .shadow(3.dp, spotColor = Color.Black.copy(alpha = 0.14f))
                .offset(x = emojiX, y = emojiY),
        )
    }
}
