package com.moments.android.views.creator.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Límites de `StoryMediaTransformLimits` en EditableImageView.swift. */
object StoryMediaTransformLimits {
    const val minScale = .45f
    const val maxScale = 1.8f
    const val snapScaleThreshold = .08f
    const val snapRotationThresholdRadians = (Math.PI / 36.0).toFloat()
}

enum class StoryMediaPresentationMode { Fill, FitWithBlur }

/** Reglas de CreatorSharedModels.swift que usa EditableImageView. */
object StoryMediaLayoutRules {
    private const val fillTolerance = .035f

    fun presentationMode(mediaAspectRatio: Float, canvasAspectRatio: Float): StoryMediaPresentationMode {
        if (!mediaAspectRatio.isFinite() || mediaAspectRatio <= 0f ||
            !canvasAspectRatio.isFinite() || canvasAspectRatio <= 0f
        ) return StoryMediaPresentationMode.Fill
        return if (abs(mediaAspectRatio - canvasAspectRatio) <= fillTolerance) StoryMediaPresentationMode.Fill
        else StoryMediaPresentationMode.FitWithBlur
    }

    fun presentationMode(mediaSize: Size, canvasSize: Size): StoryMediaPresentationMode =
        presentationMode(mediaSize.width / max(mediaSize.height, 1f), canvasSize.width / max(canvasSize.height, 1f))
}

fun storyMediaBaseRect(mediaSize: Size, canvasSize: Size): Rect = storyMediaRectForCanvas(mediaSize, canvasSize)

/** Port literal de `storyMediaRectForCanvas`. */
fun storyMediaRectForCanvas(mediaSize: Size, canvasSize: Size): Rect {
    val imageRatio = mediaSize.width / max(mediaSize.height, 1f)
    val targetRatio = canvasSize.width / max(canvasSize.height, 1f)
    val useFit = StoryMediaLayoutRules.presentationMode(imageRatio, targetRatio) == StoryMediaPresentationMode.FitWithBlur
    val mediaIsWider = imageRatio > targetRatio
    val (width, height) = if (useFit) {
        if (mediaIsWider) canvasSize.width to canvasSize.width / max(imageRatio, .0001f)
        else canvasSize.height * imageRatio to canvasSize.height
    } else {
        if (mediaIsWider) canvasSize.height * imageRatio to canvasSize.height
        else canvasSize.width to canvasSize.width / max(imageRatio, .0001f)
    }
    return Rect((canvasSize.width - width) / 2f, (canvasSize.height - height) / 2f, (canvasSize.width + width) / 2f, (canvasSize.height + height) / 2f)
}

fun storyShouldShowGeneratedBackground(scale: Float, offset: Offset, rotationRadians: Float): Boolean =
    scale < .995f || abs(offset.x) > 1f || abs(offset.y) > 1f || abs(rotationRadians) > .015f

fun storyClampedMediaScale(proposedScale: Float): Float =
    if (!proposedScale.isFinite() || proposedScale <= 0f) 1f else proposedScale.coerceIn(StoryMediaTransformLimits.minScale, StoryMediaTransformLimits.maxScale)

/** Port de `storyClampedMediaOffset`: conserva una porción mínima visible del medio. */
fun storyClampedMediaOffset(proposedOffset: Offset, canvasSize: Size, mediaSize: Size, scale: Float): Offset {
    val safeOffset = Offset(if (proposedOffset.x.isFinite()) proposedOffset.x else 0f, if (proposedOffset.y.isFinite()) proposedOffset.y else 0f)
    val base = storyMediaBaseRect(mediaSize, canvasSize)
    val scaledWidth = base.width * storyClampedMediaScale(scale)
    val scaledHeight = base.height * storyClampedMediaScale(scale)
    val minVisibleX = min(max(44f, scaledWidth * .24f), scaledWidth)
    val minVisibleY = min(max(44f, scaledHeight * .24f), scaledHeight)
    val horizontalLimit = max(0f, canvasSize.width / 2f + scaledWidth / 2f - minVisibleX)
    val verticalLimit = max(0f, canvasSize.height / 2f + scaledHeight / 2f - minVisibleY)
    return Offset(safeOffset.x.coerceIn(-horizontalLimit, horizontalLimit), safeOffset.y.coerceIn(-verticalLimit, verticalLimit))
}

