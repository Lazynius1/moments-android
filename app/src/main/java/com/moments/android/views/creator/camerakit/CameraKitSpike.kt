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

/**
 * Modelo neutral de una lente para el carrusel, equivalente al `Lens` de Camera Kit.
 * ≡ `CameraKitSpike.swift` (tipos Lens del SDK).
 */
data class CameraKitLens(
    val id: String,
    val name: String? = null,
    val iconUrl: String? = null,
)

/**
 * Port de contrato de `CameraKitController` (CameraKitSpike.swift).
 *
 * Snap Camera Kit Android **no está enlazado** (igual que el flag iOS
 * [SnapCameraKitConfiguration.isFeatureEnabled] = false). Conserva estado,
 * callbacks y ciclo de vida híbrido (prepareLenses / activate / deactivate)
 * para cablearlo cuando haya SDK + credenciales, sin fingir que las lentes
 * se aplican.
 */
class CameraKitController {
    var lenses by mutableStateOf<List<CameraKitLens>>(emptyList())
        private set
    var selectedLensID by mutableStateOf<String?>(null)
        private set
    var appliedLensName by mutableStateOf<String?>(null)
        private set
    var statusMessage by mutableStateOf("Iniciando cámara…")
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

    /** Hooks Fase 4 (subida real). ≡ `onCapturedPhoto` / `onCapturedVideo` Swift. */
    var onCapturedPhoto: ((Bitmap) -> Unit)? = null
    var onCapturedVideo: ((Uri) -> Unit)? = null

    private var lensSelectionRequestID: Long = 0L

    fun start() {
        prepareLenses()
        activateCamera()
    }

    /** Híbrido: solo carga la lista de lentes (SIN encender cámara CK). */
    fun prepareLenses() {
        if (!SnapCameraKitConfiguration.isFeatureEnabled) return
        if (!SnapCameraKitConfiguration.isConfigured) {
            statusMessage = "Faltan credenciales Snap (Secrets.xcconfig)"
            return
        }
        if (SnapCameraKitConfiguration.defaultLensGroupID == null) {
            statusMessage = "Lens Group ID vacío"
            return
        }
        // SDK Android no enlazado: stub honesto (iOS aquí crea Session + observer).
        statusMessage = "Snap Camera Kit Android SDK no enlazado"
    }

    /**
     * Híbrido: enciende la cámara CK al elegir una lente.
     * Sin SDK, solo marca activo y aplica selección de contrato.
     */
    fun activateCamera(applyingLens: CameraKitLens? = null) {
        if (!SnapCameraKitConfiguration.isFeatureEnabled) return
        if (isCameraActive) {
            if (applyingLens != null) selectLens(applyingLens)
            return
        }
        isCameraActive = true
        if (applyingLens != null) selectLens(applyingLens)
    }

    /** Híbrido: apaga CK al volver a “sin filtro”. */
    fun deactivateCamera() {
        if (!isCameraActive) return
        isCameraActive = false
        isRecording = false
    }

    fun stop() {
        isCameraActive = false
        isRecording = false
        selectedLensID = null
        appliedLensName = null
        lenses = emptyList()
    }

    fun setCameraPosition(position: Int) {
        if (cameraLensFacing == position) return
        cameraLensFacing = position
    }

    fun setZoom(factor: Float) {
        zoomFactor = factor.coerceIn(1f, 5f)
    }

    /** ≡ `updateViewport(forCanvasSize:)` — crop 9:16 lo aplica el host CameraX. */
    fun updateViewportForCanvasSize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
    }

    fun selectLens(lens: CameraKitLens?) {
        val requestID = ++lensSelectionRequestID
        if (lens == null) {
            selectedLensID = null
            appliedLensName = null
            statusMessage = "Sin filtro"
            return
        }
        if (!SnapCameraKitConfiguration.isFeatureEnabled || !SnapCameraKitConfiguration.isConfigured) {
            statusMessage = "No se pudo aplicar la lente"
            return
        }
        // SDK ausente: no mutamos selectedLensID (no hay apply real).
        if (lensSelectionRequestID != requestID) return
        statusMessage = "Snap Camera Kit Android SDK no enlazado"
    }

    /** ≡ `capturePhoto()` — sin SDK no hay PhotoCaptureOutput. */
    fun capturePhoto() {
        statusMessage = "Error foto: SDK no enlazado"
    }

    /** Entrada CameraX real, equivalente al callback de PhotoCaptureOutput. */
    fun receiveCapturedPhoto(image: Bitmap) {
        capturedImage = image
        onCapturedPhoto?.invoke(image)
    }

    fun startRecording() {
        if (!isCameraActive) return
        isRecording = true
        statusMessage = "Grabando…"
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        statusMessage = "Vídeo en proceso…"
    }

    /** Entrada CameraX real ≡ finishWriting completed. */
    fun receiveCapturedVideo(uri: Uri) {
        isRecording = false
        capturedVideoUri = uri
        statusMessage = "Vídeo guardado"
        onCapturedVideo?.invoke(uri)
    }

    /** ≡ LensRepositoryGroupObserver.didUpdateLenses. */
    fun updateLenses(updatedLenses: List<CameraKitLens>) {
        lenses = updatedLenses
        statusMessage = if (updatedLenses.isEmpty()) {
            "El grupo no tiene lentes."
        } else {
            "Elige una lente"
        }
    }

    fun reportLensesFailure(message: String?) {
        statusMessage = "Error lentes: ${message ?: "desconocido"}"
    }

    /** ≡ ErrorHandler.handleError. */
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
