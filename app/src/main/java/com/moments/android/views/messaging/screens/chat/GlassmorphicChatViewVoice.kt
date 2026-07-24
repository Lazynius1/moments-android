package com.moments.android.views.messaging.screens.chat

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.components.AudioRecordingManager
import com.moments.android.views.messaging.components.RecordedVoiceNote
import com.moments.android.views.messaging.components.VoiceRecordingComposer
import com.moments.android.views.messaging.components.VoiceRecordingDraft
import com.moments.android.views.messaging.components.VoiceRecordingFinishAction
import com.moments.android.views.messaging.components.VoiceRecordingSegment
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de `GlassmorphicChatView+Voice.swift`. */
@Stable
class GlassmorphicChatVoiceController(
    private val viewModel: EnhancedChatViewModel,
    private val onError: (Int) -> Unit = {},
    private val recorder: AudioRecordingManager = AudioRecordingManager.shared,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recordingTimer: Job? = null

    var interactionId by mutableStateOf<String?>(null)
        private set
    var draft by mutableStateOf<VoiceRecordingDraft?>(null)
        private set
    var recordingTime by mutableStateOf(0.0)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var isLocked by mutableStateOf(false)
        private set
    var isPreparingPreview by mutableStateOf(false)
        private set

    fun startVoiceRecording(activity: Activity, newInteractionId: String, startsLocked: Boolean) {
        interactionId?.takeIf { it != newInteractionId }?.let { finishVoiceRecording(it, VoiceRecordingFinishAction.CANCEL) }
        draft = null
        recordingTime = 0.0
        beginVoiceRecordingSegment(activity, newInteractionId, startsLocked)
    }

    fun resumeVoiceRecording(activity: Activity) {
        val id = interactionId ?: return
        val saved = draft ?: return
        if (saved.normalizedTrimRange == null) {
            beginVoiceRecordingSegment(activity, id, startsLocked = true)
            return
        }
        isPreparingPreview = true
        scope.launch {
            val segment = materializedSegment(saved)
            if (interactionId != id) return@launch
            if (segment == null) {
                isPreparingPreview = false
                HapticManager.shared.error()
                return@launch
            }
            draft = VoiceRecordingDraft(segments = listOf(segment), recording = segment.recording)
            recordingTime = segment.durationSeconds
            beginVoiceRecordingSegment(activity, id, startsLocked = true)
        }
    }

    fun updateVoiceRecordingTrimRange(range: ClosedFloatingPointRange<Double>) {
        val current = draft ?: return
        if (isRecording) return
        draft = current.copy(trimRangeSeconds = range)
        recordingTime = draft?.durationSeconds ?: 0.0
    }

    private fun beginVoiceRecordingSegment(activity: Activity, id: String, startsLocked: Boolean) {
        interactionId = id
        isLocked = startsLocked
        isPreparingPreview = false
        recorder.startRecording(activity) { started ->
            if (interactionId != id) {
                if (started) recorder.stopRecording { }
                return@startRecording
            }
            if (!started) {
                clearVoiceRecordingState()
                onError(R.string.chat_error_microphone_permission)
                return@startRecording
            }
            isRecording = true
            HapticManager.shared.playVoiceRecordStartSound()
            recordingTimer?.cancel()
            recordingTimer = scope.launch {
                while (interactionId == id && isRecording) {
                    delay(100L)
                    recordingTime += .1
                    if (recordingTime >= 60.0) {
                        finishVoiceRecording(id, VoiceRecordingFinishAction.SEND)
                        break
                    }
                }
            }
        }
    }

    fun pauseVoiceRecording() {
        val id = interactionId ?: return
        if (!isRecording || !isLocked) return
        stopCurrentSegment(id) { segment ->
            if (segment == null) {
                clearVoiceRecordingState()
                return@stopCurrentSegment
            }
            val current = draft ?: VoiceRecordingDraft()
            val next = current.copy(segments = current.segments + segment)
            draft = next
            scope.launch {
                val composed = VoiceRecordingComposer.compose(next.segments)
                if (interactionId == id) {
                    draft = next.copy(recording = composed)
                    isPreparingPreview = false
                    HapticManager.shared.selection()
                }
            }
        }
    }

    fun finishVoiceRecording(id: String, action: VoiceRecordingFinishAction) {
        if (interactionId != id) return
        if (action == VoiceRecordingFinishAction.CANCEL) {
            clearVoiceRecordingState()
            recorder.stopRecording { }
            return
        }
        if (isRecording) {
            stopCurrentSegment(id) { segment ->
                if (segment == null) clearVoiceRecordingState()
                else sendVoiceRecordingSegments((draft?.segments ?: emptyList()) + segment, id)
            }
        } else draft?.let { sendVoiceRecordingDraft(it, id) }
    }

    private fun stopCurrentSegment(id: String, completion: (VoiceRecordingSegment?) -> Unit) {
        recordingTimer?.cancel(); recordingTimer = null
        isRecording = false; isLocked = false; isPreparingPreview = true
        HapticManager.shared.playVoiceRecordEndSound()
        val priorDuration = draft?.durationSeconds ?: 0.0
        val duration = (recordingTime - priorDuration).coerceAtLeast(.1)
        recorder.stopRecording { recording ->
            if (interactionId != id) return@stopRecording
            completion(recording?.let { VoiceRecordingSegment(it, duration) })
        }
    }

    private fun sendVoiceRecordingDraft(current: VoiceRecordingDraft, id: String) {
        isPreparingPreview = true
        scope.launch {
            materializedSegment(current)?.let { sendComposedVoiceRecording(it.recording, it.durationSeconds) } ?: HapticManager.shared.error()
            if (interactionId == id) clearVoiceRecordingState()
        }
    }

    private suspend fun materializedSegment(current: VoiceRecordingDraft): VoiceRecordingSegment? {
        val recording = current.recording ?: VoiceRecordingComposer.compose(current.segments) ?: return null
        return VoiceRecordingComposer.trim(recording, current.fullDuration, current.normalizedTrimRange)
    }

    private fun sendVoiceRecordingSegments(segments: List<VoiceRecordingSegment>, id: String) {
        scope.launch {
            val recording = VoiceRecordingComposer.compose(segments)
            if (interactionId != id) return@launch
            recording?.let { sendComposedVoiceRecording(it, segments.sumOf(VoiceRecordingSegment::durationSeconds)) }
            clearVoiceRecordingState()
        }
    }

    private fun sendComposedVoiceRecording(recording: RecordedVoiceNote, duration: Double) {
        if (duration < .5) {
            HapticManager.shared.error()
            onError(R.string.chat_voice_record_too_short)
            return
        }
        viewModel.sendAudioMessage(recording.data, duration, recording.waveform)
    }

    fun resetVoiceRecordingInteraction() { interactionId?.let { finishVoiceRecording(it, VoiceRecordingFinishAction.CANCEL) } ?: clearVoiceRecordingState() }

    private fun clearVoiceRecordingState() {
        interactionId = null; draft = null; isRecording = false; isLocked = false; isPreparingPreview = false
        recordingTimer?.cancel(); recordingTimer = null; recordingTime = 0.0
    }
}

