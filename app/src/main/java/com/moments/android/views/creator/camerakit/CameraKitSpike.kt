package com.moments.android.views.creator.camerakit

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.moments.android.services.camera.SnapCameraKitConfiguration

/** Modelo neutral de una lente para el carrusel, equivalente al `Lens` de Camera Kit. */
data class CameraKitLens(
    val id: String,
    val name: String? = null,
    val iconUrl: String? = null,
)

/**
 * Port de contrato de `CameraKitController` (CameraKitSpike.swift).
 *
 * El binario Android no incluye aún Snap Camera Kit: la cámara/captura nativa
 * sigue siendo CameraX (`StoryCameraView`). Este controller conserva el estado,
 * los callbacks y los límites de ciclo de vida para cablearlo cuando se añadan
 * dependencia y credenciales Snap reales, sin fingir que las lentes se aplican.
 */
class CameraKitController {
    var lenses by mutableStateOf<List<CameraKitLens>>(emptyList())
        private set
    var selectedLensID by mutableStateOf<String?>(null)
        private set
    var appliedLensName by mutableStateOf<String?>(null)
        private set
    var statusMessage by mutableStateOf("Starting camera…")
        private set
    var capturedImage by mutableStateOf<Bitmap?>(null)
        private set
    var capturedVideoUri by mutableStateOf<Uri?>(null)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var cameraLensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)
        private set
    var zoomFactor by mutableStateOf(1f)
        private set
    var isCameraActive by mutableStateOf(false)
        private set

    var onCapturedPhoto: ((Bitmap) -> Unit)? = null
    var onCapturedVideo: ((Uri) -> Unit)? = null

    fun start() {
        prepareLenses()
        activateCamera()
    }

    fun prepareLenses() {
        when {
            !SnapCameraKitConfiguration.isFeatureEnabled -> {
                lenses = emptyList()
                statusMessage = "AR lenses are unavailable"
            }
            !SnapCameraKitConfiguration.isConfigured -> statusMessage = "Missing Snap credentials"
            else -> statusMessage = "Snap Camera Kit Android SDK is not linked"
        }
    }

    /** CameraX gestiona el input real hasta que Camera Kit Android esté enlazado. */
    fun activateCamera(applyingLens: CameraKitLens? = null) {
        isCameraActive = true
        if (applyingLens != null) selectLens(applyingLens)
        else if (!SnapCameraKitConfiguration.isFeatureEnabled) statusMessage = "Native camera"
    }

    fun deactivateCamera() {
        isCameraActive = false
        isRecording = false
    }

    fun stop() {
        deactivateCamera()
        selectedLensID = null
        appliedLensName = null
    }

    fun setCameraPosition(position: Int) {
        cameraLensFacing = position
    }

    fun setZoom(factor: Float) {
        zoomFactor = factor.coerceIn(1f, 5f)
    }

    fun updateViewportForCanvasSize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        // CameraX PreviewView respeta FILL_CENTER; el crop 9:16 se aplica en su host.
    }

    fun selectLens(lens: CameraKitLens?) {
        if (lens == null) {
            selectedLensID = null
            appliedLensName = null
            statusMessage = "No filter"
            return
        }
        if (!SnapCameraKitConfiguration.isFeatureEnabled || !SnapCameraKitConfiguration.isConfigured) {
            statusMessage = "Lens unavailable: Snap Camera Kit is not configured"
            return
        }
        // No cambiamos `selectedLensID`: el SDK no está presente y no se puede aplicar de verdad.
        statusMessage = "Lens unavailable: Snap Camera Kit Android SDK is not linked"
    }

    /** Entrada para la captura CameraX real, equivalente al callback de PhotoCaptureOutput. */
    fun receiveCapturedPhoto(image: Bitmap) {
        capturedImage = image
        onCapturedPhoto?.invoke(image)
    }

    fun startRecording() {
        if (!isCameraActive) return
        isRecording = true
        statusMessage = "Recording…"
    }

    /** Entrada para el callback de vídeo CameraX real. */
    fun receiveCapturedVideo(uri: Uri) {
        isRecording = false
        capturedVideoUri = uri
        statusMessage = "Video saved"
        onCapturedVideo?.invoke(uri)
    }

    fun stopRecording() {
        if (isRecording) statusMessage = "Finishing video…"
        isRecording = false
    }

    /** Preparado para el observer del repositorio de lentes cuando se integre el SDK. */
    fun updateLenses(updatedLenses: List<CameraKitLens>) {
        lenses = updatedLenses
        statusMessage = if (updatedLenses.isEmpty()) "The group has no lenses" else "Choose a lens"
    }

    fun reportError(message: String) {
        statusMessage = "Camera Kit: $message"
    }
}

/** Equivalente Compose de `CameraKitPreviewRepresentable`. */
@Composable
fun CameraKitPreview(
    previewView: PreviewView,
    onViewportUpdate: ((widthPx: Int, heightPx: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = {
            previewView.apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        update = { view -> onViewportUpdate?.invoke(view.width, view.height) },
        modifier = modifier,
    )
}
