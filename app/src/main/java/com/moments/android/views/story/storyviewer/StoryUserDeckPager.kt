package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.moments.android.views.story.StoryDeckGestureGate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Roles de `StoryDeckPageRole`. */
enum class StoryDeckPageRole { LEADING, CENTER, TRAILING }

/** Port de `StoryUserDeckPager.swift` con preview de páginas vecinas. */
@Composable
fun StoryUserDeckPager(
    userIds: List<String>,
    currentUserIndex: Int,
    onCurrentUserIndexChange: (Int) -> Unit,
    isDeckGestureEnabled: Boolean = true,
    gestureGate: StoryDeckGestureGate? = null,
    onUserChanged: ((Int) -> Unit)? = null,
    content: @Composable (String, StoryDeckPageRole, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val visible = (currentUserIndex - 1..currentUserIndex + 1).filter { it in userIds.indices }
        Box(
            Modifier.fillMaxSize().pointerInput(userIds, currentUserIndex, isDeckGestureEnabled, gestureGate?.suppressDeckNavigation) {
                detectDragGestures(
                    onDragStart = { },
                    onDragCancel = { dragOffset = 0f; dragging = false },
                    onDragEnd = {
                        val commit = abs(dragOffset) > width * .28f
                        val next = when {
                            commit && dragOffset > 0 && currentUserIndex > 0 -> currentUserIndex - 1
                            commit && dragOffset < 0 && currentUserIndex < userIds.lastIndex -> currentUserIndex + 1
                            else -> currentUserIndex
                        }
                        if (next != currentUserIndex) { onCurrentUserIndexChange(next); onUserChanged?.invoke(next) }
                        dragOffset = 0f; dragging = false
                    },
                    onDrag = { change, amount ->
                        if (!isDeckGestureEnabled || gestureGate?.suppressDeckNavigation == true) return@detectDragGestures
                        if (abs(amount.x) <= abs(amount.y) * 1.2f) return@detectDragGestures
                        change.consume()
                        dragging = true
                        val raw = dragOffset + amount.x
                        dragOffset = when {
                            raw > 0 && currentUserIndex == 0 -> raw * .22f
                            raw < 0 && currentUserIndex == userIds.lastIndex -> raw * .22f
                            else -> raw
                        }
                    },
                )
            },
        ) {
            visible.forEach { index ->
                val progress = index - currentUserIndex + dragOffset / width
                val magnitude = abs(progress).coerceAtMost(1f)
                val role = when { index < currentUserIndex -> StoryDeckPageRole.LEADING; index > currentUserIndex -> StoryDeckPageRole.TRAILING; else -> StoryDeckPageRole.CENTER }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (index - currentUserIndex) * width + dragOffset
                            scaleX = 1f - magnitude * .06f
                            scaleY = 1f - magnitude * .06f
                            alpha = 1f - magnitude * .48f
                        }
                        .blur(if (magnitude > .04f && magnitude < .98f) (5f * magnitude).dp else 0.dp),
                ) { content(userIds[index], role, dragging) }
            }
        }
    }
}
