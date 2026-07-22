package com.moments.android.services.storage

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Límites de CreatorMedia (aún no portado el modelo completo).
object CreatorMediaLimits {
    const val MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES: Long = 300L * 1024 * 1024
    const val MAX_STORY_VIDEO_READY_SIZE_BYTES: Long = 60L * 1024 * 1024
}

enum class VideoCompressionPreset {
    MOMENT,
    STORY,
    CHAT,
}

sealed class VideoCompressionError(message: String) : Exception(message) {
    object InvalidSource : VideoCompressionError("invalid video source")
    object ExportFailed : VideoCompressionError("video export failed")
    data class OutputTooLarge(val size: Long, val limit: Long) :
        VideoCompressionError("video too large: $size > $limit")
}

data class VideoCompressionLimits(
    val compressIfLargerThan: Long,
    val maxOutputBytes: Long,
)

// Port de VideoCompressionService.swift. AVAssetExportPreset1280x720 → Media3 Transformer @ 720p.
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
        val context = appContext ?: throw VideoCompressionError.ExportFailed
        val limits = limits(preset)
        val inputSize = fileSize(context, inputUri)
            ?: throw VideoCompressionError.InvalidSource

        if (inputSize <= limits.compressIfLargerThan) {
            return inputUri
        }

        val compressedUri = compressVideo(context, inputUri)
        val compressedSize = fileSize(context, compressedUri)
            ?: throw VideoCompressionError.ExportFailed

        if (compressedSize > limits.maxOutputBytes) {
            if (compressedUri != inputUri) {
                runCatching { File(compressedUri.path!!).delete() }
            }
            throw VideoCompressionError.OutputTooLarge(compressedSize, limits.maxOutputBytes)
        }
        return compressedUri
    }

    suspend fun prepareVideoDataForUpload(
        data: ByteArray,
        preset: VideoCompressionPreset,
        preferredExtension: String = "mp4",
    ): Uri {
        val context = appContext ?: throw VideoCompressionError.ExportFailed
        val temp = File(
            context.cacheDir,
            "video_upload_${UUID.randomUUID()}.$preferredExtension",
        )
        temp.writeBytes(data)
        return prepareVideoForUpload(Uri.fromFile(temp), preset)
    }

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
                            cont.resumeWithException(VideoCompressionError.ExportFailed)
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
}
