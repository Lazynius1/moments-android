package com.moments.android.views.creator.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.creatorscreens.SelfieStickerLiveCameraView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

/**
 * Port de `SelfieCameraView` / `ImagePicker` de
 * `Views/Creator/Components/StickerMediaInputs.swift`.
 *
 * El catálogo Swift actual crea el selfie directamente dentro del canvas, pero
 * esta pantalla también existe en el archivo fuente y mantiene su captura
 * frontal independiente para los consumidores que la presenten.
 */
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
                text = stringResource(R.string.common_close),
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
                    colors = listOf(Color.Black, Color(0xFF424242)),
                ),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.common_close),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable(onClick = onDismiss),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "🤳 " + stringResource(R.string.sticker_category_selfie),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Tap to open front camera",
            color = Color.White.copy(alpha = 0.8f),
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

/** Port de `Views/Creator/Components/StickerMediaInputs.swift` — bloque Audio. */
@Composable
fun AudioStickerRecordingView(
    onAdd: (File, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    fun stopPlayback() {
        player?.release()
        player = null
        isPlaying = false
    }
    fun stopRecording() {
        val active = recorder ?: return
        runCatching { active.stop() }
        active.release()
        recorder = null
        durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L).coerceAtMost(15_000L)
        isRecording = false
    }
    fun startRecording() {
        stopPlayback()
        recordingFile?.delete()
        val target = File(context.cacheDir, "story_audio_${UUID.randomUUID()}.m4a")
        val next = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(target.absolutePath)
            prepare()
            start()
        }
        recordingFile = target
        recorder = next
        startedAt = System.currentTimeMillis()
        durationMs = 0L
        isRecording = true
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching(::startRecording).onFailure { HapticManager.shared.warning() }
        else HapticManager.shared.warning()
    }
    fun togglePlayback() {
        if (isPlaying) return stopPlayback()
        val file = recordingFile ?: return
        val next = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { stopPlayback() }
            prepare()
            start()
        }
        player = next
        isPlaying = true
    }
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) stopRecording()
            stopPlayback()
        }
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.sticker_audio_title), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "%02d:%02d".format((if (isRecording) (System.currentTimeMillis() - startedAt) else durationMs) / 60_000, ((if (isRecording) (System.currentTimeMillis() - startedAt) else durationMs) / 1000) % 60),
            color = if (isRecording) Color.Red else Color.Unspecified,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recordingFile != null && !isRecording) {
                Icon(Icons.Filled.Delete, null, tint = Color.Red, modifier = Modifier.size(38.dp).clickable { stopPlayback(); recordingFile?.delete(); recordingFile = null; durationMs = 0L })
            }
            Box(Modifier.size(72.dp).background(Color.White.copy(alpha = 0.18f), CircleShape).clickable {
                if (isRecording) stopRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }, contentAlignment = Alignment.Center) {
                Icon(if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic, null, tint = if (isRecording) Color.Red else Color.White, modifier = Modifier.size(28.dp))
            }
            if (recordingFile != null && !isRecording) Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, modifier = Modifier.size(38.dp).clickable { togglePlayback() })
        }
        recordingFile?.takeIf { !isRecording }?.let { file ->
            Text(stringResource(R.string.sticker_audio_add), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp)).clickable { HapticManager.shared.mediumImpact(); onAdd(file, durationMs / 1000.0) }.padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
