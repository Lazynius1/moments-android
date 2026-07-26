package com.moments.android.views.creator.creatoruikit

import android.content.Context
import android.net.Uri
import android.util.Rational
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.abs

/**
 * Port CameraX de `CameraPreviewRepresentable` + `CameraPreviewView.swift`.
 *
 * - Preview FILL_CENTER ≡ `.resizeAspectFill`
 * - [ViewPort] en el grupo de use-cases ≡ `cropImageToVisiblePreview` (salida = lo visible)
 * - `prefersMaximumCaptureQuality` → foto MAXIMIZE_QUALITY + vídeo UHD/FHD; si no → latency + HD
 * - Center Stage: sin API Android → [onCenterStageAvailabilityChange](false) si `enablesCenterStageControls`
 * - Hardware shutter (`AVCaptureEventInteraction`): N/A en Compose; el host maneja captura
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
    centerStageEnabled: Boolean = false,
    targetRotation: Int = Surface.ROTATION_0,
    onRecordingStateChange: (Boolean) -> Unit,
    onImageCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onCaptureError: () -> Unit = {},
    onCenterStageAvailabilityChange: (Boolean) -> Unit = {},
    onCenterStageEnabledChangeFromSystem: (Boolean) -> Unit = {},
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
    val videoCapture = remember(prefersMaximumCaptureQuality) {
        val qualities = if (prefersMaximumCaptureQuality) {
            listOf(Quality.UHD, Quality.FHD, Quality.HD)
        } else {
            listOf(Quality.HD, Quality.SD)
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.fromOrderedList(qualities))
            .build()
        VideoCapture.withOutput(recorder)
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var previewSize by remember { mutableStateOf(0 to 0) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var appliedZoom by remember { mutableFloatStateOf(1f) }

    // ≡ publishCenterStageAvailability(false) — stub honesto
    LaunchedEffect(enablesCenterStageControls) {
        if (enablesCenterStageControls) {
            onCenterStageAvailabilityChange(false)
        }
    }
    @Suppress("UNUSED_PARAMETER")
    val unusedCenterStageToggle = centerStageEnabled
    @Suppress("UNUSED_PARAMETER")
    val unusedCenterStageFromSystem = onCenterStageEnabledChangeFromSystem

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    LaunchedEffect(targetRotation) {
        imageCapture.targetRotation = targetRotation
        videoCapture.targetRotation = targetRotation
    }

    LaunchedEffect(previewView, cameraPosition, prefersMaximumCaptureQuality, previewSize, targetRotation) {
        val view = previewView ?: return@LaunchedEffect
        val (w, h) = previewSize
        if (w <= 0 || h <= 0) return@LaunchedEffect
        // No reconfigurar sesión mientras graba (equiv. a no interrupir movieOutput).
        if (activeRecording != null) return@LaunchedEffect

        val cameraProvider = provider ?: suspendCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }.also { provider = it }

        val preview = Preview.Builder()
            .setTargetRotation(targetRotation)
            .build()
            .also { it.surfaceProvider = view.surfaceProvider }

        // ≡ cropImageToVisiblePreview / metadataOutputRectConverted
        val viewPort = ViewPort.Builder(
            Rational(w, h),
            targetRotation,
        ).setScaleType(ViewPort.FILL_CENTER).build()

        val useCaseGroup = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(imageCapture)
            .addUseCase(videoCapture)
            .build()

        boundCamera = runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(cameraPosition).build(),
                useCaseGroup,
            )
        }.getOrElse {
            onCaptureError()
            null
        }
        appliedZoom = 1f
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    // ≡ updateZoom: umbral abs > 0.1; clamp a min(5, maxZoom)
    LaunchedEffect(zoomLevel, boundCamera) {
        if (abs(zoomLevel - appliedZoom) <= 0.1f) return@LaunchedEffect
        val max = minOf(5f, boundCamera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 5f)
        val next = zoomLevel.coerceIn(1f, max)
        boundCamera?.cameraControl?.setZoomRatio(next)
        appliedZoom = next
    }

    LaunchedEffect(capturePhotoToken) {
        if (capturePhotoToken == 0) return@LaunchedEffect
        val output = File(captureDirectory(context), "creator_photo_${UUID.randomUUID()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    ContextCompat.getMainExecutor(context).execute {
                        onImageCaptured(result.savedUri ?: Uri.fromFile(output))
                    }
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
            val output = File(captureDirectory(context), "story_video_${System.currentTimeMillis()}.mp4")
            val pending = videoCapture.output
                .prepareRecording(context, FileOutputOptions.Builder(output).build())
                .let { if (captureAudio) it.withAudioEnabled() else it }
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> onRecordingStateChange(true)
                        is VideoRecordEvent.Finalize -> {
                            activeRecording = null
                            onRecordingStateChange(false)
                            if (event.hasError()) onCaptureError()
                            else onVideoCaptured(Uri.fromFile(output))
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
                setBackgroundColor(android.graphics.Color.BLACK)
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val w = right - left
                    val h = bottom - top
                    val oldW = oldRight - oldLeft
                    val oldH = oldBottom - oldTop
                    if (w > 0 && h > 0 && (w != oldW || h != oldH)) {
                        previewSize = w to h
                    }
                }
            }.also { previewView = it }
        },
        modifier = modifier,
    )
}

private fun captureDirectory(context: Context): File =
    File(context.cacheDir, "creator_captures").also { it.mkdirs() }
