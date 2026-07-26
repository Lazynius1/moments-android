package com.moments.android.views.creator.creatoruikit

import android.graphics.Color as AndroidColor
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Equivalente Compose de `NotificationCenter` + `"StopBackgroundCameraSession"`.
 * iOS: `NotificationCenter.default.post(name: NSNotification.Name("StopBackgroundCameraSession"), …)`
 */
object StopBackgroundCameraSession {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun post() {
        _events.tryEmit(Unit)
    }
}

/**
 * Port de `BackgroundCameraView.swift`.
 *
 * - Preview trasera CameraX ≡ `AVCaptureDevice` wide-angle back + `AVCaptureVideoPreviewLayer`
 * - `FILL_CENTER` ≡ `.resizeAspectFill`
 * - Fondo negro ≡ `controller.view.backgroundColor = .black`
 * - `isActive=false` o [StopBackgroundCameraSession.post] ≡ `Coordinator.stopSession()`
 * - `sessionPreset = .medium` no tiene 1:1 en CameraX Preview; se usa el default del use-case
 */
@Composable
fun BackgroundCameraView(
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var stoppedBySignal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        StopBackgroundCameraSession.events.collect {
            stoppedBySignal = true
            provider?.unbindAll()
        }
    }

    // Si el padre reactiva (poco habitual), permitir reanudar como sesión nueva.
    LaunchedEffect(isActive) {
        if (isActive) stoppedBySignal = false
    }

    val shouldRun = isActive && !stoppedBySignal

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            provider = null
        }
    }

    LaunchedEffect(previewView, shouldRun) {
        val view = previewView ?: return@LaunchedEffect
        val cameraProvider = provider ?: suspendCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }.also { provider = it }

        cameraProvider.unbindAll()
        if (!shouldRun) return@LaunchedEffect

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = view.surfaceProvider
        }
        runCatching {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
            )
        }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                setBackgroundColor(AndroidColor.BLACK)
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }.also { previewView = it }
        },
        modifier = modifier,
    )
}
