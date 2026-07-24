package com.moments.android.views.messaging.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.creator.creatoruikit.CameraPreviewView
import com.moments.android.views.feed.video.FeedVideoPage
import kotlinx.coroutines.delay
import java.io.File

private const val MOMENTS_CAPTURE_RATIO = 9f / 16f

enum class CameraPickerCaptureMode { PHOTO, VIDEO }
enum class CameraPickerMediaType { IMAGE, VIDEO }
enum class CameraPickerPosition { BACK, FRONT }
enum class CameraPickerFlashMode { OFF, ON, AUTO }

data class CapturedMediaPreview(
    val uri: Uri,
    val mediaType: CameraPickerMediaType,
    val isEphemeral: Boolean,
    val deleteOnDiscard: Boolean,
)

/** Port CameraX de `EnhancedCameraPickerView`. */
@Composable
fun EnhancedCameraPickerView(
    onMediaCaptured: (ByteArray, CameraPickerMediaType, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ephemeral by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(CameraPickerCaptureMode.PHOTO) }
    var position by remember { mutableStateOf(CameraPickerPosition.BACK) }
    var flash by remember { mutableStateOf(CameraPickerFlashMode.OFF) }
    var recording by remember { mutableStateOf(false) }
    var photoToken by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<CapturedMediaPreview?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) preview = CapturedMediaPreview(uri, detectMediaType(context, uri), ephemeral, deleteOnDiscard = false)
    }

    fun discard() {
        preview?.takeIf { it.deleteOnDiscard && it.uri.scheme == "file" }?.uri?.path?.let(::File)?.delete()
        preview = null
    }
    fun send() {
        val captured = preview ?: return
        runCatching { context.contentResolver.openInputStream(captured.uri)?.use { it.readBytes() } }.getOrNull()?.let { bytes ->
            onMediaCaptured(bytes, captured.mediaType, captured.isEphemeral)
            discard()
            onDismiss()
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF0B1215))) {
        if (preview != null) {
            CameraMediaPreviewOverlay(preview = preview!!, onClose = ::discard, onRetake = ::discard, onSend = ::send)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val captureWidth = minOf(maxWidth, maxHeight * MOMENTS_CAPTURE_RATIO)
                val captureHeight = captureWidth / MOMENTS_CAPTURE_RATIO
                val captureModifier = Modifier.width(captureWidth).height(captureHeight).align(Alignment.Center)
                CameraPreviewView(
                    cameraPosition = if (position == CameraPickerPosition.BACK) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT,
                    flashMode = when (flash) { CameraPickerFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF; CameraPickerFlashMode.ON -> ImageCapture.FLASH_MODE_ON; CameraPickerFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO },
                    isRecording = recording,
                    zoomLevel = 1f,
                    capturePhotoToken = photoToken,
                    captureAudio = true,
                    prefersMaximumCaptureQuality = false,
                    onRecordingStateChange = { recording = it },
                    onImageCaptured = { uri -> preview = CapturedMediaPreview(uri, CameraPickerMediaType.IMAGE, ephemeral, deleteOnDiscard = true) },
                    onVideoCaptured = { uri -> preview = CapturedMediaPreview(uri, CameraPickerMediaType.VIDEO, ephemeral, deleteOnDiscard = true) },
                    modifier = captureModifier.clip(RoundedCornerShape(28.dp)),
                )
                CameraPickerChrome(
                    mode = mode,
                    ephemeral = ephemeral,
                    recording = recording,
                    onDismiss = onDismiss,
                    onToggleEphemeral = { ephemeral = !ephemeral },
                    onToggleMode = { mode = if (mode == CameraPickerCaptureMode.PHOTO) CameraPickerCaptureMode.VIDEO else CameraPickerCaptureMode.PHOTO },
                    onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onSwitch = { position = if (position == CameraPickerPosition.BACK) CameraPickerPosition.FRONT else CameraPickerPosition.BACK },
                    onCapture = { if (mode == CameraPickerCaptureMode.PHOTO) photoToken++ else recording = !recording },
                    modifier = captureModifier,
                )
            }
        }
    }
}

@Composable
private fun CameraPickerChrome(
    mode: CameraPickerCaptureMode, ephemeral: Boolean, recording: Boolean,
    onDismiss: () -> Unit, onToggleEphemeral: () -> Unit, onToggleMode: () -> Unit,
    onGallery: () -> Unit, onSwitch: () -> Unit, onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CameraCircleButton(Icons.Filled.Close, stringResource(R.string.camera_action_close), onDismiss)
            CameraPill(stringResource(if (ephemeral) R.string.camera_mode_ephemeral else R.string.camera_mode_normal), ephemeral, onToggleEphemeral)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (recording) RecordingTime()
            CameraShutter(mode, ephemeral, recording, onCapture)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CameraCircleButton(Icons.Filled.Image, stringResource(R.string.camera_action_gallery), onGallery)
                CameraModeSelector(mode, onToggleMode)
                CameraCircleButton(Icons.Filled.FlipCameraAndroid, stringResource(R.string.camera_action_switch), onSwitch)
            }
        }
    }
}

