package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.story.storystickers.FloatingHeart
import com.moments.android.views.story.storystickers.FloatingHeartsView
import kotlin.random.Random

/** Port de `StorySegmentProgressChrome`. */
@Composable
fun StorySegmentProgressChrome(storyCount: Int, storyIndex: Int, progressForSegment: (Int) -> Float, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        repeat(storyCount) { index ->
            Box(Modifier.weight(1f).height(3.dp).background(Color.White.copy(alpha = .28f))) {
                Box(Modifier.fillMaxWidth(progressForSegment(index).coerceIn(0f, 1f)).height(3.dp).background(Color.White))
            }
        }
    }
}

/** Port de `StoryFloatingReactionLayer`. */
@Composable
fun StoryFloatingReactionLayer(hearts: List<FloatingHeart>, containerSize: DpSize, modifier: Modifier = Modifier) {
    FloatingHeartsView(hearts = hearts, containerSize = containerSize, modifier = modifier)
}

/** Port de `StoryReactionBurst`: genera y limita las partículas de reacción. */
object StoryReactionBurst {
    const val MAX_CONCURRENT = 48

    fun emit(existing: List<FloatingHeart>, emoji: String, width: Float, height: Float, sourceX: Float = width * .82f, sourceY: Float = height * .92f): List<FloatingHeart> {
        if (width <= 0f || height <= 0f) return existing
        val count = if (MotionPolicy.reduceMotion) 3 else Random.nextInt(5, 9)
        val particles = List(count) { index ->
            val main = index == 0 && !MotionPolicy.reduceMotion
            FloatingHeart(
                emoji = emoji,
                startX = sourceX + Random.nextFloat() * 40f - 20f,
                startY = sourceY + Random.nextFloat() * 16f - 8f,
                fontSize = if (main) Random.nextInt(60, 73).toFloat() else Random.nextInt(22, 43).toFloat(),
                rotation = Random.nextFloat() * 56f - 28f,
                delay = if (main) 0 else (index * 35L + Random.nextLong(31)),
                duration = if (main) 1_400L else Random.nextLong(1_300L, 2_101L),
                lateralDrift = Random.nextFloat() * 60f - 30f,
                verticalTravel = height * if (main) .62f else (.48f + Random.nextFloat() * .22f),
                peakScale = if (main) 1.45f else 1.1f + Random.nextFloat() * .16f,
                targetScale = if (main) 1.05f else .72f + Random.nextFloat() * .16f,
                rotationDelta = Random.nextFloat() * 70f - 35f,
                swayAmplitude = if (main) 8f else 6f + Random.nextFloat() * 8f,
                swayFrequency = if (main) .5f else 1f + Random.nextFloat() * 1.2f,
            )
        }
        return (existing + particles).takeLast(MAX_CONCURRENT)
    }
}
