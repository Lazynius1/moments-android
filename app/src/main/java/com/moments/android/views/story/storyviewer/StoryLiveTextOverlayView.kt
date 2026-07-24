package com.moments.android.views.story.storyviewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.models.StoryTextOverlayMetadata
import kotlin.math.roundToInt

/** Port de `StoryLiveTextOverlayView.swift`. */
@Composable
fun StoryLiveTextOverlayView(
    metadata: StoryTextOverlayMetadata,
    replayToken: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val alpha = remember(metadata.id) { Animatable(0f) }
        val scale = remember(metadata.id) { Animatable(if (metadata.motionRaw == "pop") 0.75f else 1f) }
        LaunchedEffect(metadata.id, replayToken) {
            alpha.snapTo(0f)
            if (metadata.motionRaw == "pop") scale.snapTo(0.75f)
            alpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            if (metadata.motionRaw == "pop") scale.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
        val text = if (metadata.forcesAllCaps) metadata.text.uppercase() else metadata.text
        Text(
            text = text,
            color = parseOverlayColor(metadata.colorHex),
            fontSize = metadata.fontSize.coerceIn(12.0, 64.0).toFloat().sp,
            fontWeight = if (metadata.styleRaw.contains("bold", true)) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = when (metadata.alignmentRaw.lowercase()) { "leading", "left" -> TextAlign.Start; "trailing", "right" -> TextAlign.End; else -> TextAlign.Center },
            modifier = Modifier
                .offset {
                    IntOffset(
                        (metadata.normalizedPosition.x * constraints.maxWidth).roundToInt() - 80,
                        (metadata.normalizedPosition.y * constraints.maxHeight).roundToInt() - 24,
                    )
                }
                .alpha(alpha.value)
                .scale(scale.value)
                .background(if (metadata.backgroundFillRaw == "none") Color.Transparent else Color.Black.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun parseOverlayColor(raw: String): Color = runCatching {
    val clean = raw.removePrefix("#")
    Color((0xFF000000 or clean.toLong(16)).toULong())
}.getOrDefault(Color.White)
