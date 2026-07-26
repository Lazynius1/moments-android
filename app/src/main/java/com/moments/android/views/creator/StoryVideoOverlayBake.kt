package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size as Media3Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.compose.ui.geometry.Size
import com.moments.android.views.creator.components.storyMediaBaseRect
import com.moments.android.views.creator.creatoruikit.CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bake de overlays en vídeo — trozo de `storyeditor.swift`
 * (`shouldBake` / `renderStoryOverlayImage` / `exportVideoWithCurrentOverlays`).
 *
 * El “blur bg” de iOS es solo el nombre del fichero temporal: el fondo es
 * [drawStoryMediaBackground] (paleta de colores / gradiente), no un blur gaussiano.
 */
internal fun storyRenderTargetSize(): Pair<Int, Int> {
    val targetWidth = 1080
    var targetHeight = (targetWidth / CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO.coerceAtLeast(0.0001f)).roundToInt()
    if (targetHeight > 3000) targetHeight = 3000
    if (targetHeight < 1200) targetHeight = 1200
    return targetWidth to targetHeight
}

/** ≡ iOS `shouldBakeCurrentOverlaysIntoVideo`. */
internal fun shouldBakeCurrentOverlaysIntoVideo(
    media: CreatorMedia,
    drawingImage: Bitmap?,
    hasAnyTextOverlays: Boolean,
    imageScale: Float = 1f,
    imageOffsetX: Float = 0f,
    imageOffsetY: Float = 0f,
    imageRotationRadians: Float = 0f,
): Boolean {
    if (!media.isVideo || media.storyVideoMode == StoryVideoMode.AUTO_SPLIT) return false
    return drawingImage != null ||
        hasAnyTextOverlays ||
        abs(imageScale - 1f) > 0.001f ||
        abs(imageOffsetX) > 0.5f ||
        abs(imageOffsetY) > 0.5f ||
        abs(imageRotationRadians) > 0.001f
}

/**
 * ≡ iOS `drawStoryMediaBackground` → bitmap (gradiente diagonal).
 */
