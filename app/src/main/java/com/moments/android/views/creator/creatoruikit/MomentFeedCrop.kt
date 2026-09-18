package com.moments.android.views.creator.creatoruikit

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.moments.android.models.MediaItemFeedCrop
import com.moments.android.views.profile.core.GridPreviewModeChipIcon
import com.moments.android.views.profile.core.MomentGridPreviewFitMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port de `MomentFeedCrop.swift`.
 * Recortes de la card de momento (ancho 1080):
 * - Cuadrado 1:1, retrato 4:5, retrato alto 3:4, apaisado 1.91:1
 */
object MomentFeedCrop {
    const val maxScale = 4f
    const val exportWidth = 1080f

    val squareAspect = 1f
    val portraitMax = 1080f / 1350f
    val reelsGridAspect = 1080f / 1440f
    val landscapeMax = 1080f / 566f
    /** Peek inmersivo: no más alto que 9:16. */
    val immersivePortraitMax = 9f / 16f

    fun clamp(ratio: Float): Float =
        if (!ratio.isFinite() || ratio <= 0f) squareAspect else ratio

    /** Rango continuo 3:4…1.91:1; Reels 9:16 → 4:5 en la card. */
    fun feedCardAspect(from: Float): Float {
        val ratio = clamp(from)
        if (abs(ratio - 9f / 16f) < 0.05f || ratio < 0.70f) return portraitMax
        return min(max(ratio, reelsGridAspect), landscapeMax)
    }

    fun immersiveAspect(from: Float): Float = max(clamp(from), immersivePortraitMax)

    /** Nombre persistido de cardAspect (≡ CreatorMedia.AspectRatio.fromFeedPostRatio.displayName). */
    fun cardAspectDisplayName(ratio: Float): String {
        val safe = feedCardAspect(ratio)
        return when {
            abs(safe - squareAspect) < 0.008f -> "1:1"
            abs(safe - portraitMax) < 0.008f -> "4:5"
            abs(safe - reelsGridAspect) < 0.008f -> "3:4"
            abs(safe - landscapeMax) < 0.015f -> "1.91:1"
            else -> "%.4f".format(safe).trimEnd('0').trimEnd('.')
        }
    }

    fun normalizedFeedCrop(
        rect: RectF,
        imageWidth: Float,
        imageHeight: Float,
        cardAspect: Float,
    ): MediaItemFeedCrop {
        if (imageWidth <= 1f || imageHeight <= 1f) {
            return MediaItemFeedCrop.fullBounds(cardAspectDisplayName(cardAspect))
        }
        val bounds = RectF(0f, 0f, imageWidth, imageHeight)
        val safe = RectF(rect).also { it.intersect(bounds) }
        return MediaItemFeedCrop(
            cardAspect = cardAspectDisplayName(cardAspect),
            x = (safe.left / imageWidth).toDouble(),
            y = (safe.top / imageHeight).toDouble(),
            width = (safe.width() / imageWidth).toDouble(),
            height = (safe.height() / imageHeight).toDouble(),
        )
    }

    fun immersiveCropRect(
        imageWidth: Float,
        imageHeight: Float,
        preserving: MediaItemFeedCrop?,
    ): RectF {
        val bounds = RectF(0f, 0f, imageWidth, imageHeight)
        val originalAspect = imageWidth / max(imageHeight, 1f)
        if (originalAspect >= immersivePortraitMax) return bounds

        val targetHeight = min(imageWidth / immersivePortraitMax, imageHeight)
        val protected = preserving?.rect(imageWidth, imageHeight) ?: bounds
        val preferredY = protected.centerY() - targetHeight / 2f
        val minimumY = max(protected.bottom - targetHeight, 0f)
        val maximumY = min(protected.top, imageHeight - targetHeight)
        val clampedY = if (minimumY <= maximumY) {
            preferredY.coerceIn(minimumY, maximumY)
        } else {
            preferredY.coerceIn(0f, imageHeight - targetHeight)
        }
        return RectF(0f, clampedY, imageWidth, clampedY + targetHeight)
    }

