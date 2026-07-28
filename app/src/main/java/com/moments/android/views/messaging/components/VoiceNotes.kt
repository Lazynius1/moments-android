package com.moments.android.views.messaging.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.PowerManager
import com.moments.android.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.moments.android.services.cache.PersistentAudioCache
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.AdaptiveColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.math.abs
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
    const val bubbleWidthFraction = 0.75f
    const val trailingGapMinLength = 10f
    const val timeLabelWidth = 36f
    const val speedControlWidth = 34f

    fun bubbleWidth(screenWidthDp: Float): Float = screenWidthDp * bubbleWidthFraction

    fun availableWaveformWidth(bubbleWidth: Float, includesSpeedControl: Boolean): Float {
        val trailing = timeLabelWidth + if (includesSpeedControl) outerSpacing + speedControlWidth else 0f
        return max(
            96f,
            bubbleWidth - horizontalPadding * 2 - playButtonSize - outerSpacing - waveformLeadingInset - trailing - trailingGapMinLength,
        )
    }

    fun waveformBarCount(trackWidth: Float): Int =
        (trackWidth / (barWidth + barSpacing)).toInt().coerceIn(24, 50)

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
    private val _audioPower = MutableStateFlow(0f)
    val audioPower: StateFlow<Float> = _audioPower.asStateFlow()

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
        meterJob?.cancel(); meterJob = null; _audioPower.value = 0f
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
                levels += level
                _audioPower.value = level
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
fun VisualWaveformView(
    levels: List<Float>,
    color: Color,
    activeColor: Color,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(VoiceMessageLayout.waveformHeight.dp)) {
        if (levels.isEmpty()) return@Canvas
        val barW = VoiceMessageLayout.barWidth.dp.toPx()
        val gap = VoiceMessageLayout.barSpacing.dp.toPx()
        val step = barW + gap
        levels.forEachIndexed { index, level ->
            val height = max(6f, level.coerceIn(0f, 1f) * size.height)
            val x = index * step + barW / 2f
            drawLine(
                color = if (index.toFloat() / levels.size <= progress) activeColor else color,
                start = androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2f),
                end = androidx.compose.ui.geometry.Offset(x, (size.height + height) / 2f),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun LiveWaveformView(audioPower: Float, color: Color, modifier: Modifier = Modifier) {
    var levels by remember { mutableStateOf(List(20) { 0.1f }) }
    LaunchedEffect(audioPower) {
        levels = levels.drop(1) + audioPower
    }
    VisualWaveformView(levels, color, color, 1f, modifier)
}

/**
 * Port de `SimpleProximityManager`.
 * Sensor TYPE_PROXIMITY + wake lock de pantalla (≡ UIDevice proximity monitoring).
 */
class SimpleProximityManager(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var monitoring = false

    var isNearEar by mutableStateOf(false)
        private set

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val sensor = proximitySensor ?: return
            val distance = event?.values?.firstOrNull() ?: return
            // Cerca = valor bajo (típicamente 0); lejos = maximumRange.
            isNearEar = distance < sensor.maximumRange
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun startMonitoring() {
        if (monitoring) return
        val sensor = proximitySensor ?: return
        monitoring = true
        isNearEar = false
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        // ≡ iOS isProximityMonitoringEnabled: apaga pantalla al acercar.
        if (proximityWakeLock == null) {
            @Suppress("DEPRECATION")
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "moments:voice_proximity",
            ).also { lock ->
                if (!lock.isHeld) lock.acquire(10 * 60 * 1000L)
            }
        }
    }

    fun stopMonitoring() {
        if (!monitoring) return
        monitoring = false
        sensorManager.unregisterListener(listener)
        isNearEar = false
        proximityWakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        proximityWakeLock = null
    }
}

/** Aplica ruta altavoz / auricular durante reproducción de voice note. */
private fun applyVoicePlaybackRoute(context: Context, player: ExoPlayer, toEarpiece: Boolean) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (toEarpiece) {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_VOICE_COMMUNICATION)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
    } else {
        am.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }
}