internal fun renderStoryPaletteBackgroundBitmap(
    palette: List<Color>,
    width: Int,
    height: Int,
): Bitmap {
    val resolved = palette.ifEmpty {
        listOf(Color(0xFF0B1215), Color(0xFF203A43), Color(0xFFFAF9F6))
    }
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val colors = resolved.map { it.toArgb() }.toIntArray()
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    if (colors.size == 1) {
        paint.color = colors[0]
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    } else {
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            colors,
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
    return bmp
}

/**
 * ≡ iOS `renderStoryWithOverlays`:
 * paleta de fondo + media con scale/offset/rotation + dibujo.
 * Stickers/texto NO se bakean (metadata en el viewer).
 */
internal fun renderStoryWithOverlays(
    mediaImage: Bitmap?,
    backgroundPalette: List<Color>,
    drawing: Bitmap?,
    drawingScale: Float,
    drawingOffsetX: Float,
    drawingOffsetY: Float,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotationRadians: Float,
    editorCanvasWidth: Float,
    editorCanvasHeight: Float,
    targetWidth: Int = storyRenderTargetSize().first,
    targetHeight: Int = storyRenderTargetSize().second,
): Bitmap {
    val out = renderStoryPaletteBackgroundBitmap(backgroundPalette, targetWidth, targetHeight)
    val canvas = Canvas(out)
    if (mediaImage != null && !mediaImage.isRecycled) {
        val editorW = editorCanvasWidth.coerceAtLeast(1f)
        val editorH = editorCanvasHeight.coerceAtLeast(1f)
        val scaleFactorX = targetWidth / editorW
        val scaleFactorY = targetHeight / editorH
        val mediaSize = Size(mediaImage.width.toFloat(), mediaImage.height.toFloat())
        val canvasSize = Size(targetWidth.toFloat(), targetHeight.toFloat())
        val baseRect = storyMediaBaseRect(mediaSize, canvasSize)
        val cx = targetWidth / 2f
        val cy = targetHeight / 2f
        canvas.save()
        canvas.translate(cx + imageOffsetX * scaleFactorX, cy + imageOffsetY * scaleFactorY)
        canvas.rotate(Math.toDegrees(imageRotationRadians.toDouble()).toFloat())
        canvas.scale(imageScale, imageScale)
        canvas.translate(-cx, -cy)
        canvas.drawBitmap(
            mediaImage,
            null,
            android.graphics.RectF(baseRect.left, baseRect.top, baseRect.right, baseRect.bottom),
            null,
        )
        canvas.restore()
    }
    val overlay = renderStoryOverlayImage(
        drawing = drawing,
        drawingScale = drawingScale,
        drawingOffsetX = drawingOffsetX,
        drawingOffsetY = drawingOffsetY,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        screenWidth = editorCanvasWidth,
        screenHeight = editorCanvasHeight,
    )
    if (overlay != null) {
        canvas.drawBitmap(overlay, 0f, 0f, null)
        overlay.recycle()
    }
    return out
}

/**
 * ≡ iOS `renderStoryOverlayImage` — solo dibujo (texto/stickers van como metadata).
 */
internal fun renderStoryOverlayImage(
    drawing: Bitmap?,
    drawingScale: Float,
    drawingOffsetX: Float,
    drawingOffsetY: Float,
    targetWidth: Int,
    targetHeight: Int,
    screenWidth: Float,
    screenHeight: Float,
): Bitmap? {
    if (drawing == null || drawing.isRecycled) return null
    val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val scaleFactorX = targetWidth / screenWidth.coerceAtLeast(1f)
    val scaleFactorY = targetHeight / screenHeight.coerceAtLeast(1f)
    val scaledW = targetWidth * drawingScale
    val scaledH = targetHeight * drawingScale
    val left = (targetWidth - scaledW) / 2f + drawingOffsetX * scaleFactorX
    val top = (targetHeight - scaledH) / 2f + drawingOffsetY * scaleFactorY
    val scaled = Bitmap.createScaledBitmap(
        drawing,
        scaledW.roundToInt().coerceAtLeast(1),
        scaledH.roundToInt().coerceAtLeast(1),
        true,
    )
    canvas.drawBitmap(scaled, left, top, null)
    if (scaled !== drawing) scaled.recycle()
    return out
}

/**
 * ≡ iOS `exportVideoWithCurrentOverlays`:
 * pista de fondo = still de paleta de colores; encima el vídeo con transforms; luego dibujo.
 */
internal suspend fun exportVideoWithCurrentOverlays(
    context: Context,
    source: Uri,
    overlay: Bitmap?,
    backgroundPalette: List<Color>,
    targetWidth: Int,
    targetHeight: Int,
    imageScale: Float = 1f,
    imageOffsetX: Float = 0f,
    imageOffsetY: Float = 0f,
    imageRotationRadians: Float = 0f,
    editorCanvasWidth: Float = targetWidth.toFloat(),
    editorCanvasHeight: Float = targetHeight.toFloat(),
): Uri {
    val durationUs = videoDurationUs(context, source).coerceAtLeast(1_000_000L)
    val mediaSize = videoDisplaySize(context, source)
        ?: Size(targetWidth.toFloat(), targetHeight.toFloat())
    val canvasSize = Size(targetWidth.toFloat(), targetHeight.toFloat())
    val baseRect = storyMediaBaseRect(mediaSize, canvasSize)
    val overlayScaleX = ((baseRect.width / canvasSize.width) * imageScale).coerceIn(0.05f, 3f)
    val overlayScaleY = ((baseRect.height / canvasSize.height) * imageScale).coerceIn(0.05f, 3f)
    val scaleFactorX = targetWidth / editorCanvasWidth.coerceAtLeast(1f)
    val scaleFactorY = targetHeight / editorCanvasHeight.coerceAtLeast(1f)
    val mappedOffsetX = imageOffsetX * scaleFactorX
    val mappedOffsetY = imageOffsetY * scaleFactorY
    val anchorX = (0.5f + mappedOffsetX / targetWidth).coerceIn(0f, 1f)
    val anchorY = (0.5f + mappedOffsetY / targetHeight).coerceIn(0f, 1f)
    val rotationDegrees = Math.toDegrees(imageRotationRadians.toDouble()).toFloat()

    val paletteBmp = renderStoryPaletteBackgroundBitmap(backgroundPalette, targetWidth, targetHeight)
    val paletteFile = File(context.cacheDir, "story_palette_bg_${UUID.randomUUID()}.png")
    FileOutputStream(paletteFile).use { out ->
        paletteBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    paletteBmp.recycle()

    val output = File(context.cacheDir, "story_palette_comp_${UUID.randomUUID()}.mp4")
    val bgItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(paletteFile)))
        .setDurationUs(durationUs)
        .setFrameRate(30)
        .build()
    val fgItem = EditedMediaItem.Builder(MediaItem.fromUri(source)).build()

    val compositionEffects = buildList {
        if (overlay != null && !overlay.isRecycled) {
            add(OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(overlay))))
        }
    }

    val composition = Composition.Builder(
        EditedMediaItemSequence.withVideoFrom(listOf(bgItem)),
        EditedMediaItemSequence.withAudioAndVideoFrom(listOf(fgItem)),
    )
        .setVideoCompositorSettings(
            StoryPaletteVideoCompositorSettings(
                outputWidth = targetWidth,
                outputHeight = targetHeight,
                videoScaleX = overlayScaleX,
                videoScaleY = overlayScaleY,
                rotationDegrees = rotationDegrees,
                backgroundAnchorX = anchorX,
                backgroundAnchorY = anchorY,
            ),
        )
        .setEffects(Effects(/* audioProcessors = */ emptyList(), compositionEffects))
        .build()

    return try {
        transformComposition(context, composition, output)
    } finally {
        paletteFile.delete()
    }
}