/** Estado de divisor no leído y routing inicial que comparte Voice.swift en iOS. */
@Stable
class ChatUnreadDividerController(private val viewModel: EnhancedChatViewModel) {
    var dividerMessageId by mutableStateOf<String?>(null)
        private set
    var dividerCount by mutableStateOf(0)
        private set
    var initialized by mutableStateOf(false)
        private set

    fun hasUnreadIncomingMessages(): Boolean = viewModel.unreadIncomingCount > 0
    fun clear() { dividerMessageId = null; dividerCount = 0; initialized = true }
    fun refresh(isPinnedToBottom: Boolean): Int {
        val count = viewModel.unreadIncomingCount
        dividerCount = count
        if (count == 0 || isPinnedToBottom) { clear(); return 0 }
        if (dividerMessageId == null) initialize()
        return count
    }
    fun initialize() {
        if (initialized || !hasUnreadIncomingMessages()) return
        val first = viewModel.messages.value.firstOrNull { !it.isRead && it.senderId != viewModel.currentUserId } ?: return
        dividerMessageId = first.id; dividerCount = viewModel.unreadIncomingCount; initialized = true
    }
    fun shouldShowBefore(messageIds: Set<String>, canLoadMore: Boolean): Boolean {
        val id = dividerMessageId ?: return false
        if (id !in messageIds || !hasUnreadIncomingMessages()) return false
        val index = viewModel.messages.value.indexOfFirst { it.id == id }
        return index > 0 && viewModel.messages.value.take(index).any { it.isRead || it.senderId == viewModel.currentUserId } || canLoadMore
    }
}