private fun restoreVoicePlaybackAudio(context: Context) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    am.mode = AudioManager.MODE_NORMAL
    @Suppress("DEPRECATION")
    am.isSpeakerphoneOn = false
}

/**
 * Port de `GlassmorphicAudioMessage`.
 */
@Composable
fun GlassmorphicAudioMessage(
    messageId: String,
    audioUrl: String?,
    duration: Double,
    waveformSamples: List<Float>?,
    isCurrentUser: Boolean,
    isSending: Boolean,
    progress: Double?,
    groupPosition: ChatMessageGroupPosition = ChatMessageGroupPosition.SINGLE,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = AdaptiveColors(dark)
    val outgoingFill = LocalChatOutgoingBubbleColor.current
    val screenW = LocalConfiguration.current.screenWidthDp.toFloat()
    val bubbleW = VoiceMessageLayout.bubbleWidth(screenW)
    val showsSpeedControl = !isSending && duration >= 8
    val trackWidth = VoiceMessageLayout.waveformTrackWidth(bubbleW, showsSpeedControl)
    val barCount = VoiceMessageLayout.waveformBarCount(
        VoiceMessageLayout.availableWaveformWidth(bubbleW, showsSpeedControl),
    )

    val contentColor = if (isCurrentUser) Color.White else colors.messageTextColor
    val waveformInactive = if (isCurrentUser) {
        contentColor.copy(alpha = if (dark) 0.22f else 0.28f)
    } else {
        colors.primary.copy(alpha = if (dark) 0.28f else 0.22f)
    }
    val durationLabelColor = if (isCurrentUser) contentColor.copy(0.9f) else colors.timestampColor
    val bubbleStroke = if (isCurrentUser) contentColor.copy(0.12f) else colors.messageBubbleStroke
    val shape = chatBubbleShape(
        side = if (isCurrentUser) ChatBubbleSide.TRAILING else ChatBubbleSide.LEADING,
        position = groupPosition,
        cornerRadius = 18.dp,
        joinedRadius = 6.dp,
    )

    val player = remember { ExoPlayer.Builder(context).build() }
    val proximityManager = remember { SimpleProximityManager(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableFloatStateOf(0f) }
    var playbackRate by remember { mutableFloatStateOf(1f) }
    var isCheckingAvailability by remember { mutableStateOf(true) }
    var isAudioAvailable by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var playbackFilePath by remember { mutableStateOf<String?>(null) }

    val waveformLevels = remember(waveformSamples, audioUrl, messageId, barCount) {
        val seed = audioUrl ?: messageId
        if (!waveformSamples.isNullOrEmpty()) {
            ChatVoiceWaveformSamples.resampled(waveformSamples, barCount)
        } else {
            ChatVoiceWaveformGenerator.levels(seed, barCount)
        }
    }

    // ≡ iOS checkAudioAvailability + PersistentAudioCache
    LaunchedEffect(audioUrl, isSending) {
        if (isSending) {
            isAudioAvailable = true
            isCheckingAvailability = false
            playbackFilePath = audioUrl?.takeIf { it.isNotBlank() }
            return@LaunchedEffect
        }
        if (audioUrl.isNullOrBlank()) {
            isAudioAvailable = false
            isCheckingAvailability = false
            playbackFilePath = null
            return@LaunchedEffect
        }
        isCheckingAvailability = true
        val resolved = withContext(Dispatchers.IO) {
            resolveVoicePlaybackPath(audioUrl)
        }
        playbackFilePath = resolved
        isAudioAvailable = resolved != null
        isCheckingAvailability = false
    }

    LaunchedEffect(playbackFilePath) {
        val path = playbackFilePath
        player.stop()
        player.clearMediaItems()
        if (path.isNullOrBlank()) return@LaunchedEffect
        val uri = when {
            path.startsWith("content:") || path.startsWith("http") || path.startsWith("file:") ->
                android.net.Uri.parse(path)
            else -> android.net.Uri.fromFile(File(path))
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        currentTime = 0f
        isPlaying = false
    }

    DisposableEffect(player, proximityManager) {
        onDispose {
            if (ChatAudioPlaybackCenter.shared.activeMessageId == messageId) {
                ChatAudioPlaybackCenter.shared.deactivate(messageId)
            }
            proximityManager.stopMonitoring()
            restoreVoicePlaybackAudio(context)
            player.release()
        }
    }

    // ≡ iOS onChange(of: proximityManager.isNearEar)
    LaunchedEffect(proximityManager.isNearEar, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val position = player.currentPosition
        applyVoicePlaybackRoute(context, player, toEarpiece = proximityManager.isNearEar)
        if (position > 0) player.seekTo(position)
        if (!player.isPlaying) player.play()
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTime = (player.currentPosition / 1000.0).toFloat()
            delay(50)
            if (!player.isPlaying) {
                isPlaying = false
                currentTime = 0f
                proximityManager.stopMonitoring()
                restoreVoicePlaybackAudio(context)
                ChatAudioPlaybackCenter.shared.deactivate(messageId)
            }
        }
    }

    // ≡ iOS displayedProgress / displayedTimeSeconds
    val playbackProgress = if (duration > 0) (currentTime / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val displayedProgress = scrubFraction ?: playbackProgress
    val displayedSeconds = when {
        duration <= 0 -> 0.0
        isScrubbing -> currentTime.toDouble()
        isPlaying || currentTime > 0.01f -> max(0.0, duration - currentTime)
        else -> duration
    }

    fun pausePlayback(notifyCenter: Boolean = true) {
        player.pause()
        isPlaying = false
        proximityManager.stopMonitoring()
        restoreVoicePlaybackAudio(context)
        if (notifyCenter) ChatAudioPlaybackCenter.shared.deactivate(messageId)
    }

    fun startPlayback() {
        if (!isAudioAvailable || playbackFilePath.isNullOrBlank()) return
        ChatAudioPlaybackCenter.shared.activate(messageId) {
            pausePlayback(notifyCenter = false)
        }
        // ≡ iOS configurePlaybackSession(speaker: true) al arrancar
        applyVoicePlaybackRoute(context, player, toEarpiece = false)
        player.setPlaybackSpeed(playbackRate)
        if (currentTime > 0.01f && currentTime < duration) {
            player.seekTo((currentTime * 1000).toLong())
        }
        player.play()
        isPlaying = true
        proximityManager.startMonitoring()
    }

    fun togglePlayback() {
        if (!isAudioAvailable || isCheckingAvailability) return
        if (isPlaying) pausePlayback() else startPlayback()
    }

    fun seekFraction(fraction: Float) {
        if (duration <= 0) return
        val clamped = fraction.coerceIn(0f, 1f)
        scrubFraction = clamped
        currentTime = (duration * clamped).toFloat()
        player.seekTo((currentTime * 1000).toLong())
    }

    fun cycleRate() {
        playbackRate = when (playbackRate) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        player.setPlaybackSpeed(playbackRate)
    }

    val speedLabel = when (playbackRate) {
        1.5f -> "1.5×"
        2f -> "2×"
        else -> "1×"
    }

    Row(
        modifier
            .width(bubbleW.dp)
            .clip(shape)
            .background(if (isCurrentUser) outgoingFill else colors.messageBubbleBackground)
            .border(0.5.dp, bubbleStroke, shape)
            .padding(
                horizontal = VoiceMessageLayout.horizontalPadding.dp,
                vertical = VoiceMessageLayout.verticalPadding.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoiceMessageLayout.outerSpacing.dp),
    ) {
        Box(
            Modifier
                .size(VoiceMessageLayout.playButtonSize.dp)
                .clickable(
                    enabled = isAudioAvailable && !isCheckingAvailability,
                    onClick = { togglePlayback() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isCheckingAvailability -> CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
                else -> Icon(
                    imageVector = when {
                        !isAudioAvailable -> Icons.Default.Error
                        isPlaying -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = stringResource(
                        if (isPlaying) R.string.chat_voice_pause else R.string.chat_voice_play,
                    ),
                    tint = contentColor,
                    modifier = Modifier.size(VoiceMessageLayout.playIconSize.dp),
                )
            }
            if (isSending && progress != null) {
                MediaProgressRing(progress, 34.dp, 2.dp)
            }
        }

        when {
            isCheckingAvailability -> {
                Column(Modifier.padding(start = VoiceMessageLayout.waveformLeadingInset.dp)) {
                    VisualWaveformView(
                        levels = List(barCount) { 0.35f },
                        color = waveformInactive,
                        activeColor = waveformInactive,
                        progress = 0f,
                        modifier = Modifier.width(trackWidth.dp),
                    )
                    Text(
                        stringResource(R.string.chat_loading),
                        color = durationLabelColor,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            isAudioAvailable -> {
                // ≡ iOS scrubbableWaveform + ChatHorizontalPanGesture(.both)
                VisualWaveformView(
                    levels = waveformLevels,
                    color = waveformInactive,
                    activeColor = contentColor,
                    progress = displayedProgress,
                    modifier = Modifier
                        .padding(start = VoiceMessageLayout.waveformLeadingInset.dp)
                        .width(trackWidth.dp)
                        .pointerInput(duration, trackWidth) {
                            detectVoiceWaveformScrub(
                                onBegan = {
                                    if (!isScrubbing) {
                                        isScrubbing = true
                                        wasPlayingBeforeScrub = isPlaying
                                        if (isPlaying) pausePlayback()
                                        HapticManager.shared.lightImpact()
                                    }
                                },
                                onFraction = { seekFraction(it) },
                                onEnded = {
                                    isScrubbing = false
                                    scrubFraction = null
                                    if (wasPlayingBeforeScrub) startPlayback()
                                },
                            )
                        },
                )
                Text(
                    formatVoiceDuration(displayedSeconds),
                    color = durationLabelColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = VoiceMessageLayout.trailingGapMinLength.dp),
                )
                if (showsSpeedControl) {
                    Text(
                        speedLabel,
                        color = contentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(contentColor.copy(alpha = if (dark) 0.15f else 0.12f))
                            .clickable { cycleRate() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFFF9500), modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.chat_audio_unavailable),
                        color = durationLabelColor,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * ≡ iOS `ChatHorizontalPanGesture(.both)` sobre waveform:
 * falla si el gesto es vertical (no roba scroll); scrub por posición X absoluta.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectVoiceWaveformScrub(
    onBegan: () -> Unit,
    onFraction: (Float) -> Unit,
    onEnded: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var validated = false
        var failed = false
        var began = false

        while (!failed) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) break
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            val horizontal = abs(totalX)
            val vertical = abs(totalY)

            if (!validated) {
                if (vertical > 2f && vertical > horizontal) {
                    failed = true
                    break
                }
                if (horizontal > 2f && horizontal > vertical * 1.2f) {
                    validated = true
                }
            }
            if (validated) {
                if (!began) {
                    began = true
                    onBegan()
                }
                change.consume()
                onFraction((change.position.x / size.width).coerceIn(0f, 1f))
            }
        }
        if (began && !failed) onEnded()
    }
}

/** Resuelve URL remota/local a path reproducible (≡ PersistentAudioCache + file URL). */
private suspend fun resolveVoicePlaybackPath(audioUrl: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = android.net.Uri.parse(audioUrl)
        when (uri.scheme) {
            null, "file" -> {
                val path = uri.path ?: audioUrl
                path.takeIf { File(it).exists() }
            }
            "content" -> audioUrl
            "http", "https" -> {
                PersistentAudioCache.cachedURL(audioUrl)?.absolutePath
                    ?: PersistentAudioCache.localURL(URL(audioUrl)).absolutePath
            }
            else -> audioUrl
        }
    }.getOrNull()
}

private fun formatVoiceDuration(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

