package com.moments.android.views.story.storyviewer

import android.app.Activity
import android.util.Log
import android.media.MediaMetadataRetriever
import android.graphics.Picture
import android.graphics.HardwareRenderer
import android.graphics.RenderNode
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import android.net.Uri
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.moments.android.views.creator.transformComposition
import com.moments.android.views.creator.videoDurationUs
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import com.moments.android.views.shared.MomentsTheme
import com.moments.android.views.creator.StoryMediaLayoutRules
import com.moments.android.views.creator.StoryMediaPresentationMode
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.moments.android.models.MediaItem
import com.moments.android.models.StickerData
import com.moments.android.models.Story
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.views.creator.components.resolvedTextOverlays
import com.moments.android.views.creator.creatoruikit.creatorNormalizedUp
import com.moments.android.views.creator.exportStillImageVideo
import com.moments.android.views.creator.exportVideoWithCurrentOverlays
import com.moments.android.views.creator.storyRenderTargetSize
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class StoryExportTime(val timeUs: Long, val startDateMs: Long) {
    val dateMs: Long get() = startDateMs + timeUs / 1000
}
internal val LocalStoryExportTime = staticCompositionLocalOf<StoryExportTime?> { null }
internal val LocalStoryExportGifFrames = staticCompositionLocalOf<Map<String, Bitmap>?> { null }
internal val LocalStoryExportVideoFrames = staticCompositionLocalOf<Map<String, Bitmap>?> { null }

/** ≡ iOS `StoryDownloadComposer`. */
internal object StoryDownloadComposer {
    private const val STILL_DURATION_US = 5_000_000L

    suspend fun exportAndSave(
        context: Context,
        story: Story,
        stickers: List<StickerData>,
        layoutWidthDp: Float,
    ): Boolean {
        val file = try {
            exportToTemporaryFile(context, story, stickers, layoutWidthDp)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("StoryDownload", "Unable to export story", error)
            return false
        }
        return try {
            withContext(Dispatchers.IO) { saveVideoFileToGallery(context, file) }
        } finally {
            file.delete()
        }
    }

