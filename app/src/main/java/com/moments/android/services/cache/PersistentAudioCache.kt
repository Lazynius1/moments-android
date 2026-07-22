package com.moments.android.services.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.Calendar
import java.util.Date

/** Port de PersistentAudioCache.swift. */
object PersistentAudioCache {

    @Volatile private var appContext: Context? = null
    private lateinit var cacheDirectory: File

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        cacheDirectory = File(context.cacheDir, "StoryAudio").also { it.mkdirs() }
    }

    private fun dir(): File {
        if (!::cacheDirectory.isInitialized) error("PersistentAudioCache.initialize required")
        return cacheDirectory
    }

    fun cachedURL(forRemote: String): File? {
        val file = File(dir(), filename(forRemote))
        return if (file.exists()) file else null
    }

    fun saveToCache(temporaryFile: File, forRemote: String) {
        val destination = File(dir(), filename(forRemote))
        if (destination.exists()) return
        if (!temporaryFile.renameTo(destination)) {
            temporaryFile.copyTo(destination, overwrite = true)
            temporaryFile.delete()
        }
    }

    suspend fun localURL(forRemote: URL): File = withContext(Dispatchers.IO) {
        cachedURL(forRemote.toString())?.let { return@withContext it }
        val connection = forRemote.openConnection()
        val temp = File.createTempFile("audio_", ".m4a", dir())
        connection.getInputStream().use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        saveToCache(temp, forRemote.toString())
        File(dir(), filename(forRemote.toString()))
    }

    fun downloadAndCache(url: URL) {
        if (cachedURL(url.toString()) != null) return
        Thread {
            runCatching {
                val temp = File.createTempFile("audio_", ".m4a", dir())
                url.openStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
                saveToCache(temp, url.toString())
            }
        }.start()
    }

    fun cleanupFiles(olderThanDays: Int = 7) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -olderThanDays)
        val threshold = cal.time
        dir().listFiles()?.forEach { file ->
            if (Date(file.lastModified()) < threshold) file.delete()
        }
    }

    fun cacheSizeInBytes(): Int {
        var total = 0
        dir().walkTopDown().forEach { if (it.isFile) total += it.length().toInt() }
        return total
    }

    private fun filename(remote: String) = hash(remote) + ".m4a"

    private fun hash(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(string.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
