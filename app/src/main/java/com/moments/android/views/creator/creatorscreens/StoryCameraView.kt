package com.moments.android.views.creator.creatorscreens

import com.moments.android.views.creator.components.CaptureButton
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.camerakit.LensReel
import com.moments.android.views.creator.creatoruikit.StoryGalleryPicker
import com.moments.android.views.creator.creatoruikit.storyMediaFromUri
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** iOS `StoryVideoProcessingService.maxAutoSplitDuration` = 5 × 60s */
private const val MAX_STORY_RECORD_SECONDS = 5.0 * 60.0

/**
 * Port de `StoryCameraView.swift`:
 * preview CameraX, foto (tap), vídeo (long-press), flash, flip, galería, Aa → story editing.
 * Camera Kit / LensReel: chunks siguientes.
 */
@Composable
fun StoryCameraView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    onStoryStartsInTextModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val controlFg = if (isDark) Color.White else Color.Black.copy(0.82f)
    val controlStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasCameraPermission = granted[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasAudioPermission = granted[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (!hasCameraPermission) add(Manifest.permission.CAMERA)
            if (!hasAudioPermission) add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isCapturing by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableDoubleStateOf(0.0) }
    var lastGalleryThumb by remember { mutableStateOf<Uri?>(null) }
    var isGalleryPickerPresented by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomLevel by remember { mutableStateOf(1f) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        VideoCapture.withOutput(recorder)
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    LaunchedEffect(hasCameraPermission, lensFacing, previewView) {
        val view = previewView ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        val provider = suspendCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = view.surfaceProvider
        }
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        runCatching {
            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageCapture,
                videoCapture,
            )
            boundCamera?.cameraControl?.setZoomRatio(zoomLevel)
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingDuration = 0.0
            return@LaunchedEffect
        }
        while (isRecording) {
            delay(100)
            recordingDuration += 0.1
            if (recordingDuration >= MAX_STORY_RECORD_SECONDS) {
                activeRecording?.stop()
                break
            }
        }
    }

    fun goTextMode() {
        if (isRecording) return
        onStoryStartsInTextModeChange(true)
        onSelectedMediaItemsChange(emptyList())
        onCurrentFlowChange(CreatorFlow.STORY_EDITING)
    }

    fun openCaptured(media: CreatorMedia) {
        HapticManager.shared.success()
        onStoryStartsInTextModeChange(false)
        onSelectedMediaItemsChange(listOf(media))
        onCurrentFlowChange(CreatorFlow.STORY_EDITING)
    }

    fun takePhoto() {
        if (isCapturing || isRecording || !hasCameraPermission) return
        isCapturing = true
        val name = "story_${UUID.randomUUID()}.jpg"
        val dir = File(context.cacheDir, "story_captures").also { it.mkdirs() }
        val photoFile = File(dir, name)
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            output,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    val media = storyMediaFromUri(context, uri)
                    mainExecutor.execute {
                        isCapturing = false
                        if (media != null) openCaptured(media) else HapticManager.shared.warning()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainExecutor.execute {
                        isCapturing = false
                        HapticManager.shared.warning()
                    }
                }
            },
        )
    }

    fun startRecording() {
        if (isRecording || isCapturing || !hasCameraPermission) return
        if (!hasAudioPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        val dir = File(context.cacheDir, "story_captures").also { it.mkdirs() }
        val videoFile = File(dir, "story_${UUID.randomUUID()}.mp4")
        val output = FileOutputOptions.Builder(videoFile).build()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val pending = videoCapture.output
            .prepareRecording(context, output)
            .withAudioEnabled()
            .start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        HapticManager.shared.success()
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        activeRecording = null
                        if (!event.hasError()) {
                            val uri = event.outputResults.outputUri.takeIf { it != Uri.EMPTY }
                                ?: Uri.fromFile(videoFile)
                            val media = storyMediaFromUri(context, uri)
                            if (media != null) openCaptured(media) else HapticManager.shared.warning()
                        } else {
                            HapticManager.shared.warning()
                        }
                    }
                }
            }
        activeRecording = pending
    }

    fun stopRecording() {
        activeRecording?.stop()
    }

    LaunchedEffect(Unit) {
        lastGalleryThumb = latestGalleryImageUri(context)
    }

    @Suppress("UNUSED_VARIABLE")
    val keepSelected = selectedMediaItems

    Box(modifier.fillMaxSize().background(canvas)) {
        if (!hasCameraPermission) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.creator_story_camera_permission),
                    color = controlFg,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.creator_permissions_open_settings),
                    color = Color(0xFF007AFF),
                    modifier = Modifier.clickable {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                        )
                    },
                )
            }
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }.also { previewView = it }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        // Equivalente del MagnifyGesture de StoryCameraView.swift. CameraX informa
                        // del máximo real por dispositivo, manteniendo el tope visual de 5× de iOS.
                        .pointerInput(boundCamera) {
                            detectTransformGestures { _, _, zoom, _ ->
                                val maxZoom = minOf(
                                    5f,
                                    boundCamera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 5f,
                                )
                                zoomLevel = (zoomLevel * zoom).coerceIn(1f, maxZoom)
                                boundCamera?.cameraControl?.setZoomRatio(zoomLevel)
                            }
                        },
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChromeCircleButton(
                        onClick = {
                            if (isRecording) stopRecording()
                            else onCurrentFlowChange(CreatorFlow.TYPE_SELECTION)
                        },
                        stroke = controlStroke,
                    ) {
                        Icon(Icons.Filled.Close, null, tint = controlFg, modifier = Modifier.size(18.dp))
                    }
                    ChromeCircleButton(
                        onClick = {
                            if (isRecording) return@ChromeCircleButton
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        },
                        stroke = controlStroke,
                    ) {
                        Icon(
                            when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                                else -> Icons.Filled.FlashOff
                            },
                            null,
                            tint = controlFg,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(48.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .border(1.dp, controlStroke, CircleShape)
                        .clickable(enabled = !isRecording) { goTextMode() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.creator_story_text_mode),
                        color = controlFg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                }

                if (isRecording) {
                    Text(
                        formatRecordingTime(recordingDuration),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp)
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .border(1.dp, Color.White.copy(0.18f), CircleShape)
                        .clickable(enabled = !isRecording) {
                            isGalleryPickerPresented = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (lastGalleryThumb != null) {
                        AsyncImage(
                            model = lastGalleryThumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // LensReel se mantiene aunque Camera Kit esté deshabilitado: Swift también
                // conserva el carrusel con la única celda passthrough y el disparador centrado.
                LensReel(
                    lenses = emptyList(),
                    isRecording = isRecording,
                    onSelect = { /* Camera Kit Android permanece apagado hasta enlazar su SDK. */ },
                    onCapturePhoto = { takePhoto() },
                    onStartVideo = { startRecording() },
                    onStopVideo = { stopRecording() },
                    modifier = Modifier.weight(1f),
                )

                Box(
                    Modifier
                        .size(48.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .border(1.dp, controlStroke, CircleShape)
                        .clickable(enabled = !isRecording) {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            // Swift restablece el zoom al invertir cámara para no pedir una ratio
                            // inválida al sensor que acaba de activarse.
                            zoomLevel = 1f
                            boundCamera?.cameraControl?.setZoomRatio(1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Cameraswitch, null, tint = controlFg, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.common_cancel),
                color = controlFg.copy(0.55f),
                modifier = Modifier.clickable(enabled = !isRecording, onClick = onDismiss),
            )
            Spacer(Modifier.height(8.dp))
        }

        if (isGalleryPickerPresented) {
            StoryGalleryPicker(
                isPresented = true,
                onSelect = { media ->
                    isGalleryPickerPresented = false
                    onStoryStartsInTextModeChange(false)
                    onSelectedMediaItemsChange(listOf(media))
                    onCurrentFlowChange(CreatorFlow.STORY_EDITING)
                },
                onDismiss = { isGalleryPickerPresented = false },
            )
        }
    }
}

@Composable
private fun ChromeCircleButton(
    onClick: () -> Unit,
    stroke: Color,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun formatRecordingTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private fun latestGalleryImageUri(context: android.content.Context): Uri? {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sort,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
        }
    }
    return null
}
