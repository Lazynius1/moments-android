package com.moments.android.views.messaging.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/** Port de `Views/Messaging/Components/VoiceRecordingGestureViews.swift`. */
enum class VoiceRecordingFinishAction { SEND, CANCEL }
class VoiceRecordingGestureState {
    var cancelDragOffset by mutableFloatStateOf(0f)
    var lockProgress by mutableFloatStateOf(0f)
    var followX by mutableFloatStateOf(0f)
    var followY by mutableFloatStateOf(0f)
}
object VoiceRecordingBlobMetrics { val surface=110.dp; val aura=176.dp; val innerAura=150.dp; val icon=30.dp; val lockOffset=(-122).dp; const val lockDistance=105f; const val cancelDistance=150f }

@Composable
fun VoiceRecordingReactiveAura(audioPower: Float, modifier: Modifier = Modifier) {
    val fast = ((audioPower.coerceIn(0f, 1f) - .12f) / .88f).coerceAtLeast(0f)
    Canvas(modifier.size(VoiceRecordingBlobMetrics.aura).scale(.625f + fast * .375f)) {
        drawCircle(Color(0xFF3F6F8F).copy(.24f))
        drawCircle(Color(0xFF007AFF).copy(.30f), radius = size.minDimension * .42f)
    }
}

@Composable
fun VoiceRecordingGestureButton(
    tint: Color,
    isRecording: Boolean,
    activeInteractionId: String?,
    isLocked: Boolean,
    gestureState: VoiceRecordingGestureState,
    glassInteractive: Boolean,
    audioPower: Float,
    onStart: (String, Boolean) -> Unit,
    onFinish: (String, VoiceRecordingFinishAction) -> Unit,
    onLockChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var interactionId by remember { mutableStateOf<String?>(null) }
    var recordingHeld by remember { mutableStateOf(false) }
    val description = stringResource(R.string.chat_voice_record_accessibility)
    Box(
        modifier.size(44.dp).semantics { contentDescription = description }.pointerInput(isRecording, isLocked) {
            awaitEachGesture {
                val down = awaitFirstDown(); val id = java.util.UUID.randomUUID().toString(); interactionId = id
                var started = false; var cancelled = false; var locked = isLocked
                val releasedBeforeHold = withTimeoutOrNull(190L) { waitForUpOrCancellation() }
                if (releasedBeforeHold == null) {
                    started = true; recordingHeld = true; HapticManager.shared.playVoiceRecordStartSound(); onStart(id, false)
                    while (true) {
                        val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = change.position.x - down.position.x; val dy = change.position.y - down.position.y
                        if (started && !locked) {
                            val lock = (-dy / VoiceRecordingBlobMetrics.lockDistance).coerceIn(0f, 1f)
                            val cancel = (-dx / VoiceRecordingBlobMetrics.cancelDistance).coerceIn(0f, 1f)
                            gestureState.lockProgress = lock; gestureState.cancelDragOffset = minOf(0f, dx); gestureState.followX = max(-196f, dx); gestureState.followY = max(-151f, dy)
                            if (lock >= 1f && lock >= cancel) { locked = true; onLockChanged(true); HapticManager.shared.playVoiceRecordEndSound() }
                            if (cancel >= 1f) { cancelled = true; onFinish(id, VoiceRecordingFinishAction.CANCEL); break }
                        }
                        if (!change.pressed) break
                    }
                    if (started && !locked && !cancelled) onFinish(id, VoiceRecordingFinishAction.SEND)
                }
                interactionId = null; recordingHeld = false
                gestureState.cancelDragOffset = 0f; gestureState.lockProgress = if (locked) 1f else 0f; gestureState.followX = 0f; gestureState.followY = 0f
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording || recordingHeld || isLocked) VoiceRecordingReactiveAura(audioPower, Modifier)
        AttachmentIconView(AttachmentIcon.VOICE, AttachmentIconPreset.CHAT_VOICE_INPUT, tint)
    }
}
