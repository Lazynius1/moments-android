package com.moments.android.views.messaging.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Android equivalent of `ChatBuzzShakeEffect`'s GeometryEffect transform. */
fun Modifier.chatBuzzShakeEffect(progress: Float, amplitude: Dp): Modifier = graphicsLayer {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val remaining = max(0f, 1f - clampedProgress)
    val amplitudePx = amplitude.toPx()
    translationX = sin(clampedProgress * Math.PI.toFloat() * 20f) * amplitudePx * remaining
    translationY = cos(clampedProgress * Math.PI.toFloat() * 14f) * amplitudePx * .38f * remaining
    rotationZ = (sin(clampedProgress * Math.PI.toFloat() * 16f) * .022f * remaining * 180f / Math.PI.toFloat())
}

@Composable
fun ChatBuzzToast(
    text: String,
    modifier: Modifier = Modifier,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (isDark) .22f else .12f),
                spotColor = Color.Black.copy(alpha = if (isDark) .22f else .12f),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttachmentIconView(
            icon = AttachmentIcon.BUZZ,
            size = AttachmentIconMetrics.buzzToast,
            tintColor = contentColor,
        )
        androidx.compose.material3.Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}

@Composable
fun ChatBuzzTimelineEventRow(
    text: String,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(50)
    val strokeColors = listOf(
            Color.Red.copy(alpha = if (isDark) .4f else .25f),
            Color(0xFFFF9500).copy(alpha = if (isDark) .2f else .08f),
            Color.Transparent,
        ).let { if (isOutgoing) it else it.reversed() }
    val stroke = Brush.horizontalGradient(strokeColors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .border(1.dp, stroke, shape)
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = Color.Red.copy(alpha = if (isDark) .16f else .05f),
                    spotColor = Color.Red.copy(alpha = if (isDark) .16f else .05f),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentIconView(
                icon = AttachmentIcon.BUZZ,
                size = AttachmentIconMetrics.buzzTimelineEvent,
                tintColor = Color.Red,
            )
            androidx.compose.material3.Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Private Compose shape matching `ChatBuzzWaveShape`'s five Bézier segments. */
private object ChatBuzzWaveShape : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val middleY = size.height / 2f
        val width = size.width
        return Outline.Generic(
            Path().apply {
                moveTo(0f, middleY + 5f)
                cubicTo(width * .12f, middleY - 8f, width * .22f, middleY - 1f, width * .34f, middleY - 2f)
                lineTo(width * .44f, middleY - 2f)
                cubicTo(width * .47f, middleY + 12f, width * .49f, middleY - 10f, width * .52f, middleY + 4f)
                cubicTo(width * .56f, middleY + 16f, width * .56f, middleY - 15f, width * .62f, middleY + 1f)
                cubicTo(width * .74f, middleY + 11f, width * .86f, middleY + 7f, width, middleY + 3f)
            },
        )
    }
}
