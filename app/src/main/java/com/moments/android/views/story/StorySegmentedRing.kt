package com.moments.android.views.story

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.utilities.HapticManager
import kotlin.math.min

/**
 * Port 1:1 de `StorySegmentedRing` + `StorySegment`
 * (`Views/story/StorySegmentedRing.swift`).
 *
 * ≡ iOS `Color.blue` / `.purple` / `.pink` (system).
 */
private val StoryRingLitColors = listOf(
    Color(0xFF007AFF), // systemBlue
    Color(0xFFAF52DE), // systemPurple
    Color(0xFFFF2D55), // systemPink
)

/** ≡ `StorySegmentedRing.triggerHaptic()` (UIImpactFeedbackGenerator .medium). */
fun triggerStorySegmentedRingHaptic() {
    HapticManager.shared.mediumImpact()
}

@Composable
fun StorySegmentedRing(
    storyCount: Int,
    hasStory: Boolean,
    hasUnseenStory: Boolean,
    storyViewedStatus: List<Boolean>,
    storyAudiences: List<String?> = emptyList(),
    nestedStoryAudiences: List<List<String?>> = emptyList(),
    nestedStoryViewedStatus: List<List<Boolean>> = emptyList(),
    isOwnStory: Boolean,
    ringSize: Dp = 50.dp,
    lineWidth: Dp = 2.5.dp,
    /** Reservado como en iOS (el View no lo usa; el haptic es estático). */
    @Suppress("UNUSED_PARAMETER") hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // ≡ iOS `colorScheme` (Environment)
    val isDark = isSystemInDarkTheme()
    val gapAngle = 15.0
    // Swift: .padding(lineWidth / 2 + 1)
    val ringPadding = lineWidth / 2 + 1.dp
    val outerSize = ringSize + lineWidth + 2.dp

    val viewedGrayColors = if (isDark) {
        listOf(Color.Gray.copy(alpha = 0.58f), Color.Gray.copy(alpha = 0.82f))
    } else {
        listOf(Color.Gray.copy(alpha = 0.76f), Color.Gray.copy(alpha = 0.94f))
    }
    val bestFriendsColor = if (isDark) Color(0xFF3A9A72) else Color(0xFF185C45)
    val mutualsColor = if (isDark) Color(0xFF3D5F9A) else Color(0xFF1E3866)
    val bestFriendsColors = listOf(bestFriendsColor, bestFriendsColor)
    val mutualsColors = listOf(mutualsColor, mutualsColor)

    val litBrush = remember {
        Brush.linearGradient(
            colors = StoryRingLitColors,
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    }
    val viewedGrayBrush = remember(isDark) {
        Brush.linearGradient(
            colors = viewedGrayColors,
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    }
    val bestFriendsBrush = remember(isDark) { SolidColor(bestFriendsColor) }
    val mutualsBrush = remember(isDark) { SolidColor(mutualsColor) }

    fun normalizedAudience(raw: String?): String =
        raw?.trim()?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            .orEmpty()

    fun audienceStyleFor(raw: String?): AudienceStyle? =
        when (normalizedAudience(raw)) {
            "bestfriends", "bestfriend" -> AudienceStyle.BestFriends
            "mutuals", "mutual" -> AudienceStyle.Mutuals
            else -> null
        }

    fun audienceStyle(index: Int): AudienceStyle? {
        if (index !in storyAudiences.indices) return null
        return audienceStyleFor(storyAudiences[index])
    }

    fun audienceGradient(style: AudienceStyle): Brush = when (style) {
        AudienceStyle.BestFriends -> bestFriendsBrush
        AudienceStyle.Mutuals -> mutualsBrush
    }

    fun colorsForStop(stop: SliceStop): List<Color> = when (stop) {
        SliceStop.Viewed -> viewedGrayColors
        SliceStop.Everyone -> StoryRingLitColors
        SliceStop.BestFriends -> bestFriendsColors
        SliceStop.Mutuals -> mutualsColors
    }

    fun sliceStop(audience: String?, viewed: Boolean): SliceStop {
        if (!isOwnStory && viewed) return SliceStop.Viewed
        return when (audienceStyleFor(audience)) {
            AudienceStyle.BestFriends -> SliceStop.BestFriends
            AudienceStyle.Mutuals -> SliceStop.Mutuals
            null -> SliceStop.Everyone
        }
    }

    fun nestedSliceGradient(audiences: List<String?>, viewed: List<Boolean>): Brush {
        val stops = audiences.mapIndexed { index, audience ->
            sliceStop(audience, viewed.getOrElse(index) { false })
        }
        val first = stops.firstOrNull() ?: return litBrush
        val colors = if (stops.all { it == first }) {
            colorsForStop(first)
        } else {
            stops.flatMap { colorsForStop(it) }
        }
        return Brush.linearGradient(
            colors = colors,
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    }

    // ≡ segmentGradient(for:)
    fun segmentGradient(index: Int): Brush {
        val nested = nestedStoryAudiences.getOrNull(index)
        if (!nested.isNullOrEmpty()) {
            return nestedSliceGradient(
                audiences = nested,
                viewed = nestedStoryViewedStatus.getOrElse(index) { emptyList() },
            )
        }
        val wasViewed = if (index < storyViewedStatus.size) storyViewedStatus[index] else false
        // Externos: vista → gris siempre (incluye bestfriends/mutuals)
        if (!isOwnStory && wasViewed) return viewedGrayBrush
        // PRIORIDAD: audiencia cuando NO vista
        audienceStyle(index)?.let { return audienceGradient(it) }
        return if (isOwnStory) {
            litBrush
        } else if (wasViewed) {
            viewedGrayBrush
        } else {
            litBrush
        }
    }

    // ≡ storyRingGradient (1 corte)
    fun storyRingGradient(): Brush {
        val nested = nestedStoryAudiences.getOrNull(0)
        if (!nested.isNullOrEmpty()) {
            return nestedSliceGradient(
                audiences = nested,
                viewed = nestedStoryViewedStatus.getOrElse(0) { emptyList() },
            )
        }
        val wasViewed = !hasUnseenStory
        if (!isOwnStory && wasViewed) return viewedGrayBrush
        audienceStyle(0)?.let { return audienceGradient(it) }
        return when {
            isOwnStory -> litBrush
            hasUnseenStory -> litBrush
            hasStory -> viewedGrayBrush
            else -> Brush.linearGradient(listOf(Color.Transparent))
        }
    }

    Canvas(
        // requiredSize: permitir overflow del stroke (≡ iOS overlay fuera del frame 96 del avatar)
        modifier
            .requiredSize(outerSize)
            .padding(ringPadding),
    ) {
        val stroke = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round)
        val diameter = size.minDimension
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        if (hasStory && storyCount > 0) {
            if (storyCount == 1) {
                // Círculo completo sin gaps; −90º ≡ rotationEffect del ZStack
                drawStorySegment(
                    brush = storyRingGradient(),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    topLeft = topLeft,
                    arcSize = arcSize,
                    stroke = stroke,
                )
            } else {
                val segmentAngleTotal = 360.0 / storyCount.toDouble()
                val segmentAngleUseful = segmentAngleTotal - gapAngle
                val segmentFraction = segmentAngleUseful / 360.0

                repeat(storyCount) { index ->
                    val startFraction = index * segmentAngleTotal / 360.0
                    val endFraction = min(startFraction + segmentFraction, 1.0)
                    val sweepAngle = ((endFraction - startFraction) * 360.0).toFloat()
                    if (sweepAngle <= 0f) return@repeat

                    drawStorySegment(
                        brush = segmentGradient(index),
                        // Swift: trim + rotationEffect(-90)
                        startAngle = (startFraction * 360.0 - 90.0).toFloat(),
                        sweepAngle = sweepAngle,
                        topLeft = topLeft,
                        arcSize = arcSize,
                        stroke = stroke,
                    )
                }
            }
        } else {
            // SIN HISTORIAS: anillo transparente (mantiene layout)
            drawStorySegment(
                brush = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                startAngle = -90f,
                sweepAngle = 360f,
                topLeft = topLeft,
                arcSize = arcSize,
                stroke = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

private enum class AudienceStyle {
    BestFriends,
    Mutuals,
}

private enum class SliceStop {
    Viewed,
    Everyone,
    BestFriends,
    Mutuals,
}

/** ≡ `StorySegment` (trim + stroke). */
private fun DrawScope.drawStorySegment(
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