    suspend fun exportToTemporaryFile(
        context: Context,
        story: Story,
        stickers: List<StickerData>,
        layoutWidthDp: Float,
    ): File {
        val (width, height) = evenCanvasSize()
        val mediaUrl = story.mediaItem.url
        require(mediaUrl.isNotBlank()) { "Missing media" }
        val source = File(context.cacheDir, "story_download_src_${System.nanoTime()}")
        try {
            withContext(Dispatchers.IO) {
                URL(mediaUrl).openStream().use { input -> source.outputStream().use { input.copyTo(it) } }
            }
            val base = if (story.mediaItem.type == MediaItem.MediaType.VIDEO) {
                val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                val background = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
                val uri = withContext(Dispatchers.IO) {
                    exportVideoWithCurrentOverlays(
                        context = context, source = Uri.fromFile(source), overlay = null,
                        backgroundPalette = listOf(background, background),
                        targetWidth = width, targetHeight = height,
                    )
                }
                fileFromUri(context, uri)
            } else {
                val image = withContext(Dispatchers.IO) {
                    val decoded = BitmapFactory.decodeFile(source.absolutePath) ?: error("Missing image")
                    val orientation = runCatching {
                        android.media.ExifInterface(source.absolutePath).getAttributeInt(
                            android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                    }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
                    decoded.creatorNormalizedUp(orientation).also { if (it !== decoded) decoded.recycle() }
                }
                try {
                    val frame = snapshotPublishedPhoto(context, image, width, height, layoutWidthDp)
                    try {
                        fileFromUri(context, withContext(Dispatchers.IO) {
                            exportStillImageVideo(context, frame, STILL_DURATION_US)
                        })
                    } finally { frame.recycle() }
                } finally { image.recycle() }
            }
            if (stickers.isEmpty() && story.resolvedTextOverlays.isEmpty()) return base
            return try {
                addLiveOverlays(context, base, stickers, story.resolvedTextOverlays,
                    story.id.orEmpty(), story.authorId, layoutWidthDp)
            } finally { base.delete() }
        } finally { source.delete() }
    }

    private suspend fun snapshotPublishedPhoto(
        context: Context,
        image: Bitmap,
        width: Int,
        height: Int,
        layoutWidthDp: Float,
    ): Bitmap {
        val activity = context.findActivity() ?: error("Story export requires an Activity")
        var result: Bitmap? = null
        val presentation = StoryMediaLayoutRules.presentationMode(
            image.width.toFloat() / image.height, width.toFloat() / height)
        coroutineScope {
            val renderer = StoryOfflineRenderer(activity, width, height, layoutWidthDp, this) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Image(image.asImageBitmap(), null, Modifier.fillMaxSize().scale(1.1f).blur(20.dp),
                        contentScale = ContentScale.Crop)
                    Image(image.asImageBitmap(), null, Modifier.fillMaxSize(),
                        contentScale = if (presentation == StoryMediaPresentationMode.FILL) ContentScale.Crop else ContentScale.Fit)
                }
            }
            try {
                renderer.prepare()
                result = renderer.render(0, emptyMap(), emptyMap())
            } finally { renderer.close() }
        }
        return checkNotNull(result)
    }

    /** Render the overlay scene at encoder timestamps with a private Compose clock.
     * Only the current frame is retained; no real-time recording or PNG sequence. */
    suspend fun addLiveOverlays(
        context: Context,
        source: File,
        stickers: List<StickerData>,
        textOverlays: List<StoryTextOverlayMetadata>,
        storyId: String,
        userId: String,
        layoutWidthDp: Float,
        textMaxLayoutWidthDp: Float? = null,
    ): File {
        val activity = context.findActivity() ?: error("Story export requires an Activity")
        val durationUs = withContext(Dispatchers.IO) { videoDurationUs(context, Uri.fromFile(source)) }
        require(durationUs > 0) { "Missing video duration" }
        val (width, height) = evenCanvasSize()
        val directory = File(context.cacheDir, "story_frames_${System.nanoTime()}").apply { mkdirs() }
        val gifSources = mutableMapOf<String, StoryExportGif>()
        val audioFiles = mutableMapOf<String, File>()
        val videoSources = mutableMapOf<String, Pair<MediaMetadataRetriever, Long>>()
        val videoFiles = mutableMapOf<String, File>()
        try {
            withContext(Dispatchers.IO) {
                for (url in stickers.mapNotNull { it.videoURL }.filter { it.isNotBlank() }.distinct()) {
                    val local = File(directory, "video_${videoSources.size}.mp4")
                    val uri = Uri.parse(url)
                    val stream = if (uri.scheme == "content" || uri.scheme == "file") {
                        context.contentResolver.openInputStream(uri) ?: error("Missing sticker video")
                    } else URL(url).openStream()
                    stream.use { input -> local.outputStream().use { input.copyTo(it) } }
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(local.absolutePath)
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()?.times(1000) ?: error("Missing sticker duration")
                        require(duration > 0)
                        videoSources[url] = retriever to duration
                        videoFiles[url] = local
                    } catch (error: Exception) { retriever.release(); throw error }
                }
                for (url in stickers.mapNotNull { it.gifURL }.filter { it.isNotBlank() }.distinct()) {
                    val local = File(directory, "gif_${gifSources.size}.gif")
                    copyMedia(context, url, local)
                    gifSources[url] = StoryExportGif(local)
                }
                for (url in stickers.filter { it.type == "audio" }.mapNotNull { it.audioURL }
                    .filter { it.isNotBlank() }.distinct()) {
                    val local = File(directory, "audio_${audioFiles.size}")
                    copyMedia(context, url, local)
                    audioFiles[url] = local
                }
            }
            return coroutineScope {
                val exportJob = checkNotNull(coroutineContext[Job])
                val renderer = StoryOfflineRenderer(activity, width, height, layoutWidthDp, this) {
                    StoryMediaOverlayRendererView(
                        textOverlays = textOverlays, stickers = stickers, drawingData = null,
                        storyId = storyId, userId = userId,
                        reportsDeckInteractionExclusion = false, allowsStickerHitTesting = false,
                        renderingMode = StoryOverlayRenderingMode.LIVE,
                        textMaxLayoutWidthDp = textMaxLayoutWidthDp,
                        modifier = Modifier.requiredSize(layoutWidthDp.dp, (layoutWidthDp * height / width).dp),
                    )
                }
                val overlay = object : BitmapOverlay() {
                    private var lastTimeUs = Long.MIN_VALUE
                    private var bitmap: Bitmap? = null
                    override fun getBitmap(presentationTimeUs: Long): Bitmap {
                        if (presentationTimeUs != lastTimeUs) {
                            // Media3 calls this on its GL worker. Cancellation also unblocks
                            // this bridge, so Transformer cancellation cannot wait on the UI.
                            val next = runBlocking(exportJob) {
                                val videos = mutableMapOf<String, Bitmap>()
                                try {
                                    withContext(Dispatchers.IO) {
                                        for ((url, video) in videoSources) {
                                            videos[url] = video.first.getFrameAtTime(
                                                presentationTimeUs.coerceAtLeast(0) % video.second,
                                                MediaMetadataRetriever.OPTION_CLOSEST,
                                            ) ?: error("Unable to decode video sticker")
                                        }
                                    }
                                    withContext(Dispatchers.Main.immediate) {
                                        val gifs = gifSources.mapValues { it.value.frame(presentationTimeUs) }
                                        renderer.render(presentationTimeUs, videos, gifs)
                                    }
                                } catch (error: Throwable) {
                                    // No UI dispatch here: cancellation must unblock the GL
                                    // worker even while Transformer is shutting down on Main.
                                    videos.values.forEach {
                                        if (!renderer.owns(it) && !it.isRecycled) it.recycle()
                                    }
                                    throw error
                                }
                            }
                            bitmap?.recycle()
                            bitmap = next
                            lastTimeUs = presentationTimeUs
                        }
                        return checkNotNull(bitmap)
                    }
                    override fun release() {
                        super.release()
                        bitmap?.recycle()
                        bitmap = null
                    }
                }
                try {
                    renderer.prepare()
                    val item = EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(source)))
                        .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
                        .build()
                    val sequences = mutableListOf(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item)))
                    // Shared Moment cards autoplay unmuted. Audio-only looping sequences
                    // stop at the duration of the non-looping base story.
                    for (url in stickers.filter { it.type == "shareMoment" }.mapNotNull { it.videoURL }.distinct()) {
                        val retriever = videoSources[url]?.first ?: continue
                        if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") continue
                        val file = videoFiles[url] ?: continue
                        val audio = EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(file)))
                            .setRemoveVideo(true).build()
                        sequences += EditedMediaItemSequence.withAudioFrom(listOf(audio))
                            .buildUpon().setIsLooping(true).build()
                    }
                    for (file in audioFiles.values) {
                        val audio = EditedMediaItem.Builder(androidx.media3.common.MediaItem.Builder()
                            .setUri(Uri.fromFile(file))
                            .setClippingConfiguration(androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                                .setEndPositionMs((durationUs + 999) / 1000).build()).build())
                            .setRemoveVideo(true).build()
                        sequences += EditedMediaItemSequence.withAudioFrom(listOf(audio))
                    }
                    val composition = Composition.Builder(sequences).build()
                    val output = File(context.cacheDir, "story_download_${System.nanoTime()}.mp4")
                    try {
                        transformComposition(context, composition, output)
                        output
                    } catch (error: Throwable) {
                        output.delete()
                        throw error
                    }
                } finally { renderer.close() }
            }
        } finally {
            videoSources.values.forEach { runCatching { it.first.release() } }
            gifSources.values.forEach { it.close() }
            directory.deleteRecursively()
        }
    }

    suspend fun saveEditorVideo(
        context: Context,
        base: File,
        stickers: List<StickerData>,
        texts: List<StoryTextOverlayMetadata>,
        userId: String,
        layoutWidthDp: Float,
    ): Boolean = try {
        val download = if (stickers.isEmpty() && texts.isEmpty()) base
            else addLiveOverlays(context, base, stickers, texts, "editor-download", userId, layoutWidthDp,
                textMaxLayoutWidthDp = com.moments.android.views.creator.components.StoryTextCanvasPlacement.maxLayoutWidth(layoutWidthDp))
        try { withContext(Dispatchers.IO) { saveVideoFileToGallery(context, download) } }
        finally { if (download != base) download.delete() }
    } finally { base.delete() }

    suspend fun exportEditorStill(
        context: Context,
        composed: Bitmap,
        overlay: Bitmap?,
    ): File {
        val (width, height) = evenCanvasSize()
        val frame = flattenBitmaps(listOfNotNull(composed, overlay), width, height)
        val uri = try { exportStillImageVideo(context, frame, STILL_DURATION_US) }
        finally { if (frame !== composed && !frame.isRecycled) frame.recycle() }
        return fileFromUri(context, uri)
    }

    fun evenCanvasSize(): Pair<Int, Int> {
        val (width, height) = storyRenderTargetSize()
        return (width / 2 * 2) to (height / 2 * 2)
    }

    fun flattenBitmaps(layers: List<Bitmap>, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        layers.forEach { layer ->
            if (!layer.isRecycled) {
                canvas.drawBitmap(layer, null, android.graphics.Rect(0, 0, width, height), null)
            }
        }
        return out
    }

    fun saveVideoFileToGallery(context: Context, file: File): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Moment_${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Moments")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        var target: Uri? = null
        return try {
            val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            target = destination
            resolver.openOutputStream(destination)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Unable to open gallery destination")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    destination,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            true
        } catch (error: Exception) {
            Log.e("StoryDownload", "Unable to save exported story", error)
            target?.let { runCatching { resolver.delete(it, null, null) } }
            false
        }
    }

    private fun fileFromUri(context: Context, uri: android.net.Uri): File {
        if (uri.scheme == "file") {
            return File(uri.path ?: error("missing path"))
        }
        val copy = File(context.cacheDir, "story_download_${System.nanoTime()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(copy).use { output -> input.copyTo(output) }
        } ?: error("MediaStore")
        return copy
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A seekable GIF decoder. Movie evaluates GIF disposal and frame delays without
 * starting a drawable or retaining a full-resolution bitmap for every frame. */
@Suppress("DEPRECATION")
private class StoryExportGif(file: File) : java.io.Closeable {
    private val movie = android.graphics.Movie.decodeFile(file.absolutePath)
        ?: error("Unable to decode GIF for export")
    private val bitmap = Bitmap.createBitmap(movie.width().coerceAtLeast(1),
        movie.height().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    fun frame(timeUs: Long): Bitmap {
        movie.setTime(((timeUs.coerceAtLeast(0) / 1000) % movie.duration().coerceAtLeast(100)).toInt())
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        movie.draw(AndroidCanvas(bitmap), 0f, 0f)
        return bitmap
    }
    override fun close() { bitmap.recycle() }
}

private fun copyMedia(context: Context, url: String, destination: File) {
    val uri = Uri.parse(url)
    val stream = if (uri.scheme == "content" || uri.scheme == "file") {
        context.contentResolver.openInputStream(uri) ?: error("Missing media")
    } else URL(url).openStream()
    stream.use { input -> destination.outputStream().use { input.copyTo(it) } }
}

/** The export owns a recomposer: text's existing keyframes, gradients, masks and
 * transforms all run on virtual video time, independently of display refreshes. */
private class StoryOfflineRenderer(
    private val activity: Activity,
    private val width: Int,
    private val height: Int,
    private val layoutWidthDp: Float,
    private val scope: CoroutineScope,
    private val content: @Composable () -> Unit,
) {
    private val clock = BroadcastFrameClock()
    private var recomposer: Recomposer? = null
    private var runner: Job? = null
    private var view: ComposeView? = null
    private var hardware: StoryHardwareSnapshot? = null
    private val startDateMs = System.currentTimeMillis()
    private val time = mutableStateOf(StoryExportTime(0, startDateMs))
    private val videos = mutableStateOf<Map<String, Bitmap>>(emptyMap())
    private val gifs = mutableStateOf<Map<String, Bitmap>>(emptyMap())
    private var clockNanos = 0L
    private val ownedVideoFrames = java.util.concurrent.ConcurrentHashMap.newKeySet<Bitmap>()

    suspend fun prepare() = withContext(Dispatchers.Main.immediate) {
        val owner = Recomposer(scope.coroutineContext + Dispatchers.Main.immediate + clock)
        recomposer = owner
        owner.pauseCompositionFrameClock()
        runner = scope.launch(Dispatchers.Main.immediate + clock) { owner.runRecomposeAndApplyChanges() }
        val host = ComposeView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(width, height)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            (activity as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (activity as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (activity as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
            setParentCompositionContext(owner)
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(width / layoutWidthDp.coerceAtLeast(1f), 1f),
                    LocalStoryExportTime provides time.value,
                    LocalStoryExportVideoFrames provides videos.value,
                    LocalStoryExportGifFrames provides gifs.value,
                ) { MomentsTheme { content() } }
            }
        }
        view = host
        (activity.window.decorView as ViewGroup).addView(host, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) hardware = StoryHardwareSnapshot(width, height)
        layout(host)
        // One preparation interval for cached remote artwork; never one per frame.
        delay(150)
        settle(owner)
        layout(host)
    }

    suspend fun render(timeUs: Long, videoFrames: Map<String, Bitmap>, gifFrames: Map<String, Bitmap>): Bitmap =
        withContext(Dispatchers.Main.immediate) {
            coroutineContext.ensureActive()
            val owner = checkNotNull(recomposer)
            val host = checkNotNull(view)
            time.value = StoryExportTime(timeUs, startDateMs)
            ownedVideoFrames.addAll(videoFrames.values)
            videos.value = videoFrames
            gifs.value = gifFrames
            owner.resumeCompositionFrameClock()
            Snapshot.sendApplyNotifications()
            yield()
            clockNanos = maxOf(clockNanos + 1, timeUs.coerceAtLeast(0) * 1000 + 1)
            clock.sendFrame(clockNanos)
            yield()
            owner.pauseCompositionFrameClock()
            settle(owner)
            layout(host)
            // Size callbacks can change centered text placement after measurement.
            settle(owner)
            layout(host)
            val obsolete = ownedVideoFrames.filter { it !in videoFrames.values }
            obsolete.forEach { it.recycle() }
            ownedVideoFrames.removeAll(obsolete.toSet())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkNotNull(hardware).capture(host)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val picture = Picture()
                host.draw(picture.beginRecording(width, height))
                picture.endRecording()
                val rendered = Bitmap.createBitmap(picture, width, height, Bitmap.Config.ARGB_8888)
                try { checkNotNull(rendered.copy(Bitmap.Config.ARGB_8888, false)) }
                finally { rendered.recycle() }
            } else host.drawToBitmap(Bitmap.Config.ARGB_8888)
        }

    private suspend fun settle(owner: Recomposer) {
        // Animation waiters are paused, leaving only finite layout/recomposition work.
        repeat(32) {
            Snapshot.sendApplyNotifications()
            yield()
            if (!owner.hasPendingWork) return
            clock.sendFrame(++clockNanos)
            yield()
        }
        error("Story export composition did not settle")
    }

    private fun layout(host: View) {
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, width, height)
    }

    fun owns(bitmap: Bitmap): Boolean = bitmap in ownedVideoFrames

    suspend fun close() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        view?.let { host ->
            (host.parent as? ViewGroup)?.removeView(host)
            host.disposeComposition()
        }
        view = null
        ownedVideoFrames.forEach { it.recycle() }
        ownedVideoFrames.clear()
        videos.value = emptyMap()
        recomposer?.cancel()
        runner?.cancel()
        runner?.join()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) hardware?.close()
        hardware = null
    }
}

