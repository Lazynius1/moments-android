package com.moments.android.views.messaging.components

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.moments.android.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Port en curso de `Views/Messaging/Components/VoiceNotes.swift`. */
data class RecordedVoiceNote(
    val data: ByteArray,
    val waveform: List<Float>,
)

data class VoiceRecordingSegment(
    val recording: RecordedVoiceNote,
    val durationSeconds: Double,
)

data class VoiceRecordingDraft(
    val segments: List<VoiceRecordingSegment> = emptyList(),
    val recording: RecordedVoiceNote? = null,
    val trimRangeSeconds: ClosedFloatingPointRange<Double>? = null,
) {
    val fullDuration: Double get() = segments.sumOf { it.durationSeconds }
    val normalizedTrimRange: ClosedFloatingPointRange<Double>?
        get() {
            val range = trimRangeSeconds ?: return null
            if (fullDuration <= 0.0) return null
            val lower = range.start.coerceIn(0.0, fullDuration)
            val upper = range.endInclusive.coerceIn(lower, fullDuration)
            return if (upper > lower) lower..upper else null
        }
    val durationSeconds: Double get() = normalizedTrimRange?.let { it.endInclusive - it.start } ?: fullDuration
    val trimStartSeconds: Double get() = normalizedTrimRange?.start ?: 0.0
    val trimEndSeconds: Double get() = normalizedTrimRange?.endInclusive ?: fullDuration
    val waveform: List<Float>
        get() = recording?.waveform ?: ChatVoiceWaveformSamples.resampled(segments.flatMap { it.recording.waveform }, ChatVoiceWaveformSamples.storedSampleCount)
}

object ChatVoiceWaveformSamples {
    const val storedSampleCount = 48

    fun resampled(source: List<Float>, count: Int): List<Float> {
        if (count <= 0 || source.isEmpty()) return emptyList()
        return List(count) { index ->
            val lower = index * source.size / count
            val upper = max(lower + 1, (index + 1) * source.size / count)
            val values = source.subList(lower, min(upper, source.size))
            val average = values.average().toFloat()
            val peak = values.maxOrNull() ?: average
            (average * .7f + peak * .3f).coerceIn(.12f, 1f)
        }
    }

    fun cropped(source: List<Float>, fullDuration: Double, range: ClosedFloatingPointRange<Double>): List<Float> {
        if (source.isEmpty() || fullDuration <= 0.0) return emptyList()
        val lowerFraction = (range.start / fullDuration).coerceIn(0.0, 1.0)
        val upperFraction = (range.endInclusive / fullDuration).coerceIn(lowerFraction, 1.0)
        val lower = min(source.lastIndex, floor(lowerFraction * source.size).toInt())
        val upper = min(source.size, max(lower + 1, ceil(upperFraction * source.size).toInt()))
        return resampled(source.subList(lower, upper), storedSampleCount)
    }
}

object VoiceMessageLayout {
    const val playButtonSize = 38f
    const val playIconSize = 26f
    const val waveformHeight = 30f
    const val barWidth = 3.5f
    const val barSpacing = 2.5f
    const val outerSpacing = 10f
    const val waveformLeadingInset = 12f
    const val horizontalPadding = 14f
    const val verticalPadding = 15f
    const val bubbleWidthFraction = .75f
    const val trailingGapMinLength = 10f
    const val timeLabelWidth = 36f
    const val speedControlWidth = 34f

    fun availableWaveformWidth(bubbleWidth: Float, includesSpeedControl: Boolean): Float {
        val trailing = timeLabelWidth + if (includesSpeedControl) outerSpacing + speedControlWidth else 0f
        return max(96f, bubbleWidth - horizontalPadding * 2 - playButtonSize - outerSpacing - waveformLeadingInset - trailing - trailingGapMinLength)
    }
    fun waveformBarCount(trackWidth: Float): Int = (trackWidth / (barWidth + barSpacing)).toInt().coerceIn(24, 50)
    fun waveformTrackWidth(bubbleWidth: Float, includesSpeedControl: Boolean): Float {
        val count = waveformBarCount(availableWaveformWidth(bubbleWidth, includesSpeedControl))
        return count * barWidth + max(count - 1, 0) * barSpacing
    }
}

object ChatVoiceWaveformGenerator {
    fun levels(seed: String, count: Int): List<Float> {
        if (count <= 0) return emptyList()
        var hash = seed.fold(5381L) { acc, char -> (acc shl 5) + acc + char.code }
        return List(count) { index ->
            hash = hash * 1_103_515_245L + 12_345L + index
            .2f + ((hash and Long.MAX_VALUE) % 10_000).toFloat() / 10_000f * .6f
        }
    }
}

class ChatAudioPlaybackCenter private constructor() {
    var activeMessageId: String? = null
        private set
    private var stopHandler: (() -> Unit)? = null
    fun activate(messageId: String, stopOthers: () -> Unit) { if (activeMessageId != messageId) stopHandler?.invoke(); activeMessageId = messageId; stopHandler = stopOthers }
    fun deactivate(messageId: String) { if (activeMessageId == messageId) { activeMessageId = null; stopHandler = null } }
    fun stopCurrent() { stopHandler?.invoke(); activeMessageId = null; stopHandler = null }
    companion object { val shared = ChatAudioPlaybackCenter() }
}

