package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass

/** Port de `Views/Messaging/Components/ChatInputViews.swift`. */
enum class VoiceRecordingFloatingControlMode { LOCKING, PAUSE, PREPARING, RESUME }

@Composable
fun GlassmorphicInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isRecordingVoice: Boolean,
    isVoiceRecordingLocked: Boolean,
    recordingSeconds: Long,
    isVanishModeActive: Boolean = false,
    allowsAttachments: Boolean = true,
    onSend: () -> Unit,
    onOpenAttachments: () -> Unit,
    onStartVoiceRecording: () -> Unit,
    onFinishVoiceRecording: (send: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = com.moments.android.views.feed.AdaptiveColors(androidx.compose.foundation.isSystemInDarkTheme())
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isRecordingVoice) {
            InputCircleButton(Icons.Default.Delete, Color.Red) { onFinishVoiceRecording(false) }
        } else if (allowsAttachments) {
            ChatAttachmentPlusButton(false, onOpenAttachments)
        }
        Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).momentsChromeGlass(RoundedCornerShape(22.dp), interactive = !isVanishModeActive).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (isRecordingVoice) VoiceRecordingHeldStatus(isVoiceRecordingLocked, recordingSeconds, colors) { onFinishVoiceRecording(false) }
            else BasicTextField(value = text, onValueChange = onTextChange, textStyle = TextStyle(color = colors.primary, fontSize = 15.sp), cursorBrush = SolidColor(colors.primary), modifier = Modifier.fillMaxWidth(), decorationBox = { inner -> if (text.isEmpty()) Text(stringResource(if (isVanishModeActive) R.string.chat_input_vanish_placeholder else R.string.chat_input_placeholder), color = colors.secondary, fontSize = 15.sp); inner() })
        }
        when {
            isVoiceRecordingLocked -> InputCircleButton(Icons.Default.KeyboardArrowUp, colors.accent) { onFinishVoiceRecording(true) }
            text.isNotEmpty() -> InputCircleButton(Icons.Default.Send, colors.accent, filled = true, onClick = onSend)
            allowsAttachments -> InputCircleButton(Icons.Default.Mic, colors.mediaIconColor, onClick = onStartVoiceRecording)
        }
    }
}

@Composable
fun VoiceRecordingFloatingControl(mode: VoiceRecordingFloatingControlMode, primaryTint: Color, accentTint: Color, onPause: () -> Unit, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val interactive = mode == VoiceRecordingFloatingControlMode.PAUSE || mode == VoiceRecordingFloatingControlMode.RESUME
    val icon = when (mode) { VoiceRecordingFloatingControlMode.LOCKING -> Icons.Default.Lock; VoiceRecordingFloatingControlMode.PAUSE -> Icons.Default.Pause; VoiceRecordingFloatingControlMode.RESUME -> Icons.Default.Mic; VoiceRecordingFloatingControlMode.PREPARING -> null }
    Box(modifier.size(44.dp).clip(CircleShape).momentsChromeGlass(CircleShape, interactive = interactive).clickable(enabled = interactive) { if (mode == VoiceRecordingFloatingControlMode.PAUSE) onPause() else onResume() }, contentAlignment = Alignment.Center) {
        if (icon == null) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primaryTint) else Icon(icon, null, tint = if (mode == VoiceRecordingFloatingControlMode.RESUME) accentTint else primaryTint)
    }
}

@Composable
fun VoiceRecordingHeldStatus(isLocked: Boolean, recordingSeconds: Long, colors: com.moments.android.views.feed.AdaptiveColors, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red)); Text(formatVoiceTime(recordingSeconds), color = colors.primary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (isLocked) Text(stringResource(R.string.common_cancel), color = colors.accent, modifier = Modifier.clickable(onClick = onCancel), fontSize = 12.sp) else Text(stringResource(R.string.chat_voice_slide_to_cancel), color = colors.secondary, fontSize = 11.sp)
    }
}

@Composable
private fun InputCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, filled: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).then(if (filled) Modifier.background(tint) else Modifier.momentsChromeGlass(CircleShape, interactive = true)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (filled) Color.White else tint, modifier = Modifier.size(18.dp)) }
}

private fun formatVoiceTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun VoiceRecordingDraftPreview(
    draft: VoiceRecordingDraft?,
    fallbackDurationSeconds: Double,
    isPreparing: Boolean,
    onTrimChanged: (ClosedFloatingPointRange<Double>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = draft?.durationSeconds ?: fallbackDurationSeconds
    var range by remember(draft) { mutableStateOf((draft?.trimStartSeconds ?: 0.0)..(draft?.trimEndSeconds ?: duration)) }
    Row(modifier.fillMaxWidth().heightIn(min = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isPreparing) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
        Canvas(Modifier.weight(1f).heightIn(min = 26.dp)) {
            val values = draft?.waveform?.ifEmpty { listOf(.22f) } ?: listOf(.22f)
            val step = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { index, sample ->
                val h = size.height * sample.coerceIn(.12f, 1f)
                drawLine(Color.White.copy(.45f), androidx.compose.ui.geometry.Offset(index * step, (size.height - h) / 2), androidx.compose.ui.geometry.Offset(index * step, (size.height + h) / 2), 2.5f)
            }
        }
        Text(stringResource(R.string.chat_voice_duration, range.start.toInt() / 60, range.start.toInt() % 60), fontSize = 10.sp)
    }
}
