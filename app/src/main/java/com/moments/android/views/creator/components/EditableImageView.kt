package com.moments.android.views.creator.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.moments.android.views.creator.StoryMediaLayoutRules
import com.moments.android.views.creator.StoryMediaPresentationMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** ≡ `StoryMediaTransformLimits`. */
object StoryMediaTransformLimits {
    const val minScale = 0.45f
    const val maxScale = 1.8f
    const val snapScaleThreshold = 0.08f
    const val snapRotationThresholdRadians = (Math.PI / 36.0).toFloat()
}

fun storyMediaBaseRect(mediaSize: Size, canvasSize: Size): Rect =
    storyMediaRectForCanvas(mediaSize, canvasSize)

/** Port literal de `storyMediaRectForCanvas`. */
fun storyMediaRectForCanvas(mediaSize: Size, canvasSize: Size): Rect {
    val imageRatio = mediaSize.width / max(mediaSize.height, 1f)
    val targetRatio = canvasSize.width / max(canvasSize.height, 1f)
    val useFit =
        StoryMediaLayoutRules.presentationMode(imageRatio, targetRatio) == StoryMediaPresentationMode.FIT_WITH_BLUR
    val mediaIsWider = imageRatio > targetRatio
    val (width, height) = if (useFit) {
        if (mediaIsWider) {
            canvasSize.width to canvasSize.width / max(imageRatio, 0.0001f)
        } else {
            canvasSize.height * imageRatio to canvasSize.height
        }
    } else {
        if (mediaIsWider) {
            canvasSize.height * imageRatio to canvasSize.height
        } else {
            canvasSize.width to canvasSize.width / max(imageRatio, 0.0001f)
        }
    }
    return Rect(
        (canvasSize.width - width) / 2f,
        (canvasSize.height - height) / 2f,
        (canvasSize.width + width) / 2f,
        (canvasSize.height + height) / 2f,
    )
}

fun storyShouldShowGeneratedBackground(scale: Float, offset: Offset, rotationRadians: Float): Boolean =
    scale < 0.995f || abs(offset.x) > 1f || abs(offset.y) > 1f || abs(rotationRadians) > 0.015f

fun storyClampedMediaScale(proposedScale: Float): Float =
    if (!proposedScale.isFinite() || proposedScale <= 0f) {
        1f
    } else {
        proposedScale.coerceIn(StoryMediaTransformLimits.minScale, StoryMediaTransformLimits.maxScale)
    }

/** Port de `storyClampedMediaOffset`: conserva una porción mínima visible del medio. */
fun storyClampedMediaOffset(
    proposedOffset: Offset,
    canvasSize: Size,
    mediaSize: Size,
    scale: Float,
): Offset {
    val safeOffset = Offset(
        if (proposedOffset.x.isFinite()) proposedOffset.x else 0f,
        if (proposedOffset.y.isFinite()) proposedOffset.y else 0f,
    )
    val safeScale = storyClampedMediaScale(scale)
    val base = storyMediaBaseRect(mediaSize, canvasSize)
    val scaledWidth = base.width * safeScale
    val scaledHeight = base.height * safeScale
    val minVisibleX = min(max(44f, scaledWidth * 0.24f), scaledWidth)
    val minVisibleY = min(max(44f, scaledHeight * 0.24f), scaledHeight)
    val horizontalLimit = max(0f, canvasSize.width / 2f + scaledWidth / 2f - minVisibleX)
    val verticalLimit = max(0f, canvasSize.height / 2f + scaledHeight / 2f - minVisibleY)
    return Offset(
        safeOffset.x.coerceIn(-horizontalLimit, horizontalLimit),
        safeOffset.y.coerceIn(-verticalLimit, verticalLimit),
    )
}

fun storySnappedMediaScale(scale: Float): Float =
    if (abs(scale - 1f) < StoryMediaTransformLimits.snapScaleThreshold) 1f else scale

fun storySnappedMediaRotation(rotationRadians: Float): Float =
    if (!rotationRadians.isFinite() || abs(rotationRadians) < StoryMediaTransformLimits.snapRotationThresholdRadians) {
        0f
    } else {
        rotationRadians
    }

/**
 * ≡ `storyDominantBackgroundColors(from:maxColors:)` de este archivo Swift
 * (no el extractor genérico de `StoryDominantColorsExtractor`).
 */