class AudioRecordingManager private constructor() {
    var audioPower: Float = 0f
        private set
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var levels = mutableListOf<Float>()
    private var meterJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun startRecording(activity: Activity, requestCode: Int = microphoneRequestCode, completion: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
            completion(false)
            return
        }
        completion(beginRecording(activity.cacheDir))
    }

    fun stopRecording(completion: (RecordedVoiceNote?) -> Unit) {
        meterJob?.cancel(); meterJob = null; audioPower = 0f
        val activeRecorder = recorder ?: run { completion(null); return }
        recorder = null
        val file = outputFile; outputFile = null
        runCatching { activeRecorder.stop() }
        activeRecorder.reset(); activeRecorder.release()
        val data = file?.takeIf { it.exists() && it.length() > 512L }?.readBytes()
        completion(data?.let { RecordedVoiceNote(it, ChatVoiceWaveformSamples.resampled(levels, ChatVoiceWaveformSamples.storedSampleCount)) })
        levels.clear(); file?.delete()
    }

    fun cancelRecording() = stopRecording { }

    private fun beginRecording(cacheDir: File): Boolean = runCatching {
        val file = File.createTempFile("chat_voice_", ".m4a", cacheDir)
        val next = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare(); start()
        }
        recorder = next; outputFile = file; levels.clear()
        meterJob?.cancel()
        meterJob = scope.launch {
            while (recorder === next) {
                val level = normalizedPower(next.maxAmplitude)
                levels += level; audioPower = level
                delay(50)
            }
        }
        true
    }.getOrElse { false }

    private fun normalizedPower(amplitude: Int): Float = (amplitude / 32_767f).coerceIn(0f, 1f)

    fun dispose() { cancelRecording(); scope.cancel() }

    companion object {
        const val microphoneRequestCode = 9401
        val shared: AudioRecordingManager by lazy { AudioRecordingManager() }
    }
}

object VoiceRecordingComposer {
    suspend fun compose(segments: List<VoiceRecordingSegment>): RecordedVoiceNote? = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext null
        if (segments.size == 1) return@withContext segments.first().recording
        val inputFiles = segments.map { File.createTempFile("voice_segment_", ".m4a").apply { writeBytes(it.recording.data) } }
        val output = File.createTempFile("voice_composed_", ".m4a")
        try {
            muxAudio(inputFiles, output, null)
            output.takeIf { it.length() > 0L }?.readBytes()?.let { data ->
                RecordedVoiceNote(data, ChatVoiceWaveformSamples.resampled(segments.flatMap { it.recording.waveform }, ChatVoiceWaveformSamples.storedSampleCount))
            }
        } catch (_: Exception) { null } finally { inputFiles.forEach(File::delete); output.delete() }
    }

    suspend fun trim(recording: RecordedVoiceNote, fullDuration: Double, requestedRange: ClosedFloatingPointRange<Double>?): VoiceRecordingSegment? = withContext(Dispatchers.IO) {
        if (fullDuration <= 0.0) return@withContext null
        val range = requestedRange ?: return@withContext VoiceRecordingSegment(recording, fullDuration)
        val lower = range.start.coerceIn(0.0, fullDuration); val upper = range.endInclusive.coerceIn(lower, fullDuration)
        if (upper <= lower) return@withContext null
        if (lower <= .025 && upper >= fullDuration - .025) return@withContext VoiceRecordingSegment(recording, fullDuration)
        val source = File.createTempFile("voice_trim_source_", ".m4a").apply { writeBytes(recording.data) }
        val output = File.createTempFile("voice_trimmed_", ".m4a")
        try {
            muxAudio(listOf(source), output, lower..upper)
            output.takeIf { it.length() > 0L }?.readBytes()?.let { bytes ->
                VoiceRecordingSegment(RecordedVoiceNote(bytes, ChatVoiceWaveformSamples.cropped(recording.waveform, fullDuration, lower..upper)), upper - lower)
            }
        } catch (_: Exception) { null } finally { source.delete(); output.delete() }
    }

    private fun muxAudio(inputs: List<File>, output: File, clip: ClosedFloatingPointRange<Double>?) {
        var muxer: MediaMuxer? = null; var outputTrack = -1; var outputOffsetUs = 0L
        try {
            inputs.forEach { input ->
                val extractor = MediaExtractor(); extractor.setDataSource(input.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return@forEach
                val format = extractor.getTrackFormat(track)
                if (muxer == null) { muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); outputTrack = muxer!!.addTrack(format); muxer!!.start() }
                extractor.selectTrack(track)
                val info = android.media.MediaCodec.BufferInfo(); val buffer = java.nio.ByteBuffer.allocate(256 * 1024)
                val startUs = (clip?.start?.times(1_000_000)?.toLong() ?: 0L); val endUs = (clip?.endInclusive?.times(1_000_000)?.toLong() ?: Long.MAX_VALUE)
                while (true) {
                    val size = extractor.readSampleData(buffer, 0); if (size < 0) break
                    val sampleUs = extractor.sampleTime; if (sampleUs >= startUs && sampleUs <= endUs) {
                        info.set(0, size, outputOffsetUs + (sampleUs - startUs).coerceAtLeast(0L), extractor.sampleFlags)
                        muxer!!.writeSampleData(outputTrack, buffer, info)
                    }
                    if (!extractor.advance()) break
                }
                outputOffsetUs += (endUs - startUs).takeIf { it != Long.MAX_VALUE } ?: extractor.cachedDuration
                extractor.release()
            }
        } finally { runCatching { muxer?.stop() }; muxer?.release() }
    }
}