    fun remap(
        feedCrop: MediaItemFeedCrop,
        sourceWidth: Float,
        sourceHeight: Float,
        sourceRect: RectF,
    ): MediaItemFeedCrop {
        if (sourceRect.width() <= 1f || sourceRect.height() <= 1f) return feedCrop
        val old = feedCrop.rect(sourceWidth, sourceHeight).also { it.intersect(sourceRect) }
        return MediaItemFeedCrop(
            cardAspect = feedCrop.cardAspect,
            x = ((old.left - sourceRect.left) / sourceRect.width()).toDouble(),
            y = ((old.top - sourceRect.top) / sourceRect.height()).toDouble(),
            width = (old.width() / sourceRect.width()).toDouble(),
            height = (old.height() / sourceRect.height()).toDouble(),
        )
    }

    fun translated(feedCrop: MediaItemFeedCrop, deltaX: Double, deltaY: Double): MediaItemFeedCrop {
        val maxX = max(1.0 - feedCrop.width, 0.0)
        val maxY = max(1.0 - feedCrop.height, 0.0)
        return MediaItemFeedCrop(
            cardAspect = feedCrop.cardAspect,
            x = (feedCrop.x + deltaX).coerceIn(0.0, maxX),
            y = (feedCrop.y + deltaY).coerceIn(0.0, maxY),
            width = feedCrop.width,
            height = feedCrop.height,
        )
    }

    /** factor > 1 acerca (ventana más pequeña). */
    fun scaled(
        feedCrop: MediaItemFeedCrop,
        factor: Float,
        imageWidth: Float,
        imageHeight: Float,
        minNormalizedSide: Double = 0.12,
    ): MediaItemFeedCrop {
        val safeFactor = max(factor, 0.01f)
        val cardAspect = max(feedCrop.cardAspectValue, 0.01f)
        val imageAspect = imageWidth / max(imageHeight, 1f)

        var nextWidth = feedCrop.width / safeFactor
        var nextHeight = nextWidth * imageAspect / cardAspect

        val maxWidth = min(1.0, (cardAspect / max(imageAspect, 0.01f)).toDouble())
        val maxHeight = min(1.0, (imageAspect / cardAspect).toDouble())
        nextWidth = nextWidth.coerceIn(minNormalizedSide, maxWidth)
        nextHeight = nextWidth * imageAspect / cardAspect
        if (nextHeight > maxHeight) {
            nextHeight = maxHeight
            nextWidth = nextHeight * cardAspect / max(imageAspect, 0.01f)
        }

        val centerX = feedCrop.x + feedCrop.width / 2
        val centerY = feedCrop.y + feedCrop.height / 2
        val nextX = (centerX - nextWidth / 2).coerceIn(0.0, max(1.0 - nextWidth, 0.0))
        val nextY = (centerY - nextHeight / 2).coerceIn(0.0, max(1.0 - nextHeight, 0.0))

        return MediaItemFeedCrop(
            cardAspect = feedCrop.cardAspect,
            x = nextX,
            y = nextY,
            width = nextWidth,
            height = nextHeight,
        )
    }

    /**
     * Recalcula width/height para que el crop cubra exactamente la card sobre la foto
     * (sin letterbox). Conserva el centro del encuadre.
     */
    fun alignedToImage(
        feedCrop: MediaItemFeedCrop,
        imageWidth: Float,
        imageHeight: Float,
    ): MediaItemFeedCrop {
        if (imageWidth <= 1f || imageHeight <= 1f) return feedCrop
        val card = max(feedCrop.cardAspectValue, 0.01f)
        val current = feedCrop.rect(imageWidth, imageHeight)
        val cx = current.centerX()
        val cy = current.centerY()
        var cropW = (feedCrop.width * imageWidth).toFloat()
        var cropH = cropW / card
        if (cropH > imageHeight) {
            cropH = imageHeight
            cropW = cropH * card
        }
        if (cropW > imageWidth) {
            cropW = imageWidth
            cropH = cropW / card
        }
        val left = (cx - cropW / 2f).coerceIn(0f, max(imageWidth - cropW, 0f))
        val top = (cy - cropH / 2f).coerceIn(0f, max(imageHeight - cropH, 0f))
        return normalizedFeedCrop(
            RectF(left, top, left + cropW, top + cropH),
            imageWidth,
            imageHeight,
            card,
        )
    }

