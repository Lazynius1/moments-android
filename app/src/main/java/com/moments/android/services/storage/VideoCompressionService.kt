package com.moments.android.services.storage

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
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
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Límites de `CreatorMedia` (CreatorSharedModels.swift).
 * Viven aquí para que Services/Storage no dependa de Views/Creator.
 */
object CreatorMediaLimits {
    /** iOS `CreatorMedia.maxMomentVideoUploadSizeBytes` */
    const val MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES: Long =
        com.moments.android.views.creator.CreatorMedia.MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES

    /** iOS `CreatorMedia.maxMomentVideoReadySizeBytes` — por encima → processing pending. */
    const val MAX_MOMENT_VIDEO_READY_SIZE_BYTES: Long =
        com.moments.android.views.creator.CreatorMedia.MAX_MOMENT_VIDEO_READY_SIZE_BYTES

    /** iOS `CreatorMedia.maxStoryVideoReadySizeBytes` */
    const val MAX_STORY_VIDEO_READY_SIZE_BYTES: Long =
        com.moments.android.views.creator.CreatorMedia.MAX_STORY_VIDEO_READY_SIZE_BYTES
}

/** Port de `VideoCompressionPreset`. */
enum class VideoCompressionPreset {
    MOMENT,
    STORY,
    CHAT,
}

/**
 * Port de `VideoCompressionError` (LocalizedError → strings `errors.video.*`).
 */
sealed class VideoCompressionError(message: String) : Exception(message) {
    class InvalidSource(message: String) : VideoCompressionError(message)
    class ExportFailed(message: String) : VideoCompressionError(message)
    class OutputTooLarge(val size: Long, val limit: Long, message: String) :
        VideoCompressionError(message)
}

/** Port de `VideoCompressionLimits`. */
data class VideoCompressionLimits(
    val compressIfLargerThan: Long,
    val maxOutputBytes: Long,
)

/**
 * Port de `VideoCompressionService.swift`.
 * `AVAssetExportPreset1280x720` + `shouldOptimizeForNetworkUse` → Media3 Transformer H.264 @ 720p.
 */
object VideoCompressionService {

    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun limits(preset: VideoCompressionPreset): VideoCompressionLimits = when (preset) {
        VideoCompressionPreset.MOMENT -> VideoCompressionLimits(
            compressIfLargerThan = CreatorMediaLimits.MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES,
            maxOutputBytes = CreatorMediaLimits.MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES,
        )
        VideoCompressionPreset.STORY -> VideoCompressionLimits(
            compressIfLargerThan = CreatorMediaLimits.MAX_STORY_VIDEO_READY_SIZE_BYTES,
            maxOutputBytes = CreatorMediaLimits.MAX_STORY_VIDEO_READY_SIZE_BYTES * 5,
        )
        VideoCompressionPreset.CHAT -> VideoCompressionLimits(
            compressIfLargerThan = 12L * 1024 * 1024,
            maxOutputBytes = 80L * 1024 * 1024,
        )
    }

    suspend fun prepareVideoForUpload(
        inputUri: Uri,
        preset: VideoCompressionPreset,
    ): Uri {
        val context = requireContext()
        val limits = limits(preset)
        val inputSize = fileSize(context, inputUri)
            ?: throw invalidSource(context)

        if (inputSize <= limits.compressIfLargerThan) {
            return inputUri
        }

        val compressedUri = compressVideo(context, inputUri)
        val compressedSize = fileSize(context, compressedUri)
            ?: throw exportFailed(context)

        if (compressedSize > limits.maxOutputBytes) {
            if (compressedUri != inputUri) {
                runCatching { compressedUri.path?.let { File(it).delete() } }
            }
            throw outputTooLarge(context, compressedSize, limits.maxOutputBytes)
        }

        // Caller uploads compressed file; original can remain for local preview until upload completes.
        return compressedUri
    }

    suspend fun prepareVideoDataForUpload(
        data: ByteArray,
        preset: VideoCompressionPreset,
        preferredExtension: String = "mp4",
    ): Uri {
        val context = requireContext()
        val temp = File(
            context.cacheDir,
            "video_upload_${UUID.randomUUID()}.$preferredExtension",
        )
        temp.writeBytes(data)
        return prepareVideoForUpload(Uri.fromFile(temp), preset)
    }

    /**
     * ≡ iOS `compressVideoForStory` — siempre exporta 720p (caller decide cuándo).
     */
    suspend fun compressVideoForStory(inputUri: Uri): Uri {
        val context = requireContext()
        return compressVideo(context, inputUri)
    }

    // MARK: - Private

    private suspend fun compressVideo(context: Context, inputUri: Uri): Uri =
        suspendCancellableCoroutine { cont ->
            val outputFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.mp4")
            val mediaItem = MediaItem.fromUri(inputUri)
            val edited = EditedMediaItem.Builder(mediaItem)
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
                        if (cont.isActive) cont.resume(Uri.fromFile(outputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        outputFile.delete()
                        if (cont.isActive) {
                            cont.resumeWithException(exportFailed(context))
                        }
                    }
                })
                .build()

            transformer.start(edited, outputFile.absolutePath)
            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
                outputFile.delete()
            }
        }

    private fun fileSize(context: Context, uri: Uri): Long? {
        val path = uri.path
        if (uri.scheme == "file" && path != null) {
            val file = File(path)
            return if (file.exists()) file.length() else null
        }
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }

    private fun requireContext(): Context =
        appContext ?: throw exportFailed(null)

    private fun invalidSource(context: Context?): VideoCompressionError.InvalidSource =
        VideoCompressionError.InvalidSource(
            context?.getString(R.string.errors_video_invalid_source)
                ?: "Could not read the video.",
        )

    private fun exportFailed(context: Context?): VideoCompressionError.ExportFailed =
        VideoCompressionError.ExportFailed(
            context?.getString(R.string.errors_video_export_failed)
                ?: "Could not compress the video.",
        )

    private fun outputTooLarge(
        context: Context,
        size: Long,
        limit: Long,
    ): VideoCompressionError.OutputTooLarge {
        val sizeLabel = Formatter.formatFileSize(context, size)
        val limitLabel = Formatter.formatFileSize(context, limit)
        return VideoCompressionError.OutputTooLarge(
            size = size,
            limit = limit,
            message = context.getString(
                R.string.errors_video_output_too_large,
                sizeLabel,
                limitLabel,
            ),
        )
    }
}