fun storyDominantBackgroundColors(image: Bitmap?, maxColors: Int = 3): List<Color> {
    val fallbackDark = parseStoryColorHex("0B1215")
    val fallbackLight = parseStoryColorHex("FAF9F6")
    if (image == null || image.width <= 0 || image.height <= 0) {
        return listOf(fallbackDark, fallbackLight)
    }

    val sampleSize = 36
    val sample = Bitmap.createScaledBitmap(image, sampleSize, sampleSize, true)
    val pixels = IntArray(sampleSize * sampleSize)
    sample.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
    if (sample !== image) sample.recycle()

    data class Bucket(
        var count: Int = 0,
        var red: Float = 0f,
        var green: Float = 0f,
        var blue: Float = 0f,
        var saturation: Float = 0f,
    )

    val quantizationStep = 32
    val buckets = mutableMapOf<Int, Bucket>()
    val hsv = FloatArray(3)

    for (y in 0 until sampleSize) {
        for (x in 0 until sampleSize) {
            val pixel = pixels[y * sampleSize + x]
            val alpha = AndroidColor.alpha(pixel) / 255f
            if (alpha <= 0.6f) continue

            val red = AndroidColor.red(pixel) / 255f
            val green = AndroidColor.green(pixel) / 255f
            val blue = AndroidColor.blue(pixel) / 255f
            AndroidColor.RGBToHSV(
                AndroidColor.red(pixel),
                AndroidColor.green(pixel),
                AndroidColor.blue(pixel),
                hsv,
            )
            val saturation = hsv[1]
            val brightness = hsv[2]
            if (brightness <= 0.14f || brightness >= 0.96f) continue
            if (saturation <= 0.08f && brightness >= 0.28f) continue

            val quantizedR = (red * 255f).toInt() / quantizationStep
            val quantizedG = (green * 255f).toInt() / quantizationStep
            val quantizedB = (blue * 255f).toInt() / quantizationStep
            val key = (quantizedR shl 16) or (quantizedG shl 8) or quantizedB
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count += 1
            bucket.red += red
            bucket.green += green
            bucket.blue += blue
            bucket.saturation += saturation
        }
    }

    val sorted = buckets.values
        .filter { it.count > 4 }
        .sortedWith(
            compareByDescending<Bucket> { it.count }
                .thenByDescending { it.saturation },
        )

    val selected = mutableListOf<Color>()
    val selectedHsv = mutableListOf<FloatArray>()

    for (candidate in sorted) {
        val divisor = max(candidate.count, 1).toFloat()
        val color = Color(
            red = candidate.red / divisor,
            green = candidate.green / divisor,
            blue = candidate.blue / divisor,
            alpha = 1f,
        )
        AndroidColor.RGBToHSV(
            (color.red * 255).toInt().coerceIn(0, 255),
            (color.green * 255).toInt().coerceIn(0, 255),
            (color.blue * 255).toInt().coerceIn(0, 255),
            hsv,
        )
        val hue = hsv[0] / 360f
        val saturation = hsv[1]
        val brightness = hsv[2]

        val isDistinct = selectedHsv.none { existing ->
            val existingHue = existing[0] / 360f
            val hueDelta = min(abs(existingHue - hue), 1f - abs(existingHue - hue))
            val saturationDelta = abs(existing[1] - saturation)
            val brightnessDelta = abs(existing[2] - brightness)
            hueDelta < 0.08f && saturationDelta < 0.16f && brightnessDelta < 0.16f
        }

        if (isDistinct) {
            selected += color
            selectedHsv += floatArrayOf(hsv[0], hsv[1], hsv[2])
        }
        if (selected.size == maxColors) break
    }

    if (selected.isEmpty()) {
        selected += averageColorForStoryBackground(image)
    }
    return selected.take(maxColors)
}

/** ≡ `averageColorForStoryBackground` (CIAreaAverage → promedio de píxeles). */
private fun averageColorForStoryBackground(image: Bitmap): Color {
    val fallback = parseStoryColorHex("0B1215")
    if (image.width <= 0 || image.height <= 0) return fallback
    val sampleSize = min(48, max(image.width, image.height)).coerceAtLeast(1)
    val sample = Bitmap.createScaledBitmap(image, sampleSize, sampleSize, true)
    val pixels = IntArray(sampleSize * sampleSize)
    sample.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
    if (sample !== image) sample.recycle()

    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0L
    for (pixel in pixels) {
        if (AndroidColor.alpha(pixel) < 16) continue
        r += AndroidColor.red(pixel)
        g += AndroidColor.green(pixel)
        b += AndroidColor.blue(pixel)
        count++
    }
    if (count == 0L) return fallback
    return Color(
        red = (r / count) / 255f,
        green = (g / count) / 255f,
        blue = (b / count) / 255f,
        alpha = 1f,
    )
}

/** ≡ `drawStoryMediaBackground`. */
fun DrawScope.drawStoryMediaBackground(palette: List<Color>) {
    val resolved = palette.ifEmpty { listOf(parseStoryColorHex("0B1215")) }
    if (resolved.size == 1) {
        drawRect(resolved.first())
    } else {
        drawRect(Brush.linearGradient(resolved))
    }
}

@Composable
fun StoryMediaBackgroundView(palette: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawStoryMediaBackground(palette) }
}