@Composable private fun CameraCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, click: () -> Unit) =
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.15f)).clickable(onClick = click).semantics { contentDescription = description }, contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }

@Composable private fun CameraPill(label: String, highlighted: Boolean, click: () -> Unit) =
    Box(Modifier.height(42.dp).clip(RoundedCornerShape(24.dp)).background(if (highlighted) Color(0xFFFFCC33) else Color.White.copy(.15f)).clickable(onClick = click).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(label, color = if (highlighted) Color.Black else Color.White) }

@Composable private fun CameraModeSelector(mode: CameraPickerCaptureMode, onToggle: () -> Unit) =
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(.28f)).clickable(onClick = onToggle).padding(3.dp)) {
        CameraModeLabel(stringResource(R.string.camera_capture_photo), mode == CameraPickerCaptureMode.PHOTO, Modifier.weight(1f))
        CameraModeLabel(stringResource(R.string.camera_capture_video), mode == CameraPickerCaptureMode.VIDEO, Modifier.weight(1f))
    }

@Composable private fun CameraModeLabel(label: String, selected: Boolean, modifier: Modifier) =
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Color.White.copy(.15f) else Color.Transparent).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) Color.White else Color.White.copy(.58f)) }

@Composable private fun CameraShutter(mode: CameraPickerCaptureMode, ephemeral: Boolean, recording: Boolean, capture: () -> Unit) {
    var photoFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(photoFeedback) { if (photoFeedback) { delay(100); photoFeedback = false } }
    val scale by animateFloatAsState(if (recording || photoFeedback) .8f else 1f, label = "cameraShutter")
    val captureDescription = stringResource(R.string.camera_action_capture)
    Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White.copy(.17f)).clickable { if (mode == CameraPickerCaptureMode.PHOTO) photoFeedback = true; capture() }.semantics { contentDescription = captureDescription }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (mode == CameraPickerCaptureMode.PHOTO) 62.dp else 54.dp).scale(scale).clip(if (recording) RoundedCornerShape(11.dp) else CircleShape).background(if (recording || mode == CameraPickerCaptureMode.VIDEO) Color.Red else if (ephemeral) Color(0xFFFFCC33) else Color.White))
    }
}

@Composable private fun RecordingTime() { var seconds by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }; Text(stringResource(R.string.camera_recording_time, seconds / 60, seconds % 60), color = Color.White) }

@Composable private fun CameraMediaPreviewOverlay(preview: CapturedMediaPreview, onClose: () -> Unit, onRetake: () -> Unit, onSend: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B1215))) {
        val captureWidth = minOf(maxWidth, maxHeight * MOMENTS_CAPTURE_RATIO)
        val captureHeight = captureWidth / MOMENTS_CAPTURE_RATIO
        val captureModifier = Modifier.width(captureWidth).height(captureHeight).align(Alignment.Center).clip(RoundedCornerShape(28.dp))
        if (preview.mediaType == CameraPickerMediaType.IMAGE) AsyncImage(preview.uri, null, captureModifier, contentScale = ContentScale.Fit)
        else CameraPreviewVideoPlayer(preview.uri, captureModifier)
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            CameraCircleButton(Icons.Filled.Close, stringResource(R.string.camera_action_close), onClose)
            Spacer(Modifier.weight(1f))
            if (preview.isEphemeral) CameraPill(stringResource(R.string.camera_preview_ephemeral), true) {}
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CameraPill(stringResource(R.string.camera_preview_retake), false, onRetake)
                CameraPill(stringResource(R.string.camera_preview_send), preview.isEphemeral, onSend)
            }
        }
    }
}

@Composable private fun CameraPreviewVideoPlayer(uri: Uri, modifier: Modifier) =
    FeedVideoPage(
        url = uri.toString(),
        thumbnailUrl = null,
        consumerId = "camera-preview-${uri}",
        modifier = modifier,
        allowsPlayback = true,
        allowsPauseInteraction = true,
        showMute = false,
    )

private fun detectMediaType(context: android.content.Context, uri: Uri): CameraPickerMediaType =
    if (context.contentResolver.getType(uri)?.startsWith("video/") == true) CameraPickerMediaType.VIDEO else CameraPickerMediaType.IMAGE
