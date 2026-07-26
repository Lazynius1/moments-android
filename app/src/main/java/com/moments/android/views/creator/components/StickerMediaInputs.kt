package com.moments.android.views.creator.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.creatorscreens.SelfieStickerLiveCameraView
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.VisualWaveformView
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Port de `StickerMediaInputs.swift`:
 * - `SelfieCameraView` + `ImagePicker` (cámara frontal → CameraX)
 * - `AudioStickerRecordingView`
 */

/** ≡ `SelfieCameraView` — landing + captura frontal (`ImagePicker` .camera/.front). */
@Composable
fun SelfieCameraView(
    onImageCaptured: (android.graphics.Bitmap) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var cameraOpened by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        cameraOpened = granted
    }

    if (cameraOpened && hasCameraPermission) {
        // ≡ ImagePicker(sourceType: .camera, cameraDevice: .front)
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            SelfieStickerLiveCameraView(
                onPhotoCaptured = { captured ->
                    onImageCaptured(captured)
                    onDismiss()
                },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = stringResource(R.string.common_cancel),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .clickable(onClick = onDismiss),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Gray.copy(alpha = 0.8f)),
                ),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.common_cancel),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable(onClick = onDismiss),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.sticker_selfie_title),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.sticker_selfie_tap_front),
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Box(
            Modifier
                .size(120.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFF44336))),
                    CircleShape,
                )
                .clickable {
                    if (hasCameraPermission) cameraOpened = true
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

private val audioAccent = Color(1f, 0.4f, 0.3f)

/** Port de `AudioStickerRecordingView`. */
@Composable
fun AudioStickerRecordingView(
    onAdd: (File, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val micGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.MICROPHONE) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var audioPower by remember { mutableFloatStateOf(0.1f) }
    var liveLevels by remember { mutableStateOf(List(20) { 0.1f }) }
    var waveformLevels by remember {
        mutableStateOf(List(30) { Random.nextFloat().coerceIn(0.2f, 0.8f) })
    }

    val primary = if (isSystemInDarkTheme()) Color.White else Color.Black
    val secondary = primary.copy(alpha = 0.55f)
    val recordScale by animateFloatAsState(
        targetValue = if (isRecording) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "recordScale",
    )

    fun stopPlayback() {
        player?.stop()
        player?.release()
        player = null
        isPlaying = false
        playbackProgress = 0f
    }

    fun startPlayback() {
        val file = recordingFile ?: return
        stopPlayback()
        runCatching {
            val next = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            player = next
            isPlaying = true
        }
    }

    fun stopRecording() {
        val active = recorder ?: return
        isRecording = false
        runCatching { active.stop() }
        active.release()
        recorder = null
        audioPower = 0.1f
        if (recordingFile != null) {
            startPlayback()
        }
    }

    fun startRecording() {
        stopPlayback()
        recordingFile?.delete()
        recordingFile = null
        duration = 0f
        playbackProgress = 0f
        waveformLevels = List(30) { Random.nextFloat().coerceIn(0.2f, 0.8f) }

        val target = File(context.cacheDir, "story_audio_${UUID.randomUUID()}.m4a")
        val started = runCatching {
            val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recordingFile = target
            recorder = next
            isRecording = true
        }.isSuccess
        if (!started) {
            target.delete()
            recordingFile = null
            HapticManager.shared.warning()
        }
    }

    fun discardRecording() {
        stopPlayback()
        recordingFile?.delete()
        recordingFile = null
        duration = 0f
        playbackProgress = 0f
    }

    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            micGate.requestAccess(context) { startRecording() }
        }
    }

    fun togglePlayback() {
        if (isPlaying) stopPlayback() else startPlayback()
    }

    // Timer 0.1s + auto-stop 15s (≡ Timer iOS)
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isActive && isRecording) {
            delay(100)
            duration += 0.1f
            recorder?.let { r ->
                audioPower = (r.maxAmplitude / 32_767f).coerceIn(0f, 1f)
                liveLevels = liveLevels.drop(1) + audioPower
            }
            if (duration >= 15f) {
                stopRecording()
                break
            }
        }
    }

    // Progress de playback (~0.05s)
    LaunchedEffect(isPlaying, player) {
        val active = player ?: return@LaunchedEffect
        while (isActive && isPlaying) {
            delay(50)
            val total = active.duration.takeIf { it > 0 } ?: continue
            playbackProgress = (active.currentPosition.toFloat() / total).coerceIn(0f, 1f)
            if (!active.isPlaying) {
                stopPlayback()
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                runCatching { recorder?.stop() }
                recorder?.release()
                recorder = null
            }
            stopPlayback()
        }
    }

    val statusLabel = when {
        isRecording -> stringResource(R.string.sticker_audio_recording)
        recordingFile != null && isPlaying ->
            "▶ ${stringResource(R.string.sticker_audio_recorded)}"
        recordingFile != null -> stringResource(R.string.sticker_audio_recorded)
        else -> stringResource(R.string.sticker_audio_tap_to_record)
    }

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.sticker_audio_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 8.dp),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .momentsChromeGlass(RoundedCornerShape(20.dp), interactive = false)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isRecording -> {
                        // ≡ LiveWaveformView (levels desde audioPower del MediaRecorder)
                        VisualWaveformView(
                            levels = liveLevels,
                            color = audioAccent,
                            activeColor = audioAccent,
                            progress = 1f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                        )
                    }
                    recordingFile != null -> {
                        VisualWaveformView(
                            levels = waveformLevels,
                            color = Color.White.copy(alpha = 0.2f),
                            activeColor = audioAccent,
                            progress = playbackProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 16.dp)
                                .clickable { togglePlayback() },
                        )
                    }
                    else -> {
                        AttachmentIconView(
                            icon = AttachmentIcon.VOICE,
                            preset = AttachmentIconPreset.VOICE_STICKER_PROMPT,
                            tintColor = secondary,
                            modifier = Modifier
                                .height(44.dp)
                                .padding(vertical = 5.dp)
                                .graphicsLayer { alpha = 0.5f },
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatAudioDuration(duration),
                    color = if (isRecording) Color.Red else primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = statusLabel,
                    color = secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                if (recordingFile != null && !isRecording) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable { discardRecording() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                    }
                }

                Box(
                    Modifier
                        .size(72.dp)
                        .scale(recordScale)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { toggleRecording() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRecording) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(Color.Red, RoundedCornerShape(4.dp)),
                        )
                    } else {
                        AttachmentIconView(
                            icon = AttachmentIcon.VOICE,
                            preset = AttachmentIconPreset.VOICE_RECORDING,
                            tintColor = Color.Red,
                        )
                    }
                }

                if (recordingFile != null && !isRecording) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable { togglePlayback() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = recordingFile != null && !isRecording,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
            ) {
                Text(
                    text = stringResource(R.string.sticker_audio_add),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable {
                            val file = recordingFile ?: return@clickable
                            HapticManager.shared.mediumImpact()
                            onAdd(file, duration.toDouble())
                        }
                        .padding(vertical = 14.dp),
                )
            }
        }

        PermissionPrimerGateHost(gate = micGate)
    }
}

/** ≡ `formatDuration` → `00:SS.d`. */
private fun formatAudioDuration(time: Float): String {
    val seconds = time.toInt().coerceAtLeast(0)
    val tenths = ((time - seconds) * 10).toInt().coerceIn(0, 9)
    return "00:%02d.%d".format(seconds, tenths)
}
