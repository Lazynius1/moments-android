package com.moments.android.services.cache

import androidx.media3.common.MediaItem as ExoMediaItem
import com.moments.android.models.Moment
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.services.video.VideoPlaybackSource
import java.net.URL
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Port de VideoPreloader.swift.
 * AVPlayerItem → Media3 ExoMediaItem + [VideoPlaybackSelector] tier preheat URLs.
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
        queue.execute {
            for (urlString in urls.take(MAX_CACHE_SIZE)) {
                if (cachedAsset(urlString) != null) continue
                val local = PersistentVideoCache.cachedURL(urlString)
                if (local != null) {
                    setCachedAsset(ExoMediaItem.fromUri(local.absolutePath), urlString)
                } else {
                    val remote = runCatching { URL(urlString) }.getOrNull() ?: continue
                    setCachedAsset(ExoMediaItem.fromUri(urlString), urlString)
                    PersistentVideoCache.downloadAndCache(remote)
                }
            }
        }
    }

    fun getPlayerItem(urlString: String): ExoMediaItem {
        cachedAsset(urlString)?.let { return it }
        PersistentVideoCache.cachedURL(urlString)?.let {
            return ExoMediaItem.fromUri(it.absolutePath)
        }
        return createNewItem(urlString)
    }

    private fun createNewItem(urlString: String): ExoMediaItem {
        val item = ExoMediaItem.fromUri(urlString)
        runCatching { URL(urlString) }.getOrNull()?.let { PersistentVideoCache.downloadAndCache(it) }
        return item
    }
}
