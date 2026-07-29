package com.moments.android.views.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.models.PhotoTag
import kotlin.math.roundToInt

/** Port de `PhotoTagOverlayView.swift`. */
@Composable
fun PhotoTagOverlayView(
    tags: List<PhotoTag>,
    isVisible: Boolean,
    onTagTap: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val upwardOffsetPx = with(density) { 30.dp.toPx() }

        tags.forEach { tag ->
            val centerX = (tag.x * wPx).toFloat()
            val centerY = (tag.y * hPx).toFloat() - upwardOffsetPx
            var bubbleW by remember(tag.userId, tag.x, tag.y) { mutableIntStateOf(0) }
            var bubbleH by remember(tag.userId, tag.x, tag.y) { mutableIntStateOf(0) }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                ) + scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                ),
                exit = fadeOut(),
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .offset {
                        IntOffset(
                            (centerX - bubbleW / 2f).roundToInt(),
                            (centerY - bubbleH / 2f).roundToInt(),
                        )
                    }
                    .onSizeChanged {
                        bubbleW = it.width
                        bubbleH = it.height
                    },
            ) {
                PhotoTagBubble(
                    username = tag.username,
                    onClick = { onTagTap?.invoke(tag.userId) },
                )
            }
        }
    }
}

@Composable
private fun PhotoTagBubble(
    username: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .shadow(4.dp, RoundedCornerShape(percent = 50), ambientColor = Color.Black.copy(0.2f), spotColor = Color.Black.copy(0.2f))
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(percent = 50))
                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(username, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(10.dp),
            )
        }
        Canvas(Modifier.size(8.dp, 6.dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(path, Color.Black.copy(alpha = 0.75f))
        }
    }
}
