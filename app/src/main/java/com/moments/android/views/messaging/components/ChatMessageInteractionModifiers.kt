package com.moments.android.views.messaging.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Port de `ChatTimestampRevealState`. */
@Stable
class ChatTimestampRevealState {
    var offset by mutableFloatStateOf(0f)
}

enum class ChatHorizontalPanDirection {
    LEFT, RIGHT, BOTH;

    fun accepts(translationX: Float): Boolean = when (this) {
        LEFT -> translationX < 0
        RIGHT -> translationX > 0
        BOTH -> true
    }
}

/** Port de `ChatReplySwipeMetrics`. */
object ChatReplySwipeMetrics {
    const val activationDistance = 84f
    const val maxDrag = 108f
    const val indicatorSize = 32f
    const val hapticStepPoints = 18f
    const val hapticStepCount = 4

    fun rubberBandMagnitude(raw: Float): Float = when {
        raw <= 0f -> 0f
        raw <= maxDrag -> raw
        else -> maxDrag + (raw - maxDrag) * 0.1f
    }

    fun signedDrag(rawHorizontal: Float, isOutgoing: Boolean): Float {
        val magnitude = rubberBandMagnitude(abs(rawHorizontal))
        if (magnitude <= 0f) return 0f
        return if (isOutgoing) -magnitude else magnitude
    }

    fun progress(dragOffset: Float): Float =
        (abs(dragOffset) / activationDistance).coerceIn(0f, 1f)
}

@Stable
class ChatReplySwipeState {
    var dragOffset by mutableFloatStateOf(0f)
    var hapticStep by mutableIntStateOf(0)
}

@Composable
fun rememberChatReplySwipeState() = remember { ChatReplySwipeState() }

private fun headerSpring() = spring<Float>(
    dampingRatio = MotionPolicy.Spring.HEADER_DAMPING.toFloat(),
    stiffness = 400f,
)

private fun timestampReturnSpring() = spring<Float>(
    dampingRatio = MotionPolicy.Spring.TIMESTAMP_RETURN_DAMPING.toFloat(),
    stiffness = 700f,
)

@Composable
fun ChatReplySwipeIndicator(
    progress: Float,
    isOutgoing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val circle = if (dark) Color.White.copy(0.14f) else Color.Black.copy(0.08f)
    val track = if (dark) Color.White.copy(0.16f) else Color.Black.copy(0.1f)
    val progressColor = if (dark) Color.White.copy(0.72f) else Color.Black.copy(0.45f)
    val arrowColor = if (dark) {
        Color.White.copy(0.55f + progress * 0.4f)
    } else {
        Color.Black.copy(0.35f + progress * 0.45f)
    }
    Box(
        modifier
            .size(ChatReplySwipeMetrics.indicatorSize.dp)
            .scale(0.42f + progress * 0.58f)
            .alpha(0.15f + progress * 0.85f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 1.dp.toPx()
            drawCircle(circle, radius, center)
            drawCircle(track, radius, center, style = Stroke(2.dp.toPx()))
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = arrowColor,
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { scaleX = if (isOutgoing) -1f else 1f },
        )
    }
}

@Composable
fun ChatBubbleReplySwipeContainer(
    state: ChatReplySwipeState,
    isOutgoing: Boolean,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Float = 18f,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val replyLabel = stringResource(R.string.chat_action_reply)
    // Hit-test = tamaño de la burbuja (wrap), no el ancho vacío de la fila.
    // `cornerRadius` alimenta iOS `contentShape(RoundedRectangle)`; en Compose el hit
    // ya coincide con el wrap del contenido (sin clip, para no cortar el offset horizontal).
    Box(
        modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .chatReplySwipeGesture(isOutgoing, state, onReply, scope, view)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(replyLabel) {
                        onReply()
                        true
                    },
                )
            },
        // ≡ iOS `ZStack(alignment: isOutgoing ? .trailing : .leading)`
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (abs(state.dragOffset) > 2f) {
            ChatReplySwipeIndicator(
                progress = ChatReplySwipeMetrics.progress(state.dragOffset),
                isOutgoing = isOutgoing,
                // ≡ iOS `.allowsHitTesting(false)` — zIndex bajo; el content absorbe el hit.
                modifier = Modifier.zIndex(0f),
            )
        }
        Box(
            Modifier
                .zIndex(1f)
                .offset { IntOffset(state.dragOffset.toInt(), 0) },
        ) {
            content()
        }
    }
}

