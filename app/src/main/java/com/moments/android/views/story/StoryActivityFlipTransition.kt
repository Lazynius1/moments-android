package com.moments.android.views.story

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.models.Story
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Port de `StoryActivityFlipTransition.swift`.
 *
 * La miniatura es la cara frontal y la vista real de estadísticas es la cara
 * trasera. El overlay vive en el mismo root que el grid para que las coordenadas
 * de la celda no dependan de ventanas o capturas de pantalla.
 */
@Composable
fun StoryActivityFlipTransition(
    story: Story,
    sourceBounds: Rect,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(story.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isClosing by remember(story.id) { mutableStateOf(false) }

    fun close() {
        if (isClosing) return
        isClosing = true
        scope.launch {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            )
            onDismiss()
        }
    }

    BackHandler(onBack = ::close)

    LaunchedEffect(story.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val horizontalInset = with(density) { 18.dp.toPx() }
        val destinationHeight = heightPx * 0.82f
        val destinationTop = (heightPx - destinationHeight) / 2f
        val safeSource = sourceBounds.takeIf { it.width > 0f && it.height > 0f }
            ?: Rect(
                left = widthPx / 2f - 24f,
                top = heightPx / 2f - 24f,
                right = widthPx / 2f + 24f,
                bottom = heightPx / 2f + 24f,
            )
        val destinationBounds = Rect(
            left = horizontalInset,
            top = destinationTop,
            right = widthPx - horizontalInset,
            bottom = destinationTop + destinationHeight,
        )
        val frame = lerpRect(safeSource, destinationBounds, progress.value)
        val cornerRadius = with(density) { (28.dp.toPx() * progress.value).toDp() }
        val width = with(density) { frame.width.toDp() }
        val height = with(density) { frame.height.toDp() }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * progress.value))
                .clickable(onClick = ::close),
        )

        Box(
            Modifier
                .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                .size(width = width, height = height)
                .graphicsLayer {
                    rotationY = 180f * progress.value
                    cameraDistance = 28f * density.density
                    shape = RoundedCornerShape(cornerRadius)
                    clip = true
                    shadowElevation = 24f * density.density * progress.value
                }
                .zIndex(1f),
        ) {
            if (progress.value < 0.5f) {
                ArchiveStoryCardVisual(
                    story = story,
                    cornerRadius = 0.dp,
                    keepsArchiveAspectRatio = false,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f },
                ) {
                    StoryStatsView(
                        story = story,
                        onDismiss = ::close,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun lerpRect(start: Rect, end: Rect, progress: Float): Rect = Rect(
    left = lerp(start.left, end.left, progress),
    top = lerp(start.top, end.top, progress),
    right = lerp(start.right, end.right, progress),
    bottom = lerp(start.bottom, end.bottom, progress),
)

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
