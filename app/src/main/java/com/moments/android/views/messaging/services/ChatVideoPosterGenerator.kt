package com.moments.android.views.messaging.services

import android.graphics.Bitmap
import android.net.Uri
import com.moments.android.extensions.extractVideoThumbnailFromFile
import com.moments.android.extensions.extractVideoThumbnailFromUrl
import com.moments.android.services.messaging.ChatCacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Port de `ChatVideoPosterGenerator.swift`. */
object ChatVideoPosterGenerator {
    private val memoryCache = ConcurrentHashMap<String, String>()

    suspend fun poster(videoUrl: String, messageId: String): String? = withContext(Dispatchers.IO) {
        memoryCache[messageId]?.takeIf { localFileExists(it) }?.let { return@withContext it }
        val diskFile = ChatCacheStore.posterFile(messageId)
        if (diskFile.isFile) return@withContext Uri.fromFile(diskFile).toString().also { memoryCache[messageId] = it }

        val candidates = listOf(150_000L, 0L, 500_000L)
        for (timeUs in candidates) {
            val bitmap = videoBitmap(videoUrl, timeUs) ?: continue
            val wrote = runCatching {
                ChatCacheStore.ensureDirectories()
                val data = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 78, stream)
                    stream.toByteArray()
                }
                diskFile.writeBytes(data)
                Uri.fromFile(diskFile).toString()
            }.getOrNull() ?: return@withContext null
            memoryCache[messageId] = wrote
            ChatCacheStore.enforceQuota()
            return@withContext wrote
        }
        null
    }

    fun cachedPosterUrl(messageId: String): String? = ChatCacheStore.posterFile(messageId)
        .takeIf(File::isFile)
        ?.let(Uri::fromFile)
        ?.toString()

    private suspend fun videoBitmap(url: String, timeUs: Long) =
        Uri.parse(url).takeIf { it.scheme == "file" }?.path?.let(::File)?.let { extractVideoThumbnailFromFile(it, timeUs, 720) }
            ?: extractVideoThumbnailFromUrl(url, timeUs, 720)

    private fun localFileExists(url: String): Boolean = Uri.parse(url).path?.let(::File)?.isFile == true
}