/**
 * Port de `ChatHorizontalPanGestureRecognizer`: falla pronto si el gesto es vertical
 * para no robar el scroll del LazyList.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectChatHorizontalPan(
    direction: ChatHorizontalPanDirection,
    onChanged: (translationX: Float) -> Unit,
    onEnded: (completed: Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var validated = false
        var failed = false
        var completed = false

        while (!failed) {
            // Hasta validar horizontal, observar en Initial para no robar el tap/long-press
            // de la burbuja (Main). Tras validar, consumir en Main como iOS reply/timestamp.
            val pass = if (validated) PointerEventPass.Main else PointerEventPass.Initial
            val event = awaitPointerEvent(pass)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) {
                completed = validated
                break
            }
            val delta = change.positionChange()
            if (!validated) {
                totalX += delta.x
                totalY += delta.y
            } else {
                totalX += delta.x
                totalY += delta.y
                change.consume()
                onChanged(totalX)
                continue
            }
            val horizontal = abs(totalX)
            val vertical = abs(totalY)

            if (vertical > 2f && vertical > horizontal) {
                failed = true
                break
            }
            if (horizontal > 2f && !direction.accepts(totalX)) {
                failed = true
                break
            }
            if (horizontal > 2f && horizontal > vertical * 1.2f) {
                validated = true
                onChanged(totalX)
            }
        }
        onEnded(completed && !failed)
    }
}

private fun Modifier.chatReplySwipeGesture(
    isOutgoing: Boolean,
    state: ChatReplySwipeState,
    onReply: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    view: android.view.View,
): Modifier = pointerInput(isOutgoing) {
    val direction = if (isOutgoing) ChatHorizontalPanDirection.LEFT else ChatHorizontalPanDirection.RIGHT
    detectChatHorizontalPan(
        direction = direction,
        onChanged = { horizontal ->
            state.dragOffset = ChatReplySwipeMetrics.signedDrag(horizontal, isOutgoing)
            val magnitude = abs(state.dragOffset)
            val progress = ChatReplySwipeMetrics.progress(state.dragOffset)
            val nextStep = if (progress >= 1f) {
                ChatReplySwipeMetrics.hapticStepCount + 1
            } else {
                (magnitude / ChatReplySwipeMetrics.hapticStepPoints).toInt()
                    .coerceAtMost(ChatReplySwipeMetrics.hapticStepCount)
            }
            if (nextStep != state.hapticStep) {
                when {
                    nextStep == ChatReplySwipeMetrics.hapticStepCount + 1 ->
                        HapticManager.shared.replySwipeThresholdReached(view)
                    nextStep > 0 || state.hapticStep > 0 ->
                        HapticManager.shared.replySwipeStep(view)
                }
                state.hapticStep = nextStep
            }
        },
        onEnded = { completed ->
            val didComplete = completed && ChatReplySwipeMetrics.progress(state.dragOffset) >= 1f
            if (didComplete) onReply()
            scope.launch {
                settleReplySwipe(state)
            }
        },
    )
}

private suspend fun settleReplySwipe(state: ChatReplySwipeState) {
    if (MotionPolicy.reduceMotion) {
        state.dragOffset = 0f
        state.hapticStep = 0
        return
    }
    val anim = Animatable(state.dragOffset)
    anim.animateTo(0f, headerSpring()) { state.dragOffset = value }
    state.hapticStep = 0
}

fun Modifier.chatTimestampRevealGesture(
    enabled: Boolean = true,
    state: ChatTimestampRevealState,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    coroutineScope {
        detectChatHorizontalPan(
            direction = ChatHorizontalPanDirection.LEFT,
            onChanged = { horizontal ->
                val base = horizontal
                val offset = if (base < -70f) -70f + (base + 70f) * 0.25f else base
                state.offset = offset.coerceAtLeast(-90f)
            },
            onEnded = {
                launch {
                    settleTimestampReveal(state)
                }
            },
        )
    }
}

private suspend fun settleTimestampReveal(state: ChatTimestampRevealState) {
    if (MotionPolicy.reduceMotion) {
        state.offset = 0f
        return
    }
    val anim = Animatable(state.offset)
    anim.animateTo(0f, timestampReturnSpring()) { state.offset = value }
}

/** Port de `View.chatTimestampRevealGutter`.
 *
 * Usar **dentro de un `Row`** junto a la burbuja: `fillMaxHeight()` toma la altura
 * del sibling (≡ iOS `maxHeight: .infinity` en HStack), no el viewport del LazyColumn.
 */
@Composable
fun ChatTimestampRevealGutter(
    state: ChatTimestampRevealState,
    isEnabled: Boolean,
    minWidth: Dp = 50.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .widthIn(min = minWidth)
            .fillMaxWidth()
            .fillMaxHeight()
            .then(
                if (isEnabled) Modifier.chatTimestampRevealGesture(true, state)
                else Modifier,
            ),
    )
}

/**
 * Port de `chatMessagePressClassifier`: 0.32s, max drift 18pt.
 * Soltar antes = tap; al cumplir el umbral se consume el pulso para que no abra el cuerpo.
 */
