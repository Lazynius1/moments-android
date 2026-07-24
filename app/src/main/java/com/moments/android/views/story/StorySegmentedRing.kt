package com.moments.android.views.story

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.StoryRingColors
import com.moments.android.views.feed.StoryRingViewed
import kotlin.math.min

/**
 * Port de `StorySegmentedRing` (Views/story/StorySegmentedRing.swift).
 *
 * Mantiene un segmento por historia, dejando 15º entre segmentos. Las historias
 * vistas de otros usuarios se muestran en gris; best friends y mutuals conservan
 * su color propio mientras no estén vistas.
 */
@Composable
fun StorySegmentedRing(
    storyCount: Int,
    hasStory: Boolean,
    hasUnseenStory: Boolean,
    storyViewedStatus: List<Boolean>,
    storyAudiences: List<String?> = emptyList(),
    isOwnStory: Boolean,
    ringSize: Dp = 50.dp,
    lineWidth: Dp = 2.5.dp,
    @Suppress("UNUSED_PARAMETER") hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val gapAngle = 15.0
    // Swift: .padding(lineWidth / 2 + 1)
    val ringPadding = lineWidth / 2 + 1.dp
    val outerSize = ringSize + lineWidth + 2.dp

    val unseenBrush = remember { Brush.linearGradient(StoryRingColors) }
    val viewedBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(
                listOf(Color.Gray.copy(alpha = 0.58f), Color.Gray.copy(alpha = 0.82f)),
            )
        } else {
            Brush.linearGradient(
                listOf(Color.Gray.copy(alpha = 0.76f), Color.Gray.copy(alpha = 0.94f)),
            )
        }
    }
    val bestFriendsBrush = remember {
        Brush.linearGradient(listOf(Color(0xFF24C26A), Color(0xFF5BE584)))
    }
    val mutualsBrush = remember {
        Brush.linearGradient(listOf(Color(0xFF00B4D8), Color(0xFF4CC9F0)))
    }

    fun normalizedAudience(raw: String?): String =
        raw?.trim()?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            .orEmpty()

    fun audienceBrush(index: Int): Brush? {
        if (index !in storyAudiences.indices) return null
        return when (normalizedAudience(storyAudiences[index])) {
            "bestfriends", "bestfriend" -> bestFriendsBrush
            "mutuals", "mutual" -> mutualsBrush
            else -> null
        }
    }

    fun segmentBrush(index: Int): Brush {
        val wasViewed = storyViewedStatus.getOrElse(index) { false }
        if (!isOwnStory && wasViewed) return viewedBrush
        audienceBrush(index)?.let { return it }
        return if (isOwnStory || !wasViewed) unseenBrush else viewedBrush
    }

    fun singleBrush(): Brush {
        val wasViewed = !hasUnseenStory
        if (!isOwnStory && wasViewed) return viewedBrush
        audienceBrush(0)?.let { return it }
        return when {
            isOwnStory || hasUnseenStory -> unseenBrush
            hasStory -> viewedBrush
            else -> Brush.linearGradient(StoryRingViewed)
        }
    }

    Canvas(
        modifier
            .size(outerSize)
            .padding(ringPadding),
    ) {
        if (!hasStory || storyCount <= 0) return@Canvas

        val stroke = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round)
        val diameter = size.minDimension
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        if (storyCount == 1) {
            StorySegment(
                brush = singleBrush(),
                startAngle = -90f,
                sweepAngle = 360f,
                topLeft = topLeft,
                arcSize = arcSize,
                stroke = stroke,
            )
        } else {
            val segmentAngleTotal = 360.0 / storyCount
            val segmentAngleUseful = segmentAngleTotal - gapAngle
            val segmentFraction = segmentAngleUseful / 360.0

            repeat(storyCount) { index ->
                val startFraction = index * segmentAngleTotal / 360.0
                val endFraction = min(startFraction + segmentFraction, 1.0)
                val sweepAngle = ((endFraction - startFraction) * 360.0).toFloat()
                if (sweepAngle <= 0f) return@repeat

                StorySegment(
                    brush = segmentBrush(index),
                    // Swift rotates the ZStack -90º after trimming its circles.
                    startAngle = (startFraction * 360.0 - 90.0).toFloat(),
                    sweepAngle = sweepAngle,
                    topLeft = topLeft,
                    arcSize = arcSize,
                    stroke = stroke,
                )
            }
        }
    }
}

/** Equivalente del `StorySegment` interno de Swift. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.StorySegment(
    brush: Brush,
    startAngle: Float,
    sweepAngle: Float,
    topLeft: Offset,
    arcSize: Size,
    stroke: Stroke,
) {
    drawArc(
        brush = brush,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )
}
