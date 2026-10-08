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
import com.moments.android.utilities.MomentsAudioSession
import android.media.MediaRecorder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.PowerManager
import com.moments.android.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.moments.android.utilities.withMomentsAudioFocus
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.moments.android.services.cache.PersistentAudioCache
import com.moments.android.services.performance.MotionPolicy
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
    // Barras genéricas (editor, sticker de voz, compositor).
    const val waveformHeight = 30f
    const val barWidth = 3.5f
    const val barSpacing = 2.5f

    // Tarjeta del chat (≡ iOS): fila (play, onda, velocidad) centrada en vertical;
    // el tiempo ocupa la franja inferior sin descentrarla.
    const val horizontalPadding = 12f
    const val rowHeight = 24f
    const val timeLabelHeight = 13f
    const val rowVerticalInset = 19f
    const val timeBottomInset = 4f
    const val cardHeight = rowVerticalInset * 2 + rowHeight

    const val playButtonWidth = 28f
    const val playIconSize = 22f
    /** Zona táctil mínima del play y del arrastre de la onda. */
    const val minTouchTarget = 44f
    const val outerSpacing = 10f
    const val speedControlWidth = 34f

    const val cardBarWidth = 2.5f
    const val cardBarSpacing = 2f
    const val cardBarMaxHeight = 22f
    const val cardBarMinHeight = 3f
    const val progressHeadSize = 11f

    /** Inicio horizontal de la onda dentro del contenido (alinea el tiempo). */
    const val waveformLeadingOffset = playButtonWidth + outerSpacing

    /** Mismo ancho máximo que una burbuja de texto del chat (≡ iOS). */
    @Composable
    fun bubbleWidth(isOutgoing: Boolean): Float =
        ChatBubbleLayoutWidth.capped(
            ChatBubbleLayoutWidth.maxTextBubbleWidth(isOutgoing = isOutgoing),
            gutter = if (isOutgoing) 64.dp else 88.dp,
        ).value

    fun availableWaveformWidth(bubbleWidth: Float, includesSpeedControl: Boolean): Float {
        val inner = bubbleWidth - horizontalPadding * 2
        val trailing = if (includesSpeedControl) outerSpacing + speedControlWidth else 0f
        return max(80f, inner - waveformLeadingOffset - trailing)
    }

    fun waveformBarCount(trackWidth: Float): Int =
        floor((trackWidth + cardBarSpacing) / (cardBarWidth + cardBarSpacing)).toInt().coerceIn(16, 60)

    fun waveformTrackWidth(bubbleWidth: Float, includesSpeedControl: Boolean): Float {
        val count = waveformBarCount(availableWaveformWidth(bubbleWidth, includesSpeedControl))
        return count * cardBarWidth + max(count - 1, 0) * cardBarSpacing
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

    private val audioSession = MomentsAudioSession.lease { cancelRecording() }
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
        MomentsAudioSession.initialize(activity)
        if (!audioSession.activate(contentType = android.media.AudioAttributes.CONTENT_TYPE_SPEECH)) { completion(false); return }
        val started = beginRecording(activity.cacheDir)
        if (!started) audioSession.release()
        completion(started)
    }

    fun stopRecording(completion: (RecordedVoiceNote?) -> Unit) {
        meterJob?.cancel(); meterJob = null; _audioPower.value = 0f
        val activeRecorder = recorder ?: run { audioSession.release(); completion(null); return }
        recorder = null
        val file = outputFile; outputFile = null
        runCatching { activeRecorder.stop() }
        activeRecorder.reset(); activeRecorder.release()
        audioSession.release()
        val data = file?.takeIf { it.exists() && it.length() > 512L }?.readBytes()
        completion(data?.let { RecordedVoiceNote(it, ChatVoiceWaveformSamples.resampled(levels, ChatVoiceWaveformSamples.storedSampleCount)) })
        levels.clear(); file?.delete()
    }

    fun cancelRecording() = stopRecording { }

    private fun beginRecording(cacheDir: File): Boolean {
        var pending: MediaRecorder? = null
        var pendingFile: File? = null
        return runCatching {
            val file = File.createTempFile("chat_voice_", ".m4a", cacheDir)
            pendingFile = file
            val next = MediaRecorder().also { pending = it }.apply {
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
        }.getOrElse { pending?.release(); pendingFile?.delete(); false }
    }

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
    centerInWidth: Boolean = false,
) {
    Canvas(modifier.height(VoiceMessageLayout.waveformHeight.dp)) {
        if (levels.isEmpty()) return@Canvas
        val barW = VoiceMessageLayout.barWidth.dp.toPx()
        val gap = VoiceMessageLayout.barSpacing.dp.toPx()
        val step = barW + gap
        val waveformWidth = levels.size * step - gap
        val startX = if (centerInWidth) ((size.width - waveformWidth) / 2f).coerceAtLeast(0f) else 0f
        levels.forEachIndexed { index, level ->
            val height = max(6f, level.coerceIn(0f, 1f) * size.height)
            val x = startX + index * step + barW / 2f
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
            /* handleAudioFocus = */ false,
        )
    } else {
        am.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ false,
        )
    }
}

