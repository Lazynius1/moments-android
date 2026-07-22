package com.moments.android.services.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.moments.android.extensions.AvAssetThumbnailDefaults
import com.moments.android.extensions.extractVideoThumbnailFromUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap

/** Port de VideoThumbnailCache.swift. */
object VideoThumbnailCache {

    private val memoryCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>?): Boolean {
                return size > COUNT_LIMIT
            }
        },
    )
    private const val COUNT_LIMIT = 150

    @Volatile private var appContext: Context? = null
    private lateinit var directory: File

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        directory = File(context.cacheDir, "VideoThumbnails").also { it.mkdirs() }
    }

    fun cachedThumbnail(forUrl: String): Bitmap? = memoryCache[forUrl]

    suspend fun thumbnail(forUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache[forUrl]?.let { return@withContext it }
        val file = fileURL(forUrl)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.let {
                putMemory(forUrl, it)
                return@withContext it
            }
        }
        val bitmap = extractVideoThumbnailFromUrl(
            forUrl,
            timeUs = AvAssetThumbnailDefaults.DEFAULT_TIME_US,
            maxSizePx = AvAssetThumbnailDefaults.MAX_SIZE_PX,
        ) ?: return@withContext null
        putMemory(forUrl, bitmap)
        runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }
        bitmap
    }

    private fun putMemory(key: String, bitmap: Bitmap) {
        memoryCache[key] = bitmap
    }

    private fun fileURL(urlString: String): File {
        if (!::directory.isInitialized) error("VideoThumbnailCache.initialize required")
        val digest = MessageDigest.getInstance("SHA-256").digest(urlString.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(40)
        return File(directory, "$name.jpg")
    }
}