    fun exportWindow(forAspect: Float): Pair<Float, Float> {
        val safe = feedCardAspect(forAspect)
        return when {
            abs(safe - squareAspect) < 0.02f -> 1080f to 1080f
            abs(safe - portraitMax) < 0.02f -> 1080f to 1350f
            abs(safe - reelsGridAspect) < 0.02f -> 1080f to 1440f
            abs(safe - landscapeMax) < 0.03f -> 1080f to 566f
            else -> exportWidth to (exportWidth / safe)
        }
    }

    /** Rectángulo del post dentro del canvas cuadrado de la preview. */
    fun cropWindow(aspect: Float, canvasWidth: Float, canvasHeight: Float): Pair<Float, Float> {
        val safe = feedCardAspect(aspect)
        return if (safe < 1f) {
            (canvasHeight * safe) to canvasHeight
        } else {
            canvasWidth to (canvasWidth / safe)
        }
    }

    fun coverScale(imageWidth: Float, imageHeight: Float, windowWidth: Float, windowHeight: Float): Float {
        if (imageWidth <= 0f || imageHeight <= 0f || windowWidth <= 0f || windowHeight <= 0f) return 1f
        return max(windowWidth / imageWidth, windowHeight / imageHeight)
    }

    fun fitScale(imageWidth: Float, imageHeight: Float, windowWidth: Float, windowHeight: Float): Float {
        if (imageWidth <= 0f || imageHeight <= 0f || windowWidth <= 0f || windowHeight <= 0f) return 1f
        return min(windowWidth / imageWidth, windowHeight / imageHeight)
    }

    fun fitRelativeScale(originalAspect: Float): Float {
        val aspect = clamp(originalAspect)
        if (aspect <= 0f) return 1f
        return min(aspect, 1f / aspect)
    }

    fun minRelativeScale(
        imageWidth: Float,
        imageHeight: Float,
        windowWidth: Float,
        windowHeight: Float,
        fillWindow: Boolean,
    ): Float {
        val cover = coverScale(imageWidth, imageHeight, windowWidth, windowHeight)
        if (cover <= 0f) return 1f
        if (fillWindow) return 1f
        return min(
            max(fitScale(imageWidth, imageHeight, windowWidth, windowHeight) / cover, 0.01f),
            1f,
        )
    }

    data class Offset(val x: Float, val y: Float)

    fun clampedOffset(
        imageWidth: Float,
        imageHeight: Float,
        windowWidth: Float,
        windowHeight: Float,
        scale: Float,
        offset: Offset,
        minScale: Float,
    ): Offset {
        val cover = coverScale(imageWidth, imageHeight, windowWidth, windowHeight)
        val drawScale = cover * max(scale, minScale)
        val drawnW = imageWidth * drawScale
        val drawnH = imageHeight * drawScale
        val maxX = max((drawnW - windowWidth) / 2f, 0f)
        val maxY = max((drawnH - windowHeight) / 2f, 0f)
        return Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }

    fun cropBitmap(source: Bitmap, feedCrop: MediaItemFeedCrop): Bitmap {
        if (feedCrop.isFullBounds) return source
        val rect = feedCrop.rect(source.width.toFloat(), source.height.toFloat())
        val left = rect.left.toInt().coerceIn(0, source.width - 1)
        val top = rect.top.toInt().coerceIn(0, source.height - 1)
        val width = rect.width().toInt().coerceAtLeast(1).coerceAtMost(source.width - left)
        val height = rect.height().toInt().coerceAtLeast(1).coerceAtMost(source.height - top)
        if (width <= 1 || height <= 1) return source
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    fun expandRect(rect: RectF, toAspect: Float, boundsWidth: Float, boundsHeight: Float): RectF {
        val safeAspect = clamp(toAspect)
        if (rect.width() <= 1f || rect.height() <= 1f || boundsWidth <= 1f || boundsHeight <= 1f) return RectF(rect)
        val current = rect.width() / rect.height()
        var next = RectF(rect)
        if (abs(current - safeAspect) > 0.01f) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            if (safeAspect < current) {
                val height = rect.width() / safeAspect
                next = RectF(rect.left, cy - height / 2f, rect.right, cy - height / 2f + height)
            } else {
                val width = rect.height() * safeAspect
                next = RectF(cx - width / 2f, rect.top, cx - width / 2f + width, rect.bottom)
            }
        }
        return next.also { it.intersect(RectF(0f, 0f, boundsWidth, boundsHeight)) }
    }

    fun exactCropRect(rect: RectF, toAspect: Float, imageWidth: Float, imageHeight: Float): RectF {
        val bounds = RectF(0f, 0f, imageWidth, imageHeight)
        val safeRect = RectF(rect).also { it.intersect(bounds) }
        val safeTarget = clamp(toAspect)
        if (safeRect.width() <= 1f || safeRect.height() <= 1f || safeTarget <= 0f) return safeRect
        val raw = safeRect.width() / safeRect.height()
        if (abs(raw - safeTarget) < 0.005f) return safeRect
        val next = RectF(safeRect)
        if (safeTarget > raw) {
            val height = safeRect.width() / safeTarget
            next.top += (safeRect.height() - height) / 2f
            next.bottom = next.top + height
        } else {
            val width = safeRect.height() * safeTarget
            next.left += (safeRect.width() - width) / 2f
            next.right = next.left + width
        }
        return next.also { it.intersect(bounds) }
    }

    fun cropRect(rect: RectF, toAspect: Float, imageWidth: Float, imageHeight: Float): RectF {
        val bounds = RectF(0f, 0f, imageWidth, imageHeight)
        val safeRect = RectF(rect).also { it.intersect(bounds) }
        val safeTarget = feedCardAspect(toAspect)
        if (safeRect.width() <= 1f || safeRect.height() <= 1f || safeTarget <= 0f) return safeRect
        val raw = safeRect.width() / safeRect.height()
        if (abs(raw - safeTarget) < 0.005f) return safeRect
        val next = RectF(safeRect)
        if (safeTarget > raw) {
            val height = safeRect.width() / safeTarget
            next.top += (safeRect.height() - height) / 2f
            next.bottom = next.top + height
        } else {
            val width = safeRect.height() * safeTarget
            next.left += (safeRect.width() - width) / 2f
            next.right = next.left + width
        }
        return next.also { it.intersect(bounds) }
    }

    fun clampVisibleRect(rect: RectF, imageWidth: Float, imageHeight: Float): RectF {
        val bounds = RectF(0f, 0f, imageWidth, imageHeight)
        val safeRect = RectF(rect).also { it.intersect(bounds) }
        if (safeRect.width() <= 1f || safeRect.height() <= 1f) return safeRect
        val raw = safeRect.width() / safeRect.height()
        return cropRect(safeRect, feedCardAspect(raw), imageWidth, imageHeight)
    }

