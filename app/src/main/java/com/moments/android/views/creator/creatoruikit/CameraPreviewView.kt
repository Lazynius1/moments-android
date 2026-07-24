package com.moments.android.views.creator.creatoruikit

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Cámara reutilizable para Creator y Chat, contraparte CameraX de `CameraPreviewView.swift`.
 * Center Stage no existe como API Android; el parámetro se conserva para no romper el contrato.
 */
@Composable
fun CameraPreviewView(
    cameraPosition: Int,
    flashMode: Int,
    isRecording: Boolean,
    zoomLevel: Float,
    capturePhotoToken: Int,
    captureAudio: Boolean,
    prefersMaximumCaptureQuality: Boolean,
    enablesCenterStageControls: Boolean = false,
    targetRotation: Int = Surface.ROTATION_0,
    onRecordingStateChange: (Boolean) -> Unit,
    onImageCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onCaptureError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember(prefersMaximumCaptureQuality) {
        ImageCapture.Builder()
            .setCaptureMode(
                if (prefersMaximumCaptureQuality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
            )
            .setTargetRotation(targetRotation)
            .build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD)),
            )
            .build()
        VideoCapture.withOutput(recorder)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    LaunchedEffect(previewView, cameraPosition) {
        val view = previewView ?: return@LaunchedEffect
        val cameraProvider = suspendCoroutine<ProcessCameraProvider> { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ continuation.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        provider = cameraProvider
        val preview = Preview.Builder().setTargetRotation(targetRotation).build().also {
            it.surfaceProvider = view.surfaceProvider
        }
        boundCamera = runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(cameraPosition).build(),
                preview,
                imageCapture,
                videoCapture,
            )
        }.getOrElse {
            onCaptureError()
            null
        }
    }

    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }
    LaunchedEffect(zoomLevel, boundCamera) {
        val max = minOf(5f, boundCamera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 5f)
        boundCamera?.cameraControl?.setZoomRatio(zoomLevel.coerceIn(1f, max))
    }

    LaunchedEffect(capturePhotoToken) {
        if (capturePhotoToken == 0) return@LaunchedEffect
        val output = File(captureDirectory(context), "creator_photo_${UUID.randomUUID()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    ContextCompat.getMainExecutor(context).execute { onImageCaptured(Uri.fromFile(output)) }
                }
                override fun onError(exception: ImageCaptureException) {
                    ContextCompat.getMainExecutor(context).execute(onCaptureError)
                }
            },
        )
    }

    LaunchedEffect(isRecording, captureAudio) {
        if (isRecording) {
            if (activeRecording != null) return@LaunchedEffect
            val output = File(captureDirectory(context), "creator_video_${UUID.randomUUID()}.mp4")
            val pending = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(output).build())
                .let { if (captureAudio) it.withAudioEnabled() else it }
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> onRecordingStateChange(true)
                        is VideoRecordEvent.Finalize -> {
                            activeRecording = null
                            onRecordingStateChange(false)
                            if (event.hasError()) onCaptureError() else onVideoCaptured(Uri.fromFile(output))
                        }
                    }
                }
            activeRecording = pending
        } else {
            activeRecording?.stop()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }.also { previewView = it }
        },
        modifier = modifier,
    )

    @Suppress("UNUSED_VARIABLE")
    val centerStageUnsupportedOnAndroid = enablesCenterStageControls
}

private fun captureDirectory(context: Context): File = File(context.cacheDir, "creator_captures").also { it.mkdirs() }