/** ≡ iOS `prepareMediaForStoryUpload` (rama bake). */
internal suspend fun prepareMediaForStoryUpload(
    context: Context,
    media: CreatorMedia,
    shouldBake: Boolean,
    overlay: Bitmap?,
    backgroundPalette: List<Color>,
    targetWidth: Int,
    targetHeight: Int,
    imageScale: Float = 1f,
    imageOffsetX: Float = 0f,
    imageOffsetY: Float = 0f,
    imageRotationRadians: Float = 0f,
    editorCanvasWidth: Float = targetWidth.toFloat(),
    editorCanvasHeight: Float = targetHeight.toFloat(),
): CreatorMedia {
    if (!shouldBake) return media
    val bakedUri = exportVideoWithCurrentOverlays(
        context = context,
        source = media.uri,
        overlay = overlay,
        backgroundPalette = backgroundPalette,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        imageScale = imageScale,
        imageOffsetX = imageOffsetX,
        imageOffsetY = imageOffsetY,
        imageRotationRadians = imageRotationRadians,
        editorCanvasWidth = editorCanvasWidth,
        editorCanvasHeight = editorCanvasHeight,
    )
    return media.copy(
        uri = bakedUri,
        aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
        recommendedAspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
        hasEdits = true,
        thumbnailUri = null,
    )
}

/**
 * Compositor: input 0 = paleta a pantalla completa; input 1 = vídeo con fit + transforms.
 */
private class StoryPaletteVideoCompositorSettings(
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val videoScaleX: Float,
    private val videoScaleY: Float,
    private val rotationDegrees: Float,
    private val backgroundAnchorX: Float,
    private val backgroundAnchorY: Float,
) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Media3Size>): Media3Size =
        Media3Size(outputWidth, outputHeight)

    override fun getOverlaySettings(inputIndex: Int, presentationTimeUs: Long): OverlaySettings {
        if (inputIndex == 0) {
            return StaticOverlaySettings.Builder().build()
        }
        return StaticOverlaySettings.Builder()
            .setScale(videoScaleX, videoScaleY)
            .setRotationDegrees(rotationDegrees)
            .setBackgroundFrameAnchor(backgroundAnchorX, backgroundAnchorY)
            .setOverlayFrameAnchor(0.5f, 0.5f)
            .build()
    }
}

private suspend fun transformComposition(
    context: Context,
    composition: Composition,
    output: File,
): Uri = suspendCancellableCoroutine { cont ->
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (cont.isActive) cont.resume(Uri.fromFile(output))
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                output.delete()
                if (cont.isActive) cont.resumeWithException(exportException)
            }
        })
        .build()
    transformer.start(composition, output.absolutePath)
    cont.invokeOnCancellation {
        runCatching { transformer.cancel() }
        output.delete()
    }
}

private fun videoDurationUs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        ms * 1000L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun videoDisplaySize(context: Context, uri: Uri): Size? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toFloatOrNull() ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toFloatOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) Size(height, width) else Size(width, height)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
