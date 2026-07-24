package com.moments.android.views.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.utilities.legacyPoppinsSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Port de `MomentsVideoPlaybackTimeline.swift`. */
@Composable
fun MomentsVideoPlaybackTimeline(
    currentTime: Double,
    duration: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 26.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val effectiveProgress = if (isScrubbing) {
        scrubProgress
    } else if (duration > 0) {
        min(max((currentTime / duration).toFloat(), 0f), 1f)
    } else {
        0f
    }
    val displayedCurrentTime = if (duration > 0) effectiveProgress * duration else 0.0

    fun progressFor(locationX: Float): Float {
        val clamped = min(max(locationX, 0f), trackWidthPx)
        return clamped / max(trackWidthPx, 1f)
    }

    Column(
        modifier = modifier.padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .onSizeChanged { trackWidthPx = max(it.width.toFloat(), 1f) }
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0) {
                            scrubProgress = progressFor(offset.x)
                            onSeek(scrubProgress * duration)
                        }
                    }
                }
                .pointerInput(duration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (duration > 0) {
                                isScrubbing = true
                                scrubProgress = progressFor(offset.x)
                                onSeek(scrubProgress * duration)
                            }
                        },
                        onDragEnd = { isScrubbing = false },
                        onDragCancel = { isScrubbing = false },
                    ) { change, _ ->
                        if (duration > 0) {
                            scrubProgress = progressFor(change.position.x)
                            onSeek(scrubProgress * duration)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)),
            )

            Box(
                Modifier
                    .width(with(density) { (trackWidthPx * effectiveProgress).toDp() })
                    .height(4.dp)
                    .background(Color.White, RoundedCornerShape(50)),
            )

            val knobOffsetPx = effectiveProgress * max(trackWidthPx - with(density) { 12.dp.toPx() }, 0f)
            Box(
                Modifier
                    .offset(x = with(density) { knobOffsetPx.toDp() })
                    .size(12.dp)
                    .background(Color.White, CircleShape),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() }
            androidx.compose.material3.Text(
                text = formatPlaybackTime(displayedCurrentTime),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
            )
            androidx.compose.material3.Text(
                text = formatPlaybackTime(duration),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatPlaybackTime(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0:00"
    val totalSeconds = max(0, kotlin.math.floor(value).roundToInt())
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
