package com.moments.android.services.cache

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.imageLoader
import com.moments.android.services.messaging.ChatCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/** Port de CacheManager.swift — Kingfisher disk → Coil + caches locales. */
object CacheManager : DefaultLifecycleObserver {

    private const val LAST_CLEANUP_KEY = "LastCacheCleanupDate"
    private const val PREFS = "moments_cache_manager"
    private const val MAX_CACHE_SIZE = 2000L * 1024 * 1024
    private const val WARNING_THRESHOLD = 1500L * 1024 * 1024

    @Volatile private var appContext: Context? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        startIntelligentCleanup()
    }

    private fun prefs() = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun requireContext() = appContext ?: error("CacheManager.initialize required")

    override fun onStart(owner: LifecycleOwner) {
        ioScope.launch {
            val currentSize = getCurrentCacheSize()
            if (currentSize > WARNING_THRESHOLD && currentSize > MAX_CACHE_SIZE) {
                performIntelligentCleanup()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        ioScope.launch {
            cleanupTemporaryFiles()
        }
    }

    private fun startIntelligentCleanup() {
        ioScope.launch {
            // Diferir 15 segundos para no competir con el arranque en frío ni el primer frame
            delay(15_000)
            if (shouldPerformCleanup()) performIntelligentCleanup()
            while (isActive) {
                delay(TimeUnit.HOURS.toMillis(12))
                performIntelligentCleanup()
            }
        }
    }

    private fun shouldPerformCleanup(): Boolean {
        val last = prefs().getLong(LAST_CLEANUP_KEY, 0L)
        if (last == 0L) return true
        val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        return last < oneDayAgo
    }

    private fun performIntelligentCleanup() {
        if (getCurrentCacheSize() > MAX_CACHE_SIZE) {
            cleanupOldCache()
            cleanupUnusedCache()
        }
        prefs().edit().putLong(LAST_CLEANUP_KEY, System.currentTimeMillis()).apply()
    }

    private fun cleanupOldCache() {
        // iOS: clearMemoryCache + cleanExpiredDiskCache (NO borra todo el disco).
        // Coil no expone cleanExpired; el disk cache se autorregula por maxSize.
        if (getCurrentCacheSize() > MAX_CACHE_SIZE) {
            requireContext().imageLoader.memoryCache?.clear()
        }
    }

    private fun cleanupUnusedCache() {
        cleanupVideoCache()
        cleanupAudioCache()
    }

    private fun cleanupVideoCache() {
        val videoDir = File(requireContext().cacheDir, "MomentVideos")
        if (!videoDir.exists()) return
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = cal.time
        videoDir.listFiles()?.forEach { file ->
            if (Date(file.lastModified()) < sevenDaysAgo) file.delete()
        }
    }

    private fun cleanupAudioCache() {
        PersistentAudioCache.cleanupFiles(olderThanDays = 7)
    }

    private fun cleanupTemporaryFiles() {
        // iOS: FileManager.temporaryDirectory — en Android los temps de upload viven en cacheDir.
        val tempDir = requireContext().cacheDir
        val thresholdMs = 1_800_000L // 30 minutos
        val now = System.currentTimeMillis()
        tempDir.listFiles()?.forEach { file ->
            val name = file.name
            val ours = name.contains("story_video") || name.contains("compressed_") ||
                name.contains("thumbnail_") || name.contains("Glowsy") ||
                name.contains("video_upload_") ||
                name.endsWith(".mp4") || name.endsWith(".mov") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".m4a") || name.endsWith(".wav")
            if (ours && now - file.lastModified() > thresholdMs) file.delete()
        }
    }

    private fun getCurrentCacheSize(): Long {
        val coilDisk = requireContext().imageLoader.diskCache?.size ?: 0L
        val video = getVideoCacheSize().toLong()
        val audio = PersistentAudioCache.cacheSizeInBytes().toLong()
        val chat = ChatCacheStore.totalMediaBytes()
        return coilDisk + video + audio + chat
    }

    private fun getVideoCacheSize(): Int {
        val videoDir = File(requireContext().cacheDir, "MomentVideos")
        var total = 0
        videoDir.walkTopDown().forEach { if (it.isFile) total += it.length().toInt() }
        return total
    }

    fun getCacheSize(): String {
        val sizeInMB = getCurrentCacheSize() / (1024 * 1024)
        return "$sizeInMB MB"
    }

    fun forceCleanup() {
        cleanupOldCache()
        cleanupUnusedCache()
        prefs().edit().putLong(LAST_CLEANUP_KEY, System.currentTimeMillis()).apply()
    }
}
