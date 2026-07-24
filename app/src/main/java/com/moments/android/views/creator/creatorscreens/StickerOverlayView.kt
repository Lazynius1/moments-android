package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.StoryStickerDraft
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Contenedor interactivo equivalente a `StickerOverlayView.swift`.
 *
 * El contenido del sticker permanece en `storyeditor.kt`, donde Android ya concentra sus
 * variantes visuales e inputs inline; este overlay conserva la geometría común de Swift:
 * posición centrada, límites teniendo en cuenta escala/rotación, pellizco amortiguado y giro.
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
    onDragChanged: (StoryStickerDraft) -> Unit = onUpdate,
    onDragEnded: (StoryStickerDraft) -> Unit = onUpdate,
    onStickerTapped: (StoryStickerDraft) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var contentWidthPx by remember(sticker.id) { mutableIntStateOf(0) }
    var contentHeightPx by remember(sticker.id) { mutableIntStateOf(0) }
    var interactionFeedback by remember(sticker.id) { mutableStateOf(false) }
    var latestTransformUpdate by remember(sticker.id) { mutableStateOf<StoryStickerDraft?>(null) }

    LaunchedEffect(interactionFeedback) {
        if (interactionFeedback) {
            kotlinx.coroutines.delay(200)
            interactionFeedback = false
        }
    }

    val minScale = stickerMinimumScale(sticker.type)
    val maxScale = stickerMaximumScale(
        type = sticker.type,
        baseWidthPx = contentWidthPx,
        baseHeightPx = contentHeightPx,
        canvasWidthPx = canvasWidthPx,
        canvasHeightPx = canvasHeightPx,
    )
    val currentScale = sticker.scale.toFloat().coerceIn(minScale, maxScale)
    val currentRotation = sticker.rotationRadians.toFloat()
    val clampedPosition = clampStickerPosition(
        x = sticker.normalizedX.toFloat() * canvasWidthPx,
        y = sticker.normalizedY.toFloat() * canvasHeightPx,
        contentWidthPx = contentWidthPx,
        contentHeightPx = contentHeightPx,
        scale = currentScale,
        rotationRadians = currentRotation,
        canvasWidthPx = canvasWidthPx,
        canvasHeightPx = canvasHeightPx,
    )

    fun updateTransform(
        panX: Float = 0f,
        panY: Float = 0f,
        zoom: Float = 1f,
        rotationDelta: Float = 0f,
    ) {
        if (isEditingInline || canvasWidthPx <= 0 || canvasHeightPx <= 0) return
        if (isContentEditing && sticker.type == "frame") {
            val nextScale = (sticker.contentScale?.toFloat() ?: 1f) * zoom
            val safeScale = nextScale.coerceIn(1f, 4f)
            val proposedX = (sticker.contentOffsetX?.toFloat() ?: 0f) + panX / currentScale.coerceAtLeast(.0001f)
            val proposedY = (sticker.contentOffsetY?.toFloat() ?: 0f) + panY / currentScale.coerceAtLeast(.0001f)
            val offset = clampFrameContentOffset(sticker, proposedX, proposedY, safeScale)
            onUpdate(
                sticker.copy(
                    contentScale = safeScale.toDouble(),
                    contentOffsetX = offset.first.toDouble(),
                    contentOffsetY = offset.second.toDouble(),
                ),
            )
            return
        }

        val nextScale = (currentScale * dampedMagnification(zoom)).coerceIn(minScale, maxScale)
        val nextRotation = currentRotation + rotationDelta
        val position = clampStickerPosition(
            x = clampedPosition.first + panX,
            y = clampedPosition.second + panY,
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
            scale = nextScale,
            rotationRadians = nextRotation,
            canvasWidthPx = canvasWidthPx,
            canvasHeightPx = canvasHeightPx,
        )
        val updated = sticker.copy(
            normalizedX = (position.first / canvasWidthPx).toDouble(),
            normalizedY = (position.second / canvasHeightPx).toDouble(),
            scale = nextScale.toDouble(),
            rotationRadians = nextRotation.toDouble(),
        )
        latestTransformUpdate = updated
        onDragChanged(updated)
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (clampedPosition.first - contentWidthPx / 2f).roundToInt(),
                    (clampedPosition.second - contentHeightPx / 2f).roundToInt(),
                )
            }
            .onSizeChanged {
                contentWidthPx = it.width
                contentHeightPx = it.height
            }
            .graphicsLayer {
                val feedbackScale = if (interactionFeedback) 1.05f else 1f
                scaleX = currentScale * feedbackScale
                scaleY = currentScale * feedbackScale
                rotationZ = Math.toDegrees(currentRotation.toDouble()).toFloat()
                transformOrigin = TransformOrigin.Center
            }
            .then(
                if (isEditingInline) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(sticker.id, canvasWidthPx, canvasHeightPx, isContentEditing, currentScale, currentRotation) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                updateTransform(
                                    panX = pan.x,
                                    panY = pan.y,
                                    zoom = zoom,
                                    rotationDelta = Math.toRadians(rotation.toDouble()).toFloat(),
                                )
                            }
                        }
                        // `detectTransformGestures` expone el cambio continuo pero no un callback
                        // final. Observamos el levantamiento del dedo para conservar el `onEnded`
                        // de Swift (papelera, persistencia y limpieza del estado de arrastre).
                        .pointerInput(sticker.id) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var touchesActive: Boolean
                                do {
                                    touchesActive = awaitPointerEvent(PointerEventPass.Main)
                                        .changes.any { it.pressed }
                                } while (touchesActive)
                                latestTransformUpdate?.let(onDragEnded)
                                latestTransformUpdate = null
                            }
                        }
                        .pointerInput(sticker.id, sticker.type) {
                            detectTapGestures(
                                onTap = {
                                    if (sticker.type != "selfie" || sticker.caption != "selfie_live") {
                                        interactionFeedback = true
                                        HapticManager.shared.lightImpact()
                                        onStickerTapped(sticker)
                                    }
                                },
                            )
                        }
                },
            ),
    ) {
        content()
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

private fun dampedMagnification(value: Float): Float = if (value >= 1f) {
    1f + (value - 1f) * .55f
} else {
    1f - (1f - value) * .55f
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
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
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
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewTarget.surfaceProvider }
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
        val outputFile = File(context.cacheDir, "selfie_stickers").also { it.mkdirs() }
            .resolve("selfie_${UUID.randomUUID()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(outputFile).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val source = BitmapFactory.decodeFile(result.savedUri?.path ?: outputFile.path)
                    outputFile.delete()
                    if (source != null) {
                        val rendered = makeCapturedSelfieStickerImage(source)
                        if (rendered !== source) source.recycle()
                        ContextCompat.getMainExecutor(context).execute { onPhotoCaptured(rendered) }
                    }
                }

                override fun onError(exception: ImageCaptureException) = Unit
            },
        )
    }

    AndroidView(
        factory = { previewContext ->
            PreviewView(previewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }.also { previewView = it }
        },
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .pointerInput(lensFacing) {
                detectTapGestures(
                    onTap = { capture() },
                    onLongPress = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            CameraSelector.LENS_FACING_BACK
                        } else {
                            CameraSelector.LENS_FACING_FRONT
                        }
                    },
                )
            }
            .fillMaxSize(),
    )
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
