package com.moments.android.views.messaging.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R

/** Port de `Views/Messaging/Components/MediaProgressRing.swift`. */
@Composable
fun MediaProgressRing(
    progress: Double,
    size: Dp = 40.dp,
    lineWidth: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val normalizedProgress = progress.toFloat().coerceIn(0f, 1f)
    val track = if (isDark) Color.White.copy(alpha = .14f) else Color.Black.copy(alpha = .08f)
    val label = if (isDark) Color.White else Color(0xFF0B1215)
    val shadow = if (isDark) Color(0xFF3F6F8F).copy(alpha = .22f) else Color.Black.copy(alpha = .1f)
    val gradient = if (isDark) listOf(Color(0xFF8EB6CE), Color(0xFF3F6F8F)) else listOf(Color(0xFF5C8DA8), Color(0xFF3F6F8F))
    Box(
        modifier = modifier.size(size).shadow(4.dp, CircleShape, ambientColor = shadow, spotColor = shadow).background(Color.White.copy(alpha = if (isDark) .08f else .6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size - lineWidth)) {
            val width = lineWidth.toPx()
            drawCircle(track, style = Stroke(width))
            drawArc(
                brush = Brush.linearGradient(gradient, Offset.Zero, Offset(this.size.width, this.size.height)),
                startAngle = -90f,
                sweepAngle = normalizedProgress * 360f,
                useCenter = false,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        if (size > 50.dp) {
            Text(stringResource(R.string.chat_media_progress_percent, (normalizedProgress * 100).toInt()), color = label, fontSize = 10.sp)
        }
    }
}
