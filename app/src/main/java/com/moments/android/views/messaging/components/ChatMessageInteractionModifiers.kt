package com.moments.android.views.messaging.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.R
import kotlin.math.abs

/** Port de `ChatTimestampRevealState`. */
@Stable class ChatTimestampRevealState { var offset by mutableFloatStateOf(0f) }
enum class ChatHorizontalPanDirection { LEFT, RIGHT, BOTH;
    fun accepts(translationX: Float): Boolean = when (this) { LEFT -> translationX < 0; RIGHT -> translationX > 0; BOTH -> true }
}

/** Port de `ChatReplySwipeMetrics`. */
object ChatReplySwipeMetrics {
    const val activationDistance = 84f
    const val maxDrag = 108f
    const val indicatorSize = 32f
    const val hapticStepPoints = 18f
    const val hapticStepCount = 4
    fun rubberBandMagnitude(raw: Float): Float = when { raw <= 0 -> 0f; raw <= maxDrag -> raw; else -> maxDrag + (raw - maxDrag) * .1f }
    fun signedDrag(rawHorizontal: Float, isOutgoing: Boolean): Float = rubberBandMagnitude(abs(rawHorizontal)).let { if (isOutgoing) -it else it }
    fun progress(dragOffset: Float): Float = (abs(dragOffset) / activationDistance).coerceIn(0f, 1f)
}

@Stable class ChatReplySwipeState { var dragOffset by mutableFloatStateOf(0f); var hapticStep by mutableIntStateOf(0) }

@Composable
fun rememberChatReplySwipeState() = remember { ChatReplySwipeState() }

@Composable
fun ChatReplySwipeIndicator(progress: Float, isOutgoing: Boolean = false, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val circle = if (dark) Color.White.copy(.14f) else Color.Black.copy(.08f)
    val track = if (dark) Color.White.copy(.16f) else Color.Black.copy(.1f)
    val line = if (dark) Color.White.copy(.72f) else Color.Black.copy(.45f)
    Canvas(modifier.size(ChatReplySwipeMetrics.indicatorSize.dp).scale(.42f + progress * .58f)) {
        val center = Offset(size.width / 2, size.height / 2); val radius = size.minDimension / 2 - 1.dp.toPx()
        drawCircle(circle, radius, center); drawCircle(track, radius, center, style = Stroke(2.dp.toPx()))
        drawArc(line, -90f, 360f * progress, false, Offset(center.x - radius, center.y - radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val sign = if (isOutgoing) -1 else 1
        drawLine(line.copy(alpha = .55f + progress * .4f), Offset(center.x - 6.dp.toPx() * sign, center.y), Offset(center.x + 5.dp.toPx() * sign, center.y), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun ChatBubbleReplySwipeContainer(state: ChatReplySwipeState, isOutgoing: Boolean, cornerRadius: Float = 18f, onReply: () -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val replyLabel = androidx.compose.ui.res.stringResource(R.string.chat_action_reply)
    Box(Modifier.chatReplySwipeGesture(isOutgoing, state, onReply, haptic).semantics { customActions = listOf(CustomAccessibilityAction(replyLabel) { onReply(); true }) }) {
        if (abs(state.dragOffset) > 2f) ChatReplySwipeIndicator(ChatReplySwipeMetrics.progress(state.dragOffset), isOutgoing, Modifier.zIndex(0f))
        Box(Modifier.zIndex(1f).offset { androidx.compose.ui.unit.IntOffset(state.dragOffset.toInt(), 0) }) { content() }
    }
}

private fun Modifier.chatReplySwipeGesture(isOutgoing: Boolean, state: ChatReplySwipeState, onReply: () -> Unit, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback): Modifier = pointerInput(isOutgoing) {
    var horizontal = 0f; var valid = false
    detectHorizontalDragGestures(
        onHorizontalDrag = { _, delta ->
            horizontal += delta
            if (!valid && abs(horizontal) > 2f) valid = (if (isOutgoing) ChatHorizontalPanDirection.LEFT else ChatHorizontalPanDirection.RIGHT).accepts(horizontal)
            if (!valid) return@detectHorizontalDragGestures
            state.dragOffset = ChatReplySwipeMetrics.signedDrag(horizontal, isOutgoing)
            val step = if (ChatReplySwipeMetrics.progress(state.dragOffset) >= 1f) 5 else (abs(state.dragOffset) / ChatReplySwipeMetrics.hapticStepPoints).toInt().coerceAtMost(4)
            if (step != state.hapticStep) { haptic.performHapticFeedback(if (step == 5) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove); state.hapticStep = step }
        },
        onDragEnd = { if (valid && ChatReplySwipeMetrics.progress(state.dragOffset) >= 1f) onReply(); state.dragOffset = 0f; state.hapticStep = 0 },
        onDragCancel = { state.dragOffset = 0f; state.hapticStep = 0 },
    )
}

fun Modifier.chatTimestampRevealGesture(enabled: Boolean = true, state: ChatTimestampRevealState): Modifier = pointerInput(enabled) {
    var x = 0f; var horizontal = false
    detectHorizontalDragGestures(
        onHorizontalDrag = { _, delta -> if (enabled) { x += delta; if (x < 0f) { horizontal = true; state.offset = if (x < -70f) -70f + (x + 70f) * .25f else x.coerceAtLeast(-90f) } } },
        onDragEnd = { if (horizontal) state.offset = 0f }, onDragCancel = { state.offset = 0f },
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.chatMessageLongPress(onLongPress: () -> Unit): Modifier = combinedClickable(onClick = {}, onLongClick = onLongPress)