    /** Píxeles de foto visibles en el preview (sin huecos del canvas). */
    fun visiblePhotoRect(
        imageWidth: Float,
        imageHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        fillWindow: Boolean,
    ): RectF {
        val minScale = minRelativeScale(imageWidth, imageHeight, canvasWidth, canvasHeight, fillWindow)
        val cover = coverScale(imageWidth, imageHeight, canvasWidth, canvasHeight)
        val drawScale = cover * max(scale, minScale)
        if (drawScale <= 0f || imageWidth <= 1f || imageHeight <= 1f || canvasWidth <= 1f || canvasHeight <= 1f) {
            return RectF(0f, 0f, imageWidth, imageHeight)
        }
        val drawnW = imageWidth * drawScale
        val drawnH = imageHeight * drawScale
        val clamped = clampedOffset(
            imageWidth, imageHeight, canvasWidth, canvasHeight,
            max(scale, minScale), Offset(offsetX, offsetY), minScale,
        )
        val originX = (canvasWidth - drawnW) / 2f + clamped.x
        val originY = (canvasHeight - drawnH) / 2f + clamped.y
        val drawnRect = RectF(originX, originY, originX + drawnW, originY + drawnH)
        val canvasBounds = RectF(0f, 0f, canvasWidth, canvasHeight)
        val visible = RectF(drawnRect).also { it.intersect(canvasBounds) }
        if (visible.width() <= 1f || visible.height() <= 1f) {
            return RectF(0f, 0f, imageWidth, imageHeight)
        }
        return RectF(
            (visible.left - originX) / drawScale,
            (visible.top - originY) / drawScale,
            (visible.left - originX) / drawScale + visible.width() / drawScale,
            (visible.top - originY) / drawScale + visible.height() / drawScale,
        )
    }

    val squareSize = 1080f
}

/** ≡ iOS `AssetCropSession`. */
data class AssetCropSession(
    val originalAspect: Float,
    val isSquareExpanded: Boolean = false,
    val scale: Float = MomentFeedCrop.fitRelativeScale(originalAspect),
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    constructor(pixelWidth: Int, pixelHeight: Int) : this(
        originalAspect = MomentFeedCrop.clamp(pixelWidth.toFloat() / max(pixelHeight, 1)),
    )

    val fillsPreview: Boolean get() = isSquareExpanded
    val cropAspect: Float
        get() = if (isSquareExpanded) 1f else MomentFeedCrop.feedCardAspect(originalAspect)
    val canExpandToSquare: Boolean
        get() = abs(MomentFeedCrop.feedCardAspect(originalAspect) - 1f) > 0.02f

    /** ≡ iOS `mutating func applyOrientedSize` — devuelve copia (data class inmutable). */
    fun applyOrientedSize(width: Float, height: Float): AssetCropSession {
        val previousFit = MomentFeedCrop.fitRelativeScale(originalAspect)
        val aspect = MomentFeedCrop.clamp(width / max(height, 1f))
        return if (!isSquareExpanded && abs(scale - previousFit) < 0.03f) {
            copy(
                originalAspect = aspect,
                scale = MomentFeedCrop.fitRelativeScale(aspect),
                offsetX = 0f,
                offsetY = 0f,
            )
        } else {
            copy(originalAspect = aspect)
        }
    }
}

/**
 * ≡ iOS `NormalizedMediaCropContainer`.
 * El [content] debe representar la fuente completa (aspect-fit) dentro del frame recibido.
 *
 * iOS: `content.frame(width:source, height:source).offset` + viewport `topLeading` + `clipped`.
 * Compose: [Layout] con [Constraints.fixed] al tamaño fuente (no un Box+requiredSize:
 * ExoPlayer/AndroidView ignora requiredSize y se mide a la card 16:9).
 */