private fun restoreVoicePlaybackAudio(context: Context, messageId: String) {
    val active = ChatAudioPlaybackCenter.shared.activeMessageId
    if (active != null && active != messageId) return
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
    val bubbleW = VoiceMessageLayout.bubbleWidth(isOutgoing = isCurrentUser)
    val showsSpeedControl = !isSending && duration >= 8
    val trackWidth = VoiceMessageLayout.waveformTrackWidth(bubbleW, showsSpeedControl)
    val barCount = VoiceMessageLayout.waveformBarCount(
        VoiceMessageLayout.availableWaveformWidth(bubbleW, showsSpeedControl),
    )

    val contentColor = if (isCurrentUser) chatBubbleTextColor(outgoingFill) else colors.messageTextColor
    val waveformInactive = if (isCurrentUser) {
        contentColor.copy(alpha = if (dark) 0.22f else 0.28f)
    } else {
        colors.primary.copy(alpha = if (dark) 0.28f else 0.22f)
    }
    // Recibidas: onda reproducida y cabezal con el acento del chat (contraste ≥ 3:1);
    // play/pausa y velocidad en gris secundario y barras pendientes en gris (≡ iOS).
    val accentColor = if (isCurrentUser) contentColor else chatReceivedAccentColor(outgoingFill, dark)
    val playButtonColor = if (isCurrentUser) contentColor else colors.replyBarSecondaryText
    val durationLabelColor = if (isCurrentUser) contentColor.copy(0.9f) else colors.timestampColor
    val bubbleStroke = if (isCurrentUser) contentColor.copy(0.12f) else colors.messageBubbleStroke
    val shape = chatBubbleShape(
        side = if (isCurrentUser) ChatBubbleSide.TRAILING else ChatBubbleSide.LEADING,
        position = groupPosition,
        cornerRadius = 18.dp,
        joinedRadius = ChatTextBubbleMetrics.joinedRadius,
    )

    val player = remember { ExoPlayer.Builder(context).build().withMomentsAudioFocus(context) }
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
    // Evita reanudar tras liberar el reproductor (p. ej. arrastre cancelado al salir de pantalla).
    val isDisposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

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
            isDisposed.set(true)
            if (ChatAudioPlaybackCenter.shared.activeMessageId == messageId) {
                ChatAudioPlaybackCenter.shared.deactivate(messageId)
            }
            proximityManager.stopMonitoring()
            restoreVoicePlaybackAudio(context, messageId)
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
            // Pausa propia (play/arrastre): no tocar el progreso.
            if (!isPlaying) break
            val ended = player.playbackState == Player.STATE_ENDED
            if (ended || !player.playWhenReady) {
                isPlaying = false
                // Al terminar vuelve al inicio (el tiempo muestra la duración); en pausa externa se conserva.
                currentTime = if (ended) 0f else (player.currentPosition / 1000.0).toFloat()
                proximityManager.stopMonitoring()
                restoreVoicePlaybackAudio(context, messageId)
                ChatAudioPlaybackCenter.shared.deactivate(messageId)
            }
        }
    }

    // ≡ iOS displayedProgress / displayedTimeSeconds
    val playbackProgress = if (duration > 0) (currentTime / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val displayedProgress = scrubFraction ?: playbackProgress
    // Parado al inicio: duración total. Reproduciendo, en pausa a mitad o arrastrando: transcurrido.
    val displayedSeconds = when {
        duration <= 0 -> 0.0
        scrubFraction != null -> scrubFraction!!.toDouble() * duration
        isPlaying || currentTime > 0.01f -> min(duration, currentTime.toDouble())
        else -> duration
    }

    fun pausePlayback(notifyCenter: Boolean = true) {
        player.pause()
        isPlaying = false
        proximityManager.stopMonitoring()
        restoreVoicePlaybackAudio(context, messageId)
        if (notifyCenter) ChatAudioPlaybackCenter.shared.deactivate(messageId)
    }

    fun startPlayback() {
        if (isDisposed.get() || !isAudioAvailable || playbackFilePath.isNullOrBlank()) return
        ChatAudioPlaybackCenter.shared.activate(messageId) {
            pausePlayback(notifyCenter = false)
        }
        // ≡ iOS configurePlaybackSession(speaker: true) al arrancar
        applyVoicePlaybackRoute(context, player, toEarpiece = false)
        player.setPlaybackSpeed(playbackRate)
        // Reanuda donde quedó (incluido un seek en pausa); al final o sin progreso, desde el inicio.
        val resumeAt = if (currentTime > 0.01f && currentTime < duration - 0.05) currentTime else 0f
        currentTime = resumeAt
        player.seekTo((resumeAt * 1000).toLong())
        player.play()
        isPlaying = true
        proximityManager.startMonitoring()
    }

    fun togglePlayback() {
        if (!isAudioAvailable || isCheckingAvailability) return
        if (isPlaying) pausePlayback() else startPlayback()
    }

    // ≡ iOS seekToFraction: fija el tiempo y mueve el reproductor.
    fun seekToFraction(fraction: Float) {
        if (duration <= 0 || isDisposed.get()) return
        val clamped = fraction.coerceIn(0f, 1f)
        currentTime = (duration * clamped).toFloat()
        player.seekTo((currentTime * 1000).toLong())
    }

    // ≡ iOS beginScrub: pausa (sin reiniciar) para reanudar al soltar.
    fun beginScrub(fraction: Float) {
        if (isScrubbing) return
        isScrubbing = true
        scrubFraction = fraction
        wasPlayingBeforeScrub = isPlaying
        if (isPlaying) {
            player.pause()
            isPlaying = false
        }
        HapticManager.shared.lightImpact()
    }

    // ≡ iOS endScrub: seek en la posición soltada y reanuda si sonaba.
    fun endScrub() {
        if (!isScrubbing) return
        scrubFraction?.let { seekToFraction(it) }
        isScrubbing = false
        scrubFraction = null
        if (wasPlayingBeforeScrub && isAudioAvailable) startPlayback()
        wasPlayingBeforeScrub = false
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

    val headScale by animateFloatAsState(
        targetValue = if (isScrubbing) 1.25f else 1f,
        animationSpec = if (MotionPolicy.reduceMotion) snap<Float>() else spring<Float>(
            dampingRatio = MotionPolicy.Spring.TOGGLE_DAMPING.toFloat(),
            stiffness = voiceSpringStiffness(MotionPolicy.Spring.TOGGLE_RESPONSE),
        ),
        label = "voiceHeadScale",
    )
    val scrubLabel = stringResource(R.string.chat_audio_scrub_accessibility)
    val timeText = formatVoiceDuration(displayedSeconds)
    val durationA11y = stringResource(R.string.chat_audio_duration_accessibility, timeText)
    val rowHeight = VoiceMessageLayout.rowHeight.dp

    // ≡ iOS: fila de 24 centrada (19 arriba / 19 abajo); el tiempo va en la franja inferior.
    Box(
        modifier
            .width(bubbleW.dp)
            .clip(shape)
            .background(if (isCurrentUser) outgoingFill else colors.messageBubbleBackground)
            .border(0.5.dp, bubbleStroke, shape),
    ) {
        Row(
            Modifier
                .padding(
                    horizontal = VoiceMessageLayout.horizontalPadding.dp,
                    vertical = VoiceMessageLayout.rowVerticalInset.dp,
                )
                .fillMaxWidth()
                .height(rowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoiceMessageLayout.outerSpacing.dp),
        ) {
            // Play: hueco visual 28 × 24, zona táctil de 44 (≡ iOS).
            Box(
                Modifier
                    .overflowTouchArea(
                        visualWidth = VoiceMessageLayout.playButtonWidth.dp,
                        visualHeight = rowHeight,
                        touchSize = VoiceMessageLayout.minTouchTarget.dp,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = isAudioAvailable && !isCheckingAvailability,
                        onClick = { togglePlayback() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCheckingAvailability) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        color = playButtonColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = when {
                            !isAudioAvailable -> Icons.Default.Error
                            isPlaying -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (isPlaying) R.string.chat_voice_pause else R.string.chat_voice_play,
                        ),
                        tint = playButtonColor,
                        modifier = Modifier.size(VoiceMessageLayout.playIconSize.dp),
                    )
                }
                if (isSending && progress != null) {
                    MediaProgressRing(progress, 30.dp, 2.dp)
                }
            }

            when {
                isCheckingAvailability -> {
                    VoiceCardWaveform(
                        levels = List(barCount) { 0f },
                        inactiveColor = waveformInactive,
                        activeColor = waveformInactive,
                        progress = -1f,
                        minBarHeight = VoiceMessageLayout.cardBarMinHeight + 1f,
                        modifier = Modifier.overflowTouchArea(
                            visualWidth = trackWidth.dp,
                            visualHeight = rowHeight,
                            touchSize = VoiceMessageLayout.minTouchTarget.dp,
                            touchWidth = trackWidth.dp,
                        ),
                    )
                }
                isAudioAvailable -> {
                    // ≡ iOS scrubbableWaveform: onda + cabezal; toque salta, arrastre sigue al dedo.
                    VoiceCardWaveform(
                        levels = waveformLevels,
                        inactiveColor = waveformInactive,
                        activeColor = accentColor,
                        progress = displayedProgress,
                        headColor = accentColor,
                        headScale = headScale,
                        modifier = Modifier
                            .overflowTouchArea(
                                visualWidth = trackWidth.dp,
                                visualHeight = rowHeight,
                                touchSize = VoiceMessageLayout.minTouchTarget.dp,
                                touchWidth = trackWidth.dp,
                            )
                            .pointerInput(duration, trackWidth) {
                                detectVoiceWaveformScrub(
                                    onTap = { seekToFraction(it) },
                                    onBegan = { beginScrub(it) },
                                    onFraction = { scrubFraction = it },
                                    onEnded = { endScrub() },
                                )
                            }
                            .semantics {
                                contentDescription = scrubLabel
                                stateDescription = "${formatVoiceDuration(currentTime.toDouble())} / ${formatVoiceDuration(duration)}"
                                progressBarRangeInfo = ProgressBarRangeInfo(playbackProgress, 0f..1f)
                                if (duration > 0) {
                                    setProgress { value ->
                                        seekToFraction(value)
                                        true
                                    }
                                }
                            },
                    )
                    if (showsSpeedControl) {
                        // Hueco restante sin espaciados extra: la velocidad queda al borde derecho.
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            // Velocidad en gris neutro como el play (≡ iOS `controlColor`), ancho fijo.
                            Text(
                                speedLabel,
                                color = playButtonColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(VoiceMessageLayout.speedControlWidth.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(playButtonColor.copy(alpha = if (dark) 0.15f else 0.12f))
                                    .clickable { cycleRate() }
                                    .padding(vertical = 4.dp),
                            )
                        }
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ≡ iOS bottomLabel: abajo a la izquierda, alineado con el inicio de la onda; no desplaza la fila.
        if (isCheckingAvailability || isAudioAvailable) {
            Text(
                text = if (isCheckingAvailability) stringResource(R.string.chat_loading) else timeText,
                color = durationLabelColor,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = if (isCheckingAvailability) null else FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = (VoiceMessageLayout.horizontalPadding + VoiceMessageLayout.waveformLeadingOffset).dp,
                        bottom = VoiceMessageLayout.timeBottomInset.dp,
                    )
                    .height(VoiceMessageLayout.timeLabelHeight.dp)
                    .semantics { if (!isCheckingAvailability) contentDescription = durationA11y },
            )
        }
    }
}

/** Respuesta de muelle (s) → rigidez Compose, con masa 1. */
private fun voiceSpringStiffness(response: Double): Float {
    val omega = 2 * Math.PI / response
    return (omega * omega).toFloat()
}

/**
 * Mide el contenido a la zona táctil y ocupa en el layout solo el hueco visual (≡ iOS padding negativo):
 * la fila conserva 24 de alto y el toque desborda centrado.
 */
private fun Modifier.overflowTouchArea(
    visualWidth: Dp,
    visualHeight: Dp,
    touchSize: Dp,
    touchWidth: Dp = touchSize,
): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(touchWidth.roundToPx(), touchSize.roundToPx()))
    val w = visualWidth.roundToPx()
    val h = visualHeight.roundToPx()
    layout(w, h) {
        placeable.place((w - placeable.width) / 2, (h - placeable.height) / 2)
    }
}

/**
 * Onda de la tarjeta (≡ iOS VisualWaveformView con barras de tarjeta + cabezal):
 * barras simétricas respecto a la línea central; cabezal sobre esa línea en `progress`.
 * `progress < 0` oculta el cabezal (placeholder de carga).
 */
@Composable
private fun VoiceCardWaveform(
    levels: List<Float>,
    inactiveColor: Color,
    activeColor: Color,
    progress: Float,
    modifier: Modifier = Modifier,
    headColor: Color = activeColor,
    headScale: Float = 1f,
    minBarHeight: Float = VoiceMessageLayout.cardBarMinHeight,
) {
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val barW = VoiceMessageLayout.cardBarWidth.dp.toPx()
        val step = barW + VoiceMessageLayout.cardBarSpacing.dp.toPx()
        val maxH = VoiceMessageLayout.cardBarMaxHeight.dp.toPx()
        val minH = minBarHeight.dp.toPx()
        val centerY = size.height / 2f
        val radius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
        levels.forEachIndexed { index, level ->
            val h = max(minH, level.coerceIn(0f, 1f) * maxH)
            val isActive = progress >= 0f && index.toFloat() / levels.size <= progress
            drawRoundRect(
                color = if (isActive) activeColor else inactiveColor,
                topLeft = androidx.compose.ui.geometry.Offset(index * step, centerY - h / 2f),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = radius,
            )
        }
        if (progress >= 0f) {
            val headRadius = VoiceMessageLayout.progressHeadSize.dp.toPx() / 2f * headScale
            drawCircle(
                color = headColor,
                radius = headRadius,
                center = androidx.compose.ui.geometry.Offset(size.width * progress.coerceIn(0f, 1f), centerY),
            )
        }
    }
}

