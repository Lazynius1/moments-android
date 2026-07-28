package com.moments.android.views.story.storyviewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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

/** Roles de `StoryDeckPageRole`. */
enum class StoryDeckPageRole { LEADING, CENTER, TRAILING }

/**
 * Port de `StoryUserDeckPager.swift` — Deck Pass entre usuarios del ring.
 * Preview vecinas + escala/opacity/blur; gesto con bandas laterales vía [StoryGestureCoordinator].
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
                // ≡ spring(response: 0.42, dampingFraction: 0.78)
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
        // Rect local del pager; el punto del gesto se convierte a root (≡ exclusion boundsInRoot)
        val screenRectLocal = Rect(0f, 0f, width, height)
        val ids = userIdsState.value
        val current = indexState.value.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        val visible = ((current - 1)..(current + 1)).filter { it in ids.indices }
        // ≡ stackBaseOffset: centra la página current en el HStack de visibles
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
                        // Initial pass: no esperar a que el viewer consuma el down (≡ simultaneousGesture)
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!enabledState.value || userIdsState.value.size <= 1) return@awaitEachGesture

                        val coords = pagerCoords
                        val pointInRoot = coords?.localToRoot(down.position) ?: down.position
                        val screenRectRoot = if (coords != null) {
                            val origin = coords.localToRoot(Offset.Zero)
                            Rect(origin.x, origin.y, origin.x + width, origin.y + height)
                        } else {
                            screenRectLocal
                        }

                        val gate = gateState.value
                        val allowStart = gestureCoordinator.shouldAllowDeckSwipeStart(
                            point = pointInRoot,
                            screenRect = screenRectRoot,
                            regions = gate?.interactionRegions.orEmpty(),
                            gate = gate,
                        )
                        if (!allowStart) return@awaitEachGesture

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

                                val horizontalTravel = abs(total.x)
                                val verticalTravel = abs(total.y)
                                val isHorizontal =
                                    horizontalTravel > verticalTravel * horizontalDominanceRatio
                                if (!isHorizontal || horizontalTravel <= deckArmDistancePx) {
                                    if (armed || dragOffset.value != 0f) {
                                        scope.launch { dragOffset.snapTo(0f) }
                                        isDraggingDeck = false
                                        armed = false
                                    }
                                    continue
                                }

                                // Armado: consumir para no pelear con hold/nav del viewer
                                change.consume()
                                if (!armed) {
                                    armed = true
                                    isDraggingDeck = true
                                    HapticManager.shared.lightImpact(view)
                                }

                                val idx = indexState.value
                                val count = userIdsState.value.size
                                val raw = total.x
                                val clamped = when {
                                    raw > 0f && idx == 0 -> raw * 0.22f
                                    raw < 0f && idx >= count - 1 -> raw * 0.22f
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

                            val idx = indexState.value
                            val count = userIdsState.value.size
                            val goToPrevious = translationX > 0f
                            val canNavigate = if (goToPrevious) idx > 0 else idx < count - 1
                            val crossed = abs(translationX) > width * commitThreshold
                            val flickedPrevious = velocity.x > flickVelocityPx && translationX >= 0f
                            val flickedNext = velocity.x < -flickVelocityPx && translationX <= 0f
                            val shouldCommit = canNavigate && (
                                crossed || (goToPrevious && flickedPrevious) || (!goToPrevious && flickedNext)
                            )

                            if (shouldCommit) {
                                HapticManager.shared.mediumImpact(view)
                                val exitOffset = if (goToPrevious) width else -width
                                scope.launch {
                                    // ≡ Spring.sheet + asyncAfter(0.22) desde el inicio (no al terminar)
                                    if (!MotionPolicy.reduceMotion) {
                                        launch {
                                            dragOffset.animateTo(
                                                exitOffset,
                                                spring(dampingRatio = 0.86f, stiffness = 500f),
                                            )
                                        }
                                        delay(220)
                                    } else {
                                        dragOffset.snapTo(exitOffset)
                                    }
                                    val next = if (goToPrevious) {
                                        (idx - 1).coerceAtLeast(0)
                                    } else {
                                        (idx + 1).coerceAtMost(count - 1)
                                    }
                                    if (next != idx) {
                                        onIndexChangeState.value(next)
                                        onUserChangedState.value?.invoke(next)
                                    }
                                    dragOffset.snapTo(0f)
                                    isDraggingDeck = false
                                }
                            } else {
                                resetDrag(animated = true)
                            }
                        }
                    }
                },
        ) {
            // ≡ HStack(spacing: 0) + offset(stackOffset + dragOffset)
            Row(
                Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset((stackBase + dragOffset.value).roundToInt(), 0)
                    },
            ) {
                visible.forEach { index ->
                    val role = when {
                        index < current -> StoryDeckPageRole.LEADING
                        index > current -> StoryDeckPageRole.TRAILING
                        else -> StoryDeckPageRole.CENTER
                    }
                    val progress = (index - current).toFloat() + dragOffset.value / width

                    Box(
                        Modifier
                            .width(with(density) { width.toDp() })
                            .fillMaxHeight()
                            .zIndex(if (role == StoryDeckPageRole.CENTER) 1f else 0f)
                            .deckPassPageVisual(progress),
                    ) {
                        content(ids[index], role, isDraggingDeck)
                        // ≡ .allowsHitTesting(role == .center): laterales solo visual
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
}

/** ≡ `DeckPassPageModifier` — scale / opacity / blur según progress. */
private fun Modifier.deckPassPageVisual(progress: Float): Modifier {
    val magnitude = abs(progress).coerceAtMost(1f)
    val scale = (1f - magnitude * 0.06f).coerceAtLeast(0.94f)
    val alpha = (1f - magnitude * 0.48f).coerceAtLeast(0.52f)
    val blurRadius = if (magnitude > 0.04f && magnitude < 0.98f) {
        5f * magnitude.coerceAtMost(1f)
    } else {
        0f
    }
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .blur(blurRadius.dp)
}