@Composable
fun NormalizedMediaCropContainer(
    feedCrop: MediaItemFeedCrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart,
            ) {
                content()
            }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val viewportW = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            constraints.minWidth
        }.coerceAtLeast(0)
        val viewportH = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            constraints.minHeight
        }.coerceAtLeast(0)
        val measurable = measurables.firstOrNull()
            ?: return@Layout layout(viewportW, viewportH) {}
        val crop = feedCrop
        if (crop != null &&
            crop.width > 0.0001 &&
            crop.height > 0.0001 &&
            viewportW > 0 &&
            viewportH > 0
        ) {
            val sourceW = (viewportW / crop.width).roundToInt().coerceAtLeast(1)
            val sourceH = (viewportH / crop.height).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(sourceW, sourceH))
            val x = (-sourceW * crop.x).roundToInt()
            val y = (-sourceH * crop.y).roundToInt()
            layout(viewportW, viewportH) {
                placeable.placeRelative(x, y)
            }
        } else {
            val placeable = if (viewportW > 0 && viewportH > 0) {
                measurable.measure(Constraints.fixed(viewportW, viewportH))
            } else {
                measurable.measure(constraints)
            }
            layout(
                viewportW.coerceAtLeast(placeable.width),
                viewportH.coerceAtLeast(placeable.height),
            ) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}

/** Coil: aplica feedCrop al bitmap; el contenedor (grid 1:1) hace fill. */
class NormalizedFeedCropTransformation(
    private val feedCrop: MediaItemFeedCrop,
) : Transformation {
    override val cacheKey: String =
        "moments.feedCrop.${feedCrop.cardAspect}.${feedCrop.x}.${feedCrop.y}.${feedCrop.width}.${feedCrop.height}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        MomentFeedCrop.cropBitmap(input, feedCrop)
}

fun feedCropTransformations(feedCrop: MediaItemFeedCrop?): List<Transformation> {
    if (feedCrop == null || feedCrop.isFullBounds) return emptyList()
    return listOf(NormalizedFeedCropTransformation(feedCrop))
}

/**
 * ≡ iOS `FeedCroppedRemoteImage`:
 * `NormalizedMediaCropContainer` + imagen `.scaledToFit()` (sin processor).
 */
