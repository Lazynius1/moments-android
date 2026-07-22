package com.moments.android.extensions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Port de `AVAssetImageGenerator+Thumbnail.swift`.
 * Equivalente Android con [MediaMetadataRetriever] (misma semántica que iOS: ~0.8s, máx. 480px).
 */
object AvAssetThumbnailDefaults {
    const val DEFAULT_TIME_US = 800_000L
    const val MAX_SIZE_PX = 480
}

/**
 * Extrae un frame del vídeo en [timeUs] microsegundos, escalado a [maxSizePx] en el lado largo.
 */
fun MediaMetadataRetriever.extractThumbnail(
    timeUs: Long = AvAssetThumbnailDefaults.DEFAULT_TIME_US,
    maxSizePx: Int = AvAssetThumbnailDefaults.MAX_SIZE_PX,
): Bitmap? {
    val frame = getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
    return frame.scaleToMax(maxSizePx)
}

suspend fun extractVideoThumbnailFromUrl(
    url: String,
    timeUs: Long = AvAssetThumbnailDefaults.DEFAULT_TIME_US,
    maxSizePx: Int = AvAssetThumbnailDefaults.MAX_SIZE_PX,
): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(url, HashMap())
        retriever.extractThumbnail(timeUs, maxSizePx)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

suspend fun extractVideoThumbnailFromFile(
    file: File,
    timeUs: Long = AvAssetThumbnailDefaults.DEFAULT_TIME_US,
    maxSizePx: Int = AvAssetThumbnailDefaults.MAX_SIZE_PX,
): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext null
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractThumbnail(timeUs, maxSizePx)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

suspend fun extractVideoThumbnailFromUri(
    context: Context,
    uri: Uri,
    timeUs: Long = AvAssetThumbnailDefaults.DEFAULT_TIME_US,
    maxSizePx: Int = AvAssetThumbnailDefaults.MAX_SIZE_PX,
): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.extractThumbnail(timeUs, maxSizePx)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun Bitmap.scaleToMax(maxSizePx: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSizePx) return this
    val scale = maxSizePx.toFloat() / longest
    val matrix = Matrix().apply { setScale(scale, scale) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
