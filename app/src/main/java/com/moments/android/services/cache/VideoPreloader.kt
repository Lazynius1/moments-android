package com.moments.android.services.cache

import androidx.media3.common.MediaItem as ExoMediaItem
import com.moments.android.models.Moment
import com.moments.android.services.performance.PerformanceSignposts
import com.moments.android.services.video.ReelPrebufferService
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.services.video.VideoPlaybackSource
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Port de VideoPreloader.swift.
 * AVPlayerItem → Media3 ExoMediaItem; disco vía PersistentVideoCache.
 */
object VideoPreloader {

    private val assetCache = ConcurrentHashMap<String, ExoMediaItem>()
    private val lastAccessDates = ConcurrentHashMap<String, Date>()
    private val queue = Executors.newSingleThreadExecutor()
    private const val MAX_CACHE_SIZE = 12

    private fun cachedAsset(urlString: String): ExoMediaItem? {
        val hit = assetCache[urlString]
        if (hit != null) lastAccessDates[urlString] = Date()
        return hit
    }

    private fun setCachedAsset(item: ExoMediaItem?, urlString: String) {
        if (item != null) {
            assetCache[urlString] = item
            lastAccessDates[urlString] = Date()
        } else {
            assetCache.remove(urlString)
            lastAccessDates.remove(urlString)
        }
        evictIfNeeded()
    }

    private fun evictIfNeeded() {
        if (assetCache.size <= MAX_CACHE_SIZE) return
        val overflow = assetCache.size - MAX_CACHE_SIZE
        val sorted = lastAccessDates.entries.sortedBy { it.value }.map { it.key }
        for (key in sorted.take(overflow)) {
            assetCache.remove(key)
            lastAccessDates.remove(key)
        }
    }

    fun preloadPlaybackSource(source: VideoPlaybackSource) {
        val urls = (source.preheatUrlStrings + source.playbackUrl).distinct()
        preloadAssets(urls)
    }

    fun preloadMoment(moment: Moment) {
        VideoPlaybackSelector.source(forMoment = moment)?.let { preloadPlaybackSource(it) }
    }

    fun preloadAssets(urls: List<String>) {
        PerformanceSignposts.event("VideoPreloadBatch")
        queue.execute {
            var firstHls: String? = null
            for (urlString in urls.take(MAX_CACHE_SIZE)) {
                if (cachedAsset(urlString) != null) continue
                val isHls = VideoPlaybackSelector.isHlsUrl(urlString)
                if (!isHls) {
                    val local = PersistentVideoCache.cachedURL(urlString)
                    if (local != null) {
                        setCachedAsset(ExoMediaItem.fromUri(local.absolutePath), urlString)
                        continue
                    }
                }
                setCachedAsset(ExoMediaItem.fromUri(urlString), urlString)
                if (isHls && firstHls == null) firstHls = urlString
            }
            val warmUrl = firstHls
            if (warmUrl != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching { ReelPrebufferService.prebuffer(warmUrl) }
                }
            }
        }
    }

    fun getPlayerItem(urlString: String): ExoMediaItem {
        cachedAsset(urlString)?.let { return it }
        if (!VideoPlaybackSelector.isHlsUrl(urlString)) {
            PersistentVideoCache.cachedURL(urlString)?.let {
                return ExoMediaItem.fromUri(it.absolutePath)
            }
        }
        return createNewItem(urlString)
    }

    private fun createNewItem(urlString: String): ExoMediaItem {
        return ExoMediaItem.fromUri(urlString)
    }
}
