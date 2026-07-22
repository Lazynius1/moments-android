package com.moments.android.services.cache

import android.content.Context
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.Collections

/** Port de PersistentVideoCache.swift. */
object PersistentVideoCache {

    private const val MAX_CACHE_BYTES = 500 * 1024 * 1024

    @Volatile private var appContext: Context? = null
    private lateinit var cacheDirectory: File
    private val activeDownloads = Collections.synchronizedSet(mutableSetOf<String>())

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        cacheDirectory = File(context.cacheDir, "MomentVideos").also { it.mkdirs() }
    }

    private fun dir(): File {
        if (!::cacheDirectory.isInitialized) error("PersistentVideoCache.initialize required")
        return cacheDirectory
    }

    fun cachedURL(forRemote: String): File? {
        val file = File(dir(), hash(forRemote) + ".mp4")
        if (!file.exists()) return null
        touch(file)
        return file
    }

    fun saveToCache(temporaryFile: File, forRemote: String) {
        val destination = File(dir(), hash(forRemote) + ".mp4")
        if (destination.exists()) return
        runCatching {
            if (!temporaryFile.renameTo(destination)) {
                temporaryFile.copyTo(destination, overwrite = true)
                temporaryFile.delete()
            }
            touch(destination)
            enforceSizeLimit()
        }
    }

    fun downloadAndCache(url: URL) {
        val key = url.toString()
        if (cachedURL(key) != null) return
        if (!activeDownloads.add(key)) return
        Thread {
            try {
                val temp = File.createTempFile("video_", ".mp4", dir())
                url.openStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
                saveToCache(temp, key)
            } finally {
                activeDownloads.remove(key)
            }
        }.start()
    }

    private fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    private fun enforceSizeLimit() {
        val files = dir().listFiles()?.filter { it.isFile }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        files.sortBy { it.lastModified() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun hash(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(string.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