/**
 * ≡ iOS onTapGesture + `ChatHorizontalPanGesture(.both)` sobre la onda:
 * toque corto → salta; arrastre horizontal → scrub (consume solo aquí);
 * gesto vertical → se suelta (no roba scroll); pulsación larga quieta → no se consume.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectVoiceWaveformScrub(
    onTap: (Float) -> Unit,
    onBegan: (Float) -> Unit,
    onFraction: (Float) -> Unit,
    onEnded: () -> Unit,
) {
    fun fractionAt(x: Float): Float = if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else 0f

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        var totalX = 0f
        var totalY = 0f
        var validated = false
        var failed = false
        var began = false

        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp()) {
                    if (began) {
                        change.consume()
                    } else if (!failed &&
                        abs(totalX) < slop && abs(totalY) < slop &&
                        change.uptimeMillis - down.uptimeMillis < longPressMs &&
                        !change.isConsumed
                    ) {
                        change.consume()
                        onTap(fractionAt(change.position.x))
                    }
                    break
                }
                // Otro gesto (scroll, swipe para responder, menú) ya lo tomó.
                if (!began && change.isConsumed) {
                    failed = true
                    break
                }
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
                        onBegan(fractionAt(change.position.x))
                    }
                    change.consume()
                    onFraction(fractionAt(change.position.x))
                }
            }
        } finally {
            // Fin o cancelación (puntero perdido, recomposición): aplica el seek pendiente.
            if (began) onEnded()
        }
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

