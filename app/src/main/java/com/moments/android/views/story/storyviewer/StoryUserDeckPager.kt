package com.moments.android.views.story.storyviewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.views.story.StoryDeckGestureGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class StoryDeckPageRole { LEADING, CENTER, TRAILING }

/**
 * Port directo de `StoryUserDeckPager.swift`.
 *
 * El deck escucha en la pasada inicial del árbol de punteros, igual que el
 * `.simultaneousGesture` de SwiftUI. Es importante: cada StoryViewer consume
 * sus propios gestos después, así que un pager anidado no llega a armarlos.
 */
@Composable
fun StoryUserDeckPager(
    userIds: List<String>,
    currentUserIndex: Int,
    onCurrentUserIndexChange: (Int) -> Unit,
    isDeckGestureEnabled: Boolean = true,
    gestureGate: StoryDeckGestureGate? = null,
    onUserChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (userId: String, role: StoryDeckPageRole, isDraggingDeck: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val gestureCoordinator = remember { StoryGestureCoordinator() }
    val dragOffset = remember { Animatable(0f) }
    var isDraggingDeck by remember { mutableStateOf(false) }
    var pagerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val indexState = rememberUpdatedState(currentUserIndex)
    val userIdsState = rememberUpdatedState(userIds)
    val enabledState = rememberUpdatedState(isDeckGestureEnabled)
    val gateState = rememberUpdatedState(gestureGate)
    val onIndexChangeState = rememberUpdatedState(onCurrentUserIndexChange)
    val onUserChangedState = rememberUpdatedState(onUserChanged)

    val deckBackground = if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val commitThreshold = 0.28f
    val flickVelocityPx = with(density) { 420.dp.toPx() }
    val deckArmDistancePx = with(density) { 22.dp.toPx() }
    val horizontalDominanceRatio = 1.2f

    fun resetDrag(animated: Boolean) {
        scope.launch {
            if (animated && !MotionPolicy.reduceMotion) {
                dragOffset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 380f))
            } else {
                dragOffset.snapTo(0f)
            }
            isDraggingDeck = false
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(deckBackground)
            .onGloballyPositioned { pagerCoords = it },
    ) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val screenRectLocal = Rect(0f, 0f, width, height)
        val ids = userIdsState.value.filter { it.isNotBlank() }
        if (ids.isEmpty()) return@BoxWithConstraints
        val current = indexState.value.coerceIn(0, ids.lastIndex)
        val visible = ((current - 1)..(current + 1)).filter { it in ids.indices }
        val stackBase = run {
            val position = visible.indexOf(current).takeIf { it >= 0 } ?: return@run 0f
            val centerOfCurrent = position * width + width / 2f
            width / 2f - centerOfCurrent
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ids.size, width, height) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!enabledState.value || userIdsState.value.size <= 1) return@awaitEachGesture

                        val coords = pagerCoords
                        val pointInRoot = coords?.localToRoot(down.position) ?: down.position
                        val screenRectRoot = if (coords != null) {
                            val origin = coords.localToRoot(Offset.Zero)
                            Rect(origin.x, origin.y, origin.x + width, origin.y + height)
                        } else screenRectLocal

                        val gate = gateState.value
                        if (!gestureCoordinator.shouldAllowDeckSwipeStart(
                                point = pointInRoot,
                                screenRect = screenRectRoot,
                                regions = gate?.interactionRegions.orEmpty(),
                                gate = gate,
                            )
                        ) return@awaitEachGesture

                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var armed = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                total += delta

                                val isHorizontal = abs(total.x) > abs(total.y) * horizontalDominanceRatio
                                if (!isHorizontal || abs(total.x) <= deckArmDistancePx) {
                                    if (armed || dragOffset.value != 0f) {
                                        scope.launch { dragOffset.snapTo(0f) }
                                        isDraggingDeck = false
                                        armed = false
                                    }
                                    continue
                                }

                                change.consume()
                                if (!armed) {
                                    armed = true
                                    isDraggingDeck = true
                                    HapticManager.shared.lightImpact(view)
                                }

                                val index = indexState.value
                                val count = userIdsState.value.size
                                val raw = total.x
                                val clamped = when {
                                    raw > 0f && index == 0 -> raw * 0.22f
                                    raw < 0f && index >= count - 1 -> raw * 0.22f
                                    else -> raw
                                }
                                scope.launch { dragOffset.snapTo(clamped) }
                            }
                        } finally {
                            if (!armed) {
                                isDraggingDeck = false
                                return@awaitEachGesture
                            }

                            val velocity = tracker.calculateVelocity()
                            val translationX = dragOffset.value
                            val isHorizontal = abs(total.x) > abs(total.y) * horizontalDominanceRatio &&
                                abs(total.x) > deckArmDistancePx
                            if (!isHorizontal) {
                                resetDrag(animated = true)
                                return@awaitEachGesture
                            }

                            val index = indexState.value
                            val count = userIdsState.value.size
                            val previous = translationX > 0f
                            val canNavigate = if (previous) index > 0 else index < count - 1
                            val crossed = abs(translationX) > width * commitThreshold
                            val flickedPrevious = velocity.x > flickVelocityPx && translationX >= 0f
                            val flickedNext = velocity.x < -flickVelocityPx && translationX <= 0f
                            if (canNavigate && (crossed || (previous && flickedPrevious) || (!previous && flickedNext))) {
                                HapticManager.shared.mediumImpact(view)
                                val exitOffset = if (previous) width else -width
                                scope.launch {
                                    if (!MotionPolicy.reduceMotion) {
                                        launch { dragOffset.animateTo(exitOffset, spring(dampingRatio = 0.86f, stiffness = 500f)) }
                                        delay(220)
                                    } else dragOffset.snapTo(exitOffset)
                                    val next = if (previous) (index - 1).coerceAtLeast(0) else (index + 1).coerceAtMost(count - 1)
                                    if (next != index) {
                                        onIndexChangeState.value(next)
                                        onUserChangedState.value?.invoke(next)
                                    }
                                    dragOffset.snapTo(0f)
                                    isDraggingDeck = false
                                }
                            } else resetDrag(animated = true)
                        }
                    }
                },
        ) {
            // No Row(fillMaxSize): Compose limita el maxWidth al viewport y las
            // páginas 2..N miden ancho 0 → negro. Cada página fillMaxSize + offset
            // (equivalente al HStack ancho + offset de SwiftUI).
            visible.forEachIndexed { position, index ->
                val role = when {
                    index < current -> StoryDeckPageRole.LEADING
                    index > current -> StoryDeckPageRole.TRAILING
                    else -> StoryDeckPageRole.CENTER
                }
                val progress = (index - current).toFloat() + dragOffset.value / width
                val pageX = stackBase + dragOffset.value + position * width
                Box(
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(pageX.roundToInt(), 0) }
                        .zIndex(if (role == StoryDeckPageRole.CENTER) 1f else 0f)
                        .deckPassPageVisual(progress),
                ) {
                    content(ids[index], role, isDraggingDeck)
                    if (role != StoryDeckPageRole.CENTER) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            event.changes.forEach { it.consume() }
                                            if (event.changes.none { it.id == down.id && it.pressed }) break
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.deckPassPageVisual(progress: Float): Modifier {
    val magnitude = abs(progress).coerceAtMost(1f)
    if (magnitude < 0.001f) return this
    val scale = (1f - magnitude * 0.06f).coerceAtLeast(0.94f)
    val alpha = (1f - magnitude * 0.48f).coerceAtLeast(0.52f)
    // No `Modifier.blur`: en Android randeriza negro sobre ExoPlayer/AsyncImage.
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