/** Port de `StoryEditableMediaContainer`; tamaños en píxeles de Compose. */
@Composable
fun StoryEditableMediaContainer(
    mediaSize: Size,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    rotationRadians: Float,
    onRotationChange: (Float) -> Unit,
    canvasSize: Size,
    paletteIdentity: String,
    paletteSourceImage: Bitmap,
    paletteOverride: List<Color>? = null,
    isInteractionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    foreground: @Composable (Rect) -> Unit,
) {
    val density = LocalDensity.current
    var dominantColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(paletteIdentity, paletteOverride, paletteSourceImage) {
        dominantColors = if (paletteOverride.isNullOrEmpty()) {
            storyDominantBackgroundColors(paletteSourceImage)
        } else {
            emptyList()
        }
    }

    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentRotation by rememberUpdatedState(rotationRadians)
    val onScale by rememberUpdatedState(onScaleChange)
    val onOffset by rememberUpdatedState(onOffsetChange)
    val onRotation by rememberUpdatedState(onRotationChange)

    // Clamp externo como `onChange` de iOS.
    LaunchedEffect(scale) {
        val clamped = storyClampedMediaScale(scale)
        if (clamped != scale) onScaleChange(clamped)
    }
    LaunchedEffect(offset, scale, canvasSize, mediaSize) {
        val clamped = storyClampedMediaOffset(offset, canvasSize, mediaSize, scale)
        if (clamped != offset) onOffsetChange(clamped)
    }
    LaunchedEffect(rotationRadians) {
        if (!rotationRadians.isFinite()) onRotationChange(0f)
    }

    val baseRect = storyMediaBaseRect(mediaSize, canvasSize)
    val hasIntrinsicGap =
        canvasSize.width - baseRect.width > 1f || canvasSize.height - baseRect.height > 1f
    val showBackground =
        storyShouldShowGeneratedBackground(scale, offset, rotationRadians) || hasIntrinsicGap
    val palette = paletteOverride?.takeIf { it.isNotEmpty() } ?: dominantColors
    val canvasWidth = with(density) { canvasSize.width.toDp() }
    val canvasHeight = with(density) { canvasSize.height.toDp() }
    val mediaWidth = with(density) { baseRect.width.toDp() }
    val mediaHeight = with(density) { baseRect.height.toDp() }

    Box(
        modifier
            .requiredSize(canvasWidth, canvasHeight)
            .then(
                if (!isInteractionEnabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(mediaSize, canvasSize) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled) {
                                    val zoomChange = event.calculateZoom()
                                    val rotationChange =
                                        Math.toRadians(event.calculateRotation().toDouble()).toFloat()
                                    val panChange = event.calculatePan()
                                    if (zoomChange != 1f || rotationChange != 0f || panChange != Offset.Zero) {
                                        val nextScale =
                                            storyClampedMediaScale(currentScale * zoomChange)
                                        onScale(nextScale)
                                        onOffset(
                                            storyClampedMediaOffset(
                                                currentOffset + panChange,
                                                canvasSize,
                                                mediaSize,
                                                nextScale,
                                            ),
                                        )
                                        onRotation(currentRotation + rotationChange)
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // ≡ onEnded: snap scale/rotation + re-clamp offset.
                            val snappedScale = storySnappedMediaScale(storyClampedMediaScale(currentScale))
                            val snappedRotation = storySnappedMediaRotation(currentRotation)
                            onScale(snappedScale)
                            onRotation(snappedRotation)
                            onOffset(
                                storyClampedMediaOffset(
                                    currentOffset,
                                    canvasSize,
                                    mediaSize,
                                    snappedScale,
                                ),
                            )
                        }
                    }
                },
            ),
    ) {
        // iOS: background siempre en el ZStack con opacity 0/1.
        StoryMediaBackgroundView(
            palette = palette,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (showBackground) 1f else 0f },
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(mediaWidth, mediaHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = Math.toDegrees(rotationRadians.toDouble()).toFloat()
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            foreground(baseRect)
        }
    }
}

/** Port de `EditableImageView`: `filteredImage` tiene prioridad sobre la imagen original. */
@Composable
fun EditableImageView(
    image: Bitmap,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    rotationRadians: Float,
    onRotationChange: (Float) -> Unit,
    filteredImage: Bitmap? = null,
    canvasSize: Size,
    paletteIdentity: String,
    paletteOverride: List<Color>? = null,
    isInteractionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val display = filteredImage ?: image
    val displaySize = Size(display.width.toFloat(), display.height.toFloat())
    val contentScale =
        if (StoryMediaLayoutRules.presentationMode(displaySize, canvasSize) == StoryMediaPresentationMode.FILL) {
            ContentScale.Crop
        } else {
            ContentScale.Fit
        }
    StoryEditableMediaContainer(
        mediaSize = displaySize,
        scale = scale,
        onScaleChange = onScaleChange,
        offset = offset,
        onOffsetChange = onOffsetChange,
        rotationRadians = rotationRadians,
        onRotationChange = onRotationChange,
        canvasSize = canvasSize,
        paletteIdentity = paletteIdentity,
        paletteSourceImage = display,
        paletteOverride = paletteOverride,
        isInteractionEnabled = isInteractionEnabled,
        modifier = modifier,
    ) {
        Image(
            bitmap = display.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
