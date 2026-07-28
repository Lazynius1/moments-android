package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.views.story.storystickers.FloatingHeart
import com.moments.android.views.story.storystickers.FloatingHeartsView
import kotlin.random.Random

/** Port de `StorySegmentProgressChrome`. */
@Composable
fun StorySegmentProgressChrome(
    storyCount: Int,
    storyIndex: Int,
    progressForSegment: (Int) -> Float,
    audienceForSegment: (Int) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(storyCount) { index ->
            GlassmorphicProgressBar(
                progress = progressForSegment(index).coerceIn(0f, 1f),
                isActive = index == storyIndex,
                audience = audienceForSegment(index),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Port de `StoryFloatingReactionLayer`. */
@Composable
fun StoryFloatingReactionLayer(
    hearts: List<FloatingHeart>,
    containerSize: DpSize,
    onHeartExpired: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    FloatingHeartsView(
        hearts = hearts,
        containerSize = containerSize,
        onHeartExpired = onHeartExpired,
        modifier = modifier,
    )
}

/**
 * Port de `StoryReactionBurst`.
 * `width`/`height`/`sourceX`/`sourceY` en dp (≡ puntos iOS).
 * Expire: `FloatingHeartsView` (delay+duration+0.2s).
 */
object StoryReactionBurst {
    const val MAX_CONCURRENT = 48
    private const val REDUCED_MOTION_COUNT = 3
    private val NORMAL_COUNT_RANGE = 5..8

    fun emit(
        existing: List<FloatingHeart>,
        emoji: String,
        width: Float,
        height: Float,
        sourceX: Float = width * 0.82f,
        sourceY: Float = height * 0.92f,
    ): List<FloatingHeart> {
        if (width <= 0f || height <= 0f) return existing
        val count = if (MotionPolicy.reduceMotion) {
            REDUCED_MOTION_COUNT
        } else {
            Random.nextInt(NORMAL_COUNT_RANGE.first, NORMAL_COUNT_RANGE.last + 1)
        }
        val particles = List(count) { index ->
            val isMainPop = index == 0 && !MotionPolicy.reduceMotion
            FloatingHeart(
                emoji = emoji,
                startX = sourceX + Random.nextFloat() * 40f - 20f,
                startY = sourceY + Random.nextFloat() * 16f - 8f,
                fontSize = if (isMainPop) {
                    Random.nextInt(60, 73).toFloat()
                } else {
                    Random.nextInt(22, 43).toFloat()
                },
                rotation = Random.nextFloat() * 56f - 28f,
                // ms ≡ iOS segundos * 1000
                delay = if (isMainPop) 0L else index * 35L + Random.nextLong(31),
                duration = if (isMainPop) 1_400L else Random.nextLong(1_300L, 2_101L),
                lateralDrift = if (isMainPop) {
                    Random.nextFloat() * 30f - 15f
                } else {
                    Random.nextFloat() * 60f - 30f
                },
                verticalTravel = height * if (isMainPop) 0.62f else (0.48f + Random.nextFloat() * 0.22f),
                peakScale = if (isMainPop) 1.45f else 1.1f + Random.nextFloat() * 0.16f,
                targetScale = if (isMainPop) 1.05f else 0.72f + Random.nextFloat() * 0.16f,
                rotationDelta = Random.nextFloat() * 70f - 35f,
                swayAmplitude = if (isMainPop) 8f else 6f + Random.nextFloat() * 8f,
                swayFrequency = if (isMainPop) 0.5f else 1f + Random.nextFloat() * 1.2f,
            )
        }
        // ≡ UIImpactFeedbackGenerator(style: .light)
        if (!MotionPolicy.reduceMotion) {
            HapticManager.shared.lightImpact()
        }
        // ≡ trimPool: keep last maxConcurrent
        return (existing + particles).takeLast(MAX_CONCURRENT)
    }
}
