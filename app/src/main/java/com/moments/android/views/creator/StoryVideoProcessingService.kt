package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.moments.android.R
import com.moments.android.views.creator.creatoruikit.materializeStoryVideoIfNeeded
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Errors ≡ `StoryVideoProcessingError` (LocalizedError + `storyVideo.error.*`).
 */
sealed class StoryVideoProcessingError(
    @StringRes val messageRes: Int,
) : Exception() {
    object MissingVideo : StoryVideoProcessingError(R.string.story_video_error_missing_video)
    object InvalidDuration : StoryVideoProcessingError(R.string.story_video_error_invalid_duration)
    object ExceedsAutoSplitLimit : StoryVideoProcessingError(R.string.story_video_error_exceeds_auto_split_limit)
    object ExportFailed : StoryVideoProcessingError(R.string.story_video_error_export_failed)
    object ThumbnailFailed : StoryVideoProcessingError(R.string.story_video_error_thumbnail_failed)

    override val message: String?
        get() = StoryVideoProcessingService.localizedMessage(messageRes)
}

data class StoryVideoClip(
    val media: CreatorMedia,
    val startTime: Double,
    val duration: Double,
)

/**
 * Port de `StoryVideoProcessingService.swift` (Media3 + MediaMetadataRetriever).
 * Constantes ≡ `maxStorySegmentDuration` / `maxAutoSplitPartCount` / `maxAutoSplitDuration`.
 */
object StoryVideoProcessingService {
    const val maxStorySegmentDuration = 60.0
    const val maxAutoSplitPartCount = 5
    val maxAutoSplitDuration: Double get() = CreatorMedia.MAX_MOMENT_VIDEO_DURATION_SECONDS

    /** iOS `AVAssetImageGenerator.maximumSize` = 540×960. */
    private const val THUMB_MAX_WIDTH = 540
    private const val THUMB_MAX_HEIGHT = 960

    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    internal fun localizedMessage(@StringRes messageRes: Int): String? =
        appContext?.getString(messageRes)

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
        try {
            exportClip(context, videoUri, safeStart, safeEnd, output)
        } catch (_: Exception) {
            output.delete()
            val materialized = materializeStoryVideoIfNeeded(context, videoUri)
            if (materialized == videoUri) throw StoryVideoProcessingError.ExportFailed
            exportClip(context, materialized, safeStart, safeEnd, output)
        }

        val outputUri = Uri.fromFile(output)
        // ≡ iOS: thumbnail from exported clip at 0.1s, then CreatorMedia(image:)
        val thumbnail = generateStoryThumbnail(outputUri, time = 0.1)
        val thumbnailUri = persistStoryThumbnail(context, thumbnail)
            ?: throw StoryVideoProcessingError.ThumbnailFailed

        return CreatorMedia(
            uri = outputUri,
            isVideo = true,
            durationSeconds = clipDuration,
            thumbnailUri = thumbnailUri,
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
            val timeUs = (max(0.0, time) * 1_000_000L).toLong()
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMB_MAX_WIDTH,
                    THUMB_MAX_HEIGHT,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            frame ?: throw StoryVideoProcessingError.ThumbnailFailed
        } catch (error: StoryVideoProcessingError) {
            throw error
        } catch (_: Exception) {
            throw StoryVideoProcessingError.ThumbnailFailed
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun persistStoryThumbnail(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val output = File(context.cacheDir, "story_thumb_${UUID.randomUUID()}.jpg")
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        Uri.fromFile(output)
    }.getOrNull()

    /** ≡ `AVAssetExportPreset1280x720` via Media3 `Presentation.createForHeight(720)`. */
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
        val edited = EditedMediaItem.Builder(item)
            .setEffects(
                Effects(
                    /* audioProcessors = */ emptyList(),
                    /* videoEffects = */ listOf(Presentation.createForHeight(720)),
                ),
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
                    if (continuation.isActive) {
                        continuation.resumeWithException(StoryVideoProcessingError.ExportFailed)
                    }
                }
            })
            .build()
        transformer.start(edited, output.absolutePath)
        continuation.invokeOnCancellation {
            runCatching { transformer.cancel() }
            output.delete()
        }
    }
}