fun Modifier.chatMessagePressClassifier(
    onPressingChanged: ((Boolean) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: () -> Unit,
    childHandlesTap: Boolean = false,
): Modifier = composed {
    val onTapUpdated = rememberUpdatedState(onTap)
    val onLongPressUpdated = rememberUpdatedState(onLongPress)
    val onPressingUpdated = rememberUpdatedState(onPressingChanged)
    Modifier.pointerInput(onTap, childHandlesTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPressingUpdated.value?.invoke(true)
            val origin = down.position
            var cancelled = false
            var liftedEarly = false
            // Media (onTap): Initial evita falso long-press si un hijo consume el up.
            // Spoilers (childHandlesTap): Main para no robar el tap del hijo clickable.
            // Texto plano / mentions: Initial para detectar el up antes de ClickableText.
            val observePass = when {
                onTapUpdated.value != null -> PointerEventPass.Initial
                childHandlesTap -> PointerEventPass.Main
                else -> PointerEventPass.Initial
            }
            val timedOut = withTimeoutOrNull(320L) {
                while (true) {
                    val event = awaitPointerEvent(observePass)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                        cancelled = true
                        return@withTimeoutOrNull Unit
                    }
                    if (change.changedToUp()) {
                        liftedEarly = true
                        return@withTimeoutOrNull Unit
                    }
                    val dx = change.position.x - origin.x
                    val dy = change.position.y - origin.y
                    if (hypot(dx.toDouble(), dy.toDouble()) > 18.0) {
                        cancelled = true
                        return@withTimeoutOrNull Unit
                    }
                }
            } == null
            if (timedOut && !cancelled && !liftedEarly) {
                HapticManager.shared.heavyImpact()
                onLongPressUpdated.value()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (change.changedToUp()) break
                }
            } else if (liftedEarly && !cancelled) {
                onTapUpdated.value?.invoke()
            }
            onPressingUpdated.value?.invoke(false)
        }
    }
}

/**
 * Port de `chatMessageLongPress`: 0.32s, max drift 18pt, heavy haptic.
 */
fun Modifier.chatMessageLongPress(
    onPressingChanged: ((Boolean) -> Unit)? = null,
    onLongPress: () -> Unit,
    childHandlesTap: Boolean = false,
): Modifier = chatMessagePressClassifier(
    onPressingChanged = onPressingChanged,
    onTap = null,
    onLongPress = onLongPress,
    childHandlesTap = childHandlesTap,
)

/** Port de `ChatMessageBodyOpen`. */
object ChatMessageBodyOpen {
    fun viewOnceReplayAvailable(message: EnhancedMessage, currentUserId: String): Boolean =
        message.allowReplay == true &&
            message.replayAvailableInCurrentChatSession &&
            !message.replayConsumedInCurrentChatSession &&
            !message.hasBeenReplayedBy(currentUserId)

    fun viewOnceEffectiveViewed(message: EnhancedMessage): Boolean =
        message.isViewed || message.replayAvailableInCurrentChatSession

    fun isOpenable(message: EnhancedMessage, isCurrentUser: Boolean, currentUserId: String): Boolean {
        if (message.isDeleted) return false
        return when (message.type) {
            MessageType.IMAGE, MessageType.VIDEO, MessageType.LOCATION,
            MessageType.SHARED_MOMENT, MessageType.SHARED_STORY, MessageType.EPHEMERAL,
            -> true
            MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO -> {
                if (isCurrentUser) false
                else if (viewOnceEffectiveViewed(message)) {
                    viewOnceReplayAvailable(message, currentUserId)
                } else {
                    true
                }
            }
            else -> false
        }
    }

    fun open(
        message: EnhancedMessage,
        isCurrentUser: Boolean,
        currentUserId: String,
        cluster: List<EnhancedMessage>? = null,
        onOpenMedia: (EnhancedMessage) -> Unit,
        onOpenCluster: ((List<EnhancedMessage>) -> Unit)? = null,
        onMomentNavigation: ((EnhancedMessage) -> Unit)?,
        onStoryNavigation: ((EnhancedMessage) -> Unit)?,
        onViewOnceOpen: ((EnhancedMessage, Boolean) -> Unit)?,
        onOpenLocation: ((EnhancedMessage) -> Unit)?,
        onHydrateMedia: ((EnhancedMessage) -> Unit)?,
        onMessageViewed: ((String) -> Unit)?,
    ) {
        if (cluster != null && cluster.size > 1) {
            onOpenCluster?.invoke(cluster)
            return
        }
        if (message.isDeleted) return
        when (message.type) {
            MessageType.IMAGE, MessageType.VIDEO -> onOpenMedia(message)
            MessageType.LOCATION -> onOpenLocation?.invoke(message)
            MessageType.SHARED_MOMENT -> onMomentNavigation?.invoke(message)
            MessageType.SHARED_STORY -> onStoryNavigation?.invoke(message)
            MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO -> {
                if (isCurrentUser) return
                val viewed = viewOnceEffectiveViewed(message)
                if (viewed) {
                    if (!viewOnceReplayAvailable(message, currentUserId)) return
                    onViewOnceOpen?.invoke(message, true)
                } else {
                    onViewOnceOpen?.invoke(message, false)
                }
            }
            MessageType.EPHEMERAL -> {
                if (!message.isViewed) {
                    onHydrateMedia?.invoke(message)
                    onMessageViewed?.invoke(message.id)
                } else {
                    onOpenMedia(message)
                }
            }
            else -> Unit
        }
    }
}