fun storySnappedMediaScale(scale: Float): Float = if (abs(scale - 1f) < StoryMediaTransformLimits.snapScaleThreshold) 1f else scale

fun storySnappedMediaRotation(rotationRadians: Float): Float =
    if (!rotationRadians.isFinite() || abs(rotationRadians) < StoryMediaTransformLimits.snapRotationThresholdRadians) 0f else rotationRadians

/** Canvas Compose equivalente a `drawStoryMediaBackground` / `StoryMediaBackgroundView`. */
fun DrawScope.drawStoryMediaBackground(palette: List<Color>) {
    val resolved = palette.ifEmpty { listOf(Color(0xFF0B1215)) }
    if (resolved.size == 1) drawRect(resolved.first()) else drawRect(Brush.linearGradient(resolved))
}

@androidx.compose.runtime.Composable
fun StoryMediaBackgroundView(palette: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawStoryMediaBackground(palette) }
}

/** Port de `StoryEditableMediaContainer`; tamaños en píxeles de Compose. */
@androidx.compose.runtime.Composable
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
    foreground: @androidx.compose.runtime.Composable (Rect) -> Unit,
) {
    val density = LocalDensity.current
    var dominantColors by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Color>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(paletteIdentity, paletteOverride, paletteSourceImage) {
        dominantColors = if (paletteOverride.isNullOrEmpty()) StoryDominantColorsExtractor.extract(paletteSourceImage) else emptyList()
    }
    val baseRect = storyMediaBaseRect(mediaSize, canvasSize)
    val hasIntrinsicGap = canvasSize.width - baseRect.width > 1f || canvasSize.height - baseRect.height > 1f
    val showBackground = storyShouldShowGeneratedBackground(scale, offset, rotationRadians) || hasIntrinsicGap
    val palette = paletteOverride?.takeIf { it.isNotEmpty() } ?: dominantColors
    val canvasWidth = with(density) { canvasSize.width.toDp() }
    val canvasHeight = with(density) { canvasSize.height.toDp() }
    val mediaWidth = with(density) { baseRect.width.toDp() }
    val mediaHeight = with(density) { baseRect.height.toDp() }

    Box(
        modifier
            .requiredSize(canvasWidth, canvasHeight)
            .then(
                if (!isInteractionEnabled) Modifier else Modifier.pointerInput(mediaSize, canvasSize, scale, offset, rotationRadians) {
                    detectTransformGestures { _, pan, zoom, rotationDegrees ->
                        val nextScale = storyClampedMediaScale(scale * zoom)
                        onScaleChange(nextScale)
                        onOffsetChange(storyClampedMediaOffset(offset + pan, canvasSize, mediaSize, nextScale))
                        onRotationChange(rotationRadians + Math.toRadians(rotationDegrees.toDouble()).toFloat())
                    }
                },
            ),
    ) {
        if (showBackground) StoryMediaBackgroundView(palette, Modifier.fillMaxSize())
        Box(
            Modifier
                .requiredSize(mediaWidth, mediaHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = Math.toDegrees(rotationRadians.toDouble()).toFloat()
                    translationX = offset.x
                    translationY = offset.y
                },
        ) { foreground(baseRect) }
    }
}

/** Port de `EditableImageView`: `filteredImage` tiene prioridad sobre la imagen original. */
@androidx.compose.runtime.Composable
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
            contentScale = if (StoryMediaLayoutRules.presentationMode(displaySize, canvasSize) == StoryMediaPresentationMode.Fill) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
