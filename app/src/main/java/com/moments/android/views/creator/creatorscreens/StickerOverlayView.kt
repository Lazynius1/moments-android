package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.StoryStickerDraft
import com.moments.android.views.creator.creatoruikit.creatorNormalizedUp
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Contenedor interactivo equivalente a `StickerOverlayView.swift`.
 *
 * Gestos (idiomáticos Compose, semántica iOS):
 * - Drag en **coordenadas de ventana absolutas** (≡ `DragGesture(coordinateSpace: .named("storyCanvas"))`)
 *   para que mover el `offset` del sticker no corrompa el pan local.
 * - Hit-target = tamaño natural × `max(scale, 1)` (≡ `interactiveBoundsSize`).
 * - Escala/rotación visual en el contenido interno; el área táctil no se encoge al agrandar.
 */
@Composable
fun StickerOverlayView(
    sticker: StoryStickerDraft,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    isContentEditing: Boolean,
    isEditingInline: Boolean,
    onUpdate: (StoryStickerDraft) -> Unit,
    onDelete: () -> Unit,
    /** Segundo arg: sobre papelera usando el centro propuesto (sin clamp), más fácil al agrandar. */
    onDragChanged: (StoryStickerDraft, Boolean) -> Unit = { draft, _ -> onUpdate(draft) },
    onDragEnded: (StoryStickerDraft, Boolean) -> Unit = { draft, overTrash ->
        if (overTrash) onDelete() else onUpdate(draft)
    },
    onStickerTapped: (StoryStickerDraft) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var naturalWidthPx by remember(sticker.id) { mutableIntStateOf(0) }
    var naturalHeightPx by remember(sticker.id) { mutableIntStateOf(0) }
    var interactionFeedback by remember(sticker.id) { mutableStateOf(false) }
    var layoutCoords by remember(sticker.id) { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(interactionFeedback) {
        if (interactionFeedback) {
            kotlinx.coroutines.delay(200)
            interactionFeedback = false
        }
    }

    val minScale = stickerMinimumScale(sticker.type)
    val maxScale = stickerMaximumScale(
        type = sticker.type,
        baseWidthPx = naturalWidthPx,
        baseHeightPx = naturalHeightPx,
        canvasWidthPx = canvasWidthPx,
        canvasHeightPx = canvasHeightPx,
    )
    val currentScale = sticker.scale.toFloat().coerceIn(minScale, maxScale)
    val currentRotation = sticker.rotationRadians.toFloat()
    val feedbackScale = if (interactionFeedback) 1.05f else 1f
    // ≡ interactiveBoundsSize: hit area nunca menor que el tamaño natural.
    val hitScale = max(currentScale * feedbackScale, 1f)
    val hitWidthPx = if (naturalWidthPx > 0) (naturalWidthPx * hitScale).roundToInt().coerceAtLeast(1) else 0
    val hitHeightPx = if (naturalHeightPx > 0) (naturalHeightPx * hitScale).roundToInt().coerceAtLeast(1) else 0

    val clampedPosition = clampStickerPosition(
        x = sticker.normalizedX.toFloat() * canvasWidthPx,
        y = sticker.normalizedY.toFloat() * canvasHeightPx,
        contentWidthPx = naturalWidthPx,
        contentHeightPx = naturalHeightPx,
        scale = currentScale,
        rotationRadians = currentRotation,
        canvasWidthPx = canvasWidthPx,
        canvasHeightPx = canvasHeightPx,
    )

    val latestSticker by rememberUpdatedState(sticker)
    val latestClamped by rememberUpdatedState(clampedPosition)
    val latestScale by rememberUpdatedState(currentScale)
    val latestRotation by rememberUpdatedState(currentRotation)
    val latestMinScale by rememberUpdatedState(minScale)
    val latestMaxScale by rememberUpdatedState(maxScale)
    val latestContentW by rememberUpdatedState(naturalWidthPx)
    val latestContentH by rememberUpdatedState(naturalHeightPx)
    val latestIsContentEditing by rememberUpdatedState(isContentEditing)
    val latestOnDragChanged by rememberUpdatedState(onDragChanged)
    val latestOnDragEnded by rememberUpdatedState(onDragEnded)
    val latestOnUpdate by rememberUpdatedState(onUpdate)
    val latestOnStickerTapped by rememberUpdatedState(onStickerTapped)
    val latestLayoutCoords by rememberUpdatedState(layoutCoords)

    Box(
        modifier = modifier
            .offset {
                val halfW = if (hitWidthPx > 0) hitWidthPx / 2f else naturalWidthPx / 2f
                val halfH = if (hitHeightPx > 0) hitHeightPx / 2f else naturalHeightPx / 2f
                IntOffset(
                    (clampedPosition.first - halfW).roundToInt(),
                    (clampedPosition.second - halfH).roundToInt(),
                )
            }
            .then(
                if (hitWidthPx > 0 && hitHeightPx > 0) {
                    with(density) {
                        Modifier.requiredSize(hitWidthPx.toDp(), hitHeightPx.toDp())
                    }
                } else {
                    Modifier.wrapContentSize(unbounded = true)
                },
            )
            .onGloballyPositioned { layoutCoords = it }
            // Fondo transparente: asegura hit-test del área aunque el contenido no lo pinte.
            .background(Color.Transparent)
            .then(
                when {
                    isEditingInline -> Modifier
                    // ≡ iOS DragGesture(translation) + MagnifyGesture: crop de la foto interior.
                    // translation absoluta desde el down; px→dp (contentOffset está en points/dp).
                    isContentEditing && sticker.type == "frame" -> Modifier.pointerInput(sticker.id) {
                        // PointerInputScope.density = px por dp (contentOffset ≡ points iOS).
                        val densityScale = this.density.coerceAtLeast(0.01f)
                        awaitEachGesture {
                            // requireUnconsumed: el BasicTextField del caption puede consumir el down.
                            val down = awaitFirstDown(requireUnconsumed = true)
                            val activeAtStart = latestSticker
                            val startOffsetX = activeAtStart.contentOffsetX?.toFloat() ?: 0f
                            val startOffsetY = activeAtStart.contentOffsetY?.toFloat() ?: 0f
                            val startContentScale =
                                (activeAtStart.contentScale?.toFloat() ?: 1f).coerceAtLeast(1f)
                            var pinchStartDistance = -1f
                            var pinchBaseScale = startContentScale
                            var panAnchorLocal = down.position
                            var panBaseOffsetX = startOffsetX
                            var panBaseOffsetY = startOffsetY
                            var lastPointerCount = 1

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                val stickerScale = latestScale.coerceAtLeast(0.0001f)
                                val pointerCount = pressed.size

                                if (pointerCount != lastPointerCount) {
                                    // ≡ iOS: al cambiar 1↔2 dedos, reanclar base.
                                    panAnchorLocal = if (pointerCount >= 2) {
                                        event.calculateCentroid(useCurrent = true)
                                    } else {
                                        pressed.first().position
                                    }
                                    panBaseOffsetX =
                                        latestSticker.contentOffsetX?.toFloat() ?: panBaseOffsetX
                                    panBaseOffsetY =
                                        latestSticker.contentOffsetY?.toFloat() ?: panBaseOffsetY
                                    if (pointerCount >= 2) {
                                        val a = pressed[0].position
                                        val b = pressed[1].position
                                        pinchStartDistance = (a - b).getDistance()
                                        pinchBaseScale =
                                            (latestSticker.contentScale?.toFloat() ?: 1f)
                                                .coerceAtLeast(1f)
                                    } else {
                                        pinchStartDistance = -1f
                                    }
                                    lastPointerCount = pointerCount
                                }

                                val safeScale = if (pointerCount >= 2 && pinchStartDistance > 0f) {
                                    val a = pressed[0].position
                                    val b = pressed[1].position
                                    val dist = (a - b).getDistance()
                                    (pinchBaseScale * (dist / pinchStartDistance))
                                        .coerceIn(1f, 4f)
                                } else {
                                    (latestSticker.contentScale?.toFloat() ?: startContentScale)
                                        .coerceAtLeast(1f)
                                }

                                val centroid = if (pointerCount >= 2) {
                                    event.calculateCentroid(useCurrent = true)
                                } else {
                                    pressed.first().position
                                }
                                val translationPx = centroid - panAnchorLocal
                                // Pantalla px → dp (points iOS), luego / stickerScale (foto dentro del scale).
                                val proposedOffsetX =
                                    panBaseOffsetX + (translationPx.x / densityScale) / stickerScale
                                val proposedOffsetY =
                                    panBaseOffsetY + (translationPx.y / densityScale) / stickerScale
                                val offset = clampFrameContentOffset(
                                    latestSticker,
                                    proposedOffsetX,
                                    proposedOffsetY,
                                    safeScale,
                                )
                                latestOnUpdate(
                                    latestSticker.copy(
                                        contentScale = safeScale.toDouble(),
                                        contentOffsetX = offset.first.toDouble(),
                                        contentOffsetY = offset.second.toDouble(),
                                    ),
                                )
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    else -> Modifier.pointerInput(sticker.id, canvasWidthPx, canvasHeightPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var liveX = latestClamped.first
                            var liveY = latestClamped.second
                            var liveScale = latestScale
                            var liveRotation = latestRotation
                            val gestureStartScale = latestScale
                            val gestureStartRotation = latestRotation
                            var cumulativeZoom = 1f
                            var cumulativeRotationDegrees = 0f
                            var gesturePastTouchSlop = false
                            var latestDraft: StoryStickerDraft? = null
                            var isOverTrash = false
                            var transformed = false
                            var dragged = false

                            // Ancla absoluta en ventana (no se mueve con el offset del sticker).
                            fun fingerWindow(local: Offset): Offset {
                                val coords = latestLayoutCoords?.takeIf { it.isAttached }
                                    ?: return local
                                return coords.localToWindow(local)
                            }

                            var anchorCenterX = liveX
                            var anchorCenterY = liveY
                            var anchorWindow = fingerWindow(down.position)
                            var lastPointerCount = 1

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                val pointerCount = pressed.size
                                if (pointerCount == 0) break

                                val zoomChange = if (pointerCount >= 2) event.calculateZoom() else 1f
                                val rotationChange = if (pointerCount >= 2) event.calculateRotation() else 0f
                                cumulativeZoom *= zoomChange
                                cumulativeRotationDegrees += rotationChange

                                val centroidLocal = if (pointerCount >= 2) {
                                    event.calculateCentroid(useCurrent = true)
                                } else {
                                    pressed.first().position
                                }
                                val fingerWin = fingerWindow(centroidLocal)

                                // Al pasar 1↔2 dedos, reanclar para no saltar.
                                if (pointerCount != lastPointerCount) {
                                    anchorCenterX = liveX
                                    anchorCenterY = liveY
                                    anchorWindow = fingerWin
                                    lastPointerCount = pointerCount
                                }

                                val panFromAnchor = Offset(
                                    fingerWin.x - anchorWindow.x,
                                    fingerWin.y - anchorWindow.y,
                                )
                                val proposedX = anchorCenterX + panFromAnchor.x
                                val proposedY = anchorCenterY + panFromAnchor.y

                                if (!gesturePastTouchSlop) {
                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1f - cumulativeZoom) * centroidSize
                                    val rotationMotion = abs(
                                        Math.toRadians(cumulativeRotationDegrees.toDouble()).toFloat(),
                                    ) * centroidSize
                                    val panMotion = panFromAnchor.getDistance()
                                    gesturePastTouchSlop =
                                        zoomMotion > viewConfiguration.touchSlop ||
                                            rotationMotion > viewConfiguration.touchSlop ||
                                            panMotion > viewConfiguration.touchSlop
                                }

                                val active = latestSticker
                                if (!gesturePastTouchSlop) {
                                    // Tap todavía posible.
                                } else {
                                    dragged = dragged ||
                                        panFromAnchor.getDistance() > viewConfiguration.touchSlop
                                    liveX = proposedX
                                    liveY = proposedY
                                    liveScale = (
                                        gestureStartScale * dampedStickerMagnification(cumulativeZoom)
                                        ).coerceIn(latestMinScale, latestMaxScale)
                                    liveRotation = gestureStartRotation + Math.toRadians(
                                        cumulativeRotationDegrees.toDouble(),
                                    ).toFloat()

                                    val visual = clampStickerPosition(
                                        x = liveX,
                                        y = liveY,
                                        contentWidthPx = latestContentW,
                                        contentHeightPx = latestContentH,
                                        scale = liveScale,
                                        rotationRadians = liveRotation,
                                        canvasWidthPx = canvasWidthPx,
                                        canvasHeightPx = canvasHeightPx,
                                    )
                                    // Papelera con centro propuesto sin clamp: con scale grande
                                    // el clamp deja el centro lejos de la zona inferior.
                                    isOverTrash = dragged && isPointOverStoryOverlayTrash(
                                        liveX,
                                        liveY,
                                        canvasWidthPx.toFloat(),
                                        canvasHeightPx.toFloat(),
                                        density,
                                    )
                                    val updated = active.copy(
                                        normalizedX = (visual.first / canvasWidthPx).toDouble(),
                                        normalizedY = (visual.second / canvasHeightPx).toDouble(),
                                        scale = liveScale.toDouble(),
                                        rotationRadians = liveRotation.toDouble(),
                                    )
                                    latestDraft = updated
                                    transformed = true
                                    if (dragged) {
                                        latestOnDragChanged(updated, isOverTrash)
                                    } else {
                                        latestOnUpdate(updated)
                                    }
                                }
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            if (transformed) {
                                latestDraft?.let { draft ->
                                    if (dragged) {
                                        latestOnDragEnded(draft, isOverTrash)
                                    } else {
                                        latestOnUpdate(draft)
                                    }
                                }
                            } else {
                                interactionFeedback = true
                                HapticManager.shared.lightImpact()
                                latestOnStickerTapped(latestSticker)
                            }
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                // Inline editors can grow after their first measurement. The previous
                // hit-target must not constrain the next pass or lower rows get clipped.
                .wrapContentSize(unbounded = true)
                .onSizeChanged {
                    naturalWidthPx = it.width
                    naturalHeightPx = it.height
                }
                .graphicsLayer {
                    // Escala visual completa; el padre solo amplía el hit-target (max(scale, 1)).
                    scaleX = currentScale * feedbackScale
                    scaleY = currentScale * feedbackScale
                    rotationZ = Math.toDegrees(currentRotation.toDouble()).toFloat()
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            content()
        }
    }
}

private fun dampedStickerMagnification(magnification: Float): Float {
    val damping = .55f
    return if (magnification >= 1f) {
        1f + (magnification - 1f) * damping
    } else {
        1f - (1f - magnification) * damping
    }
}

private fun stickerMinimumScale(type: String): Float = when (type) {
    "poll", "question", "quiz" -> .42f
    "time", "weather", "location", "mention", "hashtag", "link", "countdown", "emojiSlider" -> .35f
    "frame", "selfie" -> .3f
    else -> .28f
}

private fun stickerMaximumScale(
    type: String,
    baseWidthPx: Int,
    baseHeightPx: Int,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
): Float {
    if (baseWidthPx <= 0 || baseHeightPx <= 0 || canvasWidthPx <= 0 || canvasHeightPx <= 0) return 4f
    val (widthPadding, heightRatio, typeCap) = when (type) {
        "poll", "question", "quiz", "emojiSlider" -> Triple(34f, .42f, 1.45f)
        "countdown" -> Triple(40f, .34f, 1.35f)
        "time", "weather", "location", "mention", "hashtag", "link" -> Triple(44f, .28f, 1.85f)
        "frame" -> Triple(28f, .68f, 2.4f)
        "selfie" -> Triple(28f, .42f, 2f)
        else -> Triple(24f, .78f, 4f)
    }
    val hardLimit = minOf(2048f / baseWidthPx, 2048f / baseHeightPx)
    val visualWidth = (canvasWidthPx - widthPadding).coerceAtLeast(120f)
    val visualHeight = (canvasHeightPx * heightRatio).coerceAtLeast(120f)
    return minOf(typeCap, hardLimit, visualWidth / baseWidthPx, visualHeight / baseHeightPx)
        .coerceAtLeast(stickerMinimumScale(type))
}

private fun clampStickerPosition(
    x: Float,
    y: Float,
    contentWidthPx: Int,
    contentHeightPx: Int,
    scale: Float,
    rotationRadians: Float,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
): Pair<Float, Float> {
    if (canvasWidthPx <= 0 || canvasHeightPx <= 0 || contentWidthPx <= 0 || contentHeightPx <= 0) {
        return x to y
    }
    val width = contentWidthPx * scale
    val height = contentHeightPx * scale
    val cos = abs(cos(rotationRadians))
    val sin = abs(sin(rotationRadians))
    val boundsWidth = width * cos + height * sin
    val boundsHeight = width * sin + height * cos
    val halfWidth = minOf(boundsWidth, canvasWidthPx.toFloat()) / 2f
    val halfHeight = minOf(boundsHeight, canvasHeightPx.toFloat()) / 2f
    return x.coerceIn(halfWidth, canvasWidthPx - halfWidth) to
        y.coerceIn(halfHeight, canvasHeightPx - halfHeight)
}

private fun clampFrameContentOffset(
    sticker: StoryStickerDraft,
    x: Float,
    y: Float,
    contentScale: Float,
): Pair<Float, Float> {
    val image = sticker.image ?: return 0f to 0f
    val viewport = 180f
    val imageRatio = image.width.toFloat() / image.height.coerceAtLeast(1)
    val baseWidth: Float
    val baseHeight: Float
    if (imageRatio > 1f) {
        baseHeight = viewport
        baseWidth = viewport * imageRatio
    } else {
        baseWidth = viewport
        baseHeight = viewport / imageRatio.coerceAtLeast(.0001f)
    }
    val maxX = ((baseWidth * contentScale - viewport) / 2f).coerceAtLeast(0f)
    val maxY = ((baseHeight * contentScale - viewport) / 2f).coerceAtLeast(0f)
    return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
}

/** Compose/CameraX counterpart of iOS `SelfieStickerLiveCameraView`. */
@Composable
fun SelfieStickerLiveCameraView(
    onPhotoCaptured: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // ≡ lastSelfieSwitchAt — evita capturar justo tras flip
    var lastSwitchAtMs by remember { mutableLongStateOf(0L) }

    // Skill camerax: liberar use-cases al salir (sesión huérfana en OEMs).
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            provider = null
            executor.shutdown()
        }
    }

    LaunchedEffect(previewView, lensFacing) {
        val previewTarget = previewView ?: return@LaunchedEffect
        val cameraProvider = suspendCoroutine<ProcessCameraProvider> { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ continuation.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        provider = cameraProvider
        val targetRotation = previewTarget.display?.rotation ?: android.view.Surface.ROTATION_0
        imageCapture.targetRotation = targetRotation
        val preview = Preview.Builder()
            .setTargetRotation(targetRotation)
            .build()
            .also { it.surfaceProvider = previewTarget.surfaceProvider }
        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                imageCapture,
            )
        }
    }

    fun capture() {
        if (System.currentTimeMillis() - lastSwitchAtMs < 350L) return
        HapticManager.shared.mediumImpact()
        previewView?.display?.rotation?.let { imageCapture.targetRotation = it }
        val facing = lensFacing
        val outputFile = File(context.cacheDir, "selfie_stickers").also { it.mkdirs() }
            .resolve("selfie_${UUID.randomUUID()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(outputFile).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val path = result.savedUri?.path ?: outputFile.path
                    val source = BitmapFactory.decodeFile(path) ?: return
                    val exifOrientation = runCatching {
                        ExifInterface(path).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    // Píxeles upright (EXIF) — BitmapFactory a menudo ignora orientation.
                    var upright = source.creatorNormalizedUp(exifOrientation)
                    if (upright !== source) source.recycle()
                    // Frontal: espejo como el preview (iOS isVideoMirrored).
                    if (facing == CameraSelector.LENS_FACING_FRONT) {
                        val mirrored = runCatching {
                            Bitmap.createBitmap(
                                upright,
                                0,
                                0,
                                upright.width,
                                upright.height,
                                Matrix().apply { preScale(-1f, 1f) },
                                true,
                            )
                        }.getOrNull()
                        if (mirrored != null && mirrored !== upright) {
                            upright.recycle()
                            upright = mirrored
                        }
                    }
                    outputFile.delete()
                    val rendered = makeCapturedSelfieStickerImage(upright)
                    if (rendered !== upright) upright.recycle()
                    ContextCompat.getMainExecutor(context).execute { onPhotoCaptured(rendered) }
                }

                override fun onError(exception: ImageCaptureException) = Unit
            },
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewContext ->
                PreviewView(previewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { previewView = it }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(androidx.compose.foundation.shape.CircleShape)
                .pointerInput(lensFacing) {
                    detectTapGestures(
                        onTap = { capture() },
                        onLongPress = {
                            lastSwitchAtMs = System.currentTimeMillis()
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                CameraSelector.LENS_FACING_BACK
                            } else {
                                CameraSelector.LENS_FACING_FRONT
                            }
                            HapticManager.shared.mediumImpact()
                        },
                    )
                },
        )
        // ≡ badge camera.circle.fill
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(22.dp),
        )
    }
}