@Composable
fun VisualWaveformView(levels: List<Float>, color: Color, activeColor: Color, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.height(30.dp)) {
        if (levels.isEmpty()) return@Canvas
        val step = size.width / levels.size
        levels.forEachIndexed { index, level ->
            val height = max(6f, level.coerceIn(0f, 1f) * size.height)
            drawLine(if (index.toFloat() / levels.size <= progress) activeColor else color, androidx.compose.ui.geometry.Offset(index * step + step / 2f, (size.height - height) / 2f), androidx.compose.ui.geometry.Offset(index * step + step / 2f, (size.height + height) / 2f), 3.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun LiveWaveformView(audioPower: Float, color: Color, modifier: Modifier = Modifier) {
    val levels = remember { MutableList(20) { .1f } }
    levels.removeAt(0); levels += audioPower
    VisualWaveformView(levels, color, color, 1f, modifier)
}

@Composable
fun GlassmorphicAudioMessage(
    messageId: String,
    audioUrl: String?,
    duration: Double,
    waveformSamples: List<Float>?,
    isCurrentUser: Boolean,
    isSending: Boolean,
    progress: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textColor = if (isCurrentUser) Color.White else if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
    val inactive = textColor.copy(alpha = if (isCurrentUser) .26f else .34f)
    val player = remember(audioUrl) { ExoPlayer.Builder(context).build().apply { audioUrl?.let { setMediaItem(MediaItem.fromUri(it)); prepare() } } }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var playbackRate by remember { mutableFloatStateOf(1f) }
    val waveform = remember(waveformSamples, audioUrl) { ChatVoiceWaveformSamples.resampled(waveformSamples ?: ChatVoiceWaveformGenerator.levels(audioUrl ?: messageId, 40), 40) }
    DisposableEffect(player) { onDispose { if (ChatAudioPlaybackCenter.shared.activeMessageId == messageId) ChatAudioPlaybackCenter.shared.deactivate(messageId); player.release() } }
    LaunchedEffect(isPlaying) { while (isPlaying) { position = player.currentPosition; kotlinx.coroutines.delay(50); if (!player.isPlaying) { isPlaying = false; position = 0 } } }
    val toggle = {
        if (isPlaying) { player.pause(); isPlaying = false; ChatAudioPlaybackCenter.shared.deactivate(messageId) }
        else if (!audioUrl.isNullOrBlank()) { ChatAudioPlaybackCenter.shared.activate(messageId) { player.pause(); isPlaying = false }; player.setPlaybackSpeed(playbackRate); player.play(); isPlaying = true }
    }
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(if (isCurrentUser) Color(0xFF3F6F8F) else if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.12f) else Color.Black.copy(.06f)).padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clickable(onClick = toggle), contentAlignment = Alignment.Center) {
            if (isSending && progress != null) MediaProgressRing(progress, 34.dp, 2.dp)
            Icon(if (isPlaying) Icons.Default.Pause else if (audioUrl.isNullOrBlank()) Icons.Default.Error else Icons.Default.PlayArrow, contentDescription = stringResource(if (isPlaying) R.string.chat_voice_pause else R.string.chat_voice_play), tint = textColor)
        }
        if (audioUrl.isNullOrBlank() && !isSending) Text(stringResource(R.string.chat_audio_unavailable), color = textColor.copy(.7f), fontSize = 12.sp)
        else {
            VisualWaveformView(waveform, inactive, textColor, if (duration > 0) (position / 1000.0 / duration).toFloat().coerceIn(0f, 1f) else 0f, Modifier.width(130.dp).clickable { if (duration > 0) player.seekTo(((duration * 1000) / 2).toLong()) })
            Text(stringResource(R.string.chat_voice_duration, ((if (isPlaying) duration - position / 1000.0 else duration).coerceAtLeast(0.0).toLong() / 60), ((if (isPlaying) duration - position / 1000.0 else duration).coerceAtLeast(0.0).toLong() % 60)), color = textColor.copy(.8f), fontSize = 11.sp)
            if (!isSending && duration >= 8) Text(stringResource(R.string.chat_voice_speed, playbackRate), color = textColor, fontSize = 10.sp, modifier = Modifier.clickable { playbackRate = when (playbackRate) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }; player.setPlaybackSpeed(playbackRate) }.padding(4.dp))
        }
    }
}