/** Native rendering keeps RenderEffects, hardware images and Compose layers intact.
 * Uses the public HardwareRenderer/ImageReader path documented by Android. */
@RequiresApi(Build.VERSION_CODES.Q)
private class StoryHardwareSnapshot(width: Int, height: Int) : java.io.Closeable {
    private val reader = ImageReader.newInstance(
        width, height, PixelFormat.RGBA_8888, 2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    private val node = RenderNode("StoryDownload").apply { setPosition(0, 0, width, height) }
    private val renderer = HardwareRenderer().apply {
        setSurface(reader.surface)
        setContentRoot(node)
        setOpaque(false)
    }

    fun capture(view: View): Bitmap {
        val canvas = node.beginRecording()
        try {
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            view.draw(canvas)
        } finally { node.endRecording() }
        val result = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        check(result and HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND == 0) { "Export surface lost" }
        val image = reader.acquireNextImage() ?: error("Missing rendered story frame")
        return image.use {
            val buffer = image.hardwareBuffer ?: error("Missing story frame buffer")
            buffer.use {
                val bitmap = Bitmap.wrapHardwareBuffer(buffer, null) ?: error("Invalid story frame buffer")
                try { checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) }
                finally { bitmap.recycle() }
            }
        }
    }

    override fun close() {
        renderer.destroy()
        node.discardDisplayList()
        reader.close()
    }
}