/** Port de `makeCapturedSelfieStickerImage` de este mismo archivo Swift. */
private fun makeCapturedSelfieStickerImage(source: Bitmap, sizePx: Int = 120): Bitmap {
    val selfie = downscaleSelfieImageIfNeeded(source)
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val imageInset = sizePx * 0.012f
    val imageRect = RectF(imageInset, imageInset, sizePx - imageInset, sizePx - imageInset)
    val cropSize = minOf(selfie.width, selfie.height)
    val crop = Rect(
        (selfie.width - cropSize) / 2,
        (selfie.height - cropSize) / 2,
        (selfie.width + cropSize) / 2,
        (selfie.height + cropSize) / 2,
    )
    val center = sizePx / 2f
    canvas.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        setShadowLayer(sizePx * 0.08f, 0f, sizePx * 0.033f, 0x1F000000)
    })
    canvas.save()
    canvas.clipPath(Path().apply { addOval(imageRect, Path.Direction.CW) })
    canvas.drawBitmap(selfie, crop, imageRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
    canvas.drawOval(imageRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x0A000000
        style = Paint.Style.STROKE
        strokeWidth = maxOf(0.5f, sizePx * 0.005f)
    })
    if (selfie !== source) selfie.recycle()
    return output
}

/** Conserva el límite de 900 px que Swift aplica antes de rasterizar la selfie en un sticker. */
private fun downscaleSelfieImageIfNeeded(source: Bitmap, maxDimensionPx: Int = 900): Bitmap {
    if (source.width <= maxDimensionPx && source.height <= maxDimensionPx) return source
    val ratio = source.width.toFloat() / source.height.coerceAtLeast(1)
    val target = if (source.width > source.height) {
        maxDimensionPx to (maxDimensionPx / ratio).roundToInt().coerceAtLeast(1)
    } else {
        (maxDimensionPx * ratio).roundToInt().coerceAtLeast(1) to maxDimensionPx
    }
    return Bitmap.createScaledBitmap(source, target.first, target.second, true)
}
