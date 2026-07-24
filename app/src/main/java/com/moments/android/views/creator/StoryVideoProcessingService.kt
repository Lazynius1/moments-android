package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** Errors mirrored from `StoryVideoProcessingError`. */
sealed class StoryVideoProcessingError(message: String) : Exception(message) {
    object MissingVideo : StoryVideoProcessingError("Missing video")
    object InvalidDuration : StoryVideoProcessingError("Invalid video duration")
    object ExceedsAutoSplitLimit : StoryVideoProcessingError("Video exceeds automatic split limit")
    object ExportFailed : StoryVideoProcessingError("Story video export failed")
    object ThumbnailFailed : StoryVideoProcessingError("Story video thumbnail failed")
}

data class StoryVideoClip(
    val media: CreatorMedia,
    val startTime: Double,
    val duration: Double,
)

/** Media3 equivalent of `StoryVideoProcessingService.swift`. */
object StoryVideoProcessingService {
    const val maxStorySegmentDuration = 60.0
    const val maxAutoSplitPartCount = 5
    val maxAutoSplitDuration: Double get() = CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS

    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    suspend fun duration(videoUri: Uri): Double {
        val context = appContext ?: throw StoryVideoProcessingError.MissingVideo
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val seconds = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000.0
            if (!seconds.isFinite() || seconds <= 0.0) throw StoryVideoProcessingError.InvalidDuration
            seconds
        } catch (error: StoryVideoProcessingError) {
            throw error
        } catch (_: Exception) {
            throw StoryVideoProcessingError.InvalidDuration
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun exportStoryClip(videoUri: Uri, start: Double, end: Double): CreatorMedia {
        val context = appContext ?: throw StoryVideoProcessingError.MissingVideo
        val fullDuration = duration(videoUri)
        val safeStart = min(max(0.0, start), fullDuration)
        val safeEnd = min(max(safeStart + 0.1, end), fullDuration)
        val clipDuration = safeEnd - safeStart
        if (clipDuration <= 0.0) throw StoryVideoProcessingError.InvalidDuration

        val output = File(context.cacheDir, "story_clip_${UUID.randomUUID()}.mp4")
        exportClip(context, videoUri, safeStart, safeEnd, output)
        return CreatorMedia(
            uri = Uri.fromFile(output),
            isVideo = true,
            durationSeconds = clipDuration,
            aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
            recommendedAspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
            hasEdits = true,
            storyVideoMode = StoryVideoMode.TRIMMED,
        )
    }

    suspend fun splitStoryVideo(
        videoUri: Uri,
        maxSegmentDuration: Double = this.maxStorySegmentDuration,
    ): List<StoryVideoClip> {
        val totalDuration = duration(videoUri)
        if (maxSegmentDuration <= 0.0) throw StoryVideoProcessingError.InvalidDuration
        if (totalDuration > maxAutoSplitDuration) throw StoryVideoProcessingError.ExceedsAutoSplitLimit

        val clips = mutableListOf<StoryVideoClip>()
        var start = 0.0
        while (start < totalDuration) {
            val end = min(start + maxSegmentDuration, totalDuration)
            val media = exportStoryClip(videoUri, start, end).copy(
                storyVideoMode = StoryVideoMode.NORMAL,
                durationSeconds = end - start,
            )
            clips += StoryVideoClip(media = media, startTime = start, duration = end - start)
            start = end
        }
        return clips
    }

    fun generateStoryThumbnail(videoUri: Uri, time: Double = 0.1): Bitmap {
        val context = appContext ?: throw StoryVideoProcessingError.MissingVideo
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime((max(0.0, time) * 1_000_000L).toLong())
                ?: throw StoryVideoProcessingError.ThumbnailFailed
        } catch (error: StoryVideoProcessingError) {
            throw error
        } catch (_: Exception) {
            throw StoryVideoProcessingError.ThumbnailFailed
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun exportClip(
        context: Context,
        input: Uri,
        startSeconds: Double,
        endSeconds: Double,
        output: File,
    ) = suspendCancellableCoroutine { continuation ->
        val item = MediaItem.Builder()
            .setUri(input)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((startSeconds * 1_000L).toLong())
                    .setEndPositionMs((endSeconds * 1_000L).toLong())
                    .build(),
            )
            .build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    output.delete()
                    if (continuation.isActive) continuation.resumeWithException(StoryVideoProcessingError.ExportFailed)
                }
            })
            .build()
        transformer.start(EditedMediaItem.Builder(item).build(), output.absolutePath)
        continuation.invokeOnCancellation {
            runCatching { transformer.cancel() }
            output.delete()
        }
    }
}