@Composable
fun FeedCroppedRemoteImage(
    model: Any?,
    feedCrop: MediaItemFeedCrop?,
    modifier: Modifier = Modifier,
    placeholderColor: Color = Color.Transparent,
) {
    val context = LocalContext.current
    val aspect = feedCrop?.cardAspectValue?.takeIf { it > 0f } ?: 1f
    NormalizedMediaCropContainer(
        feedCrop = feedCrop,
        modifier = modifier.aspectRatio(aspect),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(placeholderColor),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .build(),
                ),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * ≡ iOS `MomentFeedCropCanvas` (MomentFeedCrop.swift).
 *
 * Misma estructura que Swift:
 * - imagen con frame drawn + offset + frame(window).clipped + gesture en el contentShape
 * - chip en overlay bottomLeading (fuera del gesture)
 * - Magnification/Drag locales hasta onEnded (aquí: liveScale/offset + commit en Final pass)
 */
@Composable
fun MomentFeedCropCanvas(
    imageUri: Uri?,
    imageWidth: Float,
    imageHeight: Float,
    isVideo: Boolean,
    videoDurationText: String?,
    session: AssetCropSession,
    onSessionChange: (AssetCropSession) -> Unit,
    modifier: Modifier = Modifier,
    cropAspect: Float = session.cropAspect,
    showsAspectToggle: Boolean = true,
    onWindowSizeChange: ((Float, Float) -> Unit)? = null,
    onImageSizeResolved: ((Float, Float) -> Unit)? = null,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedCropAspect = cropAspect
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val canvasColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val density = LocalDensity.current
    val liveSession by rememberUpdatedState(session)
    val liveOnChange by rememberUpdatedState(onSessionChange)

    // ≡ @State gestureScale / gestureOffset — live durante el gesto; se commit al soltar.
    var liveScale by remember(imageUri) { mutableFloatStateOf(session.scale) }
    var liveOffsetX by remember(imageUri) { mutableFloatStateOf(session.offsetX) }
    var liveOffsetY by remember(imageUri) { mutableFloatStateOf(session.offsetY) }
    var isGesturing by remember(imageUri) { mutableStateOf(false) }

    // Sync desde sesión cuando no hay gesto activo (chip / cambio de asset).
    LaunchedEffect(session.scale, session.offsetX, session.offsetY, session.isSquareExpanded, imageUri) {
        if (!isGesturing) {
            liveScale = session.scale
            liveOffsetX = session.offsetX
            liveOffsetY = session.offsetY
        }
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageUri)
            .size(Size.ORIGINAL)
            .build(),
    )
    val drawableSize = painterDrawableSize(painter.state)
    val imgW = when {
        imageWidth > 2f -> imageWidth
        drawableSize != null -> drawableSize.first
        else -> 1f
    }
    val imgH = when {
        imageHeight > 2f -> imageHeight
        drawableSize != null -> drawableSize.second
        else -> 1f
    }
    val hasRealSize = imgW > 2f && imgH > 2f
    LaunchedEffect(imageUri, imgW, imgH) {
        if (hasRealSize) onImageSizeResolved?.invoke(imgW, imgH)
    }

    // Chip: iOS usa session.canExpandToSquare; si el EXIF aún no orientó la sesión, usa tamaño real.
    val canExpandFromImage = hasRealSize &&
        abs(MomentFeedCrop.feedCardAspect(MomentFeedCrop.clamp(imgW / max(imgH, 1f))) - 1f) > 0.02f
    val canExpandToSquare = session.canExpandToSquare || canExpandFromImage

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(canvasColor)
            .clipToBounds(),
    ) {
        val windowW = with(density) { maxWidth.toPx() }
        val windowH = with(density) { maxHeight.toPx() }
        LaunchedEffect(windowW, windowH) {
            onWindowSizeChange?.invoke(windowW, windowH)
        }

        if (imageUri != null && hasRealSize && windowW > 2f && windowH > 2f) {
            val fillWindow = session.fillsPreview
            val minScale = MomentFeedCrop.minRelativeScale(
                imgW, imgH, windowW, windowH, fillWindow,
            )
            val clampedScale = liveScale.coerceIn(minScale, MomentFeedCrop.maxScale)
            val cover = MomentFeedCrop.coverScale(imgW, imgH, windowW, windowH)
            val drawScale = cover * clampedScale
            val drawnW = imgW * drawScale
            val drawnH = imgH * drawScale
            val clamped = MomentFeedCrop.clampedOffset(
                imgW, imgH, windowW, windowH,
                clampedScale,
                MomentFeedCrop.Offset(liveOffsetX, liveOffsetY),
                minScale,
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(imageUri, imgW, imgH, windowW, windowH, fillWindow) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            isGesturing = true
                            val minS = MomentFeedCrop.minRelativeScale(
                                imgW, imgH, windowW, windowH, liveSession.fillsPreview,
                            )
                            liveScale = (liveScale * zoom).coerceIn(minS, MomentFeedCrop.maxScale)
                            val next = MomentFeedCrop.clampedOffset(
                                imgW, imgH, windowW, windowH,
                                liveScale,
                                MomentFeedCrop.Offset(liveOffsetX + pan.x, liveOffsetY + pan.y),
                                minS,
                            )
                            liveOffsetX = next.x
                            liveOffsetY = next.y
                        }
                    }
                    .pointerInput(imageUri) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                if (event.changes.none { it.pressed } && isGesturing) {
                                    val current = liveSession
                                    val minS = MomentFeedCrop.minRelativeScale(
                                        imgW, imgH, windowW, windowH, current.fillsPreview,
                                    )
                                    val nextScale = liveScale.coerceIn(minS, MomentFeedCrop.maxScale)
                                    val nextOffset = MomentFeedCrop.clampedOffset(
                                        imgW, imgH, windowW, windowH,
                                        nextScale,
                                        MomentFeedCrop.Offset(liveOffsetX, liveOffsetY),
                                        minS,
                                    )
                                    isGesturing = false
                                    liveScale = nextScale
                                    liveOffsetX = nextOffset.x
                                    liveOffsetY = nextOffset.y
                                    liveOnChange(
                                        current.copy(
                                            scale = nextScale,
                                            offsetX = nextOffset.x,
                                            offsetY = nextOffset.y,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    // ≡ iOS `.resizable().frame(drawn)` — aspect del lienzo = imgW/imgH.
                    // requiredSize: si usamos size(), Compose clampa al window al hacer zoom
                    // y FillBounds deforma la foto.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .requiredSize(
                            with(density) { drawnW.toDp() },
                            with(density) { drawnH.toDp() },
                        )
                        .graphicsLayer {
                            translationX = clamped.x
                            translationY = clamped.y
                        },
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (imageUri != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                CircularProgressIndicator(
                    color = Color(0xFF00A896),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // ≡ .overlay(alignment: .bottomLeading) — fuera del gesture
        if (showsAspectToggle && canExpandToSquare) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .zIndex(10f)
                    .padding(12.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(0.45f), RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val oriented = if (hasRealSize) {
                            liveSession.applyOrientedSize(imgW, imgH)
                        } else {
                            liveSession
                        }
                        val expanded = !oriented.isSquareExpanded
                        val nextScale = if (expanded) {
                            1f
                        } else {
                            MomentFeedCrop.fitRelativeScale(oriented.originalAspect)
                        }
                        Log.d(
                            TAG,
                            "squareTap expanded=$expanded scale=$nextScale " +
                                "origAspect=${oriented.originalAspect}",
                        )
                        isGesturing = false
                        liveScale = nextScale
                        liveOffsetX = 0f
                        liveOffsetY = 0f
                        liveOnChange(
                            oriented.copy(
                                isSquareExpanded = expanded,
                                scale = nextScale,
                                offsetX = 0f,
                                offsetY = 0f,
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                GridPreviewModeChipIcon(
                    fitMode = if (session.fillsPreview) {
                        MomentGridPreviewFitMode.FILL
                    } else {
                        MomentGridPreviewFitMode.FIT
                    },
                    modifier = Modifier.size(14.dp),
                    tint = Color.White,
                )
            }
        }

        if (isVideo && !videoDurationText.isNullOrBlank()) {
            Text(
                videoDurationText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(2f)
                    .padding(12.dp)
                    .background(Color.Black.copy(0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private const val TAG = "MomentFeedCrop"

private fun painterDrawableSize(state: AsyncImagePainter.State): Pair<Float, Float>? {
    val success = state as? AsyncImagePainter.State.Success ?: return null
    val drawable = success.result.drawable
    val width = drawable.intrinsicWidth.toFloat()
    val height = drawable.intrinsicHeight.toFloat()
    if (width > 2f && height > 2f) return width to height
    val size = success.painter.intrinsicSize
    if (size.width.isFinite() && size.width > 2f && size.height.isFinite() && size.height > 2f) {
        return size.width to size.height
    }
    return null
}

/** Construye feedCrop + aspect desde una sesión de canvas (≡ iOS `framedMedia`). */
fun framedFeedCropFromSession(
    imageWidth: Float,
    imageHeight: Float,
    session: AssetCropSession,
    windowWidth: Float,
    windowHeight: Float,
    lockAspect: Float? = null,
): Pair<MediaItemFeedCrop, Float> {
    val visible = MomentFeedCrop.visiblePhotoRect(
        imageWidth, imageHeight,
        windowWidth, windowHeight,
        session.scale, session.offsetX, session.offsetY,
        session.fillsPreview,
    )
    val targetAspect = lockAspect ?: session.cropAspect
    val cropRect = MomentFeedCrop.cropRect(visible, targetAspect, imageWidth, imageHeight)
    val framedAspect = cropRect.width() / max(cropRect.height(), 1f)
    val feedCrop = MomentFeedCrop.normalizedFeedCrop(cropRect, imageWidth, imageHeight, framedAspect)
    return feedCrop to framedAspect
}
